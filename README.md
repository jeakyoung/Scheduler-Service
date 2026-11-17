# scheduler-service

범용 스케줄링 서비스. 스케줄 정의(대상 SP, cron, 다운스트림 동작)를 DB에 저장하고 Quartz로 실행한다.
설계 배경/결정 사항은 `.team/PLAN.md`, 작업 분해는 `.team/TASKS.md` 참고.

저장소는 두 개의 독립 배포 단위로 나뉜다:
- `backend/` — Spring Boot REST API (화면 렌더링 없음)
- `frontend/` — React SPA (백엔드와 별도로 빌드/배포, T2에서 스캐폴딩)

**단, 운영 배포는 둘을 하나의 WAR로 합쳐 외부 Tomcat에 올리는 방식으로 바뀌었다(PLAN.md §11, T22~T27)** — 실제 쓸 수 있는 배포 인프라가 외부 Tomcat뿐이라서다. 로컬 개발에서 백엔드/프론트를 따로 띄우는 방식(`mvn spring-boot:run` 8080 + `npm run dev` 5173)은 그대로 유지되며, 아래 "로컬 개발"과 "운영 배포 (Tomcat, 단일 WAR)" 섹션은 서로 다른 워크플로다. 혼동하지 말 것.

## 백엔드 로컬 기동에 필요한 환경변수

| 환경변수 | 설명 |
|---|---|
| `DB_URL` | 메타DB(스케줄 정의/설정/Quartz 상태) JDBC URL. 예: `jdbc:sqlserver://localhost;databaseName=scheduler_meta;encrypt=false;trustServerCertificate=true;` — **생략하면 로컬 개발용 H2 파일 DB(`./data/scheduler-meta`)로 자동 fallback**해서 Docker/SQL Server 없이 `mvn spring-boot:run`만으로 기동된다(기동 로그에 WARN 찍힘). 이 fallback은 메타DB에만 적용되고, `target_db_config`가 가리키는 대상 업무 DB(SP가 실제 존재하는 업체 DB)는 항상 실제 SQL Server이며 영향받지 않는다. **실제 배포 시에는 반드시 SQL Server `DB_URL`을 지정할 것** |
| `DB_USERNAME` | 메타DB 접속 계정 |
| `DB_PASSWORD` | 메타DB 접속 비밀번호 |
| `MASTER_ENCRYPTION_KEY` | `target_db_config`/`credential` 암호화용 AES-256 마스터키 (base64). 예: `openssl rand -base64 32` |
| `JWT_SIGNING_KEY` | 로그인 토큰(JWT, HS256) 서명키, AES-256 마스터키와 별도 값 사용. `openssl rand -base64 32` |
| `JWT_EXPIRATION_MINUTES` | (선택) 토큰 만료(분). 기본 720(12시간) |
| `INITIAL_ADMIN_USERNAME` | (최초 기동 시 관리자 계정이 없을 때만 사용) 시드할 관리자 계정명 |
| `INITIAL_ADMIN_PASSWORD` | (최초 기동 시) 시드할 관리자 비밀번호 |
| `CORS_ALLOWED_ORIGIN` | 프론트엔드(React SPA) 배포 오리진, 쉼표로 복수 허용. 로컬 기본값 `http://localhost:5173` |
| `SERVER_PORT` | (선택) 기본 8080 |
| `AUTH_DISABLED` | **로컬 개발 전용, 운영 환경에서는 절대 설정하지 말 것.** `true`로 설정하면 모든 `/api/**`가 토큰 없이 호출 가능해진다(로그인 생략). 기본값 `false`(미설정 시 평소처럼 인증 필요) — 켜져 있으면 기동 로그에 WARN이 찍힌다. **서버 쪽 토큰 검사만 끄는 것**이라, 프론트(React SPA)의 클라이언트 사이드 라우트 가드는 별개로 계속 로그인을 요구함 — 프론트까지 로그인 없이 쓰려면 `frontend/.env.local`의 `VITE_AUTH_DISABLED`도 같이 켜야 함(아래 프론트엔드 섹션 참고) |

