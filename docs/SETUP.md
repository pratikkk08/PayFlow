# PayFlow — Local Setup

**Status:** Phase 8.8 final verification on Windows 11 + Docker Desktop (2026-10-17)

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
| `GET http://localhost:8080/actuator/health` | Public | `{"status":"UP"}` (no component details) |
| `GET …/actuator/health/liveness` | Public | Process alive |
| `GET …/actuator/health/readiness` | Public | DB + Redis ready (status only) |
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
| `local` | Default; Compose Postgres/Redis; localhost CORS; JWT auto-generate OK |
| `test` | Automated tests + Testcontainers |
| `prod` | Fail-closed: required DB/JWT/pepper/webhook/CORS env; Secure cookies; Swagger off; Actuator health only; workers off unless opted in. See [SECURITY.md](SECURITY.md) and [`.env.production.example`](../.env.production.example). **Not** compliance-certified. |

Prod assumes TLS termination at a reverse proxy (`X-Forwarded-*` honored). Do not commit certificates or real secret values.

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

### Playwright E2E (Task 8.6)

Browser tests hit the **local Vite** app (`http://127.0.0.1:5173`) proxied to the **local Spring Boot** backend (`8080`). They use unique per-run sandbox users — not production credentials. They do **not** verify production HTTPS `Secure`-cookie login.

**Prerequisites**

1. Postgres + Redis healthy (`docker compose -f infra/docker-compose.yml --env-file .env up -d`)
2. Backend on `8080` with `SPRING_PROFILES_ACTIVE=local`
3. One-time Chromium: `cd frontend && npx playwright install chromium`

**Commands**

```powershell
cd frontend
npm run test:e2e          # headless Chromium; starts Vite unless already up
npm run test:e2e:headed   # optional headed run
```

| Variable | Default | Purpose |
|----------|---------|---------|
| `PLAYWRIGHT_BASE_URL` | `http://127.0.0.1:5173` | Frontend origin |
| `PLAYWRIGHT_SKIP_WEBSERVER` | unset | Set `1` if Vite is already running |

**Isolation:** each test registers a unique `e2e.*@example.com` user and unique merchant/payment references. Do not wipe Docker volumes or mutate shared accounts for these runs.

**Artifacts (gitignored):** `frontend/playwright-report/` (HTML), `frontend/test-results/` (screenshots/traces on failure). Open with `npx playwright show-report`.

**CI:** E2E is **not** a mandatory GitHub Actions job yet — the runner would need a reliable local-stack bring-up (Compose + backend + Vite). Unit/typecheck/lint/build remain the frontend CI gates. Defer CI E2E to a later task.

## 9. CI (GitHub Actions)

Workflow: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)

| Job | What it runs |
|-----|----------------|
| Backend verify | Temurin 21, `chmod +x mvnw`, Docker check, `./mvnw clean verify` (Testcontainers Postgres/Redis/Kafka as used by tests) |
| Frontend verify | Node 24, `npm ci`, typecheck, lint, test, build, `npm audit --omit=dev` (Playwright E2E not in CI yet — see §8) |

Triggers: pull requests, pushes to `main` and `feature/**`, manual `workflow_dispatch`. Concurrent runs on the same ref cancel in progress. Permissions are read-only (`contents: read`); the backend job may upload Surefire reports on failure (`actions: write`).

Local equivalents (same commands CI expects):

```powershell
# Backend (Docker Desktop required for Testcontainers)
. .\scripts\set-java-home.ps1
cd backend
.\mvnw.cmd clean verify

# Frontend
cd frontend
npm ci
npm run typecheck
npm run lint
npm run test
npm run build
npm audit --omit=dev
```

**Prerequisites for GitHub-hosted runs:** repo on GitHub with Actions enabled; Ubuntu runners provide Docker for Testcontainers. First remote run proves the workflow — local verify does not.

**Testcontainers notes:** CI verifies `docker version` / `docker info` before Maven. Image pulls (Postgres, Redis, Kafka) need network egress and enough disk/memory on the runner. No paid external services are required.

**Limitation:** until the branch is pushed and a workflow run completes, GitHub Actions execution is **NOT VERIFIED**.

