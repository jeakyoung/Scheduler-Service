# scheduler-service

범용 스케줄링 서비스. 스케줄 정의(대상 SP, cron, 다운스트림 동작)를 DB에 저장하고 Quartz로 실행한다.
설계 배경/결정 사항은 `.team/PLAN.md`, 작업 분해는 `.team/TASKS.md` 참고.

저장소는 두 개의 독립 배포 단위로 나뉜다:
- `backend/` — Spring Boot REST API (화면 렌더링 없음)
- `frontend/` — React SPA (백엔드와 별도로 빌드/배포, T2에서 스캐폴딩)

## 백엔드 로컬 기동에 필요한 환경변수

| 환경변수 | 설명 |
|---|---|
| `DB_URL` | 메타DB(스케줄 정의/설정/Quartz 상태) JDBC URL. 예: `jdbc:sqlserver://localhost;databaseName=scheduler_meta;encrypt=false;trustServerCertificate=true;` |
| `DB_USERNAME` | 메타DB 접속 계정 |
| `DB_PASSWORD` | 메타DB 접속 비밀번호 |
| `MASTER_ENCRYPTION_KEY` | `target_db_config`/`credential` 암호화용 AES-256 마스터키 (base64). 예: `openssl rand -base64 32` |
| `INITIAL_ADMIN_USERNAME` | (최초 기동 시 관리자 계정이 없을 때만 사용) 시드할 관리자 계정명 |
| `INITIAL_ADMIN_PASSWORD` | (최초 기동 시) 시드할 관리자 비밀번호 |
| `CORS_ALLOWED_ORIGIN` | 프론트엔드(React SPA) 배포 오리진, 쉼표로 복수 허용. 로컬 기본값 `http://localhost:5173` |
| `SERVER_PORT` | (선택) 기본 8080 |

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

### 빌드/실행

```bash
cd frontend
cp .env.example .env.local   # 최초 1회
npm install
npm run dev     # 로컬 개발 서버 (기본 http://localhost:5173)
npm run build   # 프로덕션 빌드
```

### CORS 연동 확인 (T2 완료 검증)

백엔드 `backend/src/main/java/com/scheduler/security/CorsConfig.java` + `SecurityConfig.java`가 `app.cors.allowed-origin`(`CORS_ALLOWED_ORIGIN` 환경변수)에 등록된 오리진만 허용한다. 로컬 SQL Server 컨테이너로 백엔드를 띄우고 `curl`로 프론트 오리진(`http://localhost:5173`)을 흉내 낸 preflight(OPTIONS)/실제 GET 요청을 `/api/health`에 보내 `Access-Control-Allow-Origin` 헤더가 정확히 돌아오는 것을 확인했다(브라우저가 내부적으로 수행하는 것과 동일한 점검). `SecurityConfig`는 아직 T11 이전이라 임시로 모든 요청을 허용(`permitAll`) — 실제 인증 적용은 T11.

자세한 "다른 업체 재배포" 체크리스트는 T13에서 보강한다(현재는 뼈대 단계).
