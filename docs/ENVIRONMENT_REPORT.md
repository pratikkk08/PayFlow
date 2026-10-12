# PayFlow — Environment Report

**Machine:** Windows 11 (win32 10.0.26300)  
**Shell:** PowerShell  
**Workspace:** `C:\Projects\PayFlow`  
**Last updated:** 2026-10-12 (Phase 2 verification)  

This report records **observed** command outcomes.

## 1. Summary

| Tool | Status | Version / notes |
|------|--------|-----------------|
| Java (`java`/`javac`) | OK | Temurin **21.0.12.1+1** at `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` |
| `JAVA_HOME` | Remediatable | Often unset; use `scripts/set-java-home.ps1` for the session |
| Node.js | OK | **v24.21.0** (unused in Phase 1) |
| npm | OK | **11.19.0** |
| Git | OK | **2.56.0.windows.2** (author configured) |
| Docker CLI | OK | **29.8.2** |
| Docker Compose | OK | **v5.5.1** |
| Docker engine | OK | Server **29.8.2**; Desktop VM memory ~**7.4 GiB** |
| Maven Wrapper | OK | Apache Maven **3.10.0** via `backend\mvnw.cmd` |
| Global `mvn` | Absent | Expected |

## 2. Phase 2 verification results

| Check | Result |
|-------|--------|
| `.\mvnw.cmd clean verify` | Pass — **12 tests, 0 failures** |
| Flyway on Compose Postgres | Pass (`schema_phase=phase-2`) |
| E2E register→login→me→merchant→API key→revoke | Pass (secrets redacted; not logged) |
| Jackson 3 `JsonMapper` bean wiring | Pass (Boot 4) |

## 3. Issues found and fixes

| Issue | Severity | Resolution |
|-------|----------|------------|
| Parent POM `4.1.1.RELEASE` missing on Central | High | Pin `4.1.1` |
| Host `5432` occupied (PID listening) | Medium | Compose publish **5433** |
| Kafka advertised `0.0.0.0` / bad `CLUSTER_ID` | Medium | Fix listeners + base64 cluster id; recreate kafka volume only |
| Unauthenticated API returned 403 with anonymous auth | Low | Disable anonymous; `HttpStatusEntryPoint` → **401** |
| Surefire skipped `*IT.java` | Low | Renamed to `FlywayMigrationTest` |
| Default generated security user password logged | Low | Exclude `UserDetailsServiceAutoConfiguration` |

## 4. Installation problems

None blocking after the fixes above. No system-wide env changes were made; `JAVA_HOME` is set per session via script when needed.

## 5. Git

| Item | Value |
|------|-------|
| Phase 0 baseline | `feb8bba` — `chore: initialize PayFlow architecture and project documentation` |
| Phase 1 branch | `feature/phase-1-foundation` |
| Remotes | None |
