# PayFlow â€” Deployment Plan (Tasks 9.3â€“9.4)

**Updated:** 2026-10-17 (Task 9.4 ARM64 readiness)
**Budget:** **â‚¹0 per month** (non-negotiable). No paid VPS, no paid domain, no paid CDN, no paid managed DB.
**Scope:** Planning + **local** ARM64 Buildx validation. **No Oracle resources were provisioned.**
**Product constraint:** Simulated payments only â€” never enable live card processing.

## Verdict (read this first)

| Question | Answer |
|----------|--------|
| Is a complete PayFlow demo **realistically** deployable for â‚¹0/month? | **Conditionally yes** â€” if an Oracle Cloud **Always Free Ampere A1** VM can be obtained in the tenancy home region and kept within Always Free limits. |
| What must be verified **before** any provision attempt? | (1) Account eligibility / home region, (2) live **A1 capacity** (**UNVERIFIED**), (3) billing safeguards, (4) rebuild/run on the **native** A1 host (local QEMU â‰  Ampere). |
| Local ARM64 readiness (Task 9.4)? | **Build + emulated runtime smoke passed** on Docker Desktop (amd64 host). **Native Oracle ARM VM: NOT DONE.** |
| If A1 capacity cannot be obtained? | **Do not pay.** Fall back to local HTTPS demo (Task 9.2) and/or a recorded recruiter demo. |

## 1. Strict â‚¹0 budget rules

Allowed only if **recurring cost remains â‚¹0** after any trial credits expire:

| Resource | Must stay free |
|----------|----------------|
| Compute | Always Free shape only |
| Block / object storage | Within Always Free quotas |
| Public IPv4 / egress | Within Always Free allowances |
| TLS | Letâ€™s Encrypt (or equivalent free ACME) |
| DNS / hostname | Provider public IP + free DNS helper (sslip.io / DuckDNS) â€” **no purchased domain** |
| Backups | Logical dumps + Always Free Object Storage and/or â‰¤5 Always Free volume backups |

**Forbidden without explicit future approval (out of scope for â‚¹0):** upgrading to Pay As You Go, paid shapes, paid load balancers beyond Always Free, paid DNS/domains, paid snapshots beyond free quotas, paid egress overages, Kafka-as-a-service.

Temporary card **authorization holds** for identity verification are expected at signup; they must not convert into recurring charges. Do not enable paid services â€œjust to get capacity.â€

## 2. Recommended free VPS: Oracle Cloud Always Free Ampere A1

**Primary candidate:** single `VM.Standard.A1.Flex` instance using the full Always Free Ampere pool.

### Official Always Free allowances (verified against Oracle docs, 2026-10)

Source: [Always Free Resources](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)

| Resource | Always Free allowance (current) |
|----------|----------------------------------|
| Ampere A1 (`VM.Standard.A1.Flex`) | **2 OCPUs** and **12 GB** memory total (â‰ˆ 1,500 OCPU-hours + 9,000 GB-hours / month) |
| AMD micro (`VM.Standard.E2.1.Micro`) | Up to **2** instances (1 GB RAM each) â€” **too small** for PayFlow stack alone |
| Block Volume | **200 GB** total (boot + data), home region |
| Volume backups | **5** Always Free backups |
| Object Storage | **20 GB** (Always Freeâ€“only account combined tiers) + 50k API requests/month |
| Outbound data transfer | **10 TB** / month |
| Load balancer | One Always Free flexible LB at **10 Mbps** (optional; not required if TLS on the VM) |
| Idle reclaim | Always Free compute may be **reclaimed** if idle 7 days (CPU/network/memory thresholds; A1 includes memory under 20%) |

**Note (2026 change):** Ampere Always Free was reduced from the older 4 OCPU / 24 GB marketing figures to **2 OCPU / 12 GB**. Plan against the current table above, not blog posts from earlier years.

### Recommended VM shape for PayFlow

