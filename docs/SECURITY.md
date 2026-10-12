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

## Explicit limitations

1. **No email verification** in Phase 2.
2. Access JWTs remain valid until expiry after logout (no denylist yet).
3. Browser token storage/CSRF strategy is **not** finalized — API returns JSON tokens for local/automated use only.
4. Rate limiting is basic (IP-based), not account-aware abuse protection.
5. ADMIN role exists in schema but **no endpoint grants ADMIN**.
6. LIVE API key mode is modeled (`mode`) but only TEST keys are issued.
7. Not PCI DSS, SOC 2, or GDPR certified.

## Operational guidance

- Rotate `API_KEY_HASH_PEPPER` carefully (invalidates stored secret-key hashes).
- Back up JWT private keys; losing them invalidates outstanding access tokens.
- Never commit `.env`, PEM keys, or raw API secrets.
