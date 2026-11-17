# REVIEW.md — sched-reviewer 1차 리뷰 (T1~T14 완료 시점)

> 리뷰 대상: `643b01c`(T14, HEAD)까지의 전체 커밋(`5709e96`~`643b01c`, 17개). `git log -p --all`로 전체 diff 확인, 작업 디렉터리 코드/설정/문서를 직접 열람. 레거시 참고 프로젝트(`C:\Users\USER\Desktop\scheduler-test\scheduler-test`)와 대조.
>
> **결론 먼저: blocking 이슈 없음.** major 2건(둘 다 "인증된 관리자"라는 전제 안에서의 방어심층 결함 — 외부 미인증 공격자가 직접 악용 가능한 경로는 아님), minor 3건, 문서-구현 불일치 1건을 발견했다. 전체적으로 PLAN.md/TASKS.md가 약속한 아키텍처 경계(ExecutionResult/Sink, action_type 2종, SP 파라미터화, 저장소 비의존)는 실제 코드에 정확히 구현되어 있었다.

---

## Major

### M1. `StoredProcedureInvoker`가 SP명을 SQL 문자열에 그대로 이어붙임 — SQL 인젝션 표면

> **수정 완료**(커밋 `02fee04`) — `engine/sp/SqlIdentifierValidator` 신규(SQL Server 식별자 화이트리스트 정규식). `ScheduleController.create`/`update`와 `StoredProcedureInvoker.invoke()` 양쪽에서 `spName` 검증(방어심층). 리뷰가 제시한 재현 페이로드(`"sp_test; DROP TABLE some_table; --"`)로 `POST /api/schedules` 시도 시 400 확인하는 테스트 포함. `mvn test` 86/86 그린.

- **위치**: `backend/src/main/java/com/scheduler/engine/sp/StoredProcedureInvoker.java:28-31`
  ```java
  String placeholders = ... ;
  String sql = "EXEC " + spName + placeholders;
  ...
  CallableStatement stmt = conn.prepareCall(sql);
  ```
- **문제**: `boundParams`(SP 파라미터 값)는 `?` 플레이스홀더 + `setObject`로 정상적으로 파라미터화되어 있다. 그런데 **SP 이름(`spName`) 자체는 문자열 결합으로 SQL 텍스트에 직접 삽입**된다. `spName`은 `schedule_definition.sp_name`에서 오고, 그 값은 `ScheduleRequest.spName`(`web/api/dto/ScheduleRequest.java:13`)에 `@NotBlank`만 걸려 있을 뿐 — 식별자 형식(영문/숫자/언더스코어/스키마 접두사 등) 검증이나 화이트리스트가 전혀 없다(`ActionConfigRequestValidator`도 `action_config`만 검증하고 `sp_name`은 건드리지 않음).
- **재현/확인 방법**:
  1. `POST /api/schedules`에 유효한 JWT로 `spName: "sp_test; DROP TABLE some_table; --"` (또는 SQL Server가 세미콜론으로 배치 실행을 허용하는 다른 페이로드)를 넣어 스케줄 생성.
  2. `POST /api/schedules/{id}/run-now` 호출 시 `StoredProcedureInvoker.invoke(...)`가 `"EXEC sp_test; DROP TABLE some_table; --"`를 그대로 `prepareCall`에 넘긴다 — `target_db_config`가 가리키는 **업체 업무 DB**(ERP 운영 DB일 수 있음)에서 임의 SQL이 실행된다.
  3. 코드만 봐도 확정적: `sql` 변수가 `spName`을 escape/검증 없이 직접 포함.
- **영향 범위 평가**: 이 경로는 `/api/schedules` 생성/수정 API를 호출할 수 있는 **인증된 ADMIN JWT 소유자**만 접근 가능하다. 즉 외부 미인증 공격자가 바로 쓸 수 있는 구멍은 아니다. 하지만:
  - PLAN.md/TASKS.md가 명시적으로 "SP 파라미터화"를 "파라미터는 바인딩, SP명은 신뢰 가능한 식별자"로 설계했는데(§4.1 "리터럴 아님"이라는 주석 — 즉 `sp_name`은 자유 입력 문자열이 아니라 실제 DB object 식별자여야 한다는 의도), 구현은 이 식별자를 검증 없이 SQL에 꽂는다. 이건 리뷰 지시에서 사용자가 명시적으로 지목한 "SP명/파라미터가 사용자 입력으로 조립되는 구간"에 정확히 해당.
  - `target_db_config`의 DB 계정이 ERP 운영 계정(레거시 예시로는 광범위 권한의 `erpUser`류)일 가능성이 높아, 관리자 계정 탈취/내부자 위협 시 피해 반경이 "메타DB"가 아니라 "업체 업무 DB 전체"로 확대된다.
