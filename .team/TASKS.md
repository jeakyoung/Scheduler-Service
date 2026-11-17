# TASKS.md — 작업 분해 (sched-worker용)

> `.team/PLAN.md` 기준. 순서대로 구현한다(각 작업은 원칙적으로 앞 작업의 결과물 위에 쌓임).
> 레거시(`C:\Users\USER\Desktop\scheduler-test\scheduler-test`)는 참고만 하고 수정하지 않는다.
> **실행이력 테이블 설계/마이그레이션은 이번 범위에서 제외**하고 별도 future task(T28)로만 명시한다. 단, `ExecutionResult` 값 객체 + `ExecutionResultSink` 포트를 자리 잡는 것(T6)은 이번 범위에 포함된다.
> **2026-11-13 3차 교정(사용자 직접 지시) 반영**: 관리자 화면은 Thymeleaf가 아니라 별도 React SPA(`frontend/`)로 구현한다. 백엔드(`backend/`)는 REST API만 제공한다. 이에 따라 레포를 `backend/`/`frontend/` 두 디렉터리로 분리하는 작업(T1)과 프론트엔드 스캐폴딩+CORS 연동(T2)을 별도 작업으로 추가했다.
> **2026-11-16 교정(사용자 직접 지시) 반영**: PLAN.md에 3가지 신규 기능 — (1) 알람식 반복주기 입력 UI(§9), (2) 스케줄당 대상DB 다중 선택(§4.3b), (3) 대상DB별 모아보기(§7) — 가 추가됨에 따라, T1~T14(기존 범위) 뒤에 T15~T21을 이어서 추가한다. 스키마 변경(T15) → 도메인/엔진(T16~T17) → API(T18) → 프론트(T19~T20) → 회귀테스트(T21) 순서.
> **2026-11-17 교정(사용자 직접 지시) 반영**: 실제 배포 가능한 인프라가 외부 Tomcat뿐이라는 제약에 따라 PLAN.md §11(배포 방식)이 신설됨 — "백엔드 jar+내장톰캣, 프론트 별도 배포"를 "Tomcat 단일 WAR" 배포로 전환. T21(기존 범위 마지막) 뒤에 T22~T27(WAR 전환/빌드 파이프라인 통합/SPA 폴백 라우팅/API 베이스 URL 상대경로화/로컬 개발 워크플로 회귀 확인/README 배포 가이드)을 이어서 추가한다. 기존 future task(실행이력 테이블)는 T28로 밀렸다.

---

### T1. 백엔드 프로젝트 스캐폴딩

- 레포 루트에 `backend/` 디렉터리를 만들고 그 아래에 Maven 프로젝트(`pom.xml`) 생성: Spring Boot 3.x (web, security, jdbc, quartz starter — **thymeleaf 의존성 없음**, 백엔드는 REST API 전용), Flyway, SQL Server JDBC 드라이버, Lombok(선택).
  - **Spring Boot 패치 버전은 지금 시점에 독립적으로 확인해서 고정할 것.** PLAN.md §1의 지침대로, 현재 안정적인 최신 3.x 패치 버전을 확인하고 그 버전을 쓰는 근거(Java 17/21 LTS 호환, 알려진 치명적 버그 없음 등)를 1~2줄로 `pom.xml` 근처 커밋 메시지 또는 README에 남긴다. 다른 무관한 프로젝트의 `pom.xml`/빌드 설정을 그대로 복사하지 않는다 — 베낀 것으로 보이면 반려 대상.
- `PLAN.md` §2의 `backend/` 트리 그대로 빈 패키지/디렉터리 생성(`engine/quartz`, `engine/execution`, `engine/action`, `engine/sp`, `domain/*`, `infra/*`, `web/api`, `security` — `web/admin` 없음).
- `application.yml` 뼈대 작성: 메타DB 연결(`${DB_URL}`/`${DB_USERNAME}`/`${DB_PASSWORD}` 플레이스홀더), `MASTER_ENCRYPTION_KEY` 플레이스홀더, `app.cors.allowed-origin` 플레이스홀더(실제 CORS 설정은 T2/T9에서 완성) 참조 위치만 마련.
- `SchedulerServiceApplication` 기동 확인(DB 연결 없이 컨텍스트 로드 실패해도 이 단계에선 허용 — 메타DB는 T3에서 붙음).

**DoD**: `backend/`에서 `mvn clean package` 성공. 디렉터리 구조가 PLAN.md §2의 `backend/` 트리와 일치. 사용한 Spring Boot 정확 버전과 선정 근거가 문서화됨. README 또는 `CLAUDE.md`에 "환경변수로 뭘 넣어야 로컬 기동이 되는지" 1차 안내 문구 존재.

---

### T2. 프론트엔드 스캐폴딩 + CORS 연동 준비