| Setting | Value |
|---------|--------|
| Shape | `VM.Standard.A1.Flex` (ARM64) **only** â€” never paid Flex/GPU shapes |
| OCPUs | **2** (full free pool on one VM) |
| Memory | Prefer **12 GB**; minimum practical demo target **6 GB** (tighter; watch `docker stats`) |
| Boot volume | **~50â€“80 GB** (under 200 GB Always Free block total) |
| OS image | Always Freeâ€“eligible **Ubuntu** (preferred) or **Oracle Linux** |
| Public IP | Yes â€” publish **80/443** (and SSH from your IP) only |
| Region | **Home region only** for Always Free compute |
| Workers | Kafka **off**; outbound webhooks **off** |

### Recommended memory budget (planning only â€” not a benchmark)

| Consumer | Planning allowance |
|----------|-------------------|
| Ubuntu + Docker daemon | ~1.0â€“1.5 GiB |
| PostgreSQL (limit 512 MiB) | â‰¤ 512 MiB |
| Redis (limit 128 MiB) | â‰¤ 128 MiB |
| Backend JVM (limit 768 MiB, MaxRAMPercentage 65%) | â‰¤ 768 MiB |
| Frontend Nginx (limit 64 MiB) | â‰¤ 64 MiB |
| Backup/`pg_dump` spikes | leave 0.5â€“1 GiB free |
| **Compose container limits subtotal** | **â‰ˆ 1.5 GiB** |

Do **not** claim production performance or concurrent-user capacity without measurement on the A1 VM.

### Capacity status

| Check | Status |
|-------|--------|
| Live Ampere A1 capacity in a chosen region | **UNVERIFIED** â€” not queried from this environment; Oracle commonly returns *out of host capacity* |
| Assumption that A1 is available | **Forbidden** â€” treat acquisition as a gated, possibly multi-day retry |

If creation fails with capacity errors: retry other ADs in the home region, wait/retry, or abandon cloud for the local/recorded fallback. **Do not upgrade the account to get capacity.**

## 3. ARM64 compatibility assessment (Task 9.4)

### 3a. Base-image manifests (necessary, not sufficient)

| Image (as used by PayFlow) | `linux/arm64` manifest |
|----------------------------|-------------------------|
| `eclipse-temurin:21.0.12.1_1-jdk-alpine-3.24` | **Yes** |
| `eclipse-temurin:21.0.12.1_1-jre-alpine-3.24` | **Yes** |
| `node:24.21.0-alpine` | **Yes** |
| `nginxinc/nginx-unprivileged:1.28.0-alpine` | **Yes** |
| `postgres:16.6-alpine` | **Yes** |
| `redis:7.4.2-alpine` | **Yes** |
| `apache/kafka:3.9.0` (optional) | **Yes** â€” **keep disabled** |

### 3b. PayFlow image builds (Buildx `--platform linux/arm64 --load`)

| Image | Result | Approx. time (QEMU on amd64 Desktop) | Size |
|-------|--------|--------------------------------------|------|
| `payflow-frontend:deploy-arm64` | **Pass** | ~236 s | ~77 MB |
| `payflow-backend:deploy-arm64` | **Pass** | ~548 s | ~476 MB |

Script: [`scripts/build-deploy-images-arm64.ps1`](../scripts/build-deploy-images-arm64.ps1). **No registry push.**

### 3c. Emulated ARM64 runtime smoke (Docker Desktop QEMU â€” not native Ampere)

Isolated project `payflow-arm64-smoke` + [`infra/docker-compose.deploy.arm64.yml`](../infra/docker-compose.deploy.arm64.yml):

