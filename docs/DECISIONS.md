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
