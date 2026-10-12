# PayFlow — Development Log

Chronological record of meaningful work. Newest entries at the top.

---

## 2026-10-12 — Phase 1: Backend foundation & local infrastructure

### Done

- Created Phase 0 Git baseline commit on `main`, then branched `feature/phase-1-foundation`.
- Generated Spring Boot **4.1.1** / Java 21 Maven project under `backend/` (Maven Wrapper).
- Configured profiles `local`, `test`, `prod`; Flyway owns schema; Hibernate `ddl-auto=validate`.
- Added modular package placeholders (`identity`, `merchants`, `payments`, …) via `package-info.java`.
- Implemented public `GET /api/v1/system/ping` and Actuator health/info; Security denies other APIs with **401**.
- Excluded default `UserDetailsService` and `KafkaAutoConfiguration` for Phase 1.
- Added Flyway `V1__baseline.sql` (`payflow_schema_meta` marker only).
- Added `infra/docker-compose.yml`: Postgres 16.6, Redis 7.4.2, optional Kafka 3.9.0 KRaft profile.
- Mapped Postgres to host **5433** (host `5432` already occupied).
- Tests: context + DB, Flyway, ping JSON, security denial — **`.\mvnw.cmd clean verify` BUILD SUCCESS (7 tests)**.
- Verified live: ping UP, actuator health UP, Redis PONG, Flyway v1 applied, Kafka healthy on profile, host `9094` open.

### Versions (pinned / observed)

- Spring Boot parent: `4.1.1`
- Java: Temurin `21.0.12.1`
- Maven Wrapper: Apache Maven `3.10.0`
- Postgres image: `postgres:16.6-alpine`
- Redis image: `redis:7.4.2-alpine`
- Kafka image: `apache/kafka:3.9.0`

### Not done (later phases)

- Identity, merchants, API keys, payments, refunds, webhooks, frontend, CI workflows

### Notes

- Spring Initializr returned `4.1.1.RELEASE`; Maven Central requires `4.1.1`.
- Kafka first boot failed until `CLUSTER_ID` was a valid base64 UUID and listeners matched Apache image expectations.
- Docker Desktop VM memory observed ~7.4 GiB — Kafka kept optional.

### Next

Phase 2: identity, merchants, hashed API keys, authenticated merchant APIs.

---

## 2026-10-12 — Phase 0: Initialization

### Done

- Inspected workspace at `C:\Projects\PayFlow` (empty; no prior application code).
- Initialized local Git repository on branch `main` (no remote).
- Verified toolchain from PowerShell: Java/javac, Node/npm, Git, Docker CLI, Docker Compose, WSL status.
- Created persistent agent instructions: `AGENTS.md`.
- Created project docs: product requirements, architecture, roadmap, decisions, setup, environment report.
- Created `README.md`, `.gitignore`, `.env.example` (placeholders only).
- Scaffolded directories: `backend/`, `frontend/`, `infra/`, `scripts/`, `docs/`, `.github/workflows/`.

### Not done (intentionally out of scope)

- Spring Boot application code (moved to Phase 1)
- React frontend
- Docker Compose stack files (moved to Phase 1)
- CI workflows with real jobs
