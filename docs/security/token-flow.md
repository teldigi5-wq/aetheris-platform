# Aetheris Identity Security — Token Flow and Threat Model

## Stage 2.5 scope

Aetheris uses a dedicated Identity Service for authentication, Spring Cloud Gateway for centralized request enforcement, and role claims for authorization decisions. The local development stack remains free to run and does not depend on a hosted identity provider.

## Token lifecycle

Aetheris keeps two client contracts deliberately separate.

### Direct API clients

1. A non-browser client registers or signs in through `POST /api/auth/register` or `POST /api/auth/login` on the Gateway.
2. The Identity Service verifies credentials and returns a short-lived signed JWT access token, a longer-lived opaque refresh token, and account identity/role metadata.
3. The client sends the access token as `Authorization: Bearer <token>` for protected requests.
4. When the access token expires, the client presents the opaque refresh token to `POST /api/auth/refresh`.
5. `POST /api/auth/logout` revokes the presented refresh token.

The direct API contract is retained for CLI/integration clients and hosted runtime proofs. Those clients are responsible for protecting their returned refresh credential.

### Dashboard browser

1. The browser continues to call the public dashboard URLs `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, and `/api/auth/logout`.
2. Dashboard nginx internally maps only those four exact paths to `/api/auth/browser/*` and injects a dashboard marker plus the original browser `Host` value. Direct public access to `/api/auth/browser/*` through the dashboard is blocked.
3. The browser-auth controller independently requires an `Origin` header whose plain `http(s)://authority` exactly matches that original browser host. Missing, malformed, cross-origin, path-bearing, query-bearing, fragment-bearing, or user-info origins are rejected before authentication state is changed.
4. On browser registration/login/refresh, the Identity Service places the opaque refresh credential in a host-only `HttpOnly; SameSite=Strict` cookie scoped to `/api/auth`. The cookie is `Secure` by default. The local HTTP Docker Compose profiles explicitly set `AETHERIS_BROWSER_REFRESH_COOKIE_SECURE=false`; that downgrade is not the service default.
5. Browser JSON never contains the opaque refresh credential. The compatibility field `refreshToken` contains only the non-secret sentinel `COOKIE_BOUND`, allowing existing dashboard and historical stage-page JavaScript to keep its current response shape without receiving the bearer refresh secret.
6. The dashboard currently keeps the short-lived access token, account context, and `COOKIE_BOUND` sentinel in `sessionStorage`. On refresh, the browser automatically supplies the HttpOnly cookie; the Identity Service ignores the JSON sentinel and rotates the cookie credential.
7. Logout revokes the cookie-bound refresh token and expires the cookie.
8. Successful browser auth responses use `Cache-Control: no-store`; browser-auth error responses are also non-cacheable.

## Refresh-token storage and rotation

Raw refresh tokens are never persisted. Aetheris stores only a SHA-256 hash of each refresh token in PostgreSQL. If the database is exposed, the stored value cannot be directly replayed as a refresh credential.

Refresh tokens are single-use. Rotation first performs a conditional database update that revokes the presented token only while it is still active and unexpired. A replacement is issued only when that atomic claim updates exactly one row, so concurrent replay attempts cannot each mint a valid descendant.

For the browser contract, the rotated replacement is written only to the HttpOnly cookie. For direct API clients, it is returned in the API response body.

## Authorization model

Current roles are:

- `ADMIN`
- `DEVELOPER`
- `API_CONSUMER`

All authenticated roles may read `/api/users`. User-modifying methods require `ADMIN` or `DEVELOPER`. The Gateway returns:

- `401 Unauthorized` when authentication is missing, invalid, or expired;
- `403 Forbidden` when authentication succeeds but the role lacks permission.

## Trust boundaries

### Browser → dashboard nginx → Gateway

The browser is untrusted. Dashboard nginx is the browser-auth boundary for the four exact public auth paths. It supplies the internal browser marker and original host; the Identity Service still validates the actual `Origin` independently. The Gateway must independently validate access tokens on protected APIs and must never trust caller-supplied `X-Aetheris-User-*` identity headers.

The current CSP still permits inline script/style because historical stage pages are shipped in the dashboard artifact and contain inline code. Moving the refresh credential to an HttpOnly cookie limits the impact of that browser surface, but eliminating `'unsafe-inline'` remains a separate hardening task.

### Gateway → internal services

After JWT validation, the Gateway injects internal identity headers such as user ID, email, role, and scopes. Internal services should eventually add service-level authorization checks as defense in depth rather than relying only on the Gateway.

### Identity Service → PostgreSQL

Passwords are stored as BCrypt hashes. Refresh credentials are stored only as SHA-256 hashes. Database credentials and JWT signing secrets must be replaced for non-development deployments.

## Threats and mitigations

| Threat | Current mitigation | Future hardening |
|---|---|---|
| Stolen password database | BCrypt password hashing | Tune cost factor and add breach-password checks |
| Stolen access token | Short expiry and signature validation | Keep browser access token only in memory; add issuer/audience validation and key rotation |
| Stolen refresh-token table | Only token hashes are persisted | Device/session metadata and reuse-family detection |
| Refresh-token replay | Single-use rotation with an atomic active-token claim; concurrent losers are rejected | Revoke entire token family on detected reuse |
| Browser refresh-token theft by JavaScript | Dashboard refresh credential is HttpOnly, SameSite=Strict, Secure by default, and never returned in browser JSON | Refactor historical inline stage scripts and remove CSP `'unsafe-inline'` |
| Browser CSRF against cookie auth | SameSite=Strict plus browser Origin authority must match the original dashboard Host | Add an explicit anti-CSRF token if browser auth is ever expanded to cross-site deployment patterns |
| Missing authentication | Gateway JWT filter | Service-level validation |
| Privilege misuse | Gateway RBAC/scopes | Finer-grained contextual permissions |
| Secret leakage | Environment-configurable JWT secret; refresh bearer not written into browser evidence | Secret manager / mounted secrets in production |
| Browser access-token persistence | Only the short-lived access token remains JavaScript-readable in the current dashboard session | Move access token to memory and restore session through the HttpOnly refresh cookie |

## Development vs production

The default JWT secret and the local HTTP cookie-security override exist only to keep local development and hosted Docker Compose proofs simple. They must not be used as production deployment values. Production should use a high-entropy externally supplied secret or asymmetric signing keys, TLS everywhere, secure secret storage, issuer/audience validation, key rotation, stricter CORS, rate limiting, audit logging, and preferably an established OAuth 2.0 / OpenID Connect provider or standards-compatible authorization server.

The browser refresh cookie defaults to `Secure=true`; only the explicitly local HTTP Compose profiles set it to false. Hosted CI evidence remains `HOSTED_RUNTIME` evidence and does not prove an internet deployment or physical machine.

## Interview explanation

Aetheris separates authentication from authorization and also separates browser credential handling from direct API-client handling. The Identity Service proves who the caller is and issues credentials. The Gateway validates access tokens and enforces coarse-grained policy before traffic reaches backend services. Refresh tokens are stateful, revocable, atomically claimed during rotation, and stored only as hashes. Direct API clients receive their opaque refresh token explicitly, while the dashboard browser receives it only as an HttpOnly same-origin-bound cookie. This demonstrates token lifecycle management, least privilege, central policy enforcement, credential-channel separation, and defense-in-depth planning.