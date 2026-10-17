# PayFlow — API Authentication (Phase 2)

## Credential types

| Type | Transport | Used for |
|------|-----------|----------|
| User access JWT | `Authorization: Bearer <jwt>` | `/api/v1/users/**`, `/api/v1/merchants/**` |
| Refresh token (API) | JSON body `{ "refreshToken": "..." }` | `/api/v1/auth/refresh`, `/api/v1/auth/logout` |
| Refresh token (browser) | HttpOnly cookie `payflow_refresh` | `/api/v1/auth/browser/refresh`, `/logout` |
| Merchant secret key | `Authorization: Bearer sk_test_...` | `/api/v1/integrations/**` (future payment APIs) |
| Publishable key | `pk_test_...` | Identification only — **not** auth |

JWT and API-key Bearer values are deliberately non-overlapping formats.

## Auth endpoints

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/v1/auth/register` | Public |
| POST | `/api/v1/auth/login` | Public (JSON tokens) |
| POST | `/api/v1/auth/refresh` | Public (refresh body) |
| POST | `/api/v1/auth/logout` | Public (refresh body) |
| GET | `/api/v1/auth/browser/csrf` | Public (issues `XSRF-TOKEN` cookie) |
| POST | `/api/v1/auth/browser/login` | Public + CSRF; refresh cookie set |
| POST | `/api/v1/auth/browser/refresh` | Public + CSRF; cookie in/out |
| POST | `/api/v1/auth/browser/logout` | Public + CSRF; revokes + clears cookie |
| GET | `/api/v1/users/me` | JWT |
| GET | `/api/v1/integrations/me` | Secret API key |

### Browser SPA session (Task 7.3)

- Short-lived **access JWT** returned in JSON only; frontend keeps it **in memory**.
- **Refresh** is never returned in browser JSON; it is stored in an **HttpOnly** cookie (`payflow_refresh`, Path=`/api/v1/auth/browser`, SameSite=`Lax`).
- `Secure` is **false** on `local`/`test` (HTTP localhost); **true** on `prod`.
- Cookie-authenticated browser POSTs require double-submit CSRF (`XSRF-TOKEN` cookie + `X-XSRF-TOKEN` header).
- Existing JSON login/refresh/logout endpoints remain for tests and non-browser clients.

## JWT claims (access)

- `sub` — user UUID
- `iss` — configured issuer (`payflow`)
- `aud` — configured audience (`payflow-api`)
- `token_use` — `access`
- `roles` — platform roles (e.g. `USER`)
- `exp` / `iat`

Email and passwords are **not** placed in JWTs.

## Refresh rotation

1. Login issues access JWT + opaque refresh token (new family).
2. Refresh consumes current token (row lock), issues replacement, returns new pair.
3. Reuse of a consumed refresh token **revokes the entire family**.
4. Logout revokes the family.

## Merchant API keys

- Create: `POST /api/v1/merchants/{id}/api-keys` (JWT owner) — secret returned **once**
- List: metadata only (prefix, timestamps, status)
- Revoke: `DELETE .../api-keys/{keyId}` — idempotent; subsequent API-key auth fails immediately
- Hash: `HMAC-SHA256(API_KEY_HASH_PEPPER, rawSecret)`

## Future payment endpoints

Merchant secret keys will authorize payment/refund APIs scoped to the key’s merchant. User JWT will remain required for dashboard/account management. Publishable keys will never authorize server-side money movement.

## Local testing notes

- Swagger UI: `http://localhost:8080/swagger-ui.html` (local profile)
- Prefer short-lived access tokens; treat refresh tokens as secrets
- SPA uses Vite proxy (`localhost:5173` → `:8080`) so refresh cookies are same-site to the UI origin
- Never store refresh tokens in `localStorage` / `sessionStorage` / IndexedDB
