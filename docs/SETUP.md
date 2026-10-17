# PayFlow — Local Setup

**Status:** Phase 7.1 frontend foundation on Windows 11 + Docker Desktop (2026-10-16)

## 1. Prerequisites

| Tool | Target | Notes |
|------|--------|-------|
| Windows 11 | — | PowerShell |
| Java JDK | 21 | Temurin verified: `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` |
| Node.js | 22+ / 24 | Required for `frontend/` (verified Node 24 + npm 11) |
| Git | 2.x | — |
| Docker Desktop | WSL 2 | Engine must be running |
| Maven | Wrapper only | `backend\mvnw.cmd` |

If `JAVA_HOME` is empty:

```powershell
. .\scripts\set-java-home.ps1
```

## 2. Environment file

```powershell
cd C:\Projects\PayFlow
Copy-Item .env.example .env
# Edit only if you change ports/passwords. Never commit .env.
```

Important defaults:

- Postgres **host port `5433`** (avoids conflict with native Postgres on `5432`)
- Redis `6379`
- Kafka (optional) host `9094`
- `SPRING_PROFILES_ACTIVE=local`
- `PAYFLOW_KAFKA_ENABLED=false` (Spring Kafka client still excluded in Phase 1)

## 3. Start infrastructure

From the repository root:

```powershell
# Required for local app: Postgres + Redis
docker compose -f infra/docker-compose.yml --env-file .env up -d

# Optional Kafka (KRaft, ~768MB cap)
docker compose -f infra/docker-compose.yml --env-file .env --profile kafka up -d

docker compose -f infra/docker-compose.yml ps
```

Validate Compose file:

```powershell
docker compose -f infra/docker-compose.yml config
```

Health expectations:

- `payflow-postgres` healthy on `127.0.0.1:5433`
- `payflow-redis` healthy on `127.0.0.1:6379`
- `payflow-kafka` healthy on `127.0.0.1:9094` when profile enabled

Stop services (keeps volumes):

```powershell
docker compose -f infra/docker-compose.yml --profile kafka down
```

Do **not** run `docker compose down -v` unless you intentionally want to wipe local data volumes.

## 4. Run tests

```powershell
. .\scripts\set-java-home.ps1
cd backend
.\mvnw.cmd clean verify
```

Phase 2 verified result: **12 tests, 0 failures** (requires Docker for Testcontainers Postgres + Redis).

JWT RSA keys: local profile auto-generates `backend/local-secrets/jwt-*.pem` on first start (gitignored). Optional: `.\scripts\generate-jwt-keys.ps1`.

## 5. Run the backend

```powershell
. .\scripts\set-java-home.ps1   # from repo root if needed
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

Or rely on `SPRING_PROFILES_ACTIVE=local` from `.env` / shell.

### Endpoints

| Endpoint | Auth | Expected |
|----------|------|----------|
| `GET http://localhost:8080/api/v1/system/ping` | Public | `{"status":"UP","service":"payflow-backend"}` |
| `GET http://localhost:8080/actuator/health` | Public | `{"status":"UP",...}` (no details) |
| `POST /api/v1/auth/register` / `login` | Public | Creates user / returns tokens |
| `GET /api/v1/users/me` | JWT | Current user profile |
| `GET /api/v1/merchants` | JWT | Owner-accessible merchants |
| `GET /api/v1/integrations/me` | `sk_test_` | Merchant key diagnostic |
| `GET /swagger-ui.html` | Public (local) | OpenAPI UI |

PowerShell checks:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/system/ping
Invoke-RestMethod http://localhost:8080/actuator/health
```

## 6. Profiles

| Profile | Purpose |
|---------|---------|
| `local` | Default; Compose Postgres/Redis |
| `test` | Automated tests + Testcontainers |
| `prod` | Safe defaults only; secrets via env — not production-certified |

## 7. Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Postgres port bind error on 5432 | Native Postgres occupying 5432 | Use Compose mapping **5433** (default) |
| `JAVA_HOME` empty / Maven fails | JDK path not exported | `. .\scripts\set-java-home.ps1` |
| Docker daemon errors | Desktop not running | Start Docker Desktop; wait for engine |
| Kafka restart loop | Bad cluster id / listeners | Use current `infra/docker-compose.yml`; recreate kafka volume only if needed |
| Parent POM `4.1.1.RELEASE` missing | Wrong version string | Use `4.1.1` (Maven Central) |

## 8. Frontend (Phase 7)

```powershell
cd frontend
npm install
npm run dev
```

- App: `http://localhost:5173` (Vite may fall back to **5174** if 5173 is busy) — proxies `/api/v1` and `/actuator` to Spring Boot `http://localhost:8080` (not bare `/api`, so SPA `/api-keys` is served correctly)
- Auth: `/login`, `/register`; dashboard routes require a browser session
- Session: access JWT in memory; refresh via HttpOnly cookie on `/api/v1/auth/browser/**` (CSRF required; local profile sets `cookie-secure: false` for HTTP)
- CORS (local): `http://localhost:5173` and `:5174` (and 127.0.0.1 equivalents)
- Optional: copy `frontend/.env.example` → `frontend/.env` (never put secrets or refresh tokens in Vite env)
- Checks: `npm run typecheck`, `npm run lint`, `npm run test`, `npm run build`

## 9. Related docs

- [ARCHITECTURE.md](ARCHITECTURE.md)
- [ROADMAP.md](ROADMAP.md)
- [ENVIRONMENT_REPORT.md](ENVIRONMENT_REPORT.md)
- [AI_HANDOFF.md](AI_HANDOFF.md)
- [../AGENTS.md](../AGENTS.md)
