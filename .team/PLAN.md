# PLAN.md — 범용 스케줄링 서비스 설계

> 전제: 이 문서는 `.team/RESEARCH.md`(레거시 실행흐름 분석 + (a)/(b)/(c) 조사)를 바탕으로 한다.
> 레거시(`C:\Users\USER\Desktop\scheduler-test\scheduler-test`)는 참고만 하고 수정하지 않는다.
> 바뀌는 건 레거시 대비 두 가지뿐: (1) 하드코딩 Task 3개 → DB 기반 동적 스케줄 정의, (2) `Config.java` 비밀값 하드코딩 → 외부 설정 주입. 나머지 실행 로직(SP 호출 → 결과가공 → 다운스트림 처리, 중복실행 방지)은 레거시를 일반화해서 그대로 가져간다.
>
> **2026-11-13 2차 교정(사용자 직접 지시) 반영 사항** — 이 문서 전체에 반영됨:
> 1. 실행 이력(로그) 테이블 스키마는 **이번 라운드 스코프 아님 (TBD, 추후 별도 설계)**. 대신 Job 실행 로직이 저장소 구현에 의존하지 않도록 `ExecutionResult` 값 객체 + `ExecutionResultSink` 포트로 경계를 두고, 지금은 로그 출력 수준의 임시 구현만 둔다.
> 2. 스케줄의 다운스트림 동작은 **(a) 임의 외부 API 호출**과 **(b) 호출 없이 SP 결과만 가공해 웹에 표시**, 두 모드를 모두 지원한다. 스케줄 정의에 모드 선택 필드를 둔다.
> 3. 실패 기록 영속화도 TBD — 다만 `ExecutionResult`에 실패 상태/에러메시지가 이미 포함되는 구조로 지금 설계한다.
> 4. **즉시실행(수동 트리거)** 포함 — 관리자 웹에서 "지금 실행" 시 정규 스케줄과 무관하게 1회 실행되고, 그 결과를 동기 응답으로 즉시 받아 화면에 표시한다 (영속 저장 여부와 무관).
> 5. **SP 파라미터화** — 파라미터 없는 EXEC와 파라미터 있는 EXEC를 모두 지원하는 구조.
> 6. 실행 이력은 "백업 서버"가 아니라 "**감시(모니터링) 서버**" 개념 — SP 전체 결과 행을 보존하는 게 목적이 아니라 상태/요약만 남기는 것이 목적. (TBD 스키마를 나중에 설계할 때도 이 원칙을 따를 것. 지금 스키마를 만들지는 않음.)
>
> **2026-11-13 3차 교정(사용자 직접 지시) 반영 사항**:
> 7. **백엔드/프론트 분리** — 관리자 웹 화면을 백엔드(Spring Boot)에 Thymeleaf로 통합하던 설계를 폐기하고, 백엔드는 REST API만 제공, 화면은 별도 React SPA(`frontend/`)로 분리한다(§1, §2, §6, §7).
> 8. **Spring Boot 패치 버전 독립 검증** — 특정 버전 숫자를 지금 고정하지 않고, 구현 착수 시점에 worker가 독립적으로 최신 안정 3.x 패치 버전을 확인하고 근거를 남기도록 지침만 명시(§1). 다른 무관한 프로젝트의 빌드 설정을 그대로 베끼지 않을 것.

---

## 0. 멀티테넌시 스코프 판단 (근거 명시)

**결론: 멀티테넌트로 가지 않는다. "1프로세스 = 1배포 = 1업체" 전제로 설계한다.**

근거:
- 사용자의 원요구는 "어떤 업체에 적용해도 재컴파일 없이 동작"이다. 이는 **배포 유연성**(같은 바이너리를 다른 업체에 재배포 가능)의 요구이지, **동시 서빙**(한 프로세스가 여러 업체 설정을 동시에 들고 처리)의 요구가 아니다.
- RESEARCH.md §(b)의 "관리자 웹 설정화면 + DB 암호화 저장(메모리 캐시, 재시작 없이 반영)"만으로도 재컴파일·재빌드 없이 "다른 업체에 재배포 → 관리자 화면에서 그 업체의 DB/API 설정과 스케줄을 입력"이 가능하다. 즉 요구사항은 멀티테넌시 없이 이미 충족된다.
- 멀티테넌시를 넣으면 테넌트별 DB 커넥션 풀 격리, 테넌트별 스케줄 네임스페이싱, 관리자 화면의 테넌트 스코핑, 테넌트 간 데이터 누출 방지 등 복잡도가 늘어나는데, 이를 요구하는 구체적 사용 사례(한 프로세스가 동시에 여러 업체를 서빙해야 하는 운영 시나리오)가 지금 없다.
- 레거시도 원래 "배포 1건 = 회사 1건" 전제였다(`Config.java` 상수 교체 후 재컴파일). 이 전제를 "재컴파일 없이"로 완화하는 것이 목표이고, "동시에 여러 전제를 다 만족"으로 확대하는 것은 과설계다.
- 다만 한 배포 안에서 **같은 업체가 여러 개의 다운스트림 대상**(예: SP가 여러 개, API 호출 대상이 여러 개)을 가질 수 있는 것은 멀티테넌시가 아니라 정상적인 범용성 요구이므로 지원한다(아래 스키마의 `target_db_config`/`credential`/`api_target_config`가 여러 행을 가질 수 있는 이유).