- 레포 루트에 `frontend/` 디렉터리를 만들고 React SPA 프로젝트 생성(빌드 도구는 구현 시점에 worker가 판단 — Vite 등 표준적인 선택이면 충분, 특정 도구를 강제하지 않음).
- `frontend/src/api/` 에 백엔드 호출 클라이언트 스켈레톤 생성: base URL을 환경변수(`.env.local`, gitignore)로 주입받도록 구성, `.env.example`에 플레이스홀더만 커밋.
- `frontend/src/pages/` 에 PLAN.md §7의 8개 화면에 대응하는 빈 페이지 컴포넌트 생성(내용 구현은 T12).
- 백엔드 `security/CorsConfig`를 추가(또는 T1에서 만든 자리에 실제 구현): `app.cors.allowed-origin` 환경변수로 받은 오리진만 허용. 로컬 개발 시 프론트 dev 서버 오리진을 넣어 실제로 브라우저에서 CORS 에러 없이 백엔드 헬스체크 엔드포인트를 호출할 수 있는지 1회 확인.

**DoD**: `frontend/`에서 로컬 개발 서버 기동 성공. 빈 페이지 라우팅이 동작. 프론트 dev 서버에서 백엔드(T1에서 띄운 빈 애플리케이션)로 보낸 테스트 fetch가 CORS 에러 없이 응답을 받음(또는 받아야 할 에러만 받음 — 인증 미구현 상태의 401 등은 허용, CORS 차단은 불허).

---

### T3. 메타DB 스키마 — Flyway 마이그레이션 (실행이력 제외)

- `schedule_definition`, `schedule_parameter`, `target_db_config`, `credential`, `admin_user` 테이블 Flyway 마이그레이션 작성(PLAN.md §4.1~4.5 컬럼 그대로).
- Quartz 공식 SQL Server `QRTZ_*` DDL 스크립트를 마이그레이션에 포함(직접 설계하지 않고 Quartz 배포본의 표준 스크립트 사용).
- 로컬 개발용 SQL Server(또는 테스트 컨테이너)로 마이그레이션 적용 검증.

**DoD**: `mvn flyway:migrate`(또는 Spring Boot 기동 시 자동 적용)로 전체 테이블이 에러 없이 생성됨. 실행이력 테이블은 **생성하지 않음**(의도적 제외 확인).

---

### T4. 도메인 모델 + Repository 인터페이스/구현

- `domain/schedule`: `ScheduleDefinition`, `ScheduleParameter`, `ActionType`, `GroupingMode` (값 객체/엔티티), `ScheduleRepository`/`ScheduleParameterRepository` 인터페이스.
- `domain/config`: `TargetDbConfig`, `Credential`, 해당 Repository 인터페이스.
- `domain/admin`: `AdminUser`, `AdminUserRepository`.
- `infra/persistence/jdbc`: 위 인터페이스의 `JdbcTemplate`/`NamedParameterJdbcTemplate` 구현체(레거시의 수동 `ResultSet` 매핑 스타일 유지, ORM 사용 안 함).
- `action_config` JSON ↔ Java 객체 매핑(Jackson)을 `action_type`별로 역직렬화하는 유틸(`ApiCallActionConfig`, `DisplayOnlyActionConfig`).

**DoD**: 각 Repository의 CRUD가 단위/슬라이스 테스트로 검증됨(저장 후 조회 시 필드 일치, `action_config` JSON 왕복 직렬화 정확성 포함).

---

### T5. 비밀값 암복호화 (`infra/crypto`)

- `CredentialCipher`: AES-GCM, 마스터키를 환경변수(`MASTER_ENCRYPTION_KEY`, base64)에서 로드해 `credential.encrypted_value`/`target_db_config.encrypted_password` 암복호화.
- 마스터키 미설정 시 기동 실패(명확한 에러 메시지)로 처리 — 운영 비밀값이 평문으로 새는 상황을 막기 위한 fail-fast.
- `CredentialRepository`/`TargetDbConfigRepository` 구현체가 저장 시 암호화, 조회 시 복호화하도록 T4 구현에 연결.

**DoD**: 단위 테스트로 "저장 시 암호화된 값이 DB에 들어가고, 조회 API/서비스 레벨에서는 평문이 복원됨"을 확인. 평문이 로그에 출력되지 않음을 코드 리뷰로 확인.

---

### T6. 실행 엔진 코어 — `ExecutionResult` / `Sink` / SP 호출 / 액션 핸들러

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

### T7. Quartz 연동 — 동적 등록/해제

- `engine/quartz/QuartzConfig`: JDBC JobStore 빈 설정(T3의 `QRTZ_*` 테이블 사용).
- `engine/quartz/GenericScheduleJob`: `Job` 구현, `JobDataMap`에는 `scheduleId`만 담고 실행 시점에 최신 `ScheduleDefinition`을 재조회 후 `ScheduleExecutor.execute(scheduleId, SCHEDULED)` 호출. `@DisallowConcurrentExecution` 적용.
- `engine/quartz/ScheduleRegistrar`: `register(ScheduleDefinition)`/`reschedule(...)`/`unregister(scheduleId)` — `CronTrigger` 생성/교체/삭제를 감싸는 얇은 래퍼.
- 애플리케이션 기동 시 `enabled=true`인 `schedule_definition` 전체를 읽어 `ScheduleRegistrar.register(...)` 호출하는 `ApplicationRunner`(또는 `@EventListener(ApplicationReadyEvent)`).

