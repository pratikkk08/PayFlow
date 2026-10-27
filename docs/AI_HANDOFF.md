# PayFlow — AI Handoff

**Updated:** 2026-10-27 (public GitHub readiness audit — not published)  
**Purpose:** Minimal context for a new Cursor Agent chat. Prefer this + `AGENTS.md` over re-reading the whole repo history.

## Objective

Simulated payment platform (portfolio): merchants, API keys, payments, refunds, ledger, events, webhooks, risk, analytics, React UI. **Sandbox only — no real cards or live funds.**

## Project direction (user decision)

1. **Remain LOCAL** on the Windows laptop for day-to-day use.
2. **No** cloud hosting / public deployment unless explicitly requested later.
3. GitHub stays **PRIVATE** until final review + **explicit** approval to flip public.
4. Public readiness docs prepared; publishing is a separate gated step.

## Stack

| Area | Choice |
|------|--------|
| Backend | Java 21, Spring Boot **4.1.1**, Flyway V1–V19 |
| Frontend | React 19 + TypeScript + Vite 7 (`host: 127.0.0.1`) + Vitest + Playwright |
| Auth | JWT access (memory) + opaque refresh; browser: HttpOnly cookie + CSRF |
| Dev ports | App **8080**, Vite **5173**, Postgres **5433**, Redis **6379** |

## Git

| Item | Value |
|------|--------|
| Branch | `feature/phase-9-portfolio` |
| Baseline HEAD | `99b529f` (local verification landed); readiness doc edits may be uncommitted |
| Remote | Private `https://github.com/pratikkk08/PayFlow.git` |
| Visibility | **PRIVATE** — do not change |

## Evidence

| Doc | Purpose |
|-----|---------|
| [LOCAL_VERIFICATION_REPORT.md](LOCAL_VERIFICATION_REPORT.md) | Local stack **PASSED** (242 / 82 / Playwright) |
| [PUBLIC_READINESS.md](PUBLIC_READINESS.md) | Public GitHub audit, blockers, About/topics/license options |

## First actions in a new chat

1. Read `AGENTS.md` + this file + [PUBLIC_READINESS.md](PUBLIC_READINESS.md).
2. `git status` / `git log -1` — expect possible uncommitted README/ARCHITECTURE/SETUP/readiness docs.
3. Do **not** push, commit, flip visibility, or provision cloud unless the user asks.
4. Next gated work: screenshots → license → owner “make public” approval.
