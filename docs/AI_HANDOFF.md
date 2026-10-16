# PayFlow — AI Handoff

**Updated:** 2026-10-16  
**Purpose:** Minimal context for a new Cursor Agent chat. Prefer this + `AGENTS.md` over re-reading the whole repo history.

## Objective

Simulated payment platform (portfolio): merchants, API keys, payments, refunds, ledger. **Sandbox only — no real cards or live funds.**

## Stack (implemented)

| Area | Choice |
|------|--------|
| Backend | Java 21, Spring Boot **4.1.1**, Security, JPA, Flyway, Validation, Actuator |
| Auth | OAuth2 Resource Server (RS256 JWT), BCrypt passwords, opaque refresh tokens |
| Docs API | springdoc-openapi **3.1.1** |
| DB / cache | PostgreSQL 16 (host **5433**), Redis 7 (**6379**) |
| Messaging | Kafka optional Compose profile (**9094**); Spring Kafka autoconfig **excluded** |
| Build | Maven Wrapper (`backend\mvnw.cmd`) |
| Frontend | Not started (`frontend/` placeholder) |

## Git

| Item | Value |
|------|--------|
| Branch | `feature/phase-4-refunds-ledger` |
| Latest commit | Confirm with `git log -1` (Phase 4 checkpoint: `feat: implement refunds and double-entry ledger`) |
| Also | Phase 3 tip was `281f2a4`; `main` @ Phase 0 |
| Remote | None configured; do not push unless asked |

## Completed phases

| Phase | Status | Highlights |
|-------|--------|------------|
| 0–2 | Done | Docs, boot app, identity, merchants, API keys, JWT |
| 3 | Done | Payments create/authorize/capture/cancel, idempotency, list/get |
| 4 | Done | Refunds, capacity locking, refund idempotency, double-entry ledger, refund read APIs |

**Not started:** Phase 5+ (webhooks/outbox/Kafka, risk, analytics, React UI, CI).

## Migrations (Flyway V1–V11)

Runtime marker: `payflow_schema_meta.schema_phase = phase-4`.

| Version | Purpose |
|---------|---------|
| V1–V7 | Baseline through payments + list indexes |
| V8 | `refunds` (`PENDING` / `SUCCEEDED` / `FAILED`) |
| V9 | `refund_idempotency_records` |
| V10 | Ledger accounts, transactions, entries |
| V11 | `idx_refunds_payment_created_id` |

## APIs (live additions)

**JWT merchant OWNER only** (API keys → 403; cross-merchant → 404):

- Payments: create / get / list / authorize / capture / cancel (Phase 3)
- `POST /api/v1/payments/{paymentId}/refunds` — create (`Idempotency-Key` required)
- `GET /api/v1/refunds/{refundId}` — get
- `GET /api/v1/payments/{paymentId}/refunds?page=&size=` — history (`createdAt DESC, id DESC`; size max 100)

Public auth/ping/actuator/Swagger unchanged. Secret key: `GET /api/v1/integrations/me` only.

## Phase 4 invariants (enforced in app TX)

- Refunds only against `CAPTURED` payments; no payment-level `REFUNDED` status.
- Capacity = captured − sum(`PENDING` + `SUCCEEDED`); payment row locked on create.
- Capture posting: DEBIT `PROCESSOR_CLEARING`, CREDIT `MERCHANT_PAYABLE`.
- Successful refund posting: reverse of capture; unique `(source_type, source_id)`.
- Domain change + ledger posting commit atomically; posting failure rolls back both.
- Failed authorize/capture/refund → no ledger rows.

## Known limits

- No webhooks, outbox, Kafka domain events, risk, or analytics.
- Currency locked to `INR`.
- Payment/refund APIs are JWT-owner only (no secret-key money APIs yet).
- No email verification; no ADMIN grant endpoint.
- Logout does not denylist access JWTs until expiry.
- Host Postgres often occupies **5432** → Compose uses **5433**.
- Surefire: name tests `*Test.java` (not `*IT.java`).

## Run & test (Windows)

```powershell
Copy-Item .env.example .env   # if needed
. .\scripts\set-java-home.ps1
docker compose -f infra/docker-compose.yml --env-file .env up -d
cd backend
.\mvnw.cmd clean verify
```

- Last verified: **89 tests passed**, 0 failed (`mvnw clean verify`, 2026-10-16)

## Current / next task

- **Current:** Phase 4 complete on `feature/phase-4-refunds-ledger`.
- **Next (Phase 5):** Transactional outbox, Kafka publisher, signed webhooks with retries. Do not start unless asked.

## First actions in a new chat

1. Read `AGENTS.md` + this file.  
2. `git status` / confirm branch.  
3. Implement **Phase 5 only** unless the user expands scope.