**DoD**: 통합 테스트로 (1) 기동 시 enabled 스케줄이 Quartz에 등록되고 다음 발화시각이 cron과 일치, (2) 스케줄 활성화/비활성화 토글이 실제 Quartz 트리거 add/remove로 반영, (3) cron 수정 시 재등록(cancel+재등록)이 실제로 발화시각을 바꿈을 확인.

---

### T8. 관리자 REST API — 스케줄 CRUD + 파라미터

- `web/api/ScheduleController`, `ScheduleParameterController`: PLAN.md §6 "스케줄 CRUD + 파라미터" 표의 엔드포인트 구현.
- 생성/수정/삭제/활성화토글 시 T7의 `ScheduleRegistrar`를 호출해 Quartz 상태를 즉시 동기화.
- 요청 바디 검증: `action_type`에 맞는 `action_config` 모양 검증(예: `API_CALL`인데 `url` 없음 → 400), cron 표현식 유효성 검증.

**DoD**: API 테스트(MockMvc 등)로 CRUD 전 경로 + 유효성 검증 실패 케이스 + "수정 시 Quartz 트리거가 실제로 바뀜"까지 확인.

---

### T9. 관리자 REST API — 즉시실행 + 최근결과 조회 (교정사항 #4)

- `web/api/ScheduleExecutionController`:
  - `POST /api/schedules/{id}/run-now` → `ScheduleExecutor.execute(scheduleId, MANUAL)`을 **동기 호출**하고 결과(`ExecutionResult` + 있으면 `DisplayOutput`)를 그대로 응답 바디로 반환.
  - 이미 실행 중(중복실행 가드에 걸림)일 때는 409 등 명확한 상태코드 + 메시지로 구분.
  - `GET /api/schedules/{id}/last-result` → `DisplayOutputStore`/가장 최근 `ExecutionResult`(인메모리) 조회, 아직 없으면 204/빈 응답.
  - `GET /api/schedules/{id}/status` → Quartz 기준 다음 실행 예정시각 + 현재 실행중 여부.

**DoD**: API 테스트로 (1) 정규 스케줄과 무관하게 즉시 1회 실행되고 응답에 결과가 동기적으로 담겨 있음, (2) 실행 중 중복 호출 시 거부 응답, (3) `DISPLAY_ONLY` 스케줄의 즉시실행 응답에 가공된 데이터가 포함됨을 확인.

---

### T10. 관리자 REST API — 대상DB/자격증명 설정

- `web/api/TargetDbConfigController`, `CredentialController`: PLAN.md §6 해당 표 엔드포인트.
- 응답 시 비밀번호/자격증명 값은 항상 마스킹(끝 4자 등), 평문 반환 엔드포인트 없음.
- 삭제 시 참조 중인 `schedule_definition`/`action_config`가 있으면 차단 또는 경고.

**DoD**: API 테스트로 CRUD + 마스킹 동작 + 참조 중 삭제 차단 확인. 응답 바디/로그에 평문 비밀값이 노출되지 않음을 테스트로 확인(T5의 fail-fast와 별개로, API 레벨에서도 검증).

---

### T11. 인증 (Spring Security) + CORS 최종 설정

- `admin_user` 기반 로그인(`AuthController` + `SecurityConfig`), BCrypt 비밀번호. 인증 방식(세션 쿠키 vs 토큰)은 SPA가 별도 오리진에서 호출한다는 전제 하에 구현 시점에 확정(세션 쿠키 방식이면 `SameSite`/`Access-Control-Allow-Credentials` 설정 필요).
- `/api/**` 전체를 인증 필요로 보호(로그인/헬스체크 제외) — 서버 렌더링 화면이 없으므로 `/admin/**` 같은 경로는 존재하지 않음.
- T2에서 자리 잡은 `CorsConfig`를 운영 프론트엔드 배포 오리진까지 포함하도록 최종 확정(`app.cors.allowed-origin`을 여러 오리진 허용하도록 확장 필요 시 반영).
- 최초 기동 시 관리자 계정이 하나도 없으면 환경변수(`INITIAL_ADMIN_USERNAME`/`INITIAL_ADMIN_PASSWORD`)로 1회 시드하는 로직(부트스트랩 문제 해결 — 레거시엔 이런 보호가 없었으므로 신규).

**DoD**: 인증 없이 `/api/**` 호출 시 401. 로그인 후 정상 호출. 프론트엔드 dev 서버 오리진에서 실제 로그인 플로우가 CORS 에러 없이 동작. 최초 기동 시 시드 계정으로 로그인 가능함을 통합 테스트로 확인.

---

### T12. 관리자 웹 UI (React SPA, `frontend/`)

