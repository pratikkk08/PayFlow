# PayFlow

Portfolio-grade, production-inspired **fintech sandbox** for merchant accounts, secure API keys, simulated payment processing, refunds, signed webhooks, transaction analytics, and fraud-risk rules.

> **Simulation only.** PayFlow never processes real card details or live customer funds.

## Why this project exists

PayFlow demonstrates serious backend engineering and system design: modular domain boundaries, money-safe arithmetic, idempotent APIs, a payment state machine, transactional integrity, and eventually outbox-based event publication with reliable webhook delivery.

It is intentionally **not** a thin CRUD demo.

## Current status

**Phase 0 — project initialization.** Documentation, repository layout, environment validation, and agent instructions are in place. The Spring Boot backend and React frontend are **not built yet**.

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
| Tests | JUnit 5, Mockito, Testcontainers; frontend tests; Playwright later |
| API docs | OpenAPI / Swagger |
| Infra | Docker Compose, GitHub Actions (later), cloud deployment (later) |

## Architecture approach

PayFlow starts as a **modular monolith**: one deployable Spring Boot application with clear internal domain packages (identity, merchants, payments, refunds, webhooks, risk, ledger, analytics, notifications).

- **PostgreSQL** is the authoritative store for financial transaction state.
- **Redis** supports caching and rate limiting — not financial truth.
- **Kafka** carries domain events; consumers must be idempotent under at-least-once delivery.
- An **outbox** pattern is planned for reliable event publication after DB commits.

Details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Roadmap (summary)

| Phase | Focus |
|-------|--------|
| 0 | Docs, repo layout, environment validation ← **current** |
| 1 | Backend skeleton, Docker Compose (Postgres/Redis/Kafka), Flyway baseline |
| 2 | Identity & merchants, API keys |
| 3 | Payments state machine & simulated processor |
| 4 | Refunds, ledger, idempotency hardening |
| 5 | Webhooks, outbox, Kafka consumers |
| 6 | Risk rules & analytics |
| 7 | React dashboard |
| 8 | CI, hardening, portfolio polish |

Full plan: [docs/ROADMAP.md](docs/ROADMAP.md). Product scope: [docs/PRODUCT_REQUIREMENTS.md](docs/PRODUCT_REQUIREMENTS.md).

## Repository layout

```
backend/              Spring Boot application (Phase 1+)
frontend/             React + TypeScript client (later)
infra/                Docker Compose and infrastructure assets
scripts/              Local helper scripts
docs/                 Requirements, architecture, roadmap, decisions, logs
.github/workflows/    CI workflows (when introduced)
AGENTS.md             Persistent instructions for AI coding agents
```

## Local development (intentions)

1. Copy `.env.example` → `.env` and set local-only placeholders.
2. Start infrastructure with Docker Compose from `infra/` (Compose file arrives in Phase 1).
3. Run the backend via Maven Wrapper once the Spring Boot project exists.
4. Run the frontend via `npm` once the Vite app exists.

Setup details and prerequisites: [docs/SETUP.md](docs/SETUP.md).  
Verified tool versions on this machine: [docs/ENVIRONMENT_REPORT.md](docs/ENVIRONMENT_REPORT.md).

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
| [docs/ENVIRONMENT_REPORT.md](docs/ENVIRONMENT_REPORT.md) | Tool verification results |

## Safety

- Never commit `.env`, secrets, tokens, or real customer financial data.
- Use sandbox/mock processing only.
- Do not create paid cloud resources without an explicit decision.

## License

Private portfolio project unless a license file is added later.
