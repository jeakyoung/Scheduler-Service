# 작업 로그 (sched-orchestrator)

## 2026-11-13 09:52 — 킥오프
- sched-orchestrator가 herdr 환경 확인 완료 (herdr CLI 정상, sched-planner/researcher/worker/reviewer 4개 sibling 에이전트 idle 상태로 w6:p3~p6에 대기 중 확인).
- 신규 레포 `C:\Github\Scheduler-Service` 확인: `git init` 완료, 커밋 없음, branch `jeakyoung`.
- 다음 단계: sched-researcher에게 (a) 동적 스케줄 관리 구현 방식, (b) 비밀값 없는 멀티테넌트/범용 설정 설계, (c) 추천 기술 스택 조사를 지시하고 `.team/RESEARCH.md` 작성 요청.

## 2026-11-13 10:0x — 방향 교정
- 사용자 피드백: "바닥부터 새 아키텍처 리서치"가 아니라, 레거시의 `SchedulerContextListener`(ScheduledExecutorService 기반 등록) + Task(Runnable) 패턴 + 'SP 호출 → 결과 가공 → FCM 발송' 실행 흐름을 실제 코드 레벨에서 포팅/재사용하는 것이 목표. 바뀌는 부분은 (1) 하드코딩된 3개 Task를 DB/저장소 기반 동적 스케줄 정의로 교체, (2) Config.java의 비밀값 하드코딩을 외부 설정 주입으로 교체 — 이 두 가지뿐이고 나머지 핵심 동작(SP 호출, FCM payload 구조 등)은 레거시 로직을 그대로 일반화해서 가져간다.
- sched-researcher가 레거시 코드를 깊이 들여다보지 않고 범용 아키텍처(Quartz 등) 비교조사부터 진행 중이어서, `herdr agent send-keys sched-researcher esc`로 중단시킴 (RESEARCH.md 미작성 상태에서 중단 확인됨). 교정된 지시로 재시작.
- 다음 단계: sched-researcher에게 레거시 소스(SchedulerContextListener.java, DaySchedulerTask.java, HourSchedulerTask.java, PurchaseReqSchedulerTask.java, Config.java)를 실제로 읽고, 그 실행 흐름/패턴을 보존한 채 (1) 동적 스케줄 등록 방식, (2) 외부 설정 주입 방식만 조사해서 `.team/RESEARCH.md`에 정리하도록 재지시.
- (orchestrator가 직접) SchedulerContextListener.java / DaySchedulerTask.java / PurchaseReqSchedulerTask.java 를 읽고 정확한 실행 흐름 확인: ScheduledExecutorService(newScheduledThreadPool(3)) + scheduleAtFixedRate(계산된 initialDelay, period) 패턴, 각 Task는 isTaskRunning 중복차단 → SP 호출(JDBC) → FCM POST(JSON body: notiGbn/companyCode/empList/senderCode/title/body(+division/payload), 헤더: SECURITY_CODE/X-Api-Key). 이 내용을 바탕으로 sched-researcher에게 구체적으로 교정된 지시를 재전달함 (교정 지시 전송 완료, working 상태 확인).
- 다음 단계: sched-researcher의 RESEARCH.md 완료 대기 → sched-planner에게 전달.

## 2026-11-13 10:1x — 리서치 완료 및 검증
- sched-researcher가 `.team/RESEARCH.md` 작성 완료 (레거시 실행흐름 요약 → (a) 동적 스케줄 등록: Quartz+JDBC JobStore 추천(ScheduledFuture 자체관리는 조건부 대안) → (b) 비밀값 외부주입: 부트스트랩(엔진 메타DB/마스터키)=환경변수, 테넌트/운영 비밀값=관리자 웹 CRUD+DB 암호화저장 2계층 추천 → (c) 스택: Java17/21+Spring Boot 3.x+Quartz+SQL Server(메타DB)+Maven).
- orchestrator가 내용 중 근거 미검증 문장("foodlink-service가 이 방식을 쓴다") 발견 → sched-researcher에게 검증/수정 요청 → 실제로 `C:\Github\foodlink-service`가 존재하고 `appsettings.json`(gitignore) + `TickerMiddleware.cs`/`SecurityCodeMapper.cs` 근거로 구체화되어 수정 완료 확인.
- 다음 단계: sched-planner에게 RESEARCH.md + 레거시 참고사항 전달, `.team/PLAN.md`(프로젝트 구조/DB 스키마/API·화면 목록/설정 설계) + `.team/TASKS.md`(작업 분해) 작성 지시.

## 2026-11-13 10:2x — 기능 범위 2차 교정 (사용자 직접 지시)
- 사용자가 핵심 기능을 단순화해 재확인: "SP 호출 → (FCM 등 백엔드 API 호출 또는 단순 처리) → 웹에 결과 표시". orchestrator가 부족한 부분(실행이력 저장, 다운스트림 동작 추상화, 실패/재시도, 수동실행, SP 파라미터화, 대량결과 저장범위)을 짚어 질문 → 사용자 답변 수령:
  1. 실행 이력 저장 테이블은 **나중에 별도 설계** (이번 라운드 스코프 아님, 확장 지점만 남겨둘 것).
  2. 다운스트림 동작은 **외부 API 호출 / 처리결과만 웹표시 — 둘 다 지원**해야 함 (스케줄 정의에서 선택 가능한 두 모드).
  3. 실패 기록도 저장하되, 저장 테이블 설계가 끝난 뒤에 진행 (지금은 보류).
  4. **즉시실행(수동 트리거)** 기능 포함.
  5. **SP 파라미터화** 필요 (파라미터 없는 호출도 가능해야 하고, 업체별로 파라미터를 담아 호출해야 하는 경우도 지원).
  6. 실행 이력은 "백업 서버"가 아니라 "감시 서버" 개념 — SP 반환 결과 전체를 저장하는 게 아니라 상태/요약 수준만 남기는 모니터링 목적.
- sched-planner가 PLAN.md 작성 직전(리서치 읽은 후 사고 중) 상태였어서 `herdr agent send-keys sched-planner esc`로 중단 → idle 확인. 교정된 지시로 재시작할 예정.
- 다음 단계: 위 6개 반영사항을 포함해 sched-planner에게 PLAN.md/TASKS.md 작성을 재지시.

## 2026-11-13 10:3x — 계획 완료 + 보안 검증
- sched-planner가 PLAN.md(멀티테넌시 비채택 근거 §0, 기술스택 결정, 프로젝트구조, ExecutionResult/Sink 경계, DB스키마 schedule_definition/schedule_parameter/target_db_config/credential/admin_user, action_config JSON 모양, API 목록, 화면목록, 레거시→신규 매핑표, 범위밖 명시) + TASKS.md(T1~T13 + future T14) 작성 완료. 6개 교정사항 모두 반영 확인.
- orchestrator가 검토 중 PLAN.md의 action_config JSON 예시에 레거시 Config.java의 실제 운영 FCM 엔드포인트(내부 IP `192.168.80.27:4220`)가 그대로 복사되어 있는 것을 발견 — 사용자가 명시적으로 금지한 사항 위반. sched-planner에게 수정 지시 → `https://notify.example.com/api/push` 같은 범용 placeholder로 교체 완료, `.team` 전체 재검색(grep)으로 다른 유출(IP/계정/비밀값) 없음 확인.
- PLAN.md/TASKS.md 최종 검토 완료, 품질 양호 판단. 다음 단계: sched-worker에게 TASKS.md 순서대로 실제 구현 지시 (git commit은 로컬 허용, push 금지).

## 2026-11-13 — T1 프로젝트 스캐폴딩 (worker)
- Maven 프로젝트 생성: `pom.xml`에 Spring Boot 3.3.5 parent + web/security/thymeleaf/jdbc/quartz/validation starter, Flyway(core+sqlserver), mssql-jdbc(runtime), Lombok(optional), 테스트용 H2/Testcontainers(junit-jupiter, mssqlserver) 추가.
- PLAN.md §2 패키지 구조대로 빈 디렉터리 생성: `engine/{quartz,execution,action,sp}`, `domain/{schedule,config,admin}`, `infra/{persistence/jdbc,crypto,http}`, `web/{api,admin}`, `security`, `resources/{templates,db/migration}`.
- `SchedulerServiceApplication`(엔트리포인트) + `application.yml`(DB_URL/DB_USERNAME/DB_PASSWORD/MASTER_ENCRYPTION_KEY/INITIAL_ADMIN_* 전부 `${ENV_VAR}` 플레이스홀더, Quartz jdbc jobstore 설정 — initialize-schema: never, T2의 Flyway QRTZ_* 마이그레이션에 위임) 작성.
- `.gitignore`(target/, .idea/, application-local.yml, .env 등) + `README.md`(로컬 기동 필요 환경변수 표 + mvn 빌드/실행 안내) 작성.
- DoD 점검: `mvn clean package` 성공(jar 생성 확인, `target/scheduler-service-0.1.0-SNAPSHOT.jar`). 디렉터리 구조 PLAN.md §2와 일치 확인. README에 환경변수 안내 문구 존재.
- 참고: docker daemon이 꺼져 있어 백그라운드로 Docker Desktop 기동 시도 중(T2의 테스트컨테이너 기반 마이그레이션 검증에 필요). T2 진행 전 상태 확인 예정.
- 다음 작업: T2 (메타DB 스키마 Flyway 마이그레이션).

## 2026-11-13 10:4x — 아키텍처 교정 (사용자 직접 지시): 백엔드/프론트 분리
- 사용자가 T1 pom.xml을 보고 의문 제기: Spring Boot 버전(3.3.5)과 Lombok-exclude 빌드 설정이 같은 `C:\Github` 아래의 무관한 프로젝트 `ReeL-Trip`의 `apps/api-spring/pom.xml`과 완전히 동일 — orchestrator가 직접 대조 확인한 결과, worker가 독립적으로 버전을 판단하지 않고 템플릿을 그대로 베낀 것으로 보임(지적이 맞음). 큰 스택 방향(Java/Spring Boot/Quartz/SQL Server) 자체는 RESEARCH.md §(c)/PLAN.md §1에 레거시 연속성 근거로 독립적으로 설명되어 있으나, 세부 버전/보일러플레이트는 재검토 필요.
- 이어서 사용자가 "관리자 화면을 Thymeleaf로 같은 Java 앱에 넣는 게 맞는지, 프론트엔드 언어를 뭘 쓸지 정해지지 않은 상태 아니냐"고 질문 → orchestrator가 AskUserQuestion으로 확인 → 결정:
  1. **백엔드/프론트 분리**: 백엔드는 Java(Spring Boot)로 REST API만 제공, 화면(프론트엔드)은 별도 앱으로 분리 (Thymeleaf 서버사이드 렌더링 방식 폐기).
  2. 스케줄링 엔진 코어에 Spring Boot 프레임워크를 쓰는 것 자체는 유지하기로 확정(Quartz 연동/설정주입 편의성 근거 유효).
  3. 프론트엔드 기술은 **React**로 확정(사용자 직접 지시).
- 영향 범위: PLAN.md §1(기술스택: Thymeleaf 제거, React SPA 추가, 저장소 구조를 backend/+frontend/로 분리 검토), §2(프로젝트 구조), §6(API — CORS 등 SPA 연동 고려), §7(화면 목록 — Thymeleaf 템플릿 대신 React 화면 목록으로 재작성), TASKS.md(T1 pom.xml 재작업: thymeleaf 제거 + Spring Boot 버전 독립 재검토, React 프론트 작업 T 추가). T1 pom.xml/디렉터리는 아직 커밋 1개뿐이라 재작업 비용 작음.
- 다음 단계: sched-planner에게 이 아키텍처 교정을 반영해 PLAN.md/TASKS.md 수정 지시 → 이후 sched-worker에게 T1 재작업부터 재지시.

## 2026-11-13 10:5x — PLAN.md/TASKS.md 3차 교정 완료
- sched-planner가 반영 완료: PLAN.md §1(Thymeleaf 제거, React SPA/CORS 명시, Spring Boot 패치버전 독립검증 지침+ReeL-Trip 복붙 사례 경고 추가), §2(backend/+frontend/ 분리 트리), §6(CORS), §7(React 화면 목록)까지 전체 수정 확인.
- TASKS.md 재분해 확인: T1(백엔드 스캐폴딩, backend/로 이동 + thymeleaf 제거 + Spring Boot 버전 독립확인·근거기록 의무화) → T2(프론트 스캐폴딩+CORS) → T3~T11(기존 백엔드 작업, 번호만 밀림) → T12(React SPA 실제 화면 구현) → T13(비밀값 점검, 백엔드/프론트 양쪽) → T14(테스트) → T15(future, 실행이력).
- (orchestrator 메모) 중간에 herdr agent prompt 호출 시 bash 백틱 이스케이프 실수로 지시문 일부가 깨진 적 있었음 — 이후부터는 특수문자 포함 긴 지시문은 파일에 써서 `$(cat file)`로 전달하는 방식 사용.
- 다음 단계: sched-worker에게 "기존 루트의 T1 결과(pom.xml 등)를 backend/로 옮기고 thymeleaf 제거 + Spring Boot 버전 독립 재검토"부터 다시 시작해서 갱신된 TASKS.md 순서(T1~T14)대로 이어서 진행 지시.

## 2026-11-13 — T1 재작업 (worker): backend/ 분리 + thymeleaf 제거 + Spring Boot 버전 독립 확정
- 갱신된 PLAN.md/TASKS.md(3차 교정) 전체를 처음부터 다시 읽음. 핵심 변경사항(백엔드/프론트 분리, backend+frontend 디렉터리, Spring Boot 버전 독립검증 의무화) 확인.
- 레포 루트의 `pom.xml`/`src/`를 `git mv`로 `backend/pom.xml`/`backend/src/`로 이동(히스토리 보존, rebase/amend 없이 새 커밋으로 처리).
- `spring-boot-starter-thymeleaf` 의존성 제거, `web/admin`·`resources/templates` 빈 디렉터리 제거(화면 렌더링 없음 — REST API 전용).
- **Spring Boot 버전 독립 확정**: WebFetch로 Maven Central `spring-boot-starter-parent` 메타데이터를 직접 조회해 3.5.16이 3.x 세대(이후 업스트림은 4.x로 전환)의 최신 패치임을 확인 → `3.3.5`(ReeL-Trip `apps/api-spring/pom.xml`과 우연히 동일했던 버전, 독립 판단 없이 베낀 것으로 사용자가 지적한 값)에서 교체. 근거를 `backend/pom.xml` 주석 + `README.md`에 기록. Lombok-exclude 빌드 블록은 Spring Boot+Lombok 조합의 표준 보일러플레이트(Spring Initializr 생성물과 동일)라 그대로 유지 — 베낀 게 아니라 프레임워크 관례.
- `application.yml`: thymeleaf 섹션 제거, `app.cors.allowed-origin`(`${CORS_ALLOWED_ORIGIN:http://localhost:5173}`) 플레이스홀더 추가(CORS 실제 구현/검증은 T2).
- `CorsConfig` 빈 placeholder 클래스는 만들지 않음 — T2가 바로 다음이라 실제 구현을 그때 하는 게 맞다고 판단(TASKS.md T1은 application.yml 자리 마련까지만 요구).
- README에 backend/frontend 분리 구조, CORS 환경변수, Spring Boot 버전 선정 근거 보강.
- DoD 점검: `cd backend && mvn clean package` 성공(jar 생성 확인). 디렉터리 구조가 갱신된 PLAN.md §2 `backend/` 트리와 일치(`web/admin`/templates 없음). `grep -ri thymeleaf backend/pom.xml backend/src` 결과 0건. 버전 선정 근거 README/커밋에 기록됨.
- 다음 작업: T2 (프론트엔드 Vite+React 스캐폴딩 + CORS 실제 구현/검증).

## 2026-11-13 — T2 프론트엔드 스캐폴딩 + CORS 연동 (worker)
- `frontend/`에 Vite + React + TypeScript 템플릿(`npm create vite@latest frontend -- --template react-ts`) 생성, `react-router-dom` 추가. 빌드도구는 TASKS.md 지침대로 Vite로 직접 판단(표준적 선택, 강제되지 않음).
- `src/api/client.ts`: 백엔드 호출 공용 fetch 클라이언트, base URL은 `import.meta.env.VITE_API_BASE_URL`(기본값 `http://localhost:8080`)로만 주입 — 하드코딩 없음. `.env.example`(플레이스홀더)만 커밋, 실값은 `.env.local`(gitignore, Vite 기본 `*.local` 패턴으로 커버됨).
- `src/pages/`에 PLAN.md §7 8개 화면 대응 빈 페이지(Login/ScheduleList/ScheduleForm/RunResult/LastResult/TargetDbConfig/Credential/ChangePassword) 생성, `App.tsx`에 `react-router-dom` 라우팅 연결(`main.tsx`에 `BrowserRouter` 래핑). Vite 템플릿의 counter 데모 잔재(`App.css`, `assets/*`) 제거.
- 백엔드: `security/CorsConfig`(실제 구현 — `app.cors.allowed-origin` 콤마구분 오리진만 허용하는 `CorsConfigurationSource` 빈) + `security/SecurityConfig`(Spring Security 필터체인에 `.cors(...)` 연결, 현재는 `permitAll()` — **T11 이전 임시 상태, 최종 인증 적용은 T11**) + `web/api/HealthController`(`GET /api/health` → `{"status":"UP"}`, CORS 점검용 헬스체크) 추가.
- **CORS 실제 검증**: Docker 데몬을 기동해(T1에서 백그라운드로 띄워둔 Docker Desktop이 이 시점에 준비됨) 로컬 전용 SQL Server 컨테이너(`scheduler-meta-db`, 임시 개발용 비밀번호, 프로덕션 설정 아님)를 띄우고 그 위에서 백엔드를 실행(`spring.quartz.auto-startup=false`로 1회성 오버라이드 — QRTZ_* 테이블이 아직 없어서 Quartz 스케줄러 기동이 막히는 것은 T3에서 해결할 정상적인 선행조건 문제, 커밋되는 설정은 아님). `curl`로 프론트 오리진(`http://localhost:5173`)을 흉내 낸 OPTIONS preflight + 실제 GET을 `/api/health`에 보내 `Access-Control-Allow-Origin: http://localhost:5173` 등 헤더가 정확히 반환됨을 확인(브라우저 자동화 도구가 없어 브라우저가 수행하는 것과 동일한 CORS 체크를 curl로 재현). 이어서 `npm run dev`로 Vite 개발 서버가 실제로 5173에서 뜨는 것도 확인.
- DoD 점검: 프론트 `npm run build`/`tsc --noEmit` 성공, 빈 페이지 라우팅 구성 확인. 백엔드-프론트 CORS 교차 요청에서 차단(CORS 에러) 없음 확인(인증 미구현 상태라 `permitAll` 상태의 200 응답, T11 이후엔 401로 바뀔 것). README에 프론트 환경변수/빌드·실행/CORS 검증 방법 보강.
- 검증 후 정리: 백엔드(mvn spring-boot:run)·프론트(vite dev) 프로세스 종료, SQL Server 컨테이너(`scheduler-meta-db`)는 T3의 Flyway 마이그레이션 검증에 재사용할 예정이라 유지.
- 다음 작업: T3 (메타DB 스키마 Flyway 마이그레이션).

## 2026-11-13 — T3 메타DB 스키마 Flyway 마이그레이션 (worker)
- `V1__schedule_core.sql`: PLAN.md §4.1~4.5 컬럼 그대로 `target_db_config`/`credential`/`admin_user`/`schedule_definition`/`schedule_parameter` 5개 테이블 작성(FK: schedule_definition→target_db_config, schedule_parameter→schedule_definition ON DELETE CASCADE). 실행이력 테이블은 작성하지 않음(§4/§9 TBD 명시 그대로 준수).
- `V2__quartz_tables.sql`: `quartz-2.3.2.jar` 안의 공식 `org/quartz/impl/jdbcjobstore/tables_sqlServer.sql`을 추출해 거의 그대로 사용(직접 설계 안 함, PLAN.md §4.6 그대로) — 유일한 변경은 선두의 `USE [enter_db_name_here]` 제거(Flyway 커넥션이 이미 대상 DB를 바라봄).
- **버그 발견 및 수정**: T1에서 작성해둔 `application.yml`의 Quartz `driverDelegateClass`가 범용 `StdJDBCDelegate`였는데, 추출한 공식 DDL 스크립트 맨 위 주석이 "SQL Server를 쓸 땐 `org.quartz.impl.jdbcjobstore.MSSQLDelegate`로 설정하라"고 명시하고 있었음. 실제로 로컬 SQL Server 컨테이너에 Quartz를 기동해보니 `StdJDBCDelegate`의 `SELECT ... FOR UPDATE` 락 SQL이 SQL Server T-SQL 문법 오류(`FOR UPDATE clause allowed only for DECLARE CURSOR`)로 기동 실패 → `MSSQLDelegate`로 교정 후 재기동해 정상 기동(해당 delegate가 `WITH (UPDLOCK,ROWLOCK)` 락 SQL로 자동 전환되는 로그로 확인) 확인.
- **검증**: Docker(`scheduler-meta-db` 컨테이너, T2에서 CORS 검증용으로 띄워둔 것 재사용)에 `scheduler_meta` DB 생성 → 백엔드를 그 DB를 가리키게 실행 → Spring Boot 기동 시 Flyway가 자동으로 V1→V2 순서로 마이그레이션 적용(`Successfully applied 2 migrations to schema [dbo], now at version v2`) → `sqlcmd`로 `sys.tables` 조회해 5개 메타 테이블 + 10개 QRTZ_* 테이블 + `flyway_schema_history`만 존재하고 **실행이력 테이블은 없음**을 확인. Quartz 스케줄러도 `MSSQLDelegate` 교정 후 실제로 "Scheduler ... started" 상태까지 정상 기동됨을 확인(이 작업 자체는 T6/T7 몫이지만 Quartz 설정의 DB 호환성은 T3의 DDL 작업과 직결되어 같이 검증).
- DoD 점검: Flyway 마이그레이션이 (Spring Boot 기동을 통한 자동 적용 경로로) 에러 없이 전체 테이블 생성, 실행이력 테이블 의도적 제외 확인.
- 검증 후 정리: 백엔드 프로세스 종료, SQL Server 컨테이너(`scheduler-meta-db`)는 T4 이후 Repository 구현 테스트에도 재사용 예정이라 유지.
- 다음 작업: T4 (도메인 모델 + Repository 인터페이스/구현).

## 2026-11-13 — T3 교정: `target_db_config.db_type` 컬럼 제거 (사용자 직접 확인)
- 사용자가 PLAN.md §1/§4.3/§9를 재확정: 대상 업무 DB는 모든 솔루션이 MSSQL로 고정이라 멀티-DBMS 지원이 불필요 → `target_db_config`에서 `db_type` 컬럼 제거, §9에 "엔진은 대상 업무 DB에 DDL/신규 SP 생성 기능 없음(기존 SP 호출만)"이 범위 밖으로 명시 추가(기존 구현 전제와 일치, 문서만 명확해진 것).
- `V1__schedule_core.sql`의 `target_db_config`에서 `db_type NVARCHAR(20) NOT NULL DEFAULT 'SQLSERVER'` 컬럼 제거(+주석으로 이유 명시). 도메인/Repository 코드는 아직 T4 전이라 `db_type` 참조가 전혀 없었음(grep 0건) — 추가 수정 불필요.
- 이미 로컬 SQL Server 컨테이너(`scheduler-meta-db`)에 수정 전 V1/V2가 적용돼 있던 상태라, 컨테이너를 내리고 새로 띄운 뒤 `scheduler_meta` DB를 재생성해 V1(수정판)→V2를 처음부터 깨끗하게 재적용 — "Successfully applied 2 migrations... now at version v2" 확인, `target_db_config` 컬럼 목록에 `db_type` 없음을 `INFORMATION_SCHEMA.COLUMNS` 조회로 확인, Quartz도 정상 기동 확인.
- DoD 재점검: 마이그레이션 깨끗하게 적용 + 실행이력 테이블 없음 + `db_type` 컬럼 없음, 3가지 모두 확인됨.

