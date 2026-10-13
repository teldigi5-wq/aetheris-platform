# Aetheris signed JWT issuer and audience contract

**Status: coordinated draft only; NOT deployed.** This change is security
hardening for Syntra / Aetheris owner-bound mission authorization.

## Source identity contract
Platform identity service signs JJWT 0.12.6 HMAC access tokens containing:
- `iss`: `aetheris-identity` (configurable with `AETHERIS_JWT_ISSUER`)
- `aud`: `aetheris-api` (configurable with `AETHERIS_JWT_AUDIENCE`)
- `sub`: canonical identity account email
- `uid`: persisted numeric `IdentityAccount.id` (**Long / BIGINT, not UUID**)
- `role`: enum ADMIN/DEVELOPER/API_CONSUMER
- `scopes`: authorized role scopes
- `iat` and `exp`: issuance/expiry timestamps.

The platform gateway now requires a verified signature **and** matching
`iss`, `aud`, positive numeric `uid`, and relevant route scope before
setting sanitized verified identity headers. It never accepts client-supplied
identity headers as proof of authentication.

## Security and compatibility

**IMPORTANT rollout-breaking change:** tokens issued *before* this change
have no issuer/audience claims and will be rejected once strict gateway
validation is enabled. Do **not** deploy gateway alone. Coordinate identity,
gateway, runtime verifier and client session rollout; expire/revoke old access
tokens and require sign-in after the controlled transition. Review refresh
token continuity and session UX. Do not introduce an insecure compatibility
toggle that accepts either format indefinitely.

The `AETHERIS_JWT_SECRET` must be strong, nondefault, protected from logs,
frontend code and external ports, with a reviewed key rotation procedure.
The existing development fallback is not production-safe. A shared HMAC key
also means every service with the key could mint valid tokens; asymmetric
signing with issuer-owned private keys and public-key-only verifiers should
be evaluated in the future.

Issuer and audience validation is **not** a substitute for authorization.
`orchestrator:read` does not identify which task or mission belongs to the
user, and gateway headers are not safe at an untrusted direct runtime port.
The runtime must separately verify its tokens and authorize owner/project
binding on **every** read, write and live SSE subscription. Platform draft
PR #227 and runtime drafts #24–#26 remain blocked/default-deny. This
change only prepares the signed token contract.

## Verification
GitHub Actions `Aetheris JWT Issuer Audience Contract` runs complete
identity-service and gateway Maven test suites, including:
- signed issuer/audience/uid issuance
- rejection of mismatched or absent issuer/audience
- scope, expiration and identity-header spoofing checks
- rejection of unpersisted account IDs and invalid issuance configuration.

**No protected-main merge, production deployment or physical-PC testing is
asserted from CI passing.**
