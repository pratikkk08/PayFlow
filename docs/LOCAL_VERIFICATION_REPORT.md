# PayFlow — Local Verification Report

**Date:** 2026-10-27  
**Machine:** Windows 11, ~16 GB RAM, SSD  
**Branch:** `feature/phase-9-portfolio` @ `416ff05`  
**Remote:** private `https://github.com/pratikkk08/PayFlow.git` (no push/commit this session)  
**Scope:** Local verification only — no cloud, hosting, or GitHub visibility changes  

**Overall status: PASSED**

---

## 1. Environment versions

| Tool | Version / notes |
|------|-----------------|
| Java | Temurin OpenJDK **21.0.12.1** (`JAVA_HOME` via `scripts/set-java-home.ps1`) |
| Maven Wrapper | Apache Maven **3.10.0** (`backend/mvnw.cmd`) |
| Node.js | **v24.21.0** |
| npm | **11.19.0** |
| Docker Engine | **29.8.2** (Desktop; started for this run) |
| Docker Compose | **v5.5.1** |
| Git | **2.56.0.windows.2** |
| Spring Boot | **4.1.1** |
| Frontend | React 19 + Vite **7.3.7** |

Local secrets present (gitignored, not overwritten): `.env`, `backend/local-secrets/jwt-*.pem`.

---

## 2. Startup checks

| Check | Result | Evidence |
|-------|--------|----------|
| Docker engine | PASS | Connected after starting Docker Desktop |
| Postgres `127.0.0.1:5433` | PASS | healthy; `pg_isready` OK |
| Redis `127.0.0.1:6379` | PASS | healthy; `PONG` |
| Kafka `127.0.0.1:9094` (optional) | PASS | was already running; healthy |
| Flyway V1–V19 | PASS | `Successfully validated 19 migrations`; schema at **19**; no migrate needed |
| Backend start (`local`) | PASS | `Started PayflowBackendApplication` ~8–10 s; Tomcat **8080** |
| `GET /api/v1/system/ping` | PASS | `{"status":"UP","service":"payflow-backend"}` |
| `GET /actuator/health` | PASS | `UP` (no component details) |
| Readiness / liveness | PASS | both `UP` |
| Frontend Vite | PASS | `http://127.0.0.1:5173/` (proxy `/api/v1` + `/actuator` → `8080`) |
| Login SPA load | PASS | HTTP 200; heading “Sign in” |

**URLs used**

| Surface | URL |
|---------|-----|
| Backend | `http://localhost:8080` |
| Frontend (dev) | `http://127.0.0.1:5173` (also `http://localhost:5173`) |
| HTTPS deploy smoke | `https://127.0.0.1:18443` |

No unexpected startup exceptions. Maven Flyway-community 401 metadata warnings are non-fatal noise.

---

## 3. Feature-by-feature results

Legend: **Live browser** | **API** | **Automated-test** | **Not tested**

| Area | Result | How verified |
|------|--------|--------------|
| **A. Auth — register** | PASS | Live browser + API + Playwright |
| **A. Auth — login** | PASS | Live browser + API + Playwright |
| **A. Session restore (reload)** | PASS | Live browser + Playwright |
| **A. Logout** | PASS | Live browser + API + Playwright |
| **A. Protected route redirect** | PASS | Live browser (`/dashboard` → `/login`) + Vitest |
| **B. Merchants create A/B** | PASS | Live browser + API |
| **B. Edit merchant** | PASS | API |
| **B. Switch merchants** | PASS | Live browser + Playwright isolation |
| **B. Ownership isolation** | PASS | API (cross-user **404**) + Playwright |
| **C. Payments create INR** | PASS | API + Playwright lifecycle |
| **C. Retrieve / list** | PASS | API |
| **C. Authorize / capture** | PASS | API + Playwright |
| **C. Cancel eligible payment** | PASS | API |
| **C. Duplicate reference conflict** | PASS | API (**409**) |
| **C. Idempotent retry** | PASS | API (same `paymentId`) |
| **D. Partial refund** | PASS | API + Playwright |
| **D. Retrieve / history** | PASS | API |
| **D. Remaining capacity / over-refund** | PASS | API (second partial OK; over-capacity **409**) |
| **D. Refund idempotency** | PASS | API |
| **E. Analytics counts / amounts / risk** | PASS | API overview + daily; Live browser overview UI |
| **E. Merchant isolation** | PASS | API (B = 0 payments) + Playwright |
| **F. API keys create / reveal-once / list / revoke** | PASS | API; Vitest for UI reveal-once |
| **G. Webhooks HTTPS config CRUD + secret once** | PASS | API; Vitest for UI; **no external deliveries** |
| **H. Ledger balanced / no duplicate postings** | PASS | Automated-test (`LedgerPostingTest` 9) — **no public ledger HTTP API** |
| **H. Risk assessment / velocity** | PASS | Automated-test + authorize path + analytics risk fields |