## 10. Backend Docker image (Task 8.2)

Multi-stage image: [`backend/Dockerfile`](../backend/Dockerfile) (context hygiene: [`backend/.dockerignore`](../backend/.dockerignore)).

| Stage | Image |
|-------|--------|
| Build | `eclipse-temurin:21.0.12.1_1-jdk-alpine-3.24` + Maven Wrapper |
| Runtime | `eclipse-temurin:21.0.12.1_1-jre-alpine-3.24` — JAR only, user `payflow`, port **8080** |

Measured local image size: **~477 MB**.

### Build

```powershell
cd C:\Projects\PayFlow
docker build -t payflow-backend:local -f backend/Dockerfile backend
```

### Run against existing Compose Postgres/Redis

Join the Compose network (`payflow_default`). Inside the container, use **service DNS names** and **container ports** (not `localhost` / host `5433`):

```powershell
docker run --rm --name payflow-backend-local `
  --network payflow_default `
  -p 127.0.0.1:18080:8080 `
  -e SPRING_PROFILES_ACTIVE=local `
  -e DATABASE_URL=jdbc:postgresql://payflow-postgres:5432/payflow `
  -e DATABASE_USERNAME=payflow `
  -e DATABASE_PASSWORD=change_me_local_only `
  -e REDIS_HOST=payflow-redis `
  -e REDIS_PORT=6379 `
  -e JWT_AUTO_GENERATE_KEYS=true `
  -e API_KEY_HASH_PEPPER=local-docker-pepper `
  -e WEBHOOK_SECRET_ENCRYPTION_KEY=UGF5Rmxvd0xvY2FsV2ViaG9va0VuY0tleTMyYnl0ZSE= `
  -e PAYFLOW_KAFKA_ENABLED=false `
  payflow-backend:local
```

Checks: `http://127.0.0.1:18080/api/v1/system/ping`, `http://127.0.0.1:18080/actuator/health` (details never exposed). Image `HEALTHCHECK` probes the same Actuator path via existing Alpine `wget`.

Default image profile is `prod` (`SPRING_PROFILES_ACTIVE`). For real prod-like runs: mount JWT PEMs, set `JWT_AUTO_GENERATE_KEYS=false`, `API_KEY_HASH_PEPPER`, `WEBHOOK_SECRET_ENCRYPTION_KEY`, and DB/Redis credentials via env — **never bake secrets into the image**.

JVM: `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 ...` (override at runtime if needed).

**Limitations:** no production Compose app service yet; do not stop Compose infra volumes when removing a smoke container. Host **8080** may already be in use — map another host port (e.g. `18080`).

## 11. Frontend Docker image (Task 8.3)

Multi-stage image: [`frontend/Dockerfile`](../frontend/Dockerfile), Nginx template [`frontend/nginx/default.conf.template`](../frontend/nginx/default.conf.template).

| Stage | Image |
|-------|--------|
| Build | `node:24.21.0-alpine` — `npm ci` + `npm run build` (no Vite secrets) |
| Runtime | `nginxinc/nginx-unprivileged:1.28.0-alpine` — static `dist/`, user `nginx` (101), port **8080** |

Measured local image size: **~74.9 MB**.

### Build

```powershell
cd C:\Projects\PayFlow
docker build -t payflow-frontend:local -f frontend/Dockerfile frontend
```

### Run (with temporary backend on Compose network)

```powershell
# Backend container name must match PAYFLOW_BACKEND_HOST (default payflow-backend)
docker run -d --name payflow-backend --network payflow_default `
  -e SPRING_PROFILES_ACTIVE=local `
  -e DATABASE_URL=jdbc:postgresql://payflow-postgres:5432/payflow `
  -e DATABASE_USERNAME=payflow -e DATABASE_PASSWORD=change_me_local_only `
  -e REDIS_HOST=payflow-redis -e REDIS_PORT=6379 `
  -e JWT_AUTO_GENERATE_KEYS=true `
  -e API_KEY_HASH_PEPPER=local-docker-pepper `
  -e WEBHOOK_SECRET_ENCRYPTION_KEY=UGF5Rmxvd0xvY2FsV2ViaG9va0VuY0tleTMyYnl0ZSE= `
  -e PAYFLOW_KAFKA_ENABLED=false `
  payflow-backend:local

