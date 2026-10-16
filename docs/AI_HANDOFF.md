# PayFlow — AI Handoff

**Updated:** 2026-10-16  
**Purpose:** Minimal context for a new Cursor Agent chat. Prefer this + `AGENTS.md` over re-reading the whole repo history.

## Objective

Simulated payment platform (portfolio): merchants, API keys, payments, refunds, ledger, domain events, signed webhooks, risk, analytics. **Sandbox only — no real cards or live funds.**

## Stack (implemented)

| Area | Choice |
|------|--------|
| Backend | Java 21, Spring Boot **4.1.1**, Security, JPA, Flyway, Validation, Actuator |
| Auth | OAuth2 Resource Server (RS256 JWT), BCrypt, opaque refresh tokens |
| Docs API | springdoc-openapi **3.1.1** |
| DB / cache | PostgreSQL 16 (host **5433**), Redis 7 (**6379**) |
| Messaging | Kafka optional; topic `payflow.domain-events.v1` (+ DLT) |
| Webhooks | Registration + signed delivery (HTTP off unless enabled) |
| Risk | V17–V18 + evaluate/authorize/velocity/audit (simulated rules, not fraud cert) |
| Analytics | V19 indexes + overview/daily UTC read APIs |
| Build | Maven Wrapper (`backend\mvnw.cmd`) |
| Frontend | Not started |

## Git

| Item | Value |
|------|--------|
| Branch | `feature/phase-6-risk-analytics` |
| Phase 6 | Committed checkpoint (see `git log -1`); not pushed/merged |
| Remote | None configured; do not push unless asked |

## Completed phases

| Phase | Status | Highlights |
|-------|--------|------------|
| 0–5 | Done | Identity → webhooks/outbox/Kafka |
| 6 | **Done** | Risk engine + merchant analytics (V17–V19) |
| 7 | Next | React dashboard |

## Migrations

Latest: **V19**. `schema_phase = phase-6`. Hibernate `ddl-auto=validate`.

## Risk (summary)

- Rule types: `AMOUNT_THRESHOLD` (`thresholdMinor`), `PAYMENT_VELOCITY` (`maxCount`, `windowSeconds`), `REPEATED_FAILURES` (`maxFailures`, `windowSeconds`).
- Decisions: BLOCK > REVIEW > FLAG > ALLOW; scores FLAG+25 / REVIEW+50 / BLOCK+100, clamped `[0,100]`.
- Authorize TX: payment lock → merchant lock → attempt → evaluate → assessment/matches → payment/outbox.
- REVIEW: hold `CREATED` (no approval API). BLOCK: `FAILED` + `risk_blocked`, one failure event.
- Snapshots immutable; malformed BLOCK/REVIEW fail closed; FLAG invalid params skipped.
- Velocity = peer authorize attempts; failures use FAILED `updated_at`. Multi-instance clock skew not corrected.

## Analytics (summary)

- `GET /api/v1/merchants/{id}/analytics/overview` and `.../daily` — JWT OWNER; API key → 403; cross-merchant → 404.
- UTC inclusive dates → half-open instants; default 30 days; max 366.
- Status counts vs event amounts (`captured_at` / SUCCEEDED `completed_at` / risk `evaluated_at`).
- Measured: ~1.2k-row fixture, 7-day window, aggregates typically &lt;1 ms; V19 indexes for narrow windows.

## Known limitations

- No risk rule CRUD APIs; no manual review approval.
- Daily FAILED uses `updated_at` (no `failed_at`).
- Analytics ≠ settled bank balances; status counts ≠ historical reconstructions.
- Not a production-certified fraud system.

## Run & test

```powershell
. .\scripts\set-java-home.ps1
cd backend
.\mvnw.cmd clean verify
```

- Last verified: **206 tests passed**, 0 failed (`mvnw clean verify`, 2026-10-16)

## First actions in a new chat

1. Read `AGENTS.md` + this file.
2. Next: **Phase 7** — React dashboard (Vite + React + TS + Tailwind).
3. Do not push unless asked.