- PLAN.md §7의 8개 페이지 구현: Login, ScheduleList, ScheduleForm(동적 폼), RunResult, LastResult, TargetDbConfig, Credential, ChangePassword — T2에서 만든 빈 페이지에 실제 내용 채움.
- `ScheduleForm`은 `action_type` 선택에 따라 `API_CALL`/`DISPLAY_ONLY` 입력 영역을 전환하는 컴포넌트로 구현.
- "지금 실행" 버튼 → T9 API 호출 → 같은 화면(모달/인라인)에서 결과 표시.
- `frontend/src/api/` 클라이언트가 T8~T11의 모든 엔드포인트를 호출하도록 연결, 인증 실패(401) 시 Login 페이지로 리다이렉트하는 공통 처리.

**DoD**: 브라우저로 직접 조작해 로그인 → 스케줄 생성 → cron 반영 확인(T7 상태 API로 다음 실행시각 확인) → "지금 실행"으로 결과 확인 → 대상DB/자격증명 등록 → 전체 플로우가 백엔드 재컴파일 없이, 프론트엔드도 백엔드와 독립적으로 재배포 가능한 구조로 동작함을 수동 검증(화면 스크린샷 또는 조작 로그로 보고).

---

### T13. 설정/비밀값 최종 점검 (부트스트랩 계층)

- `backend/application.yml`의 메타DB 접속정보, `MASTER_ENCRYPTION_KEY`, `INITIAL_ADMIN_*`, `app.cors.allowed-origin`이 전부 환경변수 플레이스홀더로만 존재하고 소스에 실제 값이 없는지 점검.
- `frontend/.env.local`(실제 API URL 등)이 gitignore 처리되어 있는지 점검, `.env.example`에는 플레이스홀더만.
- "env var만 바꿔서 다른 업체에 재배포" 시나리오를 문서(README)로 1페이지 정리(백엔드/프론트엔드 각각 코드 변경/재컴파일 불필요함을 체크리스트로).

**DoD**: 리포지토리 전체에서 실제 비밀값 리터럴 검색(grep) 결과 0건. README 체크리스트 존재(백엔드/프론트엔드 양쪽 다룸).

---

### T14. 테스트 정리 + 회귀 확인

- T1~T13에서 작성된 백엔드 단위/통합 테스트를 모아 `mvn test` 1회 그린 확인.
- 레거시 3가지 실행 패턴(주간 1회+그룹발송 / 평일 30분+개별발송 / 평일 1일1회+개별발송, 각각 파라미터 없음)을 **신규 스케줄 정의 데이터로 재현**하는 시나리오 테스트 최소 1개씩(= "레거시 실행흐름이 포팅됐다"는 것의 실증).

**DoD**: `mvn test` 전체 그린. 레거시 3패턴 각각에 대응하는 시나리오 테스트 3개 존재 및 통과.

---

### T15. 메타DB 마이그레이션 — 스케줄↔대상DB N:M 전환 (PLAN.md §4.3b)

- SQL Server/H2 양쪽에 Flyway `V3__schedule_target_db_multi.sql` 추가: `schedule_target_db_config(schedule_id, target_db_config_id)` 생성(PK 복합키, `schedule_id`는 `ON DELETE CASCADE`), `schedule_definition.target_db_config_id` 컬럼과 그 FK 제약을 제거.
- 아직 운영 전이므로 과거 데이터 백필은 불필요(PLAN.md §4.1 교정 문단) — 다만 로컬/테스트 환경에 시드 데이터가 있다면 마이그레이션 전/후로 테스트가 깨지지 않는지 확인.
- T3에서 만든 기존 `V1__schedule_core.sql`은 수정하지 않는다(Flyway는 적용된 마이그레이션을 수정하지 않는 것이 원칙 — 새 버전 파일로 추가).

**DoD**: SQL Server/H2 양쪽에서 `V1 → V2 → V3` 순서로 깨끗하게 마이그레이션 적용됨(기존 DB 기준 `flyway:migrate` 1회 그린). `schedule_definition`에 더 이상 `target_db_config_id` 컬럼이 없음을 확인. `schedule_target_db_config`에 같은 (schedule_id, target_db_config_id) 중복 삽입 시 PK 위반으로 거부됨을 테스트로 확인.

---

### T16. 도메인/Repository 변경 — `ScheduleTargetDbConfigRepository` + `ScheduleDefinition` 정리

- `domain/schedule/ScheduleDefinition.java`에서 `targetDbConfigId` 필드 제거(§4.1 컬럼 제거에 대응 — 레코드가 더 이상 존재하지 않는 컬럼을 참조하지 않게).
- 신규 `domain/schedule/ScheduleTargetDbConfigRepository` 인터페이스: `findTargetDbConfigIdsByScheduleId(scheduleId) -> List<Long>`, `replaceForSchedule(scheduleId, List<Long> targetDbConfigIds)`(해당 스케줄의 기존 연결을 전량 삭제 후 재삽입 — 트랜잭션 1건).
- `infra/persistence/jdbc`에 `JdbcScheduleTargetDbConfigRepository` 구현, 기존 `JdbcScheduleRepository`에서 `target_db_config_id` 매핑 코드 제거.
- `replaceForSchedule`이 존재하지 않는 `target_db_config_id`를 참조하면 FK 위반으로 명확히 실패하도록(그대로 전파하거나 명시적 예외로 감싸기는 T18에서 API 레벨 400으로 변환).