위 값들은 모두 `backend/src/main/resources/application.yml`에서 `${VAR}` 형태로만 참조되며, 소스에 실제 값은 들어있지 않다.

### 빌드/실행

```bash
cd backend
mvn clean package
mvn spring-boot:run
```

### Spring Boot 버전 선정 근거

`spring-boot-starter-parent` 3.5.16 — 2026-11-13 기준 Maven Central 메타데이터로 독립 확인한 3.x 세대 최신 패치(3.5.x가 3.x 중 최신 minor, 업스트림은 이후 4.x로 이동). PLAN.md §1이 3.x 세대를 명시적으로 고정하고 있어 4.x는 채택하지 않았다. Java 17 베이스라인이 이 프로젝트의 `java.version`과 일치. (타 프로젝트의 pom.xml을 복사하지 않고 직접 확인함 — 과거 ReeL-Trip의 pom.xml과 우연히 동일 버전(3.3.5)을 썼던 실수를 교정.)

## 프론트엔드 (`frontend/`)

Vite + React + TypeScript SPA. 백엔드와 별도로 빌드/배포된다. 백엔드 REST API 호출은 모두 `frontend/src/api/client.ts`를 거친다.

| 환경변수 (`.env.local`, gitignore) | 설명 |
|---|---|
| `VITE_API_BASE_URL` | 백엔드 API base URL. 로컬 기본값 `http://localhost:8080`. 실제 값은 `.env.example`을 복사한 `.env.local`에만 둔다 |
| `VITE_AUTH_DISABLED` | **로컬 개발 전용, 운영 환경에서는 절대 설정하지 말 것.** `true`로 설정하면 실제 로그인 호출 없이 `AuthContext`의 `user`를 더미 값(`{username: "local-dev", role: "ADMIN"}`)으로 즉시 채워서, `ProtectedRoute`가 `/login`으로 리다이렉트하지 않게 한다. 기본값 `false`(미설정 시 평소처럼 로그인 필요). **백엔드의 `AUTH_DISABLED`와 반드시 같이 켜야 의미가 있다** — 둘은 서로 독립된 게이트라, 하나만 켜면 다른 쪽에서 막힌다(백엔드만 켜면 프론트 라우트 가드가 여전히 로그인 화면으로 보내고, 프론트만 켜면 백엔드가 여전히 토큰을 검사해서 API 호출이 401로 실패한다). 켜져 있으면 네비게이션 바에 "로컬 인증 비활성화" 배지가 표시됨(백엔드의 기동 WARN 로그와 같은 목적 — 운영에서 실수로 켜졌을 때 바로 눈에 띄도록) |

### 빌드/실행

```bash
cd frontend
cp .env.example .env.local   # 최초 1회
npm install
npm run dev     # 로컬 개발 서버 (기본 http://localhost:5173)
npm run build   # 프로덕션 빌드
```

### 인증 (T11) — 토큰(JWT) 기반, 세션 쿠키 아님

React SPA가 백엔드와 다른 오리진이라, 세션 쿠키 방식 대신 **stateless JWT**로 결정(2026-11-13, 사용자 직접 지시) — `SameSite=None+Secure+CORS Allow-Credentials+CSRF` 방어를 전부 구성해야 하는 세션 쿠키 방식은 단일 관리자 역할의 내부 어드민 도구에는 불필요한 복잡도. `POST /api/auth/login` 성공 시 JWT 발급 → 프론트는 `Authorization: Bearer <token>` 헤더로 호출(`frontend/src/api/authToken.ts`, 메모리 보관 — `localStorage`에 영구저장하지 않음, 새로고침 시 재로그인 필요한 트레이드오프를 의도적으로 받아들임). 백엔드 `SecurityConfig`는 `SessionCreationPolicy.STATELESS` + CSRF 비활성 — 세션/쿠키 자체를 쓰지 않으므로 CORS에 `allowCredentials`도 불필요.

최초 기동 시 `admin_user`가 하나도 없으면 `INITIAL_ADMIN_USERNAME`/`INITIAL_ADMIN_PASSWORD`로 1회 시드된다(`AdminUserBootstrapRunner`).