| Check | Result |
|-------|--------|
| `uname -m` in postgres/redis/backend/frontend | **aarch64** |
| Java 21 Temurin in backend | **OK** |
| Nginx 1.28 in frontend | **OK** |
| Postgres + Redis healthy | **OK** (Redis needed `--ignore-warnings ARM64-COW-BUG` under Desktop QEMU only) |
| Frontend â†’ `/api/v1/system/ping` | **200** |
| `/actuator/health` via proxy | **200** |
| `/actuator/metrics` | **404** |
| Backend/DB/Redis host ports | **None published** |
| Stack teardown | **down -v** (dev Compose left intact) |

| Layer | Status |
|-------|--------|
| Image manifest compatibility | Verified |
| Successful ARM64 **build** | Verified (local Buildx) |
| Successful ARM64 **execution under emulation** | Verified (QEMU) |
| Native Oracle Ampere A1 execution | **NOT DONE** |

**On native A1:** rebuild with the same script (faster, no QEMU). Prefer Redis **without** `ARM64-COW-BUG` ignore unless Redis refuses to start on that kernel.

## 4. Free-tier resource budget (fit check)

Deploy Compose memory **limits** today (`infra/docker-compose.deploy.yml`), Kafka profile **off**:

| Service | Memory limit | Free-tier recommendation |
|---------|--------------|---------------------------|
| postgres | 512 MiB | Keep |
| redis | 128 MiB | Keep (`--save ""`, AOF off already) |
| backend | 768 MiB | Keep; optionally lower JVM later if pressure |
| frontend (Nginx) | 64 MiB | Keep |
| kafka | 768 MiB | **Disabled** (`--profile kafka` not used) |
| **Container subtotal** | **â‰ˆ 1.5 GiB** | Fits easily in **12 GiB** with OS + Docker overhead |

| Feature flags (prod) | Value |
|----------------------|--------|
| `PAYFLOW_KAFKA_ENABLED` | `false` |
| `PAYFLOW_KAFKA_CONSUMER_ENABLED` | `false` |
| `PAYFLOW_OUTBOX_PUBLISHER_ENABLED` | `false` |
| `PAYFLOW_WEBHOOK_DELIVERY_ENABLED` | `false` |

Rationale: outbox/webhook/Kafka workers add RAM, disk, and failure surface. Portfolio demo still shows authorize/capture/refund/ledger/analytics UI with workers off; enable later only if memory and ops allow â€” still within â‚¹0.

**Disk:** Docker images + Postgres data for a sandbox demo should fit in a 50â€“80 GB boot volume; keep Object Storage for off-box dumps (20 GB Always Free).

**Egress:** Recruiter demo traffic is negligible vs 10 TB/month Always Free outbound.

## 5. Zero-cost network / TLS / DNS plan

| Layer | â‚¹0 approach |
|-------|-------------|
| Network | Single public IP on the A1 VM; security list / NSG: **22** (admin IP only), **80**, **443**. No published Postgres/Redis/backend ports. |
| TLS | Terminate on host Nginx (or reuse PayFlow frontend Nginx HTTPS pattern from Task 9.2) with **Letâ€™s Encrypt** (certbot / acme.sh). |
| Hostname | Prefer **no paid domain**: `https://<public-ip>.sslip.io` or DuckDNS free subdomain pointed at the VM. Set `PAYFLOW_CORS_ALLOWED_ORIGINS` to that exact `https://â€¦` origin (prod validator rejects localhost). |
| HTTPâ†’HTTPS | Redirect 80â†’443; keep `Secure` cookies (do not weaken). |
| HSTS | Optional short max-age only after hostname is stable; avoid sticky mistakes on throwaway DNS. |

Do **not** buy a domain for this phase. Do **not** assume Oracleâ€™s free certificates product is required â€” Letâ€™s Encrypt is sufficient.

## 6. Alternatives (comparison)

