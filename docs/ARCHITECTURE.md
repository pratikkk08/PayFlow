# PayFlow — Architecture

**Status:** Modular monolith with Phases 1–7 implemented (backend domains + React operator console).  
**Last updated:** 2026-10-17

## 1. Overview

PayFlow is a **modular monolith**: one Spring Boot deployable with package-level domain boundaries. Shared infrastructure (JPA, security filters, outbox publisher) supports domains without splitting into networked microservices prematurely.

```
┌─────────────────────────────────────────────────────────────┐
│                     PayFlow Monolith                        │
│  identity │ merchants │ payments │ refunds │ webhooks       │
│  risk │ ledger │ analytics │ notifications │ shared/kernel  │
└────────────┬──────────────────┬──────────────────┬──────────┘
             │                  │                  │
        PostgreSQL            Redis              Kafka
     (source of truth)   (cache/rate limit)   (events)
```

Frontend (React) talks to HTTP APIs. Merchants also call APIs with API keys.

## 2. Backend module boundaries

| Module | Responsibility | Owns (conceptually) |
|--------|----------------|---------------------|
| `identity` | Users, credentials, dashboard sessions/JWT | `User`, credentials |
| `merchants` | Merchant profiles, membership | `Merchant` |
| `apikeys` (or under merchants) | Issue/revoke keys, hash verification | `ApiKey` |
| `payments` | Payment commands, state machine, processor port | `Payment`, `PaymentAttempt` |
| `refunds` | Refund commands & state | `Refund` |
| `ledger` | Postings / money movement audit | `LedgerEntry` |
| `webhooks` | Endpoints, signatures, delivery attempts | `WebhookEndpoint`, `WebhookDelivery` |
| `risk` | Rules evaluation before/during payment | `RiskRule`, `RiskDecision` |
| `analytics` | Aggregations / read models | projections or query services |
| `notifications` | Email/log stubs for notable events | optional |
| `outbox` / messaging | Transactional outbox, Kafka publish/consume | `OutboxMessage` |
| `shared` | Money types, idempotency, errors, time | cross-cutting primitives |

**Rule:** Domain modules do not reach into each other’s persistence internals. Prefer application services and published domain events.

## 3. Core entities and preliminary relationships

```
User 1──* MerchantMembership *──1 Merchant
Merchant 1──* ApiKey
Merchant 1──* Payment
Payment 1──* Refund
Payment 1──* LedgerEntry
Merchant 1──* WebhookEndpoint
WebhookEndpoint 1──* WebhookDelivery
Payment *──* IdempotencyRecord (scoped by merchant + key)
Merchant 1──* RiskRule
```

### Preliminary fields (conceptual)

**Money:** `amountMinor` (`bigint`), `currency` (`char(3)`). Never floating point.

**Payment:** `id`, `merchantId`, `amountMinor`, `currency`, `status`, `processorRef`, `failureCode`, `authorizedAt`, `capturedAt`, `version` (optimistic lock), `createdAt`, `updatedAt`.

**Refund:** `id`, `paymentId`, `merchantId`, `amountMinor`, `currency`, `status`, `idempotencyKey`, timestamps.

**ApiKey:** `id`, `merchantId`, `keyPrefix`, `secretHash`, `status`, `lastUsedAt`, `revokedAt`.

**IdempotencyRecord:** `merchantId`, `key`, `requestHash`, `responseRef` / stored response, `createdAt`, unique `(merchantId, key)`.

**OutboxMessage:** `id`, `aggregateType`, `aggregateId`, `eventType`, `payload`, `createdAt`, `publishedAt`.

Exact schema will be introduced via Flyway in implementation phases.

## 4. Payment lifecycle (state machine)

### Proposed statuses

| Status | Meaning |
|--------|---------|
| `CREATED` | Persisted, not yet authorized |
| `AUTHORIZED` | Hold simulated successfully (if auth/capture split used) |
| `CAPTURED` | Funds captured (success terminal for happy path charge) |
| `VOIDED` | Authorization released |
| `FAILED` | Terminal failure (decline/validation/risk) |
| `CANCELLED` | Abandoned before auth (if needed) |

**Phase 3 choice:** two-step authorize/capture (not single-step). Cancellation uses status `CANCELLED` (from `CREATED` or `AUTHORIZED`). Status `VOIDED` is not used. See [DECISIONS.md](DECISIONS.md) ADR-017.

### Allowed transitions (implemented)

```
CREATED → AUTHORIZED → CAPTURED
CREATED → FAILED
CREATED → CANCELLED
AUTHORIZED → CANCELLED
```

Refunds are a separate aggregate against `CAPTURED` payments only (no payment-level `REFUNDED` status):

```
Refund: PENDING → SUCCEEDED | FAILED
```

Illegal transitions throw a domain error; persistence uses optimistic locking (`version`) plus payment-row locks on refund create to prevent lost updates under concurrency.

### Failure scenarios (engineering)