### CORS 연동 확인 (T2, T11에서 인증과 함께 재검증)

백엔드 `backend/src/main/java/com/scheduler/security/CorsConfig.java` + `SecurityConfig.java`가 `app.cors.allowed-origin`(`CORS_ALLOWED_ORIGIN` 환경변수)에 등록된 오리진만 허용한다. 로컬 SQL Server 컨테이너로 백엔드를 띄우고 `curl`로 프론트 오리진(`http://localhost:5173`)을 흉내 낸 preflight(OPTIONS) → 로그인(POST `/api/auth/login`) → 받은 토큰으로 인증된 GET(`/api/schedules`) 전체 플로우를 실행해 매 단계 `Access-Control-Allow-Origin` 헤더가 정확히 돌아오고, 토큰 없는 요청은 401이 되는 것을 확인했다(브라우저가 내부적으로 수행하는 것과 동일한 점검).

## 운영 배포 (Tomcat, 단일 WAR) — T22~T27

실제 쓸 수 있는 운영 인프라가 외부 Tomcat뿐이라, 운영 배포는 백엔드 jar + 프론트 별도 정적 배포가 아니라 **프론트 빌드 결과를 포함한 단일 WAR**를 외부 Tomcat에 올리는 방식으로 결정됨(PLAN.md §11). 아래 순서를 그대로 따라가면 처음 보는 사람도 재현 가능하도록 작성.

### 1. WAR 빌드 (프론트 빌드까지 한 번에)

```bash
cd backend
mvn clean package
```

`backend/pom.xml`에 추가된 `frontend-maven-plugin`이 `prepare-package` 단계에서 `frontend/`의 `npm install`/`npm run build`까지 전부 수행하고(Node/npm이 시스템에 전혀 설치되어 있지 않아도 됨 — 이 플러그인이 지정된 Node 버전을 자체 다운로드함), 그 결과(`frontend/dist/**`)를 `target/classes/static/`으로 복사해 WAR 안에 함께 패키징한다. 결과물은 `backend/target/scheduler-service-0.1.0-SNAPSHOT.war` 하나. `mvn test`만 실행할 때는 이 프론트 빌드 단계가 돌지 않으므로(의도적 — 평소 백엔드 테스트 반복이 Node/npm과 무관하게 빠르게 유지됨) 느려지지 않는다.

### 2. Tomcat에 배치 — 반드시 `ROOT.war`로

```bash
cp backend/target/scheduler-service-0.1.0-SNAPSHOT.war $CATALINA_HOME/webapps/ROOT.war
```

**`ROOT.war`로 이름을 바꾸는 것은 권장이 아니라 필수 전제조건이다.** 프론트엔드의 API 호출은 모두 `/api/...`처럼 선행 슬래시가 붙은 경로라(`frontend/src/api/client.ts`), 브라우저의 URL 해석 규칙상 이런 경로는 항상 **origin root**로 요청이 가고, 페이지가 서빙된 디렉터리를 기준으로 하지 않는다(T25에서 `new URL('/api/x', 'http://host/scheduler-service/')` 같은 식으로 직접 실측 확인한 내용). 즉 `scheduler-service.war`처럼 이름을 그대로 두어 컨텍스트 경로가 `/scheduler-service`가 되면 프론트는 로드되지만 모든 API 호출이 깨진다. `ROOT.war`(컨텍스트 경로 `/`)로 배치해야만 프론트가 하드코딩된 `localhost` 없이 상대경로로 API를 정상 호출한다(T25/T26에서 실제 Tomcat에 올려 로그인→스케줄 CRUD→지금실행까지 end-to-end로 확인됨).

### 3. 환경변수 주입 — `setenv.sh`/`setenv.bat`

