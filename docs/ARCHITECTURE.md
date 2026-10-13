# PayFlow — Architecture

**Status:** Target architecture for a modular monolith. Phase 1 foundation (Boot app, Compose, Flyway baseline, ping/security) is implemented; domain modules remain planned.  
**Last updated:** 2026-10-12

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

Refunds reference `CAPTURED` payments only:

```
Refund: CREATED → SUCCEEDED | FAILED
```

Illegal transitions throw a domain error; persistence uses optimistic locking (`version`) to prevent lost updates under concurrency.

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
2. Server begins transaction; inserts idempotency row unique on `(merchant_id, key)`.
3. On conflict, load existing record; compare request hash; return stored outcome or 409.
4. Payments/refunds use `@Version` optimistic locking for state transitions.
5. Refundable balance computed from captured amount minus succeeded refunds **inside** the same transaction as the new refund insert (or via ledger constraints).

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

### Outbox → Kafka (planned)

1. Domain service commits business rows + `outbox_message`.
2. Publisher emits to Kafka topics (e.g. `payflow.payments`, `payflow.refunds`).
3. Consumers update projections, enqueue webhook jobs, etc.

### Webhooks

1. Webhook worker loads pending deliveries.
2. Signs body with HMAC-SHA256 using endpoint secret; includes timestamp header.
3. POSTs with timeout; records attempt, status code, latency.
4. Retries with exponential backoff; dead-letters after max attempts.
5. Merchants verify signature before trusting payload.

**Delivery guarantee:** at-least-once. Receivers must be idempotent (event id in payload).

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
| Frontend | Vitest/React Testing Library (when app exists) | Components/hooks |
| E2E | Playwright (later) | Critical merchant journeys |
| Manual | Compose stack | Smoke on Windows + Docker |

CI runs unit + integration on PR once workflows exist. Do not ignore failing tests.

## 11. Observability (planned)

- Structured logging (correlation / request id).
- Spring Boot Actuator health (DB, Redis, Kafka when configured).
- Metrics and tracing deferred until after MVP stability.

## 12. Deployment topology (local)

Docker Compose services (Phase 1+): `postgres`, `redis`, `kafka` (+ ZooKeeper or KRaft as chosen), later `backend`, later `frontend` or Vite on host.

Resource caps should be set so a 16 GB machine remains usable.

## 13. Phase 1–2 implementation notes

| Area | Reality |
|------|---------|
| App | Spring Boot **4.1.1**, Java 21, Maven Wrapper |
| Identity | Register/login, RS256 JWT, hashed refresh rotation, `/users/me` |
| Merchants | Owner-scoped CRUD; memberships table ready for future MEMBER role |
| API keys | `sk_test_` / `pk_test_`; secret HMAC hash; `/integrations/me` diagnostic |
| Schema | Flyway V1 baseline + V2 identity/merchants/keys/audit |
| Hibernate | `ddl-auto=validate` |
| Security | Public auth + ping + health; JWT vs API-key Bearer classification |
| OpenAPI | springdoc **3.1.1**; Swagger UI local-only |
| Kafka | Still excluded from Spring autoconfig |
| Ports | Postgres **5433**, Redis **6379**, Kafka **9094**, app **8080** |

See also [API_AUTHENTICATION.md](API_AUTHENTICATION.md) and [SECURITY.md](SECURITY.md).

## 14. Explicit honesty

Payment create/authorize/capture/cancel and Postgres idempotency are **implemented** (Phase 3). Refunds, ledger, webhooks, and Kafka consumers remain **planned**. Significant changes must be recorded in [DECISIONS.md](DECISIONS.md).