- **권장 수정 방향(참고용, 리뷰어는 코드 수정하지 않음)**: `sp_name` 저장/수정 시 SQL Server 식별자 형식(`^[A-Za-z0-9_\.\[\]]+$` 등) 화이트리스트 검증을 `ScheduleController`/`ActionConfigRequestValidator` 레벨에 추가하거나, `StoredProcedureInvoker`에서 `quotename()` 또는 식별자 검증 유틸을 통하게 한다.

### M2. `Masking.lastFour`가 4자 이하 비밀값을 마스킹 없이 그대로 노출

> **수정 완료**(커밋 `9298c10`) — 길이 4자 이하인 값은 전체를 `*`로 마스킹하도록 분기 추가, 4자 초과 값의 기존 "끝 4자 노출" 동작은 유지. `MaskingTest` 단위테스트 + `TargetDbConfigControllerTest`/`CredentialControllerTest`에 `"4002"`(4자) 재현 케이스 추가, 응답이 `"****"`이고 평문을 포함하지 않음을 확인. `mvn test` 91/91 그린.

- **위치**: `backend/src/main/java/com/scheduler/web/api/Masking.java:12-19`
  ```java
  static String lastFour(String value) {
      if (value == null || value.isEmpty()) return "";
      int visible = Math.min(4, value.length());
      String tail = value.substring(value.length() - visible);
      return "*".repeat(value.length() - visible) + tail;
  }
  ```
- **문제**: 길이가 4 이하인 비밀값은 `visible == value.length()`가 되어 `"*".repeat(0)` + 전체 문자열 = **평문 그대로 반환**된다. PLAN.md §4.4/§6은 "끝 4자만 노출, 나머지는 마스킹"을 전제하는데, 짧은 비밀값(레거시 `FCM_SECURITY_CODE = "4002"`처럼 4자리 코드가 실제 운영 사례로 존재했다)에 대해서는 마스킹이 전혀 동작하지 않아 `GET /api/config/credentials`, `GET /api/config/target-db` 응답에 평문이 그대로 노출된다.
- **재현/확인 방법**: `credential`을 `value="4002"`(4자)로 생성 후 `GET /api/config/credentials` 호출 → 응답 `maskedValue` 필드가 `"4002"`(마스킹 0글자)로 나옴. T10 테스트(`CredentialControllerTest`)는 테스트에 쓴 값들이 4자보다 길어서 이 경로가 드러나지 않았던 것으로 보임(`mvn test` grep으로 짧은 값 케이스가 없음을 확인).
- **영향**: 마스킹 API는 인증된 ADMIN만 호출 가능하므로 외부 노출 경로는 아니지만, "짧은 비밀값은 평문 노출"이라는 설계 의도와 명백히 다른 동작이고, 테스트가 이 경계(4자 이하)를 커버하지 않아 회귀로 재발하기 쉽다.

---

## Minor

### N1. `BodyTemplateRenderer`가 SP 컬럼 값을 JSON 템플릿에 이스케이프 없이 치환 — 바디 깨짐/필드 주입 가능

> **수정 완료**(커밋 `e113b68`) — 바디 전용 `renderJson(...)` 신규 추가(Jackson으로 치환값 JSON 이스케이프, `${COLUMN}`/`${cred:NAME}` 둘 다 적용). 헤더 값은 JSON이 아니므로 기존 `render()`(무이스케이프)를 그대로 유지, `ApiCallActionHandler`가 바디에만 `renderJson` 사용하도록 분리. 리뷰 재현 페이로드(`EA_TITLE = "a\",\"x\":\"injected"`)로 필드 주입 없이 올바르게 복원됨을 단위테스트+실제 HTTP 호출 테스트로 확인. `mvn test` 96/96 그린.