| Scenario | Handling |
|----------|----------|
| Validation error | 400; no payment row or row stays non-authorized |
| Auth failure | 401/403; no side effects |
| Idempotent replay (same payload) | 200 with original resource |
| Idempotent replay (different payload) | 409 Conflict |
| Risk block | Payment → `FAILED` with risk code; optional webhook |
| Processor decline | `FAILED` + decline code |
| Processor timeout | Leave retryable state or use attempt records; client retries with same idempotency key |
| Concurrent capture/void | Optimistic lock; one winner |
| Over-refund | Reject; no ledger post |

## 5. API authentication strategy

| Surface | Mechanism |
|---------|-----------|
| Merchant HTTP API | `Authorization: Bearer pk_live_...` or `X-Api-Key`; lookup by prefix, verify hash |
| Dashboard | Session cookie or JWT after password login (bcrypt/argon2 hashed passwords) |
| Actuator | Restrict to local/admin profile; never expose unchecked in production profiles |

**API key storage:** generate high-entropy secret, display once, store `HMAC`/`bcrypt`/`argon2` hash + public prefix. Revocation is immediate via status flag.

**Rate limiting:** Redis-backed counters per API key / IP (introduced with Redis wiring).

## 6. Idempotency and concurrency

1. Client sends `Idempotency-Key` on create payment / create refund.
2. Server begins transaction; inserts idempotency row unique on `(merchant_id, operation, key)` (payments) or `(merchant_id, key)` for refunds.
3. On conflict, load existing record; compare request fingerprint; return stored outcome or 409.
4. Payments/refunds use `@Version` optimistic locking for state transitions; refund create locks the parent payment (`SELECT … FOR UPDATE`) before capacity checks.
5. Refundable capacity = payment `amount_minor` − sum of refunds in `PENDING` or `SUCCEEDED`, enforced in the same TX as refund insert/update. `FAILED` releases reserved capacity.

### Ledger posting (Phase 4)

| Event | Debit | Credit |
|-------|-------|--------|
| Successful capture | `PROCESSOR_CLEARING` | `MERCHANT_PAYABLE` |
| Successful refund | `MERCHANT_PAYABLE` | `PROCESSOR_CLEARING` |

Postings are append-only double-entry rows (`ledger_transactions` + two `ledger_entries`) in the **same** PostgreSQL transaction as the domain change. Unique `(source_type, source_id)` prevents duplicate postings. Failed capture/refund attempts create no ledger rows. Amounts are positive INR minor units only.

## 7. Data consistency approach

| Concern | Approach |
|---------|----------|
| Financial truth | PostgreSQL only |
| Cache | Redis optional; always safe to invalidate |
| Multi-row money updates | Single DB transaction |
| Domain events after commit | Transactional **outbox** table written in same TX as state change |
| Kafka publish | Async poller/publisher reads outbox; marks published |
| Consumers | Idempotent by event id / aggregate version |
| Read models (analytics) | Eventually consistent projections acceptable if labeled as such |

**Do not** update payment status in Redis and treat it as authoritative.

## 8. Event handling and webhook delivery

Topic `payflow.domain-events.v1`. Dead-letter topic `payflow.domain-events.v1.dlt`. Consumer group `payflow-domain-events-internal`. Consumer identity `payflow-domain-events-recorder`.

1. Payment and refund services insert `outbox_events` in the **same** PostgreSQL transaction as the financial change. A failed outbox write rolls the money change back.
2. `OutboxPublisher` claims with `FOR UPDATE SKIP LOCKED` and a claim token, commits, then waits for a Kafka ACK (`acks=all`) before `PUBLISHED`. Stale tokens cannot finalize the row. Expired `PROCESSING` leases are reclaimable. Exhausted attempts become `FAILED`.
3. `DomainEventKafkaListener` validates the envelope. Malformed events go to the DLT without a processed marker. The handler inserts `processed_events` and matching `webhook_deliveries` in one transaction. Redelivery of the same event id is a no-op.
4. `WebhookDeliveryWorker` (off unless `payflow.webhooks.delivery.enabled=true`) claims a job, commits, then POSTs. HMAC base is `{unixSeconds}.{eventId}.{body}` with header `X-PayFlow-Signature: v1=<hex>`. The socket connects only to the DNS-validated IP. TLS uses the JVM trust store, SNI, and hostname checks for the original host, and the HTTP `Host` header is that hostname. Redirects are permanent failures. 429/5xx/timeouts retry with backoff.

**Delivery guarantee:** at-least-once. Receivers must verify the signature and dedupe by event id. PayFlow does not claim exactly-once webhook delivery.

Registration APIs: `POST/GET/PATCH/DELETE /api/v1/merchants/{merchantId}/webhooks`. The signing secret is returned once.

Operations: Micrometer gauges for pending/failed/expired outbox and webhook rows, delivery success/failure counters, and publish/delivery latency. `GET /api/v1/internal/events/health` and `/actuator/metrics` require JWT role `ADMIN`. `EventRecoveryService` can requeue terminal `FAILED` rows without an HTTP mutation API.

## 9. Security considerations

