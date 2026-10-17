# PayFlow — PostgreSQL Backup & Recovery

**Scope:** Logical backups of the **deployment Compose** Postgres (`payflow-deploy` / service `postgres`). Simulation data only.

This is **not** continuous archiving / point-in-time recovery (PITR). A `pg_dump` captures a consistent logical snapshot at dump time; you cannot roll forward to an arbitrary second between dumps without WAL archiving (not configured here).

## What to back up

| Asset | Notes |
|-------|--------|
| PostgreSQL (`payflow_deploy_pgdata`) | Financial SoT — **required** |
| Redis | Ephemeral (rate limits/cache) — optional/low value |
| JWT PEMs + env secrets | Back up outside Git with strict permissions; losing PEMs invalidates access JWTs |

## Backup (logical dump)

Script (preferred):

```powershell
.\scripts\backup-deploy-postgres.ps1
# Optional: -EnvFile secrets/deploy-smoke\.env -OutputDir backups\deploy
```

Manual equivalent (project + service explicit):

```powershell
$ts = Get-Date -Format "yyyyMMdd-HHmmss"
New-Item -ItemType Directory -Force -Path backups\deploy | Out-Null
docker compose -f infra/docker-compose.deploy.yml --env-file secrets/deploy-smoke/.env `
  exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' `
  > "backups\deploy\payflow-$ts.dump"
```

- Format: custom (`-Fc`) for `pg_restore`.
- Store under `backups/` (gitignored). Restrict ACLs on the host directory (Windows: limit to your user; do not share dumps).
- Scripts never echo `POSTGRES_PASSWORD` or dump contents to the console beyond file path/size.

### Verify a backup

```powershell
# List TOC without restoring (requires pg_restore on PATH or via container)
docker compose -f infra/docker-compose.deploy.yml --env-file secrets/deploy-smoke/.env `
  exec -T postgres pg_restore -l < backups\deploy\payflow-YYYYMMDD-HHMMSS.dump
```

Confirm Flyway history is present in the TOC / after a disposable restore (`flyway_schema_history`).

## Restore — disposable database first

**Never** restore onto the live `POSTGRES_DB` without an outage plan and explicit confirmation. Prefer:

1. Create an isolated database (e.g. `payflow_restore_check_<timestamp>`).
2. Restore the dump into that database.
3. Spot-check Flyway version / row counts.
4. Drop the disposable database when done.

Script (requires typed confirmation of the **disposable** DB name):

```powershell
.\scripts\restore-deploy-postgres.ps1 `
  -DumpFile backups\deploy\payflow-YYYYMMDD-HHMMSS.dump `
  -TargetDatabase payflow_restore_check_20261017
```

The script refuses targets that match the live database name from the env file and refuses names that do not start with `payflow_restore_`.

### Maintenance / downtime (production-like restore)

If you must replace the live DB:

1. Put the app in maintenance (stop `backend` / `frontend` or block traffic at the TLS proxy).
2. Take a fresh pre-change dump.
3. Restore into a disposable DB and validate.
4. Only then swap (documented operator procedure) — **this repo’s restore script will not overwrite the live DB**.
5. Start backend; Flyway `validate` must succeed (`ddl-auto=validate`).

## Flyway compatibility

- App expects migrations V1–current (see handoff) with Hibernate validate.
- Restoring an older dump into a newer app binary may fail Flyway checksum/version checks — align app image and dump era.
- After disposable restore, inspect: `SELECT version, success FROM flyway_schema_history ORDER BY installed_rank;`

## Limitations

- Logical dump ≠ PITR; gap since last dump is data loss risk.
- Concurrent writes during dump are handled by Postgres dump consistency for that session; prefer low traffic or short maintenance for critical dumps.
- Redis / Kafka state is not included.
- Encrypted webhook secrets remain ciphertext — restore the same `WEBHOOK_SECRET_ENCRYPTION_KEY` or secrets are unreadable.
- Automated off-site replication is out of scope.
- `MAX(version)` on `flyway_schema_history.version` is **lexicographic** (text); use `ORDER BY installed_rank DESC` for the latest migration number.

## Task 8.8 drill (isolated)

| Step | Result |
|------|--------|
| Project | `payflow-verify-88` + `secrets/deploy-verify-8.8/` (gitignored); host port **18081** |
| Marker | Inserted `verify_8_8_marker.note = payflow-verify-88-marker` on live verify DB |
| Backup | `backup-deploy-postgres.ps1` → custom dump (~83 KB) |
| Restore | Into `payflow_restore_verify88` only; marker + `flyway_schema_history` (19 rows / V1–V19) verified |
| Live protect | Target `payflow` rejected (`payflow_restore_*` rule) |
| Cleanup | Disposable DB dropped; Compose `down` **without** `-v` |

## Related

- [OPERATIONS.md](OPERATIONS.md) — start/stop/health
- [SECURITY.md](SECURITY.md) — secret handling
