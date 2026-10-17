# PayFlow — Operations Runbook

**Scope:** Local deploy Compose (`infra/docker-compose.deploy.yml`) and day-to-day ops on a ~16 GB Windows laptop. Simulation only — no live payments.

## Start / stop / restart

Prepare secrets once (gitignored):

```powershell
.\scripts\prepare-deploy-smoke-secrets.ps1
```

```powershell
# Validate
docker compose -f infra/docker-compose.deploy.yml --env-file secrets/deploy-smoke/.env config

# Start (build images)
docker compose -f infra/docker-compose.deploy.yml --env-file secrets/deploy-smoke/.env up -d --build

# Optional Kafka
docker compose -f infra/docker-compose.deploy.yml --env-file secrets/deploy-smoke/.env --profile kafka up -d

# Restart one service
docker compose -f infra/docker-compose.deploy.yml --env-file secrets/deploy-smoke/.env restart backend

# Stop containers — keeps volumes (safe default)
docker compose -f infra/docker-compose.deploy.yml --env-file secrets/deploy-smoke/.env down
```

Never use `down -v` unless you intentionally wipe deploy volumes. That does **not** touch `infra/docker-compose.yml` (dev) volumes.

## Status and health

```powershell
docker compose -f infra/docker-compose.deploy.yml --env-file secrets/deploy-smoke/.env ps
```

| Check | Expect |
|-------|--------|
| Frontend | `http://127.0.0.1:18080/` |
| Ping | `http://127.0.0.1:18080/api/v1/system/ping` |
| Aggregate health | `GET /actuator/health` → `{"status":"UP"}` (no component details) |
| Liveness | `GET /actuator/health/liveness` → process alive |
| Readiness | `GET /actuator/health/readiness` → DB + Redis OK |
| Metrics via Nginx | `GET /actuator/metrics` → **404** (not proxied) |

Backend container Docker health uses **readiness**. Compose `depends_on` waits for Postgres/Redis healthy before starting the backend.

## Logs (safe inspection)

```powershell
docker compose -f infra/docker-compose.deploy.yml --env-file secrets/deploy-smoke/.env logs --tail=200 backend
docker compose -f infra/docker-compose.deploy.yml --env-file secrets/deploy-smoke/.env logs -f frontend
```

Console lines include `[correlationId]` from `X-Request-Id` (generated if absent). Do **not** dump env files or JWT PEMs into chat/logs. Auth failures log path + exception type only — never passwords or tokens.

**Retention expectation:** Docker JSON logs rotate with the daemon defaults unless you configure `logging:` options. Treat host disk as ephemeral; ship elsewhere if you need long retention (not configured in this repo).

## Resource limits (~16 GB laptop)

Deploy Compose memory caps (approx.): Postgres 512M, Redis 128M, backend 768M, frontend 64M (+ Kafka 768M if enabled) ≈ **1.5 GB** app stack. Leave headroom for Docker Desktop, IDE, and browser. Prefer not running deploy Compose and heavy Maven Testcontainers in parallel.

## Common failures

| Symptom | Likely cause | Action |
|---------|--------------|--------|
| Backend restart loop | Missing/weak secrets | Check `secrets/deploy-smoke/.env`; pepper ≥32 chars; JWT mounts exist |
| Readiness DOWN | Postgres/Redis not ready | `ps` + postgres/redis logs; wait for healthy |
| Flyway fail on start | Schema drift / bad migration | See logs for version; restore from backup into disposable DB first — see [BACKUP_RECOVERY.md](BACKUP_RECOVERY.md) |
| Redis rate-limit 503 | Redis down | Auth fails closed by design; fix Redis, do not disable fail-closed |
| Frontend 502 | Backend not ready | Wait for backend healthy; check readiness |
| Kafka optional | Profile not enabled | Workers stay off unless env opt-in |

## Networking

- Only **frontend** publishes `127.0.0.1:18080`.
- Postgres, Redis, backend (and Kafka) are on `payflow_deploy_internal` only.
- Verify: `docker port` / Compose file shows no host ports on backend/DB/Redis.

## Outbox / webhooks

On `prod` deploy, outbox publisher and webhook delivery are **off** unless `PAYFLOW_OUTBOX_PUBLISHER_ENABLED` / `PAYFLOW_WEBHOOK_DELIVERY_ENABLED` (and Kafka if used) are explicitly set. Failed rows can accumulate; ADMIN metrics / internal event health are for operators after JWT `ADMIN` grant (manual DB role — see SECURITY.md).

## HTTPS / TLS

Terminate TLS at an outer reverse proxy. Backend `prod` trusts `X-Forwarded-*` (`forward-headers-strategy: framework`) and sets `Secure` cookies. Plain HTTP smoke can confirm headers/flags; browsers generally will not store Secure refresh cookies without HTTPS.

## Task 8.8 isolated smoke note

Final verification used Compose project `payflow-verify-88` with `secrets/deploy-verify-8.8/` and host port **18081** so existing deploy/dev volumes were not overwritten. Tear down with `down` (no `-v`) when finished.

## Related

- [BACKUP_RECOVERY.md](BACKUP_RECOVERY.md) — PostgreSQL dump/restore
- [SETUP.md](SETUP.md) — local and deploy setup
- [SECURITY.md](SECURITY.md) — secrets and Actuator exposure
