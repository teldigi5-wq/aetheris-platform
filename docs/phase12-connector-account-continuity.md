# Phase 12 Slice 10 — Connector Account Continuity

Phase 12 Slice 10 closes a write-time credential-rebinding gap in the live connector path. Existing connector controls already required an approved action, a live-authorized task/specialist boundary, an ACTIVE OAuth credential, provider match, required write scope, owner approval, audit evidence and independent task verification. What remained possible was for a credential reference to resolve to a valid access token for a different provider account than the account represented by the durable connector connection.

This slice binds every supported live write to the provider account already stored on the connector connection before the first provider mutation is dispatched.

## Stable provider identity

`ProviderIdentityResolver` resolves an immutable/stable provider subject from the exact access token that will be used for the write:

- GitHub: numeric user `id` from the configured GitHub user-info endpoint.
- Gmail / Google Calendar: OpenID Connect `sub` from the configured Google user-info endpoint.

Emails and GitHub login names are deliberately not used as the continuity key because they can change independently of the underlying account.

Identity resolution is fail closed. Missing provider identity, non-2xx user-info responses, malformed JSON, interruption, or request failure produce a generic connector block without copying the access token into the error.

## Durable account comparison

`ProviderAccountContinuityService` loads the same durable `ConnectorConnectionEntity` referenced by the connector action and requires:

1. the connection exists;
2. the connection provider exactly equals the action provider;
3. `externalAccountRef` is nonblank and represents the stable provider account identifier;
4. the current stable provider identity resolved from the access token exactly equals that stored account reference.

There is no alias, prefix, email, display-name, or wildcard match. A mismatch fails closed with a generic continuity error and does not mutate the stored connector identity.

## Live-write ordering

`LiveConnectorWriteExecutor` keeps its existing credential/provider/status/expiry/scope validation. It then resolves the exact access token from the credential vault and calls:

`accountContinuity.assertCurrent(action, accessToken)`

before dispatching any Gmail send, Calendar event creation, or GitHub issue creation.

Therefore a credential rebound to another account is rejected before the provider write endpoint is selected or called. Owner approval does not override this account-identity boundary.

## Acceptance proof

`Phase12ProviderAccountContinuityTest` proves:

- access-token rotation remains valid when both tokens resolve to the same stable provider account;
- rebinding to a different stable provider account fails closed;
- the failure message does not reveal the access token or compared account identifiers;
- provider mismatch fails before provider identity lookup;
- a missing stored stable account reference fails before provider identity lookup;
- the exact vault-resolved token is checked by account continuity before provider endpoint access.

The dedicated `Phase 12 Connector Account Continuity Proof` workflow runs that test and statically verifies:

- GitHub uses numeric `id`;
- Google uses `sub`;
- the durable `externalAccountRef` is compared exactly with the resolved identity;
- continuity is invoked before live provider dispatch.

## Contract and safety impact

No new HTTP endpoint, JPA table, agent ID, credential type, provider mutation, shell capability, live-money path, or physical-hardware claim is added.

The live connector write surface remains limited to the already-governed Gmail, Calendar, and GitHub action kinds. The change adds a fail-closed identity prerequisite before those existing effects.

## Important scope boundary

This slice certifies **write-time account continuity**. It does not claim that OAuth completion, refresh-token exchange, or connector-health validation currently reject an account change at the moment the new token is stored. Those flows should be audited separately before Aetheris claims end-to-end account continuity across the complete OAuth credential lifecycle.

Until that later lifecycle hardening is certified, the invariant established here is narrower and explicit: **a live connector write cannot proceed unless the exact access token being used still resolves to the durable connector account**.

## Rollback

Revert `ProviderIdentityResolver`, `ProviderAccountContinuityService`, the `LiveConnectorWriteExecutor` continuity call, `Phase12ProviderAccountContinuityTest`, the dedicated continuity workflow, and this document as one unit.