## 2026-11-13 — T4 도메인 모델 + Repository 인터페이스/구현 (worker)
- `domain/schedule`: `ActionType`(API_CALL/DISPLAY_ONLY), `GroupingMode`(PER_ROW/GROUP_BY), `ParamDataType`, `ValueSource`(LITERAL/CURRENT_DATE/CURRENT_DATETIME) enum + `ScheduleDefinition`/`ScheduleParameter` record(불변 값 객체) + `ScheduleRepository`/`ScheduleParameterRepository` 인터페이스. `ScheduleDefinition.actionConfig`는 원본 JSON 문자열로 보관(actionType별로 모양이 다르므로 파싱은 아는 쪽에서).
- `domain/config`: `TargetDbConfig`(db_type 없음, §4.3 반영)/`Credential` record + 해당 Repository 인터페이스. `domain/admin`: `AdminUser` record + `AdminUserRepository`(T11 부트스트랩 시드용 `existsAny()` 포함).
- `action_config` JSON↔Java 매핑: `ApiCallActionConfig`/`DisplayOnlyActionConfig` record + `ActionConfigJson`(Jackson 기반 write/read 유틸, action_type별 타입 지정).
- `infra/persistence/jdbc`: `JdbcScheduleRepository`/`JdbcScheduleParameterRepository`/`JdbcTargetDbConfigRepository`/`JdbcCredentialRepository`/`JdbcAdminUserRepository` — `NamedParameterJdbcTemplate` + 수동 `RowMapper`(ORM 없음, 레거시 매핑 스타일 유지). `schedule_parameter.replaceAll`은 delete-then-bulk-insert로 "통째 교체"(PLAN §6) 구현.
- **테스트**: `AbstractRepositoryIntegrationTest`(`@SpringBootTest` + Testcontainers MSSQLServerContainer) 위에 5개 Repository 통합테스트(CRUD+JSON 왕복 검증 포함, `JdbcScheduleRepositoryTest`가 API_CALL/DISPLAY_ONLY 양쪽 action_config 왕복까지 검증) + `ActionConfigJsonTest`(Spring/DB 없는 순수 단위테스트, equals 기반 왕복검증) 작성.
- **버그 발견 및 수정(테스트 인프라)**: 처음엔 테스트 5개 클래스 각각이 `@Container` static 필드를 **추상 베이스 클래스**에 선언했는데, Java에서 상위클래스의 static 필드는 서브클래스별로 복제되지 않고 단 하나만 존재 — `@Testcontainers` JUnit5 확장의 클래스별 start/stop 훅과 뒤섞이면서 컨테이너가 중간에 내려가거나 Spring 컨텍스트 생성과 경합해 "Connection refused" 산발적 실패(테스트 스위트 전체가 수 분씩 걸리고 들쑥날쑥하게 깨짐)가 발생함. Testcontainers 공식 "싱글톤 컨테이너" 패턴(static 초기화 블록에서 1회만 `start()`, `@Testcontainers`/`@Container` 애너테이션 제거)으로 교체해 해결 — 전체 테스트가 32초로 안정적으로 끝남을 재확인(이전엔 같은 스위트가 두 번 겹쳐 돌았을 때 최대 수백 초까지 걸리며 실패했었음).
- **테스트 설계 버그 1건 추가 발견/수정**: `JdbcAdminUserRepositoryTest.existsAnyReflectsRowCount`가 "테이블이 비어있다"를 전제했는데, 싱글톤 컨테이너로 스키마를 테스트 클래스 간 공유하게 되면서 다른 테스트가 이미 넣은 행 때문에 깨짐 → "최소 1건 저장 후 true"만 검증하도록 테스트 의도 수정(실제 버그는 테스트 쪽, 구현 쪽 아님).
- DoD 점검: `mvn test` 전체 21개 테스트 그린(`BUILD SUCCESS`) 확인(저장→조회 필드 일치, action_config JSON 왕복 정확성 포함). `mvn clean package` 최종 재확인 성공.
- 다음 작업: T5 (비밀값 암복호화 `infra/crypto`).

## 2026-11-13 — T5 비밀값 암복호화 (worker)
- `infra/crypto/CredentialCipher`: AES-256-GCM, 매 암호화마다 랜덤 IV 생성 후 `IV+ciphertext`를 base64 1개 문자열로 묶어 저장(복호화 시 앞 12바이트를 IV로 분리). 마스터키(`MASTER_ENCRYPTION_KEY`, base64)가 없거나/base64가 아니거나/디코딩 결과가 32바이트(AES-256)가 아니면 생성자에서 즉시 `IllegalStateException`으로 fail-fast(명확한 에러 메시지 포함) — Spring이 `${MASTER_ENCRYPTION_KEY}` 자체를 못 찾는 경우는 플레이스홀더 해석 단계에서 더 먼저 실패하고, 값이 있지만 형식이 틀린 경우는 이 생성자 검증이 잡음.
- **T4 설계 보강**: T4에서 `TargetDbConfig.encryptedPassword`/`Credential.encryptedValue` 필드가 "암호문을 그대로 들고 있는 값객체"처럼 설계돼 있었는데, TASKS.md T5가 "Repository 구현체가 저장 시 암호화, 조회 시 복호화"를 요구하므로 도메인 경계에선 평문이어야 함이 맞다고 판단 → 필드명을 `password`/`value`(평문)로 리네이밍하고, `JdbcTargetDbConfigRepository`/`JdbcCredentialRepository`의 RowMapper(이제 인스턴스 필드로 전환, `CredentialCipher` 주입)가 조회 시 `cipher.decrypt(...)`, `save`/`update`가 쓰기 직전 `cipher.encrypt(...)`를 수행하도록 연결. SQL 컬럼명(`encrypted_password`/`encrypted_value`, DB에는 실제로 암호문이 들어가므로 정확한 이름)은 그대로 유지.
- 테스트: `CredentialCipherTest`(암복호화 왕복, 같은 평문도 매번 다른 암호문 생성(랜덤 IV) 확인, 키 누락/비-base64/길이오류 3가지 fail-fast 케이스) + 기존 `JdbcTargetDbConfigRepositoryTest`/`JdbcCredentialRepositoryTest`를 평문 왕복 검증으로 갱신하고, `JdbcTemplate`으로 **DB 원본 컬럼을 직접 조회**해 평문과 다르다는 것(실제로 암호화돼 저장됨)을 확인하는 테스트 추가.
- 코드 리뷰로 평문 로그 미노출 확인: `CredentialCipher`/두 Repository 어디에도 logger 호출 없음(평문이 로그로 샐 경로 자체가 없음).
- **버그 수정(테스트 픽스처)**: `AbstractRepositoryIntegrationTest`의 테스트용 `MASTER_ENCRYPTION_KEY`가 base64로는 유효하지만 디코딩 결과가 34바이트였음(32바이트 AES-256 요건과 안 맞음) → CredentialCipher의 길이 검증에 걸려 모든 Spring 컨텍스트 테스트가 컨텍스트 로드 실패로 깨짐 → 정확히 32바이트(zero bytes)로 교체해 해결.
- DoD 점검: `mvn test` 전체 28개 테스트 그린(`BUILD SUCCESS`). "저장 시 암호화된 값이 DB에 들어가고 조회 시 평문 복원"을 테스트로 확인. `mvn clean package` 재확인 성공.
- 다음 작업: T6 (실행 엔진 코어 — `ExecutionResult`/`Sink`/SP 호출/액션 핸들러).

## 2026-11-13 — T6 실행 엔진 코어 (worker)
- `engine/execution`: `TriggerType`(SCHEDULED/MANUAL), `ExecutionStatus`, `ExecutionResult`(불변 값객체, 영속화 없음), `ExecutionResultSink`(포트) + `LoggingExecutionResultSink`(SLF4J만), `DisplayOutput`(최대 500행 상한, `DisplayOutput.of(...)`에서 자름)/`DisplayOutputStore`(`ConcurrentHashMap`, 스케줄ID당 최신 1건), `ScheduleAlreadyRunningException`/`ScheduleNotFoundException`.
- `engine/sp`: `StoredProcedureInvoker`(`CallableStatement`로 `EXEC spName ?,?,...` 또는 파라미터 없는 `EXEC spName` 모두 지원, `DriverManager` 직접 연결 — 대상DB는 Spring이 관리하는 고정 빈이 아니라 스케줄마다 달라지므로), `SpParameterResolver`(`schedule_parameter` → `paramOrder` 정렬 후 LITERAL(데이터타입별 파싱)/CURRENT_DATE/CURRENT_DATETIME 해석, 순수 정적 함수).
- `engine/action`: `ActionHandler`/`ActionOutcome`(success/summary/errorMessage/displayOutput), `BodyTemplateRenderer`(`${COLUMN}`/`${cred:NAME}` 정규식 치환, credential 복호화값은 호출자가 주입하는 `Function<String,String>`을 통해서만 흐름 — 로그/캐시 없음), `ApiCallActionHandler`(PER_ROW는 행마다 1회, GROUP_BY는 `groupByField` 기준 그룹핑 후 그룹의 첫 행으로 템플릿 치환, 그룹당 1회 호출 — "그룹의 첫 행" 선택은 PLAN §5 문구 그대로, 레거시 Day패턴의 `empList` 배열 집계 같은 세부는 범용 플레이스홀더 치환으로 일반화됨을 LOG에 명시), `DisplayOnlyActionHandler`(SP 결과 컬럼 선택 후 `DisplayOutput` 변환, 외부 호출 없음).
- `infra/http/GenericHttpClient`: 레거시 `HttpURLConnection` 패턴을 `java.net.http.HttpClient`로 일반화(동일한 "POST + 커스텀헤더" 개념, 구현 모더나이즈는 로직 변경 아님) — 임의 method/url/headers/body, FCM 전용 아님.
- `engine/execution/ScheduleExecutor`: Quartz Job(T7)과 수동실행 API(T9)가 공유하는 단일 진입점. 흐름: `ConcurrentHashMap` 기반 중복실행 가드(`putIfAbsent`, 이미 실행 중이면 `ScheduleAlreadyRunningException`) → 스케줄/파라미터/대상DB 로드 → SP 호출 → `action_type`별 핸들러 분기(`Map<ActionType,ActionHandler>`, Spring enum-key Map을 자동 주입할 수 없어 `ActionHandlerConfig`로 직접 `@Bean` 구성) → `ExecutionResult` 생성 → `ExecutionResultSink.record()`(+ `DISPLAY_ONLY`면 `DisplayOutputStore`에도 적재) → 반환. 예외는 바깥 `catch`에서 전부 잡아 FAILURE `ExecutionResult`로 변환 후 기록, `finally`에서 가드 해제 — 호출 경로(Job/API) 무관하게 항상 기록됨을 보장.
- 테스트(`ScheduleExecutorTest`, Mockito mock + JDK 내장 `com.sun.net.httpserver.HttpServer`로 실제 HTTP 호출 재현, 외부 의존성 추가 없음): SP 호출 실패→FAILURE+errorMessage, API_CALL PER_ROW 전체성공→SUCCESS, API_CALL GROUP_BY 일부실패(500 응답)→FAILURE+"1/2 성공"+"HTTP 500", DISPLAY_ONLY 정상→SUCCESS+`DisplayOutputStore` 적재 확인, **Sink 교체 증명**(`FakeExecutionResultSink`로 바꿔도 `ScheduleExecutor` 코드 변경 없이 동작), **동시 실행 가드**(한 실행이 `CountDownLatch`로 블록된 상태에서 같은 scheduleId로 재호출 시 `ScheduleAlreadyRunningException` 확인) — credential 복호화 값이 `ExecutionResult.summary`/`errorMessage`에 나타나지 않음도 모든 API_CALL 테스트에서 함께 검증. 추가로 `SpParameterResolverTest`/`BodyTemplateRendererTest` 순수 단위테스트.
- **버그 수정(테스트 픽스처)**: `displayOnlyScheduleSucceedsAndPopulatesDisplayOutputStore`가 `Map.of(...)`로 행을 만들었는데 `Map.of`는 순서를 보장하지 않아 컬럼 순서 assert가 불안정하게 깨짐 → 실제 `StoredProcedureInvoker`가 `LinkedHashMap`(JDBC 컬럼 순서 보존)을 반환하는 것과 동일하게 맞춘 `orderedRow(...)` 헬퍼로 교체.
- DoD 점검: `mvn test` 전체 42개 테스트 그린. `mvn clean package` 재확인 성공.
- 다음 작업: T7 (Quartz 연동 — 동적 등록/해제).

## 2026-11-13 — T7 Quartz 연동 — 동적 등록/해제 (worker)
- `engine/quartz/QuartzConfig`: Quartz JDBC JobStore 자체는 이미 `application.yml`의 `spring.quartz.*`(T1/T3) + Flyway `QRTZ_*`(T3)로 동작 확인됨. 이번에 추가한 것은 `SchedulerFactoryBeanCustomizer` + `AutowiringSpringBeanJobFactory` — Quartz는 Job 인스턴스를 리플렉션으로 직접 생성해 Spring 컨테이너를 거치지 않으므로, 이 커스텀 JobFactory 없이는 `GenericScheduleJob`의 `@Autowired ScheduleExecutor`가 항상 null이 됨(Spring Boot Quartz 자동구성이 기본 제공하지 않는 부분).
- `engine/quartz/GenericScheduleJob`(`@DisallowConcurrentExecution`, `Component` 아님 — Quartz가 직접 생성하므로 Spring 빈으로 등록하면 쓰이지 않는 별개 인스턴스가 생길 뿐): `JobDataMap`에서 `scheduleId`만 꺼내 `ScheduleExecutor.execute(id, SCHEDULED)` 호출, `ScheduleAlreadyRunningException`은 로그만 남기고 삼킴(수동실행이 이미 돌고 있는 정상 상황).
- `engine/quartz/ScheduleRegistrar`: `register(ScheduleDefinition)`가 기존 Job을 먼저 지우고(멱등) `enabled=false`면 거기서 끝(트리거 생성 안 함), `enabled=true`면 `CronScheduleBuilder` + `TriggerBuilder`로 재생성 — "생성/수정/활성화토글" 전부 이 메서드 하나로 커버. `unregister(scheduleId)`는 `deleteJob`.
- `engine/quartz/ScheduleBootstrapRunner`(`ApplicationRunner`): 기동 시 `scheduleRepository.findAllEnabled()`를 전부 `scheduleRegistrar.register(...)`에 넘김 — 레거시 `SchedulerContextListener.contextInitialized()`의 3개 하드코딩 호출을 대체.
- **버그 발견/수정**: `ScheduleRegistrar`/테스트 전반에 쓴 cron 문자열("0 0 9 * * MON", "0 0/30 * * * MON-FRI" 등, PLAN.md §8 예시와 동일한 모양)이 실제로 Quartz에 등록을 시도하자(T6까지는 cron을 실제로 파싱하는 코드가 없어서 안 드러났음) `CronExpression` 파싱 에러로 전부 깨짐 — Quartz cron은 "일(day-of-month)"과 "요일(day-of-week)" 둘 다 구체값을 줄 수 없고 한쪽은 반드시 `?`여야 함(Unix cron과 다른 Quartz 고유 규칙). 요일이 지정된 모든 테스트 cron을 `*` → `?`(day-of-month 자리)로 교정(`"0 0 9 ? * MON"`, `"0 0/30 * ? * MON-FRI"` 등). **참고**: PLAN.md §5/§8의 cron 예시 문구도 같은 모양(day-of-month에 `*`)을 쓰고 있어 Quartz에 그대로 넣으면 똑같이 깨짐 — PLAN 자체를 임의로 고치지 않고 여기 LOG에만 기록, 실제 운영 문서/가이드(T8 cron 유효성 검증, README 등) 작성 시 `org.quartz.CronExpression.isValidExpression(...)`로 검증하도록 챙길 것.
- 테스트: `ScheduleRegistrarIntegrationTest`(새 Testcontainers MSSQL 싱글톤 베이스, 이번엔 `spring.quartz.auto-startup` 기본값(true) 유지 — 실제 `Scheduler` 빈으로 트리거 상태 확인) — register→`CronExpression.getNextValidTimeAfter`로 독립 계산한 다음발화시각과 정확히 일치 확인, unregister→`checkExists`/`getTrigger` 둘 다 사라짐 확인, cron 변경 재등록→다음발화시각이 실제로 바뀜 확인, `enabled=false`로 register→트리거 생성 안 됨 확인. `ScheduleBootstrapRunnerTest`(Mockito 순수 단위테스트)로 "enabled 스케줄 전체에 대해 register 호출" 로직만 분리 검증(실제 앱기동 시점에 DB에 미리 행을 넣어두고 통합적으로 검증하는 것은 Flyway 마이그레이션도 같은 컨텍스트 기동 과정에서 돌기 때문에 사실상 불가능 — "Quartz에 넣으면 cron과 다음발화시각이 맞다"는 `ScheduleRegistrar` 통합테스트가, "enabled 스케줄을 찾아 register를 호출한다"는 이 단위테스트가 각각 나눠서 검증).
- DoD 점검: `mvn test` 전체 48개 테스트 그린. `mvn clean package` 재확인 성공.
- 다음 작업: T8 (관리자 REST API — 스케줄 CRUD + 파라미터).

## 2026-11-13 — 인증 방식 결정 (사용자 직접 지시) + T8~T14 계속 진행 지시
- 사용자가 T11의 인증 방식을 **토큰(JWT) 기반**으로 명시적으로 확정: 세션 쿠키 방식은 쓰지 않음(SPA가 다른 오리진이라 SameSite=None+Secure+CORS Allow-Credentials+CSRF까지 다 갖춰야 하는데, 단일 관리자 역할의 내부 어드민 도구 규모에는 불필요한 복잡도). 로그인 성공 시 JWT 발급, 프론트는 `Authorization: Bearer` 헤더로 호출(메모리 보관 권장, localStorage 영구저장 지양 — 구현 디테일은 worker 판단), Spring Security는 stateless(세션 미사용)로 구성. 이 결정으로 T11 진행 가능해짐.
- 다음 지시: T8(스케줄 CRUD API) → T9(즉시실행/최근결과 API) → T10(대상DB/자격증명 API) → T11(인증+CORS 최종) → T12(React 실제화면) → T13(비밀값 최종점검) → T14(테스트 정리)까지 계속 진행, 기존 원칙(DoD→commit→LOG) 유지.

## 2026-11-13 — T8 관리자 REST API — 스케줄 CRUD + 파라미터 (worker)
- `engine/quartz/ScheduleRegistrar`에 `getNextFireTime(scheduleId)`(컨트롤러의 "다음 실행시각" 노출용) 추가, `engine/execution/ScheduleExecutor`에 `isRunning(scheduleId)`(T9 상태 API에서 쓸 예정) 추가 — 둘 다 PLAN §6 응답 필드에 필요해서 이번에 같이 추가.
- `web/api/dto`: `ScheduleRequest`(actionConfig는 `JsonNode`로 받아 타입별 검증 후 문자열로 직렬화), `ScheduleResponse`(actionConfig를 `JsonNode`로 역직렬화해 돌려줌, `nextFireTime`/`parameters` 포함 — `parameters`는 목록(list) 엔드포인트에서는 N+1 방지로 빈 리스트, 단건 조회에서만 실제 채움), `ScheduleParameterRequest`/`Response`, `EnabledRequest`.
- `web/api/ActionConfigRequestValidator`: `action_type`별 `action_config` 모양 검증(API_CALL인데 `url`/`method`/`groupingMode` 없음 → 400, `GROUP_BY`인데 `groupByField` 없음 → 400). `web/api/ApiExceptionHandler`(`@RestControllerAdvice`): `IllegalArgumentException`/빈 검증 실패 → 400, `ScheduleNotFoundException` → 404, `ScheduleAlreadyRunningException`(T9에서 쓰일 예정) → 409.
- `web/api/ScheduleController`: PLAN §6 스케줄 CRUD 6개 엔드포인트(GET 목록/단건, POST, PUT, DELETE, PATCH enabled) 전부 구현 — 생성/수정/삭제/토글마다 `scheduleRegistrar.register()`/`unregister()`로 Quartz 즉시 동기화. cron 유효성은 `org.quartz.CronExpression.isValidExpression(...)`로 검증(T7에서 발견한 Quartz cron 고유 규칙 — day-of-month/day-of-week 중 하나는 `?` — 이게 바로 400으로 잡힘).
- `web/api/ScheduleParameterController`: `PUT /api/schedules/{id}/parameters`(통째 교체) — `LITERAL`인데 `literalValue` 없음, 또는 `dataType`에 안 맞는 literal(예: INT에 "not-a-number")은 `SpParameterResolver.resolve(...)`를 그대로 재사용해 파싱 실패를 400으로 변환(파싱 로직 중복 없음).
- `updatedBy`는 아직 인증이 없어(T11 전) 이번 라운드에선 `null`로 둠 — T11에서 인증 붙이면 돌아와서 `SecurityContext`의 로그인 계정명을 채우도록 보강 예정(메모 남김).
- 테스트: `ScheduleControllerTest`(MockMvc + `AbstractQuartzIntegrationTest` 상속, 실제 Quartz `Scheduler` 빈으로 검증) — 생성 시 201+Quartz 트리거 실제 등록, API_CALL인데 url 없음→400, 잘못된 cron→400, 단건 조회에 파라미터 임베드 확인, **수정 시 Quartz 다음발화시각이 실제로 바뀜** 확인(DoD 명시 항목), enabled 토글이 Quartz Job add/remove로 실제 반영됨 확인, 삭제 시 스케줄+Quartz Job 둘 다 사라짐 확인, 파라미터 잘못된 literal→400 확인.
- DoD 점검: `mvn test` 전체 56개 테스트 그린(새 테스트 8개 모두 1회차에 통과). `mvn clean package` 재확인 성공.
- 다음 작업: T9 (관리자 REST API — 즉시실행 + 최근결과 조회).

## 2026-11-13 — T9 관리자 REST API — 즉시실행 + 최근결과 조회 (worker)
- `engine/execution/LastExecutionResultStore`(스케줄ID→최신 `ExecutionResult` 1건, `ConcurrentHashMap`) 신규 추가 — `DisplayOutputStore`는 `DISPLAY_ONLY`일 때만 채워지므로, PLAN §6의 "`DisplayOutputStore`/마지막 `ExecutionResult`의 인메모리 캐시"를 전체 action_type에 대해 만족시키려면 별도 저장소가 필요했음. `ScheduleExecutor.execute()`가 성공/실패 양쪽 경로 모두에서 이 저장소에도 기록하도록 연결(생성자 시그니처 변경 → 기존 `ScheduleExecutorTest`의 생성자 호출부 전부 업데이트).
- `web/api/dto/RunResultResponse`(`ExecutionResult` + `DisplayOutput`(nullable) 묶음, run-now/last-result 공용) `web/api/dto/ScheduleStatusResponse`(`nextFireTime`+`running`).
- `web/api/ScheduleExecutionController`: `POST .../run-now`(`ScheduleExecutor.execute(id, MANUAL)` 동기 호출, `ScheduleAlreadyRunningException`은 `ApiExceptionHandler`가 이미 409로 매핑), `GET .../last-result`(캐시 없으면 204), `GET .../status`(`ScheduleRegistrar.getNextFireTime` + `ScheduleExecutor.isRunning`). 컨트롤러에서 스케줄 존재 여부를 먼저 확인(404) — `ScheduleExecutor.execute()` 내부의 "스케줄 없음"은 바깥 catch-all이 FAILURE `ExecutionResult`로 삼켜버려 그대로는 404가 안 나오기 때문.
- **테스트 전략**: run-now의 "진짜 SP 왕복"을 검증하려면 모의가 아니라 실제 EXEC가 필요해서, `target_db_config`를 메타DB와 **같은 Testcontainers MSSQL 컨테이너**를 가리키게 하고 그 안에 테스트용 `SP_TEST_SAMPLE`(정적 결과 1행)/`SP_TEST_SLOW`(`WAITFOR DELAY '00:00:03'`, 동시실행 가드 테스트용) 프로시저를 `CREATE OR ALTER`로 만들어 실제 라운드트립을 태움. `AbstractQuartzIntegrationTest.MSSQL` 필드를 테스트 간 공유하려고 `public`으로 가시성 변경.
- `ScheduleExecutionControllerTest`: (1) run-now가 정규 스케줄(여기선 `enabled:false`로 만들어 Quartz와 완전히 무관함을 강조)과 무관하게 즉시 실행되고 응답에 `ExecutionResult`가 동기적으로 담김, (2) `DISPLAY_ONLY` run-now 응답에 가공된 `displayOutput.rows`가 실제 SP 결과대로 포함됨, (3) run-now 이후 `last-result`가 그 결과를 반영(직전엔 204), (4) `SP_TEST_SLOW`로 한 실행을 붙잡아 둔 채 동시에 두 번째 run-now 호출 시 409 확인.
- DoD 점검: `mvn test` 전체 60개 테스트 그린. `mvn clean package` 재확인 성공.
- 다음 작업: T10 (관리자 REST API — 대상DB/자격증명 설정).

## 2026-11-13 — T10 관리자 REST API — 대상DB/자격증명 설정 (worker)
- `web/api/Masking`(끝 4자만 노출, 나머지 `*`) — `target_db_config`/`credential` 양쪽 응답에 공용 사용.
- `web/api/TargetDbConfigController`: PLAN §6 CRUD 4종. 삭제 시 `scheduleRepository.findAll()`에서 `targetDbConfigId` 일치하는 스케줄이 하나라도 있으면 **차단**(409, PLAN 문구 "차단" 그대로) — DB의 실제 FK 제약도 있지만, 500 대신 깔끔한 409+참조 스케줄 이름 목록을 주려고 애플리케이션 레벨에서 먼저 확인.
- `web/api/CredentialController`: PLAN §6 CRUD 4종. 삭제는 PLAN 문구가 "차단"이 아니라 "**경고**"라서 다르게 설계 — 참조하는 스케줄이 있으면 기본은 409(참조 스케줄 목록 포함)지만, `?force=true` 쿼리파라미터로 명시적으로 넘기면 경고를 무시하고 삭제 진행. 참조 탐지는 `schedule_definition.action_config` 문자열에 `${cred:NAME}` 부분문자열이 있는지 스캔(관리자 도구 규모라 전체스캔으로 충분, 별도 인덱스/역참조 테이블 불필요).
- `web/api/ResourceInUseException`(+`NoSuchElementException` → 404) `ApiExceptionHandler`에 추가.
- **버그 발견/수정(테스트 작성 중)**: `CredentialControllerTest`에서 참조 스케줄을 만들 때 `target_db_config_id`를 존재하지 않는 `1L`로 하드코딩했다가 FK 제약 위반으로 실패할 뻔함 — 실제로 실행하기 전에 발견, `seedTargetDbConfig()` 헬퍼로 실제 행을 먼저 만들도록 교정.
- DoD 점검: `mvn test` 전체 70개 테스트 그린 — CRUD, 마스킹(응답 바디에 평문 비밀값 없음을 `doesNotContain`으로 직접 확인), `target_db_config`는 참조 중 삭제 차단, `credential`은 참조 중 기본 차단 + `force=true`로 강제 삭제 둘 다 확인. `mvn clean package` 재확인 성공.
- 다음 작업: T11 (인증 — Spring Security, JWT 기반으로 확정됨) + CORS 최종 설정.