- No PAN/CVV storage; mock `pm_tok_...` or last4/brand only.
- Secrets in env / secret store; `.env` gitignored.
- HTTPS in deployed environments (local HTTP acceptable).
- Principle of least privilege for DB users when Compose hardens.
- Parameterized queries via JPA; validate all inputs (Bean Validation).
- Structured logs without secrets or full API keys.
- CORS locked to known frontend origins per environment.
- Dependency updates via Dependabot/Renovate later.

## 10. Testing strategy

| Layer | Tools | Focus |
|-------|-------|-------|
| Unit | JUnit 5, Mockito | State machine, money, idempotency hashing, signature |
| Integration | Testcontainers (Postgres, Redis, Kafka as needed) | Repositories, Flyway, API slices |
| API | MockMvc / WebTestClient | Auth, validation, status codes |
| Frontend | Vitest + React Testing Library | Auth, merchants, analytics, payments/refunds, API keys, webhooks |
| E2E | Playwright (later) | Critical merchant journeys |
| Manual | Compose stack | Smoke on Windows + Docker |

CI runs unit + integration on PR once workflows exist. Do not ignore failing tests.

## 11. Observability

- Structured logs for publish and delivery outcomes. Secrets and payloads are not logged.
- Actuator `/health` and `/info` are public. `/actuator/metrics` requires `ADMIN`.
- Gauges refresh on an interval (`payflow.operations.metrics-refresh-ms`, default 15s). Labels are status or outcome only.

## 12. Deployment topology (local)

Docker Compose services (Phase 1+): `postgres`, `redis`, `kafka` (+ ZooKeeper or KRaft as chosen), later `backend`, later `frontend` or Vite on host.

Resource caps should be set so a 16 GB machine remains usable.

## 13. Implementation notes

| Area | Reality |
|------|---------|
| App | Spring Boot **4.1.1**, Java 21, Maven Wrapper |
| Identity | Register/login, RS256 JWT, hashed refresh rotation, `/users/me` |
| Merchants | Owner-scoped CRUD; memberships table ready for future MEMBER role |
| API keys | `sk_test_` / `pk_test_`; secret HMAC hash; `/integrations/me` diagnostic |
| Schema | Flyway V1–V19; `schema_phase = phase-6` |
| Hibernate | `ddl-auto=validate` |
| Security | Public auth + ping + health; JWT vs API-key Bearer classification; internal ops require `ADMIN` |
| OpenAPI | springdoc **3.1.1**; Swagger UI local-only |
| Kafka | Optional. Publisher and consumer stay off until `payflow.kafka.enabled=true` (and the matching publisher/consumer flags). Bootstrap `localhost:9094` when Compose profile `kafka` is up |
| Ports | Postgres **5433**, Redis **6379**, Kafka **9094**, app **8080** |

See also [API_AUTHENTICATION.md](API_AUTHENTICATION.md) and [SECURITY.md](SECURITY.md).

## 14. Risk evaluation (Phase 6)

Merchant-scoped rules (`AMOUNT_THRESHOLD`, `PAYMENT_VELOCITY`, `REPEATED_FAILURES`) with actions FLAG/REVIEW/BLOCK. Authorize path: lock payment → lock merchant → record attempt → evaluate → persist assessment/matches → apply payment/outbox in one TX. Decision precedence BLOCK > REVIEW > FLAG > ALLOW; scores clamped to `[0, 100]`. REVIEW holds payment in `CREATED` (no approval API). BLOCK → `FAILED` (`risk_blocked`) with one failure outbox event. Assessments and match snapshots are immutable. Velocity uses authorize attempts, not create-time. Not a production fraud system.

## 15. Merchant analytics (Phase 6)

Read-only JWT OWNER endpoints: `GET /api/v1/merchants/{id}/analytics/overview` and `.../daily`. UTC calendar dates (inclusive) map to half-open instants; default last 30 days; max 366. Status counts use current status among payments created in-window; amounts use `captured_at` / SUCCEEDED `completed_at` / risk `evaluated_at`. Live SQL aggregates (no Redis). Sandbox activity only — not settled balances.

## 16. React operator console (Phase 7)

Vite 7 + React 19 + TypeScript + Tailwind 4 SPA under `frontend/`. Axios client proxies `/api/v1` via Vite. Auth: access JWT in memory; refresh HttpOnly cookie + CSRF on `/api/v1/auth/browser/**`; concurrent refresh single-flight. Merchant selection persists merchant id only (validated against owned list); TanStack Query keys scoped by merchant and cleared on switch/logout. Surfaces: analytics (real backend aggregates), simulated payments/refunds with stable idempotency keys, API-key and webhook management with one-time secret reveal (not query-cached).

## 17. Explicit honesty

Phases 2–7 are **implemented** on `feature/phase-7-frontend` (backend tip from Phase 6 `f943e87` plus SPA). Risk rule-management APIs, manual review approval, CI, and webhook secret rotation are not. Kafka publish and outbound webhook HTTP default to off. Delivery is at-least-once. Access JWTs are not denylisted on logout. Significant changes must be recorded in [DECISIONS.md](DECISIONS.md).
