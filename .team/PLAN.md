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

> **2026-11-16 교정 — "스케줄당 대상DB 다중 선택" 기능과 §0의 관계**: 스케줄 1개가 등록된 `target_db_config` 중 여러 개를 선택해 동시에 같은 SP를 호출하도록 하는 기능(§4.3b, §6)을 추가했다. 이는 §0의 결론을 재론하는 것이 아니라, §0이 이미 명시적으로 허용해 둔 "같은 업체가 여러 개의 다운스트림 대상을 가질 수 있다"(위 근거 문단의 마지막 줄, `target_db_config`가 여러 행을 가질 수 있는 이유)는 범위를 "스케줄이 그 여러 대상 중 1개에만 고정"에서 "스케줄이 그중 여러 개를 동시에 선택"으로 확장한 것일 뿐이다. 여전히 "1프로세스 = 1배포 = 1업체"이고, 멀티테넌시(여러 업체를 동시에 서빙)와는 무관하다 — 한 업체 안에 여러 다운스트림 대상(예: 지점별 DB, 사업부별 DB)이 있고 하나의 스케줄이 그 여러 대상 모두에 같은 SP를 돌려야 하는 경우를 지원하는 것뿐이다.

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
| 배포 형태 | **WAR, 외부 Tomcat에 배치**(2026-11-17 교정 — §11 참고. 기존 "jar+내장톰캣 권장" 결정을 대체) | 실제 배포 가능한 인프라가 외부 Tomcat뿐이라는 제약이 확인됨에 따라 변경. 로컬 개발 워크플로(`mvn spring-boot:run`)는 그대로 유지(§11.1) |
| 관리자 웹 UI | **React (SPA)** — 소스/빌드는 백엔드와 분리된 별도 프론트엔드 앱, **운영 배포는 백엔드 WAR 안에 정적 리소스로 통합**(2026-11-17 교정, §11.2) | 백엔드는 REST API만 제공하고 화면은 React가 담당하는 역할 분리(2026-11-13 3차 교정)는 유지. 다만 "완전히 분리된 배포"였던 전제는 Tomcat 단일 인프라 제약으로 §11에서 깨졌다 — 로컬 개발 시의 분리 실행(프론트 dev서버+백엔드 별도 기동)은 계속 지원하므로 CORS 설정 자체는 유지(§11.5) |
| 인증 | Spring Security, DB 기반 관리자 계정 + 토큰/세션 기반 API 인증 | 레거시에는 이런 보호가 전혀 없었음(개선점). 단일 업체 전제이므로 복잡한 역할(role)/권한 체계는 불필요 — ADMIN 단일 역할로 시작. SPA가 별도 오리진에서 호출하므로 CORS와 함께 쿠키(SameSite)/토큰 방식 중 구현 단계에서 선택 |
| DB 접근 | Spring JDBC (`JdbcTemplate` 또는 `NamedParameterJdbcTemplate`) | 레거시의 `Statement`/`ResultSet` 수동 매핑 스타일을 거의 1:1 포팅. ORM 불필요(SP 호출 패턴 유지) |
| 마이그레이션 | Flyway | 메타DB 스키마(스케줄 정의/설정/관리자 계정) 버전 관리. Quartz `QRTZ_*` 테이블도 Flyway 마이그레이션으로 함께 적용 |

> **Spring Boot 버전 지침 (2026-11-13 3차 교정, 4차 재확인)**: 이 문서는 Spring Boot 3.x 계열을 채택한다는 결정만 내리고, **구체적인 패치 버전(예: 3.x.y) 숫자는 지금 박지 않는다.** 구현 시점(worker)이 현재 기준으로 안정적인 최신 Spring Boot 3.x 패치 버전을 **독립적으로 확인**하고, 그 버전을 고정하는 근거(해당 버전이 왜 적절한지 — 예: 사용 중인 Java 버전과의 호환성, 알려진 치명적 버그 없음 등)를 **커밋 메시지나 `LOG.md`에 1~2줄로** 남길 것.
>
> **다른 무관한 프로젝트의 `pom.xml`을 템플릿으로 그대로 복사해서 버전/보일러플레이트를 결정하지 말 것.** 실제로 T1에서 생성된 `backend/pom.xml`의 `spring-boot-starter-parent` 버전(3.3.5)과 Lombok-exclude 빌드 설정 블록이, 같은 `C:\Github` 아래의 무관한 프로젝트 **ReeL-Trip**의 `apps/api-spring/pom.xml` 설정과 완전히 동일한 것이 발견된 사례가 있었다(사용자 직접 지적, worker가 버전을 독립적으로 판단하지 않고 그대로 베낀 것으로 확인됨) — 같은 실수를 반복하지 말 것.

---

## 2. 프로젝트 구조 (디렉터리/패키지)

**2026-11-13 3차 교정(사용자 직접 지시) 반영**: 백엔드(Spring Boot, REST API 전용)와 프론트엔드(React SPA)를 레포 루트에서 `backend/`/`frontend/` 두 디렉터리로 분리한다. 기존에 레포 루트가 곧 Maven 프로젝트였던 구조는 `backend/`로 옮기고, `frontend/`를 신설한다. 백엔드는 화면을 전혀 렌더링하지 않고(Thymeleaf 제거) API만 제공한다.

