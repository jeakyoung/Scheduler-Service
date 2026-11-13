# RESEARCH.md — 범용 스케줄링 서비스: 레거시 실행 흐름 포팅 조사

> 범위: 레거시(`C:\Users\USER\Desktop\scheduler-test\scheduler-test`, 읽기 전용 참고)의 **실행 흐름은 그대로 포팅**하고, 바뀌는 부분은 딱 두 가지만 조사한다.
> (1) 하드코딩된 3개 Task 등록 → DB 기반 동적 스케줄 등록/해제/변경
> (2) `Config.java`의 비밀값 하드코딩 → 외부 설정 주입
>
> 조사 과정에서 실제로 읽은 레거시 파일: `SchedulerContextListener.java`, `DaySchedulerTask.java`, `HourSchedulerTask.java`, `PurchaseReqSchedulerTask.java`, `Config.java`.

## 0. 레거시 실행 흐름 요약 (포팅 대상, 그대로 유지)

```
SchedulerContextListener.contextInitialized()
  └─ ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3)
  └─ 작업별 "다음 실행까지 남은 초(initialDelay)"를 전용 메서드로 계산
       - calculateInitialDelayForNextMonday9AM()   (DaySchedulerTask, period=7일)
       - calculateInitialDelayFor30Min()            (HourSchedulerTask, period=30분)
       - calculateInitialDelayForDaily830AM()        (PurchaseReqSchedulerTask, period=24시간)
  └─ scheduler.scheduleAtFixedRate(task, initialDelay, period, SECONDS) × 3

각 Task(Runnable).run():
  1. (Hour/PurchaseReq만) 주말 체크 — 토/일이면 즉시 return
  2. isTaskRunning(static boolean, synchronized) 로 중복 실행 차단
  3. executeTask1(): Class.forName(DRIVER) → DriverManager.getConnection(Config.getDbUrl/UserName/Password)
                      → Statement.executeQuery("EXEC SP_XXX") → ResultSet을 행 단위로 수집
  4. executeTask2(): 수집된 행을 기반으로 JSON 문자열(바디) 구성
                      → HttpURLConnection POST to Config.getFcmUrl()
                      → 헤더: SECURITY_CODE=Config.getFcmSecurityCode(), X-Api-Key=Config.getFcmApiKey()
  5. finally에서 isTaskRunning = false
```

세 Task의 차이는 사실상 **① 호출하는 SP명, ② FCM 요청 바디에 들어가는 필드 구성(title/body/division/payload 유무, 단건 vs 그룹 전송), ③ 등록 주기(언제/얼마나 자주)** 뿐이다. 즉 "Task 클래스 자체"가 아니라 **그 3개 축의 파라미터**만 바뀌면 하나의 범용 Job으로 합칠 수 있다. 이 조사는 그 전제 위에서 진행한다.

---

## (a) 동적 스케줄 등록/해제 — ScheduledFuture 자체관리 vs Quartz

### 공통 전제: 무엇을 "스케줄 정의"로 뺄 것인가

레거시의 하드코딩을 걷어내면 DB에 저장해야 할 스케줄 정의 필드는 최소한 다음과 같다 (구현 아님, 설계 근거용 나열):

- 식별자, 활성화 여부(enabled)
- 실행 주기 표현 (레거시 3종 패턴 — 매주 월요일 9시 / 평일 매 정각·30분 / 평일 8시30분 — 을 일반화할 "주기 표현식")
- 평일/주말 필터 여부 (레거시는 Hour/PurchaseReq에서 Task 내부 코드로 체크하던 것 — 일반화하면 주기 표현식 자체가 요일을 표현할 수 있어야 이 별도 필터가 필요 없어짐)
- 호출할 SP명 (레거시는 `SP_WORK_LIMIT_LIST`, `SP_PURCHASE_REQ_NOTICE` 등 Task 클래스에 문자열 리터럴로 박혀 있던 것)
- FCM 요청 바디 템플릿/필드 매핑 (title/body 텍스트 소스, division·payload 포함 여부, 그룹핑 방식: 레거시 DaySchedulerTask는 문서 타이틀별로 묶어 1회 전송, Hour/PurchaseReq는 수신자별 개별 전송 — 이 "그룹핑 모드" 차이도 파라미터화 대상)

