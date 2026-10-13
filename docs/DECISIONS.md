# PayFlow — Architecture Decision Records

Format: short ADRs. Status values: `Proposed` | `Accepted` | `Superseded` | `Rejected`.

Only decisions that affect structure, stack, or safety belong here. Implementation details go in the development log.

---

## ADR-001: Modular monolith first

- **Date:** 2026-10-12
- **Status:** Accepted
- **Context:** Need clear domains without the operational cost of many services on a 16 GB laptop.
- **Decision:** Build one Spring Boot deployable with package/module boundaries. Extract microservices only if a concrete scaling or team boundary appears.
- **Consequences:** Simpler local Compose topology; careful package discipline required to avoid a “big ball of mud.”

---

## ADR-002: PostgreSQL as financial source of truth

- **Date:** 2026-10-12
- **Status:** Accepted
- **Context:** Redis is attractive for speed but unsafe as the system of record for money.
- **Decision:** All payment/refund/ledger authoritative state lives in PostgreSQL. Redis is limited to cache, rate limits, and ephemeral coordination.
- **Consequences:** Strong transactional consistency for money paths; cache invalidation must be correct but non-critical.

---

## ADR-003: Money as integer minor units

- **Date:** 2026-10-12
- **Status:** Accepted
- **Context:** Floating-point types cause rounding bugs in financial software.
- **Decision:** Store and compute amounts as integer **minor units** (e.g. cents) plus ISO 4217 currency codes. If `BigDecimal` appears at boundaries, convert explicitly; never use `float`/`double` for money.
- **Consequences:** Currencies with non-2 exponent need documented scaling; zero-decimal currencies must be handled explicitly when supported.

---

## ADR-004: At-least-once messaging with idempotent consumers

- **Date:** 2026-10-12
- **Status:** Accepted
- **Context:** Kafka does not provide end-to-end exactly-once for arbitrary external side effects (e.g. webhooks).
- **Decision:** Design for at-least-once delivery. Use transactional outbox for publish-after-commit. Consumers and webhook receivers must be idempotent (event id / delivery id).
- **Consequences:** Duplicate handling is mandatory; documentation must not claim exactly-once.

---

## ADR-005: Simulated payments only

- **Date:** 2026-10-12
- **Status:** Accepted
- **Context:** Real card processing implies PCI scope, legal risk, and paid vendor accounts.
- **Decision:** Implement a sandbox processor port with deterministic/configurable outcomes. Never store PAN/CVV; never connect live acquirers in this project unless a future ADR explicitly reverses this (unlikely).
- **Consequences:** Portfolio remains honest and runnable offline; some “realism” is mocked.

---

## ADR-006: API keys hashed at rest; secrets via env

- **Date:** 2026-10-12
- **Status:** Accepted
- **Context:** Plaintext API secrets in DB or Git are unacceptable.
- **Decision:** Show API secret once at creation; persist one-way hash + prefix. Application secrets and DB passwords come from environment variables / `.env` (gitignored). Provide `.env.example` with placeholders only.
- **Consequences:** Lost secrets require rotation; debugging needs prefixes and hashes, not raw keys in logs.

---

## ADR-007: Payment state machine over free-form status updates

- **Date:** 2026-10-12
- **Status:** Accepted
- **Context:** Free-form status columns invite illegal transitions and race bugs.
- **Decision:** Encode allowed transitions in domain logic; persist status as an enum/string constrained by that logic; use optimistic locking on payment rows.
- **Consequences:** Slightly more code upfront; far fewer integrity incidents. Exact MVP status set finalized in Phase 3 (single-step vs auth/capture).

---

## ADR-008: Transactional outbox for domain events

- **Date:** 2026-10-12
- **Status:** Accepted (implementation in Phase 5)
- **Context:** Publishing to Kafka inside a DB transaction is unreliable; publishing after commit without a store risks lost events.
- **Decision:** Write outbox rows in the same PostgreSQL transaction as state changes; a publisher process relays to Kafka.
- **Consequences:** Extra table and publisher; dual-write races avoided.

---

## ADR-009: Maven Wrapper; no global Maven required

- **Date:** 2026-10-12
- **Status:** Accepted
- **Context:** Host `mvn` may be absent (confirmed on the Phase 0 machine).
- **Decision:** Use Maven Wrapper (`mvnw` / `mvnw.cmd`) in `backend/` once the project is generated.
- **Consequences:** Reproducible builds; Java 21 still required on the host or build container.

---

## ADR-010: Deferred frontend until APIs stabilize

- **Date:** 2026-10-12
- **Status:** Accepted
- **Context:** Building UI before payment/idempotency APIs wastes rework.
- **Decision:** Phase 7 delivers the React dashboard after core backend phases. `frontend/` directory reserved earlier.
- **Consequences:** Longer period without UI demos; APIs must be documented via OpenAPI for interim testing.