이 전제가 깨지는 명확한 신호(예: "하나의 운영 서버가 10개 업체를 동시에 서빙해야 한다")가 나오면 이 섹션을 다시 열어야 한다.

---

## 1. 기술스택 최종 결정

RESEARCH.md (c)의 추천을 그대로 채택한다. 조정 없음.

| 항목 | 결정 | 근거 |
|---|---|---|
| 언어/런타임 | Java 17 (LTS) | Quartz/Spring Boot 3.x 호환, 팀의 기존 JVM/JDBC/SQL Server 경험 재사용 |
| 프레임워크 | Spring Boot 3.x (백엔드는 **REST API 전용** — 화면 렌더링 없음) | Quartz 통합(`spring-boot-starter-quartz`), `@ConfigurationProperties`/Profile로 (2)의 외부 설정 주입을 프레임워크 차원에서 지원, Spring Security로 API 인증. **구체적 패치 버전은 이 문서에서 고정하지 않음 — 아래 "Spring Boot 버전 지침" 참고** |
| 스케줄링 | Quartz (JDBC JobStore) | RESEARCH (a) 결론 그대로: 임의 cron 지원·재시작 복구·(향후 다중 인스턴스 대비) 트리거 락킹을 기본 제공. **단일 프로세스 전제**(§0)이므로 다중 인스턴스 락킹은 "현재 필요"가 아니라 "future-proofing에 거의 비용 없이 따라오는 보너스"로 간주 — 이것 때문에 Quartz를 선택한 것은 아니고, 임의 cron + 재시작 복구만으로도 선택 이유가 충분하다 |
| 메타DB (스케줄 정의/설정/Quartz 상태 저장) | SQL Server | Quartz JDBC JobStore 공식 지원, 팀의 기존 SQL Server 운영 경험 |
| 대상 업무 DB(SP를 호출할 대상) | SQL Server로 고정 (멀티-DBMS 지원 없음) | 사용자 확인: 연결정보(URL/계정)는 업체마다 다르지만 대상 DB 자체는 모든 솔루션이 MSSQL로 구성됨 — 멀티-DBMS 지원은 불필요한 범용성(2026-11-13 교정). `target_db_config`에 `db_type` 같은 DBMS 선택 필드 없음(§4.3 참고) |
| 빌드도구 | Maven | RESEARCH 근거 동일(온보딩 마찰 최소) |
| 배포 형태 | Spring Boot 실행 가능 jar(내장 톰캣) | 컨테이너화·운영 단순성. WAR 패키징도 기술적으로 가능하지만 기본값은 jar |
| 관리자 웹 UI | **React (SPA), 백엔드와 완전히 분리된 별도 프론트엔드 앱** | 백엔드는 REST API만 제공, 화면은 별도 React 앱이 그 API를 호출하는 구조로 분리(2026-11-13 3차 교정, 사용자 직접 지시). Spring Boot를 Thymeleaf 서버 렌더링에 쓰지 않는다 — 프론트/백엔드가 독립적으로 빌드·배포되므로 CORS 허용 오리진 설정이 백엔드에 필요(§6 참고) |
| 인증 | Spring Security, DB 기반 관리자 계정 + 토큰/세션 기반 API 인증 | 레거시에는 이런 보호가 전혀 없었음(개선점). 단일 업체 전제이므로 복잡한 역할(role)/권한 체계는 불필요 — ADMIN 단일 역할로 시작. SPA가 별도 오리진에서 호출하므로 CORS와 함께 쿠키(SameSite)/토큰 방식 중 구현 단계에서 선택 |
| DB 접근 | Spring JDBC (`JdbcTemplate` 또는 `NamedParameterJdbcTemplate`) | 레거시의 `Statement`/`ResultSet` 수동 매핑 스타일을 거의 1:1 포팅. ORM 불필요(SP 호출 패턴 유지) |
| 마이그레이션 | Flyway | 메타DB 스키마(스케줄 정의/설정/관리자 계정) 버전 관리. Quartz `QRTZ_*` 테이블도 Flyway 마이그레이션으로 함께 적용 |

> **Spring Boot 버전 지침 (2026-11-13 3차 교정, 4차 재확인)**: 이 문서는 Spring Boot 3.x 계열을 채택한다는 결정만 내리고, **구체적인 패치 버전(예: 3.x.y) 숫자는 지금 박지 않는다.** 구현 시점(worker)이 현재 기준으로 안정적인 최신 Spring Boot 3.x 패치 버전을 **독립적으로 확인**하고, 그 버전을 고정하는 근거(해당 버전이 왜 적절한지 — 예: 사용 중인 Java 버전과의 호환성, 알려진 치명적 버그 없음 등)를 **커밋 메시지나 `LOG.md`에 1~2줄로** 남길 것.
>
> **다른 무관한 프로젝트의 `pom.xml`을 템플릿으로 그대로 복사해서 버전/보일러플레이트를 결정하지 말 것.** 실제로 T1에서 생성된 `backend/pom.xml`의 `spring-boot-starter-parent` 버전(3.3.5)과 Lombok-exclude 빌드 설정 블록이, 같은 `C:\Github` 아래의 무관한 프로젝트 **ReeL-Trip**의 `apps/api-spring/pom.xml` 설정과 완전히 동일한 것이 발견된 사례가 있었다(사용자 직접 지적, worker가 버전을 독립적으로 판단하지 않고 그대로 베낀 것으로 확인됨) — 같은 실수를 반복하지 말 것.

