# PayFlow — Local Setup

**Status:** Phase 0 — prerequisites and intentions only. Application start commands arrive in Phase 1+.

## 1. Prerequisites

| Tool | Target | Notes |
|------|--------|-------|
| Windows 11 | — | Developed on Windows + PowerShell |
| Java JDK | 21 | Temurin or equivalent; `java` and `javac` on PATH |
| Node.js | 22 LTS preferred | Host may run newer; frontend not scaffolded yet |
| npm | Bundled with Node | — |
| Git | 2.x | — |
| Docker Desktop | Recent stable | WSL 2 backend |
| Docker Compose | V2+ plugin (`docker compose`) | — |
| Maven | Not required globally | Use Maven Wrapper once `backend/` exists |
| RAM | 16 GB class | Cap Compose memory for Kafka/Postgres |

Verified versions on the authoring machine are recorded in [ENVIRONMENT_REPORT.md](ENVIRONMENT_REPORT.md). Re-run checks on any new machine.

## 2. Clone / obtain the repo

```powershell
cd C:\Projects\PayFlow
# If cloning in the future:
# git clone <remote-url> PayFlow
# cd PayFlow
```

There may be no remote during early local development.

## 3. Environment file

```powershell
Copy-Item .env.example .env
# Edit .env with local-only values. Never commit .env.
```

Placeholders in `.env.example` are intentionally non-secret. Replace passwords and signing secrets before running services that read them (Phase 1+).

## 4. Infrastructure (Phase 1+)

When `infra/docker-compose.yml` exists:

```powershell
cd infra
docker compose up -d
docker compose ps
```

Expected services (planned): PostgreSQL, Redis, Kafka (and dependencies). Adjust memory limits if the laptop swaps heavily.

Stop:

```powershell
docker compose down
```

Data directories under `infra/` should remain gitignored.

## 5. Backend (Phase 1+)

When the Spring Boot project exists:

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Default API port (planned): `8080`. Confirm with Actuator health:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

## 6. Frontend (Phase 7+)

When the Vite app exists:

```powershell
cd frontend
npm install
npm run dev
```

Default Vite port (planned): `5173`.

## 7. Useful docs

- Product scope: [PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md)
- Architecture: [ARCHITECTURE.md](ARCHITECTURE.md)
- Phases: [ROADMAP.md](ROADMAP.md)
- Agent rules: [../AGENTS.md](../AGENTS.md)

## 8. Safety checklist

- [ ] `.env` is not staged for commit
- [ ] No real card data in fixtures
- [ ] Docker Desktop has enough disk and is running before Compose
- [ ] Kafka/Postgres containers are not left unbounded if RAM is tight

## 9. Troubleshooting (initial)

| Symptom | Likely cause | Mitigation |
|---------|--------------|------------|
| `docker` errors / daemon | Docker Desktop not running | Start Docker Desktop; wait until engine is ready |
| `java` not found | JDK not installed or PATH | Install Temurin 21; restart shell |
| `JAVA_HOME` empty | Not set system-wide | Optional: set `JAVA_HOME` to JDK root; PATH alone may suffice |
| Port 5432/9092 in use | Local Postgres/Kafka already bound | Stop conflicting service or change Compose ports |
| WSL distro Stopped | Docker Desktop idle/stopped | Start Docker Desktop; WSL distro starts with engine |

Update this file whenever Phase 1 commands are verified on Windows PowerShell.