- **위치**: `backend/src/main/java/com/scheduler/engine/action/BodyTemplateRenderer.java:34, 41-43`
- **문제**: `${COLUMN}` 치환 시 `stringify(row.get(name))`이 `toString()`만 하고 JSON 특수문자(`"`, `\`, 개행)를 이스케이프하지 않는다. `action_config.bodyTemplate`는 PLAN.md §5 예시대로 JSON 문자열(`"{\"title\":\"${EA_TITLE}\"}"`)로 작성되는 것이 일반적인데, SP가 반환하는 컬럼 값(예: 직원 이름, 문서 타이틀 등 — 엔드유저가 ERP에 입력한 값일 수 있음)에 `"`나 `\`가 포함되면 결과 바디의 JSON 구조가 깨지거나(다운스트림 API가 파싱 실패) 의도치 않은 JSON 필드가 주입될 수 있다.
- **재현**: `bodyTemplate: "{\"title\":\"${EA_TITLE}\"}"`에서 SP가 `EA_TITLE = "a\",\"x\":\"injected"`인 행을 반환하면 렌더링 결과가 `{"title":"a","x":"injected"}`가 되어 원래 없던 `x` 필드가 추가된다.
- **영향**: 공격자가 직접 트리거하긴 어렵지만(업무 DB의 SP 결과 데이터에 간접 의존), "다운스트림 API 호출 바디가 깨질 수 있다"는 실제 운영 버그 소지가 있다. 레거시 코드가 문자열 결합으로 FCM JSON을 만들던 것과 동일한 패턴을 그대로 일반화했을 뿐이라 "레거시 로직 보존"이라는 설계 의도에는 부합하지만, 범용 임의 API 호출 구조로 일반화하면서 이 위험도 함께 일반화됐다는 점은 언급할 가치가 있다.

### N2. PLAN.md §4.1/§8의 "표준 cron" 서술이 Quartz 고유 규칙(day-of-month/day-of-week 중 하나는 `?`)을 언급하지 않음 — 문서-구현 불일치 (orchestrator 사전 지적사항)

- **위치**: `.team/PLAN.md:196`("표준 cron"), `:384-385`(레거시 매핑표, `MON-FRI` 예시) / 실제 구현: `ScheduleController.validateCron`(`org.quartz.CronExpression.isValidExpression`), `engine/quartz/ScheduleRegistrar`.
- **확인 내용**: PLAN.md 어디에도 깨지는 리터럴 cron 문자열이 실제로 박혀있지는 않다(현재 문서에 `"0 0 9 * * MON"` 같은 완전한 6필드 cron 리터럴은 없음 — grep으로 확인). 다만 §4.1이 `cron_expression`을 "표준 cron(요일 포함 가능)"이라고만 서술하고, §8 매핑표도 "요일 조건을 cron 자체로 표현(예: `MON-FRI`)"이라고만 적어, **Quartz cron이 Unix cron과 달리 "일(day-of-month)"과 "요일(day-of-week)" 중 하나는 반드시 `?`여야 한다**는 고유 제약을 전혀 언급하지 않는다. T7 작업 중(LOG.md T7 섹션) worker가 실제로 이 규칙에 걸려 테스트 cron(`"0 0 9 * * MON"` 류)이 전부 파싱 실패했고, `"0 0 9 ? * MON"`으로 교정하면서 "PLAN.md 문서 자체는 고치지 않고 LOG에만 기록한다"고 self-flag한 바 있다.
- **영향**: 실제 동작(API가 `CronExpression.isValidExpression`으로 검증해 깨지는 cron은 400으로 거부)은 올바르다 — 런타임 버그는 아니다. 하지만 PLAN.md를 읽고 cron을 입력할 미래의 운영자/개발자가 "표준 cron"이라는 문구만 보고 Unix 5필드 cron(요일 필드에 `*`와 숫자 동시 지정 가능한 문법)을 가정하면 400을 반복해서 받게 된다. 문서에 이 Quartz 고유 규칙 한 줄을 추가하는 것이 바람직.
- **참고**: orchestrator가 LOG.md 마지막 항목(2026-11-16)에서 이 점을 리뷰에 포함시키라고 직접 지시했음.

### N3. `run-now`/`last-result` 응답이 실패한 실행에도 "이전 성공 실행"의 `DisplayOutput`을 그대로 붙여 반환할 수 있음

> **수정 완료**(커밋 `30537d4`) — `ScheduleExecutionController`에 `displayOutputFor(id, result)` 헬퍼 추가, `ExecutionResult.status()`가 `SUCCESS`가 아니면 무조건 `null` 반환하도록 `runNow`/`lastResult` 둘 다 교체. 실제로 SP를 `DROP`해서 다음 실행을 진짜로 실패시킨 뒤 응답의 `displayOutput`이 `null`임을 확인하는 테스트 추가(목업 아님). `mvn test` 97/97 그린.

- **위치**: `backend/src/main/java/com/scheduler/web/api/ScheduleExecutionController.java:49-61` — `displayOutputStore.get(id)`를 `ExecutionResult`의 성공/실패와 무관하게 항상 조회해서 응답에 포함.
- **문제**: `DisplayOutputStore`는 `DisplayOnlyActionHandler`가 성공했을 때만 갱신된다(`ScheduleExecutor.doExecute`). 만약 스케줄이 `DISPLAY_ONLY`인데 이번 실행은 SP 호출 단계에서 실패했다면, `ExecutionResult.status = FAILURE`이지만 응답의 `displayOutput`에는 **이전에 성공했던 실행의 오래된 결과**가 그대로 담겨 나간다. "이번 실행 결과"와 "화면에 보이는 데이터"가 시점상 불일치할 수 있다.
- **영향**: 기능 오류라기보다 UX 모호함에 가깝다(프론트는 `status`를 보고 실패 처리하면 되므로 치명적이지 않음). 다만 PLAN.md §7 LastResult 화면의 "영속 이력 아님" 안내 문구만으로는 "이 displayOutput이 지금 이 실행의 결과가 아니라 과거 것일 수 있다"는 걸 사용자가 알기 어렵다. `generatedAt` 필드로 구분 가능하긴 하지만 화면에서 적극적으로 보여주지 않으면 혼동 소지.

---

## 계획 부합 여부 — 확인된 사항 (문제 없음)

다음은 PLAN.md/TASKS.md가 약속한 것과 실제 구현이 정확히 일치함을 코드로 직접 확인한 항목들이다:

- **ExecutionResult/Sink 경계**: `ExecutionResult`(불변 record), `ExecutionResultSink` 인터페이스, `LoggingExecutionResultSink`(SLF4J만, DB 적재 없음) 모두 `engine/execution` 패키지에 있고 `domain`/`infra` 패키지에 대한 의존이 없다. 실행이력 테이블이 실제로 존재하지 않음(`V1__schedule_core.sql`에 스케줄/파라미터/대상DB/자격증명/관리자계정 5개 테이블만 존재, grep으로 확인) — TBD 원칙이 지켜짐.
- **action_type 2종 모두 동작**: `ApiCallActionHandler`(PER_ROW/GROUP_BY 분기, 임의 URL/메서드/헤더/바디 — FCM 하드코딩 없음), `DisplayOnlyActionHandler`(컬럼 선택 후 `DisplayOutput` 변환, 외부 호출 없음) 둘 다 `ScheduleExecutor`가 `Map<ActionType, ActionHandler>`로 분기해서 실제로 호출.
- **SP 파라미터화(파라미터 없음/있음 둘 다)**: `StoredProcedureInvoker.invoke`가 `boundParams.isEmpty()`일 때 플레이스홀더 없는 `EXEC spName`을, 있을 때 `EXEC spName ?,?,...`을 모두 처리. `SpParameterResolver`가 `LITERAL`/`CURRENT_DATE`/`CURRENT_DATETIME` 세 가지 `value_source`를 해석. `LegacyPatternScenarioTest`의 3개 시나리오 모두 `schedule_parameter` 0건(파라미터 없는 EXEC)으로 레거시 3패턴을 재현하고 있어 "파라미터 없는 경로"가 실제로 검증됨 — 다만 "파라미터 있는 EXEC" 경로는 레거시 3패턴 재현 테스트에는 없고(레거시 자체가 파라미터 없었으므로 당연), T6의 `ScheduleExecutorTest`/T9의 실제 SP 라운드트립 테스트 쪽에서 커버되는 것으로 확인됨.
- **비밀값 하드코딩 없음**: 레거시 `Config.java`의 실제 운영값(`218.38.64.229`, `192.168.80.27`, `erpUser`/`erpPasswd`, `f1s-fcm-9f3k2m8x4v1w7q5n`, FCM 포트 `4220`/`4110`, 시큐리티코드 `4002`/`4000`)을 레포 working tree 전체 + `git log -p --all`(전체 커밋 히스토리)에서 grep — **0건**. `.team/PLAN.md`/`RESEARCH.md`/`LOG.md`/`README.md`에 남아있는 "iPlusERP"는 일반명사 레이블일 뿐 실제 접속정보가 아님을 직접 확인(T13 로그가 주장한 내용과 일치).
- **관리자 엔드포인트 인증**: `SecurityConfig`가 `/api/auth/login`, `/api/health`, 모든 `OPTIONS` 요청만 `permitAll()`로 열어두고 나머지 `anyRequest().authenticated()` — 스케줄/파라미터/실행/대상DB/자격증명/비밀번호변경 컨트롤러 전부 보호 대상에 포함됨을 코드로 확인(별도 `permitAll` 예외 없음).
- **JWT 구현**: 서명키(`JWT_SIGNING_KEY`)와 암호화 마스터키(`MASTER_ENCRYPTION_KEY`)가 분리되어 있고, 둘 다 미설정/비-base64/길이오류 시 fail-fast. 만료는 `Jwts.builder().expiration(...)` + `Jwts.parser()`가 만료된 토큰에 `ExpiredJwtException`(→ `JwtException` 상위 캐치)을 던져 `InvalidJwtException`으로 변환되고 필터가 SecurityContext를 비워 401로 귀결 — 레거시의 "서명 유효하지만 만료된 토큰"을 디코딩해서 재사용하는 패턴과는 무관(해당 패턴은 foodlink-service CLAUDE.md의 사례이며 이 프로젝트와는 다른 레포/다른 설계임 — 혼동 주의).
- **동적 스케줄 입력값 검증**: cron은 `CronExpression.isValidExpression`으로 검증됨. `action_config`는 `action_type`별 필수 필드 검증(`ActionConfigRequestValidator`). 파라미터 리터럴은 `SpParameterResolver.resolve`를 재사용해 타입 불일치를 400으로 변환. **단, SP명 자체의 검증은 없음 — M1 참고.**

---

## 종합

- **Blocking: 없음.**
- **Major: 2건** (M1 SP명 SQL 인젝션, M2 짧은 비밀값 마스킹 실패) — 둘 다 "인증된 ADMIN"이라는 전제 안에서 발생하는 방어심층 결함이라 즉시 서비스 장애/외부 침해로 이어지는 것은 아니지만, PLAN.md가 명시적으로 걱정한 지점(SP명 조립, 비밀값 노출)에 실제로 존재하는 결함이므로 다음 라운드에서 수정 권장.
- **Minor: 3건** (N1 JSON 템플릿 이스케이프 누락, N2 cron 문서화 공백, N3 run-now/last-result의 displayOutput 시점 불일치).
- TASKS.md DoD 기준으로는 T1~T14 전부 LOG.md가 주장하는 대로 구현되어 있음을 코드 레벨에서 직접 확인했고, 테스트 스위트 실행(81/81)은 이번 리뷰에서 재실행하지 않았다(Docker/Testcontainers 필요, 정적 코드 리뷰로 범위를 좁힘 — 필요 시 별도로 `mvn test` 재실행 권장).

---

## 2차 리뷰 (다중 대상DB + cron 빌더, 커밋 `f8aa871^..194daac`)

> 리뷰 대상: 설계 커밋 `f8aa871`부터 T21(`194daac`)까지 8개 커밋. `.team/PLAN.md`(§0 2026-11-16 교정 문단, §3.1/§3.1b/§3.3, §4.3b, §5, §6, §7, §9), `.team/TASKS.md`(T15~T21), `.team/LOG.md`(T15~T21 섹션, 2026-11-16/17)를 전부 읽고 `git diff f8aa871^..HEAD`(49개 파일, +2127/-247)로 실제 변경분을 전부 확인. 1차 리뷰의 M1(SP명 SQL 인젝션)·M2(짧은 비밀값 마스킹) 수정이 이번 라운드의 신규 코드(신규 Repository/Controller)에서 재발했는지도 별도로 확인했다.
>
> **결론 먼저: blocking 이슈 없음.** 1차 리뷰 때 지적한 M1/M2 패턴은 이번에 추가된 코드에서 재발하지 않았다(아래 "재발 여부 확인" 참고). 다만 이번 라운드 자체의 신규 결함으로 major 1건(`targetDbConfigIds` 배열의 중복 id 미검증 → 500), minor 2건(스케줄 생성/수정과 대상DB 연결 교체 간 원자성 없음, `executeForTargetDb`의 불필요한 중복 조회)을 발견했다.

### Major

#### M4. `targetDbConfigIds` 배열에 중복 id가 섞이면 400이 아니라 500 — 배열 검증이 "존재 여부"만 다루고 "중복"은 다루지 않음

- **위치**: `backend/src/main/java/com/scheduler/web/api/ScheduleController.java:144-151`(`validateTargetDbConfigIds`), `backend/src/main/java/com/scheduler/infra/persistence/jdbc/JdbcScheduleTargetDbConfigRepository.java:38-60`(`replaceForSchedule`).
- **문제**: PLAN.md §4.3b는 "PK는 `(schedule_id, target_db_config_id)` 복합키(같은 대상DB를 같은 스케줄에 중복 선택하는 것을 DB 레벨에서 방지)"라고 명시한다. 실제로 DB 레벨 방지는 맞고(`ScheduleTargetDbMultiMigrationTest`가 중복 insert의 PK 위반을 확인) — 하지만 그 "DB 레벨 방지"가 **API 레벨에서 깔끔한 400으로 변환되지 않는다**. `validateTargetDbConfigIds`는 `targetDbConfigRepository.findById(id).isEmpty()`로 "존재하지 않는 id"만 걸러내고, 중복 원소 자체는 걸러내지 않는다(`@NotEmpty`도 중복을 막지 못함). `replaceForSchedule`은 받은 리스트를 그대로 `batchUpdate`에 넘기므로, 같은 id가 두 번 들어오면 두 번째 INSERT가 복합 PK 위반으로 `DuplicateKeyException`(`DataIntegrityViolationException`의 서브타입)을 던진다. `ApiExceptionHandler`(`web/api/ApiExceptionHandler.java`)에는 이 예외에 대한 핸들러가 없어 Spring Boot 기본 에러 핸들러로 떨어져 **500**이 나간다.
- **재현/확인 방법**:
  1. 유효한 JWT로 `POST /api/schedules`에 `targetDbConfigIds: [5, 5]`(존재하는 같은 id 두 번)를 포함한 바디 전송.
  2. `validateTargetDbConfigIds`는 두 원소 모두 `findById`가 존재를 반환하므로 통과.
  3. `scheduleTargetDbConfigRepository.replaceForSchedule(savedId, [5, 5])`가 `(savedId, 5)`를 두 번 insert 시도 → 두 번째 삽입에서 PK 위반 → 핸들러 없는 예외 → 500.
  4. 수정(`PUT /api/schedules/{id}`)도 동일 코드경로(`validateTargetDbConfigIds` + `replaceForSchedule`)를 타므로 같은 방식으로 재현된다.
- **영향 평가**: React SPA의 `TargetDbConfigMultiSelect`(체크박스 토글 방식)는 구조적으로 중복을 만들 수 없으므로, 공식 프론트엔드 경로로는 이 버그에 도달하지 않는다 — 실제로는 API를 직접 호출하는 경로(수동 테스트, 향후 통합, Postman 등)에서만 트리거된다. 그럼에도 "배열 검증이 모든 경로에서 일관되는지"를 명시적으로 요청받은 항목이고, 테스트 스위트(`ScheduleControllerTest`)에도 빈 배열·존재하지 않는 id 케이스는 있지만 중복 id 케이스는 없어(grep으로 확인, `createWithEmptyTargetDbConfigIdsIsBadRequest`/`createWithNonExistentTargetDbConfigIdIsBadRequest`만 존재) 이 경계가 테스트로 커버되지 않은 채 남아있다. 500 자체가 응답 바디에 평문 비밀값이나 스택트레이스를 노출하지는 않지만(Spring Boot 기본값), API 계약 관점에서 "검증 가능한 입력 오류"가 "서버 오류"로 분류되는 것은 명백한 결함이다.
- **권장 수정 방향(참고용)**: `validateTargetDbConfigIds`에서 `targetDbConfigIds`에 `Set`으로 변환했을 때 크기가 줄어들면(중복 존재) 400을 던지거나, 아예 `replaceForSchedule` 호출 전에 `List.copyOf(new LinkedHashSet<>(ids))`로 묵묵히 중복 제거하는 두 방법 중 하나를 택하면 된다(전자가 "조용히 다른 동작을 하지 않는다"는 원칙에 더 맞는다).
- **수정 완료**(`93be02d`): `validateTargetDbConfigIds`가 `LinkedHashSet` 변환 후 크기 비교로 중복을 400으로 명시 거부(권장안의 전자 방식). `createWithDuplicateTargetDbConfigIdsIsBadRequest`/`updateWithDuplicateTargetDbConfigIdsIsBadRequest`로 create/update 양쪽 재현 시나리오 테스트 확인, `mvn clean test` 118/118 그린.

### Minor

#### N4. 스케줄 생성/수정과 대상DB 연결 교체(`replaceForSchedule`)가 하나의 트랜잭션으로 묶여 있지 않음

- **위치**: `backend/src/main/java/com/scheduler/web/api/ScheduleController.java:83-89`(`create`), `:102-108`(`update`).
- **문제**: `create`/`update` 모두 `scheduleRepository.save(...)`(또는 `update(...)`)와 `scheduleTargetDbConfigRepository.replaceForSchedule(...)`를 **별개의 두 호출**로 순서대로 실행한다. `replaceForSchedule` 자체는 `@Transactional`이 붙어 있어 그 내부(DELETE+batch INSERT)는 원자적이지만, "스케줄 행 저장"과 "대상DB 연결 교체" 사이를 묶는 상위 트랜잭션은 없다. 두 호출 사이에 `replaceForSchedule`이 실패하면(위 M4의 중복 id 케이스, 또는 검증 통과 후 다른 요청이 그 사이에 해당 `target_db_config`를 삭제하는 TOCTOU) `schedule_definition` 행은 이미 커밋된 채로 남고, 그 스케줄은 대상DB가 0개인 상태가 된다 — PLAN.md §4.3b가 "스케줄 생성/수정 시 최소 1개 이상의 대상DB가 선택되어야 한다(0개인 상태는 허용하지 않는다)"고 명시한 불변조건이 DB에 일시적으로 깨진 채 남을 수 있다.
- **영향**: 발생 조건이 좁다(동시성 경합 또는 위 M4 같은 다른 버그와 겹쳐야 함)는 점에서 심각도는 낮다. 다만 `GenericScheduleJob`/`ScheduleExecutor`가 이 상태의 스케줄을 만나면 `targetDbResults`가 빈 리스트인 채로 `FAILURE`("선택된 대상DB가 없음")를 조용히 반복 기록하게 되어, 사용자가 관리자 화면에서 "분명히 대상DB를 선택했는데 계속 실패한다"는 혼란스러운 상태를 겪을 수 있다.
- **수정 완료**(`aef01fd`): `create`/`update`에 `@Transactional` 추가. 신규 `ScheduleTargetDbTransactionTest`(`@MockitoSpyBean`으로 `replaceForSchedule`이 던지도록 스텁)가 create 경로에서 스케줄 행이 전혀 커밋되지 않음, update 경로에서 원본 값이 그대로 롤백됨을 확인(수정 전 먼저 재현 실패를 확인한 뒤 수정). `mvn clean test` 120/120 그린.

#### N5. `ScheduleExecutor.executeForTargetDb`가 같은 `target_db_config`를 대상DB당 두 번 조회

- **위치**: `backend/src/main/java/com/scheduler/engine/execution/ScheduleExecutor.java:131-158`.
- **문제**: `targetDbConfigName`을 구하기 위해 `targetDbConfigRepository.findById(targetDbConfigId)`를 먼저 호출(실패 시 `"target-db-" + id`로 폴백)하고, 바로 이어서 `try` 블록 안에서 **같은 id로 다시** `targetDbConfigRepository.findById(targetDbConfigId)`를 호출해 `orElseThrow`한다. 기능적으로 틀린 것은 아니지만(이름 조회가 실패해도 실행 자체는 계속되고, 두 번째 조회가 진짜 존재 검증을 담당), 대상DB가 N개면 매 실행마다 불필요하게 2N번의 DB 조회가 발생한다. 또한 두 조회 사이의 아주 좁은 시간 창에서 해당 `target_db_config`가 삭제되면(동시에 다른 관리자가 그 대상DB를 지우려는, TargetDbConfigController의 참조-차단이 걸려 있어 실제로는 거의 불가능하지만) 첫 조회는 이름을 얻고 두 번째 조회는 `NoSuchElementException`을 던지는 미세한 비일관 상태가 가능하다.
- **영향**: 순수 성능/미화 이슈에 가깝다 — 대상DB 수가 "소수"라는 §0 전제(단일 업체, 보통 1~2개)에서는 체감 영향이 거의 없다. 심각도를 minor로 유지.
- **수정 완료**(`57d9b03`): `findById`를 한 번만 호출해 `Optional<TargetDbConfig>`를 지역변수로 재사용, 이름 조회 실패 시 폴백 동작은 그대로 유지. 순수 리팩터링이라 별도 테스트 없이 기존 `ScheduleExecutorTest` 전체 통과로 동작 불변 확인.

### N:M 전환 정합성 — 확인된 사항 (문제 없음)

- **마이그레이션(V3) 정합성**: `schedule_target_db_config` PK가 `(schedule_id, target_db_config_id)` 복합키로 생성되어 있고(`ScheduleTargetDbMultiMigrationTest`가 중복 insert의 PK 위반을 실제로 확인), `schedule_id`는 `ON DELETE CASCADE`, `target_db_config_id`는 `ON DELETE` 절이 없어 기본 `NO ACTION`(참조 중인 `target_db_config` 삭제를 DB 레벨에서도 차단) — PLAN.md §4.3b가 요구한 정책과 SQL Server/H2 양쪽 마이그레이션 파일이 정확히 일치. `schedule_definition.target_db_config_id` 컬럼과 그 FK 제약도 V3에서 함께 제거되어 있고, 사전 데이터 백필이 없는 것도 "아직 운영 전"이라는 PLAN.md 문서화된 결정과 일치(운영 전이므로 blocking 아님).
- **스케줄 삭제 CASCADE**: `ScheduleTargetDbConfigRepositoryTest`(T16)가 스케줄 삭제 시 `schedule_target_db_config` 행이 CASCADE로 같이 삭제됨을 확인.
- **대상DB 삭제 차단**: `TargetDbConfigController.delete`가 `schedule_target_db_config` 참조 체크(`findScheduleIdsByTargetDbConfigId`)로 변경되어 있고(기존 단일 FK 참조 체크에서 올바르게 전환됨), DB 레벨 `NO ACTION` FK가 방어심층으로 같이 작동.
- **순차 실행 + 부분실패 허용**: `ScheduleExecutor.doExecute`가 `for` 루프로 대상DB를 순차 순회하고, `executeForTargetDb`가 대상DB 1개당 하나의 try/catch로 감싸져 있어 한 대상DB의 `StoredProcedureInvoker`/`ActionHandler` 예외가 다음 대상DB 순회를 막지 않음을 코드로 직접 확인(`multiTargetDbOneFailureStillRunsTheOtherAndReportsPartialSuccess` 테스트가 mock 호출 횟수로 "두 대상DB 모두 실제로 invoke 호출됨"까지 검증).
- **집계 규칙**: `aggregateStatus`가 PLAN.md §3.1b 규칙(전부 성공→SUCCESS, 전부 실패→FAILURE, 혼재→PARTIAL_SUCCESS, 빈 리스트→FAILURE)과 정확히 일치. `TargetDbExecutionResult.status()`는 SUCCESS/FAILURE만 쓰고 PARTIAL_SUCCESS는 상위 `ExecutionResult`에서만 등장 — PLAN.md §3.1b "PARTIAL 없음" 명시와 일치.
- **대상DB 데이터 비혼합**: `DisplayOutput`에 `targetDbConfigId`/`targetDbConfigName`이 추가되고 `DisplayOutputStore`가 `(scheduleId, targetDbConfigId)` 복합키로 전환되어, 한 스케줄의 여러 대상DB 결과가 섞이지 않음을 `multiTargetDbDisplayOnlyKeepsSeparateDisplayOutputsPerTargetNeverMixed` 테스트와 프론트 `RunResultModal.test.tsx`(성공한 대상DB의 행 데이터가 실패한 대상DB 섹션에 나타나지 않음을 RTL로 확인) 양쪽에서 실증.
- **`run-now`/`last-result`의 실패 시 과거 데이터 노출 방지(1차 리뷰 N3의 다중 대상DB 확장판)**: `ScheduleExecutionController.displayOutputsFor`가 `targetDbResults`에서 **이번 실행에 SUCCESS인 대상DB만** 필터링해 `displayOutputStore`를 조회하므로, 어떤 대상DB가 이번 실행에서 실패했다면 그 대상DB의 과거 성공 결과가 응답에 섞여 나오지 않는다 — 1차 리뷰 때 지적한 문제의 재발이 없고, 오히려 대상DB 단위로 더 세밀하게 해결되어 있음.
- **`groupingMode`/`groupByField`가 대상DB 경계를 넘지 않음**: `ApiCallActionHandler.handle(schedule, spResultRows)`는 대상DB 1개분의 `spResultRows`만 받으며(해당 지식은 `ScheduleExecutor`에만 있음), `groupRows`는 그 범위 내에서만 그룹핑한다 — PLAN.md §5 "2026-11-16 교정" 문단이 요구하는 "대상DB를 섞지 않는다" 원칙이 `ActionHandler` 인터페이스를 바꾸지 않고도(PLAN.md §3.1b "ActionHandler와의 경계" 그대로) 지켜짐.

### 1차 리뷰 M1/M2 재발 여부 확인

- **M1(SP명 SQL 인젝션) 재발 없음**: `ScheduleController.create`/`update` 양쪽 모두 여전히 `SqlIdentifierValidator.validate(request.spName())`을 호출한다(이번 diff에서 그대로 유지됨, `:79`/`:98`). `StoredProcedureInvoker.invoke`도 변경되지 않아 1차 수정(`02fee04`)이 그대로 살아있다. 이번 라운드가 건드린 "SP 호출" 관련 변경은 "대상DB 1개당 1회 호출을 N개로 반복"뿐이고, SP명/파라미터 바인딩 로직 자체는 손대지 않았다.
- **M2(짧은 비밀값 마스킹) 재발 없음**: `Masking.lastFour`는 이번 diff에 포함되지 않았고(`web/api/Masking.java`는 변경 파일 목록에 없음), `TargetDbConfigController`/`CredentialController`가 여전히 그 수정된 버전을 그대로 사용한다. 이번 라운드에서 신설된 `TargetDbConfigRef` DTO(`id`/`name`만 담음, 비밀값 필드 없음)도 마스킹 대상 필드를 노출하지 않는다.
- **새로 추가된 코드 중 유사 패턴 점검**: `JdbcScheduleTargetDbConfigRepository`(신규 Repository)는 전부 `NamedParameterJdbcTemplate` + 고정 SQL 텍스트(테이블/컬럼명은 리터럴, 바인딩 값은 전부 `:scheduleId`/`:targetDbConfigId` 파라미터)만 쓰고, 사용자 입력을 SQL 문자열에 직접 결합하는 지점이 없다 — M1과 같은 패턴이 신규 코드에 재도입되지 않았다. `cronPatterns.ts`의 `buildCron`은 사용자 입력(숫자/요일 코드)을 문자열 템플릿에 꽂지만 SQL이 아니라 cron 문자열이고, 최종적으로 백엔드 `CronExpression.isValidExpression`이 재검증하므로(§9.1 설계 그대로) 이 경로로 들어간 임의 문자열이 그대로 실행되는 경로는 없다.

### 2차 리뷰 종합

- **Blocking: 없음.**
- **Major: 1건**(M4 — `targetDbConfigIds` 중복 id가 400이 아닌 500을 유발. API를 직접 호출하는 경로에서만 트리거되고 공식 SPA 경로로는 도달 불가하지만, 명시적으로 요청받은 "모든 경로에서 일관된 배열 검증" 기준에는 못 미침).
- **Minor: 2건**(N4 스케줄 저장과 대상DB 연결 교체 간 트랜잭션 경계 없음, N5 `executeForTargetDb`의 중복 조회).
- 1차 리뷰의 M1/M2는 이번 라운드의 신규/변경 코드에서 재발하지 않았고, N:M 전환(마이그레이션/CASCADE/삭제차단/순차실행/부분실패/데이터 비혼합/집계규칙)과 cron 구조화 입력(생성/역변환/경계케이스)은 PLAN.md가 약속한 그대로 구현되어 있음을 코드와 테스트 양쪽에서 확인했다.
- 이번 리뷰도 1차와 마찬가지로 정적 코드 리뷰(파일 열람 + diff 대조)로 수행했고, `mvn test`/`npm test`를 직접 재실행하지는 않았다(LOG.md가 주장하는 116/116, 25/25는 코드 레벨 대조로 교차검증은 했으나 직접 실행 재확인은 아님).
