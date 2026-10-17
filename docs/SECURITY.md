# PayFlow — Security Notes

**Status:** Portfolio hardening through Phase 8.8 + Task 9.2 local HTTPS Secure-cookie verify — **not** a claim of production, PCI, or fraud-certification compliance.

## Implemented controls

| Area | Approach |
|------|----------|
| Passwords | BCrypt via Spring `PasswordEncoder`; never logged or returned |
| Access tokens | Short-lived RS256 JWTs (default 15m); issuer + audience + `token_use=access` validated |
| Signing keys | RSA PEM files outside Git; **auto-generate forbidden** on `prod`; missing PEMs fail startup |
| Refresh tokens | Opaque random tokens; **SHA-256 hash** stored in PostgreSQL; rotation + reuse detection |
| Logout | Revokes refresh token **family**; does **not** instantly revoke already-issued access JWTs |
| API secret keys | `sk_test_…` only; **HMAC-SHA256(pepper, secret)** stored; raw secret returned once |
| Publishable keys | `pk_test_…`; cannot authenticate privileged endpoints |
| Auth distinction | Bearer JWT vs `sk_test_`/`pk_test_` classified separately; API keys never parsed as JWTs |
| Authorization | Service-layer ownership checks; cross-tenant access returns **404** |
| Rate limiting | Redis counters on register/login/refresh; **fail-closed** if Redis unavailable |
| Errors | JSON `ApiError` without stack traces (`include-stacktrace: never`) |
| CORS | Profile-driven explicit origins (no `*`); `prod` requires `PAYFLOW_CORS_ALLOWED_ORIGINS` and rejects localhost |
| Browser SPA session | Access JWT in memory only; refresh HttpOnly `payflow_refresh`; `Secure` + SameSite Lax/Strict required on `prod` |
| CSRF | Required for `/api/v1/auth/browser/{login,refresh,logout}`; non-HttpOnly `XSRF-TOKEN` + `X-XSRF-TOKEN`; cookie `Secure` mirrors browser-session |
| SPA secret UX | API-key / webhook signing secrets revealed once in UI; not query-cached |
| Clipboard | Copy Secret may leave plaintext in the OS clipboard until overwritten |
| OpenAPI UI | Enabled for `local`; **disabled** for `prod` / tests |
| Actuator | `prod` exposes **health** (+ liveness/readiness groups) only (`show-details: never`); metrics ADMIN-gated when exposed; Nginx proxies exact health probes only |
| Request correlation | `X-Request-Id` generated/propagated; MDC `correlationId` in logs; never logs Authorization/Cookie/JWT/passwords/API secrets |
| Webhook signing secrets | AES-256-GCM at rest via required `WEBHOOK_SECRET_ENCRYPTION_KEY` (32-byte Base64) |
| Webhook URL / delivery / SSRF | Unchanged Phase 5 controls; delivery **off** unless explicitly enabled |
| Kafka / outbox workers | **Off** by default on `prod`; explicit env opt-in |
| Payments | Simulated sandbox processor only — no live gateways |
| Nginx SPA | Same-origin `/api/v1` proxy; no arbitrary upstreams; CSP without `unsafe-eval` |
| Prod fail-fast | `ProductionSecurityValidator` rejects missing/weak DB password, pepper, webhook key, JWT paths, insecure cookies, empty/localhost CORS |

## Explicit limitations