API journey script: `scripts/local-verification-journey.ps1` — **39/39 PASS** this run.

---

## 4. Backend tests

```text
mvnw.cmd clean verify
Tests run: 242, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Includes ledger, risk velocity/concurrency, Flyway, Kafka outbox/consumer, webhook TLS transport, browser-session security, production hardening.

---

## 5. Frontend tests

| Command | Result |
|---------|--------|
| `npm run typecheck` | PASS (exit 0) |
| `npm run lint` | PASS (exit 0) |
| `npm run test` (Vitest) | **82 passed** / 12 files |
| `npm run build` | PASS (chunk-size warning only) |
| `npm audit --omit=dev` | **0 vulnerabilities** |

---

## 6. Playwright E2E

### HTTP (`npm run test:e2e` against local Vite + Spring Boot)

| Spec | Result |
|------|--------|
| `auth.spec.ts` | PASS |
| `merchant-isolation.spec.ts` | PASS |
| `payment-lifecycle.spec.ts` | PASS |
| `https-secure-cookies.spec.ts` | SKIPPED under HTTP base URL (by design) |

**Final HTTP E2E: 3 passed, 0 failed** (after Vite bind fix).

### HTTPS Secure-cookie (`npm run test:e2e:https`)

Prerequisites present (`secrets/local-https/*`, `secrets/deploy-https/.env`). Stack started with existing `payflow-*:deploy` images (no rebuild). Local app processes stopped temporarily to free RAM; **volumes not deleted** (`down` without `-v`).

| Check | Result |
|-------|--------|
| `https://127.0.0.1:18443/api/v1/system/ping` | UP |
| SPA | 200 |
| `/actuator/metrics` | 404 |
| HTTP → HTTPS redirect | 301 |
| Playwright HTTPS Secure-cookie | **1 passed** |

---

## 7. Database persistence

| Check | Result |
|-------|--------|
| Payment CAPTURED after backend restart | PASS |
| Refund SUCCEEDED after restart | PASS |
| Merchant edit persisted | PASS |
| Cancelled payment persisted | PASS |
| Flyway on restart | Validated 19; **no migration / no data reset** |

---

## 8. Docker infrastructure

| Item | Status |
|------|--------|
| `infra/docker-compose.yml` Postgres/Redis | healthy; ports `5433` / `6379` on loopback |
| Kafka (profile) | healthy on `9094`; RestartCount=0 |
| Unexpected restart loops | None observed |
| Plaintext secrets in container logs | Not observed in sampled logs |
| Volume deletion | **None** — Compose recreate kept named volumes; HTTPS `down` without `-v` |

Application containers: HTTPS deploy smoke only (targeted); local app ran via Maven/Vite, not Compose app services.

---

## 9. Security observations

| Control | Observation |
|---------|-------------|
| Access JWT memory-only | Live CDP: empty `localStorage` / `sessionStorage`; no token keys |
| Refresh HttpOnly | `document.cookie` showed only non-HttpOnly `XSRF-TOKEN` (expected) |
| CSRF | Browser login/logout path exercised; BrowserSessionSecurityTest PASS |
| Merchant authorization | Cross-user merchant GET → **404** |
| API-key secret not in list/storage | Create returns once; list has no secret; revoked key → **401** |
| Idempotency | Payment + refund retries return same ids |
| Payment state machine | create → authorize → capture; separate cancel path |
| Error leakage | Duplicate/capacity returned status codes without stack traces in client |
| Real gateways | Simulated processor only (`PAYMENT_PROCESSOR_MODE` / sandbox) |
| Secrets in Git | `.env`, PEMs, `secrets/` gitignored; no unexpected tracked secrets |

Security controls were **not** weakened for verification.

---

## 10. Bugs found and fixed

| Issue | Severity | Resolution |
|-------|----------|------------|
| Vite default bind on Windows was **IPv6-only** (`[::1]:5173`), so Playwright `http://127.0.0.1:5173` got `ERR_CONNECTION_REFUSED` | Environment / local-dev UX | Set `server.host: '127.0.0.1'` in `frontend/vite.config.ts` (uncommitted). Re-ran E2E → 3/3 PASS |

No application-domain defects found in the verified journeys.

---

## 11. Known limitations (unchanged product scope)

- No public ledger HTTP API (ledger covered by tests).
- Webhook delivery off by default; not exercised against external URLs.
- Access JWT not instantly revoked on logout (documented).
- Risk engine is simulated; no rule-management UI/API.
- HTTPS E2E uses self-signed cert + Playwright `ignoreHTTPSErrors` — not OS trust-store proof.
- Hosting / Oracle / public GitHub release **out of scope** for this report.

---

## 12. Exact startup / shutdown commands

### Infrastructure

```powershell
cd C:\Projects\PayFlow
Copy-Item .env.example .env   # only if missing — do not overwrite secrets
docker compose -f infra/docker-compose.yml --env-file .env up -d
# optional Kafka:
docker compose -f infra/docker-compose.yml --env-file .env --profile kafka up -d
```

### Backend

```powershell
. .\scripts\set-java-home.ps1
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

### Frontend

```powershell
cd frontend
npm run dev
# App: http://127.0.0.1:5173/
```

### Regression

```powershell
# Backend
. .\scripts\set-java-home.ps1
cd backend
.\mvnw.cmd clean verify

# Frontend
cd frontend
npm run typecheck
npm run lint
npm run test
npm run build
npm audit --omit=dev

# Playwright HTTP (backend + Vite up)
$env:PLAYWRIGHT_SKIP_WEBSERVER='1'
$env:PLAYWRIGHT_BASE_URL='http://127.0.0.1:5173'
npm run test:e2e

# Playwright HTTPS (deploy HTTPS stack; see SETUP.md §12a)
$env:PLAYWRIGHT_BASE_URL='https://127.0.0.1:18443'
$env:PLAYWRIGHT_SKIP_WEBSERVER='1'
npm run test:e2e:https
```

### Shutdown (keep volumes)

```powershell
# Stop Maven/Vite processes in their terminals (Ctrl+C)
docker compose -f infra/docker-compose.yml --profile kafka down
# NEVER use down -v unless intentionally wiping local data
```

---

## 13. Git working tree after verification

| Item | Value |
|------|--------|
| Branch | `feature/phase-9-portfolio` |
| HEAD | `416ff05` |
| Dirty | `frontend/vite.config.ts` (host bind fix) |
| Untracked | `scripts/local-verification-journey.ps1` (API journey helper) |
| Commit / push | **Not performed** (per instructions) |

---

## 14. Overall

**PASSED** — Complete local PayFlow stack verified on this machine: infrastructure, backend, frontend, API + browser journeys, automated regression (242 + 82), Playwright HTTP + HTTPS Secure-cookie, persistence across restart, and Docker health without destructive volume operations.
