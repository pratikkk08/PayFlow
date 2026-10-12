# PayFlow — Agent Instructions

Persistent context for AI coding agents working on this repository. Read this file at the start of every session.

## What PayFlow Is

PayFlow is a **portfolio-grade, production-inspired fintech platform** demonstrating merchant accounts, secure API keys, **simulated** payment processing, refunds, signed webhooks, transaction analytics, fraud-risk rules, and event-driven processing.

**Payments are simulated.** Never process real card details or live customer funds. Never claim live payment capability.

## Current phase

Phase 1 foundation is in progress / complete when documented in `docs/DEVELOPMENT_LOG.md` and `docs/ROADMAP.md`. Treat those docs as the source of truth for what is implemented versus planned. Do not invent completed domain features (auth, payments, etc.).

## Durable development conventions (Phase 1+)

- Prefer **Spring Boot 4.x** versions that resolve on Maven Central (e.g. `4.1.1`, not `4.1.1.RELEASE` from Initializr metadata).
- Set session `JAVA_HOME` via `scripts/set-java-home.ps1` when unset; do not invent system-wide env changes.
- Local Compose Postgres is published on **host `5433`** (container `5432`) to avoid Windows conflicts with a native Postgres on `5432`.
- Kafka is an **optional Compose profile** (`kafka`). Spring Kafka auto-configuration stays **excluded** until domain messaging is required.
- Domain packages use `package-info.java` placeholders until that phase implements code — do not invent empty services/repos.
- Surefire picks up `*Test.java`; use Failsafe/`*IT.java` only when intentionally splitting integration suites.

## Technology stack

| Layer | Choice |
|-------|--------|
| Backend | Java 21, Spring Boot, Spring Security, Spring Data JPA, Maven Wrapper, Flyway, Bean Validation |
| Database | PostgreSQL (authoritative financial state) |
| Cache / rate limit | Redis |
| Messaging | Apache Kafka |
| Frontend | React, TypeScript, Vite, Tailwind CSS |
| Testing | JUnit 5, Mockito, Testcontainers; frontend unit tests; Playwright later |
| API docs | OpenAPI / Swagger |
| Infra | Docker Compose, GitHub Actions; cloud later |
| Observability | Structured logs, Spring Boot Actuator; metrics later |

## Architecture principles

1. **Modular monolith first** — clear packages/modules, not microservices until justified.
2. **Domain boundaries** — identity, merchants, payments, refunds, webhooks, risk, ledger, analytics, notifications.
3. **PostgreSQL is source of truth** for financial transaction state; Redis is cache/rate-limit only.
4. **Money** — integer minor units (preferred) or `BigDecimal` with documented currency rules; never `float`/`double`.
5. **Reliability** — idempotency, concurrency safety, transactional integrity, auditability; outbox for event publication when messaging lands.
6. **Payment state machine** — no arbitrary status transitions.
7. **Secrets** — never store raw API secrets or passwords in plaintext (hash/encrypt as appropriate).
8. **Kafka** — at-least-once consumers; idempotent handlers. Do not claim exactly-once delivery.
9. **Never commit** passwords, tokens, API secrets, real PII/financial data, or `.env` files.
10. **Sandbox only** — mock/simulated processors only.

## Coding standards

- Inspect existing files before creating or changing anything.
- Prefer officially maintained, stable, compatible dependencies.
- Avoid unnecessary abstractions, premature microservices, and speculative frameworks.
- Prefer clear package boundaries and explicit domain language.
- Keep Docker Compose resource usage practical for a ~16 GB Windows laptop.
- Prefer PowerShell-compatible commands on Windows.
- Match existing style when application code exists; do not drive-by refactor unrelated files.
- Do not add docs the user did not ask for beyond agreed project docs.

## Safety requirements

- No real card data, live payment gateways, or paid cloud resources without explicit approval.
- No destructive Git operations (`push --force`, hard reset, history rewrite) without explicit approval.
- Ask before installing system-wide software or making external cloud changes.
- Keep development local until deployment is explicitly requested.
- Never delete valuable files without approval.

## Testing policy

- Do not silently skip failing tests or fabricate command output.
- Prefer Testcontainers for integration tests involving PostgreSQL/Redis/Kafka when infrastructure is in place.
- Unit-test domain logic (state machine, money, idempotency) independently of infrastructure.
- Report real failures and blockers; fix or document them honestly.

## Preferred AI workflow

1. Read `AGENTS.md`, then the relevant docs under `docs/` for the current phase.
2. Inspect the workspace and Git status before editing.
3. Work incrementally with clear acceptance criteria.
4. Generate real files and run safe verification commands.
5. Update `docs/DEVELOPMENT_LOG.md` and `docs/DECISIONS.md` when meaningful work or decisions land.
6. Do not expand scope beyond the requested phase unless the user asks.
7. When finished, report created/modified files, verification results, blockers, and the recommended next phase task.

## Repository layout (target)

```
backend/          Spring Boot modular monolith
frontend/         React + TypeScript (Vite)
infra/            Docker Compose and infra assets
scripts/          Local helper scripts
docs/             Product, architecture, roadmap, decisions, logs
.github/workflows CI workflows (added when CI is introduced)
```

## Documentation index

| Document | Purpose |
|----------|---------|
| [docs/PRODUCT_REQUIREMENTS.md](docs/PRODUCT_REQUIREMENTS.md) | Goals, non-goals, MVP, future features |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Modules, entities, lifecycle, consistency |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Phased milestones and acceptance criteria |
| [docs/DEVELOPMENT_LOG.md](docs/DEVELOPMENT_LOG.md) | Chronological implementation log |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Architecture Decision Records |
| [docs/SETUP.md](docs/SETUP.md) | Local setup instructions |
| [docs/ENVIRONMENT_REPORT.md](docs/ENVIRONMENT_REPORT.md) | Verified tool versions and issues |

## Explicit non-goals for agents

- Do not build real payment processing.
- Do not push to remotes or create commits unless the user explicitly asks.
- Do not claim Phase N is complete without meeting that phase’s acceptance criteria.
