# PayFlow — AI Handoff

**Updated:** 2026-10-13  
**Purpose:** Minimal context for a new Cursor Agent chat. Prefer this + `AGENTS.md` over re-reading the whole repo history.

## Objective

Simulated payment platform (portfolio): merchants, API keys, payments (authorize/capture/cancel), refunds/webhooks later. **Sandbox only — no real cards or live funds.**

## Stack (implemented)

| Area | Choice |
|------|--------|
| Backend | Java 21, Spring Boot **4.1.1**, Security, JPA, Flyway, Validation, Actuator |
| Auth | OAuth2 Resource Server (RS256 JWT), BCrypt passwords, opaque refresh tokens |
| Docs API | springdoc-openapi **3.1.1** |
| DB / cache | PostgreSQL 16 (host **5433**), Redis 7 (**6379**) |
| Messaging | Kafka optional Compose profile (**9094**); Spring Kafka autoconfig **excluded** |
| Build | Maven Wrapper (`backend\mvnw.cmd`) |
| Frontend | Not started (`frontend/` placeholder) |

## Git

| Item | Value |
|------|--------|
| Branch | `feature/phase-3-payments` |
| Latest commit | `feat: implement payment processing engine and idempotency` (confirm with `git log -1`) |
| Also | `main` @ `feb8bba` (Phase 0); Phase 1 @ `75e8b55`; Phase 2 @ `46d2f1a` |
| Remote | None configured; do not push unless asked |

## Completed phases

| Phase | Status | Highlights |
|-------|--------|------------|
| 0 | Done | Docs, layout, `.gitignore`, `.env.example` |
| 1 | Done | Boot app, Compose, Flyway V1, ping, deny-by-default security |
| 2 | Done | Identity, merchants, API keys, JWT/refresh, OpenAPI, rate limit |
| 3 | Done | Payments schema, idempotent create, authorize/capture/cancel, list/get |

**Not started:** Phase 4+ (refunds, ledger, webhooks/outbox, risk, analytics, React UI, CI).

## Migrations (Flyway V1–V7)

| Version | Purpose |
|---------|---------|
| V1 | Baseline `payflow_schema_meta` |
| V2 | Users, refresh, merchants, memberships, api_keys, audit |
| V3 | `payments` table (minor units, status check, optimistic `version`) |
| V4 | `payment_idempotency_records` |
| V5 | Auth columns (`failure_code`, `authorized_at`, `processor_ref`) |
| V6 | Capture/cancel timestamps |
| V7 | List indexes (`merchant_id, created_at, id`) |

Runtime marker: `payflow_schema_meta.schema_phase = phase-3`.

## APIs (live)

Public: `POST /api/v1/auth/{register,login,refresh,logout}`, `GET /api/v1/system/ping`, actuator health/info, Swagger (local).

JWT user: `GET /api/v1/users/me`; merchants CRUD; API key create/list/revoke.

**JWT payments (owner only):**

- `POST /api/v1/payments` — create (`Idempotency-Key` required); money = `amountMinor` + `INR`
- `GET /api/v1/payments/{id}` — get
- `GET /api/v1/payments?merchantId=&status=&createdFrom=&createdTo=&page=&size=` — list
- `POST /api/v1/payments/{id}/authorize` — simulated auth → `AUTHORIZED` or `FAILED`
- `POST /api/v1/payments/{id}/capture` — `AUTHORIZED` → `CAPTURED`
- `POST /api/v1/payments/{id}/cancel` — `CREATED`/`AUTHORIZED` → `CANCELLED`

Secret API key (`Bearer sk_test_…`): `GET /api/v1/integrations/me` only — **not** payment endpoints (403).

Cross-merchant access → **404**. Publishable `pk_test_…` cannot authenticate.

## Key decisions (see `DECISIONS.md`)

- Modular monolith; Postgres SoT; money as integer minor units (ADR-003).
- Two-step flow: `CREATED` → `AUTHORIZED` → `CAPTURED`; cancel uses `CANCELLED` (not `VOIDED`).
- Idempotency in Postgres: unique `(merchant_id, operation, key)`; fingerprint conflict → 409 (ADR-016).
- Domain state machine + `@Version` + `SELECT … FOR UPDATE` on transitions.
- Deterministic simulated processor (decline when merchant reference ends with `-decline`).
- Jackson **3** `JsonMapper`; Boot **4.1.1**.

## Known limits

- No refunds, ledger, webhooks, outbox, Kafka domain events, risk, or analytics.
- Currency locked to `INR` in schema/check.
- Payment create/lifecycle is JWT-owner only (no secret-key payment API yet).
- No email verification; no ADMIN grant endpoint.
- Logout does not denylist access JWTs until expiry.
- Host Postgres often occupies **5432** → Compose uses **5433**.
- Surefire: name tests `*Test.java` (not `*IT.java`).

## Run & test (Windows)

```powershell
Copy-Item .env.example .env   # if needed
. .\scripts\set-java-home.ps1
docker compose -f infra/docker-compose.yml --env-file .env up -d
cd backend
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

- Ping: `http://localhost:8080/api/v1/system/ping`
- Swagger (local): `http://localhost:8080/swagger-ui.html`
- JWT PEMs auto-create under `backend/local-secrets/` (gitignored) on local profile
- Last verified: **45 tests passed**, 0 failed (`mvnw clean verify`, 2026-10-13)

## Current / next task

- **Current:** Phase 3 complete on `feature/phase-3-payments`. Do not start Phase 4 unless asked.
- **Next (Phase 4):** Refunds within capture limits; ledger postings; idempotent refunds; concurrency invariants. Still no real card processing.

## First actions in a new chat

1. Read `AGENTS.md` + this file.  
2. `git status` / confirm branch.  
3. Implement **Phase 4 only** unless the user expands scope.