**DoD**: Repository 테스트로 (1) `replaceForSchedule`이 기존 연결을 덮어쓰기(추가/삭제 모두) 정확히 반영, (2) 스케줄 삭제 시 `schedule_target_db_config` 행이 CASCADE로 같이 삭제, (3) 존재하지 않는 `target_db_config_id`로 연결 시도 시 실패함을 확인.

---

### T17. 실행 엔진 변경 — `ScheduleExecutor` 대상DB 다중 순회 + `ExecutionResult`/`TargetDbExecutionResult`/`DisplayOutput` 확장 (PLAN.md §3.1/§3.1b/§3.3)

1. `ExecutionStatus`에 `PARTIAL_SUCCESS` 추가.
2. 신규 `engine/execution/TargetDbExecutionResult` 값 객체(targetDbConfigId/Name, status, summary, errorMessage).
3. `ExecutionResult`에 `targetDbResults: List<TargetDbExecutionResult>` 필드 추가(기존 필드는 유지, 의미는 "전체 요약"으로 재해석 — PLAN.md §3.1 집계 규칙: 전부 성공 SUCCESS, 전부 실패 FAILURE, 혼재 PARTIAL_SUCCESS).
4. `DisplayOutput`에 `targetDbConfigId`/`targetDbConfigName` 필드 추가. `DisplayOutputStore`의 키를 `scheduleId` 단일값 → `(scheduleId, targetDbConfigId)` 복합키로 변경, `getAllForSchedule(scheduleId) -> List<DisplayOutput>` 추가.
5. `ScheduleExecutor.doExecute()`를 재작성: `ScheduleTargetDbConfigRepository`로 대상DB id 목록을 조회 → **순차 순회**(PLAN.md §3.1b "병렬 아님" 결정) → 대상DB마다 개별 try/catch로 감싸 `StoredProcedureInvoker.invoke(...)` + (변경 없는) `ActionHandler.handle(schedule, spResultRowsForThisTargetDb)` 호출 → 결과를 `TargetDbExecutionResult`로 변환(실패 시 해당 대상DB만 FAILURE로 기록하고 다음 대상DB로 계속 — 부분실패 허용, PLAN.md §3.1b 근거) → `DISPLAY_ONLY`면 `DisplayOutput`에 대상DB 식별정보를 태그해 `DisplayOutputStore`에 적재 → 전체 루프 종료 후 집계 규칙대로 상위 `ExecutionResult` 구성.
6. `ActionHandler`/`ActionOutcome` 인터페이스 자체는 변경하지 않는다(PLAN.md §3.1b "ActionHandler와의 경계" — 대상DB를 아는 책임은 `ScheduleExecutor`에만 둠).

**DoD**:
- 통합 테스트로 (1) 대상DB 전부 성공 → `SUCCESS`, (2) 일부 성공/일부 실패(예: 2개 중 1개가 잘못된 접속정보) → `PARTIAL_SUCCESS`이고 실패한 대상DB만 `targetDbResults`에서 FAILURE로 표시, (3) 전부 실패 → `FAILURE`.
- 한 대상DB의 `StoredProcedureInvoker` 예외가 다른 대상DB 순회를 막지 않음을 테스트로 확인(나머지 대상DB가 실제로 호출됐는지 mock 호출 횟수로 검증).
- `DISPLAY_ONLY` 스케줄이 대상DB 2개를 가질 때 `DisplayOutputStore.getAllForSchedule(id)`가 서로 다른 행 데이터를 가진 2건의 `DisplayOutput`을 반환하고, 어느 한쪽 데이터가 다른 쪽과 섞이지 않음을 확인.
- `credential` 복호화 값이 `targetDbResults[i].summary`/`errorMessage`/로그에 노출되지 않음을 확인(기존 T6 DoD의 연장).

---

### T18. 관리자 REST API 변경 — `targetDbConfigIds` 배열 계약 + run-now/last-result 응답 재구성 (PLAN.md §6)

- `ScheduleController`의 생성/수정 요청 바디를 `targetDbConfigId`(단수) → `targetDbConfigIds: Long[]`(복수)로 변경. 빈 배열이면 400, 존재하지 않는 id가 섞여 있으면 400.
- 목록/단건 조회 응답에 `targetDbConfigIds`와 이름까지 해석한 `targetDbConfigs: [{id, name}]`을 포함(프론트가 N+1 조회 없이 바로 배지를 그릴 수 있게 — PLAN.md §7 ScheduleList).
- 수정 시 `targetDbConfigIds`를 T16의 `replaceForSchedule`로 전량 교체.
- `ScheduleExecutionController`: `run-now` 응답에 `targetDbResults` 포함(T17 그대로 직렬화), `last-result` 응답을 대상DB별로 그룹화된 배열 형태로 재구성.
- `TargetDbConfigController`의 삭제 차단 조건을 `schedule_definition.target_db_config_id` 참조 체크에서 `schedule_target_db_config` 참조 체크로 변경.

