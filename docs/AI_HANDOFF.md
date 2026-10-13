# PayFlow — AI Handoff

**Updated:** 2026-10-13  
**Purpose:** Minimal context for a new Cursor Agent chat. Prefer this + `AGENTS.md` over re-reading the whole repo history.

## Objective

Simulated payment platform (portfolio): merchants, secure API keys, payments/refunds/webhooks later. **Sandbox only — no real cards or live funds.**

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
| Branch | `feature/phase-2-identity-merchants` |
| Latest commit | `46d2f1a` — `feat: implement secure identity and merchant API access` |
| Also | `main` @ `feb8bba` (Phase 0); `feature/phase-1-foundation` @ `75e8b55` |
| Remote | None configured; do not push unless asked |

## Completed phases

| Phase | Status | Highlights |
|-------|--------|------------|
| 0 | Done | Docs, layout, `.gitignore`, `.env.example` |
| 1 | Done | Boot app, Compose, Flyway V1, ping, deny-by-default security |
| 2 | Done | Identity, merchants, API keys, JWT/refresh, OpenAPI, rate limit |

**Not started:** Phase 3+ (payments, refunds, ledger, webhooks/outbox, risk, analytics, React UI, CI).

## Migrations

- `V1__baseline.sql` — `payflow_schema_meta`
- `V2__identity_merchants_api_keys.sql` — users, roles, refresh families/tokens, merchants, memberships, api_keys, security_audit_events  
- Runtime marker: `payflow_schema_meta.schema_phase = phase-2`

## APIs (live)

Public: `POST /api/v1/auth/{register,login,refresh,logout}`, `GET /api/v1/system/ping`, actuator health/info, Swagger (local).

JWT user: `GET /api/v1/users/me`; merchants CRUD; API key create/list/revoke.

Secret API key (`Bearer sk_test_…`): `GET /api/v1/integrations/me`.

Publishable `pk_test_…` cannot authenticate. Cross-merchant access → **404**.

## Key decisions (see `DECISIONS.md`)

- Modular monolith; Postgres SoT; money as minor units later.
- Boot **4.1.1**; Jackson **3** `JsonMapper`.
- RS256 access JWT + hashed refresh rotation/reuse revoke (ADR-013).
- API secrets: HMAC-SHA256(pepper, raw) (ADR-014).
- Auth rate limit fail-closed without Redis (ADR-015).
- Kafka Compose optional; Spring Kafka wiring deferred.

## Known limits

- No email verification; no ADMIN grant endpoint; no payments yet.
- Logout does not denylist access JWTs until expiry.
- JSON tokens for API/testing — browser cookie/CSRF strategy deferred.
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
- Last verified: **12 tests passed** (`mvnw clean verify`); Phase 2 E2E against Compose succeeded

## Current / next task

- **Current:** Phase 2 complete on `feature/phase-2-identity-merchants` (`46d2f1a`). Handoff docs prepared.
- **Next (Phase 3):** Simulated payment state machine — minor units, idempotency keys, authorize/capture/fail transitions, optimistic locking, Testcontainers. Still no real card processing. Branch from latest Phase 2 commit when starting.

## First actions in a new chat

1. Read `AGENTS.md` + this file.  
2. `git status` / confirm branch.  
3. Implement **Phase 3 only** unless the user expands scope.