docker run --rm --name payflow-frontend-local --network payflow_default `
  -p 127.0.0.1:18081:8080 `
  -e PAYFLOW_BACKEND_HOST=payflow-backend `
  -e PAYFLOW_BACKEND_PORT=8080 `
  payflow-frontend:local
```

Then open `http://127.0.0.1:18081`. Nginx serves the SPA and same-origin-proxies `/api/v1/` (and `/actuator/health` only) to the backend so HttpOnly refresh cookies and `XSRF-TOKEN` / `X-XSRF-TOKEN` CSRF work without cross-origin CORS.

Routing notes:

- SPA deep links → `index.html` (`try_files`)
- `/api/v1/*` → backend (paths preserved; unknown API routes are **not** rewritten to the SPA)
- `/actuator/metrics` and other Actuator paths → **404** at Nginx
- `/assets/*` → long-lived immutable cache; `index.html` → `Cache-Control: no-store`
- Security headers: `nosniff`, `Referrer-Policy`, `X-Frame-Options: DENY`
- CSP: `script-src 'self'` (no `unsafe-eval`); `style-src 'self' 'unsafe-inline'` retained for React/Recharts inline styles

Remove only temporary app containers when finished; leave Postgres/Redis/Kafka volumes alone.

## 12. Deployment Compose (Task 8.5)

Separate from §3 dev infra. File: [`infra/docker-compose.deploy.yml`](../infra/docker-compose.deploy.yml).

| Service | Host ports | Notes |
|---------|------------|--------|
| `frontend` | `127.0.0.1:18080` → 8080 | Only published service |
| `backend` | none | Internal DNS `backend:8080`; prod profile |
| `postgres` | none | Internal `postgres:5432` |
| `redis` | none | Internal `redis:6379` |
| `kafka` | none | Optional `--profile kafka` |

Network: `payflow_deploy_internal`. Approximate RAM (limits): postgres 512M + redis 128M + backend 768M + frontend 64M ≈ **1.5 GB** (Kafka +768M if enabled). Not a capacity claim.

### Smoke (temporary secrets outside Git)

```powershell
.\scripts\prepare-deploy-smoke-secrets.ps1
docker compose -f infra/docker-compose.deploy.yml --env-file secrets/deploy-smoke/.env config
docker compose -f infra/docker-compose.deploy.yml --env-file secrets/deploy-smoke/.env up -d --build
# Checks: http://127.0.0.1:18080/  /api/v1/system/ping  /actuator/health
docker compose -f infra/docker-compose.deploy.yml --env-file secrets/deploy-smoke/.env down
# Do NOT use down -v unless you intend to wipe deploy volumes.
```

JWT PEMs mount read-only at `/run/secrets/*.pem`. Placeholder template: [`.env.production.example`](../.env.production.example).

**HTTPS:** prod sets `Secure` cookies. Local HTTP smoke verifies health/proxy/CSRF `Secure` flags, but full browser login needs TLS termination (or browser localhost HTTPS). Do not disable `Secure` for convenience.

## 13. Operations & backup (Task 8.7)

- Day-to-day: [OPERATIONS.md](OPERATIONS.md) — start/stop, health probes, logs, resource caps, networking
- Postgres: [BACKUP_RECOVERY.md](BACKUP_RECOVERY.md) — `.\scripts\backup-deploy-postgres.ps1`, disposable restore via `.\scripts\restore-deploy-postgres.ps1`
- Correlation: clients may send `X-Request-Id`; responses echo it; logs include `[correlationId]`
- Dumps land in `backups/` (gitignored); never commit them

## 14. Related docs

- [ARCHITECTURE.md](ARCHITECTURE.md)
- [ROADMAP.md](ROADMAP.md)
- [ENVIRONMENT_REPORT.md](ENVIRONMENT_REPORT.md)
- [OPERATIONS.md](OPERATIONS.md)
- [BACKUP_RECOVERY.md](BACKUP_RECOVERY.md)
- [AI_HANDOFF.md](AI_HANDOFF.md)
- [../AGENTS.md](../AGENTS.md)
