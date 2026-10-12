# PayFlow — Development Roadmap

**Last updated:** 2026-10-12

Phases are sequential. Do not claim a phase complete until its acceptance criteria pass. Application features start at Phase 1.

---

## Phase 0 — Initialization & documentation

**Objective:** Establish repository, agent context, docs, ignore rules, and verify the local toolchain.

### Deliverables

- `AGENTS.md`, `README.md`, `.gitignore`, `.env.example`
- Docs: product, architecture, roadmap, decisions, development log, setup, environment report
- Directory scaffolding: `backend/`, `frontend/`, `infra/`, `scripts/`, `docs/`, `.github/workflows/`
- Git initialized locally (no remote/commit required unless requested)

### Acceptance criteria

- [x] Workspace inspected; tools checked and recorded honestly
- [x] Documentation reflects planned vs implemented state
- [x] No application backend/frontend implementation in this phase
- [x] No secrets committed; `.env.example` placeholders only
- [x] Empty dirs tracked with `.gitkeep` where needed

---

## Phase 1 — Backend skeleton & local infrastructure

**Objective:** Runnable empty Spring Boot app + Compose for Postgres, Redis, Kafka + Flyway baseline.

### Deliverables

- Maven Wrapper Spring Boot **4.1.1** project under `backend/`
- `infra/docker-compose.yml` with resource-conscious settings (Kafka optional profile)
- Flyway migration stub / baseline schema (minimal)
- Actuator health endpoint + public ping
- Application reads config from env / `.env` pattern documented in SETUP

### Acceptance criteria

- [x] `.\mvnw.cmd clean verify` succeeds for Phase 1 tests
- [x] `docker compose` starts Postgres + Redis; Kafka via `--profile kafka`
- [x] App connects to Postgres; Flyway runs cleanly
- [x] SETUP.md updated with verified commands
- [x] DEVELOPMENT_LOG entry added

---

## Phase 2 — Identity, merchants, API keys

**Objective:** Users, merchants, secure API key issuance and authentication filter.

### Acceptance criteria

- [ ] Register/login with hashed passwords
- [ ] Create merchant; issue API key (secret shown once, hash stored)
- [ ] Revoke API key; revoked keys rejected
- [ ] OpenAPI docs for auth/merchant/key endpoints
- [ ] Tests for hashing and auth filter behavior

---

## Phase 3 — Payments state machine & simulated processor

**Objective:** Idempotent create payment + simulated processor + strict transitions.

### Acceptance criteria

- [ ] Money as minor units + currency
- [ ] Idempotency-Key semantics (replay + payload conflict)
- [ ] State machine enforced in domain layer
- [ ] Simulated success/decline paths
- [ ] Optimistic locking under concurrent updates
- [ ] Integration tests with Testcontainers Postgres

---

## Phase 4 — Refunds, ledger, hardening

**Objective:** Refunds within capture limits; ledger postings; concurrency safety.

### Acceptance criteria

- [ ] Partial/full refunds; over-refund rejected
- [ ] Ledger entries for capture/refund
- [ ] Idempotent refunds
- [ ] Domain tests for balance invariants

---

## Phase 5 — Outbox, Kafka, webhooks

**Objective:** Reliable event publication and signed webhook delivery with retries.

### Acceptance criteria

- [ ] Outbox written in same TX as domain change
- [ ] Publisher ships to Kafka; consumers idempotent
- [ ] Webhook signature + delivery attempts + backoff
- [ ] At-least-once documented; no exactly-once claims
- [ ] Tests for signature verification and idempotent consumer

---

## Phase 6 — Risk rules & analytics

**Objective:** Configurable risk checks and merchant analytics summaries.

### Acceptance criteria

- [ ] Rules can flag/block simulated payments
- [ ] Analytics endpoints for volume/success (honest eventual consistency if projected)
- [ ] Tests for rule evaluation

---

## Phase 7 — React dashboard

**Objective:** Operator UI for login, keys, transactions, refunds, webhooks.

### Acceptance criteria

- [ ] Vite + React + TS + Tailwind app under `frontend/`
- [ ] Core flows usable against local API
- [ ] Basic frontend tests
- [ ] No real card data collected

---

## Phase 8 — CI, polish, portfolio readiness

**Objective:** GitHub Actions, hardening, README demos, optional Playwright smoke.

### Acceptance criteria

- [ ] CI runs backend tests on PR
- [ ] Docs match implemented behavior
- [ ] Known limitations listed honestly
- [ ] Resource guidance for Docker on 16 GB machines

---

## Future (unscheduled)

Cloud deployment, OpenTelemetry metrics/traces, chargeback simulation, advanced FX, richer RBAC — see product requirements “future features.”

## Dependency notes

- Phase 3 depends on 1–2.
- Phase 5 depends on 3 (events need payments).
- Phase 7 can prototype earlier against mocks, but acceptance requires live local API.