이 정의를 DB/저장소에 두고, 엔진이 그걸 읽어 런타임에 add/remove/update 하는 것이 요구사항 (1)의 핵심이다. 아래 두 방식은 "그 add/remove/update를 무엇으로 구현하느냐"의 비교다.

### 옵션 1 — `ScheduledExecutorService` + `Map<scheduleId, ScheduledFuture>` 자체관리 (레거시 메커니즘 직접 포팅)

레거시의 `scheduler.scheduleAtFixedRate(...)` 호출 자체는 그대로 쓰되, 3개를 리터럴로 호출하는 대신:

- 기동 시 DB에서 `enabled=true`인 스케줄 행을 모두 읽어 등록하고, 각 등록에서 반환되는 `ScheduledFuture`를 `scheduleId`로 매핑해 보관.
- 관리자 웹에서 Create/Update/Delete 요청이 오면 해당 `ScheduledFuture.cancel()` 후 재계산해서 다시 등록(Update는 cancel+재등록으로 처리).
- "다음 실행까지 남은 초" 계산은 레거시의 `calculateInitialDelayForNextMonday9AM()` 류 3종 전용 메서드 대신, 범용 cron 표현식(예: `0 0 9 ? * MON`)을 받아 다음 실행 시각을 계산하는 범용 로직이 필요하다. 레거시 방식(고정 period로 `scheduleAtFixedRate`)은 "매주", "매 30분", "매일" 같은 **등간격** 패턴에는 맞지만, 임의 cron(예: "매월 마지막 영업일")처럼 **간격이 일정하지 않은** 패턴엔 맞지 않으므로, 완전히 일반화하려면 매 실행 후 다음 발화 시각을 다시 계산해 **1회성 `schedule()`을 반복 재등록**하는 방식으로 바꿔야 한다. 이는 레거시의 "실행 메커니즘"은 유지하되(같은 `ScheduledExecutorService`, 같은 cancel 가능한 핸들 보관 패턴) 고정 `period` 가정만 깨는 변경이다.
- 레거시의 `isTaskRunning`(static boolean, 단일 Task 클래스 전용)은 범용 Job 하나가 여러 스케줄을 실행하므로 `scheduleId`별 가드(`ConcurrentHashMap<scheduleId, AtomicBoolean>`)로 일반화.

**장점**
- 레거시 코드 구조·사고방식을 거의 그대로 재사용 — 추가 외부 라이브러리 의존 없음(cron 다음-발화시각 계산용 소규모 라이브러리 정도만 필요).
- 스레드풀 크기(`newScheduledThreadPool(3)`) 등 레거시가 이미 내린 결정을 그대로 승계 가능.

**단점 (직접 구현해야 하는 것들)**
- **재시작 시 복구**: 레거시는 배포=등록이라 이 문제가 없었다. 동적 저장소로 바뀌면 프로세스 재시작 시 DB에서 enabled 스케줄을 다시 읽어 재등록하는 로직을 직접 만들어야 함.
- **다중 인스턴스 중복 실행**: 레거시는 단일 WAR/단일 JVM 전제라 `isTaskRunning`만으로 충분했다. 인스턴스를 2개 이상 띄우면 양쪽 다 같은 스케줄을 등록해 SP 호출·FCM 발송이 중복된다 — DB 레벨 분산 락(`SELECT ... FOR UPDATE SKIP LOCKED` 또는 ShedLock류)을 직접 추가해야 cross-process 중복을 막을 수 있음. 레거시의 `isTaskRunning`이 풀던 문제를 프로세스 경계 밖까지 일반화하는 비용.
- **Misfire 정책 없음**: 프로세스가 다운돼 있던 동안 지나간 실행 시각은 레거시처럼 그냥 누락됨(이 도메인에선 레거시도 원래 이 동작이었으므로 허용 가능하지만, 명시적으로 결정해 둘 필요가 있음).
- 관리자 UI에 "다음 실행 예정 시각" 같은 상태를 보여주려면 이 또한 직접 추적해야 함.

