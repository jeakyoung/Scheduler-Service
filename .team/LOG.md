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
