# Phase 12 Slice 11 — OAuth Lifecycle Continuity

Phase 12 Slice 11 extends the stable provider-account invariant from live connector writes into the OAuth credential lifecycle itself.

Slice 10 already guaranteed that a Gmail, Calendar, or GitHub live write cannot execute unless the exact access token resolves to the durable provider account stored on the connector connection. The remaining gap was earlier in the lifecycle: OAuth completion and refresh could persist or rotate a credential before proving that the newly issued access token still belonged to that same durable account, while provider health could report a valid token without requiring the returned provider subject to match the connection binding.

This slice closes those three lifecycle boundaries.

## Durable identity invariant

For every connector connection, `externalAccountRef` remains the durable stable provider account identifier:

- GitHub: numeric user `id`.
- Gmail / Google Calendar: OpenID Connect `sub`.

`OAuthLifecycleAccountContinuityService` compares that durable reference with the stable provider identity resolved from the candidate access token. Comparison is exact after surrounding whitespace normalization. Email addresses, GitHub login names, display names, aliases, prefixes, and wildcards are not accepted as identity substitutes.

Failures are fail closed and return a generic lifecycle continuity error that does not include the access token, expected account identifier, or observed account identifier.

## OAuth completion

After an authorization code is exchanged, the candidate access token is checked against the connection's durable provider account **before** the access token or refresh token is written to the credential vault and before a credential entity is created or rotated.

Therefore a user completing the OAuth browser flow while authenticated to a different provider account cannot silently rebind an existing Aetheris connector.

The credential provider must also match the connector provider before an existing credential can be rotated.

## Refresh-token rotation

A refresh response is treated as an untrusted candidate credential until the new access token resolves to the same durable provider account.

Account continuity is checked before the new access token is stored and before the persisted credential references are rotated. A provider-account change therefore leaves the previously accepted credential references untouched.

## Provider health

Health validation now requires both conditions:

1. the provider identity endpoint reports a healthy response; and
2. the returned stable provider account identifier exactly matches the connector's durable `externalAccountRef`.

If the provider token is valid but belongs to a different account, the credential is validated as unhealthy and the health response is fail closed with a generic account-continuity message. The mismatched provider account identifier and display name are not returned.

## Provider-bound credential metadata

Completion, refresh, and health also verify that any existing `ProviderCredentialEntity.provider` matches the owning `ConnectorConnectionEntity.provider`. Cross-provider credential reuse is rejected before lifecycle mutation.

## Acceptance proof

`Phase12OAuthLifecycleContinuityTest` proves:

- the same stable provider account remains valid across token changes;
- a rebound token for another provider account fails closed;
- compared account identifiers and access tokens are not included in the lifecycle error;
- a missing durable account reference fails before provider identity lookup;
- provider identity-resolution failure is rewrapped without token leakage;
- health identity matching uses the exact stable account reference.

The dedicated `Phase 12 OAuth Lifecycle Continuity Proof` workflow additionally verifies source ordering:

- OAuth completion checks stable account identity before access-token persistence;
- token refresh checks stable account identity before credential rotation;
- provider health includes the durable account identity comparison before marking the credential healthy;
- completion, refresh, and health enforce connector/credential provider consistency.

## Security effect

After this slice, account continuity is enforced at all currently implemented consequential connector credential boundaries:

`OAuth completion -> credential storage -> refresh rotation -> health validation -> live write execution`

A newly issued or rotated token for another provider account is not accepted simply because it is otherwise valid OAuth material.

## Scope boundary

This slice does not add new providers, connector write actions, authentication protocols, secrets backends, database tables, agent capabilities, shell authority, live-money execution, or physical-device control.

It also does not claim provider-side revocation webhooks or continuous background token introspection. Those are separate lifecycle capabilities and should only be claimed after dedicated runtime proof.

## Rollback

Revert `OAuthLifecycleAccountContinuityService`, the lifecycle checks in `ConnectorOAuthService`, `Phase12OAuthLifecycleContinuityTest`, the dedicated proof workflow, and this document as one unit.