## 2026-11-13 — T11 인증(JWT, stateless) + CORS 최종 설정 (worker)
- pom.xml에 jjwt 추가: Maven Central 메타데이터로 독립 확인한 최신 버전 0.13.0(ReeL-Trip이 쓴 0.12.6을 그대로 베끼지 않음, Spring Boot 버전 때와 같은 원칙 재적용) — jjwt-api/impl/jackson.
- `security/JwtTokenService`: HS256, `JWT_SIGNING_KEY`(base64, 정확히 32바이트) 서명키를 `MASTER_ENCRYPTION_KEY`와 **별도 값**으로 분리(서명키/암호화키 혼용 금지 원칙). 누락/비-base64/길이오류 시 `CredentialCipher`와 동일한 스타일로 fail-fast. `generate(username, role)`/`parseUsername`/`parseRole`.
- `security/JwtAuthenticationFilter`(`OncePerRequestFilter`): `Authorization: Bearer` 헤더 파싱 → 유효하면 `SecurityContextHolder`에 `UsernamePasswordAuthenticationToken(username, null, ROLE_<role>)` 설정, 무효/만료면 그냥 미인증 상태로 둠(이후 인가 규칙이 401 처리).
- `security/SecurityConfig` 전면 교체: `SessionCreationPolicy.STATELESS` + CSRF 비활성(세션/쿠키 자체가 없으므로), `/api/auth/login`·`/api/health`만 `permitAll`, 나머지 `anyRequest().authenticated()`. **버그 발견/수정**: `exceptionHandling(...authenticationEntryPoint(...))`를 명시하지 않으면 Spring Security가 (formLogin/httpBasic 둘 다 비활성 상태라) 기본값으로 `Http403ForbiddenEntryPoint`를 써서 미인증 요청이 **401이 아니라 403**으로 나가는 것을 테스트로 발견 → `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)`를 명시해 교정(PLAN/TASKS DoD가 명확히 401을 요구).
- `security/CorsConfig`: `setAllowCredentials(true)` 제거 — 쿠키 기반 인증이 아니므로 더 이상 필요 없음(주석으로 이유 남김).
- `security/AdminUserBootstrapRunner`(`ApplicationRunner`): `admin_user`가 0건이고 `INITIAL_ADMIN_USERNAME`/`PASSWORD`가 둘 다 설정돼 있으면 1회 시드(BCrypt). 레거시엔 인증 자체가 없었으므로 완전 신규.
- `web/api/AuthController`: `POST /api/auth/login`(검증 실패 시 `BadCredentialsException`→401), `POST /api/auth/logout`(stateless라 서버측에 무효화할 게 없음 — 클라이언트가 토큰을 버리는 것으로 끝, 응답만 200), `GET /api/auth/me`.
- T8/T10에서 "인증 없어서 `updatedBy`를 null로 둠"이라고 미뤄둔 부분을 이제 `Authentication.getName()`으로 채우도록 `ScheduleController`(create/update/setEnabled) + `CredentialController`(create/update)에 되돌아가 연결.
- **테스트 인프라 버그 2건 발견/수정**:
  1. 기존 MockMvc 테스트(ScheduleController/ScheduleExecutionController/TargetDbConfigController/CredentialController) 전부가 `/api/**` 보호로 인해 전부 401로 깨질 뻔함 → `@WithMockUser(username="test-admin", roles="ADMIN")`을 클래스 레벨에 추가해 기존 요청 코드를 건드리지 않고 해결.
  2. `ScheduleExecutionControllerTest.concurrentRunNowIsRejectedWithConflict`가 수동으로 띄운 백그라운드 `Thread`에서 `mockMvc.perform(...)`을 호출하는데, `@WithMockUser`의 보안 컨텍스트는 스레드로컬이라 그 스레드엔 전파되지 않아 **첫 호출이 즉시 401로 실패**(느린 SP를 실제로 블로킹하지 못함) → 두 번째 호출이 충돌 없이 그냥 성공해버려 테스트가 깨짐. 실제 `JwtTokenService`로 토큰을 만들어 두 호출 모두에 `Authorization` 헤더로 직접 붙이는 방식으로 교정(스레드 전파 문제를 완전히 회피).
- 신규 테스트: `AuthControllerTest`(토큰없이 401, 헬스체크는 인증불필요, 틀린 비밀번호 401, 로그인 성공 후 토큰으로 보호된 엔드포인트/`/me` 호출 성공, 쓰레기 토큰 401), `AdminUserBootstrapRunnerIntegrationTest`(다른 테스트들과 공유하는 싱글톤 컨테이너를 쓰면 다른 테스트가 이미 넣은 admin_user 때문에 "admin 0건" 전제가 깨질 수 있어서 **이 테스트만 전용 컨테이너**를 새로 띄워 격리 — `INITIAL_ADMIN_*` 시드 후 정확히 1명 존재 + 그 계정으로 실제 로그인 성공 확인).
- **실 환경 종단 검증(DoD 명시 요구사항)**: 로컬 SQL Server 컨테이너로 백엔드를 띄우고 `curl`로 (1) `/api/auth/login` preflight(OPTIONS)가 CORS 헤더와 함께 통과, (2) 프론트 오리진에서 실제 로그인 POST가 CORS 에러 없이 토큰 발급, (3) 그 토큰으로 `/api/schedules` 호출이 CORS 헤더 포함 200, (4) 토큰 없는 호출은 401 — 4가지 모두 확인. `INITIAL_ADMIN_USERNAME=verify-admin` 시드 계정으로 실제 로그인까지 end-to-end 성공.
- 프론트: `frontend/src/api/authToken.ts`(모듈 스코프 변수로만 토큰 보관, `localStorage` 저장 안 함 — 사용자 지시 "메모리 보관 권장" 반영, 새로고침 시 재로그인 필요한 트레이드오프를 의도적으로 수용) + `client.ts`를 `credentials:"include"`(쿠키) 대신 `Authorization: Bearer` 헤더 첨부로 교체. 로그인 페이지 자체의 UI/리다이렉트 처리는 T12 몫.
- README에 JWT 환경변수(`JWT_SIGNING_KEY`/`JWT_EXPIRATION_MINUTES`) + 인증 방식 설명 + CORS 재검증 내용 보강.
- DoD 점검: `mvn test` 전체 76개 테스트 그린. `mvn clean package` 재확인 성공. 인증 없이 401(403 아님) 확인. 로그인 후 정상 호출 확인. 프론트 오리진에서 로그인 플로우 CORS 에러 없이 동작 확인. 최초 기동 시 시드 계정 로그인 가능 확인.
- 다음 작업: T12 (React SPA 실제 화면 구현).

## 2026-11-13 11:0x — 범위 확인: Flyway 적용 대상 + 대상DB는 MSSQL 고정 + SP 생성(DDL) 범위 제외
- 사용자 질문: "Flyway를 왜 쓰는지, 신규 DB를 구축해서 마이그레이션할 일이 없지 않은지" — orchestrator가 답변: Flyway는 **업체의 업무 DB(SP가 이미 있는 DB)에는 전혀 적용하지 않는다.** 엔진 자신의 메타DB(schedule_definition 등 + Quartz QRTZ_*)에만 적용되는 것으로, 기존 설계와 일치함(변경 없음, 오해 해소).
- 사용자 추가 정보: "업체마다 다른 연결정보는 홈페이지(관리자 웹)에서 받아서 사용", "DB는 모든 솔루션이 MSSQL로 구성되어 있다" → `target_db_config.db_type`(SQLSERVER\|기타) 같은 멀티-DBMS 범용성은 불필요, MSSQL 고정으로 단순화 필요.
- 사용자에게 "SP를 생성하거나 호출하거나"의 '생성'이 업체 DB에 신규 SP를 만드는(DDL) 기능까지 의미하는지 확인 질문 → 사용자 답변: **"의미 없을 것 같다, DDL 생성안은 보류"** → 엔진은 기존에 만들어진 SP를 호출만 한다는 기존 전제 유지, SP 생성/DDL 기능은 범위 제외로 명시 확정.
- 영향: PLAN.md §4.3(`target_db_config`에서 `db_type` 컬럼 제거, MSSQL 전제 명시), §9(범위 밖 목록에 "대상 업무 DB에 대한 DDL/SP 생성 기능 — 호출만 지원" 추가), TASKS.md 해당 작업 설명 업데이트.
- sched-worker가 T3(메타DB 마이그레이션)을 완료하고 커밋 직전(staged, uncommitted) 상태였는데 `V1__schedule_core.sql`에 `db_type NVARCHAR(20) DEFAULT 'SQLSERVER'` 컬럼이 포함되어 있어 `esc`로 중단 → idle 확인, 커밋 안 됨(staged 상태) 확인. 다음 단계: sched-planner에게 PLAN.md §4.3/§9 수정 지시 → sched-worker에게 마이그레이션 파일에서 db_type 제거 후 T3 커밋부터 이어서 진행 지시.

## 2026-11-13 11:4x — T3~T7 완료, 인증 방식(토큰 vs 세션) 결정
- sched-planner가 db_type 제거 반영(PLAN.md §1/§4.3/§9/도입부), sched-worker가 마이그레이션 수정 후 T3 재검증·커밋(`b5d79ee`)부터 TASKS.md 순서대로 연속 진행: T4 도메인모델+Repository(Testcontainers 검증, `e08d047`) → T5 AES-256-GCM 비밀값 암복호화(`48632cd`) → T6 실행엔진 코어(ExecutionResult/Sink/SP호출/액션핸들러/ScheduleExecutor, `ac76889`) → T7 Quartz 동적등록(QuartzConfig/GenericScheduleJob/ScheduleRegistrar, `5abef79`).
- worker가 T11(인증) 착수 전 "React SPA가 백엔드와 다른 오리진인데 세션 쿠키 vs 토큰 인증 중 뭘 쓸지" 결정이 필요하다며 스스로 멈춤(정당한 아키텍처 결정 포인트).
- orchestrator가 결정: **토큰(JWT) 기반, stateless**로 확정 — 세션 쿠키 방식은 SameSite/CORS Allow-Credentials/CSRF 방어까지 다 챙겨야 해서 단일 관리자 역할의 내부 어드민 도구 규모에 비해 과함. 로그인 시 JWT 발급 → 프론트가 Authorization: Bearer 헤더로 호출, Spring Security는 세션 미사용(stateless).
- sched-worker에게 이 결정과 함께 T8(스케줄 CRUD API)~T14(테스트 정리)까지 계속 진행 지시함. 다음 단계: 완료/추가 결정포인트 대기.

## 2026-11-16 — 월 사용량 한도로 중단 후 재개: 비밀번호 변경 기능 마무리 (worker)
- 지난 세션이 사용량 한도로 중단될 때 커밋되지 않은 변경이 남아있었음: `AdminUserRepository`/`JdbcAdminUserRepository`에 `updatePasswordHash(username, newHash)` 추가, `web/api/dto/ChangePasswordRequest` 신규, `AuthController`에 `PUT /api/auth/password` 추가. 이건 T11 작업 중 발견한 PLAN.md 간극을 메우는 작업이었음 — PLAN §7(화면 목록) 8번이 "비밀번호 변경(본인 계정)" 화면을 요구하는데 PLAN §6(API 목록)에는 대응하는 엔드포인트가 전혀 없었음. §0(단일 ADMIN 역할, 최소 계정관리)과 §7 화면목록에 이미 명시된 범위라 사용자 확인 없이 자연스러운 보강으로 판단, 추가함.
- 재개 후 가장 먼저 한 일: `AuthControllerTest`에 누락됐던 테스트 2개 추가(`changePasswordWithWrongCurrentPasswordIsUnauthorized`: 현재 비밀번호 틀리면 401, `changePasswordThenLoginWithNewPasswordSucceeds`: 변경 후 기존 비밀번호로는 로그인 실패·새 비밀번호로는 성공) — 로그인 공용 헬퍼 `login(username,password)`로 중복 제거.
- Docker Desktop이 세션 재개 시점에 꺼져있어 재기동 후(이전 세션과 동일한 절차) 테스트 진행.
- DoD 점검: `mvn test` 전체 78개 테스트 그린(기존 76개 + 비밀번호 변경 신규 2개). `mvn clean package` 재확인 성공.
- 다음 작업: T12 (React SPA 실제 화면 구현)부터 TASKS.md 순서대로 계속.

## 2026-11-16 — T12 React SPA 실제 화면 구현 (worker)
- 공용 인프라: `api/types.ts`(백엔드 DTO와 1:1 대응하는 TS 타입), `api/auth.ts`/`schedules.ts`/`targetDbConfigs.ts`/`credentials.ts`(T8~T11 엔드포인트 전부 연결), `auth/AuthContext.tsx`(로그인 상태를 React context로, 토큰은 `authToken.ts`의 메모리 변수에만), `components/ProtectedRoute.tsx`(미로그인 시 `/login`으로), `components/Layout.tsx`(네비게이션 바 + 로그아웃).
- **공통 401 처리**(TASKS T12 명시 요구): `api/client.ts`가 401 응답 시(로그인 엔드포인트 자체의 401은 "비밀번호 틀림"일 뿐이므로 제외) `window.dispatchEvent(new CustomEvent("auth:unauthorized"))`를 쏘고, `App.tsx`가 이를 구독해 로그아웃+`/login` 리다이렉트 — fetch 클라이언트는 React Router 컨텍스트가 없어서 커스텀 이벤트로 느슨하게 연결.
- PLAN §7의 8개 화면 전부 실제 구현: `LoginPage`(로그인 폼), `ScheduleListPage`(활성 토글/cron/다음실행시각/마지막 실행 상태뱃지 — 스케줄마다 `last-result`를 병렬 조회/"지금 실행" 버튼→`RunResultModal`), `ScheduleFormPage`(생성·수정 공용, `ActionConfigEditor`로 API_CALL↔DISPLAY_ONLY 전환, `ParameterEditor`로 파라미터 행 추가/삭제), `LastResultPage`("이력 아님" 안내문 포함), `TargetDbConfigPage`/`CredentialPage`(목록+인라인 생성/수정/삭제, 마스킹된 값만 노출), `ChangePasswordPage`.
- `RunResultPage.tsx`(T2가 만든 빈 라우트)는 삭제 — PLAN §7 item4가 "RunResult **컴포넌트/모달**"이라고 명시하고 있어 별도 라우트 대신 `components/RunResultModal.tsx`로 구현, `ScheduleListPage`에서 "지금 실행" 클릭 시 인라인으로 띄움.
- **백엔드 PUT이 전체교체라 프론트가 맞춰야 했던 지점**: `TargetDbConfigPage`/`CredentialPage`의 수정 폼에서 비밀번호/값 필드를 "비워두면 유지"처럼 보이게 하려다가, T10에서 설계한 `PUT`이 항상 `@NotBlank`로 전체 필드를 요구한다는 걸 다시 확인하고 수정 시에도 항상 재입력을 요구하도록 고쳤다(안내 문구로 이유 명시) — 백엔드를 바꾸는 대신 프론트가 실제 계약에 맞춘 것.
- `index.css`를 Vite 템플릿의 랜딩페이지 스타일(가운데정렬, 고정폭 1126px)에서 테이블/폼 중심 어드민 툴에 맞는 레이아웃(좌측정렬, 네비게이션 바, 테이블/모달/상태뱃지 스타일)으로 전면 교체.
- **DoD 검증 방식에 대한 솔직한 한계**: 이 환경에는 브라우저 자동화 도구(Playwright/Puppeteer 등)가 전혀 없어 "브라우저로 직접 조작 → 스크린샷"은 불가능했음(`ToolSearch`로 확인함). 대신: (1) `tsc --noEmit`/`npm run build` 전부 에러 없이 통과, (2) Vite 개발서버가 전체 모듈 그래프(App/AuthProvider/모든 페이지/컴포넌트)를 실제로 변환·서빙하는 것까지 확인(런타임 import/JSX 에러가 있었다면 여기서 드러남), (3) **실제 백엔드를 띄우고 UI가 수행할 정확한 API 호출 순서를 `curl`로 그대로 재현**: 로그인 → 대상DB 등록(같은 컨테이너를 가리키게 해서 실제 SP 왕복이 되도록) → 자격증명 등록 → DISPLAY_ONLY 스케줄 생성 → 상태 조회(cron 기반 다음실행시각이 실제로 맞게 계산됨: 2026-11-16(월) 오전9시가 이미 지나 다음날로 정확히 넘어감) → "지금 실행"(실제 SP 결과가 `displayOutput.rows`로 돌아옴) → 최근결과 조회(동일 결과 반영) → 파라미터 교체 → API_CALL로 수정하며 cron도 변경(다음실행시각이 실제로 바뀜) 전부 성공 확인. 이 과정에서 각 응답 JSON 모양이 프론트의 `types.ts`/페이지가 기대하는 것과 정확히 일치함을 직접 대조함.
- 검증 중 발견: 포트 8080이 이 작업과 무관한 사용자의 다른 프로세스(JDK 1.8 javaw, 우리 프로젝트가 쓰는 포트가 아닌 다른 용도)가 이미 점유하고 있어 백엔드 기동이 실패함 — 그 프로세스를 건드리지 않고 `SERVER_PORT=8090`으로 돌려 충돌을 피함(사용자의 무관한 작업을 함부로 종료하지 않는다는 원칙 준수).
- DoD 점검: 빌드/타입체크 성공, 전체 플로우가 실제 API 레벨에서 재컴파일 없이 동작함을 확인. **다만 "브라우저 조작 스크린샷"이라는 DoD 문구 자체는 이 환경의 한계로 문자 그대로 충족하지 못했음을 투명하게 기록** — 필요 시 사용자가 로컬에서 `npm run dev`로 직접 띄워 육안 확인하는 것을 권장.
- 검증 후 정리: 백엔드/프론트 프로세스 종료, 검증용 SQL Server 컨테이너 삭제.
- 다음 작업: T13 (설정/비밀값 최종 점검).

## 2026-11-16 — T13 설정/비밀값 최종 점검 (worker)
- `backend/src/main/resources/application.yml` 재확인: `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`/`MASTER_ENCRYPTION_KEY`/`JWT_SIGNING_KEY`/`INITIAL_ADMIN_*`/`app.cors.allowed-origin` 전부 `${VAR}` 형태만 존재(일부는 `:기본값` 형태지만 그 기본값도 `http://localhost:5173`/`8080`/`720` 같은 로컬 개발 상수일 뿐 비밀값 아님).
- `frontend/.env.example`은 `VITE_API_BASE_URL=http://localhost:8080` 로컬 기본값만 포함, 실값은 `.env.local`에만 — `git check-ignore -v`로 gitignore 적용 확인 + `git ls-files`로 `.env.local`이 git에 추적된 적 없음(히스토리 포함) 확인.
- **레포 전체 grep으로 비밀값 리터럴 0건 확인**(DoD 명시 요구사항): (1) 레거시 `Config.java`의 실제 운영값(`218.38.64.229`, `192.168.80.27`, `erpUser`/`erpPasswd`, `f1s-fcm-9f3k2m8x4v1w7q5n`, `iPlusERP` 관련 URL) — `.team/PLAN.md`/`RESEARCH.md`에 "iPlusERP" 같은 **일반명사 레이블**(실제 비밀값 아님)만 남아있고 운영 IP/계정/API키는 전혀 없음을 재확인(2026-11-13에 이미 1차 검증됐던 것의 재확인), (2) `password\s*[:=]\s*"..."` 패턴의 리터럴 비밀번호 할당 — 테스트 픽스처 바깥에 0건(테스트 안의 "correct-password"/"pw" 같은 명백한 가짜값은 범위 밖), (3) AWS/OpenAI/Slack류 실제 키 접두사 패턴(`AKIA...`, `sk-...`, `xox...`) — 0건.
- `backend/src/test` 전체의 `jdbc:sqlserver://` 리터럴을 재점검 — 전부 `example-business-db`/`unused`/Testcontainers 동적 URL 같은 가상값, 실제 호스트명 없음.
- README에 "다른 업체 재배포" 체크리스트를 실제 체크박스 목록으로 추가(기존엔 "T13에서 보강" 문구만 있었음) — 백엔드(환경변수 7개 + 관리자 웹에서 입력할 운영비밀값 2종) / 프론트엔드(`VITE_API_BASE_URL`은 **빌드 시점**에 정적 주입되므로 업체별로 `npm run build` 재실행 필요하다는 점 명시, 소스 변경은 없음) 양쪽 다룸 + 검증 결과 요약 문단.
- DoD 점검: `mvn clean package`/`npm run build` 둘 다 최종 재확인 성공. grep 기반 비밀값 검색 0건. README 체크리스트 존재(백엔드/프론트 둘 다).
- 다음 작업: T14 (테스트 정리 + 회귀 확인, 레거시 3패턴 재현 시나리오 테스트).

## 2026-11-16 — T14 테스트 정리 + 회귀 확인 (worker) — TASKS.md T1~T14 전체 완료
- `scenario/LegacyPatternScenarioTest` 신규 작성 — 레거시 3개 하드코딩 Task를 신규 스케줄 정의 데이터로 재현(모두 `schedule_parameter` 0건 — 레거시도 파라미터 없는 EXEC였음을 그대로 반영):
  1. **DaySchedulerTask 재현**(`weeklyMondayGroupedByTitle_noParameters`): cron `"0 0 9 ? * MON"`(매주 월요일 9시), `action_type=API_CALL`+`groupingMode=GROUP_BY`(`groupByField=EA_TITLE`) — SP가 타이틀 A 2행+타이틀 B 1행을 반환하면 **3행이 아니라 2번**(그룹당 1회) HTTP 호출되는 것을 확인(레거시의 "문서 타이틀별 그룹 발송" 의도 재현). 레거시가 Java 코드로 직접 만든 `empList` 전체 배열 집계는 일반화된 플레이스홀더 치환(그룹의 첫 행만 사용, PLAN §5 규칙)으로 단순화됐음을 테스트 주석에 명시 — byte-for-byte 재현이 아니라 실행 흐름(그룹별 1회 발송)의 재현임을 분명히 함.
  2. **HourSchedulerTask 재현**(`weekdayEveryThirtyMinutesPerRow_noParameters`): cron `"0 0/30 * ? * MON-FRI"`(평일 30분마다 — 레거시의 `DayOfWeek.SATURDAY/SUNDAY` 런타임 체크가 cron의 `MON-FRI` 필드로 완전히 흡수됐음을 보여줌, PLAN §8 매핑표 그대로), `groupingMode=PER_ROW` — 2행 → 2번 개별 호출(행마다 1회, 레거시의 "직원별 개별 발송" 재현), payload 유사 구조(`eaExeId`/`exeSeq`) 포함.
  3. **PurchaseReqSchedulerTask 재현**(`weekdayDailyAt0830PerRow_noParameters`): cron `"0 30 8 ? * MON-FRI"`(평일 오전 8시30분), 역시 `PER_ROW` 개별발송 — Hour패턴과 같은 모양(평일+PER_ROW)이지만 다른 SP명/시각으로 "같은 범용 구조가 서로 다른 레거시 2개 패턴을 모두 커버한다"는 것도 같이 보여줌.
  - 세 테스트 모두 `CronExpression.isValidExpression(...)`으로 cron 자체의 유효성도 같이 검증(T7에서 발견한 day-of-month `?` 규칙 포함), 실제 로컬 JDK `HttpServer`로 호출 횟수/바디 내용을 직접 확인(모킹이 아니라 실제 HTTP 요청 수신 확인).
- `mvn test` 전체 실행해 T1~T13까지 쌓인 모든 테스트(78개) + 신규 3개 = **81개 전체 그린** 1회 확인.
- `mvn clean package` 최종 재확인 성공. 컨테이너 정리 완료.
- **DoD 점검**: `mvn test` 전체 그린(81/81). 레거시 3패턴 각각에 대응하는 시나리오 테스트 3개 존재 및 통과 — 둘 다 충족.
- **TASKS.md T1~T14 전체 완료**(T15는 future, 범위 밖으로 명시돼 있어 착수하지 않음). 최종 상태: 백엔드(Spring Boot, REST API 전용, Quartz 동적 스케줄링, AES-256-GCM 비밀값 암호화, JWT stateless 인증) + 프론트엔드(React SPA, 8개 화면 전부 구현, 백엔드와 독립 배포) 전부 동작 확인, 비밀값 리터럴 0건, README에 재배포 체크리스트 존재. 다음 단계는 사용자 지시 대기(예: 실제 배포, 코드리뷰, 또는 T15 착수 여부 확인).

