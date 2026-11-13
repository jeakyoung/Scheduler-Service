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
