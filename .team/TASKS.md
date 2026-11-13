# TASKS.md — 작업 분해 (sched-worker용)

> `.team/PLAN.md` 기준. 순서대로 구현한다(각 작업은 원칙적으로 앞 작업의 결과물 위에 쌓임).
> 레거시(`C:\Users\USER\Desktop\scheduler-test\scheduler-test`)는 참고만 하고 수정하지 않는다.
> **실행이력 테이블 설계/마이그레이션은 이번 범위에서 제외**하고 별도 future task(T14)로만 명시한다. 단, `ExecutionResult` 값 객체 + `ExecutionResultSink` 포트를 자리 잡는 것(T5)은 이번 범위에 포함된다.

---

### T1. 프로젝트 스캐폴딩

- Maven 프로젝트 생성(`pom.xml`): Spring Boot 3.x (web, security, thymeleaf, jdbc, quartz starter), Flyway, SQL Server JDBC 드라이버, Lombok(선택).
- `PLAN.md` §2 패키지 구조대로 빈 패키지/디렉터리 생성.
- `application.yml` 뼈대 작성: 메타DB 연결(`${DB_URL}`/`${DB_USERNAME}`/`${DB_PASSWORD}` 플레이스홀더), `MASTER_ENCRYPTION_KEY` 플레이스홀더 참조 위치만 마련(실제 암복호화 구현은 T6).
- `SchedulerServiceApplication` 기동 확인(DB 연결 없이 컨텍스트 로드 실패해도 이 단계에선 허용 — 메타DB는 T2에서 붙음).

**DoD**: `mvn clean package` 성공. 디렉터리 구조가 PLAN.md §2와 일치. README 또는 `CLAUDE.md`에 "환경변수로 뭘 넣어야 로컬 기동이 되는지" 1차 안내 문구 존재.

---

### T2. 메타DB 스키마 — Flyway 마이그레이션 (실행이력 제외)

- `schedule_definition`, `schedule_parameter`, `target_db_config`, `credential`, `admin_user` 테이블 Flyway 마이그레이션 작성(PLAN.md §4.1~4.5 컬럼 그대로).
- Quartz 공식 SQL Server `QRTZ_*` DDL 스크립트를 마이그레이션에 포함(직접 설계하지 않고 Quartz 배포본의 표준 스크립트 사용).
- 로컬 개발용 SQL Server(또는 테스트 컨테이너)로 마이그레이션 적용 검증.

**DoD**: `mvn flyway:migrate`(또는 Spring Boot 기동 시 자동 적용)로 전체 테이블이 에러 없이 생성됨. 실행이력 테이블은 **생성하지 않음**(의도적 제외 확인).

---

### T3. 도메인 모델 + Repository 인터페이스/구현

- `domain/schedule`: `ScheduleDefinition`, `ScheduleParameter`, `ActionType`, `GroupingMode` (값 객체/엔티티), `ScheduleRepository`/`ScheduleParameterRepository` 인터페이스.
- `domain/config`: `TargetDbConfig`, `Credential`, 해당 Repository 인터페이스.
- `domain/admin`: `AdminUser`, `AdminUserRepository`.
- `infra/persistence/jdbc`: 위 인터페이스의 `JdbcTemplate`/`NamedParameterJdbcTemplate` 구현체(레거시의 수동 `ResultSet` 매핑 스타일 유지, ORM 사용 안 함).
- `action_config` JSON ↔ Java 객체 매핑(Jackson)을 `action_type`별로 역직렬화하는 유틸(`ApiCallActionConfig`, `DisplayOnlyActionConfig`).

**DoD**: 각 Repository의 CRUD가 단위/슬라이스 테스트로 검증됨(저장 후 조회 시 필드 일치, `action_config` JSON 왕복 직렬화 정확성 포함).

---

### T4. 비밀값 암복호화 (`infra/crypto`)