---

## 2. 프로젝트 구조 (디렉터리/패키지)

**2026-11-13 3차 교정(사용자 직접 지시) 반영**: 백엔드(Spring Boot, REST API 전용)와 프론트엔드(React SPA)를 레포 루트에서 `backend/`/`frontend/` 두 디렉터리로 분리한다. 기존에 레포 루트가 곧 Maven 프로젝트였던 구조는 `backend/`로 옮기고, `frontend/`를 신설한다. 백엔드는 화면을 전혀 렌더링하지 않고(Thymeleaf 제거) API만 제공한다.

```
Scheduler-Service/                         (레포 루트, git 루트)
├─ backend/                                (Maven 루트, pom.xml — 기존 scheduler-service 그대로 이 아래로)
│  ├─ src/main/java/com/scheduler/
│  │  ├─ SchedulerServiceApplication.java  (Spring Boot 엔트리포인트)
│  │  │
│  │  ├─ engine/                           ─ 실행 엔진 (레거시 실행흐름 포팅 지점)
│  │  │  ├─ quartz/
│  │  │  │  ├─ GenericScheduleJob.java     (Quartz Job, JobDataMap에는 scheduleId만 — 매 실행마다 최신 정의를 메타DB에서 재조회)
│  │  │  │  ├─ QuartzConfig.java           (JDBC JobStore 설정)
│  │  │  │  └─ ScheduleRegistrar.java      (scheduleJob/rescheduleJob/deleteJob 래핑 — 관리자 API가 호출)
│  │  │  │
│  │  │  ├─ execution/                     ─ 저장소 비의존 핵심 (교정사항 #1, #3, #6)
│  │  │  │  ├─ ScheduleExecutor.java       (Job과 수동실행 API가 공유하는 실행 진입점 — 레거시 run() 본문에 해당)
│  │  │  │  ├─ TriggerType.java            (SCHEDULED | MANUAL)
│  │  │  │  ├─ ExecutionResult.java        (값 객체: scheduleId, triggerType, startedAt, finishedAt, status, summary, errorMessage)
│  │  │  │  ├─ ExecutionStatus.java        (SUCCESS | FAILURE)
│  │  │  │  ├─ ExecutionResultSink.java    (포트 인터페이스: record(ExecutionResult))
│  │  │  │  ├─ LoggingExecutionResultSink.java (현재 유일한 구현 — SLF4J 로그 출력. 추후 DB Sink로 교체/추가)
│  │  │  │  ├─ DisplayOutput.java          (값 객체: 모드 b 결과물 — 컬럼/행, 생성시각)
│  │  │  │  └─ DisplayOutputStore.java     (스케줄ID별 "마지막 표시결과"만 보관하는 인메모리 저장소 — 이력이 아니라 최신값 캐시)
│  │  │  │
│  │  │  ├─ action/                        ─ 다운스트림 동작 2종 (교정사항 #2)
│  │  │  │  ├─ ActionHandler.java          (인터페이스: handle(spResultRows, ScheduleDefinition) → ActionOutcome)
│  │  │  │  ├─ ApiCallActionHandler.java   (임의 URL/메서드/헤더/바디템플릿 POST — 레거시 executeTask2 일반화)
│  │  │  │  ├─ DisplayOnlyActionHandler.java (SP 결과를 DisplayOutput으로 가공, 외부 호출 없음)
│  │  │  │  └─ BodyTemplateRenderer.java   (`${COLUMN}` / `${cred:NAME}` 플레이스홀더 치환)
│  │  │  │
│  │  │  └─ sp/                            ─ SP 호출 (레거시 executeTask1 일반화)
│  │  │     ├─ StoredProcedureInvoker.java (JDBC로 `EXEC {spName} {params...}` 실행, DataTable류 행 리스트 반환)
│  │  │     └─ SpParameterResolver.java    (schedule_parameter 행 → 실제 바인딩 값. LITERAL/CURRENT_DATE/CURRENT_DATETIME 등 value_source 해석 — 교정사항 #5)
│  │  │
│  │  ├─ domain/
│  │  │  ├─ schedule/
│  │  │  │  ├─ ScheduleDefinition.java, ScheduleParameter.java
│  │  │  │  ├─ ActionType.java            (API_CALL | DISPLAY_ONLY)
│  │  │  │  ├─ GroupingMode.java           (PER_ROW | GROUP_BY)
│  │  │  │  └─ ScheduleRepository.java (인터페이스) / ScheduleParameterRepository.java
│  │  │  ├─ config/
│  │  │  │  ├─ TargetDbConfig.java, Credential.java
│  │  │  │  └─ TargetDbConfigRepository.java / CredentialRepository.java
│  │  │  └─ admin/
│  │  │     └─ AdminUser.java, AdminUserRepository.java
│  │  │
│  │  ├─ infra/
│  │  │  ├─ persistence/jdbc/              (위 Repository 인터페이스의 JdbcTemplate 구현체)
│  │  │  ├─ crypto/
│  │  │  │  └─ CredentialCipher.java       (AES-GCM, 마스터키는 환경변수에서 로드 — 교정 이전 지시의 비밀값 설계)
│  │  │  └─ http/
│  │  │     └─ GenericHttpClient.java      (임의 URL/메서드/헤더 POST 래퍼 — 레거시 HttpURLConnection 로직 일반화)
│  │  │
│  │  ├─ web/
│  │  │  └─ api/                           (REST만 — 화면 렌더링 컨트롤러 없음)
│  │  │     ├─ ScheduleController.java, ScheduleParameterController.java
│  │  │     ├─ ScheduleExecutionController.java (수동실행/최근결과 — 교정사항 #4)
│  │  │     ├─ TargetDbConfigController.java, CredentialController.java
│  │  │     └─ AuthController.java
│  │  │
│  │  └─ security/
│  │     ├─ SecurityConfig.java            (Spring Security: /api/** 보호, 로그인 제외)
│  │     └─ CorsConfig.java                (frontend 오리진을 허용 오리진으로 등록 — §6 참고)
│  │
│  ├─ src/main/resources/
│  │  ├─ application.yml                   (기본값 + `${ENV_VAR}` 플레이스홀더, `app.cors.allowed-origin` 등)
│  │  ├─ application-local.yml              (로컬 개발용, gitignore)
│  │  └─ db/migration/                      (Flyway: V1__schedule_core.sql, V2__quartz_tables.sql, ...)
│  │
│  └─ pom.xml
│
└─ frontend/                                (React SPA, 백엔드와 별도 빌드/배포)
   ├─ src/
   │  ├─ pages/                             (§7 화면 목록에 1:1 대응 — Login, ScheduleList, ScheduleForm, RunResult, LastResult, TargetDbConfig, Credential, ChangePassword)
   │  ├─ components/                        (공용 UI: 파라미터 편집기, 헤더/바디템플릿 에디터 등)
   │  ├─ api/                               (백엔드 REST 호출 클라이언트, baseURL = 환경변수로 주입)
   │  └─ App.tsx / 라우팅
   ├─ .env.example                          (API_BASE_URL 등 — 실제 값은 gitignore된 .env.local)
   └─ package.json
```