1. **No email verification** in Phase 2.
2. Access JWTs remain valid until expiry after logout (no denylist yet).
3. Browser SPA uses HttpOnly refresh cookie + CSRF; JSON token APIs remain for non-browser clients. Access JWTs are still not instantly revoked on logout.
4. Rate limiting is basic (IP-based), not account-aware abuse protection.
5. `ADMIN` is not self-assignable. Registration grants `USER` only. There is no role-grant API. Putting `ADMIN` in `user_roles` is an explicitly trusted operator action and must be recorded in the operator's own change log; the application does not write an audit row for that insert. After the insert, that user logs in and the JWT `roles` claim includes `ADMIN`. `GET /api/v1/internal/events/health` and `/actuator/metrics` require that role. `EventRecoveryService` requeues terminal `FAILED` rows internally and audits `outbox_event_requeued` / `webhook_delivery_requeued` with the id only. HTTP retry endpoints are not exposed.
6. LIVE API key mode is modeled (`mode`) but only TEST keys are issued.
7. Not PCI DSS, SOC 2, or GDPR certified.
8. Webhook secret **rotation API** is designed but not implemented (immediate invalidation of old secret planned).
9. Webhook delivery is at-least-once; receivers must verify signatures and dedupe by event id.
10. Local TLS fixture tests cover chain validation, SNI, hostname checks, pinned-IP connect, redirect rejection, read timeout, and overall timeout. A dropped-packet connect-timeout against an off-box blackhole was not run. Closed-port connects fail as `io_error` and do not fall back to another address.
11. Risk engine is a **simulated** rule evaluator — not a production-certified fraud detection system. No rule-management HTTP APIs yet (rules seeded via DB/tests).
12. REVIEW holds payment in `CREATED` without a manual approval workflow; re-authorize is rejected while the assessment exists.
13. Multi-instance clock skew is not corrected for risk velocity windows or analytics default “today”.
14. CSP still allows `style-src 'unsafe-inline'` for React/Recharts inline styles; tighten when UI no longer needs it.
15. TLS certificates are not stored in Git; terminate TLS at a reverse proxy and set `X-Forwarded-Proto` (`server.forward-headers-strategy: framework` on `prod`).
16. Deploy Compose (`infra/docker-compose.deploy.yml`) keeps DB/Redis/backend off the host network; only the frontend publishes a loopback port. Remote GitHub Actions runs not verified until push.
17. Task **9.2** local HTTPS override (`infra/docker-compose.deploy.https.yml`) terminates TLS in the existing Nginx frontend (no extra proxy container). Self-signed certs live under `secrets/local-https/` (gitignored). Playwright used narrowly scoped `ignoreHTTPSErrors` — that proves Secure-cookie session behavior, **not** OS-trusted certificate validation. No HSTS on the local HTTPS template (avoids sticky localhost browser HSTS).
18. `ProductionSecurityValidator` still rejects localhost CORS on `prod`. Local HTTPS E2E relies on same-origin Nginx proxy; do not weaken Secure cookies or the validator for convenience.
19. Application logs may retain correlation ids on the host Docker volume; rotate/redact before sharing dumps. Backup dumps under `backups/` are sensitive — keep ACLs tight and never commit them (see [BACKUP_RECOVERY.md](BACKUP_RECOVERY.md)).

## Logging redaction expectations

- Do not log JWTs, passwords, raw API keys, webhook signing secrets, Authorization/Cookie headers, or full payment request bodies.
- Prefer identifiers (paymentId, merchantId, eventId) and status/reason codes.
- Unexpected errors log an `errorId` + correlation id server-side; API responses stay generic (`internal_error`).
- Retention: Docker daemon defaults unless operators configure log drivers; no ELK/Prometheus in this phase.

## Production secrets (placeholders only)

See [`.env.production.example`](../.env.production.example). Required at minimum:

| Variable | Purpose |
|----------|---------|
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Postgres (no local defaults on `prod`) |
| `REDIS_HOST` | Redis |
| `JWT_PRIVATE_KEY_PATH` / `JWT_PUBLIC_KEY_PATH` | Mounted RSA PEMs |
| `API_KEY_HASH_PEPPER` | ≥32 chars; not a local placeholder |
| `WEBHOOK_SECRET_ENCRYPTION_KEY` | Base64 of exactly 32 raw bytes |
| `PAYFLOW_CORS_ALLOWED_ORIGINS` | Comma-separated `https://…` origins (no localhost) |

## Operational guidance

- Rotate `API_KEY_HASH_PEPPER` carefully (invalidates stored secret-key hashes).
- Back up JWT private keys; losing them invalidates outstanding access tokens.
- Never commit `.env`, PEM keys, raw API secrets, or `WEBHOOK_SECRET_ENCRYPTION_KEY`.
- Losing/rotating `WEBHOOK_SECRET_ENCRYPTION_KEY` makes existing webhook ciphertext unreadable — plan re-encryption or re-issue before rotation.
- Prefer same-origin Nginx SPA + API proxy so refresh cookies stay first-party; do not put refresh tokens in JS storage.
- Deploy Compose: prepare PEMs/env outside Git (`.\scripts\prepare-deploy-smoke-secrets.ps1` → `secrets/deploy-smoke/`); never commit that directory.
- Local HTTPS smoke: `.\scripts\prepare-deploy-https-smoke.ps1` → `secrets/deploy-https/` + `secrets/local-https/`; never commit. See [SETUP.md](SETUP.md) §12a.
- Day-to-day ops: [OPERATIONS.md](OPERATIONS.md). Postgres dump/restore: [BACKUP_RECOVERY.md](BACKUP_RECOVERY.md) (no automatic live overwrite).
