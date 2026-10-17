# PayFlow

Portfolio-grade, production-inspired **fintech sandbox** for merchant accounts, secure API keys, simulated payment processing, refunds, signed webhooks, transaction analytics, and fraud-risk rules.

> **Simulation only.** PayFlow never processes real card details or live customer funds.

## Why this project exists

PayFlow demonstrates serious backend engineering and system design: modular domain boundaries, money-safe arithmetic, idempotent APIs, a payment state machine, transactional integrity, and eventually outbox-based event publication with reliable webhook delivery.

It is intentionally **not** a thin CRUD demo.

## Current status

**Phase 9 in progress on `feature/phase-9-portfolio`.** Phase 8 complete locally. Task **9.2** verified Secure-cookie auth over **local HTTPS**. Task **9.3** hosting plan: **₹0/month** — prefer Oracle Always Free Ampere A1 (capacity **unverified**); see [docs/DEPLOYMENT_PLAN.md](docs/DEPLOYMENT_PLAN.md). No cloud resources provisioned. Remote GitHub Actions **not** verified. No real-money processing.

Not yet built: remote CI green proof, free-tier public VM (gated), CI-hosted E2E, risk rule-management APIs, webhook secret rotation.

See [docs/ROADMAP.md](docs/ROADMAP.md) and [docs/DEVELOPMENT_LOG.md](docs/DEVELOPMENT_LOG.md) for phase progress.

## Goals

- Merchant onboarding and API-key authenticated payment APIs
- Simulated authorize / capture / void / refund flows with a strict state machine
- Idempotency keys, concurrency safety, and auditable financial records
- Signed webhooks with retry semantics
- Risk rules, analytics summaries, and event-driven processing (Kafka)
- Clear documentation suitable for portfolio review

## Non-goals

- Real card networks, acquirers, or live settlement
- Storing raw PANs or CVVs
- Premature microservices
- Exactly-once Kafka delivery claims

## Tech stack

| Area | Technology |
|------|------------|
| Backend | Java 21, Spring Boot, Spring Security, Spring Data JPA, Flyway, Bean Validation |
| Build | Maven Wrapper |
| Database | PostgreSQL |
| Cache / rate limits | Redis |
| Messaging | Apache Kafka |
| Frontend | React, TypeScript, Vite, Tailwind CSS |
| Tests | JUnit 5, Mockito, Testcontainers; Vitest; Playwright Chromium E2E (local) |
| API docs | OpenAPI / Swagger |
| Infra | Docker Compose, GitHub Actions CI, backend + frontend Docker images (Tasks 8.2–8.3), cloud deployment (later) |

## Architecture approach

PayFlow starts as a **modular monolith**: one deployable Spring Boot application with clear internal domain packages (identity, merchants, payments, refunds, webhooks, risk, ledger, analytics, notifications).

- **PostgreSQL** is the authoritative store for financial transaction state.
- **Redis** supports caching and rate limiting — not financial truth.
- **Kafka** carries domain events; consumers must be idempotent under at-least-once delivery.
- An **outbox** in PostgreSQL is written in the same transaction as the domain change. A publisher sends to Kafka only after the broker ACK.

Details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Roadmap (summary)

| Phase | Focus |
|-------|--------|
| 0 | Docs, repo layout, environment validation |
| 1 | Backend skeleton, Docker Compose (Postgres/Redis/Kafka), Flyway baseline |
| 2 | Identity & merchants, API keys |
| 3 | Payments state machine & simulated processor |
| 4 | Refunds, ledger, idempotency hardening |
| 5 | Webhooks, outbox, Kafka consumers |
| 6 | Risk rules & analytics |
| 7 | React dashboard ← **done** |
| 8 | CI, hardening, portfolio polish ← **done locally** (8.1–8.8) |
| 9 | Portfolio polish ← **in progress** (9.1–9.3 docs/local; ₹0 host / remote CI pending approval) |

Full plan: [docs/ROADMAP.md](docs/ROADMAP.md). Product scope: [docs/PRODUCT_REQUIREMENTS.md](docs/PRODUCT_REQUIREMENTS.md).

## Repository layout

```
backend/              Spring Boot application + Dockerfile (Phase 8.2)
frontend/             React + TypeScript + Vite client + Dockerfile/Nginx (Phase 8.3)
infra/                Docker Compose and infrastructure assets
scripts/              Local helper scripts
docs/                 Requirements, architecture, roadmap, decisions, logs
.github/workflows/    CI (`ci.yml` — backend + frontend verify)
AGENTS.md             Persistent instructions for AI coding agents
```

## Local development

```powershell
Copy-Item .env.example .env
. .\scripts\set-java-home.ps1
docker compose -f infra/docker-compose.yml --env-file .env up -d
cd backend
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

Frontend (separate terminal; Node 22+/24 on PATH):

```powershell
cd frontend
npm install
npm run dev
# http://localhost:5173  (proxies /api/v1 → localhost:8080)
```

- Ping: `http://localhost:8080/api/v1/system/ping`
- Health: `http://localhost:8080/actuator/health`
- Swagger (local): `http://localhost:8080/swagger-ui.html`
- Auth guide: [docs/API_AUTHENTICATION.md](docs/API_AUTHENTICATION.md)
- Security notes: [docs/SECURITY.md](docs/SECURITY.md)
- Optional Kafka: `docker compose -f infra/docker-compose.yml --env-file .env --profile kafka up -d`