**DoD**: API 테스트로 (1) 빈 배열/존재하지 않는 id로 생성·수정 시 400, (2) 정상 생성 후 목록 조회 응답에 `targetDbConfigs` 이름이 올바르게 채워짐, (3) `run-now` 응답이 T17에서 검증한 전체요약+대상DB별 세부결과 구조와 일치, (4) 대상DB 삭제가 참조 중인 스케줄이 있으면 차단됨을 확인.

---

### T19. 프론트엔드 — 반복주기 구조화 입력(`CronBuilder`) 구현 (PLAN.md §9)

- `frontend/src/components/CronBuilder.tsx`: §9.2의 6가지 패턴(일별/월별/년별/요일반복/간격반복/1회성) 각각의 입력 폼 + 각 폼에서 Quartz cron 문자열을 생성하는 순수 TS 함수.
- 역변환 함수: 임의 cron 문자열을 6가지 생성 규칙의 "역" 패턴과 매칭해 구조화 값으로 복원하거나, 매칭 실패 시 `null`(= CUSTOM) 반환.
- "고급: 직접 cron 입력" 토글 — 기존 `ScheduleFormPage.tsx`의 cron 텍스트 입력을 이 모드로 유지(완전히 막지 않음, PLAN.md §9.3).
- `ScheduleFormPage.tsx`에서 cron 텍스트 입력을 `CronBuilder`로 교체: 생성(create) 시 기본 "고급" 모드가 아니라 패턴 선택부터 시작, 수정(edit) 시 로드된 `cronExpression`을 역변환해 매칭되는 패턴 폼을 복원하고 안 되면 고급 모드로 폴백.

**DoD**: 6가지 생성 함수 각각에 대한 단위 테스트(대표값 + 간격반복의 경계 케이스 — PLAN.md §9.2 유의점) + "생성 → 역변환 → 원래 구조화 값과 일치" 왕복 테스트 + 패턴에 맞지 않는 임의 cron 문자열에 대해 역변환이 `null`(CUSTOM)을 반환하고 고급 모드로 폴백함을 테스트로 확인. `CronBuilder`가 생성한 cron 문자열을 실제로 스케줄 생성 API에 보내 백엔드 `CronExpression.isValidExpression` 검증을 통과함을 1회 이상 수동/통합 검증.

---

### T20. 프론트엔드 — 대상DB 다중선택 + 결과 화면 그룹뷰 (PLAN.md §7)

- `frontend/src/components/TargetDbConfigMultiSelect.tsx`: 체크박스 기반 다중 선택, 최소 1개 선택 전 제출 차단(클라이언트 검증, 서버 검증은 T18과 별개로 존재).
- `ScheduleFormPage.tsx`: 기존 단일 `<select>`를 이 컴포넌트로 교체, 저장 시 `targetDbConfigIds` 배열로 전송.
- `ScheduleListPage.tsx`: 각 스케줄 행에 포함된 대상DB 이름을 배지/칩 목록으로 표시(`targetDbConfigs` 응답 필드 사용).
- `RunResultModal`/`LastResultPage`: 상단에 전체 요약(상태 배지: 성공/부분성공/실패, "대상DB N개 중 M개 성공"), 그 아래 대상DB별 섹션(이름 제목 + 상태 배지 + summary/errorMessage + `DISPLAY_ONLY`면 표)으로 재구성.

**DoD**: 브라우저로 직접 조작해 (1) 대상DB 2개를 선택한 스케줄 생성, (2) ScheduleList에 두 대상DB 이름이 배지로 보임, (3) "지금 실행" 결과에 대상DB별 섹션 2개가 분리돼 보이고 서로 데이터가 섞이지 않음, (4) 한쪽 대상DB의 접속정보를 일부러 잘못 입력해 실행 시 부분성공(`PARTIAL_SUCCESS`) 상태와 실패 섹션이 올바르게 표시됨을 수동 검증(스크린샷 또는 조작 로그로 보고).

---

### T21. 회귀 테스트 — 레거시 3패턴 재검증 + 다중 대상DB 시나리오 추가

- T14에서 작성한 레거시 3패턴 시나리오 테스트를 새 `targetDbConfigIds` 배열 계약(단일 원소 배열)에 맞게 갱신 — 동작 자체는 바뀌지 않아야 함(대상DB 1개짜리는 기존과 동일하게 동작).
- 신규 시나리오: 대상DB 2개 + `DISPLAY_ONLY` — 두 대상DB의 `DisplayOutput`이 분리되어 저장되고 행이 섞이지 않음을 확인.
- 신규 시나리오: 대상DB 2개 + `API_CALL` — 한쪽 대상DB의 외부 API 호출이 실패(모킹된 HTTP 서버가 500 반환)하고 다른 쪽은 성공할 때, `run-now` 응답이 `PARTIAL_SUCCESS`와 정확한 `targetDbResults`를 반환함을 엔드투엔드로 확인.

