# Phase 12 Slice 9 — Provider Account Continuity

Phase 12 Slice 9 closes a delayed-write authority gap in LIVE connectors.

Before this slice, a connector action persisted the connection ID and provider, while `LiveConnectorWriteExecutor` resolved the credential currently attached to that connection at execution time. OAuth completion can rotate/replace the credential attached to an existing connection. An action approved while account A was connected could therefore remain approved after the same connection was rebound to account B.

## Security invariant

A LIVE connector action is bound to the provider account that was active when the action was created for owner approval.

The binding uses a non-secret, non-reversible 128-bit fingerprint (32 lowercase hex characters) computed as SHA-256 over:

`provider-name + newline + provider-issued stable account id`

The raw account ID is not persisted in the action. Access tokens, refresh tokens, email addresses and display names are not inputs to the fingerprint.

Provider-issued stable identity comes from the existing OAuth user-info surface:

- GitHub: numeric `id`;
- Google-backed Gmail/Calendar: OIDC `sub`.

## LIVE action creation

Before a LIVE action can create its durable task and owner approval, `ProviderAccountContinuityService.snapshot(...)` requires:

1. an existing credential for the requested connection;
2. provider equality between the connection/action and credential;
3. ACTIVE credential status;
4. a non-expired access token;
5. a successful read-only provider identity request;
6. a non-empty stable provider account ID.

The resulting fingerprint is stored on `connector_actions.account_fingerprint`.

Synthetic actions intentionally do not require provider identity and leave the nullable fingerprint empty, preserving hosted synthetic proof compatibility.

## Delayed execution

`LiveConnectorWriteExecutor.execute(...)` calls `accountContinuity.assertCurrent(action)` before it resolves the credential used by the mutation path.

Execution fails closed when:

- the persisted action has no valid account fingerprint;
- the current credential is missing, inactive or expired;
- provider identity cannot be read;
- the current stable account fingerprint differs from the fingerprint captured before approval.

A different-account reconnect therefore cannot inherit an older action's approval. The owner must recreate and re-approve the action for the current account.

A normal token refresh for the same provider account remains valid because continuity is derived from the stable provider principal, not rotating token material.

## Privacy and secret handling

The action stores only the truncated SHA-256 fingerprint. It does not persist or expose provider account IDs, email addresses, access tokens or refresh tokens.

The identity request uses the existing in-memory credential vault and does not copy token material into action metadata, logs or approval evidence. Provider identity response bodies are not surfaced in continuity errors.

## Existing boundaries preserved

Slice 9 does not add a new endpoint, agent, credential backend, write adapter, live-money capability, remote-control path or physical-hardware claim.

All prior gates remain required:

- connector must be ENABLED;
- owner approval is still mandatory;
- task must be RUNNING;
- `automation-engineer` must remain the active specialist with the required direct-execution tool family;
- LIVE writes remain disabled by default;
- provider write scopes are still checked;
- independent QA/verifier completion remains required;
- synthetic action behavior remains unchanged.

## Acceptance proof

`Phase12ProviderAccountContinuityTest` proves:

- LIVE action creation snapshots the current provider-account fingerprint before approval;
- synthetic action creation does not require LIVE provider identity;
- an account mismatch stops execution before the live writer resolves its mutation credential or endpoint.

The cumulative Phase 12 workflow also verifies:

- `account_fingerprint` is persisted on connector actions;
- LIVE action creation calls the continuity snapshot;
- live execution calls the continuity assertion;
- GitHub `id` and Google `sub` are the stable identity inputs;
- the fingerprint is not derived from access/refresh token material;
- continuity enforcement precedes mutation-credential resolution.

## Contract and migration behavior

The persistence change is additive: `connector_actions.account_fingerprint` is nullable so existing synthetic history remains readable.

Legacy LIVE actions without a continuity fingerprint fail closed at execution and must be recreated after provider identity is established. This is intentional; owner approval for an account-ambiguous action is not migrated into authority over a current account.

## Rollback

Revert `ProviderAccountContinuityService`, the additive action fingerprint field, LIVE action snapshot wiring, live-executor continuity assertion, Slice 9 tests/workflow guards and this document as one unit.

Rollback restores the previous connection-level behavior and therefore reopens the delayed account-rebind risk; it should not be used as a compatibility bypass for LIVE writes.