### 옵션 2 — Quartz Scheduler (JDBC JobStore)

레거시의 3개 Task 클래스를 **하나의 범용 Quartz Job 클래스**로 합치고, SP명/바디템플릿/그룹핑모드 등은 `JobDataMap`(스케줄 DB 행에서 로드)으로 주입한다. 등록/해제/변경은 `Scheduler.scheduleJob/rescheduleJob/deleteJob` API로 요구사항 (1)의 Create/Update/Delete에 1:1로 대응된다.

**장점**
- `CronTrigger`가 레거시의 3종 전용 "다음 실행 초 계산" 메서드를 하나의 표준 cron 표현식으로 완전히 대체 — 관리자가 임의 주기를 입력해도 추가 코드 없이 동작.
- JDBC JobStore(`QRTZ_*` 테이블)가 스케줄 상태를 DB에 영속화하므로 **재시작 시 자동 복구**가 기본 제공.
- JDBC JobStore의 행 단위 락킹이 **다중 인스턴스에서도 동일 트리거가 중복 실행되지 않도록** 보장 — 옵션 1에서 직접 만들어야 했던 분산 락 문제가 해결됨. 레거시의 단일 프로세스용 `isTaskRunning`이 하던 역할을, 클러스터 범위로 공식 지원.
- `@DisallowConcurrentExecution` 애노테이션이 레거시 `isTaskRunning` 플래그와 동일한 "같은 Job 중복 실행 방지"를 선언적으로 제공.
- Misfire 정책(즉시실행/스킵 등)을 트리거 단위로 명시적으로 고를 수 있음.

**단점**
- `QRTZ_*` 스키마 운영/마이그레이션이라는 새 의존성이 생김.
- 팀이 Quartz API(스케줄러 리스너, JobDataMap 직렬화 제약 등)를 새로 익혀야 함.
- 이 프로젝트 규모(Job 종류 자체는 적음)에 비해 다소 무겁다는 인상을 줄 수 있음.

### 비교 요약

| 항목 | 옵션1: ScheduledFuture 자체관리 | 옵션2: Quartz(JDBC JobStore) |
|---|---|---|
| 레거시 실행 메커니즘 재사용도 | 매우 높음 (거의 그대로) | 중간 (실행 로직은 포팅, 등록 메커니즘은 교체) |
| 임의 cron 주기 지원 | 직접 구현 필요 | 기본 제공 (CronTrigger) |
| 재시작 후 자동 복구 | 직접 구현 필요 | 기본 제공 |
| 다중 인스턴스 중복실행 방지 | 직접 구현 필요 (분산 락) | 기본 제공 (JobStore 락킹) |
| 신규 의존성/운영 부담 | 거의 없음 | QRTZ 스키마 운영 필요 |
| 중복실행 가드(단일 프로세스) | `isTaskRunning` 그대로 일반화 | `@DisallowConcurrentExecution` |

### 추천

**Quartz(JDBC JobStore)를 추천한다.** 이유: 요구사항이 "관리자가 임의 주기를 UI에서 등록"이라는 시점에, 옵션 1이 결국 직접 만들어야 하는 두 가지(① 임의 cron 다음-발화시각 계산, ② 재시작/다중인스턴스 환경에서의 중복실행 방지)가 바로 이 프로젝트의 핵심 일반화 지점이다. Quartz는 이 둘을 검증된 방식으로 기본 제공하면서도, **SP 호출 → 바디 구성 → FCM POST라는 레거시의 실질적 실행 로직은 Job 클래스 본문에 거의 그대로 옮겨 쓸 수 있다** — 즉 "실행 흐름을 포팅한다"는 원칙은 깨지지 않고, 그 실행 흐름을 "언제 실행할지"로부터 분리해 주는 틀만 Quartz가 맡는 구조다.

다만 다음 조건이 모두 맞는다면 옵션 1도 정당한 선택이다: (i) 운영 인스턴스가 항상 1개로 확정, (ii) 주기 종류가 레거시의 3패턴 수준(요일/시각 조합)에서 크게 늘지 않을 것 — 이 경우 Quartz 도입 비용이 얻는 이득보다 커질 수 있다. 이 전제가 맞는지는 설계 확정 전 팀/기획과 확인이 필요하다.

