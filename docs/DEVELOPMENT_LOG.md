# PayFlow — Development Log

Chronological record of meaningful work. Newest entries at the top.

---

## 2026-10-12 — Phase 0: Initialization

### Done

- Inspected workspace at `C:\Projects\PayFlow` (empty; no prior application code).
- Initialized local Git repository on branch `main` (no remote, no commit unless requested later).
- Verified toolchain from PowerShell: Java/javac, Node/npm, Git, Docker CLI, Docker Compose, WSL status.
- Created persistent agent instructions: `AGENTS.md`.
- Created project docs: product requirements, architecture, roadmap, decisions, setup, environment report.
- Created `README.md`, `.gitignore`, `.env.example` (placeholders only).
- Scaffolded directories: `backend/`, `frontend/`, `infra/`, `scripts/`, `docs/`, `.github/workflows/` with `.gitkeep` where needed.

### Not done (intentionally out of scope)

- Spring Boot application code
- React frontend
- Docker Compose stack files
- CI workflows with real jobs
- Any commit or remote push

### Notes / issues

- Host Node reported **v24.x** (newer than the documented target Node 22 LTS). Compatible for later frontend work; pin to 22 LTS in CI when frontend starts if reproducibility matters.
- `JAVA_HOME` was unset in the shell session; `java`/`javac` still resolved on PATH.
- System `mvn` not on PATH — expected; Maven Wrapper planned in Phase 1.
- WSL listed `docker-desktop` as Stopped at check time, while `docker ps` succeeded against the Docker engine — see environment report.

### Next

Execute **Phase 1**: Spring Boot skeleton, Flyway baseline, Docker Compose for Postgres/Redis/Kafka, Actuator health, update SETUP with verified commands.