Full instructions: [docs/SETUP.md](docs/SETUP.md).  
Verified tool versions: [docs/ENVIRONMENT_REPORT.md](docs/ENVIRONMENT_REPORT.md).

**Note:** Compose Postgres uses host port **5433** by default. JWT PEM keys are auto-created under `backend/local-secrets/` for local profile.

### CI

GitHub Actions workflow [`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on PRs, pushes to `main`/`feature/**`, and `workflow_dispatch`:

- **Backend:** Temurin 21 + Maven Wrapper `clean verify` (Docker/Testcontainers)
- **Frontend:** Node 24 — `npm ci`, typecheck, lint, test, build, `npm audit --omit=dev`
- **Playwright E2E:** local only for now (`npm run test:e2e`); not a CI gate until full-stack runner bring-up exists — see [docs/SETUP.md](docs/SETUP.md) § Playwright E2E

Local parity commands: [docs/SETUP.md](docs/SETUP.md) § CI. Remote Actions runs are **not verified** until the repo is pushed and a workflow completes.

### Playwright E2E (local)

```powershell
# Backend on :8080 + Compose Postgres/Redis must already be healthy
cd frontend
npx playwright install chromium   # once
npm run test:e2e
```

HTTP scenarios (Vite): auth/session, merchant isolation, simulated payment lifecycle. Reports under `frontend/playwright-report/` (gitignored).

Local HTTPS Secure-cookie proof (deploy Compose + self-signed Nginx TLS): see [docs/SETUP.md](docs/SETUP.md) §12a — `npm run test:e2e:https`. Playwright may ignore self-signed cert errors for that run only; that is not OS trust-store proof. No public cloud deploy yet.

### Backend Docker image

```powershell
docker build -t payflow-backend:local -f backend/Dockerfile backend
# Run on Compose network — see docs/SETUP.md § Backend Docker image
```

Runtime: Temurin 21 JRE Alpine, non-root `payflow`, port 8080, `HEALTHCHECK` → `/actuator/health`. Measured size ~**477 MB**. No secrets in the image.

### Frontend Docker image

```powershell
docker build -t payflow-frontend:local -f frontend/Dockerfile frontend
# Run on Compose network with backend — see docs/SETUP.md § Frontend Docker image
```

Runtime: Nginx unprivileged Alpine, non-root `nginx`, port 8080, SPA + `/api/v1` proxy. Measured size ~**74.9 MB**. Same-origin cookies/CSRF; CSP without `unsafe-eval`; no Vite secrets in the image.

### Production profile

`SPRING_PROFILES_ACTIVE=prod` requires explicit DB/JWT/pepper/webhook/CORS configuration and rejects local placeholders. Template: [`.env.production.example`](.env.production.example). Details: [docs/SECURITY.md](docs/SECURITY.md).

### Deployment Compose

```powershell
.\scripts\prepare-deploy-smoke-secrets.ps1
docker compose -f infra/docker-compose.deploy.yml --env-file secrets/deploy-smoke/.env up -d --build
# http://127.0.0.1:18080  (DB/Redis/backend not published)
docker compose -f infra/docker-compose.deploy.yml --env-file secrets/deploy-smoke/.env down
```

Dev infra remains `infra/docker-compose.yml` (published Postgres/Redis). See [docs/SETUP.md](docs/SETUP.md) § Deployment Compose. Day-to-day ops and backups: [docs/OPERATIONS.md](docs/OPERATIONS.md), [docs/BACKUP_RECOVERY.md](docs/BACKUP_RECOVERY.md).

## Documentation

| Document | Description |
|----------|-------------|
| [AGENTS.md](AGENTS.md) | Architecture, standards, and AI workflow |
| [docs/PRODUCT_REQUIREMENTS.md](docs/PRODUCT_REQUIREMENTS.md) | Goals, MVP, non-goals |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Modules, entities, consistency, security |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Milestones and acceptance criteria |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Architecture Decision Records |
| [docs/DEVELOPMENT_LOG.md](docs/DEVELOPMENT_LOG.md) | Chronological progress log |
| [docs/SETUP.md](docs/SETUP.md) | Local environment setup |
| [docs/OPERATIONS.md](docs/OPERATIONS.md) | Deploy Compose ops / health / troubleshooting |
| [docs/BACKUP_RECOVERY.md](docs/BACKUP_RECOVERY.md) | PostgreSQL logical backup & disposable restore |
| [docs/ENVIRONMENT_REPORT.md](docs/ENVIRONMENT_REPORT.md) | Tool verification results |

## Safety

- Never commit `.env`, secrets, tokens, or real customer financial data.
- Use sandbox/mock processing only.
- Do not create paid cloud resources without an explicit decision.

## License

Private portfolio project unless a license file is added later.