외부 Tomcat은 `catalina.sh`/`catalina.bat`로 기동되지 셸 `export`로 기동되는 게 아니므로, 환경변수는 `$CATALINA_HOME/bin/setenv.sh`(Linux/Mac) 또는 `setenv.bat`(Windows)에 `JAVA_OPTS`의 `-D` 시스템 프로퍼티로 주입한다. Tomcat은 기동 시 이 파일이 있으면 자동으로 읽는다(표준 확장 지점). Spring Boot는 JVM 시스템 프로퍼티와 OS 환경변수를 모두 동일하게 `${VAR}` 플레이스홀더로 해석하므로, `application.yml`이나 자바 코드는 이 때문에 전혀 바뀌지 않는다.

**이 파일은 Tomcat 설치본(`$CATALINA_HOME/bin/`) 안에 두는 것이고, 이 레포에는 절대 커밋하지 않는다** — 아래는 참고용 예시일 뿐이다.

`setenv.sh` (Linux/Mac):
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

`setenv.bat` (Windows):
```bat
set "JAVA_OPTS=%JAVA_OPTS% -DDB_URL=jdbc:sqlserver://meta-db-host;databaseName=scheduler_meta -DDB_USERNAME=scheduler_app -DDB_PASSWORD=*** -DMASTER_ENCRYPTION_KEY=*** -DJWT_SIGNING_KEY=*** -DINITIAL_ADMIN_USERNAME=admin -DINITIAL_ADMIN_PASSWORD=*** -DCORS_ALLOWED_ORIGIN=http://localhost:5173"
```

**특수문자 인용 주의**: `MASTER_ENCRYPTION_KEY`/`JWT_SIGNING_KEY`는 base64 값이라 `+`, `/`, `=` 같은 문자가 포함된다. `setenv.sh`에서는 값 전체를 더블쿼트(`"..."`)로 감싸는 한 `+`/`=`는 셸 특수문자가 아니라서 안전하지만(위 예시처럼), 값을 다시 작은따옴표로 중첩하거나 백틱으로 감싸면 깨질 수 있으니 예시 형태를 그대로 따를 것. `setenv.bat`에서는 `%`가 배치 변수 치환 문자이므로 값에 `%`가 들어가면 반드시 `%%`로 escape해야 하고(base64는 보통 `%`를 포함하지 않지만 다른 값, 예를 들어 비밀번호에 `%`가 들어가면 문제가 됨), `set "..."` 형태(전체를 쿼트)로 양쪽 끝을 감싸는 위 예시 형태를 유지할 것 — 쿼트 없이 `set JAVA_OPTS=...`로 쓰면 `&`, `|`, `<`, `>` 같은 문자에서 배치 파서가 깨진다.

### 4. Tomcat 기동/재기동 + 확인 포인트

```bash
$CATALINA_HOME/bin/shutdown.sh   # 이미 떠 있다면 먼저 종료 (재배치 시)
$CATALINA_HOME/bin/startup.sh    # 또는 systemd/Windows 서비스로 등록된 Tomcat 재시작
```

기동 후 확인할 것:
- `curl http://<host>:8080/` → 200, SPA의 `index.html`이 반환되는지(정적 자산이 WAR 안에 제대로 포함됐는지의 증거).
- `curl http://<host>:8080/api/health` → 인증 없이 200(헬스체크 엔드포인트, `SecurityConfig`에서 `permitAll`).
- `curl -i http://<host>:8080/api/schedules` (토큰 없이) → 401이 와야 함 — 200이 오면 `SecurityConfig`/`SpaWebConfig` 설정이 깨진 것.
- 브라우저로 접속해 `INITIAL_ADMIN_USERNAME`/`INITIAL_ADMIN_PASSWORD`로 로그인 → 스케줄 목록 화면이 뜨는지(최초 기동 시 1회만 관리자 계정이 시드되므로, 두 번째 기동부터는 이 환경변수들이 더 이상 영향 없음).
- Tomcat 로그(`$CATALINA_HOME/logs/catalina.out`)에 `DB_URL이 설정되지 않아...H2로 fallback` 경고가 찍히면 `-DDB_URL`이 빠진 것 — 운영에서는 반드시 실제 SQL Server `DB_URL`을 지정해야 한다.

## 다른 업체 재배포 체크리스트 (T13)

