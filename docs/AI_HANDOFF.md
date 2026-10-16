# PayFlow — AI Handoff

**Updated:** 2026-10-16  
**Purpose:** Minimal context for a new Cursor Agent chat. Prefer this + `AGENTS.md` over re-reading the whole repo history.

## Objective

Simulated payment platform (portfolio): merchants, API keys, payments, refunds, ledger, domain events, signed webhooks. **Sandbox only — no real cards or live funds.**

## Stack (implemented)

| Area | Choice |
|------|--------|
| Backend | Java 21, Spring Boot **4.1.1**, Security, JPA, Flyway, Validation, Actuator |
| Auth | OAuth2 Resource Server (RS256 JWT), BCrypt passwords, opaque refresh tokens |
| Docs API | springdoc-openapi **3.1.1** |
| DB / cache | PostgreSQL 16 (host **5433**), Redis 7 (**6379**) |
| Messaging | Kafka optional; topic `payflow.domain-events.v1`, DLT `payflow.domain-events.v1.dlt`, group `payflow-domain-events-internal` |
| Webhooks | Registration + signed delivery (HTTP off unless `payflow.webhooks.delivery.enabled=true`) |
| Build | Maven Wrapper (`backend\mvnw.cmd`) |
| Frontend | Not started (`frontend/` placeholder) |

## Git

| Item | Value |
|------|--------|
| Branch | `feature/phase-5-events-webhooks` |
| Latest commit | Phase 5 checkpoint (this commit) |
| Remote | None configured; do not push unless asked |

## Completed phases

| Phase | Status | Highlights |
|-------|--------|------------|
| 0–4 | Done | Identity through refunds/ledger |
| 5 | Done | Outbox, Kafka, idempotent consumer, signed webhooks, ops metrics |

**Not started:** risk, analytics, React UI, CI, webhook secret rotation, self-service ADMIN.

## Migrations (Flyway V1–V16)

`schema_phase = phase-5`. Latest: **V16** `webhook_deliveries`. V12–V13 outbox + claim tokens. V14 `processed_events`. V15 `webhook_endpoints`.

## Phase 5 notes

- Outbox insert is in the payment/refund transaction. Publisher ACKs Kafka before `PUBLISHED`. Expired leases are reclaimable. Stale claim tokens cannot finalize.
- Consumer writes `processed_events` and `webhook_deliveries` together. Duplicate event ids do not create a second job or a second ledger posting.
- At-least-once only. Receivers verify HMAC `v1` (`{timestamp}.{eventId}.{body}`) and dedupe by event id.
- TLS: connect to the validated IP only; PKIX + SNI + original `Host`. Redirects fail permanently. 429/5xx/timeouts retry.
- `GET /api/v1/internal/events/health` and `/actuator/metrics` require JWT `ADMIN`. Granting ADMIN is a trusted operator `user_roles` insert, not an API. FAILED requeue is internal.

## Run & test

```powershell
. .\scripts\set-java-home.ps1
cd backend
.\mvnw.cmd clean verify
```

- Last verified: **155 tests passed**, 0 failed (`mvnw clean verify`, 2026-10-16)

## First actions in a new chat

1. Read `AGENTS.md` + this file.
2. Do not start Phase 6 unless asked. Next task is Phase 6 risk rules and analytics.
3. Do not push unless asked.
