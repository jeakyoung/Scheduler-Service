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