**레거시 대비 구조적 의도**: `engine/execution`과 `engine/action`, `engine/sp` 패키지는 `domain`/`infra`(= 영속성 구현)를 모른 채로 동작할 수 있어야 한다. `ScheduleExecutor`는 `ExecutionResultSink`/`DisplayOutputStore` 인터페이스만 의존하므로, 교정사항 #1의 "저장소 구현에 의존하지 않는 경계"가 패키지 경계로 그대로 드러난다. 이 경계는 백엔드/프론트엔드 분리와는 별개 축이다 — 프론트엔드는 어느 경우에도 `web/api`가 내놓는 REST 계약(JSON)만 바라본다.

---

## 3. 핵심 도메인 경계: `ExecutionResult` / `ExecutionResultSink` / `DisplayOutput`

이번 라운드에서 **실행이력 테이블 스키마는 설계하지 않는다(TBD, 추후 별도 설계)**. 대신 다음 경계만 지금 확정한다.

### 3.1 `ExecutionResult` (값 객체, 영속화 대상 아님 — 테이블 없음)

| 필드 | 타입 | 설명 |
|---|---|---|
| scheduleId | Long | 어떤 스케줄 실행인지 |
| triggerType | enum | `SCHEDULED`(Quartz 발화) \| `MANUAL`(관리자 "지금 실행") |
| startedAt / finishedAt | Instant | 실행 구간 |
| status | enum | `SUCCESS` \| `FAILURE` |
| summary | String | **요약 수준만** — 예: "처리행 12건, API 호출 12/12 성공" / "SP 결과 5행 가공 완료". SP가 반환한 전체 행을 담지 않는다(교정사항 #6: 감시 서버, 백업 서버 아님) |
| errorMessage | String (nullable) | 실패 시 예외 메시지(스택트레이스 전체 아님, 1~2줄 수준) — 교정사항 #3: 실패 상태를 지금 구조에 포함 |

### 3.2 `ExecutionResultSink` (포트)

```
interface ExecutionResultSink {
    void record(ExecutionResult result);
}
```

- 현재 구현: `LoggingExecutionResultSink` — SLF4J로 INFO/ERROR 로그만 남긴다(임시 구현, 영속화 없음).
- 향후: 실행이력 테이블 설계가 끝나면 `JdbcExecutionResultSink` 같은 구현을 추가하고, Spring Bean 교체(또는 `CompositeExecutionResultSink`로 로깅+DB 동시 기록)만으로 `ScheduleExecutor`/`GenericScheduleJob` 쪽 코드는 변경이 필요 없어야 한다. **이것이 이번 라운드의 완료 기준이다** — 실제 DB 적재는 범위 밖.

### 3.3 `DisplayOutput` / `DisplayOutputStore` (모드 b 전용, 이력과는 다른 개념)

모드 (b)(외부 호출 없이 SP 결과를 웹에 표시)는 "실행 이력"이 아니라 **"지금 보여줄 화면 데이터"**이므로 별도로 다룬다.

- `DisplayOutput`: 컬럼명 리스트 + 행 데이터(SP 결과를 그대로, 또는 스케줄 정의의 컬럼 선택에 따라 추려서) + 생성시각.
- `DisplayOutputStore`: 스케줄ID별로 **가장 최근 1건만** 인메모리(`ConcurrentHashMap`)로 들고 있는 캐시. 이력이 아니라 "최신 상태 스냅샷"이므로 프로세스 재시작 시 사라져도 무방하다(다음 실행에서 다시 채워짐). 관리자 웹의 "최근 실행 결과" 화면과 수동실행 응답이 이 캐시를 읽는다.
- 주의: SP가 매우 큰 결과셋을 반환하는 극단적 케이스에 대한 메모리 상한선(예: 최대 N행만 보관)은 구현 단계에서 결정하되, 기본 원칙(교정사항 #6, "전체 보존이 목적 아님")을 따른다.

---

## 4. DB 스키마 (메타DB, SQL Server)

> **실행이력 테이블은 이번 라운드에 스키마를 만들지 않는다 (TBD).** 설계 시점에 §3의 `ExecutionResult`/감시-서버 원칙(교정사항 #6: 상태/요약만, 전체 결과행 보존 금지)을 따를 것.
>
> **적용 범위 명확화**: 아래 §4.1~4.6의 Flyway 마이그레이션은 **엔진 자신의 메타DB(스케줄 정의/설정/Quartz 상태 저장용)에만** 적용된다. `target_db_config`가 가리키는 **대상 업무 DB(SP가 실제로 존재하는 업체 DB)에는 Flyway가 전혀 적용되지 않는다** — 엔진은 그 DB에 대해 스키마 변경 권한도, DDL 실행 기능도 갖지 않는다(§9 참고).

### 4.1 `schedule_definition` — 스케줄(Job) 정의

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| name | NVARCHAR(200) | 관리자 UI 표시용 |
| enabled | BIT | false면 Quartz에 등록하지 않음/등록 해제 |
| cron_expression | NVARCHAR(100) | 레거시 3종 고정주기 메서드를 대체하는 Quartz cron (요일 포함 가능 — 별도 "주말 스킵" 필드 불필요). **Quartz cron은 Unix cron과 문법이 다름** — day-of-month 필드와 day-of-week 필드 중 반드시 하나는 `?`로 둬야 함(둘 다 `*`나 둘 다 구체값이면 유효하지 않음) |
| timezone | NVARCHAR(50) | 기본 `Asia/Seoul` |
| sp_name | NVARCHAR(200) | 호출할 저장프로시저명 (레거시 `SP_WORK_LIMIT_LIST` 등에 해당, 리터럴 아님) |
| target_db_config_id | BIGINT FK → target_db_config | SP를 실행할 대상 DB |
| action_type | NVARCHAR(20) | `API_CALL` \| `DISPLAY_ONLY` (교정사항 #2) |
| action_config | NVARCHAR(MAX) (JSON) | action_type에 따라 모양이 다른 설정. §5 참고 |
| description | NVARCHAR(500) | nullable |
| created_at / updated_at | DATETIME2 | |
| updated_by | NVARCHAR(100) | 관리자 계정명 |

### 4.2 `schedule_parameter` — SP 파라미터 (교정사항 #5)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| schedule_id | BIGINT FK → schedule_definition | |
| param_order | INT | 위치 기반 바인딩 순서 |
| param_name | NVARCHAR(100) | 문서화/표시용 (예: `@FromDate`) |
| data_type | NVARCHAR(20) | `STRING`/`INT`/`DECIMAL`/`DATE`/`DATETIME`/`BOOLEAN` |
| value_source | NVARCHAR(20) | `LITERAL`(관리자가 입력한 고정값) \| `CURRENT_DATE` \| `CURRENT_DATETIME` — 실행 시점에 `SpParameterResolver`가 해석. 이후 소스 종류는 enum 값 추가만으로 확장(스키마 변경 없음) |
| literal_value | NVARCHAR(500) | value_source=`LITERAL`일 때만 사용 |

파라미터가 없는 스케줄은 이 테이블에 행이 없는 것으로 표현 — "파라미터 없는 EXEC"와 "파라미터 있는 EXEC"를 같은 구조로 지원.

### 4.3 `target_db_config` — SP를 실행할 대상 업무 DB 접속정보

**대상 업무 DB는 MSSQL(SQL Server)로 고정한다.** 사용자 확인 결과, 연결정보(URL/계정)는 업체마다 다르지만 대상 DB 자체는 모든 솔루션이 MSSQL로 구성되어 있어 멀티-DBMS 지원은 불필요한 범용성이다(2026-11-13 교정, 사용자 직접 확인) — 따라서 `db_type` 같은 DBMS 선택 필드를 두지 않는다. 연결정보(URL/계정/비밀번호)만 관리자 웹에서 업체별로 입력받는다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| name | NVARCHAR(100) | 관리자 UI 표시용 (예: "iPlusERP 운영") |
| jdbc_url | NVARCHAR(500) | MSSQL JDBC URL 전제 |
| username | NVARCHAR(200) | |
| encrypted_password | NVARCHAR(MAX) | AES-GCM 암호화, 마스터키는 환경변수 |
| created_at / updated_at | DATETIME2 | |

단일 업체 전제(§0)이므로 보통 1~2행이면 충분하지만, 한 업체가 여러 대상 DB를 쓰는 경우(멀티테넌시 아님)를 위해 테이블로 둔다.

### 4.4 `credential` — 다운스트림 API 호출에 쓰는 비밀값(헤더용 등)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| name | NVARCHAR(100) UNIQUE | 예: `FCM_API_KEY`, `FCM_SECURITY_CODE` — action_config의 `${cred:NAME}`에서 참조 |
| encrypted_value | NVARCHAR(MAX) | AES-GCM 암호화 |
| description | NVARCHAR(300) | nullable |
| updated_at | DATETIME2 | |
| updated_by | NVARCHAR(100) | |

관리자 웹에서 조회 시 평문을 보여주지 않고 마스킹(끝 4자만 노출 등) — RESEARCH (b) 옵션3 유의사항 반영.

### 4.5 `admin_user` — 관리자 계정

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| username | NVARCHAR(100) UNIQUE | |
| password_hash | NVARCHAR(200) | BCrypt |
| role | NVARCHAR(20) | 기본 `ADMIN` 단일 역할(§0: 복잡한 권한체계 불필요) |
| created_at | DATETIME2 | |

### 4.6 Quartz `QRTZ_*` 테이블

Quartz 공식 SQL Server DDL 스크립트를 Flyway 마이그레이션으로 그대로 적용(직접 설계하지 않음, 표준 스크립트 사용).

### 4.7 비밀값 2계층 설계 확정 (RESEARCH (b) 추천 반영, 단일 테넌트로 단순화)

| 계층 | 값 | 주입 방식 |
|---|---|---|
| 부트스트랩 | 메타DB(위 4.1~4.6) 접속정보, `credential`/`target_db_config` 암호화용 마스터 키, (필요시) 세션 시크릿 | **환경변수** (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `MASTER_ENCRYPTION_KEY` 등). 재시작 필요하지만 애플리케이션 기동 자체에 필요한 최소 비밀값이므로 허용 |
| 운영 비밀값 | `target_db_config`의 업무DB 접속정보, `credential`의 API 키/시큐리티코드 등(레거시 `Config.java`의 6개 상수에 대응) | **관리자 웹 CRUD + DB 암호화 저장**, 애플리케이션이 메모리 캐시 후 저장 시 캐시 무효화 → **재시작 없이 즉시 반영** |

멀티테넌시를 채택하지 않았으므로(§0) RESEARCH가 언급한 "테넌트별 접두사 env var 한계" 문제는 발생하지 않는다 — 운영 비밀값 자체를 테이블/UI로 뺀 이유는 "재컴파일 없는 재배포"와 "재시작 없는 변경"이며, 멀티테넌시 지원이 목적이 아니다.

---

## 5. `action_config` JSON 모양 (교정사항 #2, #5 반영)

`schedule_definition.action_config`는 `action_type`에 따라 아래 두 모양 중 하나를 가진다. (JSON 컬럼을 쓰는 이유: 바디 템플릿/헤더처럼 가변 구조가 스키마 변경 없이 확장돼야 함 — 레거시 3개 Task의 FCM 바디 차이가 이 필드만으로 표현됨)

### `action_type = API_CALL`

```json
{
  "method": "POST",
  "url": "https://notify.example.com/api/push",
  "headers": {
    "Content-Type": "application/json; charset=UTF-8",
    "SECURITY_CODE": "${cred:FCM_SECURITY_CODE}",
    "X-Api-Key": "${cred:FCM_API_KEY}"
  },
  "groupingMode": "PER_ROW",
  "groupByField": null,
  "bodyTemplate": "{\"notiGbn\":\"8\",\"companyCode\":\"000001\",\"empList\":[\"${EMPLOYEE_NO}\"],\"senderCode\":\"0000\",\"title\":\"${EA_TITLE}\",\"body\":\"...\"}"
}
```

- `groupingMode: PER_ROW` → SP 결과 행마다 1회 호출 (레거시 Hour/PurchaseReq 패턴)
- `groupingMode: GROUP_BY` + `groupByField` → 지정 컬럼 기준으로 그룹핑 후 그룹당 1회 호출 (레거시 Day 패턴 — 문서 타이틀별 묶음)
- `${COLUMN_NAME}` : SP 결과 행(또는 그룹의 첫 행/집계값)의 컬럼값으로 치환
- `${cred:NAME}` : `credential` 테이블에서 실행 시점에 복호화해 치환 — 이 치환 결과는 `ExecutionResult.summary`/로그에 원문 그대로 남기지 않는다(비밀값 로그 유출 방지)
- 이 모드는 FCM 한정이 아니라 **임의 URL/메서드/헤더/바디** — FCM 호출은 이 범용 구조의 한 사례일 뿐 하드코딩하지 않는다

### `action_type = DISPLAY_ONLY`

```json
{
  "displayFormat": "TABLE",
  "columns": ["EMPLOYEE_NO", "EA_TITLE", "EXE_SEQ"]
}
```

- `columns`가 없으면 SP가 반환한 모든 컬럼을 표시
- 외부 호출 없음 — `DisplayOnlyActionHandler`가 SP 결과를 `DisplayOutput`으로 변환해 `DisplayOutputStore`에 적재하는 것으로 끝

---

## 6. API 목록

모든 `/api/**`는 Spring Security 인증 필요(로그인 제외). 백엔드와 프론트엔드(React SPA)가 별도 오리진에서 배포되므로 **CORS 허용 오리진 설정이 필요**하다 — 백엔드 `CorsConfig`가 프론트엔드 배포 오리진(로컬 개발 시 `http://localhost:5173` 류, 운영 시 실제 프론트 배포 도메인)을 환경변수(`app.cors.allowed-origin`)로 주입받아 허용 목록에 등록한다. 자격증명(쿠키/세션) 포함 요청을 쓸 경우 `Access-Control-Allow-Credentials`까지 함께 고려해야 함(세션 vs 토큰 인증 중 구현 단계 선택에 따라 결정).

### 인증
| Method | Path | 설명 |
|---|---|---|
| POST | /api/auth/login | 관리자 로그인 |
| POST | /api/auth/logout | 로그아웃 |
| GET | /api/auth/me | 현재 로그인 계정 정보 |

### 스케줄 CRUD + 파라미터
| Method | Path | 설명 |
|---|---|---|
| GET | /api/schedules | 목록 (enabled, 다음 실행시각 등 포함) |
| GET | /api/schedules/{id} | 단건 조회(파라미터 포함) |
| POST | /api/schedules | 생성 → 즉시 Quartz 등록(enabled=true인 경우) |
| PUT | /api/schedules/{id} | 수정 → cancel 후 재등록(cron/파라미터/action_config 변경 반영) |
| DELETE | /api/schedules/{id} | 삭제 → Quartz 등록 해제 |
| PATCH | /api/schedules/{id}/enabled | 활성/비활성 토글 → Quartz add/remove |
| PUT | /api/schedules/{id}/parameters | 파라미터 목록 전체 교체(단순화를 위해 개별 CRUD 대신 통째 교체) |

### 실행 (교정사항 #4)
| Method | Path | 설명 |
|---|---|---|
| POST | /api/schedules/{id}/run-now | **즉시실행**. 정규 스케줄과 무관하게 1회 실행하고 `ExecutionResult`(+ `DISPLAY_ONLY`면 `DisplayOutput`)를 응답 바디로 동기 반환. 이미 실행 중이면 중복 실행 거부 응답(상태코드로 구분) |
| GET | /api/schedules/{id}/last-result | `DisplayOutputStore`/마지막 `ExecutionResult`의 인메모리 캐시 조회(영속 이력 아님 — 프로세스 기동 이후 최신 1건) |
| GET | /api/schedules/{id}/status | Quartz 기준 다음 실행 예정 시각, 현재 실행중 여부 |

### 대상 DB 설정
| Method | Path | 설명 |
|---|---|---|
| GET | /api/config/target-db | 목록(비밀번호 마스킹) |
| POST | /api/config/target-db | 생성(비밀번호 암호화 저장) |
| PUT | /api/config/target-db/{id} | 수정 |
| DELETE | /api/config/target-db/{id} | 삭제(사용 중인 schedule_definition 있으면 차단) |

### 자격증명(Credential)
| Method | Path | 설명 |
|---|---|---|
| GET | /api/config/credentials | 목록(값 마스킹 — 끝 4자만) |
| POST | /api/config/credentials | 생성(암호화 저장) |
| PUT | /api/config/credentials/{id} | 값 교체 |
| DELETE | /api/config/credentials/{id} | 삭제(참조 중인 action_config 있으면 경고) |

---

## 7. 화면 목록 (관리자 웹, React SPA — 백엔드와 별도 앱)

화면 자체의 목록·내용은 교정 전과 동일하고, 구현 기술만 Thymeleaf 서버 렌더링 → React 컴포넌트/페이지로 바뀐다. 각 페이지는 `frontend/src/api/` 클라이언트를 통해서만 백엔드 `/api/**`를 호출한다(페이지가 직접 DB/Quartz를 알지 못함).

1. **Login 페이지** — 로그인 폼 → `/api/auth/login` 호출, 인증 토큰/세션 저장
2. **ScheduleList 페이지** — 스케줄 목록 테이블: enabled 토글, cron, 다음 실행 예정시각, 마지막 실행 상태(성공/실패 뱃지 — 인메모리 캐시 기반, "이력 아님" 명시), "지금 실행" 버튼
3. **ScheduleForm 페이지** — 생성/수정 공용: 기본정보(이름/cron/timezone/대상DB/SP명) + 파라미터 편집기 컴포넌트(행 추가/삭제, 이름/타입/값소스/리터럴값) + `action_type` 선택에 따라 폼 영역이 동적으로 전환되는 컴포넌트:
   - `API_CALL` 선택 시: method/url/headers(키-값 추가, 값에 `${cred:NAME}` 선택 가능하도록 credential 목록 드롭다운 제공)/groupingMode/groupByField/bodyTemplate(placeholder 안내 포함)
   - `DISPLAY_ONLY` 선택 시: displayFormat/columns 선택
4. **RunResult 컴포넌트/모달** — "지금 실행" 클릭 시 `/api/schedules/{id}/run-now` 응답(성공/실패, summary, errorMessage, `DISPLAY_ONLY`면 표/텍스트 결과)을 같은 화면에서 모달 또는 인라인으로 바로 표시
5. **LastResult 페이지** — 스케줄별 `last-result` 조회(= `DisplayOutputStore`/`ExecutionResult` 캐시 열람, 영속 이력이 아니라는 안내 문구 포함)
6. **TargetDbConfig 페이지** — 대상 DB 설정 목록/생성/수정/삭제, 비밀번호 마스킹
7. **Credential 페이지** — 자격증명 목록/생성/수정/삭제, 값 마스킹
8. **ChangePassword 페이지** (본인 계정) — 최소 수준의 계정 관리, 복잡한 계정 관리 화면은 범위 밖(§0: 단일 역할)

---

## 8. 레거시 → 신규 매핑표 (구체화)

| 레거시 | 신규 |
|---|---|
| `SchedulerContextListener.contextInitialized()`의 3개 `scheduleAtFixedRate` 리터럴 호출 | 기동 시 `schedule_definition`의 `enabled=true` 행 전체를 읽어 `ScheduleRegistrar`가 Quartz에 등록 + 관리자 CRUD가 호출하는 `scheduleJob/rescheduleJob/deleteJob` |
| `DaySchedulerTask`/`HourSchedulerTask`/`PurchaseReqSchedulerTask` (3개 클래스, SP명/주기/바디구성이 클래스에 내재) | `GenericScheduleJob` 1개 + `ScheduleExecutor`. SP명/파라미터/cron/action_type/action_config는 모두 `schedule_definition`/`schedule_parameter` 행에서 로드 |
| `calculateInitialDelayForNextMonday9AM()`/`calculateInitialDelayFor30Min()`/`calculateInitialDelayForDaily830AM()` | `CronTrigger` (Quartz cron 표현식) — 요일 조건(토/일 제외)도 cron 자체로 표현, 별도 "주말 체크" 코드 불필요. 단, Quartz cron은 day-of-month와 day-of-week 필드 중 하나를 반드시 `?`로 둬야 하는 Quartz 고유 문법(Unix cron과 다름)을 따른다 |
| Task `run()` 내부의 주말 체크(`DayOfWeek.SATURDAY/SUNDAY`면 return) | cron 표현식에 요일 포함(예: `0 30 8 ? * MON-FRI` — day-of-month를 `?`로 둠)으로 대체 — 런타임 분기 코드 제거 |
| `isTaskRunning` (static boolean, 클래스당 1개) | `@DisallowConcurrentExecution`(Quartz, 스케줄 1개당 자동 격리) — 수동실행(`run-now`)도 같은 `ScheduleExecutor` 경로를 타므로 "정규 실행 중 수동실행 요청" 충돌도 동일 가드로 처리 |
| `executeTask1()`: `Class.forName` + `DriverManager.getConnection(Config.xxx)` + `EXEC SP_XXX` | `StoredProcedureInvoker` — 로직은 그대로, DB 접속정보는 `target_db_config`(복호화), SP명/파라미터는 `schedule_definition`/`schedule_parameter`에서 로드. **파라미터 없는 EXEC와 파라미터 있는 EXEC 모두 지원**(교정사항 #5) |
| `executeTask2()`: 문자열 결합 JSON + `HttpURLConnection` POST + `SECURITY_CODE`/`X-Api-Key` 헤더 | `ApiCallActionHandler` + `GenericHttpClient` + `BodyTemplateRenderer` — 로직은 그대로(동일한 POST+커스텀헤더 개념), URL/헤더/바디 템플릿은 `action_config`(JSON)에서 로드, FCM 전용이 아니라 임의 API 호출 지원(교정사항 #2-a) |
| (레거시에는 없음 — 결과를 FCM으로만 내보냄) | `DisplayOnlyActionHandler` + `DisplayOutput`/`DisplayOutputStore` — SP 결과를 외부 호출 없이 웹에 표시하는 모드 신규 추가(교정사항 #2-b) |
| (레거시에는 없음 — 콘솔 `System.out.println`만) | `ExecutionResult` + `ExecutionResultSink`(현재는 `LoggingExecutionResultSink`만, 영속화는 TBD) — 교정사항 #1/#3/#6 |
| (레거시에는 없음 — 수동 실행 불가, 재배포/재시작만 가능) | `POST /api/schedules/{id}/run-now` — 즉시실행 + 동기 결과 반환(교정사항 #4) |
| `Config.java`의 6개 상수(DB url/user/pw, FCM url/securityCode/apiKey) + 주석 스왑 재컴파일 | 부트스트랩 비밀값(메타DB 접속정보, 마스터키) = 환경변수 / 운영 비밀값(`target_db_config`, `credential`) = 관리자 웹 CRUD + DB 암호화 저장, 재시작 없이 반영 |
| (레거시에는 없음 — 인증 없는 WAR 배포) | Spring Security 세션 로그인(`admin_user`) |

---

## 9. 범위 밖 (이번 라운드에서 하지 않는 것)

- 실행이력(로그) 테이블 스키마/마이그레이션 설계 — TBD, 추후 별도 라운드
- 실패 기록의 영속화 — `ExecutionResult`에 실패 상태/에러메시지는 포함하되 저장은 하지 않음
- 멀티테넌시(한 프로세스가 여러 업체를 동시 서빙) — §0 근거에 따라 범위 밖
- 복잡한 역할/권한 체계 — 단일 ADMIN 역할만
- 다중 인스턴스 운영을 전제로 한 설계(분산 락 등 직접 구현) — Quartz JDBC JobStore가 부가적으로 제공하지만, 이를 적극적으로 활용/검증하는 것은 범위 밖(§0 전제: 단일 인스턴스)
- 대상 업무 DB에 대한 **DDL 실행/신규 SP 생성 기능** — 엔진은 업체 DB에 **이미 존재하는 SP를 호출만** 하고, 생성/수정하지 않는다(사용자 확인: "의미 없을 것 같다, DDL 생성안은 보류" — 이번뿐 아니라 앞으로도 범위에 넣지 않기로 확정)
- 대상 업무 DB에 대한 멀티-DBMS 지원 — MSSQL로 고정(§1, §4.3 참고)
