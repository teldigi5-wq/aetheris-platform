# RSA-only Compose compatibility candidate — not a deployment approval

27 October 2026 · stacked on draft #338 · Stage45 NO_GO.

## Scope

The three existing Compose variants (`docker-compose.yml`,
`docker-compose.core.yml`, `docker-compose.integration-external.yml`)
now map four existing Java authentication services to explicit optional
JWT environment parameters. Default HMAC secret interpolation is no longer
required to render a Compose config, but the actual Java services still
**fail closed** if neither a reviewed RS256 key nor an adequate legacy
HMAC secret exists. No operational credentials, images, volumes, schema
migrations or production settings have changed.

The same operator-provided RSA public PEM can be verified by Gateway,
User and Audit; **only Identity** receives the signing private PEM.
Gateway audiences are distinct, and User and Audit each receive the
matching sole audience. An explicitly missing/invalid issuer or audience
fails a protected API, never falls back to accepting a differently scoped
token. Pinned active and staged public key IDs are forwarded only to
verifiers. No private PEM is placed into a repository or test fixture.

## Synthetic preflight

Run a **non-deploying** configuration check with:

`python3 scripts/security/check_rs256_compose_contract.py --mode legacy`

or

`python3 scripts/security/check_rs256_compose_contract.py --mode rs256`

The program reads operator environment variables, validates mode separation,
key IDs, issuer equality and target audiences and emits only field names and
non-secret errors. No networking, dynamic key download, real owner device,
Docker stack startup, production process or signing operation is involved.
The preflight is NOT automatically enforced by Docker Compose; operator
automation would need to invoke it explicitly before a reviewed deployment.
PEM structure and cryptographic correspondence are checked by the actual
Java RS256 parsers and cryptographic tests, not established by this source
preflight.

Tests prove the three Compose variants carry the intended mappings, reject
shared-HMAC configuration in requested RSA-only preflight mode and fail
incomplete/ambiguous operator input without echoing secrets.

## Important blockers

- **Not a complete RSA-only deployment:** Helm templates, related production
  automation and any other signing consumers remain outside this PR.
- The existing Identity access-token login still issues ONE fixed default
  audience. Real target-specific exchange and authenticated workload identity
  are not implemented. Configuring one recipient does not grant other APIs.
- Private PEM passed via environment variables requires a reviewed
  production secret-store/volume design; container introspection and runtime
  key exposure risks remain. No secure key-custody endorsement is implied.
- Key removal, session migration/revocation, certified DB owner binding and
  nine external Stage45 attestations remain open.
- No protected-main merge, actual service start, key upload, Helm mutation,
  credential deletion or production release is authorized.

Issue #251 OPEN. Stage45 NO_GO; draft and unmerged.