- `CredentialCipher`: AES-GCM, 마스터키를 환경변수(`MASTER_ENCRYPTION_KEY`, base64)에서 로드해 `credential.encrypted_value`/`target_db_config.encrypted_password` 암복호화.
- 마스터키 미설정 시 기동 실패(명확한 에러 메시지)로 처리 — 운영 비밀값이 평문으로 새는 상황을 막기 위한 fail-fast.
- `CredentialRepository`/`TargetDbConfigRepository` 구현체가 저장 시 암호화, 조회 시 복호화하도록 T3 구현에 연결.

**DoD**: 단위 테스트로 "저장 시 암호화된 값이 DB에 들어가고, 조회 API/서비스 레벨에서는 평문이 복원됨"을 확인. 평문이 로그에 출력되지 않음을 코드 리뷰로 확인.

---

### T5. 실행 엔진 코어 — `ExecutionResult` / `Sink` / SP 호출 / 액션 핸들러

이번 작업이 "교정사항 #1·#2·#3·#5·#6"을 실제로 코드 경계로 만드는 핵심 단계.

1. `engine/execution`: `TriggerType`, `ExecutionStatus`, `ExecutionResult`(불변 값 객체), `ExecutionResultSink`(인터페이스), `LoggingExecutionResultSink`(SLF4J 로그만 남기는 현재 유일 구현), `DisplayOutput`, `DisplayOutputStore`(스케줄ID→최신 1건, `ConcurrentHashMap` 기반 인메모리 캐시, 최대 보관 행수 상한 적용).
2. `engine/sp`: `StoredProcedureInvoker`(JDBC `EXEC {spName} {bound params}` 실행, 파라미터 없는 호출도 지원), `SpParameterResolver`(`schedule_parameter.value_source`가 `LITERAL`/`CURRENT_DATE`/`CURRENT_DATETIME`일 때 실제 바인딩 값 산출).
3. `engine/action`: `ActionHandler` 인터페이스, `ApiCallActionHandler`(그룹핑 모드 PER_ROW/GROUP_BY 분기, `BodyTemplateRenderer`로 `${COLUMN}`/`${cred:NAME}` 치환, `GenericHttpClient`로 POST), `DisplayOnlyActionHandler`(SP 결과 → `DisplayOutput` 변환, 외부 호출 없음).
4. `engine/execution/ScheduleExecutor`: 위 조각들을 조립하는 단일 진입점. 흐름: 스케줄 정의 로드 → 중복실행 가드 → SP 호출 → `action_type` 분기(ActionHandler 호출) → 성공/실패에 따라 `ExecutionResult` 생성 → `ExecutionResultSink.record()` 호출 (+ `DISPLAY_ONLY`면 `DisplayOutputStore`에도 적재) → 결과 반환(호출자가 Quartz Job이든 수동실행 API든 동일 반환값 사용).
5. 예외 발생 시에도 `finally`에서 반드시 `ExecutionResult`(status=FAILURE, errorMessage)가 만들어지고 Sink에 기록되는지 확인(레거시 `try/finally`의 가드 해제 패턴을 실행결과 기록으로 일반화).

**DoD**:
- SP 호출 실패/성공, `API_CALL` 성공/일부실패, `DISPLAY_ONLY` 정상 동작 각각에 대해 `ScheduleExecutor`를 직접 호출하는 통합 테스트(메타DB·모의 대상DB·모의 HTTP 서버 사용)로 `ExecutionResult.status`/`summary`/`errorMessage`가 기대대로 채워짐을 확인.
- `ExecutionResultSink` 구현을 테스트용 Fake로 교체해도 `ScheduleExecutor` 코드 변경이 필요 없음을 테스트로 증명(= 저장소 비의존 경계 검증).
- `credential` 복호화 값이 `ExecutionResult.summary`/로그에 그대로 노출되지 않음을 확인.

---

### T6. Quartz 연동 — 동적 등록/해제