| Provider | Permanent free compute? | Fit for full PayFlow stack | â‚¹0 recurring? | Notes |
|----------|-------------------------|----------------------------|---------------|-------|
| **Oracle Always Free A1** | **Yes** (2 OCPU / 12 GB) | **Best fit** if capacity exists | **Yes** if staying Always Free | Capacity shortages common; idle reclaim; card verification |
| **Oracle AMD E2.1.Micro Ã—2** | Yes (2Ã— ~1 GB) | **Poor** â€” Java+Postgres+Redis will not fit one micro; split-host complexity high | Yes | Not recommended as primary |
| **Google Cloud Always Free** | **Yes**: 1Ã— `e2-micro` in us-west1 / us-central1 / us-east1 + 30 GB disk + **1 GB** egress/month | **Insufficient RAM** (~1 GB) for Compose stack | Yes for that tiny VM | `$300` trial credit is **temporary** â€” do not depend on it for permanent demo |
| **AWS Free plan (new accounts)** | EC2 is **not** a permanent Always Free VM for new signups; **temporary** credits (e.g. up to ~$200 / ~6 months per AWS Free Tier announcements) | Possible briefly on credit, then **paid** | **No** for permanent â‚¹0 | Reject for permanent portfolio hosting |

**Conclusion:** Only Oracle A1 meets â€œpermanent-ish free VM large enough for PayFlow.â€ GCP/AWS are fallbacks only for experiments that expire or are too small â€” not for the â‚¹0 standing demo.

## 7. Account, eligibility, and billing safeguards

| Topic | Guidance |
|-------|----------|
| Signup | Valid email, country/home region choice (Always Free compute is home-region bound), identity verification |
| Card | Credit/debit often required; Oracle may place a **temporary authorization hold** (typically released in days) â€” not a PayFlow charge, but monitor the bank statement |
| Multiple free accounts | Prohibited by Oracle terms â€” do not create extras to chase capacity |
| Idle / abandoned | Idle Always Free **compute** may be reclaimed (7-day utilization rules). Separate risk: accounts idle ~30+ days may face suspension per Oracle Free Tier FAQ â€” keep the demo reachable or document offline fallback |
| Accidental billing | Never click â€œUpgradeâ€ / Pay As You Go for capacity. Set budgets/alerts to **â‚¹0** / $0 if available. Delete any nonâ€“Always Free resource immediately. Prefer compartment quotas locking paid shapes |
| Free Trial $300 | Optional trial credits exist; **do not use them** for PayFlow if that risks creating paid resources after expiry. Prefer Always Freeâ€“only usage from day one |

**Approval gates (human):**

1. Confirm home region and that A1 is Always Freeâ€“eligible there.
2. Attempt instance create; if capacity fails â†’ stop (fallback path).
3. Confirm billing console shows **â‚¹0** expected charges before installing Docker.
4. Only then install Docker, build ARM images, bring up Compose with Kafka/webhooks **off**.
5. TLS + free hostname + CORS update.
6. Smoke: ping, login, Secure cookies, actuator metrics blocked.

## 8. Free-tier-safe backup strategy

Do **not** rely on paid snapshot SKUs or extra paid disks.

| Method | How (â‚¹0) |
|--------|----------|
| Logical Postgres dump | Adapt [`scripts/backup-deploy-postgres.ps1`](../scripts/backup-deploy-postgres.ps1) / `pg_dump -Fc` on the VM |
| Off-box copy | Upload dump to **Always Free Object Storage** (â‰¤20 GB). Encrypt client-side if desired |
| Volume backups | At most **5** Always Free boot/block backups â€” rotate; do not create a 6th (fails / may leave Always Free) |
| Secrets | JWT PEMs + `.env` stored offline (password manager / encrypted USB) â€” never in Git |
| Restore drill | Restore into a **disposable** Postgres container/volume on the same VM; never overwrite live without approval |

This is logical backup, not PITR (same limitation as local [BACKUP_RECOVERY.md](BACKUP_RECOVERY.md)).

## 9. Fallback strategy (if no free VM)

If A1 cannot be acquired without payment:

