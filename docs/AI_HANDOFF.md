# PayFlow — AI Handoff

**Updated:** 2026-10-27 (local complete-system verification)  
**Purpose:** Minimal context for a new Cursor Agent chat. Prefer this + `AGENTS.md` over re-reading the whole repo history.

## Objective

Simulated payment platform (portfolio): merchants, API keys, payments, refunds, ledger, events, webhooks, risk, analytics, React UI. **Sandbox only — no real cards or live funds.**

## Project direction (user decision)

1. **Remain LOCAL** on the Windows laptop for now.
2. **No** Oracle Cloud, Vercel, VPS, or hosting work unless the user explicitly requests it later.
3. GitHub repo stays **PRIVATE** until a later public-release prep step.
4. After local verification (done), public GitHub release prep is a **separate** gated task.

## Stack

| Area | Choice |
|------|--------|
| Backend | Java 21, Spring Boot **4.1.1**, Flyway V1–V19 |
| Frontend | React 19 + TypeScript + Vite 7 + Tailwind 4 + Vitest + Playwright |
| Auth | JWT access (memory) + opaque refresh; browser: HttpOnly cookie + CSRF |
| Dev ports | App **8080**, Vite **5173** (`127.0.0.1`), Postgres **5433**, Redis **6379** |

## Git

| Item | Value |
|------|--------|
| Branch | `feature/phase-9-portfolio` |
| HEAD (verified) | `416ff05` |
| Remote | Private `origin` → `https://github.com/pratikkk08/PayFlow.git` |
| Working tree note | Local verification report + Vite IPv4 bind fix landed on this branch |

## Local verification (2026-10-27)

**Status: PASSED** — full report: [LOCAL_VERIFICATION_REPORT.md](LOCAL_VERIFICATION_REPORT.md)

| Gate | Result |
|------|--------|
| Infra Postgres/Redis/Kafka | Healthy |
| Backend ping/health + Flyway 19 | UP |
| API journey (auth→webhooks) | 39/39 |
| Live browser auth/merchants/session | PASS |
| Backend `mvnw clean verify` | **242** passed |
| Frontend typecheck/lint/test/build/audit | **82** Vitest; 0 audit vulns |
| Playwright HTTP E2E | **3** passed |
| Playwright HTTPS Secure-cookie | **1** passed |
| Persistence across restart | PASS |
| Fix applied | Vite `server.host: '127.0.0.1'` (Playwright IPv4 bind) |

## Limitations

- Simulation only; not PCI/SOC2.
- No public hosting; cloud plan docs remain historical reference only.
- Public GitHub release **not started**.

## First actions in a new chat

1. Read `AGENTS.md` + this file + [LOCAL_VERIFICATION_REPORT.md](LOCAL_VERIFICATION_REPORT.md).
2. Confirm branch `feature/phase-9-portfolio` and read [LOCAL_VERIFICATION_REPORT.md](LOCAL_VERIFICATION_REPORT.md).
3. Do **not** change GitHub visibility or provision cloud unless the user asks.
4. Next gated work (only if requested): public-release prep checklist — not hosting.
