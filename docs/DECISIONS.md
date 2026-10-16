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

- **Date:** 2026-10-12 (schema foundation 2026-10-16)
- **Status:** Accepted (Flyway V12 schema landed; publisher not yet wired)
- **Context:** Publishing to Kafka inside a DB transaction is unreliable; publishing after commit without a store risks lost events.
- **Decision:** Persist `outbox_events` in the same PostgreSQL transaction as state changes. Rows carry stable event id, aggregate type/id, event type + schema version, optional merchant id, JSONB payload, status (`PENDING`/`PROCESSING`/`PUBLISHED`/`FAILED`), attempt metadata, and `claim_expires_at` for crash-safe concurrent claiming. Future Kafka key = `aggregate_id` (ordering); consumers dedupe by event id. Exhausted `FAILED` rows remain inspectable and can be manually requeued to `PENDING`.
- **Consequences:** Extra table and later publisher; dual-write races avoided; at-least-once only (ADR-004).

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

---

## ADR-018: Refund capacity reservation without payment REFUNDED status

- **Date:** 2026-10-13
- **Status:** Accepted
- **Context:** Partial and full refunds must not overspend a captured payment; concurrent pending refunds must not race past remaining balance.
- **Decision:** Store refunds in their own table (`PENDING` / `SUCCEEDED` / `FAILED`). Do **not** add a payment-level `REFUNDED` status — a `CAPTURED` payment may have zero, partial, or full successful refunds. Refundable capacity = payment `amount_minor` − sum of refund `amount_minor` where status ∈ {`PENDING`, `SUCCEEDED`}. `PENDING` reserves capacity until terminal resolution; `FAILED` releases it. Enforce the sum invariant inside the same DB transaction as refund insert/update (service layer); schema CHECK constraints cover per-row positivity, status, and `completed_at` consistency only.
- **Consequences:** List/get payment APIs may later expose refunded totals as derived fields; over-refund attempts return a domain conflict/validation error without mutating ledger.

---

## ADR-019: Double-entry ledger with application-enforced balance

- **Date:** 2026-10-16
- **Status:** Accepted
- **Context:** Capture and refund money movements need an auditable, append-only posting model without real settlement. Multi-row debit/credit balance cannot be expressed as a single-row CHECK.
- **Decision:** Introduce `ledger_accounts`, `ledger_transactions`, and `ledger_entries` (Flyway V10). Account types: platform `PROCESSOR_CLEARING` (`merchant_id` NULL) and per-merchant `MERCHANT_PAYABLE`. Sources: `PAYMENT_CAPTURE` / `REFUND` with unique `(source_type, source_id)`. Currency locked to INR minor units. Conventions: capture = debit clearing / credit payable; successful refund = reverse. `LedgerPostingService` writes the transaction and balanced entry pair in the caller's DB transaction (propagation REQUIRED, never REQUIRES_NEW) and verifies Σ debits = Σ credits before commit. Schema CHECK covers per-row positivity and enums only — **the database does not already guarantee balanced postings**.
- **Consequences:** No public update/delete of entries; duplicate source posting is rejected by unique constraint + service check; posting failure rolls back the payment capture or refund in the same TX.

---

## ADR-020: Webhook secrets encrypted at rest; SSRF deferred to delivery

- **Date:** 2026-10-16
- **Status:** Accepted
- **Context:** Merchants need registered HTTPS webhook endpoints with per-endpoint signing secrets. Secrets must not be recoverable from list APIs or logs. Outbound delivery is SSRF-sensitive.
- **Decision:** Store secrets as AES-256-GCM ciphertext keyed by `WEBHOOK_SECRET_ENCRYPTION_KEY` (env). Return plaintext once on create. Registration validates URL shape only (HTTPS, no credentials/fragments/IP literals/blocked hostnames) and never contacts the URL. Future delivery must re-resolve DNS every attempt, block private/metadata addresses, refuse redirects, and re-load endpoints from Postgres. Secret rotation API is deferred; design is immediate replace + `secret_rotated_at` (optional grace window later).
- **Consequences:** Key loss requires re-issuing secrets; registration is not a complete SSRF control.

---

## ADR-021: Signed webhook delivery with IP-pinned HTTPS

- **Date:** 2026-10-16
- **Status:** Accepted
- **Context:** Merchant webhooks need at-least-once signed delivery without SSRF or blocking payment APIs.
- **Decision:** Enqueue `webhook_deliveries` in the Kafka consumer TX with `processed_events`. A separate worker claims via `SKIP LOCKED`, commits, then POSTs. Signature base = `{unixSeconds}.{eventId}.{body}` → `X-PayFlow-Signature: v1=<hex HMAC-SHA256>`. The transport opens TCP to the already validated IP only, then TLS with SNI and certificate checks for the original hostname, and sends that hostname in the HTTP `Host` header. Redirects are rejected. Retry 429/5xx/timeouts with backoff; other 4xx are permanent failures. Delivery disabled unless `payflow.webhooks.delivery.enabled=true`.
- **Consequences:** Ordinary HTTP clients that re-resolve hostnames after validation are unsuitable; custom transport required.

---

## ADR-022: Deterministic merchant-scoped risk on authorize

- **Date:** 2026-10-16
- **Status:** Accepted
- **Context:** Portfolio needs credible fraud-*shaped* controls without claiming real fraud detection. Concurrent authorizes must not race velocity thresholds.
- **Decision:** Evaluate merchant rules (`AMOUNT_THRESHOLD`, `PAYMENT_VELOCITY`, `REPEATED_FAILURES`) inside the authorize transaction after payment→merchant locks. Persist immutable `risk_assessments` + `risk_rule_matches` and durable `risk_authorization_attempts` (one per payment). Decision precedence BLOCK > REVIEW > FLAG > ALLOW; fixed score contributions clamped to `[0, 100]`. REVIEW holds `CREATED` (no approval API); BLOCK declines with `risk_blocked` and a single failure outbox event. Velocity counts peer authorize attempts; repeated failures use FAILED `updated_at`. Malformed BLOCK/REVIEW params fail closed; FLAG params skip. Injected `Clock` for windows/timestamps.
- **Consequences:** Not production fraud certification; multi-instance clock skew uncorrected; rule CRUD APIs deferred; REVIEW needs a future approval workflow to release holds.

---

## ADR-023: Live SQL merchant analytics (UTC, read-only)

- **Date:** 2026-10-16
- **Status:** Accepted
- **Context:** Dashboard needs volume/success summaries without Redis caches or claiming settled revenue.
- **Decision:** JWT OWNER read APIs for overview and daily series. Reporting timezone UTC; inclusive calendar dates → half-open instants; default 30 days; max 366. Status counts = current status among payments created in-window; event amounts use `captured_at` / SUCCEEDED `completed_at` / risk `evaluated_at`. Aggregate in PostgreSQL with bound parameters and V19 indexes; no entity hydration of full histories.
- **Consequences:** Status counts are not historical reconstructions; nets can be negative across periods; FAILED daily uses `updated_at` until a dedicated `failed_at` exists.