1. **Local deploy HTTPS** (Task 9.2) â€” Secure-cookie proof on loopback.
2. **Recorded demo** â€” screen capture of register â†’ login â†’ payment/refund â†’ analytics.
3. **Optional:** share private GitHub + CI green later (Task 9.1) without a public URL.

Do **not** rent a paid VPS to â€œfinishâ€ Phase 9 under this budget.

## 10. Risks summary

| Risk | Severity | Mitigation |
|------|----------|------------|
| A1 capacity unavailable | High | Mark unverified; retry AD/time; fallback local/recorded |
| Idle reclaim stops VM | Medium | Light cron/health traffic; document restart steps |
| Accidental paid upgrade | High | Never upgrade; budget alerts; Always Freeâ€“only discipline |
| ARM build surprise | Medium | Build on A1; smoke health/readiness |
| 12 GB tighter than old 24 GB docs | Medium | Keep Kafka/webhooks off; watch `docker stats` |
| Free DNS / LE rate limits | Low | sslip.io or DuckDNS; cache certs |
| Card auth holds / account suspension | Medium | Use legitimate identity; monitor email/billing |

## 11. Deployment safety checklist (manual â€” no provisioning here)

| Topic | Action |
|-------|--------|
| Eligibility | Sign up for Oracle Free Tier; Always Free compute only in **home region** |
| Capacity check | Console â†’ Compute â†’ Create instance â†’ shape `VM.Standard.A1.Flex`, 2 OCPU / 6â€“12 GB. If *out of host capacity*, retry other ADs / later â€” **do not upgrade** |
| Avoid paid shapes | Never select nonâ€“Always Free shapes, GPU, or paid block beyond 200 GB |
| Storage / network | Stay within 200 GB block, 20 GB object, 10 TB egress |
| Firewall / security list | Ingress: **22** (your IP), **80**, **443**. No 5432/6379/8080 from internet |
| Private DB/Redis/backend | Compose keeps them on internal network only â€” host publish web only |
| Free TLS / DNS | Letâ€™s Encrypt + `sslip.io` or DuckDNS (see [`.env.a1.example`](../.env.a1.example)) |
| Idle reclaim | Always Free VMs may be reclaimed after ~7 days low utilization â€” keep light health traffic or accept restart |
| Off-instance backup | `pg_dump` â†’ Always Free Object Storage (â‰¤20 GB); â‰¤5 volume backups; secrets offline |

## 12. Config artifacts added (Task 9.4)

| File | Purpose |
|------|---------|
| [`infra/docker-compose.deploy.arm64.yml`](../infra/docker-compose.deploy.arm64.yml) | `platform: linux/arm64`, free-tier JVM defaults, Kafka/webhooks off |
| [`scripts/build-deploy-images-arm64.ps1`](../scripts/build-deploy-images-arm64.ps1) | Local Buildx arm64 `--load` |
| [`.env.a1.example`](../.env.a1.example) | Placeholders only â€” no IPs/domains/secrets hardcoded |
| `PAYFLOW_DEPLOY_BIND` in deploy Compose | Default `127.0.0.1`; set `0.0.0.0` on VM only after TLS |

Base HTTP deploy Compose remains the default for amd64 local smoke.

## 13. What was **not** done

- No OCI account creation or VM
- No public endpoint, DNS, or Letâ€™s Encrypt on the internet
- No registry push, GitHub push, or git commit
- No native Ampere verification
- A1 capacity in any region still **UNVERIFIED**

## 14. Next steps (after approval)

1. User decides: attempt Oracle Always Free A1 **or** local/recorded fallback.
2. If attempting: confirm **capacity** before installing Docker.
3. On the VM: `build-deploy-images-arm64.ps1`, env from `.env.a1.example`, Compose + arm64 override, TLS, CORS.
4. Optional parallel: private GitHub remote CI (Task 9.1).

Do not provision cloud resources unless explicitly approved.
