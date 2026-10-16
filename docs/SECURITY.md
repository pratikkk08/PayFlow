# PayFlow — Security Notes (Phase 2)

**Status:** Local/portfolio hardening — **not** a claim of production or regulatory compliance.

## Implemented controls

| Area | Approach |
|------|----------|
| Passwords | BCrypt via Spring `PasswordEncoder`; never logged or returned |
| Access tokens | Short-lived RS256 JWTs (default 15m); issuer + audience + `token_use=access` validated |
| Signing keys | RSA PEM files outside Git (`backend/local-secrets/`); auto-generate only for local/test |
| Refresh tokens | Opaque random tokens; **SHA-256 hash** stored in PostgreSQL; rotation + reuse detection |
| Logout | Revokes refresh token **family**; does **not** instantly revoke already-issued access JWTs |
| API secret keys | `sk_test_…` only; **HMAC-SHA256(pepper, secret)** stored; raw secret returned once |
| Publishable keys | `pk_test_…`; cannot authenticate privileged endpoints |
| Auth distinction | Bearer JWT vs `sk_test_`/`pk_test_` classified separately; API keys never parsed as JWTs |
| Authorization | Service-layer ownership checks; cross-tenant access returns **404** |
| Rate limiting | Redis counters on register/login/refresh; **fail-closed** if Redis unavailable |
| Errors | JSON `ApiError` without stack traces |
| CORS | Explicit localhost Vite origins only (no `*` production policy) |
| OpenAPI UI | Enabled for `local`; disabled for `prod` profile |
| Webhook signing secrets | Per-endpoint `whsec_…` secrets; AES-256-GCM at rest via `WEBHOOK_SECRET_ENCRYPTION_KEY`; plaintext returned once on create |
| Webhook URL registration | HTTPS only; reject credentials, fragments, IP literals, localhost/metadata hostnames. **No outbound fetch at registration** |
| Webhook delivery | Claimed jobs; HMAC-SHA256 `v1` signatures; TCP to the pinned IP only; JVM PKIX plus SNI/hostname checks; `Host` is the original hostname; redirects disabled; delivery off by default |
| Webhook SSRF (delivery) | Re-resolve DNS each attempt; block private/link-local/metadata addresses; connect only to validated IP; refuse redirects; never disable TLS verification |

## Explicit limitations

1. **No email verification** in Phase 2.
2. Access JWTs remain valid until expiry after logout (no denylist yet).
3. Browser token storage/CSRF strategy is **not** finalized — API returns JSON tokens for local/automated use only.
4. Rate limiting is basic (IP-based), not account-aware abuse protection.
5. `ADMIN` is not self-assignable. Registration grants `USER` only. There is no role-grant API. Putting `ADMIN` in `user_roles` is an explicitly trusted operator action and must be recorded in the operator's own change log; the application does not write an audit row for that insert. After the insert, that user logs in and the JWT `roles` claim includes `ADMIN`. `GET /api/v1/internal/events/health` and `/actuator/metrics` require that role. `EventRecoveryService` requeues terminal `FAILED` rows internally and audits `outbox_event_requeued` / `webhook_delivery_requeued` with the id only. HTTP retry endpoints are not exposed.
6. LIVE API key mode is modeled (`mode`) but only TEST keys are issued.
7. Not PCI DSS, SOC 2, or GDPR certified.
8. Webhook secret **rotation API** is designed but not implemented (immediate invalidation of old secret planned).
9. Webhook delivery is at-least-once; receivers must verify signatures and dedupe by event id.
10. Local TLS fixture tests cover chain validation, SNI, hostname checks, pinned-IP connect, redirect rejection, read timeout, and overall timeout. A dropped-packet connect-timeout against an off-box blackhole was not run. Closed-port connects fail as `io_error` and do not fall back to another address.

## Operational guidance

- Rotate `API_KEY_HASH_PEPPER` carefully (invalidates stored secret-key hashes).
- Back up JWT private keys; losing them invalidates outstanding access tokens.
- Never commit `.env`, PEM keys, raw API secrets, or `WEBHOOK_SECRET_ENCRYPTION_KEY`.
- Losing/rotating `WEBHOOK_SECRET_ENCRYPTION_KEY` makes existing webhook ciphertext unreadable — plan re-encryption or re-issue before rotation.