- `engine/quartz/QuartzConfig`: JDBC JobStore 빈 설정(T2의 `QRTZ_*` 테이블 사용).
- `engine/quartz/GenericScheduleJob`: `Job` 구현, `JobDataMap`에는 `scheduleId`만 담고 실행 시점에 최신 `ScheduleDefinition`을 재조회 후 `ScheduleExecutor.execute(scheduleId, SCHEDULED)` 호출. `@DisallowConcurrentExecution` 적용.
- `engine/quartz/ScheduleRegistrar`: `register(ScheduleDefinition)`/`reschedule(...)`/`unregister(scheduleId)` — `CronTrigger` 생성/교체/삭제를 감싸는 얇은 래퍼.
- 애플리케이션 기동 시 `enabled=true`인 `schedule_definition` 전체를 읽어 `ScheduleRegistrar.register(...)` 호출하는 `ApplicationRunner`(또는 `@EventListener(ApplicationReadyEvent)`).

**DoD**: 통합 테스트로 (1) 기동 시 enabled 스케줄이 Quartz에 등록되고 다음 발화시각이 cron과 일치, (2) 스케줄 활성화/비활성화 토글이 실제 Quartz 트리거 add/remove로 반영, (3) cron 수정 시 재등록(cancel+재등록)이 실제로 발화시각을 바꿈을 확인.

---

### T7. 관리자 REST API — 스케줄 CRUD + 파라미터

- `web/api/ScheduleController`, `ScheduleParameterController`: PLAN.md §6 "스케줄 CRUD + 파라미터" 표의 엔드포인트 구현.
- 생성/수정/삭제/활성화토글 시 T6의 `ScheduleRegistrar`를 호출해 Quartz 상태를 즉시 동기화.
- 요청 바디 검증: `action_type`에 맞는 `action_config` 모양 검증(예: `API_CALL`인데 `url` 없음 → 400), cron 표현식 유효성 검증.

**DoD**: API 테스트(MockMvc 등)로 CRUD 전 경로 + 유효성 검증 실패 케이스 + "수정 시 Quartz 트리거가 실제로 바뀜"까지 확인.

---

### T8. 관리자 REST API — 즉시실행 + 최근결과 조회 (교정사항 #4)

- `web/api/ScheduleExecutionController`:
  - `POST /api/schedules/{id}/run-now` → `ScheduleExecutor.execute(scheduleId, MANUAL)`을 **동기 호출**하고 결과(`ExecutionResult` + 있으면 `DisplayOutput`)를 그대로 응답 바디로 반환.
  - 이미 실행 중(중복실행 가드에 걸림)일 때는 409 등 명확한 상태코드 + 메시지로 구분.
  - `GET /api/schedules/{id}/last-result` → `DisplayOutputStore`/가장 최근 `ExecutionResult`(인메모리) 조회, 아직 없으면 204/빈 응답.
  - `GET /api/schedules/{id}/status` → Quartz 기준 다음 실행 예정시각 + 현재 실행중 여부.

**DoD**: API 테스트로 (1) 정규 스케줄과 무관하게 즉시 1회 실행되고 응답에 결과가 동기적으로 담겨 있음, (2) 실행 중 중복 호출 시 거부 응답, (3) `DISPLAY_ONLY` 스케줄의 즉시실행 응답에 가공된 데이터가 포함됨을 확인.

---

### T9. 관리자 REST API — 대상DB/자격증명 설정

- `web/api/TargetDbConfigController`, `CredentialController`: PLAN.md §6 해당 표 엔드포인트.
- 응답 시 비밀번호/자격증명 값은 항상 마스킹(끝 4자 등), 평문 반환 엔드포인트 없음.
- 삭제 시 참조 중인 `schedule_definition`/`action_config`가 있으면 차단 또는 경고.

**DoD**: API 테스트로 CRUD + 마스킹 동작 + 참조 중 삭제 차단 확인. 응답 바디/로그에 평문 비밀값이 노출되지 않음을 테스트로 확인(T4의 fail-fast와 별개로, API 레벨에서도 검증).

---

### T10. 인증 (Spring Security)