### 레거시 → 신규 매핑표 (포팅 지점 명시)

| 레거시 | 신규 (Quartz 기준) |
|---|---|
| `SchedulerContextListener`의 3개 `scheduleAtFixedRate` 리터럴 호출 | 기동 시 DB의 enabled 스케줄 행 전체를 순회하며 `scheduler.scheduleJob(...)` 등록 + 관리자 CRUD API가 호출하는 `scheduleJob/rescheduleJob/deleteJob` |
| `DaySchedulerTask`/`HourSchedulerTask`/`PurchaseReqSchedulerTask` (3개 클래스, SP명·주기·바디구성이 클래스에 내재) | 1개의 범용 Job 클래스. SP명/주기(cron)/바디 템플릿/그룹핑모드는 `JobDataMap`(스케줄 DB 행)에서 주입 |
| `calculateInitialDelayForNextMonday9AM()` 등 3종 전용 메서드 | `CronTrigger` (표준 cron 표현식) |
| `isTaskRunning` static boolean | `@DisallowConcurrentExecution` (단일 인스턴스) + JDBC JobStore 락킹 (다중 인스턴스) |
| `executeTask1()`: `Class.forName` + `DriverManager.getConnection(Config.xxx)` + `EXEC SP_XXX` | 로직 그대로 유지, SP명은 리터럴이 아니라 JobDataMap 파라미터, DB 접속정보는 (b)의 외부 설정에서 획득 |
| `executeTask2()`: 문자열 결합 JSON + `HttpURLConnection` POST + `SECURITY_CODE`/`X-Api-Key` 헤더 | 로직 그대로 유지(동일 헤더·엔드포인트 개념), 바디에 들어가는 필드값/그룹핑 방식만 스케줄 파라미터로 치환 |

---

## (b) `Config.java` 비밀값 하드코딩 → 외부 설정 주입

### 문제 재확인

`Config.java`는 운영/개발 두 세트의 DB·FCM 값을 **자바 상수 + 주석 스왑**으로 관리한다. 즉 "환경을 바꾼다"는 행위가 곧 "코드를 고치고 재컴파일한다"는 행위와 같다. 이걸 깨는 것이 요구사항 (2)다.

### 옵션 1 — 환경변수

`Config.getDbUrl()` 같은 getter의 반환값을 상수 대신 `System.getenv("DB_URL")`(또는 프레임워크의 `${DB_URL}` 플레이스홀더)로 바꾸는 가장 단순한 방법. 컨테이너/오케스트레이터(k8s Secret → env var 주입 등)와 잘 맞고 추가 인프라가 필요 없다.

- 장점: 구현 비용 최소, 비밀값이 소스/이미지에 전혀 남지 않음.
- 단점: 값을 바꾸려면 프로세스 재시작이 필요 — "실행 중 변경"은 안 됨. 그리고 **프로세스당 한 세트의 값**이라, 레거시가 암묵적으로 가정하던 "배포 1건 = 회사/환경 1건" 모델 그대로는 되지만, 하나의 스케줄러 프로세스가 여러 테넌트(여러 회사 DB·여러 FCM 키)의 스케줄을 동시에 돌려야 하는 "범용성" 요구에는 그대로 확장하기 어렵다(테넌트별 env var 세트를 접두사로 구분해야 하는데 테넌트 수가 늘면 금방 한계).

### 옵션 2 — 외부 설정 파일 (환경별 분리, 배포 시 마운트)

`application-{profile}.yml` 류, 혹은 소스 트리 밖에 두고 배포 시 마운트하는 설정 파일. 참고로 같은 `C:\Github` 아래의 별개 프로젝트인 `foodlink-service`(`Scheduler-Service`와 상하위 관계는 아님, 단순 인접 레포)가 실제로 이 방식을 쓰는 사례로 확인됨(`F1Soft.Starmap.Service/appsettings.json`이 `.gitignore`에 등록되어 있고, `Common/Env/TickerMiddleware.cs`/`SecurityCodeMapper.cs`가 헤더 기반 티커/환경 조회를 구현). 구조화된 키-값을 다루기엔 env var보다 편하지만, **재시작 없이 바뀌지 않는다는 한계와 "프로세스당 한 세트"라는 한계는 옵션 1과 동일**하다.

