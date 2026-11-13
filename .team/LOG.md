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
