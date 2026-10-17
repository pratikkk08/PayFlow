# PayFlow — AI Handoff

**Updated:** 2026-10-17 (Task 9.4)
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
| Deploy | `infra/docker-compose.deploy.yml` (+ optional HTTPS / **ARM64** overrides) |
| Hosting plan | [DEPLOYMENT_PLAN.md](DEPLOYMENT_PLAN.md) — **₹0/month**; Oracle Always Free A1 |
| Ops | [OPERATIONS.md](OPERATIONS.md), [BACKUP_RECOVERY.md](BACKUP_RECOVERY.md) |

## Git

| Item | Value |
|------|--------|
| Branch | `feature/phase-9-portfolio` (from `d68269c`) |
| Working tree | Tasks **9.1–9.4** local checkpoint (see latest commit on branch) |
| Remote | Private `origin` expected after Task 9.5B (push still separate) |
| GitHub Actions | **NOT VERIFIED** |
| Cloud / native A1 | **NOT PROVISIONED / NOT VERIFIED** |

## Task 9.4 — ARM64 deployment readiness (local)

| Check | Result |
|-------|--------|
| Buildx | `linux/arm64` available (Docker Desktop) |
| `payflow-frontend:deploy-arm64` | **Built** (~236 s QEMU) |
| `payflow-backend:deploy-arm64` | **Built** (~548 s QEMU) |
| Emulated Compose smoke | **Pass** — aarch64 uname; ping/SPA/health 200; metrics 404; DB/Redis unpublished |
| Redis on Desktop QEMU | Needs `--ignore-warnings ARM64-COW-BUG` (arm64 override); re-evaluate on native A1 |
| Native Oracle Ampere | **NOT DONE** |
| A1 capacity | **UNVERIFIED** |
| Registry / cloud | No push; no provision |

Artifacts: `infra/docker-compose.deploy.arm64.yml`, `scripts/build-deploy-images-arm64.ps1`, `.env.a1.example`.

## Tasks 9.1–9.3 (summary)

- **9.1** GitHub readiness docs (no remote yet)
- **9.2** Local HTTPS Secure-cookie E2E passed
- **9.3** ₹0 hosting plan → Oracle Always Free A1 (2 OCPU / 12 GB)

## Limitations

- Emulated ARM ≠ native Ampere proof
- No public deploy; no paid fallback
- Not PCI/SOC2

**Next:** User approval — Oracle A1 capacity attempt **or** recorded/local demo packaging. Optional: private GitHub + remote CI.

## First actions in a new chat

1. Read `AGENTS.md` + this file + [DEPLOYMENT_PLAN.md](DEPLOYMENT_PLAN.md).
2. Confirm branch `feature/phase-9-portfolio` and uncommitted Phase 9 work.
3. Do not push, commit, or provision cloud unless asked.