### 옵션 3 — 관리자 웹 설정화면 + DB 암호화 저장

DB 접속정보·FCM url/securityCode/apiKey를 **스케줄러 자체 관리용 DB 테이블**에 테넌트별/회사별 행으로 저장하고, (a)에서 만들 관리자 웹 UI(스케줄 CRUD와 같은 화면)에서 등록/수정/삭제하도록 한다. 앱은 이 값을 메모리에 캐시해 두고 저장 시 캐시를 무효화해, **재시작/재배포 없이** 즉시 반영한다.

- 이는 `Config.java`의 6개 상수를 "컴파일타임 상수"에서 "런타임에 UI로 편집 가능한 암호화된 행"으로 바꾸는 직접적인 일반화이며, 한 프로세스가 여러 테넌트의 설정을 동시에 들고 있을 수 있게 해 준다 — 레거시의 "배포=회사" 가정을 깨는 유일한 옵션.
- 단, DB 비밀번호/API 키 같은 민감 컬럼은 **저장 시 암호화**해야 한다(평문 테이블 저장은 하드코딩보다 나쁠 수 있음). 이때 암호화 키 자체는 다시 "하드코딩할 수 없는 비밀값"이 되는데, 이 범위를 **딱 1개의 마스터 키**로 좁혀서 옵션 1(환경변수)로 주입하면 "N개의 테넌트 비밀값을 env var로 관리"하는 문제를 "1개의 마스터 키를 env var로 관리"하는 문제로 축소할 수 있다.
- 장점: 실행 중 변경 가능, 다중 테넌트 네이티브 지원, 신규 테넌트 온보딩이 배포 없이 가능.
- 단점: 암호화/마스킹/접근권한(누가 비밀값을 볼 수 있는지) 설계가 추가로 필요 — UI에 평문 노출하지 않고 마지막 몇 자리만 보여주는 등의 처리가 필요함.

### 옵션 4 — 외부 시크릿 매니저 (Vault/AWS Secrets Manager/Azure Key Vault 등)

로테이션, 접근 감사(audit), 단기 자격증명 등 가장 강력하지만 별도 인프라 운영 부담이 생긴다. 이 프로젝트 규모에서는 **옵션 3의 마스터 키 또는 엔진 자체 메타DB 접속정보**(부트스트랩 비밀값)를 보관하는 용도로 쓰는 것이 가장 비용 대비 효율이 좋고, 테넌트별 N개 비밀값 자체를 여기 넣는 것은 과한 설계로 판단된다(그 값들은 어차피 UI로 CRUD가 되어야 하므로 옵션 3의 DB 테이블이 더 적합).

### 추천: 2계층 구조

1. **부트스트랩 비밀값** (스케줄러 엔진 자체의 메타DB 접속정보, 마스터 암호화 키) → **환경변수**로 주입 (운영 환경에서는 그 값 자체를 Vault/Secrets Manager에서 가져오도록 확장 가능). 이 계층은 "앱이 뜨기 위해 최소한으로 필요한" 값만 담아 범위를 최소화한다.
2. **테넌트/스케줄별 운영 비밀값** (레거시 `Config.java`의 DB url/username/password, FCM url/securityCode/apiKey에 해당) → **관리자 웹 설정화면에서 CRUD, DB에 암호화 저장, 메모리 캐시+저장 시 무효화**로 재시작 없이 반영.

이 구조는 레거시의 6개 상수를 "코드 수정 없이 주입 가능"하게 만들면서도, 레거시가 암묵적으로 전제하던 "1배포=1회사"를 넘어 "1프로세스=N테넌트"로 일반화하는 요구사항(2)을 동시에 만족시킨다. 또한 (a)에서 만드는 관리자 UI/API를 그대로 재사용할 수 있어 별도 화면이 필요 없다.

---

## (c) 추천 기술 스택