> **2026-11-17 교정**: 소스 트리의 `backend/`/`frontend/` 분리 자체는 그대로 유지한다(로컬 개발은 지금처럼 각자 따로 실행). 다만 **운영 배포 산출물은 분리되지 않는다** — `backend/pom.xml`의 `mvn package`가 `frontend/`를 빌드해 그 결과물을 백엔드 WAR 안(`target/classes/static/`)에 포함시키고, Tomcat에는 그 WAR 1개만 올라간다. 자세한 내용은 §11.

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
│  │  │  │  ├─ ExecutionResult.java        (값 객체: scheduleId, triggerType, startedAt, finishedAt, status, summary, errorMessage, **targetDbResults**(§3.1, 2026-11-16 신규))
│  │  │  │  ├─ TargetDbExecutionResult.java (값 객체, 2026-11-16 신규 — §3.1b: targetDbConfigId/Name, status, summary, errorMessage)
│  │  │  │  ├─ ExecutionStatus.java        (SUCCESS | PARTIAL_SUCCESS(2026-11-16 신규) | FAILURE)
│  │  │  │  ├─ ExecutionResultSink.java    (포트 인터페이스: record(ExecutionResult))
│  │  │  │  ├─ LoggingExecutionResultSink.java (현재 유일한 구현 — SLF4J 로그 출력. 추후 DB Sink로 교체/추가)
│  │  │  │  ├─ DisplayOutput.java          (값 객체: 모드 b 결과물 — scheduleId, **targetDbConfigId/Name**(2026-11-16 신규), 컬럼/행, 생성시각)
│  │  │  │  └─ DisplayOutputStore.java     ((scheduleId, targetDbConfigId) 복합키별 "마지막 표시결과"만 보관하는 인메모리 저장소 — 이력이 아니라 최신값 캐시. getAllForSchedule(scheduleId) 추가(2026-11-16)
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
│  │  │  │  └─ ScheduleRepository.java (인터페이스) / ScheduleParameterRepository.java / ScheduleTargetDbConfigRepository.java (2026-11-16 신규 — §4.3b 조인 테이블, findByScheduleId/replaceForSchedule)
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
│  └─ pom.xml                               (packaging=war, 2026-11-17 — §11.1/§11.2: frontend-maven-plugin + maven-resources-plugin으로 frontend/dist를 target/classes/static으로 복사)
│
└─ frontend/                                (React SPA, 소스/로컬개발은 백엔드와 분리. 운영 WAR에는 빌드 결과물만 통합 — §11.2)
   ├─ src/
   │  ├─ pages/                             (§7 화면 목록에 1:1 대응 — Login, ScheduleList, ScheduleForm, RunResult, LastResult, TargetDbConfig, Credential, ChangePassword)
   │  ├─ components/                        (공용 UI: 파라미터 편집기, 헤더/바디템플릿 에디터, **CronBuilder**(2026-11-16 신규 — §9의 6가지 구조화 입력 + "고급: 직접 cron 입력" 토글, cron↔구조화 양방향 변환 로직), **TargetDbConfigMultiSelect**(2026-11-16 신규) 등)
   │  ├─ api/                               (백엔드 REST 호출 클라이언트, baseURL = 환경변수로 주입)
   │  └─ App.tsx / 라우팅
   ├─ .env.example                          (API_BASE_URL 등 — 실제 값은 gitignore된 .env.local)
   ├─ .env.local                            (로컬 개발용 절대 URL, 예: VITE_API_BASE_URL=http://localhost:8080 — gitignore)
   ├─ .env.production                       (2026-11-17 신규 — §11.4: VITE_API_BASE_URL을 빈 값으로 둬서 운영 빌드가 상대경로로 API를 호출하게 함)
   └─ package.json
```

**레거시 대비 구조적 의도**: `engine/execution`과 `engine/action`, `engine/sp` 패키지는 `domain`/`infra`(= 영속성 구현)를 모른 채로 동작할 수 있어야 한다. `ScheduleExecutor`는 `ExecutionResultSink`/`DisplayOutputStore` 인터페이스만 의존하므로, 교정사항 #1의 "저장소 구현에 의존하지 않는 경계"가 패키지 경계로 그대로 드러난다. 이 경계는 백엔드/프론트엔드 분리와는 별개 축이다 — 프론트엔드는 어느 경우에도 `web/api`가 내놓는 REST 계약(JSON)만 바라본다.

---

## 3. 핵심 도메인 경계: `ExecutionResult` / `ExecutionResultSink` / `DisplayOutput`

이번 라운드에서 **실행이력 테이블 스키마는 설계하지 않는다(TBD, 추후 별도 설계)**. 대신 다음 경계만 지금 확정한다.

### 3.1 `ExecutionResult` (값 객체, 영속화 대상 아님 — 테이블 없음)

**2026-11-16 교정 — 스케줄당 대상DB 다중 선택(§4.3b) 반영**: 스케줄 1회 실행이 이제 대상DB 1개가 아니라 N개를 순회하므로(§4.3b), `ExecutionResult`는 **전체 요약(top-level)** 과 **대상DB별 세부결과(`targetDbResults`)** 를 모두 담도록 확장한다. 기존 필드(scheduleId/triggerType/startedAt/finishedAt/summary/errorMessage)는 깨지 않고 `targetDbResults` 필드만 추가한다.

| 필드 | 타입 | 설명 |
|---|---|---|
| scheduleId | Long | 어떤 스케줄 실행인지 |
| triggerType | enum | `SCHEDULED`(Quartz 발화) \| `MANUAL`(관리자 "지금 실행") |
| startedAt / finishedAt | Instant | 실행 구간(전체 대상DB 순회 포함) |
| status | enum | `SUCCESS`(대상DB 전부 성공) \| `PARTIAL_SUCCESS`(일부만 성공 — 신규 추가) \| `FAILURE`(전부 실패, 또는 대상DB에 도달하기 전 스케줄/엔진 레벨에서 실패) |
| summary | String | **전체 요약** — 예: "대상DB 3개 중 2개 성공, 1개 실패". SP가 반환한 전체 행을 담지 않는다(교정사항 #6: 감시 서버, 백업 서버 아님) |
| errorMessage | String (nullable) | **스케줄/엔진 레벨** 오류(예: 스케줄 정의 조회 실패)만 담는다 — 1~2줄 수준. 개별 대상DB의 오류는 `targetDbResults[i].errorMessage`에 담기고 여기엔 올라오지 않는다(교정사항 #3 원칙 유지) |
| targetDbResults | `List<TargetDbExecutionResult>` | **신규** — 대상DB별 세부결과. 스케줄/엔진 레벨에서 실패해 대상DB 순회 자체를 시작 못 했으면 빈 리스트일 수 있다 |

### 3.1b `TargetDbExecutionResult` (값 객체, 신규 — 대상DB별 세부결과)

| 필드 | 타입 | 설명 |
|---|---|---|
| targetDbConfigId | Long | 어느 `target_db_config`에 대한 실행인지 |
| targetDbConfigName | String | 화면에 바로 쓸 수 있도록 이름을 같이 담아둔다(§4.3, 매 조회마다 `target_db_config`를 재조회하지 않아도 되게) |
| status | enum | `SUCCESS` \| `FAILURE` (대상DB 1개 단위이므로 PARTIAL 없음) |
| summary | String | 해당 대상DB에서의 처리 요약(예: "처리행 5건, API 호출 5/5 성공") — 전체 결과행 보존 금지 원칙은 여기도 동일 |
| errorMessage | String (nullable) | 해당 대상DB 실행 실패 시 1~2줄 수준 에러 |

**집계 규칙**: 대상DB N개 중 성공이 N개면 `SUCCESS`, 0개면 `FAILURE`, 둘 다 있으면 `PARTIAL_SUCCESS`.

**부분실패 허용 결정(2026-11-16, 근거 명시)**: 대상DB 하나의 실행이 실패해도 나머지 대상DB는 **계속 진행**한다(허용). 근거: 한 업체(대상DB)의 DB 장애·SP 오류가 다른 업체에 대한 실행까지 막으면 안 된다 — 레거시도 애초에 "행 단위로 실패해도 나머지 행은 계속 FCM 발송" 패턴(`executeTask2`가 각 대상마다 개별 try 없이도 한 건의 HTTP 실패가 전체를 중단시키지 않는 구조)과 같은 정신이다. `ScheduleExecutor`는 대상DB별로 개별 try/catch를 두어, 한 대상DB의 `StoredProcedureInvoker`/`ActionHandler` 예외가 다른 대상DB 순회를 막지 않도록 구현한다.

**실행 순서**: 대상DB는 **순차 실행**한다(병렬 아님) — 레거시도 애초에 각 Task가 자신의 단일 DB를 순차적으로 처리했고(동시성 이슈가 없었음), §0 전제(단일 프로세스, 소수의 다운스트림 대상)에서 병렬화로 얻는 이득보다 스레드풀 관리/부분실패 순서 보장의 단순성이 더 크다고 판단한다.

**`ActionHandler`와의 경계**: `ActionHandler`/`ActionOutcome` 인터페이스 자체는 바뀌지 않는다 — `ScheduleExecutor`가 대상DB마다 `handler.handle(schedule, spResultRowsForThisTargetDb)`를 한 번씩 호출하고, 그 결과를 대상DB 식별 정보와 함께 `TargetDbExecutionResult`로 감싸는 책임을 진다. 즉 "여러 대상DB를 안다"는 지식은 `ScheduleExecutor`에만 있고, `ActionHandler` 구현체(`ApiCallActionHandler`/`DisplayOnlyActionHandler`)는 여전히 "대상DB 1개분의 SP 결과를 처리한다"는 좁은 책임만 가진다.

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

**2026-11-16 교정 — 대상DB별로 분리, 섞지 않음**: 스케줄 1개가 대상DB를 여러 개 가질 수 있게 되면서, `DisplayOutput`은 **대상DB 하나분의 결과만** 담도록 식별 정보를 추가한다. "대상DB를 섞어서 합치지 않는다"(§5) 원칙을 이 값 객체 수준에서 강제한다.

- `DisplayOutput`: `scheduleId` + **`targetDbConfigId`/`targetDbConfigName`(신규)** + 컬럼명 리스트 + 행 데이터(해당 대상DB의 SP 결과를 그대로, 또는 스케줄 정의의 컬럼 선택에 따라 추려서) + 생성시각. 여러 대상DB의 행을 한 `DisplayOutput`에 합쳐 담지 않는다.
- `DisplayOutputStore`: 키를 스케줄ID 단일값에서 **(scheduleId, targetDbConfigId) 복합키**로 바꾼다. 각 (스케줄, 대상DB) 조합별로 **가장 최근 1건만** 인메모리(`ConcurrentHashMap`)로 들고 있는 캐시 — "대상DB별 최신 상태 스냅샷". 스케줄 하나의 전체 조회용으로 `getAllForSchedule(scheduleId) -> List<DisplayOutput>`(해당 스케줄에 속한 모든 대상DB의 최신 건)를 추가 제공한다. 이력이 아니라 최신 스냅샷이므로 프로세스 재시작 시 사라져도 무방(다음 실행에서 다시 채워짐)한 원칙은 그대로다. 관리자 웹의 "최근 실행 결과" 화면(대상DB별 섹션 — §7)과 수동실행 응답이 이 캐시를 읽는다.
- 주의: SP가 매우 큰 결과셋을 반환하는 극단적 케이스에 대한 메모리 상한선(예: 최대 N행만 보관, 대상DB별로 개별 적용)은 구현 단계에서 결정하되, 기본 원칙(교정사항 #6, "전체 보존이 목적 아님")을 따른다.

---

## 4. DB 스키마 (메타DB, SQL Server)

> **실행이력 테이블은 이번 라운드에 스키마를 만들지 않는다 (TBD).** 설계 시점에 §3의 `ExecutionResult`/감시-서버 원칙(교정사항 #6: 상태/요약만, 전체 결과행 보존 금지)을 따를 것.
>
> **적용 범위 명확화**: 아래 §4.1~4.6의 Flyway 마이그레이션은 **엔진 자신의 메타DB(스케줄 정의/설정/Quartz 상태 저장용)에만** 적용된다. `target_db_config`가 가리키는 **대상 업무 DB(SP가 실제로 존재하는 업체 DB)에는 Flyway가 전혀 적용되지 않는다** — 엔진은 그 DB에 대해 스키마 변경 권한도, DDL 실행 기능도 갖지 않는다(§10 참고).
>
> **로컬 개발용 H2 fallback (2026-11-16 교정, 사용자 직접 확정)**: 백엔드 기동 시 `DB_URL` 환경변수가 설정되지 않으면 **메타DB를 H2(파일 기반 임베디드 DB)로 자동 fallback**해 `mvn spring-boot:run`만으로 로컬 기동이 가능하게 한다. **이 fallback은 메타DB(§4.1~4.6)에만 적용되고 로컬 개발 전용이다** — `target_db_config`가 가리키는 대상 업무 DB는 이 변경과 무관하게 항상 MSSQL 고정(§1)이며, 운영 환경은 지금처럼 `DB_URL`에 SQL Server를 지정해서 쓴다. Flyway 마이그레이션과 Quartz JDBC JobStore가 현재 SQL Server 전용 DDL(T-SQL, Quartz `tables_sqlServer.sql`)을 쓰고 있으므로, H2에서 기동하려면 H2용 마이그레이션/Quartz delegate를 별도로 구성해야 한다(구체적 구현 방식은 구현 시점에 결정).

### 4.1 `schedule_definition` — 스케줄(Job) 정의

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| name | NVARCHAR(200) | 관리자 UI 표시용 |
| enabled | BIT | false면 Quartz에 등록하지 않음/등록 해제 |
| cron_expression | NVARCHAR(100) | 레거시 3종 고정주기 메서드를 대체하는 Quartz cron (요일 포함 가능 — 별도 "주말 스킵" 필드 불필요). **Quartz cron은 Unix cron과 문법이 다름** — day-of-month 필드와 day-of-week 필드 중 반드시 하나는 `?`로 둬야 함(둘 다 `*`나 둘 다 구체값이면 유효하지 않음) |
| timezone | NVARCHAR(50) | 기본 `Asia/Seoul` |
| sp_name | NVARCHAR(200) | 호출할 저장프로시저명 (레거시 `SP_WORK_LIMIT_LIST` 등에 해당, 리터럴 아님) |
| action_type | NVARCHAR(20) | `API_CALL` \| `DISPLAY_ONLY` (교정사항 #2) |
| action_config | NVARCHAR(MAX) (JSON) | action_type에 따라 모양이 다른 설정. §5 참고 |
| description | NVARCHAR(500) | nullable |
| created_at / updated_at | DATETIME2 | |
| updated_by | NVARCHAR(100) | 관리자 계정명 |

> **2026-11-16 교정 — `target_db_config_id` 컬럼 제거**: 스케줄 1개가 대상DB 1개에 고정되던 단일 FK를 제거하고, 스케줄이 대상DB를 N개 선택할 수 있도록 N:M 조인 테이블(§4.3b `schedule_target_db_config`)로 옮겼다. 아직 운영 전이라 과거 데이터 마이그레이션 걱정 없이 컬럼을 바로 제거한다(과도기 호환 컬럼을 남기지 않음).

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

### 4.3b `schedule_target_db_config` — 스케줄↔대상DB N:M (2026-11-16 신규)

스케줄 1개가 등록된 `target_db_config` 중 여러 개를 선택해, 실행 시 선택된 대상DB 전부에 대해 같은 SP를 호출할 수 있게 하는 조인 테이블. §4.1의 `target_db_config_id` 단일 FK를 대체한다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| schedule_id | BIGINT FK → schedule_definition | ON DELETE CASCADE(스케줄 삭제 시 연결도 같이 삭제) |
| target_db_config_id | BIGINT FK → target_db_config | ON DELETE는 제한(사용 중인 `target_db_config` 삭제 차단 — §6의 기존 "사용 중인 schedule_definition 있으면 차단" 규칙이 이 조인 테이블 기준으로 재정의됨) |

PK는 `(schedule_id, target_db_config_id)` 복합키(같은 대상DB를 같은 스케줄에 중복 선택하는 것을 DB 레벨에서 방지). **스케줄 생성/수정 시 최소 1개 이상의 대상DB가 선택되어야 한다**(API 레벨 검증, §6) — 0개인 상태는 허용하지 않는다.

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
- **2026-11-16 교정**: `groupingMode`/`groupByField`는 **대상DB 1개의 SP 결과 안에서만** 적용된다. 스케줄이 대상DB를 여러 개 선택한 경우(§4.3b) 각 대상DB의 결과는 독립적으로 그룹핑·호출되며, 서로 다른 대상DB의 행을 하나의 그룹/호출로 합치지 않는다(§0 교정 문단, §3.1b 참고) — 어느 업체 데이터인지 불분명해지는 것을 막기 위함

### `action_type = DISPLAY_ONLY`

```json
{
  "displayFormat": "TABLE",
  "columns": ["EMPLOYEE_NO", "EA_TITLE", "EXE_SEQ"]
}
```

- `columns`가 없으면 SP가 반환한 모든 컬럼을 표시
- 외부 호출 없음 — `DisplayOnlyActionHandler`가 SP 결과를 `DisplayOutput`으로 변환해 `DisplayOutputStore`에 적재하는 것으로 끝
- **2026-11-16 교정**: 대상DB를 여러 개 선택한 스케줄이면 대상DB별로 별도의 `DisplayOutput`이 생성·적재된다(§3.3) — 여러 대상DB의 행을 하나의 `DisplayOutput`으로 합치지 않는다

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
| GET | /api/schedules | 목록 (enabled, 다음 실행시각, **`targetDbConfigs: [{id, name}]`**(2026-11-16 신규 — 어느 대상DB들을 포함하는지, §7 ScheduleList 표시용) 등 포함) |
| GET | /api/schedules/{id} | 단건 조회(파라미터 포함, **`targetDbConfigIds`**/`targetDbConfigs` 포함) |
| POST | /api/schedules | 생성 → 즉시 Quartz 등록(enabled=true인 경우). **`targetDbConfigIds: number[]`**(2026-11-16, 기존 단수 `targetDbConfigId` 폐지) — 최소 1개 이상 필수, 0개면 400 |
| PUT | /api/schedules/{id} | 수정 → cancel 후 재등록(cron/파라미터/action_config/**`targetDbConfigIds`** 변경 반영) — `targetDbConfigIds`도 같은 요청 바디에서 통째 교체(전체 배열을 다시 보냄, §4.3b 조인 테이블을 해당 스케줄 기준으로 전량 교체) |
| DELETE | /api/schedules/{id} | 삭제 → Quartz 등록 해제, `schedule_target_db_config` 연결도 CASCADE로 함께 삭제 |
| PATCH | /api/schedules/{id}/enabled | 활성/비활성 토글 → Quartz add/remove |
| PUT | /api/schedules/{id}/parameters | 파라미터 목록 전체 교체(단순화를 위해 개별 CRUD 대신 통째 교체) |

### 실행 (교정사항 #4)
| Method | Path | 설명 |
|---|---|---|
| POST | /api/schedules/{id}/run-now | **즉시실행**. 정규 스케줄과 무관하게 1회 실행하고 `ExecutionResult`(전체 요약 + **`targetDbResults: TargetDbExecutionResult[]`**, 2026-11-16 확장 — §3.1/§3.1b)를 응답 바디로 동기 반환. `DISPLAY_ONLY`면 대상DB별 `DisplayOutput`도 함께 포함(대상DB별로 섞지 않고 분리된 배열로). 이미 실행 중이면 중복 실행 거부 응답(상태코드로 구분) |
| GET | /api/schedules/{id}/last-result | `DisplayOutputStore.getAllForSchedule(id)`/마지막 `ExecutionResult`의 인메모리 캐시 조회(영속 이력 아님 — 프로세스 기동 이후 최신 1건). **2026-11-16**: 응답이 대상DB별로 그룹화된 배열을 담는다(§7 LastResult 화면이 대상DB별 섹션으로 렌더링) |
| GET | /api/schedules/{id}/status | Quartz 기준 다음 실행 예정 시각, 현재 실행중 여부 |

### 대상 DB 설정
| Method | Path | 설명 |
|---|---|---|
| GET | /api/config/target-db | 목록(비밀번호 마스킹) |
| POST | /api/config/target-db | 생성(비밀번호 암호화 저장) |
| PUT | /api/config/target-db/{id} | 수정 |
| DELETE | /api/config/target-db/{id} | 삭제(`schedule_target_db_config`에서 참조 중이면 차단 — 2026-11-16, 기존 단일 FK 참조에서 N:M 조인 테이블 참조로 변경) |
| POST | /api/config/target-db/test-connection | **연결 테스트(2026-11-18 신규, 저장 전)** — 아직 저장되지 않은 입력값(jdbcUrl/username/password, 평문)을 바디로 받아 1회성 JDBC 연결을 시도하고 결과만 반환. 저장하지 않음 |
| POST | /api/config/target-db/{id}/test-connection | **연결 테스트(2026-11-18 신규, 저장된 설정)** — id로 저장된 설정을 조회해 서버가 복호화한 뒤 연결 테스트. 평문 비밀번호는 요청/응답 어디에도 포함되지 않음 |

**연결 테스트 설계 근거 (2026-11-18 신규, 사용자 직접 확정)**:
- 두 엔드포인트 모두 응답 모양을 `{success: boolean, message: string}`로 통일한다.
- **호출 방식**: 커넥션 풀을 쓰지 않는 1회성 `DriverManager.getConnection(jdbcUrl, username, password)` 연결을 시도하고, 성공하면 `SELECT 1`류 최소 쿼리로 응답을 한 번 확인한 뒤 즉시 연결을 닫는다.
- **타임아웃**: 짧은 타임아웃(3~5초)을 강제한다(`DriverManager.setLoginTimeout` 등) — 응답 없는 대상DB 때문에 이 API 자체가 오래 걸리면 안 된다. §3.1b의 "대상DB 하나가 느려도 스케줄 실행 전체가 막히면 안 된다"와 같은 정신을, 여기서는 "관리자 화면/API가 느린 대상DB 때문에 오래 걸리면 안 된다"로 적용한 것이다.
- **보안**: JDBC 드라이버 예외 메시지가 접속정보(URL/비밀번호)를 그대로 포함하는 경우가 있으므로, 클라이언트에 돌려주는 `message`는 서버가 정제(sanitize)한 문구만 담는다 — 원본 예외(필요하면 전체 스택트레이스)는 서버 로그에만 남기고, 그 로그에도 평문 비밀번호가 찍히지 않도록 한다(`CredentialCipher`로 암호화해 저장하는 기존 원칙과 같은 정신 — 평문이 어디에도 흔적을 남기지 않게). 두 엔드포인트 모두 기존 인증 체계(Spring Security, ADMIN) 그대로 보호되며, 이를 위한 `SecurityConfig` 변경은 필요 없다(기존 `/api/**` 보호 규칙에 자연히 포함됨).
- **영속화하지 않음**: 테스트 결과는 DB에 저장하지 않는다 — §3/§10의 "감시 서버, 백업 서버 아님" 원칙과 같은 정신으로, 상태는 매 요청 시점에 실시간으로만 확인하면 충분하다고 판단한다. `target_db_config` 테이블에 상태 컬럼을 추가하지 않는다(스키마 변경 없음, §4.3 그대로).
- **범위 밖(이번 라운드)**: 자동 주기적 백그라운드 헬스체크(예: 스케줄러가 모든 대상DB를 주기적으로 핑)는 포함하지 않는다 — 목록 화면의 상태 뱃지는 페이지 로드 자동 호출이 아니라 각 행의 "테스트" 버튼 클릭으로 on-demand 수행한다(§7). 근거: 목록을 열 때마다 등록된 모든 대상DB에 동시 접속을 시도하면 단순 조회 화면치고 불필요한 외부 트래픽/지연이 생기고, "자동 주기 헬스체크는 범위 밖"이라는 결정과도 같은 방향이다. 필요해지면 별도 라운드에서 설계(§10에 추가됨).

### 자격증명(Credential)
| Method | Path | 설명 |
|---|---|---|
| GET | /api/config/credentials | 목록(값 마스킹 — 끝 4자만) |
| POST | /api/config/credentials | 생성(암호화 저장) |
| PUT | /api/config/credentials/{id} | 값 교체 |
| DELETE | /api/config/credentials/{id} | 삭제(참조 중인 action_config 있으면 경고) |

---

## 7. 화면 목록 (관리자 웹, React SPA — 백엔드와 별도 앱)

화면 목록(8개)은 교정 전과 동일하고, 구현 기술은 Thymeleaf 서버 렌더링 → React 컴포넌트/페이지로 바뀌었다(3차 교정). 각 페이지는 `frontend/src/api/` 클라이언트를 통해서만 백엔드 `/api/**`를 호출한다(페이지가 직접 DB/Quartz를 알지 못함). **2026-11-16 교정**: ScheduleList/ScheduleForm/RunResult/LastResult 4개 페이지는 아래처럼 대상DB 다중선택·구조화 cron 입력·대상DB별 섹션 표시가 추가되어 내용이 확장됐다(화면 "목록" 자체는 그대로, 각 화면의 "내용"은 변경됨).

1. **Login 페이지** — 로그인 폼 → `/api/auth/login` 호출, 인증 토큰/세션 저장
2. **ScheduleList 페이지** — 스케줄 목록 테이블: enabled 토글, cron(사람이 읽을 수 있는 요약 문구 — 아래 §9 참고 — 와 원본 cron 둘 다), 다음 실행 예정시각, 마지막 실행 상태(전체 요약 뱃지: 성공/부분성공/실패 — 인메모리 캐시 기반, "이력 아님" 명시), **포함된 대상DB 이름 목록(2026-11-16 신규 — `target_db_config.name`을 배지/칩 형태로 나열, §4.3b)**, "지금 실행" 버튼
3. **ScheduleForm 페이지** — 생성/수정 공용: 기본정보(이름/timezone/SP명) + **반복주기 입력(2026-11-16 신규 — §9의 구조화 cron 빌더, cron 텍스트 입력을 대체)** + **대상 DB 다중 선택(2026-11-16 신규 — 체크박스/멀티셀렉트, 최소 1개 이상 필수, §4.3b)** + 파라미터 편집기 컴포넌트(행 추가/삭제, 이름/타입/값소스/리터럴값) + `action_type` 선택에 따라 폼 영역이 동적으로 전환되는 컴포넌트:
   - `API_CALL` 선택 시: method/url/headers(키-값 추가, 값에 `${cred:NAME}` 선택 가능하도록 credential 목록 드롭다운 제공)/groupingMode/groupByField/bodyTemplate(placeholder 안내 포함) — groupingMode/groupByField는 선택된 대상DB 각각의 결과 안에서만 적용됨을 폼에 안내 문구로 표시
   - `DISPLAY_ONLY` 선택 시: displayFormat/columns 선택
4. **RunResult 컴포넌트/모달** — "지금 실행" 클릭 시 `/api/schedules/{id}/run-now` 응답을 같은 화면에서 모달 또는 인라인으로 바로 표시. **2026-11-16 변경**: 상단에 전체 요약(성공/부분성공/실패, 대상DB N개 중 M개 성공)을 먼저 보여주고, 그 아래 **대상DB(`target_db_config.name`)별로 섹션을 나눠** 각 섹션에 해당 대상DB의 summary/errorMessage/(DISPLAY_ONLY면) 표 결과를 표시 — 대상DB 데이터를 하나로 합쳐 보여주지 않는다
5. **LastResult 페이지** — 스케줄별 `last-result` 조회(= `DisplayOutputStore`/`ExecutionResult` 캐시 열람, 영속 이력이 아니라는 안내 문구 포함). **2026-11-16 변경**: RunResult와 동일하게 전체 요약 + **대상DB별 섹션 구분**으로 렌더링
6. **TargetDbConfig 페이지** — 대상 DB 설정 목록/생성/수정/삭제, 비밀번호 마스킹. **2026-11-18 신규**: 생성/수정 폼에 "연결 테스트" 버튼 추가 — 클릭 시 폼에 입력된 현재 값(아직 저장 전, 평문)으로 `POST /api/config/target-db/test-connection`을 호출하고, 결과를 폼 안에 인라인으로 초록 "연결 성공" / 빨강 "연결 실패: {message}"로 표시한다. 목록 테이블에는 "연결 상태" 컬럼을 추가해 각 행에 상태 뱃지(초록=성공/빨강=실패/회색=미확인 — 3가지)와 "테스트" 버튼을 둔다. 뱃지는 페이지 로드 시 자동으로 채우지 않고 **해당 행의 "테스트" 버튼을 클릭했을 때만** `POST /api/config/target-db/{id}/test-connection`을 호출해 on-demand로 채운다(§6 근거 참고 — 자동 백그라운드 헬스체크 아님). 뱃지 상태는 프론트 메모리에만 있고 영속화되지 않으므로 새로고침하면 다시 "미확인"으로 초기화된다
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
| (레거시에는 없음 — 각 Task가 `Config.getDbUrl()` 단일 DB에 고정) | 스케줄당 대상DB 다중 선택(`schedule_target_db_config` N:M, §4.3b) — 대상DB별로 SP 호출/액션을 독립 수행, 부분실패 허용(2026-11-16 신규, 교정사항) |
| (레거시에는 없음 — cron 문법을 코드로 직접 작성) | 구조화 입력(년/월/일/시/분/요일/간격) → Quartz cron 변환 UI(§9, 2026-11-16 신규) |

---

## 9. 반복주기 구조화 입력 → Quartz cron 변환 설계 (2026-11-16 신규)

관리자가 Quartz cron 문법을 몰라도 스케줄 주기를 등록할 수 있도록, 년/월/일/시/분을 구조화된 입력으로 받아 cron으로 변환한다. **DB의 `cron_expression` 컬럼에는 항상 유효한 Quartz cron 문자열만 저장된다** — 구조화 입력은 그 문자열을 만들어내는 UI 계층일 뿐, 스키마 변경은 없다(§4.1 `cron_expression` 컬럼 그대로).

### 9.1 어디서 변환할 것인가 (결정)

**구조화 입력 ↔ cron 변환(양방향)은 프론트엔드(TypeScript)에서 수행한다.** 백엔드는 지금처럼 최종 저장 시 Quartz `CronExpression.isValidExpression`으로 **검증만** 한다(기존 T8 범위 그대로, 변경 없음).

근거:
- 아래 6가지 패턴은 cron 필드 위치가 고정된 **결정적(deterministic) 문자열 생성**이라 Quartz 라이브러리 없이도 TS로 정확히 만들 수 있다(파싱이 필요한 임의 cron 해석이 아니라, 우리가 정의한 좁은 패턴 집합의 생성/역생성이므로).
- 매 입력 변경마다 백엔드 왕복을 요구하면(예: 키 입력마다 호출) UX가 나빠진다. 생성 로직을 프론트에 두면 즉시 미리보기(생성된 cron, 다음 실행 예시)를 보여줄 수 있다.
- 다만 "프론트가 만든 cron이 실제로 유효한 Quartz cron인가"는 **백엔드가 여전히 최종 검증**하므로(기존 `CronExpression.isValidExpression` 유지), 프론트 로직에 버그가 있어도 잘못된 cron이 DB에 저장되는 일은 없다 — 이 이중 체크가 프론트/백엔드 cron 로직 drift 위험의 안전망이다.
- 대안으로 "백엔드에 변환 엔드포인트(`/api/schedules/cron/build`)를 두는 안"도 고려했으나, 생성 규칙이 결정적이라 추가 API 왕복이 주는 이득이 적고, 이 프로젝트 규모(§0)에서 불필요한 엔드포인트 증가로 판단해 기각했다.

### 9.2 지원하는 6가지 반복 유형과 cron 생성 규칙

Quartz cron 필드 순서: `초 분 시 일(day-of-month) 월 요일(day-of-week) [년(선택)]`. day-of-month/day-of-week 중 하나는 반드시 `?`(§4.1/§8의 Quartz 고유 규칙).

| # | 유형 | 입력 필드 | 생성 규칙(예) |
|---|---|---|---|
| 1 | 일별 | 시(HH), 분(mm) | `0 {mm} {HH} * * ?` |
| 2 | 월별 | 일(1~31), 시, 분 | `0 {mm} {HH} {일} * ?` |
| 3 | 년별 | 월(1~12), 일, 시, 분 | `0 {mm} {HH} {일} {월} ?` |
| 4 | 요일반복 | 요일 다중선택(MON..SUN), 시, 분 | `0 {mm} {HH} ? * {MON,WED,FRI 등 콤마결합}` |
| 5 | 간격반복 | 시작시각 HH:mm, 종료시각 HH:mm, 간격값 N, 간격단위(분/시간), 요일 필터(선택, 기본 전체 요일) | 분 단위: `0 {시작분}/{N} {시작시}-{종료시} ? * {요일}`. 시간 단위: `0 {시작분} {시작시}-{종료시}/{N} ? * {요일}` — 레거시 `HourSchedulerTask`(평일 매시 정각·30분) 패턴과 동일 개념 |
| 6 | 1회성 특정일시 | 연-월-일, 시, 분 | `0 {mm} {HH} {일} {월} ? {연}` — Quartz cron의 선택적 7번째 "년" 필드를 사용해 그 해/월/일/시각에 **단 1회만** 매칭되게 함(반복 없음) |

**1회성(#6) 관련 유의점**: 해당 연-월-일시가 지나면 트리거는 다시는 발화하지 않는다(Quartz가 년 필드로 자연스럽게 처리) — 레거시에 없던 신규 기능이며 별도의 "반복 안 함" 플래그 컬럼 없이 cron 자체로 표현된다. `enabled`를 자동으로 false로 되돌릴지는 구현 단계에서 결정(관리자가 "이미 지나간 1회성 스케줄"을 목록에서 구분해 볼 수 있어야 UX상 혼란이 없다 — §7 ScheduleList 화면에 반영 여지를 남겨둠).

**간격반복(#5) 관련 유의점**: 분 단위 간격은 `시작분/N` + `시작시-종료시` 조합(Quartz는 범위와 증분을 함께 쓰는 `a-b/c` 문법을 지원)을 쓴다. 분/시간 조합이 정확히 맞아떨어지지 않는 경계 케이스(예: 종료시각의 분이 간격과 어긋나는 경우)가 있을 수 있으므로, 구현 시 단위 테스트로 경계값을 검증할 것(TASKS.md 해당 작업의 DoD에 포함).

### 9.3 수정(edit) 시 역변환 — "고급: 직접 cron 입력"으로 폴백

기존 스케줄을 수정할 때, 저장된 `cron_expression` 문자열을 위 6가지 생성 규칙의 "역" 정규식으로 매칭을 시도한다(생성 규칙이 결정적이므로 역방향도 결정적 패턴 매칭으로 가능 — 예: `0 \d+ \d+ \* \* \?` 형태면 "일별", `0 \d+ \d+ \? \* [A-Z,\-]+` 형태면 "요일반복" 등).

- **매칭되면**: 해당 유형의 구조화 폼을 그 값으로 복원해서 보여준다.
- **매칭되지 않으면**(레거시 임포트, 수동 DB 편집, 6가지 패턴 밖의 임의 cron 등): **"고급: 직접 cron 입력" 모드**로 폴백한다 — cron 텍스트 입력창(현재 `ScheduleFormPage.tsx`에 이미 있는 그 입력 방식)을 그대로 보여주고 원본 cron 값을 채워 넣는다. **이 모드는 항상 선택 가능하게 남겨둔다**(구조화 폼과 토글 전환 가능) — 완전히 막지 않는다는 요구사항을 그대로 반영.
- 저장 시 어느 모드든 최종적으로 문자열 하나(cron)가 되고, 백엔드는 그 문자열만 보고 검증한다 — 백엔드 입장에서 "구조화 모드로 만들었는지 고급 모드로 직접 썼는지"는 구분하지 않는다(별도 컬럼 없음, §4.1 그대로).

---

## 10. 범위 밖 (이번 라운드에서 하지 않는 것)

- 실행이력(로그) 테이블 스키마/마이그레이션 설계 — TBD, 추후 별도 라운드
- 실패 기록의 영속화 — `ExecutionResult`에 실패 상태/에러메시지는 포함하되 저장은 하지 않음
- 멀티테넌시(한 프로세스가 여러 업체를 동시 서빙) — §0 근거에 따라 범위 밖
- 복잡한 역할/권한 체계 — 단일 ADMIN 역할만
- 다중 인스턴스 운영을 전제로 한 설계(분산 락 등 직접 구현) — Quartz JDBC JobStore가 부가적으로 제공하지만, 이를 적극적으로 활용/검증하는 것은 범위 밖(§0 전제: 단일 인스턴스)
- 대상 업무 DB에 대한 **DDL 실행/신규 SP 생성 기능** — 엔진은 업체 DB에 **이미 존재하는 SP를 호출만** 하고, 생성/수정하지 않는다(사용자 확인: "의미 없을 것 같다, DDL 생성안은 보류" — 이번뿐 아니라 앞으로도 범위에 넣지 않기로 확정)
- 대상DB **자동 주기적 백그라운드 연결 헬스체크** — 이번 라운드(2026-11-18)는 관리자 화면에서의 on-demand 연결 테스트(버튼 클릭 시 1회성 확인)만 지원한다. 모든 대상DB를 주기적으로 자동 핑하는 기능은 필요해지면 별도 라운드에서 설계한다
- 대상 업무 DB에 대한 멀티-DBMS 지원 — MSSQL로 고정(§1, §4.3 참고)

---

## 11. 배포 방식 (Tomcat 단일 WAR, 2026-11-17 신규)

**전제가 바뀌었다**: 지금까지 전제했던 "백엔드는 jar+내장톰캣 실행, 프론트는 별도 정적빌드+별도 서빙"(§1 "배포 형태"/"관리자 웹 UI" 원래 행, §2의 "백엔드/프론트엔드가 독립적으로 빌드·배포")은 **실제 배포 가능한 인프라가 외부 Tomcat뿐**이라는 제약 때문에 Tomcat 단일 WAR 배포로 바뀐다. "백엔드/프론트 완전분리 배포" 원칙(2026-11-13 3차 교정)은 **이번 운영 배포 방식에서만** 깨는 것으로 확정됐다 — 로컬 개발 시 분리 실행(프론트 dev서버 5173 + 백엔드 8080)은 그대로 유지한다.

### 11.1 WAR 패키징 전환 + 로컬 개발 워크플로 보존

- `backend/pom.xml`의 `<packaging>jar</packaging>` → `<packaging>war</packaging>`로 변경.
- `SchedulerServiceApplication`이 `SpringBootServletInitializer`를 extends하고 `configure(SpringApplicationBuilder)`를 오버라이드해 `sources(SchedulerServiceApplication.class)`를 반환하도록 변경 — 외부 서블릿 컨테이너(Tomcat)가 WAR를 배치할 때 이 클래스를 통해 Spring 컨텍스트를 띄운다. `main()`은 그대로 남겨 jar 실행/로컬 개발 경로도 유지한다.
- 내장 톰캣(`spring-boot-starter-tomcat`, 지금은 `spring-boot-starter-web`이 전이의존성으로 끌어옴)을 **`provided` 스코프로 명시적으로 선언**해 외부 Tomcat의 자체 서블릿 컨테이너와 클래스 충돌을 피한다. WAR에는 포함되지 않지만, 컴파일/테스트/로컬 실행 시점에는 여전히 classpath에 존재한다.
- **로컬 개발 워크플로가 깨지지 않는 근거**: Maven의 `provided` 스코프는 "최종 패키지(war)에는 포함하지 않지만, 컴파일·테스트·그리고 `spring-boot-maven-plugin`의 `run` 목표가 구성하는 실행 classpath에는 포함한다"는 의미다. Spring Boot 공식 문서가 "배치 가능한 WAR 만들기" 패턴으로 바로 이 조합(WAR 패키징 + 내장 컨테이너 `provided` + `SpringBootServletInitializer`)을 권장하며, 이 조합에서도 `mvn spring-boot:run`이 내장 톰캣으로 정상 기동되는 것은 널리 쓰이는 표준 패턴이다(이 프로젝트가 처음 시도하는 구성이 아님). 다만 "문서상 근거"와 "이 프로젝트에서 실제로 동작 확인"은 다른 문제이므로, TASKS.md의 해당 작업(T23)에 **실제로 `mvn spring-boot:run`이 WAR 전환 후에도 로컬에서 기동되는지 회귀 테스트**를 DoD로 명시해 둔다 — 문서의 주장만으로 끝내지 않는다.

### 11.2 프론트엔드 정적 리소스를 백엔드 WAR에 통합 — 단일 빌드 파이프라인

**목표**: `mvn clean package` 한 번으로 "백엔드 컴파일 + 프론트 빌드(`npm run build`) + 그 결과물을 백엔드 WAR의 정적 리소스 경로로 복사"가 전부 끝난다. 개발자가 따로 `npm run build`를 수동으로 먼저 돌릴 필요가 없다.

- **`frontend-maven-plugin`**(eirslett)을 `backend/pom.xml`에 추가해, Maven 빌드 생명주기 안에서 Node.js/npm을 설치하고(버전 고정) `npm install` → `npm run build`를 실행한다. `workingDirectory`를 `${project.basedir}/../frontend`로 지정해 백엔드 pom이 형제 디렉터리인 프론트엔드를 빌드하게 한다.
- 프론트엔드 빌드 산출물은 그대로 `frontend/dist/`에 생성된다(소스 트리를 더럽히지 않음). **`maven-resources-plugin`**의 `copy-resources` 실행을 `generate-resources` 단계(Spring Boot의 기본 리소스 처리보다 먼저)에 바인딩해 `frontend/dist/**`를 `${project.build.outputDirectory}/static`(즉 `target/classes/static/`)로 복사한다 — `src/main/resources/static`이 아니라 **빌드 산출물 디렉터리(`target/`)로 복사**하는 쪽을 선택한다. 이유: `frontend/dist`가 매 빌드마다 바뀌는 산출물이라 `src/main/resources/static`에 커밋하면 소스 트리에 빌드 아티팩트가 섞이고 git diff가 오염된다 — `target/classes/static`은 이미 `.gitignore` 대상인 빌드 디렉터리라 깨끗하다.
- Spring Boot의 기본 정적 리소스 서빙(`classpath:/static/`)이 이 `target/classes/static/`을 그대로 읽으므로, 별도 리소스 핸들러 등록 없이 `index.html`/JS/CSS가 서빙된다(SPA 폴백은 §11.3에서 별도로 다룸).
- **DoD 수준의 확인 필요 사항**(TASKS.md에 명시): `mvn clean package`만 실행했을 때 Node/npm이 로컬에 전혀 설치되어 있지 않은 깨끗한 환경에서도 빌드가 끝까지 성공하는지(= `frontend-maven-plugin`이 Node를 로컬에 다운로드하는 것으로 충분한지) 확인.

### 11.3 SPA 라우팅(React Router) ↔ 정적 리소스 서빙 충돌 해결

브라우저가 `/schedules`처럼 React Router가 다루는 경로를 **직접 새로고침/딥링크**로 요청하면, 그 경로에 해당하는 정적 파일이 없으므로 서버가 404를 낸다. 이를 막기 위해 "정적 파일로 존재하지 않고 `/api/**`도 아닌 모든 GET 요청은 `index.html`로 포워드"하는 폴백이 필요하다.

- **선택한 방식**: `WebMvcConfigurer.addResourceHandlers`에 `/**`를 매핑하되, `PathResourceResolver`를 오버라이드해 "요청한 경로의 리소스가 실제로 존재하면 그걸 서빙하고, 존재하지 않으면 `index.html`을 반환"하도록 구성한다(Spring Boot + SPA 통합에서 널리 쓰이는 표준 패턴).
- **`/api/**`가 이 폴백에 걸리지 않는 이유**: Spring MVC의 `HandlerMapping` 우선순위상 `@RestController`의 `@RequestMapping("/api/...")`로 등록된 `RequestMappingHandlerMapping`이 정적 리소스용 `SimpleUrlHandlerMapping`보다 먼저 매칭을 시도한다 — 즉 `/api/**` 요청은 애초에 이 리소스 핸들러까지 도달하지 않는다(별도의 명시적 `exclude` 설정이 필요 없음). 다만 이건 "Spring의 기본 동작에 의존하는 암묵적 배제"이므로, TASKS.md에 **인증 없이 `/api/schedules`를 호출하면 401이 오고 `index.html`(200)이 오지 않는다는 회귀 테스트**를 DoD로 명시해 이 가정이 틀리지 않았는지 반드시 확인한다.
- 대안으로 "경로에 점(`.`)이 없으면 forward"하는 정규식 기반 포워딩 컨트롤러 방식도 검토했으나, 정적 리소스 서빙과 폴백을 하나의 메커니즘(리소스 체인)으로 처리할 수 있는 `PathResourceResolver` 방식이 더 단순해 채택했다.

### 11.4 컨텍스트 경로 결정 — `ROOT.war` 권장 + API 베이스 URL 상대경로화

- Tomcat은 배치된 WAR 파일명을 컨텍스트 경로로 쓴다(`scheduler-service.war` → `/scheduler-service`, `ROOT.war` → `/`). **운영자에게 `ROOT.war`로 배치하는 것을 권장안으로 제시한다** — 컨텍스트 경로 프리픽스를 프론트/백엔드 양쪽 어디서도 신경 쓸 필요가 없어 가장 단순하다.
- 그와 무관하게 더 안전한 설계는 **프론트엔드가 API를 항상 상대경로로 호출**하게 하는 것이다 — 같은 WAR에서 서빙되는 한 프론트/백엔드는 항상 같은 오리진·같은 컨텍스트 경로 아래에 있으므로, `fetch("/api/schedules")`처럼 **오리진을 포함하지 않는 경로만 쓰면 컨텍스트 경로가 무엇이든(`/` 든 `/scheduler-service` 든) 그대로 동작**한다(브라우저가 현재 페이지와 같은 오리진으로 자동 해석).
- `frontend/src/api/client.ts`의 `API_BASE_URL`은 지금 `import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080"`로, 로컬 개발(백엔드 분리 실행) 시 절대 URL이 필요하다. **운영 빌드(`npm run build`)에서는 `VITE_API_BASE_URL`을 빈 문자열로 설정**해 `${API_BASE_URL}${path}`가 `${path}`(상대경로)가 되게 한다 — Vite의 `??`(nullish coalescing)는 빈 문자열을 "값 있음"으로 취급하므로 코드 변경 없이 `.env.production`에 `VITE_API_BASE_URL=`(빈 값)만 두면 된다. 로컬 개발용 `.env.local`(절대 URL, `http://localhost:8080`)은 그대로 유지 — Vite는 모드별로 `.env.local`(dev)과 `.env.production`(build)을 다르게 읽으므로 두 설정이 충돌하지 않는다.
- `ROOT.war`가 아니라 컨텍스트 경로가 있는 이름으로 배치해야 하는 운영 사정이 생기면, 상대경로 방식이라 프론트/백엔드 양쪽 다 코드 변경이 필요 없다는 점이 이 설계의 핵심 이점이다.

### 11.5 CORS 설정의 운명 — 유지, 통합배포에서는 사실상 미사용

- `CorsConfig`(`app.cors.allowed-origin`)는 **제거하지 않고 그대로 둔다**. 통합배포(같은 오리진)에서는 브라우저가 CORS 프리플라이트 자체를 트리거하지 않으므로 이 설정이 관여하지 않지만(사실상 미사용), 로컬 개발 시 프론트 dev 서버(`localhost:5173`)와 백엔드(`localhost:8080`)를 분리 실행하는 기존 워크플로를 계속 지원하려면 CORS가 여전히 필요하다.
- 운영 환경의 `CORS_ALLOWED_ORIGIN` 환경변수는 통합배포에서는 실질적으로 호출될 일이 없는 값이 되지만(같은 오리진이라 CORS 체크 자체가 안 일어남), 비워두거나 운영 도메인 자체를 넣어도 무해하다 — 관련 코드/설정을 조건부로 끄는 분기를 추가하지 않는다(불필요한 복잡도).

### 11.6 환경변수 공급 방식 — 외부 Tomcat에서 `setenv.sh`/`setenv.bat` + 시스템 프로퍼티

외부 Tomcat은 쉘 `export`로 기동되는 게 아니라 `catalina.sh`/`catalina.bat`(또는 서비스로 등록된 Tomcat)로 기동되므로, `$CATALINA_HOME/bin/setenv.sh`(Linux/Mac) 또는 `setenv.bat`(Windows) 파일에 `JAVA_OPTS`로 JVM 시스템 프로퍼티(`-D` 플래그)를 주입하는 방식을 쓴다. Tomcat은 기동 시 이 파일이 있으면 자동으로 source/호출한다(Tomcat 표준 확장 지점, `catalina.sh`가 존재 여부를 체크해 자동 로드).

- **Spring이 `-D` 시스템 프로퍼티와 OS 환경변수를 모두 해석하므로 코드 변경이 필요 없는 이유**: `application.yml`의 `${DB_URL}` 같은 플레이스홀더는 Spring의 `PropertySourcesPlaceholderResolver`가 해석하는데, Spring Boot는 기본적으로 JVM 시스템 프로퍼티(`System.getProperties()`, `-D`로 주입)와 OS 환경변수(`System.getenv()`) 둘 다를 `PropertySource`로 등록해 둔다. `-DDB_URL=...`로 넘긴 시스템 프로퍼티는 정확히 키 이름 `DB_URL`로 노출되므로, OS 환경변수로 `DB_URL`을 주입하던 것과 `${DB_URL}` 플레이스홀더 해석 결과가 동일하다 — 즉 "OS env var 대신 JVM 시스템 프로퍼티로 주입"은 순수한 배포 환경 차이일 뿐, `application.yml`이나 자바 코드를 전혀 바꿀 필요가 없다.
- **예시 (`setenv.sh`, Linux Tomcat)**:
  ```sh
  export JAVA_OPTS="$JAVA_OPTS \
    -DDB_URL=jdbc:sqlserver://meta-db-host;databaseName=scheduler_meta \
    -DDB_USERNAME=scheduler_app \
    -DDB_PASSWORD=*** \
    -DMASTER_ENCRYPTION_KEY=*** \
    -DJWT_SIGNING_KEY=*** \
    -DINITIAL_ADMIN_USERNAME=admin \
    -DINITIAL_ADMIN_PASSWORD=*** \
    -DCORS_ALLOWED_ORIGIN=http://localhost:5173"
  ```
- **예시 (`setenv.bat`, Windows Tomcat)**:
  ```bat
  set "JAVA_OPTS=%JAVA_OPTS% -DDB_URL=jdbc:sqlserver://meta-db-host;databaseName=scheduler_meta -DDB_USERNAME=scheduler_app -DDB_PASSWORD=*** -DMASTER_ENCRYPTION_KEY=*** -DJWT_SIGNING_KEY=*** -DINITIAL_ADMIN_USERNAME=admin -DINITIAL_ADMIN_PASSWORD=*** -DCORS_ALLOWED_ORIGIN=http://localhost:5173"
  ```
- 값에 특수문자(base64의 `+`/`=` 등)가 있으면 셸/배치 인용(quoting) 규칙에 주의해야 한다는 점을 README에 명시(TASKS.md 해당 작업).
- `setenv.sh`/`setenv.bat`는 Tomcat 설치본(`$CATALINA_HOME/bin/`) 안에 두는 파일이라 **이 레포에는 커밋하지 않는다** — README에 "이 내용을 Tomcat의 `bin/setenv.sh`에 넣으라"는 안내와 예시만 제공한다.

### 11.7 이번 배포방식 변경이 깨는 것 / 깨지 않는 것 요약

| | 깨지는가 | 비고 |
|---|---|---|
| 백엔드/프론트 "완전분리 배포" 원칙(3차 교정) | **운영 배포에서만 깨짐** | 로컬 개발 분리 실행은 유지(§11.1/§11.5) |
| `mvn spring-boot:run` 로컬 개발 워크플로 | 안 깨짐(의도) | §11.1, 단 TASKS.md에서 실측 회귀 테스트로 재확인 |
| CORS 설정 | 안 깨짐(유지) | §11.5, 통합배포에서는 미사용일 뿐 |
| 프론트 API 호출 방식 | **바뀜** | 절대 URL → 운영 빌드에서 상대경로(§11.4) |
| `application.yml`/자바 코드의 환경변수 해석 로직 | 안 바뀜 | §11.6, `-D` 시스템 프로퍼티도 동일하게 해석됨 |
