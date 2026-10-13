# PayFlow — Development Log

Chronological record of meaningful work. Newest entries at the top.

---

## 2026-10-13 — Docs: AI development handoff

### Done

- Condensed `AGENTS.md` to durable conventions; pointed agents at `docs/AI_HANDOFF.md`.
- Added `docs/AI_HANDOFF.md` summarizing completed Phases 0–2, Git tip (`46d2f1a` on `feature/phase-2-identity-merchants`), APIs, migrations, run/test commands, and Phase 3 next step.
- No application logic changes.

### Next

Phase 3: simulated payments state machine + idempotency (new branch from Phase 2 tip).

---

## 2026-10-12 — Phase 2: Identity, merchants & secure API keys

### Done

- Branched `feature/phase-2-identity-merchants` from Phase 1 (`75e8b55`).
- Added Flyway `V2__identity_merchants_api_keys.sql` (users, roles, refresh sessions, merchants, memberships, api_keys, audit).
- Implemented register/login, RS256 JWT access tokens, hashed refresh rotation/reuse detection/logout.
- Implemented merchant CRUD (owner-scoped), API key create/list/revoke, integration diagnostic auth.
- Distinguishes JWT vs `sk_test_` Bearer credentials; publishable keys rejected for auth.
- Redis rate limiting on auth endpoints (fail-closed); OpenAPI/springdoc 3.1.1; JSON error model.
- Tests: **12 passed** via `.\mvnw.cmd clean verify`.
- Live E2E against Compose: register → login → me → merchant → API key → integration → revoke → reject; no secret in logs.

### Dependencies added

- `spring-boot-starter-oauth2-resource-server` (Boot managed)
- `springdoc-openapi-starter-webmvc-ui` **3.1.1**

### Not done

- Payments, refunds, email verification, ADMIN workflows, frontend, Kafka domain events

### Next

Phase 3: payment state machine & simulated processor with idempotency.

---

## 2026-10-12 — Phase 1: Backend foundation & local infrastructure

### Done

- Spring Boot **4.1.1** / Java 21 Maven project, Compose Postgres/Redis/optional Kafka, Flyway V1, ping + security deny-by-default.
- Commit `75e8b55` on `feature/phase-1-foundation`.

---

## 2026-10-12 — Phase 0: Initialization

### Done

- Docs, AGENTS.md, scaffolding, environment report. Commit `feb8bba` on `main`.