레거시는 Spring 없는 순수 서블릿(Tomcat WAR) + 원시 JDBC(`DriverManager`) + SQL Server 조합이다. (a)에서 Quartz(JVM 생태계 라이브러리) 도입을 추천했고, SP 호출 로직을 거의 그대로 포팅하는 것이 목표이므로 **JVM 스택을 유지하는 것이 가장 리스크가 낮다** — 언어 전환으로 인한 재작성 비용/리스크가 없고, 팀이 이미 Java/JDBC/SQL Server에 익숙하다.

- **언어/런타임: Java 17 또는 21.** LTS이며 Quartz, Spring Boot 최신 버전과 호환.
- **프레임워크: Spring Boot 3.x.**
  - `spring-boot-starter-quartz`로 (a)의 Quartz 통합이 바로 지원됨(스케줄러 빈 자동 구성, JobDataMap 연동).
  - Spring MVC로 관리자 웹 UI(또는 최소한 REST API, 프론트는 가벼운 SPA나 서버사이드 템플릿 선택 가능)와 (b)의 테넌트 설정 CRUD API를 같은 애플리케이션에서 구현.
  - `${ENV_VAR}` 플레이스홀더, `@ConfigurationProperties`, Spring Profile이 (b)의 환경변수/파일 계층을 프레임워크 차원에서 바로 지원.
  - Spring Security로 관리자 화면 인증을 얹기 용이(레거시엔 이런 보호가 없었다는 점도 개선 지점).
  - 레거시의 `Statement`/`ResultSet` 수동 매핑 스타일은 Spring Data JDBC 혹은 `JdbcTemplate`로 거의 1:1 포팅 가능 — 과도한 ORM 추상화 없이 SP 호출 패턴을 유지할 수 있음.
- **엔진 메타DB (스케줄 정의/테넌트 설정/실행이력 저장용): SQL Server.** Quartz JDBC JobStore는 SQL Server를 공식 지원하며, 팀이 이미 SQL Server 운영 경험(레거시의 iPlusERP 연동)이 있어 새 DB 엔진을 추가로 학습/운영할 필요가 없다. 참고로 이 메타DB는 "각 스케줄이 호출하는 대상 업무 DB"(과거 `Config.getDbUrl()`이 가리키던 곳, 테넌트마다 다를 수 있음)와는 완전히 분리된 개념이므로, 대상 업무 DB가 SQL Server가 아니어도(예: 다른 테넌트가 PostgreSQL을 쓰더라도) 무방하다.
- **빌드도구: Maven.** 레거시가 전형적 서블릿/WAR + IDE 플러그인(스마트토캣) 구조였고, Spring Boot 레퍼런스 자료 다수가 Maven 기준이라 온보딩 마찰이 적다. Gradle도 기술적으로 문제없으나 이 프로젝트 규모에서 뚜렷한 이점이 없다면 Maven으로 충분하다는 판단.
- **배포 형태: Spring Boot 실행 가능 jar(내장 톰캣) 권장**, 필요 시 WAR로 패키징해 기존 운영 중인 외부 Tomcat에도 배포 가능(Spring Boot가 양쪽 다 지원). 내장 서버 방식은 컨테이너화·CI/CD·(a)에서 Quartz JDBC JobStore 클러스터링과 맞물린 다중 인스턴스 수평 확장에 더 유리하다.

---

## 결론 요약

- **(a)** Quartz + JDBC JobStore로 레거시의 3개 하드코딴 Task를 1개의 범용 Job + DB 기반 스케줄 정의로 대체. SP 호출/FCM POST 로직(§0)은 Job 본문에 그대로 포팅. 조건이 맞으면 ScheduledFuture 자체관리도 대안으로 유효(위 조건 참고).
- **(b)** 부트스트랩 비밀값(엔진 메타DB, 마스터 키)은 환경변수, 테넌트/스케줄별 운영 비밀값(옛 `Config.java`의 6개 상수에 해당)은 관리자 웹 화면에서 CRUD + DB 암호화 저장.
- **(c)** Java 17/21 + Spring Boot 3.x + Quartz + SQL Server(엔진 메타DB) + Maven + 내장 톰캣 배포.