이 프로젝트는 "같은 바이너리/빌드를 재컴파일 없이 다른 업체에 재배포"를 전제로 설계됨(PLAN.md §0). 아래 체크리스트의 값만 바꾸면 되고, **백엔드/프론트엔드 모두 소스 수정이나 재컴파일이 필요 없다.**

위 "운영 배포 (Tomcat, 단일 WAR)" 섹션의 통합 WAR로 배포하는 경우, 아래 프론트엔드 섹션의 "업체별로 `npm run build`를 따로 실행" 안내는 적용되지 않는다 — `VITE_API_BASE_URL`이 빈 값(상대경로)으로 고정된 그 **같은 WAR**를 그대로 재사용하면 되고(어떤 업체의 도메인에 배치하든 상대경로라 그대로 동작), 업체별로 바꿔야 하는 값은 아래 백엔드 섹션의 환경변수들뿐이다. 프론트엔드 섹션은 백엔드/프론트를 여전히 분리 배포하는 경우에만 해당.

### 백엔드 (`backend/`) — 재빌드한 WAR를 그대로 재사용

- [ ] `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` — 그 업체의 메타DB(신규 스키마, Flyway가 기동 시 자동 적용). **`DB_URL`을 반드시 지정** — 생략하면 로컬 개발용 H2로 fallback되므로 실제 배포에서는 절대 비워두지 말 것
- [ ] `MASTER_ENCRYPTION_KEY` — 업체별로 **새로 생성**(`openssl rand -base64 32`). 다른 배포와 재사용하지 않음(이 키가 다르면 기존 암호문을 복호화 못 함 — 재배포 = 새 메타DB + 새 키이므로 문제 없음)
- [ ] `JWT_SIGNING_KEY` — 업체별로 새로 생성, 마찬가지로 재사용하지 않음
- [ ] `INITIAL_ADMIN_USERNAME`/`INITIAL_ADMIN_PASSWORD` — 그 업체의 최초 관리자 계정(최초 기동 1회만 사용됨)
- [ ] `CORS_ALLOWED_ORIGIN` — 그 업체의 프론트엔드 배포 도메인
- [ ] (선택) `SERVER_PORT`, `JWT_EXPIRATION_MINUTES`
- [ ] 기동 후 관리자 웹에서 그 업체의 **대상 업무 DB 접속정보**(`target_db_config`)와 **자격증명**(`credential`, 예: 다운스트림 API 키)을 입력 — 코드에 없음, 전부 DB에 암호화 저장되고 재시작 없이 즉시 적용됨
- [ ] 관리자 웹에서 그 업체의 **스케줄 정의**(SP명, cron, action_type/action_config) 입력

### 프론트엔드 (`frontend/`) — 같은 `npm run build` 산출물을 그대로 재사용

- [ ] `VITE_API_BASE_URL` — 그 업체의 백엔드 API 도메인으로 설정한 `.env.local`(또는 배포 파이프라인의 환경변수 주입)로 **빌드 시점**에 반영. Vite는 빌드 시 환경변수를 정적으로 주입하므로, 업체마다 다른 백엔드 도메인을 쓰려면 업체별로 `npm run build`를 한 번씩 실행해야 함(소스 변경은 없음 — 환경변수만 다르게 주고 같은 명령 재실행)

### 검증 결과 (DoD)

리포지토리 전체에 실제 비밀값 리터럴이 없는지 점검 완료 — 레거시 `Config.java`의 운영 DB IP/계정(`218.38.64.229`, `erpUser`/`erpPasswd`)·FCM 엔드포인트(`192.168.80.27`)·API 키(`f1s-fcm-...`) 어느 것도 저장소에 존재하지 않음(grep 결과 0건, `.team/LOG.md` 2026-11-13 10:3x 기록 참고). `application.yml`/프론트 `.env.example`은 전부 `${VAR}` 플레이스홀더 또는 명백한 로컬 개발용 기본값(`localhost`)만 포함. `.env.local`/`application-local.yml`은 `.gitignore`에 등록되어 있고 git 추적 이력에도 없음(`git ls-files`로 확인).