- `admin_user` 기반 세션 로그인(`AuthController` + `SecurityConfig`), BCrypt 비밀번호.
- `/api/**`, `/admin/**` 전체를 인증 필요로 보호(로그인/헬스체크 제외).
- 최초 기동 시 관리자 계정이 하나도 없으면 환경변수(`INITIAL_ADMIN_USERNAME`/`INITIAL_ADMIN_PASSWORD`)로 1회 시드하는 로직(부트스트랩 문제 해결 — 레거시엔 이런 보호가 없었으므로 신규).

**DoD**: 인증 없이 `/api/**` 호출 시 401/302(로그인 리다이렉트), 로그인 후 정상 호출. 최초 기동 시 시드 계정으로 로그인 가능함을 통합 테스트로 확인.

---

### T11. 관리자 웹 UI (Thymeleaf)

- PLAN.md §7의 8개 화면 구현: 로그인, 스케줄 목록, 스케줄 생성/수정(동적 폼), 수동실행 결과, 최근 실행결과 보기, 대상DB 설정, 자격증명 관리, 비밀번호 변경.
- 스케줄 생성/수정 폼은 `action_type` 선택에 따라 `API_CALL`/`DISPLAY_ONLY` 입력 영역을 JS로 토글.
- "지금 실행" 버튼 → T8 API 호출 → 같은 화면에서 결과 표시(페이지 리로드 없이 AJAX 권장, 서버사이드 폼 제출도 허용).

**DoD**: 브라우저로 직접 조작해 스케줄 생성 → cron 반영 확인(T6 상태 API로 다음 실행시각 확인) → "지금 실행"으로 결과 확인 → 대상DB/자격증명 등록 → 전체 플로우가 재컴파일 없이 동작함을 수동 검증(화면 스크린샷 또는 조작 로그로 보고).

---

### T12. 설정/비밀값 최종 점검 (부트스트랩 계층)

- `application.yml`의 메타DB 접속정보, `MASTER_ENCRYPTION_KEY`, `INITIAL_ADMIN_*`가 전부 환경변수 플레이스홀더로만 존재하고 소스에 실제 값이 없는지 점검.
- `.gitignore`에 로컬 비밀값 파일(`application-local.yml` 등) 등록 확인.
- "env var만 바꿔서 다른 업체에 재배포" 시나리오를 문서(README)로 1페이지 정리(코드 변경/재컴파일 불필요함을 체크리스트로).

**DoD**: 리포지토리 전체에서 실제 비밀값 리터럴 검색(grep) 결과 0건. README 체크리스트 존재.

---

### T13. 테스트 정리 + 회귀 확인

- T1~T12에서 작성된 단위/통합 테스트를 모아 `mvn test` 1회 그린 확인.
- 레거시 3가지 실행 패턴(주간 1회+그룹발송 / 평일 30분+개별발송 / 평일 1일1회+개별발송, 각각 파라미터 없음)을 **신규 스케줄 정의 데이터로 재현**하는 시나리오 테스트 최소 1개씩(= "레거시 실행흐름이 포팅됐다"는 것의 실증).

**DoD**: `mvn test` 전체 그린. 레거시 3패턴 각각에 대응하는 시나리오 테스트 3개 존재 및 통과.

---

## Future (이번 범위 제외, 별도 라운드)

### T14 (future). 실행이력 테이블 설계 + 마이그레이션 + `JdbcExecutionResultSink`

- PLAN.md §3.1/§3.2, §6(TBD 명시)에 따라 실행이력 테이블 스키마를 별도로 설계(감시 서버 원칙: 상태/요약만, SP 전체 결과행 보존 금지. 실패 기록 포함).
- `ExecutionResultSink`의 새 구현(`JdbcExecutionResultSink` 등)을 추가하고 `LoggingExecutionResultSink`와 조합(`CompositeExecutionResultSink`) 또는 교체.
- **착수 조건**: 이번 TASKS.md 범위(T1~T13) 완료 후, 별도로 이력 테이블 스키마 설계 라운드를 거친 뒤 진행.