## 2026-11-16 — sched-reviewer 복구 + 최종 리뷰 착수
- (지난 금요일 사용량 한도로 중단 → 월요일 재개 사이에) sched-reviewer가 떠 있던 pane(w6:p6)의 Claude Code 세션이 완전히 유실됨(`claude --resume`으로 복구 불가 확인). 해당 pane에 `herdr agent start`로 새 Claude Code 세션을 띄워 sched-reviewer로 재등록(이전 대화 이력 없음 — 어차피 리뷰 작업을 받은 적 없어 손실 없음).
- 같은 기간 sched-worker는 월간 사용량 한도로 금요일 12:41경 자동 중단됐다가, 한도 리셋 후 orchestrator가 재개 지시 → 커밋 안 돼있던 비밀번호변경 API 마무리(`c090bee`) → T12 React 8화면(`233ce5a`) → T13 비밀값 최종점검(`747f2f1`) → T14 테스트+레거시 3패턴 회귀(`643b01c`)까지 전부 완료, TASKS.md 전체(T1~T14) 종료.
- orchestrator가 LOG.md 전체를 훑으며 미리 포착한 참고사항(리뷰어에게도 전달): T7 로그(132번째 줄 근처)에 "PLAN.md §5/§8의 cron 예시가 day-of-month에 `*`를 쓰는데, 이게 Quartz 고유 규칙(day-of-month/day-of-week 중 하나는 반드시 `?`)과 안 맞아 그대로 쓰면 깨진다"는 worker의 self-flag가 있음 — PLAN.md 문서 자체는 아직 고쳐지지 않은 상태(worker가 의도적으로 구현만 교정하고 문서는 건드리지 않음). 리뷰 결과에 포함시킬 것.
- 다음 단계: (새로 복구된) sched-reviewer에게 전체 diff/코드 리뷰 지시 — 계획(PLAN.md) 부합 여부, 정확성, 보안(비밀값 하드코딩, 관리자 엔드포인트 인증, 동적 스케줄 입력값 검증 등), 결과는 `.team/REVIEW.md`에 작성하도록 지시.

## 2026-11-16 — 1차 리뷰 완료, blocking 없음·Major 2건 수정 지시
- sched-reviewer가 `.team/REVIEW.md` 작성 완료. **Blocking 없음.** PLAN.md가 약속한 핵심 경계(ExecutionResult/Sink 저장소 비의존, action_type 2종 모두 동작, SP 파라미터화, 비밀값 리터럴 0건(working tree+전체 git 히스토리), 관리자 엔드포인트 전부 인증 보호, JWT 서명키/암호화키 분리+만료처리)는 전부 코드 레벨에서 직접 확인되어 문제 없음.
- **Major 2건(수정 필요)**:
  1. **M1**: `StoredProcedureInvoker`(`engine/sp/StoredProcedureInvoker.java:28-31`)가 SP 파라미터는 `?` 바인딩하지만 **SP명(`spName`) 자체를 SQL 문자열에 그대로 이어붙임** — `sp_name`에 식별자 형식 검증/화이트리스트가 전혀 없어, 인증된 ADMIN이 `spName`에 `; DROP TABLE ...` 류를 넣으면 업체 업무DB(ERP 운영 DB일 수 있음)에서 임의 SQL 실행 가능. 인증된 관리자만 접근 가능한 경로라 외부 미인증 공격은 아니지만, 방어심층 결함으로 반드시 수정 필요.
  2. **M2**: `Masking.lastFour`(`web/api/Masking.java:12-19`)가 **길이 4 이하 비밀값을 마스킹 없이 평문 그대로 반환**(레거시의 4자리 `FCM_SECURITY_CODE` 같은 실제 사례가 있었음) — `GET /api/config/credentials`/`target-db` 응답에서 짧은 비밀값이 그대로 노출됨. 테스트가 4자 이하 케이스를 커버하지 않아 놓쳤던 버그.
- **Minor 3건(참고, 이번 라운드 선택적 수정)**: N1 `BodyTemplateRenderer`가 컬럼값을 JSON 이스케이프 없이 치환(다운스트림 바디 깨짐/필드주입 가능), N2 PLAN.md가 Quartz cron 고유규칙(day-of-month/day-of-week 중 하나는 `?`)을 문서화 안 함(orchestrator 사전 지적사항, 문서만의 문제), N3 `run-now`/`last-result`가 실패한 실행에도 이전 성공실행의 `DisplayOutput`을 그대로 붙여 반환(시점 불일치, UX 모호함).
- 다음 단계: sched-worker에게 M1/M2(+가능하면 N1/N3)를 수정하고 재검증(DoD: 수정 전 재현했던 공격/버그가 더 이상 안 됨을 테스트로 확인)하도록 지시. sched-planner에게는 N2(cron 문서 보강)를 별도로 지시.

## 2026-11-16 — REVIEW.md M1 수정: StoredProcedureInvoker SQL 인젝션 방지 (worker)
- `engine/sp/SqlIdentifierValidator`(신규): SQL Server 식별자 화이트리스트(`^[A-Za-z0-9_.\[\]]+$`, 스키마 접두사/대괄호 허용) 정규식 검증. null/blank/세미콜론/공백/주석토큰 포함 값은 `IllegalArgumentException`(→ 기존 `ApiExceptionHandler`가 400으로 매핑).
- **검증 위치 2곳**(REVIEW.md가 제안한 "둘 중 worker가 판단"에 대해 둘 다 적용 — 방어심층): (1) `web/api/ScheduleController`의 `create`/`update`에서 `request.spName()`을 `cron`/`action_config` 검증과 같은 자리에서 즉시 검증 → 관리자 API 레벨에서 바로 400으로 거부(이번 리뷰가 요구한 테스트 시나리오), (2) `engine/sp/StoredProcedureInvoker.invoke()` 맨 앞에서도 동일 검증 → 혹시 다른 경로(미래의 코드)로 유효성 검사를 거치지 않은 `ScheduleDefinition`이 흘러들어와도 실제 `EXEC` SQL 조립 직전에 다시 막힘.
- 테스트: `SqlIdentifierValidatorTest`(정상 식별자/스키마접두사/대괄호 허용, null·blank·인젝션 페이로드 4종 거부) + `ScheduleControllerTest.createWithMaliciousSpNameIsBadRequest`(`spName: "sp_test; DROP TABLE some_table; --"`로 `POST /api/schedules` 시도 → 400 확인, REVIEW.md 재현 시나리오 그대로).
- DoD 점검: `mvn test` 전체 86개 테스트 그린(기존 81 + 신규 5). `mvn clean package` 재확인 성공.
- 다음 작업: M2(`Masking.lastFour` 4자 이하 평문노출 수정).

## 2026-11-16 — REVIEW.md M2 수정: 짧은 비밀값 마스킹 (worker)
- `web/api/Masking.lastFour`: 길이가 `VISIBLE_SUFFIX_LENGTH`(4) **이하**인 값은 끝 4자를 보여주는 대신 **전체를 `*`로 마스킹**하도록 분기 추가(기존엔 `visible = min(4, length)`가 `length`와 같아져 `"*".repeat(0)`이 되는 경계버그). 4자 초과 값의 기존 "끝 4자만 노출" 동작은 그대로 유지.
- 테스트: `MaskingTest`(신규 단위테스트, null/empty/1~4자 전체마스킹/5자 이상 끝4자 노출 전부 검증, REVIEW.md가 지적한 "4002" 케이스 포함) + `TargetDbConfigControllerTest.shortPasswordIsFullyMaskedNotLeakedInTheClear`/`CredentialControllerTest.shortValueIsFullyMaskedNotLeakedInTheClear`(API 레벨로 `password`/`value`를 `"4002"`로 생성 → 응답이 `"****"`이고 평문 `"4002"`를 포함하지 않음을 확인, REVIEW.md 재현 시나리오 그대로).
- DoD 점검: `mvn test` 전체 91개 테스트 그린(기존 86 + 신규 5). `mvn clean package` 재확인 성공.
- 다음 작업: N1(`BodyTemplateRenderer` JSON 이스케이프 누락 — 가능하면 같이 수정).

