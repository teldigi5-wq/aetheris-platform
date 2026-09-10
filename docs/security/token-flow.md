# Aetheris Identity Security — Token Flow and Threat Model

## Stage 2.5 scope

Aetheris uses a dedicated Identity Service for authentication, Spring Cloud Gateway for centralized request enforcement, and role claims for authorization decisions. The local development stack remains free to run and does not depend on a hosted identity provider.

## Token lifecycle

1. A client registers or signs in through `POST /api/auth/register` or `POST /api/auth/login`.
2. The Identity Service verifies credentials and returns:
   - a short-lived signed JWT access token;
   - a longer-lived opaque refresh token;
   - account identity and role metadata.
3. The dashboard keeps the active session in `sessionStorage` and sends the access token as `Authorization: Bearer <token>` for protected requests.
4. The Gateway validates the JWT signature and required identity claims before forwarding protected `/api/users/**` traffic.
5. When the access token expires, the dashboard sends the refresh token to `POST /api/auth/refresh`.
6. Refresh tokens are single-use. The Identity Service revokes the presented token and issues a replacement refresh token plus a new access token.
7. `POST /api/auth/logout` revokes the current refresh token. The dashboard then removes its local session state.

## Refresh-token storage

Raw refresh tokens are never persisted. Aetheris stores only a SHA-256 hash of each refresh token in PostgreSQL. If the database is exposed, the stored value cannot be directly replayed as a refresh credential.

## Authorization model

Current roles are:

- `ADMIN`
- `DEVELOPER`
- `API_CONSUMER`

All authenticated roles may read `/api/users`. User-modifying methods require `ADMIN` or `DEVELOPER`. The Gateway returns:

- `401 Unauthorized` when authentication is missing, invalid, or expired;
- `403 Forbidden` when authentication succeeds but the role lacks permission.

## Trust boundaries

### Browser → Gateway

The browser is untrusted. The Gateway must independently validate access tokens and must never trust caller-supplied `X-Aetheris-*` identity headers.

### Gateway → internal services

After JWT validation, the Gateway injects internal identity headers such as user ID, email, and role. Internal services should eventually add service-level authorization checks as defense in depth rather than relying only on the Gateway.

### Identity Service → PostgreSQL

Passwords are stored as BCrypt hashes. Refresh credentials are stored only as SHA-256 hashes. Database credentials and JWT signing secrets must be replaced for non-development deployments.

## Threats and mitigations

| Threat | Current mitigation | Future hardening |
|---|---|---|
| Stolen password database | BCrypt password hashing | Tune cost factor and add breach-password checks |
| Stolen access token | Short expiry and signature validation | Key rotation, audience/issuer validation |
| Stolen refresh-token table | Only token hashes are persisted | Device/session metadata and reuse-family detection |
| Refresh-token replay | Single-use rotation and revocation | Revoke entire token family on reuse |
| Missing authentication | Gateway JWT filter | Service-level validation |
| Privilege misuse | Gateway RBAC | Fine-grained scopes/permissions |
| Secret leakage | Environment-configurable JWT secret | Secret manager / mounted secrets in production |
| Browser persistence | `sessionStorage` limits persistence to the tab/session | HttpOnly secure cookie design for internet deployment |

## Development vs production

The default JWT secret exists only to keep local development simple. It must not be used in a real deployment. Production should use a high-entropy externally supplied secret or asymmetric signing keys, TLS everywhere, secure secret storage, issuer/audience validation, key rotation, stricter CORS, rate limiting, audit logging, and preferably an established OAuth 2.0 / OpenID Connect provider or standards-compatible authorization server.

## Interview explanation

Aetheris separates authentication from authorization. The Identity Service proves who the caller is and issues credentials. The Gateway validates those credentials and enforces coarse-grained policy before traffic reaches backend services. Access tokens are stateless and short-lived, while refresh tokens are stateful, revocable, rotated on use, and stored only as hashes. This design demonstrates token lifecycle management, least privilege, central policy enforcement, and defense-in-depth planning.
