# Phase 11 — Security Production Hardening

Phase 11 begins the post-roadmap security-hardening pass from the fully certified Phase 10 canonical development SHA. This slice closes the credential-value access gap without changing secret storage backends or weakening any existing execution boundary.

## Security gap closed

Before Phase 11, `CredentialVault.resolve(alias)` was a storage abstraction but carried no caller or purpose context. The two production components that actually retrieved secret values — GitHub execution and the cloud-model provider — could ask the vault for their configured alias directly.

Phase 11 introduces `ScopedCredentialAccessService` as the only production credential-value boundary for those consumers.

## Guarantees

- **Finite caller identities:** secret-value access uses a fixed enum, not an arbitrary caller string.
- **Finite purposes:** GitHub read, GitHub publish, and model inference are explicit purposes.
- **Alias binding:** GitHub purposes may resolve only the configured GitHub alias; model inference may resolve only the configured cloud-model alias.
- **Cross-provider denial:** a model provider cannot resolve the GitHub credential and the GitHub adapter cannot resolve the model credential.
- **Denial before resolution:** unauthorized requests never call `CredentialVault.resolve`.
- **Audit before release:** an invocation-audit entry must be created before vault resolution.
- **Audit completion before release:** a successfully resolved secret is returned only after the success audit finishes.
- **Fail closed on audit failure:** if audit completion fails after resolution, the char array is zeroed and access fails.
- **No secret values in audit:** audit metadata contains caller, purpose, result, a short SHA-256 alias fingerprint, and `secretValueRedacted=true` only.
- **Bounded telemetry:** `aetheris_secret_access_total` labels use finite caller, purpose, and result values.
- **Secret lifetime discipline:** existing adapters continue zeroing resolved `char[]` values after use.
- **Readiness remains non-secret:** cloud-model availability uses `CredentialVault.describe` through the scoped service and never resolves the credential value.

## Policy matrix

| Caller | Purpose | Allowed credential |
| --- | --- | --- |
| `GITHUB_ADAPTER` | `GITHUB_READ` | configured GitHub credential alias |
| `GITHUB_ADAPTER` | `GITHUB_PUBLISH` | configured GitHub credential alias |
| `CLOUD_MODEL_PROVIDER` | `MODEL_INFERENCE` | configured cloud-model credential alias |

Every other caller/purpose/alias combination is denied.

## Audit visibility

Secret-value access reuses the existing `aetheris_invocation_audit` pipeline with target id `credential-vault`. No new audit table or privileged logging path is introduced.

The audit record deliberately does **not** persist:

- credential values;
- authorization headers;
- plaintext credential aliases.

Alias correlation uses only the first 64 bits of the SHA-256 digest of the normalized alias. This fingerprint is operational metadata, not authentication material.

## Observability

Metric:

```text
aetheris_secret_access_total{caller,purpose,result}
```

Expected result labels are bounded to:

- `resolved`
- `unavailable`
- `denied`
- `error`
- `audit_failure`

No credential aliases or secret-derived values are metric labels.

## Storage backends unchanged

Phase 11 does not migrate or copy credentials. Existing vault implementations remain responsible for storage:

- environment-backed CI/development credentials;
- OS-backed credential-vault contracts where enabled and physically validated.

`CredentialVault.describe(alias)` remains non-secret metadata. Direct secret retrieval is now routed through the scoped service for the two production consumers that resolve values.

## Security review notes

1. Authorization is evaluated before `CredentialVault.resolve`.
2. Audit-start failure prevents vault access.
3. Audit-success failure after a resolved credential zeroes the credential array before throwing.
4. Denial messages do not include credential aliases.
5. Missing-credential messages returned by migrated consumers do not include credential aliases.
6. Metric labels are enum/static values to avoid unbounded cardinality and accidental secret labels.
7. No new HTTP endpoint, JPA table, shell capability, remote-access path, production activation path, or live-money path is introduced.
8. Existing Stage 26, contract-freeze, CodeQL, dependency-lockdown, runtime, and connector proofs remain merge gates.

## Rollback

This slice is additive around the existing vault abstraction. Rollback is:

1. stop new deployments if scoped secret access shows a regression;
2. revert the Phase 11 PR;
3. restore the prior adapter injection of `CredentialVault`;
4. keep the underlying secret stores unchanged;
5. retain invocation-audit records as historical security evidence.

No secret migration or data-destructive rollback step is required.

## Acceptance proof

`ScopedCredentialAccessServiceTest` proves:

1. authorized scoped resolution;
2. redacted audit metadata;
3. cross-provider alias denial before vault access;
4. purpose mismatch denial before vault access;
5. missing credential audit without alias leakage;
6. audit-start failure prevents vault resolution;
7. audit-completion failure zeroes the resolved secret and fails closed;
8. non-secret availability checks obey the same policy;
9. invalid context never touches the vault.

The dedicated GitHub Actions gate is **Phase 11 Security Production Proof**.
