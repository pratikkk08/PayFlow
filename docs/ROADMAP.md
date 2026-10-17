# PayFlow — Development Roadmap

**Last updated:** 2026-10-13

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

- [x] Register/login with hashed passwords
- [x] Create merchant; issue API key (secret shown once, hash stored)
- [x] Revoke API key; revoked keys rejected
- [x] OpenAPI docs for auth/merchant/key endpoints
- [x] Tests for hashing and auth filter behavior
- [x] JWT + refresh rotation + ownership isolation

---

## Phase 3 — Payments state machine & simulated processor

**Objective:** Idempotent create payment + simulated processor + strict transitions.

### Acceptance criteria

- [x] Money as minor units + currency
- [x] Idempotency-Key semantics (replay + payload conflict)
- [x] State machine enforced in domain layer
- [x] Simulated success/decline paths
- [x] Optimistic locking under concurrent updates
- [x] Integration tests with Testcontainers Postgres

---

## Phase 4 — Refunds, ledger, hardening

**Objective:** Refunds within capture limits; ledger postings; concurrency safety.

### Acceptance criteria

- [x] Partial/full refunds; over-refund rejected
- [x] Ledger entries for capture/refund
- [x] Idempotent refunds
- [x] Domain tests for balance invariants

---

## Phase 5 — Outbox, Kafka, webhooks

**Objective:** Reliable event publication and signed webhook delivery with retries.

### Acceptance criteria

- [x] Outbox written in same TX as domain change
- [x] Publisher ships to Kafka; consumers idempotent
- [x] Webhook signature + delivery attempts + backoff
- [x] At-least-once documented; no exactly-once claims
- [x] Tests for signature verification and idempotent consumer

---

## Phase 6 — Risk rules & analytics

**Objective:** Configurable risk checks and merchant analytics summaries.

### Acceptance criteria

- [x] Rules can flag/block simulated payments (ALLOW/FLAG/REVIEW/BLOCK; REVIEW holds CREATED without approval workflow)
- [x] Analytics endpoints for volume/success (live Postgres aggregates; UTC reporting; sandbox metrics only)
- [x] Tests for rule evaluation, authorize integration, velocity concurrency, audit/security, analytics accuracy

**Status:** Complete on `feature/phase-6-risk-analytics` (Flyway V17–V19; 206 tests). Not a production-certified fraud system.

---

## Phase 7 — React dashboard

**Objective:** Operator UI for login, keys, transactions, refunds, webhooks.

### Acceptance criteria

- [x] Vite + React + TS + Tailwind app under `frontend/`
- [x] Core flows usable against local API
- [x] Basic frontend tests
- [x] No real card data collected

**Delivered (2026-10-17):** browser session auth; merchant selection; analytics dashboard; simulated payments/refunds with idempotency; API-key and webhook management; Vitest suite; live local smoke.

---

## Phase 8 — CI, polish, portfolio readiness

**Objective:** GitHub Actions, hardening, README demos, optional Playwright smoke.

### Acceptance criteria

- [x] CI workflow present (backend Testcontainers + frontend Node 24); remote GitHub Actions run **not verified** until push
- [x] Backend/frontend Docker images; deploy Compose with internal-only DB/Redis/backend
- [x] Production fail-closed secrets; Actuator health/liveness/readiness; Nginx health-only proxy
- [x] Playwright Chromium E2E (local Vite + Spring Boot); CI E2E deferred
- [x] Ops + backup/restore runbooks; disposable restore drill on isolated Compose project
- [x] Docs match implemented behavior; known limitations listed honestly
- [x] Resource guidance for Docker on ~16 GB machines

**Delivered (2026-10-17):** Tasks 8.1–8.8 on `feature/phase-8-devops` (local verification; no remote CI/push).

---

## Phase 9 — Recommended next (not started)

Portfolio polish and deployability: remote CI green on GitHub, TLS termination + Secure-cookie browser login verification, optional CI-hosted E2E, OpenTelemetry/metrics export, webhook secret rotation API, risk rule-management APIs — pick based on portfolio goals. **Do not claim Phase 9 complete without acceptance criteria.**

## Future (unscheduled)

Cloud deployment, chargeback simulation, advanced FX, richer RBAC — see product requirements “future features.”

## Dependency notes

- Phase 3 depends on 1–2.
- Phase 5 depends on 3 (events need payments).
- Phase 7 can prototype earlier against mocks, but acceptance requires live local API.