---

## ADR-011: Spring Boot 4.1.1 on Java 21

- **Date:** 2026-10-12
- **Status:** Accepted
- **Context:** Spring Initializr currently defaults to Boot 4.x; Maven Central publishes versions as `4.1.1` (no `.RELEASE` suffix). Boot 3.x remains available but Initializr’s generated starters (`webmvc`, modular test jars) target Boot 4.
- **Decision:** Use **Spring Boot 4.1.1** with Java 21 for `payflow-backend`, pinning the parent POM to the Central-resolvable version string.
- **Consequences:** Newer package names (e.g. `org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration`, `spring-boot-starter-webmvc`). Agents must not assume Boot 2/3 artifact IDs blindly.

---

## ADR-012: Compose Postgres on host 5433; Kafka optional profile

- **Date:** 2026-10-12
- **Status:** Accepted
- **Context:** Host port `5432` was already bound by a native PostgreSQL process on the Phase 1 machine. Kafka is memory-heavy on a Docker Desktop VM (~7 GB allocated).
- **Decision:** Publish Compose Postgres as `127.0.0.1:5433→5432`. Keep Redis on `6379`. Ship Kafka as Compose profile `kafka` (KRaft, `apache/kafka:3.9.0`, host `9094`). Exclude Spring `KafkaAutoConfiguration` until messaging phases need it; still verify the broker via Compose when the profile is started.
- **Consequences:** `.env` / JDBC defaults use port `5433`. Developers with a free `5432` may remap, but docs standardize on `5433`.

---

## ADR-013: RS256 JWT access tokens + opaque hashed refresh tokens

- **Date:** 2026-10-12
- **Status:** Accepted
- **Context:** Need short-lived API auth without storing session state for every request, plus revocable longer-lived sessions.
- **Decision:** Issue RS256 access JWTs (issuer/audience/`token_use` validated via OAuth2 Resource Server). Refresh tokens are opaque, stored as SHA-256 hashes with family-based rotation and reuse revocation. RSA PEM keys live outside Git.
- **Consequences:** Logout cannot instantly kill access JWTs without a denylist; refresh reuse detection requires DB locks.

---

## ADR-014: HMAC-SHA256 peppered hashes for high-entropy API keys

- **Date:** 2026-10-12
- **Status:** Accepted
- **Context:** API secrets are high-entropy random strings; bcrypt is unnecessary and slower for this use case.
- **Decision:** Store `HMAC-SHA256(API_KEY_HASH_PEPPER, rawSecret)` for secret keys; return raw secret once; classify `sk_test_` vs JWT Bearer formats explicitly.
- **Consequences:** Pepper rotation invalidates all secret key hashes; pepper must be treated as a critical secret.

---

## ADR-015: Auth rate limiting fails closed without Redis

- **Date:** 2026-10-12
- **Status:** Accepted
- **Context:** Brute-force protection on register/login/refresh should not silently disappear if Redis is down.
- **Decision:** Redis sliding counters for auth endpoints; if Redis is unavailable, return **503** rather than allowing unlimited attempts.
- **Consequences:** Auth endpoints depend on Redis availability in addition to Postgres.

---

## ADR-016: Payment create idempotency in PostgreSQL

- **Date:** 2026-10-13
- **Status:** Accepted
- **Context:** Concurrent `POST /payments` retries must not double-charge; Redis must not be SoT for money paths.
- **Decision:** Require `Idempotency-Key` scoped by `(merchant_id, operation, key)`. Reserve with `INSERT … ON CONFLICT DO NOTHING` against a unique constraint; create the payment and mark the record `COMPLETED` with fingerprint + response JSON in the **same** transaction. Replays return HTTP 201 with the stored body; fingerprint mismatch returns 409. Retention: `expires_at = created_at + 72h`; cleanup (future) may remove only expired `COMPLETED` rows — never active `STARTED` leases.
- **Consequences:** Concurrent callers serialize on the unique index; aborted creates roll back the reservation; no Redis lock dependency.

---

## ADR-017: Two-step authorize/capture with CANCELLED

- **Date:** 2026-10-13
- **Status:** Accepted
- **Context:** Architecture allowed either single-step capture or auth/capture split; Phase 3 needed a concrete machine.
- **Decision:** Use two-step flow: `CREATED` → `AUTHORIZED` → `CAPTURED`, with simulated decline `CREATED` → `FAILED`. Abandonment uses `CANCELLED` from `CREATED` or `AUTHORIZED` (no `VOIDED` status). Transitions enforced on `PaymentEntity`; concurrent updates use `@Version` plus `SELECT … FOR UPDATE`.
- **Consequences:** Clients must call authorize then capture; cancel is the release path. Refunds (Phase 4) attach only to `CAPTURED`.
