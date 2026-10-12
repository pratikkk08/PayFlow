# PayFlow — Environment Report

**Machine:** Windows 11 (win32 10.0.26300)  
**Shell:** PowerShell  
**Workspace:** `C:\Projects\PayFlow`  
**Report date:** 2026-10-12  
**Phase:** 0 (initialization)

This report records **observed** command outcomes. Do not treat unchecked tools as working.

## 1. Summary

| Tool | Status | Version / notes |
|------|--------|-----------------|
| Java (`java`) | OK | OpenJDK Temurin **21.0.12.1+1** LTS |
| Java compiler (`javac`) | OK | **21.0.12.1** |
| `JAVA_HOME` | Warning | Unset in the verification shell; binaries still on PATH |
| Node.js | OK (version drift) | **v24.21.0** (project docs prefer 22 LTS) |
| npm | OK | **11.19.0** |
| Git | OK | **2.56.0.windows.2** |
| Docker CLI | OK | **29.8.2** (build 7fc2dff) |
| Docker Compose | OK | **v5.5.1** (`docker compose`) |
| Docker daemon | OK (at check) | `docker ps` succeeded (no containers) |
| WSL | Partial | WSL 2; `docker-desktop` distro listed as **Stopped** during `wsl -l -v` |
| Maven (`mvn`) | Not installed globally | Not on PATH — **expected**; use Maven Wrapper in Phase 1 |
| Spring Boot app | Not present | Phase 1 |
| Node app / `frontend` | Not present | Later phase |

## 2. Command outcomes

### Java

```
java -version
→ openjdk version "21.0.12.1" 2026-08-18 LTS
→ OpenJDK Runtime Environment Temurin-21.0.12.1+1
→ OpenJDK 64-Bit Server VM Temurin-21.0.12.1+1

javac -version
→ javac 21.0.12.1
```

### Node / npm

```
node -v  → v24.21.0
npm -v   → 11.19.0
```

### Git

```
git --version → git version 2.56.0.windows.2
git init      → Initialized empty Git repository (branch main)
```

### Docker

```
docker --version         → Docker version 29.8.2, build 7fc2dff
docker compose version   → Docker Compose version v5.5.1
docker ps                → Empty container list (success)
docker info              → Client 29.8.2, context desktop-linux (partial output captured)
```

### WSL

```
wsl --status
→ Default Distribution: docker-desktop
→ Default Version: 2
→ Note: WSL1 not supported with current machine configuration (informational)

wsl -l -v
→ docker-desktop    Stopped    2
```

Interpretation: Docker CLI could talk to the engine (`docker ps` OK) while the WSL distro listing showed Stopped — likely timing/UI state while Docker Desktop was available to the client. Re-check with Docker Desktop open before relying on Compose for Phase 1.

### Maven

```
mvn -v → not on PATH
```

Recommended fix: do **not** require a global Maven install; add Maven Wrapper with the Spring Boot project in Phase 1.

## 3. Workspace inspection

- Directory was **empty** before Phase 0 file creation (no pre-existing app to preserve).
- No `.git` directory initially; repository initialized locally.
- No remote configured.

## 4. Issues and recommended fixes

| Issue | Severity | Recommendation |
|-------|----------|----------------|
| `JAVA_HOME` unset | Low | Optional: set user/system `JAVA_HOME` to the Temurin 21 root for tools that require it |
| Node 24 vs documented 22 LTS | Low (for now) | Proceed; when scaffolding frontend, consider `nvm`/Volta pin to 22 LTS and CI matrix on 22 |
| Global Maven missing | None | Use `mvnw.cmd` in Phase 1 |
| WSL distro reported Stopped | Medium for Compose | Ensure Docker Desktop is running before `docker compose up`; re-run `wsl -l -v` and `docker info` |
| 16 GB RAM + Kafka | Medium (Phase 1) | Set Compose mem limits; avoid extra unused services; prefer single-node KRaft if feasible |

## 5. Installation problems

No blocking install failures during Phase 0 verification. All primary CLIs responded. Cloud tooling was not installed or tested (out of scope).

## 6. Phase 0 environment acceptance

| Check | Result |
|-------|--------|
| Java 21 available | Pass |
| Node/npm available | Pass |
| Git available + repo init | Pass |
| Docker + Compose CLIs available | Pass |
| Docker engine responsive | Pass at time of `docker ps` |
| Docs record honest gaps | Pass |

**Blockers for Phase 0:** none.  
**Watch items for Phase 1:** Docker Desktop running before Compose; Kafka memory; Maven Wrapper generation.
