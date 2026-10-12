# PayFlow — API Authentication (Phase 2)

## Credential types

| Type | Transport | Used for |
|------|-----------|----------|
| User access JWT | `Authorization: Bearer <jwt>` | `/api/v1/users/**`, `/api/v1/merchants/**` |
| Refresh token | JSON body `{ "refreshToken": "..." }` | `/api/v1/auth/refresh`, `/logout` |
| Merchant secret key | `Authorization: Bearer sk_test_...` | `/api/v1/integrations/**` (future payment APIs) |
| Publishable key | `pk_test_...` | Identification only — **not** auth |

JWT and API-key Bearer values are deliberately non-overlapping formats.

## Auth endpoints

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/v1/auth/register` | Public |
| POST | `/api/v1/auth/login` | Public |
| POST | `/api/v1/auth/refresh` | Public (refresh body) |
| POST | `/api/v1/auth/logout` | Public (refresh body) |
| GET | `/api/v1/users/me` | JWT |
| GET | `/api/v1/integrations/me` | Secret API key |

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
- Frontend must choose cookie+CSRF or equivalent — do not assume `localStorage` for long-lived secrets
