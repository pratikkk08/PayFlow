# PayFlow — Agent Instructions

Persistent context for AI coding agents. Read this at the start of every session. For session-specific progress, read [docs/AI_HANDOFF.md](docs/AI_HANDOFF.md).

## What PayFlow Is

Portfolio-grade **simulated** fintech platform: merchants, API keys, payments, refunds, webhooks, risk, analytics. **Never** process real cards or live funds. Never claim live payment capability.

## Durable conventions

- **Stack:** Java 21, Spring Boot **4.1.1** (Maven Central version string — not `4.1.1.RELEASE`), modular monolith.
- **Jackson 3:** inject `tools.jackson.databind.json.JsonMapper` — not `com.fasterxml.jackson.databind.ObjectMapper`.
- **Money:** integer minor units + ISO currency; never `float`/`double`.
- **Postgres is SoT** for financial state; Redis is cache/rate-limit only.
- **Ports:** Postgres host **5433**, Redis **6379**, Kafka (optional Compose profile) **9094**, app **8080**.
- **Kafka:** Spring `KafkaAutoConfiguration` stays excluded until messaging phases; design for at-least-once + idempotent consumers.
- **Auth:** classify Bearer JWT vs `sk_test_`/`pk_test_` — never conflate. JWT PEMs in `backend/local-secrets/` (gitignored). API-key pepper via env.
- **Auth rate limit:** fail closed if Redis is down.
- **Schema:** Flyway owns DDL; Hibernate `ddl-auto=validate`.
- **Tests:** Surefire discovers `*Test.java` (not `*IT.java` unless Failsafe is configured). Prefer Testcontainers for Postgres/Redis.
- **OS:** PowerShell-compatible commands; keep Compose light for ~16 GB RAM.
- **Secrets:** never commit `.env`, PEMs, tokens, raw API secrets, or real PII.

## Architecture principles

1. Modular monolith with domain packages: identity, merchants, payments, refunds, webhooks, risk, ledger, analytics, notifications.
2. Payment state machine — no arbitrary status transitions.
3. Idempotency, optimistic locking, transactional integrity; outbox when events land.
4. Sandbox/mock processors only.
5. Inspect existing code before editing; match style; no drive-by refactors.

## Safety

- No real card data, live gateways, or paid cloud without approval.
- No force-push, hard reset, or history rewrite without approval.
- Do not push or commit unless the user asks (except agreed doc checkpoints).
- Do not claim Phase N complete without meeting its acceptance criteria.

## Workflow

1. Read `AGENTS.md` + `docs/AI_HANDOFF.md`, then phase docs under `docs/`.
2. Check Git status; work on the phase branch.
3. Implement incrementally; run real tests; update `DEVELOPMENT_LOG.md` / `DECISIONS.md` when meaningful.
4. Report files changed, verification results, blockers, and next task.

## Docs index

| Doc | Purpose |
|-----|---------|
| [docs/AI_HANDOFF.md](docs/AI_HANDOFF.md) | Current progress handoff (start here each new chat) |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Phases and acceptance criteria |
| [docs/DEVELOPMENT_LOG.md](docs/DEVELOPMENT_LOG.md) | Chronological log |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Target design |
| [docs/DECISIONS.md](docs/DECISIONS.md) | ADRs |
| [docs/SETUP.md](docs/SETUP.md) | How to run locally |
| [docs/DEPLOYMENT_PLAN.md](docs/DEPLOYMENT_PLAN.md) | ₹0 hosting plan (Task 9.3); Oracle Always Free A1 |
| [docs/API_AUTHENTICATION.md](docs/API_AUTHENTICATION.md) | JWT / API-key auth |
| [docs/SECURITY.md](docs/SECURITY.md) | Security controls and limits |
