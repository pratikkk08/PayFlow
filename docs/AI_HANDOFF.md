# PayFlow — AI Handoff

**Updated:** 2026-10-17  
**Purpose:** Minimal context for a new Cursor Agent chat. Prefer this + `AGENTS.md` over re-reading the whole repo history.

## Objective

Simulated payment platform (portfolio): merchants, API keys, payments, refunds, ledger, events, webhooks, risk, analytics, React UI. **Sandbox only — no real cards or live funds.**

## Stack

| Area | Choice |
|------|--------|
| Backend | Java 21, Spring Boot **4.1.1**, Flyway V1–V19 |
| Frontend | React 19 + TypeScript + Vite 7 + Tailwind 4 + React Router 7 + TanStack Query 5 + Axios + Recharts + Vitest |
| Auth | JWT access (memory) + opaque refresh; browser: HttpOnly cookie + CSRF |
| Ports | App **8080**, Vite **5173** (fallback **5174**), Postgres **5433**, Redis **6379** |

## Git

| Item | Value |
|------|--------|
| Branch | `feature/phase-7-frontend` |
| Phase 7 commit | See `git log -1` after Task 7.8 checkpoint |
| Remote | None; do not push unless asked |

## Completed phases

| Phase | Status |
|-------|--------|
| 0–6 | Done (backend through risk + analytics) |
| 7 | **Done** — React dashboard + browser auth (Tasks 7.1–7.8) |

## Frontend (Phase 7)

- Secure browser session: memory access JWT; HttpOnly refresh; CSRF; concurrent refresh single-flight
- Merchants + selection; merchant-scoped TanStack Query isolation
- Analytics dashboard (real backend aggregates only)
- Simulated payments/refunds with stable idempotency keys
- API keys + webhooks; one-time secret reveal (not query-cached); OS clipboard caveat documented
- Vite proxy `/api/v1` only (SPA `/api-keys` safe); CORS allows Vite `5173`/`5174`

## Limitations

- Settings page still a placeholder
- No webhook secret-rotation API; outbound delivery off by default
- Access JWT not denylisted on logout (documented)
- No CI / Playwright suite yet (Phase 8)
- Risk rule-management UI/APIs not built

## Verification (Task 7.8)

| Check | Result |
|-------|--------|
| Frontend typecheck/lint/test/build | Pass — **82** tests |
| `npm audit --omit=dev` | **0** vulnerabilities |
| Backend `mvnw clean verify` | Pass — **211** tests |
| Browser E2E | Dedicated account: login → merchants A/B → switch → analytics → payment authorize/capture → refund submit → API key create/revoke → webhook create/disable/delete → logout → protected redirect |

## Run

```powershell
docker compose -f infra/docker-compose.yml --env-file .env up -d
. .\scripts\set-java-home.ps1
cd backend
& "C:\Windows\System32\cmd.exe" /c "mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local"
cd ..\frontend
npm run dev
```

## First actions in a new chat

1. Read `AGENTS.md` + this file.
2. Next: **Phase 8** — CI, hardening, portfolio polish (optional Playwright).
3. Do not push unless asked.