**DoD**: `mvn test` 전체 그린(T14의 레거시 3패턴 + 신규 다중 대상DB 2개 시나리오 포함). `frontend`의 `CronBuilder` 단위 테스트(T19)도 그린. T20의 수동 UI 검증이 완료 보고됨.

---

### T22. WAR 패키징 전환 (PLAN.md §11.1)

- `backend/pom.xml`: `<packaging>jar</packaging>` → `<packaging>war</packaging>`.
- `SchedulerServiceApplication`이 `SpringBootServletInitializer`를 extends하도록 변경하고 `configure(SpringApplicationBuilder builder)`를 오버라이드해 `builder.sources(SchedulerServiceApplication.class)`를 반환. 기존 `main()`/`SpringApplication.run(...)`은 그대로 유지(로컬 jar 실행/개발 경로).
- `spring-boot-starter-tomcat`을 `provided` 스코프로 명시 선언(지금은 `spring-boot-starter-web`의 전이의존성으로 묵시적으로 들어와 있음 — 명시적 `<dependency>` 엔트리를 추가해 스코프를 오버라이드).

**DoD**: `mvn clean package`가 `.war` 파일을 생성함(`target/*.war`). **회귀 확인**: `mvn spring-boot:run`이 WAR 패키징+`provided` 스코프 상태에서도 여전히 내장 톰캣으로 로컬 기동되는지 실제로 실행해 확인(PLAN.md §11.1이 "문서상 근거"로 남겨둔 가정의 실측 검증 — 통과하지 않으면 이 작업은 완료로 보지 않는다). 기존 단위/통합 테스트(T1~T21) 전부 그린 유지.

---

### T23. 빌드 파이프라인 통합 — Maven이 프론트엔드 빌드까지 수행 (PLAN.md §11.2)

- `backend/pom.xml`에 `frontend-maven-plugin`(eirslett) 추가: Node.js/npm 버전을 고정해 설치하고, `workingDirectory`를 `${project.basedir}/../frontend`로 지정해 `npm install` → `npm run build`를 Maven 빌드 생명주기(예: `generate-resources` 단계)에 바인딩.
- `maven-resources-plugin`의 `copy-resources` 실행을 추가해 `frontend/dist/**`를 `${project.build.outputDirectory}/static`(`target/classes/static/`)로 복사 — `src/main/resources/static`에는 커밋하지 않는다(빌드 산출물과 소스 트리 분리, PLAN.md §11.2 근거).
- 두 플러그인 실행 순서가 "npm build 완료 → 그 다음 복사 → 그 다음 war 패키징"이 되도록 Maven 단계(phase) 바인딩을 맞춘다.

**DoD**: Node/npm이 전혀 설치되어 있지 않은 깨끗한 환경(또는 그렇게 흉내 낸 환경)에서 `mvn clean package` 한 번만 실행했을 때 끝까지 성공하고, 생성된 WAR 안(`unzip -l target/*.war` 또는 `jar tf`)에 `WEB-INF/classes/static/index.html`과 프론트 빌드 산출물(JS/CSS)이 포함되어 있음을 확인.

---

### T24. SPA 폴백 라우팅 (PLAN.md §11.3)

- `WebMvcConfigurer.addResourceHandlers`로 `/**`를 `classpath:/static/`에 매핑하고, `PathResourceResolver`를 오버라이드해 요청 리소스가 존재하지 않으면 `classpath:/static/index.html`을 반환하도록 구현.
- `/api/**`는 별도 제외 설정을 추가하지 않는다(PLAN.md §11.3 — `RequestMappingHandlerMapping`이 `SimpleUrlHandlerMapping`보다 먼저 매칭되므로 자연히 배제됨) — 대신 아래 DoD의 회귀 테스트로 이 가정을 검증한다.

**DoD**: 통합 테스트로 (1) 정적 파일로 존재하지 않는 경로(예: `GET /schedules`, 확장자 없음)를 요청하면 `index.html` 내용이 200으로 반환됨, (2) 실제 존재하는 정적 자산(예: 빌드된 JS 파일 경로)을 요청하면 그 파일이 그대로 반환됨(index.html로 대체되지 않음), (3) **인증 없이 `GET /api/schedules`를 호출하면 401이 오고 `index.html`(200)이 오지 않음**(§11.3이 명시한 암묵적 배제 가정의 실측 검증 — 이 테스트가 실패하면 명시적 exclude 설정을 추가해야 함)을 확인.

---

### T25. 프론트엔드 API 베이스 URL 상대경로화 (PLAN.md §11.4)