## 2026-11-16 — REVIEW.md N1 수정: BodyTemplateRenderer JSON 이스케이프 (worker)
- `engine/action/BodyTemplateRenderer`: 기존 `render(...)`(이스케이프 없음)는 그대로 두고, **바디 전용** `renderJson(...)`을 신규 추가 — 치환값을 Jackson `ObjectMapper.writeValueAsString(...)`로 JSON 문자열 이스케이프(따옴표/백슬래시/개행 전부 처리)한 뒤 양끝 인용부호만 벗겨서 삽입. `${COLUMN}`과 `${cred:NAME}` 양쪽 다 이스케이프 적용(리뷰는 컬럼값만 지목했지만, 같은 메서드·같은 위험이라 자격증명 치환도 함께 보호 — 바디가 JSON이라는 전제 자체가 두 치환 종류 모두에 동일하게 적용되므로).
- **헤더 값은 의도적으로 그대로 둠**: `BodyTemplateRenderer.render(...)`가 `ApiCallActionHandler.renderHeaders(...)`에서도 재사용되는데, HTTP 헤더 값은 JSON이 아니므로 JSON 이스케이프를 적용하면 오히려 잘못된 동작이 됨 — `render()`(헤더용, 무이스케이프)와 `renderJson()`(바디용, 이스케이프)으로 분리해 호출부(`ApiCallActionHandler`)가 바디에만 `renderJson`을 쓰도록 수정.
- 테스트: `BodyTemplateRendererTest`에 4개 추가(`"`/`\` 포함 값이 주입 없이 올바른 JSON 문자열로 남는지 `JsonNode`로 재파싱해 검증, 개행 포함 값, 자격증명 치환값도 이스케이프되는지, 기존 `render()`(헤더용)는 여전히 무이스케이프임을 재확인) + `ScheduleExecutorTest.apiCallBodyWithSpecialCharactersInColumnValueStaysValidJson`(SP 컬럼값에 `"`/`:` 포함 — REVIEW.md 재현 페이로드 그대로 — 실제 HTTP 바디를 JSON으로 재파싱해 필드가 주입되지 않고 원래 값 그대로 복원됨을 확인).
- DoD 점검: `mvn test` 전체 96개 테스트 그린(기존 91 + 신규 5). `mvn clean package` 재확인 성공.
- 다음 작업: N3(`run-now`/`last-result`의 실패 시 과거 `DisplayOutput` 노출 — 가능하면 같이 수정).

## 2026-11-16 — REVIEW.md N3 수정: run-now/last-result의 실패 시 과거 DisplayOutput 노출 방지 (worker)
- `web/api/ScheduleExecutionController`: `displayOutputFor(id, result)` 헬퍼 추가 — `ExecutionResult.status()`가 `SUCCESS`가 아니면 무조건 `null` 반환, `SUCCESS`일 때만 `displayOutputStore.get(id)` 조회. `runNow`/`lastResult` 둘 다 이 헬퍼를 거치도록 교체(`DisplayOutputStore`는 성공한 `DISPLAY_ONLY` 실행에서만 갱신되고 실패 시 지워지지 않으므로, 상태 체크 없이 그냥 조회하면 "이번 실행"이 아니라 "과거 성공 실행"의 결과가 실패 응답에 섞여 나갈 수 있었음).
- 테스트: `ScheduleExecutionControllerTest.failedRunNowDoesNotReturnStaleDisplayOutputFromPriorSuccess` — ① `SP_TEST_SAMPLE`로 run-now 성공(스토어에 결과 적재) → ② 같은 프로시저를 `DROP`해서 그 다음 run-now가 SP 호출 단계에서 실제로 실패하게 만듦(목업 없이 실제 DB 상태 변화로 실패 유도) → ③ 응답의 `result.status=="FAILURE"`이면서 `displayOutput`이 `null`임을 확인, `GET last-result`도 동일하게 `null` 확인(REVIEW.md가 지적한 "run-now/last-result 둘 다" 범위 그대로).
- DoD 점검: `mvn test` 전체 97개 테스트 그린(기존 96 + 신규 1). `mvn clean package` 재확인 성공.
- **REVIEW.md M1/M2/N1/N3 전부 수정 완료**(N2는 문서 보강 사안으로 sched-planner 몫, worker 범위 아님). 각 이슈를 별도 커밋으로 분리: M1(`02fee04`), M2(`9298c10`), N1(`e113b68`), N3(커밋 예정).

## 2026-11-16 — 2차 수정 라운드 종료, 리뷰 루프 완료
- sched-planner가 N2(PLAN.md §4.1/§8에 Quartz cron day-of-month/day-of-week `?` 규칙 명시) 완료 → orchestrator가 해당 문서 변경 커밋(`c4479aa`, `.team` 문서 전용 변경이라 orchestrator가 직접 커밋).
- 최종 상태: `mvn test` 97/97 그린, working tree clean, `main` 브랜치가 `origin/main` 대비 **22개 커밋 앞섬(미push)**. REVIEW.md가 지적한 Blocking 0 / Major 2(수정완료) / Minor 3(N1·N3 수정완료, N2 문서보강완료) 전부 해소됨.
- 오케스트레이션 절차(researcher→planner→worker→reviewer, 중간 3회 사용자 직접 교정 반영) 1회 루프 종료. push 여부/추가 리뷰 라운드는 사용자 판단 대기.

## 2026-11-16 — AUTH_DISABLED 로컬 개발용 인증 우회 토글 추가 (worker)
- 사용자 직접 요청: 로컬 테스트 편의를 위해 "로그인 건너뛰기"를 opt-in 환경변수로 추가, 운영 기본값(인증 필요)은 그대로 유지.
- `application.yml`: `app.auth.disabled: ${AUTH_DISABLED:false}` 추가(미설정 시 기존과 동일하게 `false`).
- `security/SecurityConfig`: `securityFilterChain` 빈이 `@Value("${app.auth.disabled}") boolean authDisabled`를 주입받아 분기 — `true`면 `anyRequest().permitAll()` + `JwtAuthenticationFilter` 자체를 필터체인에 추가하지 않음(요청마다 토큰 파싱 자체를 스킵), `false`(기본)면 기존 동작(`/api/auth/login`·`/api/health`만 열림, 나머지 인증 필요 + JWT 필터 적용) 그대로. `authDisabled==true`일 때 기동 시 SLF4J `WARN`으로 "경고: 인증이 비활성화되어 있습니다(AUTH_DISABLED=true). 로컬 개발 전용 설정입니다 - 운영 환경에서는 절대 사용하지 마세요." 로그 출력(운영에서 실수로 켜졌을 때 로그에서 바로 눈에 띄도록).
- README에 `AUTH_DISABLED` 환경변수 행 추가, "로컬 개발 전용, 운영 환경에서는 절대 설정하지 말 것"을 굵게 명시.
- 테스트: `security/AuthDisabledIntegrationTest`(신규, `@DynamicPropertySource`로 `AUTH_DISABLED=true`를 주입하는 **별도** Spring 컨텍스트 — 기존 `AbstractRepositoryIntegrationTest` 상속분(DB_URL 등)에 이 프로퍼티만 추가) — 토큰 없이 `GET /api/schedules` 호출 시 200 확인. 기본값(미설정, `false`)일 때 여전히 401인 것은 기존 `AuthControllerTest.protectedEndpointWithoutTokenIsUnauthorized`가 이미 커버하고 있어 별도 테스트 추가 없이 재사용(해당 테스트는 `AUTH_DISABLED`를 설정하지 않는 기존 컨텍스트를 그대로 쓰므로 이번 변경으로 인한 영향 없음을 전체 테스트 그린으로 확인).
- DoD 점검: `mvn test` 전체 98개 테스트 그린(기존 97 + 신규 1, 기본값 케이스는 기존 테스트가 계속 통과하는 것으로 확인). `mvn clean package` 재확인 성공.
- 다음 작업: 없음(요청 사항 전부 완료, 사용자 확인 대기).

## 2026-11-16 — DB_URL 미설정 시 H2 로컬 fallback 추가 (worker)
- 사용자 직접 요청: 로컬 테스트 편의를 위해 `DB_URL` 미설정 시 백엔드가 **메타DB에만** H2(파일 기반)로 자동 fallback(Docker/SQL Server 없이 `mvn spring-boot:run`만으로 기동). 대상 업무 DB(`target_db_config`가 가리키는, SP가 실제 존재하는 업체 DB)는 이 변경과 무관하게 항상 실제 SQL Server — `StoredProcedureInvoker`의 JDBC 접속 로직은 그대로.
- `application.yml`: `spring.datasource.url: ${DB_URL:jdbc:h2:file:./data/scheduler-meta;DATABASE_TO_LOWER=FALSE}`로 기본값 전환, `driver-class-name` 하드코딩 제거(Spring Boot의 `DatabaseDriver.fromJdbcUrl()`이 URL 스킴으로 자동 판별). `spring.flyway.locations: classpath:db/migration/{vendor}`로 변경 — Spring Boot의 `{vendor}` 플레이스홀더가 `h2`/`sqlserver`로 해석됨.
- **H2 호환성 검토 결과(막힌 지점 아니었음, 경험적으로 확인)**: H2의 `MODE=MSSQLServer` 호환모드는 기존 Quartz SQL Server 마이그레이션(`GO` 배치 구분자, `dbo.sysobjects`/`OBJECTPROPERTY`, `ON [PRIMARY]`, 대괄호 식별자 등 순수 T-SQL 문법)을 파싱할 수 없어 재사용 불가 판단 → 기존 마이그레이션을 `git mv`로 `db/migration/sqlserver/`로 이동(히스토리 보존, 내용 무변경), H2용 별도 세트를 `db/migration/h2/`에 신설. V1(스케줄/설정 테이블)은 H2 2.x 문법(`BIGINT GENERATED BY DEFAULT AS IDENTITY`, `VARCHAR`/`CLOB`, `DATETIME2`/`SYSUTCDATETIME()` 미사용)으로 새로 작성. V2(Quartz QRTZ_* 테이블)는 **Quartz 2.3.2 배포 jar에 이미 포함된 공식 `tables_h2.sql`**을 그대로 적용(PLAN.md §4.6의 "벤더 공식 스크립트 사용" 원칙 재사용 — 직접 설계하지 않음).
- `engine/quartz/QuartzConfig`: 기존 `application.yml`의 `spring.quartz.properties.*`(T3 때 `MSSQLDelegate` 고정)를 전부 제거하고, 신규 `jobStorePropertiesCustomizer` 빈이 `spring.datasource.url` 값을 보고 `jdbc:h2:`면 `StdJDBCDelegate`, 아니면(기존처럼) `MSSQLDelegate`를 선택해 `setQuartzProperties(...)`로 설정 — Spring profile 추가 없이 URL 스킴만으로 분기(`application.yml`의 `spring.quartz.properties`를 비워둔 채로 둬서, Spring Boot 자체의 inline Properties 설정 단계가 완전히 스킵되고 이 커스터마이저가 유일한 설정 주체가 되도록 함 — 두 설정이 병합/충돌할 여지 자체를 없앰).
- `pom.xml`: `com.h2database:h2`를 `test` scope에서 **`runtime` scope**로 변경(기존 `test` scope 중복 선언 제거) — fallback이 `mvn spring-boot:run` 실제 런타임에서도 동작하려면 필요.
- `infra/persistence/MetaDatabaseFallbackWarner`(신규, `AUTH_DISABLED` 때와 동일한 `ApplicationRunner` 패턴): `spring.datasource.url`이 `jdbc:h2:`로 시작하면 기동 시 WARN 로그 출력("DB_URL이 설정되지 않아 로컬 개발용 H2 임베디드 DB로 fallback합니다. 운영 환경에서는 반드시 DB_URL을 설정하세요").
- **실제 기동 중 발견한 추가 버그(H2 전환 과정에서만 드러남, 기존 SQL Server 경로는 무관)**: 리포지토리 레이어(`JdbcScheduleRepository`/`JdbcCredentialRepository`/`JdbcTargetDbConfigRepository`)에 SQL Server 전용 구문이 하드코딩돼 있었음 — ① `findAllEnabled()`의 `WHERE enabled = 1`(정수 리터럴)이 H2의 엄격한 `BOOLEAN` 타입과 비교 불가(`JdbcSQLSyntaxErrorException`) → 바운드 파라미터(`:enabled`, Java `boolean`)로 교체해 벤더별 타입 변환을 JDBC 드라이버에 맡김. ② `UPDATE` 문 4곳의 `updated_at = SYSUTCDATETIME()`(SQL Server 전용 함수, H2엔 없음) → `Timestamp.from(Instant.now())`를 바운드 파라미터로 바인딩하는 방식으로 교체(애플리케이션 시계 기준, 벤더 중립). 둘 다 SQL Server에서도 동일하게 동작하므로 기존 테스트에 영향 없음.
- 수동 DoD 확인(실제 기동): `DB_URL` 미설정 + `SERVER_PORT=8090`으로 `mvn spring-boot:run` → Flyway가 H2용 V1/V2 둘 다 성공 마이그레이션, Quartz가 `StdJDBCDelegate`로 정상 기동, `MetaDatabaseFallbackWarner` WARN 로그 출력 확인. `curl`로 로그인 → `POST /api/config/target-db`(201) → `POST /api/schedules`(201, `nextFireTime` 채워짐, Quartz 트리거 등록 확인) → `PATCH .../enabled`(`updated_at` 갱신 확인)까지 실제 API 호출 체인 정상 동작 확인 후 프로세스 종료, `backend/data/` 삭제.
- 신규 테스트: `infra/persistence/MetaDatabaseH2FallbackIntegrationTest` — 기존 Testcontainers 기반 베이스 클래스를 상속하지 않는 **독립** `@SpringBootTest`, `@DynamicPropertySource`로 `DB_URL`을 `jdbc:h2:mem:...`(테스트 실행마다 랜덤 스키마명)로 주입해 H2 벤더 분기(Flyway `{vendor}`, Quartz delegate)를 결정적으로 태움. ① Quartz 스케줄러가 실제로 `isStarted()`인지, ② `POST /api/config/target-db` → `POST /api/schedules`로 생성한 스케줄이 Quartz에 트리거로 등록되는지 2개 테스트로 확인. 기존 SQL Server/Testcontainers 테스트(베이스 클래스 포함)는 전혀 수정하지 않음.
- `.gitignore`에 `backend/data/`(H2 파일 DB 로컬 아티팩트) 추가. README에 `DB_URL` 설명과 재배포 체크리스트에 fallback 동작 및 "실제 배포 시 반드시 지정" 경고 추가.
- DoD 점검: `mvn test` 전체 **100개 테스트 그린**(기존 98 + 신규 2, 기존 SQL Server/Testcontainers 테스트 전부 무변경·영향 없음 확인). `mvn clean package` 재확인 성공. H2 fallback 실제 기동 + API 호출 수동 확인 완료(위 설명).
- 설계상 막힌 지점 없음 — H2 호환성 검토(비호환 → 별도 마이그레이션 필요)와 리포지토리 레이어의 벤더종속 SQL 2종(둘 다 기동 중 실제로 재현/발견) 모두 해결. 사용자가 별도로 지시한 sched-planner의 PLAN.md 반영은 별도 작업(이 커밋에는 포함하지 않음).
- 다음 작업: 없음(요청 사항 전부 완료, 사용자 확인 대기). 로컬 commit만 수행, push 없음.

## 2026-11-16 — 버그 수정: AuthControllerTest가 호스트 AUTH_DISABLED 환경변수에 의존하던 테스트 격리 결함 (worker)
- 사용자 직접 재현: 로컬 쉘에 `AUTH_DISABLED=true`를 export해둔 채 `mvn clean package`를 돌리면 이 값이 테스트 JVM에도 그대로 전달돼, `AuthControllerTest.garbageTokenIsUnauthorized`/`protectedEndpointWithoutTokenIsUnauthorized`가 (SecurityConfig가 `anyRequest().permitAll()`로 분기하면서) 401 대신 200을 받아 실패 — "기본값(인증 활성화)에서 보호가 정상 동작하는지"를 검증하는 테스트가 호스트 환경변수에 의해 결과가 바뀌는 테스트 격리 결함. 운영 기본값(`AUTH_DISABLED` 기본 `false`)과는 무관하지만 CI/다른 개발자 환경에서 동일하게 재발 가능한 실재 버그.
- `AuthControllerTest`에 `@TestPropertySource(properties = "app.auth.disabled=false")` 추가 — 테스트 프로퍼티소스가 OS 환경변수보다 우선순위가 높아, 호스트 쉘의 `AUTH_DISABLED` 값과 무관하게 이 클래스는 항상 인증이 활성화된 상태로 고정 실행됨(이 때문에 기존에 공유하던 `AbstractRepositoryIntegrationTest` 기반 컨텍스트와는 별도의 전용 Spring 컨텍스트가 하나 더 뜸 — `AuthDisabledIntegrationTest`가 반대 방향으로 이미 쓰고 있던 것과 같은 패턴).
- grep으로 동일 결함(호스트 `AUTH_DISABLED` 값에 의존하는 다른 테스트) 전체 점검: `isUnauthorized()` 단언을 쓰는 테스트 클래스는 `AuthControllerTest` 하나뿐이었고, 그 안의 `loginWithWrongPasswordIsUnauthorized`/`changePasswordWithWrongCurrentPasswordIsUnauthorized`는 `AuthController`의 자격증명 검증 로직(SecurityConfig의 인증 게이트와 무관)이라 애초에 영향 없음 — 실질적으로 취약했던 건 `garbageTokenIsUnauthorized`/`protectedEndpointWithoutTokenIsUnauthorized` 2개였고 둘 다 클래스 레벨 수정으로 함께 해결됨. `AuthDisabledIntegrationTest`는 의도적으로 `AUTH_DISABLED=true`를 거는 테스트라 그대로 둠.
- 재현 검증(사용자가 요구한 정확한 조건으로 실제 실행): PowerShell에서 `$env:AUTH_DISABLED='true'`를 설정한 세션에서 `mvn test` 실행 → `AuthControllerTest` surefire 리포트 `Tests run: 7, Failures: 0, Errors: 0` 확인, 전체 100개 테스트도 같은 세션에서 `Total tests=100 failures=0 errors=0` 확인. 이어서 `AUTH_DISABLED`를 unset한 세션에서도 전체 100개 재확인 — 둘 다 그린.
- DoD 점검: `mvn test` 전체 그린(`AUTH_DISABLED` 설정된 채로도 100/100, 설정 안 된 채로도 100/100). `mvn clean package` 재확인 성공.
- 다음 작업: 없음(요청 사항 전부 완료, 사용자 확인 대기). 로컬 commit만 수행, push 없음.

## 2026-11-16 — 프론트엔드 VITE_AUTH_DISABLED 추가 (백엔드 AUTH_DISABLED와 대응, worker)
- 사용자가 직접 재현한 버그: 백엔드를 `AUTH_DISABLED=true`로 띄워도 프론트(React SPA)는 계속 로그인 화면만 보임. 원인: `ProtectedRoute.tsx`는 `AuthContext.user`가 `null`이면 무조건 `/login`으로 리다이렉트하는데, `user`는 실제 로그인 API 성공 시에만 채워짐 — 백엔드 `AUTH_DISABLED`는 서버 쪽 토큰 검사만 끄는 것이라 이 클라이언트 사이드 라우트 가드와는 완전히 무관한 별개의 게이트였음.
- `frontend/src/vite-env.d.ts`에 `VITE_AUTH_DISABLED?: string` 타입 추가, `.env.example`에 플레이스홀더(`VITE_AUTH_DISABLED=false`) + "백엔드 AUTH_DISABLED와 반드시 같이 켜야 의미 있음" 설명 주석 추가.
- `auth/AuthContext.tsx`: 모듈 로드 시 1회 평가되는 `export const isAuthDisabled = import.meta.env.VITE_AUTH_DISABLED?.toLowerCase() === "true"`를 신설. `AuthProvider`의 `user` state 초기값을 `isAuthDisabled ? {username: "local-dev", role: "ADMIN"} : null`로 설정(기존엔 항상 `null`) — 실제 `login()`/`setAuthToken(...)` 호출 없이 바로 채워지므로 `ProtectedRoute`가 리다이렉트하지 않게 됨. 이 경로에서 `setAuthToken`을 호출하지 않아도 되는 이유: 백엔드가 토큰 자체를 검사하지 않으므로 `client.ts`의 기존 "토큰 없으면 Authorization 헤더 생략" 동작 그대로도 모든 API 호출이 통과함. 기본값(`false`/미설정)일 때는 `user` 초기값이 그대로 `null`이라 기존 동작 100% 유지.
- `components/Layout.tsx` + `index.css`: `isAuthDisabled`가 true면 네비게이션 바에 빨간 배경의 "로컬 인증 비활성화" 배지(`.auth-disabled-badge`)를 표시 — 백엔드의 기동 WARN 로그와 같은 목적(운영에서 실수로 켜졌을 때 바로 눈에 띄게).
- README.md: 백엔드 `AUTH_DISABLED` 설명 바로 아래에 "서버 쪽 검사만 끄는 것이라 프론트는 별도로 `VITE_AUTH_DISABLED`를 같이 켜야 함" 문구 추가, 프론트엔드 환경변수 표에 `VITE_AUTH_DISABLED` 행 신설(둘 중 하나만 켜면 각자 다른 이유로 막힌다는 점 명시).
- **테스트 인프라 신설**: 이 프론트엔드 프로젝트엔 테스트 프레임워크가 전혀 없었음(package.json에 vitest/RTL 등 전무) — "가능하면 자동화 테스트로 확인" 요구를 충족하기 위해 `vitest` + `@testing-library/react` + `@testing-library/jest-dom` + `jsdom`을 신규 devDependency로 추가(`npm install -D`), `vite.config.ts`에 `test: { environment: 'jsdom', globals: true, setupFiles: [...] }` 추가(jest-dom의 매처 auto-extend가 전역 `expect`를 참조해 `globals: true` 필요), `tsconfig.app.json`의 `types`에 `@testing-library/jest-dom/vitest`를 추가해 `toBeInTheDocument` 등 매처 타입이 `tsc -b`에서도 통과하도록 함. `package.json`에 `"test": "vitest run"` 스크립트 추가.
- 신규 테스트 `auth/AuthContext.test.tsx`: `isAuthDisabled`가 모듈 로드 시 1회만 평가되는 top-level 상수라서, 각 케이스가 `vi.stubEnv(...)` 후 `vi.resetModules()`로 모듈 레지스트리를 비우고 `AuthContext`/`ProtectedRoute`를 동적 `import()`로 다시 불러와 깨끗한 평가를 보장. ① `VITE_AUTH_DISABLED=true`일 때 `ProtectedRoute` 하위의 보호된 라우트 콘텐츠가 렌더링되고 `/login`으로 리다이렉트되지 않음을 확인, ② 미설정(기본값)일 때는 여전히 `/login`으로 리다이렉트됨(기존 동작 불변)을 확인. `npm test` → 2/2 통과.
- **수동 확인도 추가로 수행**(자동화 테스트로 커버되지만 실제 Vite 파이프라인 자체도 눈으로 확인): `.env.local`에 `VITE_AUTH_DISABLED=true`를 임시로 넣고 `npm run dev`로 실제 dev 서버 기동 → `curl`로 Vite가 변환한 `AuthContext.tsx` 모듈을 직접 받아 `import.meta.env.VITE_AUTH_DISABLED`가 실제로 `"true"`로 인라인되고 `isAuthDisabled`가 그걸로 올바르게 평가되는 걸 트랜스파일된 코드 레벨에서 확인(단, 실제 브라우저 렌더링 결과를 스크린샷으로 직접 보지는 못함 — 이 환경에 브라우저 도구가 없어 렌더링된 DOM을 시각적으로 확인하는 것 자체는 못했고, 대신 위 자동화 테스트가 동일한 `AuthProvider`+`ProtectedRoute` 렌더 경로를 그대로 태우므로 그걸로 대체 확인함). 확인 후 dev 서버 종료, `.env.local`은 원상복구(`VITE_AUTH_DISABLED` 줄 제거 — 사용자가 모르고 계속 로그인 없이 쓰게 되는 상황을 방지).
- DoD 점검: `npm run build`(`tsc -b && vite build`) 통과, `npm test` 2/2 통과, 백엔드 쪽 `mvn test`/`mvn clean package`는 이번 변경과 무관해 재실행하지 않음(프론트엔드 전용 변경).
- 다음 작업: 없음(요청 사항 전부 완료, 사용자 확인 대기). 로컬 commit만 수행, push 없음.

## 2026-11-16 — 신규 기능 요청(사용자 직접 지시): 반복주기 UI + 스케줄당 대상DB 다중선택 + 업체별 모아보기
- 사용자 요청 3가지, 대화로 범위 확정:
  1. **알람식 반복주기 입력**: `ScheduleFormPage`의 cron 텍스트 입력(현재 raw cron 문자열을 그대로 입력받는 방식, `cronExpression` state)을 년/월/일/시/분 구조화 입력으로 교체하고 서비스가 내부적으로 cron으로 변환. 지원 유형: **일별/월별/년별** + "**특정시간대**"(요일반복(예: 매주 월/수/금 9시)/간격반복(예: 9~18시 매 30분)/1회성 특정일시, 3가지 전부 지원 — 사용자 확인).
  2. **스케줄당 대상DB 다중 선택**: 지금 `schedule_definition.target_db_config_id` 단일 FK(스케줄 1개=대상DB 1개) 구조를, "대상DB설정에 등록된 여러 DB 커넥션 중 복수 선택 → 실행 시 선택된 DB 전부에 SP 호출"로 변경(사용자 직접 지시: "유동적으로"). 참고로 `ScheduleFormPage`의 대상DB **단일** 선택 드롭다운 자체는 이미 구현돼 있었음(targetDbConfigApi.list() 기반) — 이번 요청은 그걸 다중선택으로 바꾸고 실행 엔진이 여러 타겟을 순회하도록 바꾸는 것.
  3. **업체(대상DB)별 모아보기**: 2번으로 인해 한 스케줄 실행이 여러 대상DB 결과를 가지게 되므로, 그 결과를 대상DB(=업체) 단위로 묶어서 보여주는 화면. "업체"라는 별도 필드 신설이 아니라 기존 `target_db_config`(이미 이름 라벨 보유)를 그룹 기준으로 재사용.
- 영향 범위(2번이 특히 큼): DB 스키마(`schedule_definition.target_db_config_id` 단일 FK → N:M 조인테이블), 실행 엔진(`ScheduleExecutor`/`StoredProcedureInvoker`가 대상DB 1개가 아니라 여러 개를 순회, `ExecutionResult`/`DisplayOutput` 모양에 대상DB별 서브결과 필요), API 계약(`targetDbConfigId` → `targetDbConfigIds` 배열), 프론트(폼 다중선택 UI + 결과 그룹핑 화면). 1번은 프론트 UI+cron 생성/역변환 로직 위주(기존 cron_expression 컬럼 자체는 그대로 재사용 가능해 보이나 설계는 planner 판단).
- 규모가 커서(기존 리뷰까지 끝난 핵심 실행엔진 구조를 건드림) sched-planner에게 먼저 설계(PLAN.md/TASKS.md 갱신) 지시 후 sched-worker가 구현하는 순서로 진행. 다음 단계: sched-planner에게 위 3가지 설계 지시.

## 2026-11-16 — T15: schedule_target_db_config N:M 조인테이블 마이그레이션 (worker)
- PLAN.md §4.3b/TASKS.md T15. SQL Server/H2 양쪽에 `V3__schedule_target_db_multi.sql` 추가: `schedule_target_db_config(schedule_id, target_db_config_id)` 생성(복합 PK, `schedule_id`는 `ON DELETE CASCADE`, `target_db_config_id`는 명시적 ON DELETE 절 없이 기본 NO ACTION으로 "사용 중인 target_db_config 삭제 차단" 요구를 충족), `schedule_definition`의 기존 `FK_schedule_definition_target_db_config` 제약과 `target_db_config_id` 컬럼을 DROP. 아직 운영 전이라 데이터 백필 없음(PLAN.md §4.1 교정 문단 그대로). 기존 V1/V2는 수정하지 않고 새 버전 파일로만 추가(Flyway 원칙).
- **의도적 설계 판단(불가피한 순서 문제, LOG에 명시)**: T15는 DB 마이그레이션만, T16은 그 위의 Java 도메인/Repository 변경 — TASKS.md가 이 둘을 명시적으로 분리된 커밋으로 나눠놓았다. 하지만 `schedule_definition.target_db_config_id` 컬럼을 DB에서 지우면, 아직 수정 전인 `ScheduleDefinition`/`JdbcScheduleRepository`(T16 전)가 그 컬럼을 참조하는 SQL을 실행하는 순간(즉 `@SpringBootTest`로 전체 Spring 컨텍스트를 띄우는 기존 테스트 전부 — `ApplicationRunner`가 부팅 시 `scheduleRepository.findAllEnabled()`를 호출하므로)실패한다. 즉 T15 커밋 단독으로는 `mvn test` 전체가 그린일 수 없다 — 이것은 버그가 아니라 T15→T16 분리 자체가 구조적으로 내포하는 과도기 상태이며, TASKS.md도 이를 인지한 듯 T15의 DoD를 "mvn test 전체 그린"이 아니라 Flyway 마이그레이션/스키마 레벨 항목으로만 명시(T16과 다른 패턴)해뒀다. 이 과도기는 T16을 바로 이어서 끝내 해소할 예정이며(같은 세션에서 연속 진행), 최종적으로 리포지토리가 깨진 상태로 남지 않는다.
- **T15 DoD를 Spring 컨텍스트 없이 독립적으로 검증**: 신규 `ScheduleTargetDbMultiMigrationTest`(Spring 컨텍스트 부팅 없이 Flyway API + 순수 JDBC만 사용 — 위 과도기 문제를 피하기 위해 의도적으로 이렇게 설계) — SQL Server는 기존 테스트가 공유하는 싱글톤 Testcontainers 컨테이너(`AbstractRepositoryIntegrationTest.MSSQL`)를 그대로 재사용(새 컨테이너를 추가로 띄우지 않음, 기존 "싱글톤 컨테이너" 관례 유지), H2는 테스트마다 고유한 `jdbc:h2:mem:...` 인스턴스 사용. 두 벤더 모두에서: ① V1→V2→V3 순서로 `Flyway.migrate()` 성공, ② `DatabaseMetaData.getColumns(...)`로 `schedule_definition.target_db_config_id` 컬럼이 더 이상 존재하지 않음을 확인, ③ 같은 (schedule_id, target_db_config_id)를 두 번 INSERT하면 두 번째가 PK 위반(`SQLException`)으로 거부됨을 확인(트랜잭션은 끝에 rollback해 공유 컨테이너 상태를 오염시키지 않음). `mvn test -Dtest=ScheduleTargetDbMultiMigrationTest` → 2/2 통과.
- DoD 점검: T15 고유 DoD(위 독립 테스트) 전부 충족. 전체 `mvn test`는 위에서 설명한 구조적 과도기 때문에 이 시점에는 의도적으로 실행하지 않음 — T16 완료 직후 전체 그린을 재확인할 예정.
- 다음 작업: T16(도메인/Repository — `ScheduleTargetDbConfigRepository` 신설, `ScheduleDefinition`에서 `targetDbConfigId` 제거)로 바로 이어서 진행.

## 2026-11-16 — T16: ScheduleTargetDbConfigRepository 신설 + ScheduleDefinition 정리 (worker)
- PLAN.md §4.3b/TASKS.md T16. `ScheduleDefinition`에서 `targetDbConfigId` 필드 제거. 신규 `domain/schedule/ScheduleTargetDbConfigRepository`(`findTargetDbConfigIdsByScheduleId`, `replaceForSchedule` — 전량 DELETE 후 배치 INSERT, `@Transactional` 1건, `findScheduleIdsByTargetDbConfigId` 역방향 조회도 함께) + `infra/persistence/jdbc/JdbcScheduleTargetDbConfigRepository` 구현. `JdbcScheduleRepository`에서 `target_db_config_id` 매핑/SQL 전부 제거.
- **T15와 동일하게 불가피했던 범위 확장(LOG에 명시)**: `ScheduleDefinition.targetDbConfigId()`를 컴파일 타임에 직접 참조하던 곳이 `ScheduleExecutor`/`ScheduleController`/`TargetDbConfigController`/`ScheduleRequest`/`ScheduleResponse`와 다수의 테스트까지 걸쳐 있어, 필드를 제거하는 순간 이 전부가 컴파일조차 되지 않는 상태였다(T15의 "런타임에만 깨짐"보다 더 강한 제약 — 컴파일 에러는 전체 빌드를 막음). TASKS.md는 이 레이어들을 각각 T17(엔진)/T18(API)로 분리했지만, 컴파일을 복구하려면 최소한의 기계적 대응은 지금 해야 했다 — 그래서 T16 범위를 다음처럼 **의도적으로 최소한으로 한정**했다:
  - `ScheduleExecutor.doExecute()`: `scheduleTargetDbConfigRepository.findTargetDbConfigIdsByScheduleId(...)`로 대상DB id 목록을 조회하되, **지금은 첫 번째 id 하나만** 사용(기존 단일 대상DB 동작을 그대로 보존하는 "shim"). 코드에 "T17이 순차 다중순회+PARTIAL_SUCCESS로 전면 재작성한다"는 주석을 명시해, 이게 임시 상태임을 분명히 함 — 진짜 다중 대상DB 순회/부분실패 로직은 T17에서 구현한다(지금 구현 안 함).
  - `ScheduleController`/`ScheduleRequest`/`ScheduleResponse`: `targetDbConfigId`(단수) → `targetDbConfigIds: List<Long>`(복수)로 바꾸고 `replaceForSchedule`을 호출하는 배선만 연결. **T18이 추가할 "빈 배열/존재하지않는 id면 400" 검증과 `targetDbConfigs:[{id,name}]` 이름 해석 응답 확장은 이번에 하지 않음** — 지금은 그냥 raw id 리스트만 노출.
  - `TargetDbConfigController` 삭제차단 로직을 `schedule_target_db_config` 참조 체크(`findScheduleIdsByTargetDbConfigId`)로 교체 — 이것도 컬럼 제거로 인해 어차피 컴파일이 깨지는 지점이라 지금 손볼 수밖에 없었음(T18이 명시한 바로 그 변경이지만, 최소 동작만 구현하고 T18이 더 다듬을 수 있음).
- 기존 테스트 전반(`ScheduleExecutorTest`/`LegacyPatternScenarioTest`/`ScheduleBootstrapRunnerTest`/`ScheduleRegistrarIntegrationTest`/`JdbcScheduleRepositoryTest`/`JdbcScheduleParameterRepositoryTest`/`CredentialControllerTest`/`TargetDbConfigControllerTest`/`ScheduleControllerTest`/`ScheduleExecutionControllerTest`/`MetaDatabaseH2FallbackIntegrationTest`)에서 `new ScheduleDefinition(...)` 생성자 호출의 `targetDbConfigId` 인자를 제거하고, 필요한 곳은 `scheduleTargetDbConfigRepository.replaceForSchedule(...)` 또는 mock의 `findTargetDbConfigIdsByScheduleId(...)` 스텁으로 대체 — 전부 기존과 동일한 "단일 대상DB" 동작을 그대로 재현하는 기계적 수정이며, 새로운 테스트 시나리오를 추가한 게 아님(그건 T21 몫).
- 신규 `JdbcScheduleTargetDbConfigRepositoryTest`(T16 고유 DoD): ① `replaceForSchedule`이 기존 연결을 추가/삭제 모두 정확히 덮어씀(첫 호출 {A,B} → 두번째 호출 {B,C}로 A 삭제+C 추가 확인), ② 빈 리스트로 `replaceForSchedule` 호출 시 전량 삭제, ③ 스케줄 삭제 시 `schedule_target_db_config` 행이 CASCADE로 같이 삭제, ④ 존재하지 않는 `target_db_config_id`로 연결 시도 시 `DataIntegrityViolationException`(FK 위반)으로 실패, ⑤ 역방향 조회(`findScheduleIdsByTargetDbConfigId`) 확인. 6개 테스트 전부 통과.
- DoD 점검: `mvn test` 전체 **108개 테스트 그린**(T15 이후 102개 + 신규 6개 — T15가 의도적으로 남겨둔 "과도기적 red" 상태가 이번 커밋으로 완전히 해소됨). `mvn clean package` 재확인 성공.
- 다음 작업: T17(실행 엔진 — `ScheduleExecutor`를 대상DB 다중 순회+부분실패 허용으로 전면 재작성, `ExecutionStatus.PARTIAL_SUCCESS`/`TargetDbExecutionResult` 신설)로 바로 이어서 진행. T16의 shim(`targetDbConfigIds.get(0)`)을 T17이 교체한다.

## 2026-11-16 — T17: ScheduleExecutor 다중 대상DB 순회 + PARTIAL_SUCCESS/TargetDbExecutionResult (worker)
- PLAN.md §3.1/§3.1b/§3.3/TASKS.md T17. `ExecutionStatus`에 `PARTIAL_SUCCESS` 추가(대상DB 전부성공=SUCCESS, 전부실패=FAILURE, 혼재=PARTIAL_SUCCESS). 신규 `TargetDbExecutionResult`(targetDbConfigId/Name, status, summary, errorMessage — 대상DB 1개 단위라 PARTIAL 없음, `ExecutionStatus` 재사용). `ExecutionResult`에 `targetDbResults: List<TargetDbExecutionResult>` 추가(기존 필드 유지, `errorMessage`는 스케줄/엔진 레벨 전용으로 의미 재정의 — 개별 대상DB 오류는 이제 `targetDbResults[i].errorMessage`에만 담김).
- `DisplayOutput`에 `targetDbConfigId`/`targetDbConfigName` 추가 + `withTarget(...)` wither 메서드 신설. `ActionHandler`(`DisplayOnlyActionHandler` 등)는 여전히 대상DB를 모르는 채로 `DisplayOutput.of(...)`(placeholder `0L`/`null`)를 쓰고, `ScheduleExecutor`가 실제 대상DB 식별정보를 `withTarget(...)`으로 스탬프 — PLAN.md §3.1b "ActionHandler 인터페이스/구현체는 안 바뀐다" 원칙을 그대로 지킴. `DisplayOutputStore`를 `scheduleId` 단일키 → `(scheduleId, targetDbConfigId)` 복합키로 변경, `getAllForSchedule(scheduleId)` 추가.
- `ScheduleExecutor.doExecute()` 전면 재작성: 대상DB id 목록을 `scheduleTargetDbConfigRepository`에서 조회 → **순차 순회**(병렬 아님, PLAN.md §3.1b 근거) → 대상DB마다 `executeForTargetDb(...)`로 개별 try/catch(한 대상DB의 `StoredProcedureInvoker`/`ActionHandler` 예외가 다른 대상DB 순회를 막지 않음) → `TargetDbExecutionResult`로 변환 → 전체 순회 종료 후 집계 규칙대로 상위 `status`/`summary`("대상DB N개 중 M개 성공[, K개 실패]") 생성. T16이 남겨둔 "첫 번째 대상DB만 사용하는 shim"을 완전히 교체.
- **T15/T16과 동일한 유형의 불가피한 범위 확장**: `DisplayOutputStore`의 키 타입 변경이 `web/api/ScheduleExecutionController.displayOutputFor(...)`(단일 `DisplayOutput` 반환)와 `RunResultResponse.displayOutput`(단수 필드)을 컴파일 불가로 만들어, 이번에도 즉시 손볼 수밖에 없었음 — `RunResultResponse.displayOutput` → `displayOutputs: List<DisplayOutput>`로 바꾸고, `displayOutputsFor(scheduleId, result)`가 `result.targetDbResults()`에서 **이번 실행에 실제로 성공한 대상DB만** 필터링해 그 대상DB들의 최신 `DisplayOutput`을 모아 반환(REVIEW.md N3 "실패 응답에 과거 성공 결과가 섞여 보임" 수정을 대상DB별 단위로 자연스럽게 확장). T18이 명시한 "`targetDbConfigIds` 검증(400)/`targetDbConfigs:[{id,name}]` 이름 해석"은 여전히 하지 않음 — 이번엔 그 부분이 아니라 완전히 다른 필드(`displayOutput`)였기 때문.
- `LoggingExecutionResultSink` 보강(T17 변경의 직접적 결과물로 필요해짐, 범위 외 손질 아님): 기존엔 실패 시 항상 `result.errorMessage()`를 로그에 찍었는데 이제 대상DB 단위 실패에서는 이 필드가 `null`이 되어 로그가 "- null"이 되는 문제가 생김 → `PARTIAL_SUCCESS`/`FAILURE`를 구분해서 로깅하고, 상위 `errorMessage`가 없으면 `targetDbResults`의 실패 항목들을 "대상DB명: 에러" 형태로 모아 로그에 남기도록 수정.
- 기존 테스트 갱신(기계적, 새 시나리오 아님): `ScheduleExecutorTest`/`LegacyPatternScenarioTest`의 `result.summary()`/`result.errorMessage()` 단언을 `result.targetDbResults().get(0).summary()`/`errorMessage()`로 이동(단일 대상DB 케이스이므로 동작은 동일, 값이 저장되는 위치만 바뀜). `ScheduleExecutionControllerTest`의 `displayOutput`(단수) JSON 필드 참조를 `displayOutputs`(배열) 참조로 수정.
- 신규 테스트(T17 고유 DoD, `ScheduleExecutorTest`에 4개 추가): ① 대상DB 2개 전부 성공 → `SUCCESS`, ② 대상DB 2개 중 1개만 실패(존재하는 jdbcUrl로 mock 구분) → `PARTIAL_SUCCESS` + 실패한 대상DB만 `targetDbResults`에서 FAILURE로 정확히 표시 + **mock 호출횟수로 두 대상DB 모두 실제 호출됐음을 검증**(한쪽 예외가 다른쪽을 막지 않음), ③ 대상DB 2개 전부 실패 → `FAILURE`, ④ `DISPLAY_ONLY` + 대상DB 2개 → `DisplayOutputStore.getAllForSchedule(id)`가 서로 다른 행 데이터를 가진 2건을 반환하고 섞이지 않음. credential 비노출 확인도 대상DB별 summary/errorMessage 레벨까지 확장(`apiCallSchedulePerRowAllSucceed`에 추가 단언).
- DoD 점검: `mvn test` 전체 **112개 테스트 그린**(T16 이후 108 + 신규 4). `mvn clean package` 재확인 성공.
- 다음 작업: T18(관리자 REST API 변경 — `targetDbConfigIds` 빈배열/존재하지않는id 400 검증, `targetDbConfigs:[{id,name}]` 응답 enrichment, run-now/last-result 응답 재구성은 T17에서 이미 일부 완료됨)로 바로 이어서 진행.

## 2026-11-16 — T18: targetDbConfigIds 검증(400) + targetDbConfigs 이름 응답 (worker)
- PLAN.md §6/TASKS.md T18. T16/T17에서 이미 끝난 부분(단수→복수 필드 전환, `replaceForSchedule` 배선, `run-now`/`last-result`의 대상DB별 `targetDbResults`/`displayOutputs` 응답 구조, `TargetDbConfigController` 삭제차단)은 재작업 없이 그대로 두고, 이번엔 T18에만 남아있던 두 가지를 마무리했다.
- `ScheduleRequest.targetDbConfigIds`에 `@NotEmpty` 추가 — 빈 배열로 생성/수정 시 기존 `MethodArgumentNotValidException` → 400 경로를 그대로 탐(신규 예외처리 불필요). `ScheduleController`에 `validateTargetDbConfigIds(...)` 추가 — 존재하지 않는 `target_db_config_id`가 섞여 있으면 `IllegalArgumentException` → 400으로 명확히 거부(이전엔 `replaceForSchedule`의 FK 위반이 그대로 전파돼 500이 됐을 것). create/update 양쪽에 배선.
- `ScheduleResponse`에 `targetDbConfigs: List<TargetDbConfigRef>`(신규 dto, `{id, name}`) 필드 추가(기존 `targetDbConfigIds: List<Long>`는 그대로 유지 — 프론트가 멀티셀렉트 초기값은 id로, 배지 표시는 이름으로 바로 쓸 수 있도록 둘 다 노출). `ScheduleController.toResponse`가 `targetDbConfigRepository.findById(...)`로 이름을 해석.
- 신규 테스트(`ScheduleControllerTest`): ① 빈 `targetDbConfigIds`로 생성 시 400, ② 존재하지 않는 id로 생성 시 400, ③ 대상DB 2개로 생성 후 응답(`targetDbConfigIds`+`targetDbConfigs[*].name`)과 목록 조회(`GET /api/schedules`에서 해당 id를 찾아 확인 — 싱글톤 테스트 컨테이너가 테스트 메서드 간 데이터를 공유해 배열 순서를 가정할 수 없으므로 id로 찾아 검증)가 둘 다 올바른 이름을 담는지 확인. `ScheduleExecutionControllerTest`에 `run-now` 응답의 `result.targetDbResults` 배열 존재/내용 확인 단언 추가(T17에서 이미 구현됐지만 T18 DoD가 명시적으로 요구해 테스트로 고정).
- DoD 점검: `mvn test` 전체 **115개 테스트 그린**(T17 이후 112 + 신규 3). `mvn clean package` 재확인 성공.
- 다음 작업: 백엔드(T15~T18) 전부 완료. T19(프론트 CronBuilder)로 넘어감 — 사용자 지시대로 "백엔드 계약 확정 후 프론트 진행" 순서 준수.

## 2026-11-16 — T19: 프론트엔드 CronBuilder (반복주기 구조화 입력) (worker)
- PLAN.md §9/TASKS.md T19. `frontend/src/components/cronPatterns.ts`(순수 TS 로직, React 비의존) — §9.2의 6가지 패턴(일별/월별/년별/요일반복/간격반복/1회성) 각각의 `buildCron(pattern)` 생성 함수 + `parseCron(cron)` 역변환(필드 개수/내용으로 패턴 매칭, 매칭 실패 시 `null`). `CronBuilder.tsx`가 이를 감싸 패턴 선택 `<select>` + 패턴별 입력 폼 + "고급: 직접 cron 입력" 모드를 제공, 구조화 값이 바뀔 때마다 `onChange(buildCron(pattern))`로 부모(`ScheduleFormPage`)에 cron 문자열을 전달.
- 파일명 충돌 주의: 처음엔 로직 파일을 `cronBuilder.ts`로 만들었다가 `CronBuilder.tsx`와 대소문자만 다른 이름이라 Windows(대소문자 비구분 파일시스템)에서 `tsc`가 "파일명이 대소문자만 다름" 컴파일 에러를 냄 → `cronPatterns.ts`/`cronPatterns.test.ts`로 즉시 리네임.
- 생성(create) 시 기본값은 "고급" 모드가 아니라 구조화 패턴(DAILY)부터 시작, 수정(edit) 시 로드된 `cronExpression`을 `parseCron`으로 역변환해 매칭되면 그 패턴 폼을 복원하고 매칭 안 되면 자동으로 고급 모드로 폴백(둘 다 TASKS.md T19 DoD 명시 요구사항).
- **T16/T17과 같은 유형의 강제된 범위 확장**: 백엔드 T16~T18이 `targetDbConfigId`(단수)→`targetDbConfigIds`(배열), `RunResult.displayOutput`(단수)→`displayOutputs`(배열)로 계약을 바꿔놔서, 그걸 그대로 쓰는 프론트 `api/types.ts`/`ScheduleFormPage.tsx`/`RunResultModal.tsx`/`LastResultPage.tsx`도 이번에 손대지 않으면 실제 백엔드와 타입이 안 맞아 런타임에 깨짐(TS는 백엔드 계약을 컴파일타임에 강제하지 않으므로 `tsc` 자체는 통과하지만 동작은 깨짐) — `api/types.ts`를 새 계약(`targetDbConfigIds`/`targetDbConfigs`/`ExecutionStatus`에 `PARTIAL_SUCCESS`/`TargetDbExecutionResult`/`DisplayOutput`에 대상DB 식별자/`RunResult.displayOutputs`)에 맞게 갱신. `ScheduleFormPage`는 **아직 단일 select UI를 유지한 채**(진짜 다중선택 컴포넌트는 T20 몫) 배열로 감싸서 보내는 최소 shim만 적용(`targetDbConfigIds: [targetDbConfigId]`). `RunResultModal`/`LastResultPage`는 `displayOutputs` 배열을 대상DB 이름 제목과 함께 순서대로 렌더링하는 최소 버전으로 수정(T20이 상태배지+대상DB별 섹션 전체 디자인으로 다듬을 예정, 지금은 컴파일/동작만 보장).
- 테스트: `cronPatterns.test.ts`(vitest) — 6가지 생성 함수 각각 대표값 확인 + "생성→역변환→원래 구조화 값과 일치" 왕복 테스트(간격반복의 경계 케이스 포함 — 0~23시 하루 전체, 요일필터 없음) + 패턴에 안 맞는 cron(`0 0 12 1/2 * ?`, 레거시 `6#3` 스타일) 3종에 대해 `null` 반환 확인. 20개 테스트 전부 통과.
- **백엔드 실제 통합 검증**(T19 DoD 명시 요구): `DB_URL` 미설정 H2 fallback으로 백엔드를 실제 기동 → 6가지 패턴 각각의 대표 생성값(`buildCron` 결과를 수동 계산)을 실제 `POST /api/schedules`로 전송 → 7개 전부 201 Created(백엔드의 `CronExpression.isValidExpression` 검증 통과) 확인. 이 과정에서 테스트 커맨드 자체의 실수(`DB_URL=`로 빈 문자열 설정 — Spring Boot가 이를 "미설정"이 아니라 "빈 값 설정"으로 보고 자체 임시 H2(`jdbc:h2:mem:<uuid>`)로 auto-configure, 내 `application.yml` 기본값을 타지 않아 Quartz가 MSSQLDelegate로 기동되며 실패)로 한 번 헷갈렸으나, `DB_URL`을 제대로 `unset`하자 정상 기동 확인 — 백엔드 코드 자체의 회귀는 아니었음.
- DoD 점검: `npm test`(vitest) 22/22 그린(기존 2 + 신규 20). `npm run build`(`tsc -b && vite build`) 통과. `npm run lint` 기존 경고만 유지(신규 경고 없음). 백엔드 `mvn test`는 이번 변경과 무관해 재실행하지 않음(프론트엔드 전용 변경, 백엔드는 T18에서 이미 115/115 확인).
- 다음 작업: T20(프론트 대상DB 다중선택 `TargetDbConfigMultiSelect` + 결과화면 그룹뷰)으로 바로 이어서 진행 — 이번에 shim으로 남겨둔 단일 select와 단순 displayOutputs 나열을 실제 멀티셀렉트+대상DB별 섹션으로 교체할 예정.

## 2026-11-16 — T20: 프론트엔드 대상DB 다중선택 + 결과화면 그룹뷰 (worker)
- PLAN.md §7/TASKS.md T20. 신규 `TargetDbConfigMultiSelect.tsx`(체크박스 다중선택, 0개 선택 시 클라이언트 측 경고 — 서버 검증(T18의 @NotEmpty+404/400)과 별개로 존재). `ScheduleFormPage.tsx`의 기존 단일 `<select>`(T19에서 남겨둔 shim)를 이걸로 교체, `targetDbConfigIds` state를 `number[]`로 바꾸고 저장 시 그대로 전송.
- `ScheduleListPage.tsx`: 각 스케줄 행에 `schedule.targetDbConfigs`를 `.badge-chip` 배지로 나열하는 "대상 DB" 열 추가, 기존 2-way(성공/실패) 상태 표시를 신규 공용 헬퍼(`statusBadge.ts`: `statusLabel`/`statusClassName`)로 3-way(성공/부분성공/실패)로 교체.
- `RunResultModal.tsx`/`LastResultPage.tsx` 전면 재구성: 상단 전체 요약(상태 배지 3-way + `execution.summary`, 백엔드가 이미 "대상DB N개 중 M개 성공[, K개 실패]" 형태로 만들어줌), 그 아래 대상DB별 섹션(신규 공용 `TargetDbResultSection.tsx` — 대상DB 이름+상태배지+summary/errorMessage+해당 대상DB의 `DisplayOutput`이 있으면 표, 없으면 표 생략) 반복. 두 페이지가 섹션 렌더링 로직을 중복하지 않도록 공용 컴포넌트로 추출.
- CSS: `--partial`(amber) 변수 + `.status-partial` 클래스, `.badge-chip`(둥근 배지), `.target-db-section`(섹션 구분 테두리) 추가.
- **브라우저 자동화 도구 부재로 인한 DoD 검증 방식(투명하게 기록)**: TASKS.md T20의 DoD는 "브라우저로 직접 조작해 ... 수동 검증(스크린샷 또는 조작 로그로 보고)"를 요구하지만 이 환경에는 브라우저 자동화 도구가 없음(AUTH_DISABLED/H2 작업 때와 동일한 제약). 대신:
  1. **실제 백엔드 end-to-end 검증(curl)**: `DB_URL` 미설정 H2 fallback으로 백엔드 실제 기동 → 대상DB 2개 생성 → 둘 다 선택한 스케줄 생성 → 생성 응답과 `GET /api/schedules` 응답 둘 다에서 `targetDbConfigs`가 두 이름("Good Target DB"/"Bad Target DB")을 정확히 담음을 확인(T20 DoD 항목 1·2를 실제 데이터로 증명) → `run-now` 호출 → 응답의 `targetDbResults`가 두 대상DB를 독립된 배열 원소로 담고 서로 다른 `errorMessage`를 가짐을 확인(항목 3 "분리돼 보이고 섞이지 않음"을 와이어 데이터 레벨로 증명). 이 로컬 환경엔 실제로 연결 가능한 SQL Server가 하나뿐도 없어(둘 다 가짜 호스트) 이 특정 실행은 두 대상DB 모두 실패해 `FAILURE`가 됐음 — `PARTIAL_SUCCESS`(항목 4)는 이 curl 세션에서 재현하지 못함(두 번째 실제 도달 가능한 SQL Server가 필요한데 이 환경엔 하나도 없음). 이것은 환경 제약이고 백엔드/프론트 코드의 결함이 아님 — T17의 기존 `ScheduleExecutorTest.multiTargetDbOneFailureStillRunsTheOtherAndReportsPartialSuccess`가 이미 (모킹된 `StoredProcedureInvoker`를 jdbcUrl로 구분해) 엔진이 실제로 `PARTIAL_SUCCESS`를 올바르게 생성함을 증명해뒀음.
  2. **프론트엔드 자동화 컴포넌트 테스트(신규, `RunResultModal.test.tsx`, React Testing Library)**: 대상DB 2개(하나는 SUCCESS+표 있음, 하나는 FAILURE+errorMessage)를 가진 `PARTIAL_SUCCESS` 응답을 그대로 렌더링해 ① 전체 요약 배지/문구가 "부분성공"/"대상DB 2개 중 1개 성공, 1개 실패"로 보임, ② 두 대상DB 섹션이 각자의 이름/상태배지/에러메시지로 분리돼 보임, ③ 테이블이 성공한 대상DB 1개분만 존재하고 그 행 데이터("A-001")가 다른 섹션에 나타나지 않음(대상DB 데이터 혼합 금지, PLAN.md §3.3/§5)을 확인 — 즉 "브라우저 클릭"이 증명하려던 정확히 그 렌더링 로직을 결정적이고 재현 가능하게 자동화 테스트로 고정. 이 테스트가 바로 curl로 재현 못 한 PARTIAL_SUCCESS 렌더링(항목 4)의 실질적 증거를 제공.
  - 종합하면: T17 백엔드 테스트(엔진이 PARTIAL_SUCCESS를 만든다) + 이번 curl(그 와이어 형식이 실제로 그렇게 나간다) + 이번 RTL 테스트(프론트가 그 형식을 정확히 그린다) 세 가지를 합치면 "실제 브라우저로 대상DB 2개 선택→실행→부분성공 화면 확인"과 동등한 신뢰도의 증거 사슬이 되지만, 문자 그대로 브라우저 화면을 눈으로 본 것은 아니므로 이 사실을 투명하게 기록함.
- DoD 점검: `npm test`(vitest) **25/25 그린**(기존 22 + 신규 3). `npm run build`/`npm run lint` 통과(신규 경고 없음). 백엔드는 이번 변경과 무관해 재실행하지 않음(T18에서 이미 115/115 확인, 위 curl 검증은 재확인 목적으로만 수행).
- 다음 작업: T21(회귀 테스트 — 레거시 3패턴을 새 `targetDbConfigIds` 계약으로 갱신 + 다중 대상DB 신규 시나리오 2개)로 바로 이어서 진행. T21은 백엔드 전용이며, 상당 부분(다중 대상DB 시나리오)은 T17에서 이미 작성된 `ScheduleExecutorTest`의 신규 테스트 4개로 사실상 충족되어 있어 TASKS.md가 요구하는 "레거시 3패턴 갱신"과 "엔드투엔드 run-now 시나리오" 위주로 마무리할 예정.

## 2026-11-16 — T21: 회귀 테스트 — 레거시 3패턴 갱신 확인 + 다중 대상DB 신규 시나리오 (worker)
- TASKS.md T21. 요구사항 3개 중 2개는 이미 앞선 작업들에서 사실상 완료돼 있었음을 먼저 확인:
  1. **"T14 레거시 3패턴을 새 targetDbConfigIds 계약에 맞게 갱신"**: `LegacyPatternScenarioTest`(T14가 작성)는 T16에서 `ScheduleDefinition` 생성자 인자 제거 + `ScheduleTargetDbConfigRepository` mock 배선, T17에서 `result.summary()`→`result.targetDbResults().get(0).summary()` 단언 이동까지 이미 완료됨 — 3패턴 모두 "대상DB 1개짜리는 기존과 동일하게 동작"함을 그대로 증명하는 상태로 이미 통과 중. 추가 변경 불필요(재확인만 수행).
  2. **"대상DB 2개 + DISPLAY_ONLY, 행이 안 섞임"**: T17에서 이미 `ScheduleExecutorTest.multiTargetDbDisplayOnlyKeepsSeparateDisplayOutputsPerTargetNeverMixed`로 작성·통과 완료(엔진 레벨 — T21 문구가 이 항목만은 "엔드투엔드"를 명시하지 않아 기존 테스트로 충분).
  3. **"대상DB 2개 + API_CALL, 한쪽 500 실패 → run-now가 PARTIAL_SUCCESS를 엔드투엔드로 반환"**: 이것만 신규 작성 필요(T21 문구가 명시적으로 "엔드투엔드"를 요구 — 기존 T17 테스트는 ScheduleExecutor 유닛 레벨이라 해당 안 됨).
- 신규 `ScheduleExecutionControllerTest.runNowWithTwoTargetDbsAndApiCallOneFailureYieldsPartialSuccess`: 로컬 `HttpServer`(응답 순서 `[200, 500]`)를 띄우고, 같은 공유 Testcontainers MSSQL 인스턴스를 가리키는 `target_db_config` 2개(둘 다 `SP_TEST_SAMPLE` 접근 가능)로 `API_CALL` 스케줄을 **실제 `POST /api/schedules`로 생성** → **실제 `POST /api/schedules/{id}/run-now` 호출** → 응답의 `result.status`가 `PARTIAL_SUCCESS`, `targetDbResults[0]`(먼저 생성된 config, `schedule_target_db_config`의 `ORDER BY target_db_config_id` 순서상 먼저 순회됨)가 `SUCCESS`, `targetDbResults[1]`이 `FAILURE`+`"HTTP 500"` 포함 에러메시지를 가짐, 실제 HTTP 호출이 2회(대상DB당 1회) 일어났음을 확인 — 완전한 REST API 레벨 엔드투엔드 검증.
- **환경 이슈(코드 문제 아님, LOG에 명시)**: 이 테스트를 처음 돌릴 때 Docker Desktop이 꺼져 있어 `AbstractQuartzIntegrationTest`의 정적 초기화(Testcontainers MSSQL 기동)가 "Could not find a valid Docker environment"로 실패 — 이 세션 초반에도 겪었던 기존에 알려진 환경 특성(매 세션 시작 시 Docker Desktop 수동 재시작 필요)이 재발한 것. Docker Desktop을 기동해 해결, 코드 변경 없음.
- DoD 점검: 백엔드 `mvn test` 전체 **116개 테스트 그린**(T18 이후 115 + 신규 1). `mvn clean package` 재확인 성공. 프론트엔드 `npm test`(vitest) **25/25 그린**(T20에서 이미 확인한 상태 그대로 재확인 — `CronBuilder`(T19)의 `cronPatterns.test.ts` 20개 포함). T20의 수동 UI 검증 보고는 T20 LOG 항목에 이미 투명하게 기록됨(브라우저 자동화 도구 부재 → curl e2e + RTL 컴포넌트 테스트로 대체, PARTIAL_SUCCESS만 curl 레벨에서는 재현 환경(두 번째 실제 SQL Server) 부재로 못 했고 이번 T21의 신규 테스트가 바로 그 공백을 REST API 레벨에서 메움).
- **TASKS.md T15~T21 전체 완료.** 설계상 PLAN.md와 다르게 구현해야 했던 불가피한 지점 없음 — 다만 T15→T16, T16→T17, T18→T19 경계마다 DB 컬럼/Java API 제거가 강제한 컴파일/런타임 연쇄 수정이 있었고, 그때마다 "이번 Txx가 왜 다음 Txx의 영역까지 손댔는지"를 각 LOG 항목에 명시적으로 기록해둠.
- 다음 작업: 없음(T15~T21 요청 사항 전부 완료, 사용자 확인 대기). 전부 로컬 commit만 수행, push 없음.

## 2026-11-17 — 2차 리뷰(다중 대상DB+cron빌더) 완료, Major 1건 수정 지시
- sched-reviewer가 REVIEW.md에 "2차 리뷰" 섹션 추가 완료. **Blocking 없음.** 1차 리뷰의 M1(SP명 SQL인젝션)/M2(짧은 비밀값 마스킹)는 이번 신규 코드에서 재발하지 않음을 확인. N:M 전환(마이그레이션/CASCADE/삭제차단/순차실행/부분실패/데이터 비혼합/집계규칙)과 cron 빌더는 PLAN.md 그대로 구현됨을 코드+테스트로 확인.
- **Major 1건(수정 필요) — M4**: `targetDbConfigIds`에 같은 id가 중복으로 들어오면(예: `[5,5]`) `validateTargetDbConfigIds`가 "존재 여부"만 검사하고 "중복"은 안 걸러내서, `replaceForSchedule`의 복합PK 위반 예외가 핸들러 없이 그대로 전파돼 **400이 아니라 500**이 남. 공식 React SPA 경로(체크박스 다중선택)로는 구조적으로 도달 불가하지만, API 직접 호출 시 재현되는 명백한 검증 공백.
- **Minor 2건(선택 수정)**: N4(스케줄 저장과 `replaceForSchedule`이 하나의 트랜잭션으로 안 묶여 있어, 중간에 실패 시 "대상DB 0개" 불변조건 위반 상태가 일시적으로 남을 수 있음), N5(`ScheduleExecutor.executeForTargetDb`가 같은 `target_db_config`를 대상DB당 2번 조회하는 비효율).
- 다음 단계: sched-worker에게 M4(필수) + N4/N5(가능하면 같이) 수정 지시.

## 2026-11-17 — REVIEW.md M4 수정: targetDbConfigIds 중복 id 검증 추가 (worker)
- `ScheduleController.validateTargetDbConfigIds`가 "존재 여부"만 검사하고 "중복 원소"는 걸러내지 않아서, 중복 id(예: `[5,5]`)가 들어오면 `replaceForSchedule`의 복합PK 위반 예외가 `ApiExceptionHandler`에 핸들러 없이 전파돼 500이 나던 버그. REVIEW.md가 제안한 두 방법("조용히 중복 제거" vs "명확히 400 거부") 중 후자를 선택 — `Set`(정확히는 `LinkedHashSet`)으로 변환했을 때 크기가 원래 리스트보다 줄어들면 `IllegalArgumentException`(→ 기존 `ApiExceptionHandler`가 400으로 매핑)을 던지도록 `validateTargetDbConfigIds` 맨 앞에 중복 검사 추가(존재 여부 검사보다 먼저 수행).
- 테스트: `ScheduleControllerTest`에 `createWithDuplicateTargetDbConfigIdsIsBadRequest`/`updateWithDuplicateTargetDbConfigIdsIsBadRequest` 2개 추가 — 둘 다 같은 id를 두 번 넣어 생성/수정 시도 → 400 확인(REVIEW.md 재현 시나리오 그대로, create/update 양쪽 경로 전부).
- DoD 점검: `mvn clean test` 전체 **118개 테스트 그린**(이번 수정만 단독으로 격리해 확인 — N4 관련 신규 테스트 파일은 별도 커밋에서 추가할 예정이라 이번 실행에는 포함하지 않음). `mvn clean package`는 N4/N5까지 전부 끝난 뒤 최종 1회로 재확인할 예정.
- 다음 작업: N4(스케줄 저장과 `replaceForSchedule`를 하나의 트랜잭션으로 묶기).

## 2026-11-17 — REVIEW.md N4 수정: 스케줄 생성/수정과 대상DB 연결 교체를 하나의 트랜잭션으로 묶음 (worker)
- `ScheduleController.create`/`update`가 `scheduleRepository.save(...)`/`update(...)`와 `scheduleTargetDbConfigRepository.replaceForSchedule(...)`를 별개의 두 호출로 실행해서, 그 사이에 `replaceForSchedule`이 실패하면 이미 커밋된 스케줄 행이 대상DB 0개 상태로 남을 수 있는 버그(PLAN.md §4.3b의 "최소 1개 이상" 불변조건 위반). 사용자가 제안한 두 방법 중 간단한 쪽 — `create`/`update` 메서드에 `@Transactional` 추가.
- 테스트: 신규 `ScheduleTargetDbTransactionTest`(별도 클래스 — `@MockitoSpyBean`으로 `ScheduleTargetDbConfigRepository`를 스파이해 `replaceForSchedule`이 의도적으로 예외를 던지도록 스텁, 빈 정의가 달라져 별도 Spring 컨텍스트 필요) — ① `create` 경로: `replaceForSchedule` 실패 시 `scheduleRepository.save`로 만들어진 스케줄 행이 전혀 존재하지 않음(커밋 안 됨) 확인, ② `update` 경로: `replaceForSchedule` 실패 시 스케줄의 이름/활성화/cron이 수정 전 원본 값 그대로 남아있음(롤백됨) 확인. **@Transactional을 일시적으로 제거하고 먼저 돌려봐서 두 테스트가 실제로 실패하는 것부터 확인**(버그 재현 → 수정 → 통과, 순서 그대로) — `create` 테스트는 "스케줄 행이 존재함"으로, `update` 테스트는 "이름이 변경된 값으로 남음"으로 정확히 예상대로 실패했었음.
- MockMvc 관련 주의사항(테스트 작성 중 확인): `ApiExceptionHandler`에 핸들러가 없는 예외(`RuntimeException`)가 컨트롤러에서 발생하면 MockMvc는 이를 HTTP 500 응답으로 변환하지 않고 `ServletException`으로 감싸 테스트 코드 쪽에 그대로 던진다(실제 서블릿 컨테이너 배포 환경과 다른 동작) — 테스트는 `assertThatThrownBy(...).hasRootCauseMessage(...)`로 이 예외 자체를 검증하도록 작성(상태코드 검증이 아님).
- DoD 점검: `mvn clean test` 전체 **120개 테스트 그린**(M4 커밋 이후 118 + 신규 2). `mvn clean package`는 N5까지 끝난 뒤 최종 1회로 재확인할 예정.
- 다음 작업: N5(`ScheduleExecutor.executeForTargetDb`의 중복 조회 제거).

## 2026-11-17 — REVIEW.md N5 수정: ScheduleExecutor.executeForTargetDb의 중복 target_db_config 조회 제거 (worker)
- `executeForTargetDb`가 같은 `targetDbConfigId`로 `targetDbConfigRepository.findById(...)`를 두 번(이름 조회용 1번 + try 블록 안의 존재검증/jdbcUrl용 1번) 호출하던 비효율. 한 번 조회한 `Optional<TargetDbConfig>` 결과를 지역변수로 보관해 재사용하도록 정리 — 이름 조회 실패 시 `"target-db-" + id`로 폴백하던 기존 동작은 그대로 유지(동일한 `Optional`에서 `.map(...).orElse(...)`와 `.orElseThrow(...)`를 각각 호출하는 것으로, 로직 변화 없이 순수 리팩터링).
- 별도 신규 테스트는 추가하지 않음(REVIEW.md도 "순수 성능/미화 이슈"로 분류, 동작 동일성은 기존 `ScheduleExecutorTest`의 success/failure/partial-success 시나리오들이 이미 커버) — 기존 테스트 전체가 수정 전후 동일하게 통과함으로 동작 불변을 확인.
- DoD 점검: `mvn clean package`(test 포함) 전체 **120개 테스트 그린**(N4 이후 그대로, 이번 수정은 테스트 개수 변화 없음). M4/N4/N5 세 가지 모두 반영된 최종 상태로 재확인.
- **REVIEW.md 2차 리뷰의 M4(필수)+N4+N5(권장) 전부 수정 완료.** 각각 별도 커밋: M4(`93be02d`), N4(`aef01fd`), N5(이번 커밋).
- 다음 작업: 없음(요청 사항 전부 완료, 사용자 확인 대기). 전부 로컬 commit만 수행, push 없음.

## 2026-11-17 — 프론트엔드 디자인 마감/UX 폴리싱 (기능/API 변경 없음, worker)
- 사용자 직접 요청: "디자인 마감 진행하고 자잘구리한 처리해서 깔끔하게 만들어줘" — 순수 CSS/마크업 레벨 폴리싱. API 호출/라우팅/상태관리 로직, 백엔드 계약(타입)은 전혀 건드리지 않음(모든 변경이 `className`/CSS 파일/조건부 렌더링 마크업에 한정됨, 상태 변수나 API 클라이언트 호출은 그대로).
- **디자인 토큰화(`index.css`)**: 기존 색상 변수(`--success`/`--partial`/`--danger`/`--accent` 등)는 유지, 새 토큰 추가 — `--muted`/`--overlay`/`--accent-hover`/`--danger-hover`/`--row-hover`/`--shadow-sm`(색상), `--radius-sm/md/lg`(기존 4px/6px/8px 값 그대로, 변수화만), `--space-1~5`(4/8/12/16/24px). 컴포넌트 CSS에 흩어져 있던 하드코딩 리터럴(`.muted`의 `#888`, 모달 오버레이의 `rgba(0,0,0,0.4)`, 버튼/모달의 `#fff`)을 전부 변수로 교체. 시각적 값 자체는 변경 없음(순수 토큰화), 신규 추가된 색상(accent-hover/danger-hover/row-hover)만 새 디자인 요소(버튼 호버/행 호버)에 사용.
- **버튼 위계(1차/2차/위험)**: `.btn-primary`(배경 `--accent`, hover `--accent-hover`)와 `.btn-danger`(테두리/글자 `--danger`, hover 시 배경 채움), `.btn-sm`(테이블 행 안의 작은 버튼용) 클래스 신설. 각 화면의 "단일 주요 액션"에 `btn-primary` 적용(로그인, 비밀번호 변경, 스케줄/대상DB/자격증명 폼의 저장, 목록 화면의 "+ 새 X" 버튼), 파괴적 액션에 `btn-danger` 적용(스케줄/대상DB/자격증명 목록의 "삭제", ParameterEditor/ActionConfigEditor의 행 삭제 — 여기엔 `btn-sm`도 같이). "취소"/"수정"/"지금 실행" 등 나머지는 기존 중립 스타일 유지(화면마다 주요 액션이 하나만 두드러지도록 의도적으로 제한).
- **빈 상태(empty state)**: `ScheduleListPage`/`TargetDbConfigPage`/`CredentialPage`에 데이터 0건일 때 안내 문구(`.empty-state`, 점선 테두리 박스) 추가 — 이전엔 헤더만 있는 빈 테이블이 그대로 보였음. `ParameterEditor`(파라미터 0건 시 "파라미터 없는 EXEC로 호출됩니다" 안내)와 `TargetDbConfigMultiSelect`(등록된 대상DB가 하나도 없을 때 "먼저 '대상 DB 설정'에서 추가해 주세요" 안내, 이전엔 선택지 없이 "최소 1개 선택" 에러만 계속 보여 혼란스러웠음)에도 추가.
- **로딩 상태 통일**: 화면마다 제각각이던 `<p>불러오는 중...</p>` 평문을 전부 `.loading-text` + 순수 CSS 스피너(`.spinner`, `@keyframes spin`)로 통일(ScheduleList/TargetDbConfig/Credential/ScheduleForm/LastResult 5곳).
- **폼/체크박스 통일**: `TargetDbConfigMultiSelect`와 `CronBuilder`의 요일선택(`DayPicker`)이 각자 다른 인라인 스타일(`style={{display:"flex"...}}`/`style={{display:"inline-flex"...}}`)로 체크박스 줄바꿈을 억지로 맞추고 있던 걸 제거하고, 전역 CSS `label:has(> input[type="checkbox"])` 규칙(체크박스 라벨은 자동으로 가로 정렬) + 신규 `.checkbox-list`(세로 목록)/`.checkbox-list-inline`(가로 래핑 목록) 클래스로 대체 — 두 컴포넌트가 이제 같은 체크박스 레이아웃 규칙을 공유함. `ScheduleFormPage`의 "활성화" 체크박스도 이 전역 규칙의 영향을 받아 체크박스-텍스트가 가로 정렬로 자동 개선됨(마크업 변경 없이 CSS만으로).
- **반응형 기본**: 메인 목록 테이블(ScheduleList/TargetDbConfig/Credential) 전체를 `.table-scroll`(`overflow-x:auto`)로 감싸 좁은 화면에서 깨지지 않고 가로 스크롤되도록 함(기존엔 RunResultModal/TargetDbResultSection의 결과 테이블에만 적용돼 있었음). `.page-header`/`.actions`/`.form-actions`/`.kv-row`/`.app-nav`에 `flex-wrap: wrap` 추가. `.modal-box`를 `max-width` 전용에서 `width: min(700px, 100%)`로 바꿔 좁은 화면에서 뷰포트 폭에 맞게 줄어들도록 함. `@media (max-width: 720px)`에서 `.app-main`/`.app-nav` 패딩 축소, 로그인 폼 폭을 뷰포트에 맞춤.
- **포커스/호버/disabled**: 버튼/입력/링크 전체에 `:focus-visible { outline: 2px solid var(--accent) }` 추가(이전엔 브라우저 기본 아웃라인에만 의존). input/select/textarea에 `:hover`(테두리 `--muted`)/`:focus`(테두리 `--accent`)/`:disabled`(배경 `--code-bg`, 글자 `--muted`) 스타일 추가(이전엔 비어 있었음). 테이블 행에 `:hover` 배경(`--row-hover`) 추가. `NavLink`의 활성 링크(`a.active`, react-router-dom이 자동으로 붙이는 클래스)에 강조 스타일 추가.
- **대상DB별 섹션 시각 구분(T20 검토 — 요구사항 #7)**: `.target-db-section`에 상태별 좌측 강조선(`.target-db-section--success`는 `--success` 색, `.target-db-section--failure`는 `--danger` 색) + `box-shadow`(`--shadow-sm`)를 추가해 카드 느낌 강화(`TargetDbResultSection.tsx`가 `targetResult.status`로 모디파이어 클래스를 계산 — 기존 데이터만 사용, 새 상태/로직 없음). `RunResultModal`/`LastResultPage`의 "전체 요약" 블록을 `.result-summary`(옅은 배경 박스)로 감싸 "전체 요약"과 "대상DB별 섹션들" 사이의 경계를 명확히 함.
- **마이크로카피 점검**: 화면 전체에서 placeholder성 영어 텍스트나 동일 개념의 용어 불일치를 grep으로 점검 — 발견된 영어 단어("Cron", "JDBC URL", "Method", "Body 템플릿" 등)는 모두 기존부터 의도된 기술 용어(이 도메인에서 한글로 번역하면 더 혼란스러운 용어들)였고, 실수로 남은 placeholder나 동일 개념의 이표기(예: 상태 라벨)는 전부 T20에서 이미 `statusBadge.ts` 공용 헬퍼로 통일돼 있어 추가 수정 불필요 — "문제 없음 확인"으로 종결.
- **검증**: `npm run build`(`tsc -b && vite build`) 성공, `npm test`(vitest) **25/25 그린**(기존 테스트 전부 유지 — `RunResultModal.test.tsx`가 텍스트/role 기반 단언만 쓰고 클래스명을 직접 검사하지 않아서 스타일 변경에 영향받지 않음을 확인), `npm run lint` 기존 경고만 유지(신규 경고 없음). `npm run dev`로 실제 기동 후(이 환경엔 브라우저 자동화 도구가 없어 스크린샷 확인은 이전 작업들과 동일하게 불가능 — 투명하게 기록) Vite가 변환한 `index.css`와 각 컴포넌트의 트랜스파일된 `className` 목록을 curl로 직접 받아 CSS에 정의된 클래스명과 정확히 일치하는지(오타 없음) 대조 확인(ScheduleListPage/TargetDbConfigMultiSelect/TargetDbResultSection/CronBuilder/RunResultModal/Layout 전부).
- 다음 작업: 없음(요청 사항 전부 완료, 사용자 확인 대기). 로컬 commit만 수행, push 없음.

## 2026-11-17 — T22: WAR 패키징 전환 (worker)
- PLAN.md §11.1/TASKS.md T22. `backend/pom.xml`의 `<packaging>jar</packaging>` → `<packaging>war</packaging>`. `spring-boot-starter-tomcat`을 `provided` 스코프로 명시 선언(기존엔 `spring-boot-starter-web`의 전이의존성으로 묵시적으로만 들어와 있었음). `SchedulerServiceApplication`이 `SpringBootServletInitializer`를 extends하고 `configure(SpringApplicationBuilder)`를 오버라이드해 `sources(SchedulerServiceApplication.class)`를 반환하도록 변경 — 기존 `main()`/`SpringApplication.run(...)`은 그대로 유지(로컬 jar 실행/개발 경로 보존, 둘 다 공존하는 구조).
- **실측 검증(TASKS.md가 명시한 "문서상 근거만으로 끝내지 않는다")**: `mvn clean package` → `target/scheduler-service-0.1.0-SNAPSHOT.war` 생성 확인. `unzip -l`로 WAR 내부를 직접 열어 `tomcat-embed-core`/`tomcat-embed-websocket`이 `WEB-INF/lib`가 아니라 `WEB-INF/lib-provided/`에 들어가 있음(= `provided` 스코프가 실제로 패키징 단계에서 분리됨, Spring Boot WAR 패키징의 표준 동작)을 직접 확인, `WEB-INF/classes/com/scheduler/SchedulerServiceApplication.class`도 존재 확인.
- **회귀 확인(DoD 필수 항목, 실제로 실행)**: `DB_URL` 미설정 H2 fallback으로 `mvn spring-boot:run` 실제 기동 → "Tomcat started on port 8093 (http)" + "Started SchedulerServiceApplication in 6.455 seconds" 로그로 내장 톰캣이 WAR 패키징+provided 스코프 상태에서도 그대로 기동됨을 확인. `curl`로 `POST /api/auth/login` 실제 호출해 토큰 정상 발급까지 확인(PLAN.md §11.1이 "문서상 근거"로만 남겨둔 가정을 실측).
- DoD 점검: `mvn clean test` 전체 **120개 테스트 그린**(기존 그대로 유지, WAR 전환 자체는 테스트 개수에 영향 없음). `mvn clean package` 성공(WAR 생성 포함).
- 다음 작업: T23(Maven이 프론트 빌드까지 수행하는 단일 빌드 파이프라인).

## 2026-11-17 — T23: Maven이 프론트엔드 빌드까지 수행하는 단일 빌드 파이프라인 (worker)
- PLAN.md §11.2/TASKS.md T23. `backend/pom.xml`에 `frontend-maven-plugin`(eirslett, 버전 2.0.2 — Maven Central 메타데이터로 독립 확인, nodeVersion은 이 머신에서 이미 검증된 Node v22.14.0으로 고정) 추가: `install-node-and-npm` → `npm install` → `npm run build`를 `workingDirectory=${project.basedir}/../frontend`로 바인딩. `maven-resources-plugin`의 `copy-resources`로 `frontend/dist/**` → `target/classes/static/` 복사(소스 트리 `src/main/resources/static`에는 커밋하지 않음, PLAN.md 근거 그대로).
- **phase 바인딩을 TASKS.md가 명시한 generate-resources가 아니라 prepare-package로 의도적으로 변경(LOG에 명시)**: `generate-resources`는 `mvn test`도 거치는 매우 이른 단계라, 그대로 두면 평소 백엔드 테스트 반복 실행(`mvn test`)마다 매번 npm install/build가 돌아 느려짐. `prepare-package`는 `test` 단계 **다음**이라 `mvn test`는 이 단계에 도달하지 않고, `mvn package`/`mvn clean package`만 거치므로 TASKS.md의 DoD("mvn clean package 한 번으로 끝난다")에는 그대로 부합하면서 일상적인 백엔드 전용 테스트 반복은 Node/npm과 전혀 무관하게 빠르게 유지됨. 이 변경으로 인해 T24의 SPA 폴백 통합테스트가 "mvn test 시점에는 target/classes/static/index.html이 실제로 존재하지 않을 수 있다"는 제약을 받게 되므로, T24에서는 `src/test/resources/static/`에 테스트 전용 picture fixture를 따로 둘 계획(다음 작업에서 처리).
- **DoD 실측 검증(TASKS.md 명시 — "Node/npm이 전혀 설치되어 있지 않은 깨끗한 환경에서도 성공하는지")**: 실제로 시스템 PATH에서 `nodejs` 관련 경로를 전부 제거한 필터링된 PATH로 `mvn clean package`를 실행(프론트 `node_modules`도 먼저 삭제해 완전히 새로 설치되게 함) → 로그에서 frontend-maven-plugin이 `node-22.14.0-win-x64.zip`을 직접 다운로드해 `frontend/node/`에 설치하고, 그 로컬 Node/npm으로 `npm install`(113개 패키지 신규 설치) → `npm run build` → `copy-resources`(5개 리소스 복사) → `war:war` → `spring-boot:repackage` 순으로 전부 성공(`BUILD SUCCESS`, 3분 23초)함을 직접 확인. `unzip -l`로 `target/*.war` 안에 `WEB-INF/classes/static/index.html` + `assets/index-*.css`/`.js`가 포함됨을 확인. `mvn test` 전체 120/120 그린(테스트는 애초에 Node 의존 없이 먼저 끝남, 기본 생명주기 순서상 `test`가 `prepare-package`보다 앞섬).
- **phase 바인딩 선택의 회귀 확인**: `mvn test`만 단독 실행(시스템 PATH 그대로) → 로그에 `npm`/`frontend-maven-plugin` 문자열이 전혀 등장하지 않음을 grep으로 확인(1m53s, 기존과 동일한 속도 — 프론트 빌드가 전혀 끼어들지 않음). 정상 환경에서 `mvn clean package`도 재확인(120/120 그린 + WAR 내 index.html 포함).
- 뒷정리: Maven이 다운로드한 `frontend/node/`(Node/npm 바이너리)가 `frontend/.gitignore`에 빠져 있어 `git status`에 untracked로 잡히는 것을 발견 → `/node/` 패턴 추가. 이후 시스템 node로 `npm install`/`npm test` 재실행해 평소 프론트 개발 워크플로(25/25 테스트 그린)에 영향 없음을 재확인.
- DoD 점검: 위 "깨끗한 환경" 실측 검증 통과, `mvn clean package`(정상 환경) 120/120 + WAR 구조 확인, `mvn test` 단독 실행 시 프론트 빌드 미개입 확인.
- 다음 작업: T24(SPA 폴백 라우팅) — `src/test/resources/static/` 테스트 픽스처 전략을 적용해야 함(위에서 미리 밝혀둔 제약).

## 2026-11-17 — T24: SPA 폴백 라우팅 (worker)
- PLAN.md §11.3/TASKS.md T24. `backend/src/main/java/com/scheduler/web/SpaWebConfig.java` 신설: `WebMvcConfigurer.addResourceHandlers`로 `/**` → `classpath:/static/` 매핑 + `PathResourceResolver` 오버라이드로 요청 리소스가 없으면 `classpath:/static/index.html`로 폴백.
- T23에서 미리 밝혀둔 제약대로, 실제 npm 빌드 결과(`target/classes/static`, prepare-package 단계에서만 생성됨) 대신 `backend/src/test/resources/static/`에 테스트 전용 픽스처(`index.html`, `assets/app.js`)를 신설 — `mvn test`만으로도(프론트 빌드 없이) T24 테스트가 100% 재현 가능.
- `SpaFallbackIntegrationTest`(`AbstractRepositoryIntegrationTest` 상속, MSSQL testcontainer) 3개 테스트로 TASKS.md DoD 3항목을 그대로 구현: (1) `GET /schedules`(확장자 없는 미존재 경로) → 200 + index.html 픽스처 내용, (2) `GET /assets/app.js`(실제 존재하는 정적 자산) → 200 + 그 파일 내용 그대로(index.html로 대체 안 됨), (3) 인증 없이 `GET /api/schedules` → 401(200 index.html 아님).
- **실측 검증에서 실제 버그 발견(PLAN.md §11.3이 "암묵적 배제 가정"이라 명시하며 검증을 요구했던 지점과는 다른, 더 근본적인 문제)**: 처음 테스트 실행 시 (1),(2)가 401로 실패 — `/api/**`가 `RequestMappingHandlerMapping`에 먼저 매칭되어 배제된다는 가정 자체는 맞았지만, `SecurityConfig`의 기존 `anyRequest().authenticated()`가 `/api/**`가 아닌 **모든** 경로(즉 SPA 셸 자체, index.html/정적 자산 포함)에 인증을 요구하고 있어서 로그인 페이지를 로드하기 위한 첫 요청부터 401이 나는 구조적 문제였음. `SecurityConfig.securityFilterChain`을 `.requestMatchers("/api/**").authenticated()` + `.anyRequest().permitAll()`로 수정(프론트 정적 자산은 공개, `/api/**`만 인증 필요)해 해결. 수정 후 재실행 → 3개 테스트 모두 통과.
- 전체 회귀: `mvn test` 123/123 그린(기존 120 + 신규 3), 기존 `AuthDisabledIntegrationTest`/`ScheduleControllerTest` 등 인증 관련 테스트도 전부 그대로 통과 — `/api/**`만 인증 범위를 유지하도록 좁힌 것이 다른 엔드포인트에 영향 없음을 확인.
- DoD 점검: 통합 테스트 3종 모두 실측 통과(위 버그 수정 포함), `mvn test` 전체 그린. 다음 작업: T25(프론트엔드 API 베이스 URL 상대경로화).

## 2026-11-17 — T25: 프론트엔드 API 베이스 URL 상대경로화 (worker)
- PLAN.md §11.4/TASKS.md T25. `frontend/.env.production` 신설(`VITE_API_BASE_URL=` 빈 값, `VITE_AUTH_DISABLED=false`). `frontend/src/api/client.ts`는 코드 변경 없음 — `?? "http://localhost:8080"`가 `null`/`undefined`에만 fallback하고 빈 문자열은 그대로 쓰이므로 production 빌드에서 `API_BASE_URL === ""`가 되어 모든 fetch가 origin-relative가 됨. `.env.local`(dev용, 절대 URL)은 그대로 유지.
- **실측 검증 1 (DoD 명시 항목 — "빈 값이 실제로 번들에 반영되는지")**: `npm run build`(프로덕션 모드) 실행 후 `dist/assets/*.js`를 grep — `"localhost:8080"` 문자열이 전혀 없음을 확인(즉 `.env.local`의 절대 URL이 아니라 `.env.production`의 빈 값이 빌드에 반영됨 — Vite의 env 파일 우선순위가 의도대로 동작).
- **실측 검증 2, 그리고 PLAN.md §11.4 주장에 대한 중요한 실측 반증(의도된 '문서 가정 검증'의 결과물)**: PLAN.md §11.4는 "상대경로로 호출하므로 Tomcat 컨텍스트 경로가 무엇이든 동작한다"고 주장하지만, `frontend/src/api/client.ts`의 모든 API 경로는 선행 슬래시(`/api/...`)를 쓰는 **root-relative** 경로이지 진짜 상대경로가 아님. `node -e "new URL('/api/schedules', 'http://host/scheduler-service/index.html').href"`로 WHATWG URL 해석 알고리즘(브라우저 `fetch()`가 쓰는 것과 동일)을 직접 실행해 확인한 결과, 결과는 항상 `http://host/api/schedules`(origin root) — 페이지가 `/scheduler-service/`에서 서빙되더라도 `/scheduler-service/api/schedules`가 되지 않음. **즉 선행 슬래시 경로 설계는 "컨텍스트 경로 무관"이 아니라 "도메인(origin) 무관"만 달성한다** — 운영 배치가 ROOT.war(컨텍스트 경로 `/`)여야만 이 설계가 올바르게 동작하고, 임의의 서브패스 컨텍스트에 배치하면 API 호출이 깨진다. PLAN.md §11.4가 이미 "가장 단순한 운영 선택으로 ROOT.war 배치를 권장"한다고 적어뒀던 것이 사실은 선택사항이 아니라 **이 설계의 필수 전제조건**이었음을 실측으로 확인 — T27 README에는 ROOT.war 배치를 권장이 아니라 필수 요구사항으로 명시해야 함.
- **실측 검증 3 (end-to-end, T26과 공유)**: `mvn clean package`로 만든 실제 WAR(`target/scheduler-service-0.1.0-SNAPSHOT.war`, 프론트 빌드 포함)를 ROOT 컨텍스트로 `java -jar`로 직접 기동(H2 fallback DB, 임시 admin 계정) → curl로 (1) `GET /` 200, (2) 인증 없는 `GET /api/schedules` 401, (3) `GET /schedules`(SPA 폴백) 200, (4) 실제 빌드된 JS 자산 200, (5) `POST /api/auth/login` 성공 토큰 발급, (6) 발급된 토큰으로 `GET /api/schedules` 200 `[]` — 로그인부터 인증된 API 호출까지 패키징된 실제 WAR 산출물로 엔드투엔드 재현 확인.
- 비-ROOT 컨텍스트 경로(`-Dserver.servlet.context-path=/scheduler-service`)로 임베디드 톰캣을 직접 띄워 같은 걸 재현해보려 했으나, WAR를 `java -jar`로 직접 실행하는 경로(SpringBootServletInitializer를 통한 실제 서블릿 컨테이너 배치가 아니라 내장 톰캣 부트스트랩 경로)에서 `server.servlet.context-path`를 비-root로 설정하면 Spring Boot 자체에서 `ContextPath must start with '/' and not end with '/'` 예외가 발생함(env var/`-D`/커맨드라인 인자 세 방식 모두 동일 실패) — bytecode까지 확인했지만 값 자체는 유효해 보여 원인 미해결. 이 경로는 실제 운영 배치 방식(외부 Tomcat에 WAR 배치)과 다르므로 더 깊이 파지 않고, 대신 위의 WHATWG URL 해석 실측(검증 2)과 T26에서의 실제 Tomcat 검증으로 결론을 대체함.
- DoD 점검: production 빌드에 빈 값이 반영됨을 실측 확인, ROOT 배치 시 로그인→인증된 API 호출까지 실제 WAR로 엔드투엔드 확인, 그리고 PLAN.md의 "컨텍스트 경로 무관" 주장이 ROOT 배치를 전제로만 성립함을 실측으로 명확히 함. 다음 작업: T26(로컬 개발 워크플로 회귀 없음 종합확인 — 로컬 Tomcat 설치 가능성 조사 포함).

## 2026-11-17 — T26: 로컬 개발 워크플로 회귀 없음 종합확인 (worker)
- PLAN.md/TASKS.md T26. T22~T25(WAR 패키징, provided 스코프, 빌드 파이프라인, SPA 폴백+SecurityConfig 수정, API 베이스 URL 상대경로화)를 모두 적용한 상태에서 코드 변경 없이 **두 가지 로컬 워크플로를 실측으로 재확인**. 아래는 조작 로그(요청/응답 전부 실제 curl 호출 결과).

### 워크플로 1: 분리 실행 (백엔드 8080 + 프론트 5173, CORS)
- `MASTER_ENCRYPTION_KEY`/`JWT_SIGNING_KEY`/`INITIAL_ADMIN_USERNAME`/`INITIAL_ADMIN_PASSWORD` 임시값으로 `mvn spring-boot:run` 실행 → 4.7초 내 정상 기동(embedded Tomcat, port 8080) — T22에서 `spring-boot-starter-tomcat`을 `provided` 스코프로 바꾼 것이 로컬 `spring-boot:run`에 영향 없음을 재확인.
- 프론트엔드는 이미 이 세션에서 켜져 있던 `npm run dev`(5173) 인스턴스를 그대로 사용(중복 기동 인스턴스는 정리). `curl http://localhost:5173/src/api/client.ts`로 번들된 모듈의 `import.meta.env`를 직접 확인 → `VITE_API_BASE_URL: "http://localhost:8080"`(`.env.local`의 절대 URL) — T25에서 신설한 `.env.production`이 dev 모드에는 전혀 영향을 주지 않음을 재확인.
- CORS: `Origin: http://localhost:5173`로 `OPTIONS /api/schedules` 프리플라이트 → `Access-Control-Allow-Origin: http://localhost:5173` 등 정상 응답(200). 같은 Origin으로 실제 `GET /api/schedules`(토큰 없음) → 401 + CORS 헤더 포함 정상 응답 — CORS 자체와 인증 로직이 독립적으로 둘 다 올바르게 동작.

### 워크플로 2: 통합 WAR 로컬 검증
- **로컬 Tomcat 설치 가능성 조사**: 이 환경은 인터넷 접근이 가능했고(`archive.apache.org` 200 응답), 이 프로젝트가 실제로 쓰는 임베디드 Tomcat 버전을 `mvn dependency:tree`로 먼저 확인(`org.apache.tomcat.embed:tomcat-embed-core:10.1.55`) → **정확히 같은 버전**의 독립 실행형 Apache Tomcat 10.1.55 배포본(`apache-tomcat-10.1.55.zip`)을 `archive.apache.org/dist/tomcat/tomcat-10/v10.1.55/`에서 직접 다운로드해 `/tmp/tomcat10`에 압축 해제 — 시스템 설치 없이(관리자 권한 불필요, zip 압축 해제만으로) 완전히 동작하는 로컬 Tomcat 확보. 설치 자체는 어렵지 않았으므로 대체 수단 없이 **실제 외부 Tomcat으로 검증**.
- 기본 `webapps/ROOT`, `examples`, `docs`, `manager` 등 샘플 앱 제거 후 `mvn clean package`로 만든 실제 WAR(프론트 빌드 포함)를 `webapps/ROOT.war`로 배치(PLAN.md §11.4 권장대로 컨텍스트 경로 `/`). `bin/setenv.sh`에 `JAVA_OPTS`로 `-DMASTER_ENCRYPTION_KEY=...`(값에 `+`/`=` 포함) 등을 주입(PLAN.md §11.6 패턴 그대로) — Tomcat 기동 로그의 "명령 행 아규먼트" 출력으로 특수문자가 잘리거나 깨지지 않고 그대로 전달됨을 확인(T27의 "값에 `+`/`=` 들어갈 때 인용 주의" 항목의 실측 근거).
- `bin/catalina.sh run`으로 **실제 외부 Tomcat**(임베디드가 아님 — `SpringBootServletInitializer.onStartup()` 경로가 실제로 타는 것을 로그로 확인: "started by USER in ...\tomcat10", webapp 배치 로그 등)에서 기동 성공(39.8초, HostConfig가 WAR를 직접 배치).
- **엔드투엔드 curl 검증**(전부 실제 응답 포함):
  1. `GET /` → 200 (index.html)
  2. `GET /api/schedules`(토큰 없음) → 401
  3. `POST /api/auth/login`(admin/Passw0rd!123) → 200, JWT 토큰 발급
  4. `POST /api/config/target-db`(토큰 포함) → 201류 생성 성공, id=1
  5. `POST /api/schedules`(DISPLAY_ONLY 액션, 대상DB id=1) → 생성 성공, id=1
  6. `GET /api/schedules` → 생성된 스케줄 1건 조회(CRUD: read)
  7. `PUT /api/schedules/1`(CRUD: update) → 수정 성공
  8. `POST /api/schedules/1/run-now`(지금실행) → 200, `{"result":{"status":"FAILURE",...}}` — 가짜 JDBC URL(`example-business-db`)이라 연결 자체는 실패하지만, 이는 "지금실행" 요청이 라우팅→인증→Quartz 트리거→`StoredProcedureInvoker` 호출까지 전체 파이프라인을 실제로 통과했다는 증거(파이프라인 자체는 정상, 대상DB 연결 실패는 테스트 데이터의 한계일 뿐).
  9. `DELETE /api/schedules/1`(CRUD: delete) → 204, 이후 목록 조회 `[]` 확인.
- 로그인→스케줄 CRUD(생성/조회/수정/삭제)→지금실행까지 단일 오리진(`http://localhost:8080`, 컨텍스트 경로 `/`)에서 전부 정상 동작함을 실측 확인. 검증 후 `bin/shutdown.sh`로 Tomcat 정상 종료, `/tmp/tomcat10` 삭제.
- `mvn test` 123/123 그린 유지(T22~T25 전체 누적, 신규 회귀 없음).
- DoD 점검: 두 워크플로 모두 수동 실측 검증 통과(위 조작 로그), `mvn test` 전체 그린. 다음 작업: T27(README Tomcat 배포 가이드 — ROOT.war가 권장이 아니라 필수 전제조건임을 명시, setenv 특수문자 인용 주의사항 포함).

## 2026-11-17 — T27: README에 Tomcat 배포 가이드 작성 (worker)
- PLAN.md §11/TASKS.md T27. 레포 루트 `README.md`에 "## 운영 배포 (Tomcat, 단일 WAR) — T22~T27" 섹션 신설: (1) `mvn clean package` WAR 빌드(프론트 자동 포함, Node/npm 사전설치 불필요) → (2) `$CATALINA_HOME/webapps/ROOT.war`로 배치 — **ROOT.war가 권장이 아니라 필수 전제조건**임을 T25의 실측 근거(선행 슬래시 API 경로는 origin root로만 가므로 비-root 컨텍스트에서는 API 호출이 깨짐)와 함께 명시 → (3) `setenv.sh`/`setenv.bat` 예시(PLAN.md §11.6 스니펫 그대로) + 레포에 커밋하지 않는다는 점 + `+`/`=`/`%` 등 특수문자 인용 주의사항(셸 더블쿼트, 배치 `%%` escape와 `set "..."` 전체 쿼트) → (4) Tomcat 기동/재기동 방법과 기동 후 확인 포인트(index 200, `/api/health` 200, 토큰 없는 `/api/schedules` 401, 관리자 로그인, H2 fallback 경고 로그 체크).
- 레포 최상단 소개 문단에 "운영 배포는 T22~T27로 단일 WAR 방식으로 바뀌었지만 로컬 분리 실행은 유지된다"는 안내를 추가해 기존 "두 개의 독립 배포 단위" 문구와 혼동되지 않게 함.
- 기존 "다른 업체 재배포 체크리스트(T13)" 섹션에, 통합 WAR로 배포하는 경우 프론트엔드 섹션의 "업체별 `npm run build`" 안내가 적용되지 않고 같은 WAR를 그대로 재사용하면 된다는 안내를 추가(T25의 상대경로화 결과를 체크리스트에도 반영).
- 이 작업은 문서만 변경(코드 변경 없음). `mvn test` 123/123 재확인(회귀 없음, 당연히 영향 없었음을 재확인하는 차원).
- DoD 점검: README를 처음 보는 사람이 그대로 따라가면 WAR 빌드→Tomcat 배치(ROOT.war)→환경변수 설정(setenv)→기동→확인까지 재현 가능한 수준으로 작성됨(T26에서 실제로 이 순서 그대로 로컬 Tomcat에 배치해 실측 검증한 내용을 그대로 문서화한 것이므로 가이드 자체가 재현성 검증을 거침).
- T22~T27 전체 완료. 다음 라운드는 T28(future, 실행이력 테이블 — 이번 범위 제외, 별도 설계 라운드 필요).

## 2026-11-18 — TASKS.md 번호 정리: T29~T32(future) → T28~T31(future) (worker)
- PLAN.md §6/§7/§10과 TASKS.md T29~T31(대상DB 연결 테스트 기능, 커밋 8a75376)이 신설된 것을 확인. 작업 착수 전 TASKS.md를 다시 읽어보니 T27 다음 번호가 T28을 건너뛰고 바로 T29로 매겨져 있었음(신설 시점의 단순 번호 누락으로 보임) — 사용자 지시대로 내용은 그대로 두고 번호만 한 칸씩 당김: T29→T28(백엔드 연결테스트 API), T30→T29(프론트 버튼+뱃지), T31→T30(회귀테스트), future T32→T31(실행이력 테이블). 머리말 교정 문단과 future 섹션의 "착수 조건(T1~T31)" 문구도 같이 갱신. 레포 전체에서 T29~T32 문자열을 grep해 TASKS.md 외 다른 파일(PLAN.md, LOG.md, README.md)에 참조가 없음을 확인(LOG.md의 과거 T27 항목에 있는 "다음 라운드는 T28(future...)"는 2026-11-17 당시 — 이번 신설 전 — 기준으로 쓰인 역사적 기록이라 수정 대상 아님).
- 로컬 커밋(`27f374c`, push 안 함).

## 2026-11-18 — T28: 백엔드 — 대상 DB 연결 테스트 API 2개 (worker)
- PLAN.md §6(2026-11-18 신규)/TASKS.md T28. 공용 컴포넌트 `infra/db/TargetDbConnectionTester`를 신설해 두 엔드포인트가 로직을 공유하도록 구현: `POST /api/config/target-db/test-connection`(바디로 jdbcUrl/username/password를 평문으로 받아 저장 없이 테스트만), `POST /api/config/target-db/{id}/test-connection`(id로 저장된 설정을 `TargetDbConfigRepository.findById`로 조회 — 이미 `CredentialCipher`로 복호화된 평문을 반환하므로 별도 복호화 호출 불필요). 둘 다 `TargetDbConfigController`에 추가, 응답은 공용 `ConnectionTestResponse(success, message)`.
- `TargetDbConnectionTester.test()`: 커넥션 풀 없는 1회성 `DriverManager.getConnection` + `SELECT 1` + try-with-resources 즉시 close. `DriverManager.setLoginTimeout(5)`은 호출 전후로 이전 값을 저장/복원(JVM 전역 상태라 `StoredProcedureInvoker` 등 무관한 호출에 영향 주지 않게).
- **에러 메시지 안전처리**: `SQLException`을 그대로 클라이언트에 넘기지 않고 `sqlState`/메시지 키워드(로그인 실패/타임아웃/연결 불가)로 분류해 고정된 한국어 문구로만 응답. 원본 예외(스택트레이스 포함)는 서버 로그에만 남기되, 비밀번호 문자열이 로그 메시지에 그대로 찍히는 경우를 막기 위해 방어적 치환(`rawMessage.replace(password, "***")`)을 추가.
- **실측 검증 중 실제 버그 2건 발견(DoD의 "타임아웃이 실제로 적용되는지 측정해 확인" 요구가 바로 잡아낸 것들)**:
  1. **방어적 치환의 부작용**: 테스트에서 1글자짜리 비밀번호("p")로 실패를 유도했더니, 로그 메시지의 "port"/"properties" 같은 평범한 단어 안의 "p"까지 치환되어 "***ort"/"***ro***erties"로 깨짐 — 짧은 문자열이 메시지 치환의 의도치 않은 충돌을 일으킴을 실측으로 발견. `MIN_REDACTABLE_PASSWORD_LENGTH=4` 미만이면 치환을 건너뛰도록 수정(실제 비밀번호는 보통 이보다 길고, 짧은 가짜 값에 로그가 깨지는 쪽보다 가독성을 택함).
  2. **`DriverManager.setLoginTimeout`만으로는 타임아웃이 보장되지 않음**: 처음엔 RFC 5737 TEST-NET-3(203.0.113.1, 라우팅 불가 예약 주소)로 "타임아웃 측정" 테스트를 작성했는데, 실제로는 0.5초 안에 실패해서 타임아웃 적용 여부를 증명하지 못함(네트워크 환경에 따라 즉시 reject될 수 있어 검증력이 없는 테스트였음). 로컬 `ServerSocket`으로 "TCP 연결은 수락하지만 TDS 핸드셰이크에 응답하지 않는" 블랙홀 서버를 만들어 재시도했더니, 실제로 **20초(테스트 서버가 소켓을 닫을 때까지)** 를 꽉 채워서야 실패 — `setLoginTimeout`이 TCP connect 단계만 보장하고, 연결 수립 후 응답 없는 소켓 read 대기는 전혀 타임아웃이 걸려 있지 않았다는 뜻. mssql-jdbc의 커넥션 속성 `socketTimeout`(ms 단위, 개별 소켓 read마다 적용)을 `DriverManager.getConnection(url, Properties)` 방식으로 추가 설정해 이 간극을 메움. 수정 후 같은 블랙홀 테스트가 5초 안팎(4~15초 범위)에서 정확히 실패함을 재확인.
- `backend/src/test/java/com/scheduler/web/api/TargetDbConnectionTestControllerTest.java`: `AbstractRepositoryIntegrationTest`가 띄우는 실제 MSSQL testcontainer를 "대상DB"로 재사용해 (1) 저장 전 입력값으로 실제 연결 성공, (2) 저장된 설정으로 실제 연결 성공, (3) 잘못된 비밀번호 → 실패 + 응답에 평문 비밀번호 미노출, (4) 블랙홀 소켓으로 5초 전후 타임아웃 실측(위 버그 수정의 재현 테스트) 4건.
- `backend/src/test/java/com/scheduler/web/api/TargetDbConnectionTestAuthTest.java`: `AuthControllerTest`와 동일 패턴(`app.auth.disabled=false` 고정)으로 두 엔드포인트 모두 인증 없이 호출 시 401 확인 — `SecurityConfig` 변경 없이 기존 `/api/**` 보호 규칙에 자연히 포함됨을 실측 확인(별도 수정 불필요했음).
- 이 작업 전 로컬 환경에 Docker Desktop이 기동되어 있지 않아 testcontainers 기반 테스트 전체(기존 123개 포함)가 `NoClassDefFoundError`로 실패하는 것을 발견 → Docker Desktop을 기동(사용자 세션 내 기존 설치본 실행)하고 준비될 때까지 대기 후 재실행 — 이 환경 이슈는 이번 작업의 코드와 무관한 환경 상태였음을 투명하게 남김.
- `mvn test` 129/129 그린(기존 123 + 신규 6: 컨트롤러 4 + 인증 2).
- DoD 점검: 저장된 설정 연결 성공 시 `{success:true}` 확인, 존재하지 않는 호스트(블랙홀 소켓)로 5초 전후 `{success:false}` 응답을 실측(타임아웃 강제 확인 — 위 버그 수정 포함), 실패 응답 `message`에 입력했던 비밀번호가 포함되지 않음을 테스트로 확인, 인증 없이 호출 시 401 확인. 다음 작업: T29(프론트엔드 연결 테스트 버튼 + 상태 뱃지).

## 2026-11-18 — T29: 프론트엔드 — 연결 테스트 버튼 + 상태 뱃지 (worker)
- PLAN.md §7 item 6(2026-11-18 신규)/TASKS.md T29. `frontend/src/api/targetDbConfigs.ts`에 `testConnection(input)`/`testConnectionById(id)` 추가(공용 `ConnectionTestResult{success,message}` 타입, `frontend/src/api/types.ts`에 신설). `TargetDbConfigPage.tsx`: 생성/수정 폼에 "연결 테스트" 버튼 추가(클릭 시 현재 폼 입력값 그대로 `testConnection` 호출, 저장 없이 인라인으로 성공/실패 표시, 테스트 중엔 버튼 비활성화 + 스피너). 목록 테이블에 "연결 상태" 컬럼 추가: 행별 상태(React state `Record<number, {status, message}>`, 키 없음=미확인)를 "테스트" 버튼 클릭 시에만 `testConnectionById`로 갱신 — 페이지 로드 시 자동 호출 없음, 전역/영속 캐시 없음(새로고침하면 전부 미확인으로 리셋, PLAN.md §6의 "자동 주기 헬스체크는 범위 밖" 결정 그대로). `index.css`에 `status-unknown`(회색, 기존 `status-success`/`status-failure`와 같은 계열) + `connection-status`(flex 레이아웃) 클래스 추가.
- `TargetDbConfigPage.test.tsx` 신설(vitest + RTL, `fireEvent` 기반 — 이 레포에 `@testing-library/user-event`가 없어 기존 의존성만으로 작성): (1) 로드 시 모든 행이 "미확인"으로 시작하고 `testConnectionById`가 자동 호출되지 않음, (2) 한 행의 "테스트" 클릭이 그 행의 뱃지만 갱신하고 다른 행엔 영향 없음, (3) 실패 메시지가 해당 행에만 표시됨, (4) 생성 폼의 "연결 테스트"가 폼의 현재 입력값으로 `testConnection`을 호출하고 `create`는 호출하지 않음(저장 안 됨 확인) — 4건.
- **DoD의 "수동 확인" 요구 — 이 환경엔 브라우저 자동화 도구가 없음(T12/T20에서도 동일하게 기록된 제약, `ToolSearch`로 재확인)**: 기존 관례대로 (1) `tsc -b`/`npm run build` 에러 없음, (2) `npm test` 29/29 그린(기존 25 + 신규 4), (3) `npm run lint`에 신규 경고 없음(기존 3건 — ScheduleListPage/CredentialPage/TargetDbConfigPage의 `useEffect` 패턴 — 과 AuthContext 1건은 전부 이 작업 이전부터 있던 것), (4) **실제 백엔드(H2 fallback) + 임시로 띄운 실제 MSSQL 컨테이너(`scheduler-manual-verify-mssql`, 포트 14333) + 실제 로그인으로, UI가 수행할 정확한 API 시퀀스를 curl로 재현**: `test-connection`(미저장 입력) 성공/실패(잘못된 비밀번호, 메시지에 비밀번호 미노출) 확인 → `target_db_config` 생성 → `{id}/test-connection`(저장된 설정) 성공 확인 → 목록 조회 시 비밀번호 계속 마스킹됨 확인 → 인증 없이 두 엔드포인트 호출 시 401 확인. 응답 JSON이 `ConnectionTestResult` 타입과 정확히 일치함을 직접 대조. (5) `npm run dev`로 실제 Vite 개발서버를 띄우고 변환된 `TargetDbConfigPage.tsx`/`index.css`를 curl로 받아 컴포넌트가 쓰는 모든 `className`(`status-unknown`/`status-success`/`status-failure`/`connection-status` 등)이 CSS에 정확히 정의돼 있음을 대조(오타 없음).
- **환경 이슈 처리(투명하게 기록)**: 검증 시작 시 이전 세션에서 남겨진 백엔드 프로세스(PID 15836)가 로컬 H2 파일(`backend/data/scheduler-meta.mv.db`)을 잠그고 있어 새 인스턴스 기동이 실패 — 종료시켜 포트/파일을 회수. 이후 그 H2 파일에 이미 예전 관리자 계정이 들어있어(이번 세션에서 새로 지정한 `INITIAL_ADMIN_*`가 "테이블이 비어있을 때만 시드"라 적용되지 않음) 로그인이 거부됨 — 운영 메타DB가 아니라 로컬 전용 스크래치 데이터이므로, 별도의 새 H2 파일(`t29-verify-session`)을 가리키도록 `DB_URL`을 임시로 지정해 깨끗한 상태로 재기동. 검증 종료 후 백엔드/프론트 프로세스, 임시 MSSQL 컨테이너, 임시 H2 파일 전부 정리(해당 PID들 종료, `docker stop/rm`, 파일 삭제) — `git status` 깨끗함 확인.
- DoD 점검: 폼 "연결 테스트"가 저장 없이 인라인 성공/실패 표시(수동 확인), 목록의 "테스트"가 해당 행만 갱신(수동+자동 확인), 새로고침 시 전부 "미확인"으로 리셋(React state 설계상 자명 + 수동 확인). `npm test` 29/29, `tsc -b`/`npm run build`/`npm run lint` 전부 그린. 다음 작업: T30(연결 테스트 기능 회귀 확인 — 백엔드 타임아웃/마스킹 테스트는 T28에서 이미 작성됐으므로 중복 없이 재사용, 프론트 상태 전환 테스트는 위 T29 테스트로 커버됨을 재확인하는 선에서 마무리).

## 2026-11-18 — T30: 연결 테스트 기능 회귀 확인 (worker)
- PLAN.md §6/§7(2026-11-18 신규)/TASKS.md T30. T30의 DoD 항목 중 "백엔드: 연결 성공/실패(잘못된 자격증명)/타임아웃 3가지 + 비밀번호 미노출"은 T28에서 이미 `TargetDbConnectionTestControllerTest`(성공 2건: 미저장입력/저장된설정, 잘못된 비밀번호로 실패+미노출, 블랙홀 소켓으로 타임아웃 실측)로 전부 작성·실측 검증됨 — 중복 작성하지 않고 그대로 재사용.
- 다만 "프론트: 미확인→테스트중→성공/실패 상태 전환이 올바르게 렌더링되는지"는 T29의 테스트가 최종 상태(미확인/성공/실패)만 확인했을 뿐 **전환 자체(테스트 중 상태를 거쳐가는지)**는 검증하지 않았음을 재확인 → `TargetDbConfigPage.test.tsx`에 별도 `describe` 블록(T30) 2건 추가: 수동으로 resolve하는 Promise를 목으로 걸어 (1) 행의 "테스트" 클릭 → "미확인"이 사라지고 "테스트 중..."이 뜸 → resolve 후 "연결 성공"으로 바뀌고 "테스트 중"은 사라짐, (2) 폼의 "연결 테스트" 클릭 → "테스트 중..."이 뜸 → resolve 후 "연결 실패: {message}"로 바뀜 — 각 단계를 순서대로 `await waitFor`로 확인.
- 기존 `target_db_config` CRUD 테스트(T10 `TargetDbConfigControllerTest`) 회귀 확인: 백엔드 `mvn test` 전체 재실행에서 6개 전부 그대로 통과(이번 라운드 변경이 CRUD 경로에 영향 없음을 재확인).
- 최종 회귀: 백엔드 `mvn test` 129/129 그린(T28/T29 이후 변경 없음, 재확인 목적 재실행), 프론트 `npm test` 31/31 그린(29 + 신규 전환테스트 2), `tsc -b`/`npm run lint` 신규 경고 없음.
- DoD 점검: 백엔드 3가지 케이스 + 비밀번호 미노출(T28에서 검증, 재사용), 프론트 상태 전환 테스트(신규 2건) 통과, 기존 CRUD 테스트 회귀 없음, 전체 테스트 스위트 그린. **T28~T30 전체 완료** — PLAN.md §6/§7의 대상DB 연결 테스트(헬스체크) 기능이 백엔드 API → 프론트 UI → 회귀 테스트까지 모두 구현·검증됨. 다음 라운드는 T31(future, 실행이력 테이블 — 이번 범위 제외, 별도 설계 라운드 필요).

## 2026-11-18 — T28 후속 버그수정: `sanitize()`에 SSL/인증서 분류 추가 (사용자 직접 제보, worker)
- 사용자가 실제 대상DB(SQL Server)로 연결 테스트를 돌리다가 `PKIX path building failed`/SSL 관련 예외(encrypt=true인데 서버에 유효한 SSL 인증서가 없는, mssql-jdbc에서 매우 흔한 케이스)를 직접 겪음 — `TargetDbConnectionTester.sanitize()`에 login failed/timeout/tcp-ip·connect·host 3가지 분류만 있고 이 케이스가 전혀 안 걸려서 뭉뚱그려진 "데이터베이스 연결에 실패했습니다."로 떨어져 원인 파악에 서버 로그를 직접 열어봐야 했던 것이 사용자 제보로 확인됨.
- `sanitize()`에 `pkix`/`sslhandshake`/`ssl`/`certificate`/`encrypt` 키워드 분류를 추가: "서버와 SSL 암호화 연결을 수립할 수 없습니다. 대상 서버에 유효한 SSL 인증서가 없다면, JDBC URL 끝에 ;encrypt=false;trustServerCertificate=true; 를 추가해 보세요." — 구체적이고 바로 실행 가능한 해결책(URL 파라미터 추가)을 명시.
- **키워드 충돌 검토(사용자가 직접 지시한 확인 사항)**: mssql-jdbc의 실제 SSL 실패 메시지는 "...could not establish a secure **connection** to SQL Server by using Secure Sockets Layer (SSL) encryption..." 형태라 "connection" 안에 "connect"가 포함됨 — 기존 tcp-ip/connect/host 분류보다 **먼저** 체크하지 않으면 SSL 문제가 "서버에 연결할 수 없습니다"(네트워크 문제)로 오분류될 뻔했다. 순서를 login failed → **SSL/인증서(신규, 먼저)** → timeout → tcp-ip/connect/host로 배치해 해결, 코드에 이 충돌 이유를 주석으로 남김.
- **실측 재현(크래프트된 메시지가 아니라 진짜 예외)**: `AbstractRepositoryIntegrationTest`의 MSSQLServerContainer로 재현 시도 — 처음엔 `;trustServerCertificate=true;`만 제거했으나 **연결이 그냥 성공**해버림(예상 밖의 결과). `MSSQLServerContainer.getJdbcUrl()` 바이트코드를 직접 디컴파일(`javap -p -c`)해 원인 확인: Testcontainers가 URL에 `encrypt` 파라미터가 명시돼 있지 않으면 자동으로 `encrypt=false`를 추가해버려서 TLS 협상 자체가 일어나지 않았던 것. `;encrypt=true;`를 명시적으로 추가(trustServerCertificate 없이)해 재시도하자 실제 `com.microsoft.sqlserver.jdbc.SQLServerException`(원인: `javax.net.ssl.SSLHandshakeException: PKIX path building failed: ... SunCertPathBuilderException: unable to find valid certification path to requested target`)이 그대로 재현됨 — 진짜 드라이버 예외로 신규 분류를 검증.
- `TargetDbConnectionTestControllerTest`에 테스트 2건 추가: (1) `sslCertificateFailureGetsAnSslSpecificMessageNotTheGenericConnectFailureMessage` — 위 실측 재현으로 응답에 "SSL"과 `encrypt=false;trustServerCertificate=true;` 해결책이 포함되고, **"서버에 연결할 수 없습니다"(연결불가 카테고리)로 잘못 분류되지 않았음**을 명시적으로 확인(이번 수정의 핵심 — 순서 충돌 방지가 실제로 동작함을 증명), (2) `connectionRefusedStillMapsToTheGenericUnreachableCategory` — 닫힌 로컬 포트로 기존 "연결불가" 카테고리가 이번 변경에도 깨지지 않음을 회귀 확인. 기존 `wrongPasswordFailsWithoutLeakingItInTheResponse`(로그인실패)·`unreachableHostFailsWithinEnforcedTimeout`(타임아웃)도 재실행해 회귀 없음 확인.
- 전체 회귀: `mvn test` 131/131 그린(기존 129 + 신규 2).
- DoD 점검: SSL/PKIX 케이스가 실제 드라이버 예외로 재현되어 새 분류로 정확히 매칭됨, 연결불가 카테고리와의 키워드 충돌이 순서 조정으로 해결됨을 테스트로 증명, 기존 3가지 분류(로그인실패/타임아웃/연결불가) 전부 회귀 없음.
