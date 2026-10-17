# PayFlow — AI Handoff

**Updated:** 2026-10-17  
**Purpose:** Minimal context for a new Cursor Agent chat. Prefer this + `AGENTS.md` over re-reading the whole repo history.

## Objective

Simulated payment platform (portfolio): merchants, API keys, payments, refunds, ledger, events, webhooks, risk, analytics, React UI. **Sandbox only — no real cards or live funds.**

## Stack

| Area | Choice |
|------|--------|
| Backend | Java 21, Spring Boot **4.1.1**, Flyway V1–V19 |
| Frontend | React 19 + TypeScript + Vite 7 + Tailwind 4 + Vitest + Playwright |
| Auth | JWT access (memory) + opaque refresh; browser: HttpOnly cookie + CSRF |
| Dev ports | App **8080**, Vite **5173**, Postgres **5433**, Redis **6379** |
| Deploy | `infra/docker-compose.deploy.yml` — frontend loopback only (default **18080**) |
| Ops | [OPERATIONS.md](OPERATIONS.md), [BACKUP_RECOVERY.md](BACKUP_RECOVERY.md) |

## Git

| Item | Value |
|------|--------|
| Branch | `feature/phase-8-devops` |
| Phase 8 checkpoint | See latest commit on branch after Task 8.8 |
| Remote / GitHub Actions | **NOT VERIFIED** (no push) |

## Phase 8 — complete locally (Tasks 8.1–8.8)

CI workflow, backend/frontend images, prod fail-closed config, deploy Compose, Playwright E2E foundation, ops/backup runbooks, final verification checkpoint.

### Task 8.8 verification (this machine)

| Check | Result |
|-------|--------|
| Backend `mvnw clean verify` | **242** tests, 0 failures |
| Frontend typecheck/lint/test/build | Pass; Vitest **82** |
| `npm audit --omit=dev` | 0 vulnerabilities |
| `npm ci` | EPERM on locked native binary once; gates re-run after `npm install` |
| Playwright E2E | **3** passed |
| Images | backend **477MB**, frontend **74.9MB**; non-root; readiness healthcheck |
| Deploy smoke (`payflow-verify-88`, :18081) | All healthy; Flyway **V19**; metrics **404**; Secure CSRF cookie |
| Prod image without secrets | Fails closed (`DATABASE_URL` unset) |
| Backup/restore drill | Pass on disposable DB; live name rejected |
| HTTPS browser Secure-cookie login | **NOT VERIFIED** |
| Remote GitHub Actions | **NOT VERIFIED** |

## Limitations / Phase 9

- No remote CI proof until push
- Production HTTPS browser session not verified (do not disable `Secure`)
- Playwright not in CI yet
- No Prometheus/Grafana/ELK; logical backup ≠ PITR
- Not PCI/SOC2

**Next:** Phase 9 portfolio polish (remote CI, TLS login verify, optional CI E2E) — see [ROADMAP.md](ROADMAP.md). Do not implement unless tasked.

## First actions in a new chat

1. Read `AGENTS.md` + this file.
2. Confirm git status / latest Phase 8 commit.
3. Do not push unless asked.