- `frontend/.env.production` 신설: `VITE_API_BASE_URL=`(빈 값)으로 설정.
- `frontend/src/api/client.ts`는 코드 변경 없이 그대로 두되(`?? "http://localhost:8080"`는 `null`/`undefined`일 때만 fallback하고 빈 문자열은 그대로 사용되므로 의도대로 동작 — PLAN.md §11.4 근거), 로컬 개발용 `.env.local`(`VITE_API_BASE_URL=http://localhost:8080`)은 그대로 유지해 두 모드가 충돌하지 않는지 확인.
- `README`(T27에서 작성)에 "운영 빌드는 상대경로로 API를 호출하므로 Tomcat 컨텍스트 경로가 무엇이든 동작한다"는 설명을 남길 자리를 마련.

**DoD**: `npm run build`(production 모드)로 생성된 산출물을 WAR에 포함시켜 Tomcat에 **컨텍스트 경로가 있는 이름으로**(예: `scheduler-service.war` → `/scheduler-service`) 배치했을 때, 프론트엔드가 하드코딩된 `localhost`가 아니라 상대경로로 API를 정상 호출함을 수동으로 1회 확인(§11.4가 주장하는 "컨텍스트 경로 무관 동작"의 실측 검증).

---

### T26. 로컬 개발 워크플로 회귀 없음 확인 (T22~T25 종합)

- T22~T25의 변경(WAR 패키징, provided 스코프, 빌드 파이프라인, 리소스 폴백, API 베이스 URL)을 모두 적용한 뒤, 기존 로컬 개발 워크플로 두 가지가 전부 깨지지 않았는지 종합 확인:
  1. **분리 실행**: 백엔드는 `mvn spring-boot:run`(8080), 프론트는 `npm run dev`(5173, `.env.local`의 절대 URL 사용) — CORS 포함 정상 동작.
  2. **통합 WAR 로컬 검증**: `mvn clean package`로 만든 WAR를 로컬 Tomcat(또는 `mvn tomcat7:run-war`류 로컬 검증 수단)에 올려 브라우저로 로그인→스케줄 CRUD→지금실행 전체 플로우가 단일 오리진에서 동작하는지 확인.
- 두 워크플로 중 하나라도 깨졌으면 이 작업은 완료로 보지 않고 원인이 된 선행 작업(T22~T25)으로 돌아가 수정한다.

**DoD**: 위 두 가지 로컬 워크플로가 모두 수동 검증으로 통과(조작 로그 또는 스크린샷으로 보고). 기존 `mvn test` 전체 그린 유지.

---

### T27. README에 Tomcat 배포 가이드 작성 (PLAN.md §11)

- 레포 루트 또는 `backend/README.md`에 "WAR 빌드 → Tomcat 배치 → 환경변수 설정 → 기동" 순서의 운영 배포 가이드를 작성:
  1. `mvn clean package`로 WAR 빌드(프론트 빌드가 자동 포함됨 — T23).
  2. 생성된 WAR를 `$CATALINA_HOME/webapps/`에 배치(권장: `ROOT.war`로 이름 변경해 컨텍스트 경로를 `/`로 — PLAN.md §11.4 근거도 같이 설명).
  3. `$CATALINA_HOME/bin/setenv.sh`(Linux/Mac) 또는 `setenv.bat`(Windows)에 `JAVA_OPTS`로 `-DDB_URL=...` 등 환경변수를 시스템 프로퍼티로 주입하는 예시(PLAN.md §11.6의 스니펫 그대로 가져와 실제 값 예시와 주석 포함) — 이 파일은 Tomcat 설치본에 두는 것이라 레포에는 커밋하지 않는다는 점도 명시.
  4. Tomcat 기동/재기동 방법과, 기동 후 확인 포인트(헬스체크 URL, 초기 관리자 계정 로그인 등).
- 값에 특수문자(`+`/`=` 등 base64 값)가 들어갈 때의 셸/배치 인용 주의사항 한 줄 포함.

**DoD**: README를 처음 보는 사람이 그대로 따라가면 WAR를 빌드해서 Tomcat에 배치하고 환경변수를 설정해 기동까지 할 수 있는 수준으로 작성됨(리뷰어가 가이드만 보고 실제로 재현 가능한지 확인).

---

## Future (이번 범위 제외, 별도 라운드)

### T28 (future). 실행이력 테이블 설계 + 마이그레이션 + `JdbcExecutionResultSink`

- PLAN.md §3.1/§3.2, §10(TBD 명시)에 따라 실행이력 테이블 스키마를 별도로 설계(감시 서버 원칙: 상태/요약만, SP 전체 결과행 보존 금지. 실패 기록 포함 — 대상DB별 세부결과(`TargetDbExecutionResult`, T17)도 영속화 대상에 포함할지 이 라운드에서 결정).
- `ExecutionResultSink`의 새 구현(`JdbcExecutionResultSink` 등)을 추가하고 `LoggingExecutionResultSink`와 조합(`CompositeExecutionResultSink`) 또는 교체.
- **착수 조건**: 이번 TASKS.md 범위(T1~T27) 완료 후, 별도로 이력 테이블 스키마 설계 라운드를 거친 뒤 진행.
