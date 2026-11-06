# Aetheris signed replay-admission boundary — review-only disposable integration

**Parent:** Verified draft [PR #396](https://github.com/teldigi5-wq/aetheris-platform/pull/396), exact SHA `ca40edbaee7a7b193d009f934c6cf1ab5717457d`. This child is a scoped **Java integration test, CI workflow, and explanation**, not the implementation or activation of a production recovery controller. The security/recovery branch remains stacked; protected `main` and the independent AI Runtime are unchanged.

## What this incremental proof covers

Previous stages established at-least-once RabbitMQ delivery, outbox idempotency, database snapshot mismatch rejection, unacknowledged-message recovery HOLD, and all-queue inventory inside a single disposable vhost. Those controls do **not** bind a replayed message to an **authenticated current recovery generation and still-authorized tenant principal**. The existing user outbox row field `recovery_generation` is a bounded **per-event parked-replay counter** (0–2) and must not be misrepresented as a global externally enforced monotonic recovery epoch.

The next test, `BrokerSignedReplayAdmissionIT`, uses the existing project's JJWT RS256 library with a fresh ephemeral RSA-3072 signing key and its public verifier; pins the algorithm, key ID, issuer, audience, subject, UUID tenant, unique event ID, exact `audit:replay` scope, generation, issuance, expiry and maximum token lifetime. Signing is test-only: this does not introduce another production issuer, a publicly trusted signing key, a new REST endpoint, or new credentials.

The test creates three uniquely named review-only tables **only** within `xerj_fence_audit`, a disposable PostgreSQL 16 logical database constructed by the preceding multi-database rehearsal:

- `xerj_review_replay_authority` — local synthetic tenant, generation and `ACTIVE/FROZEN` marker.
- `xerj_review_replay_principal` — test principal, tenant, scope and a dynamically checked revocation flag.
- `xerj_review_replay_effects` — synthetic effect ID primary key for idempotent replay.

For a signed event, a test-side authorization policy verifies the signature and claims, then **inside the same database transaction as the synthetic effect** locks the generation row (`FOR UPDATE`), checks phase/generation/tenant and re-reads the principal (`FOR SHARE`). It uses a single `INSERT ... ON CONFLICT DO NOTHING` for stable-event deduplication. The authority is intentionally stored in the same disposable database: it is **not** independent of privileged restore or administrator rollback.

## Required positive and negative cases

1. Missing/invalid bearer fails closed. A correctly signed, in-scope generation-1 event is inserted once; replaying the identical token is idempotent.
2. A local generation transition to 2 with `FROZEN` phase denies valid old generation-1 tokens and valid generation-2 tokens during the freeze. Only an explicit test-side `ACTIVE` change permits generation-2 work; generation-1 stays denied.
3. A valid generation-2 replayed event is inserted once; repeated delivery cannot create a second synthetic effect.
4. **Revocation after issuance** is effective at replay time, not only when the signed capability was created.
5. Cryptographically valid but wrong tenant, wrong scope, wrong issuer, wrong audience, wrong key ID, expired token, or a signature from a different RSA key must be denied without a synthetic database write.
6. A real persistent message on a *new disposable queue* carrying an old signed generation is consumed with manual acknowledgements, rejected `requeue=false` and observed in a distinct short-lived, operator-owned synthetic dead-letter queue. The exact message bytes are preserved in the test-only quarantine, and no effect is inserted.
7. A fresh authorized generation-2 message from the same disposable broker produces one synthetic effect and is ACKed; duplicate admission remains idempotent. The test deletes only its own random queues/exchange and review-only tables.
8. All earlier Identity/Gateway/User/Audit security regressions, RSA/mTLS, broker identity/DLQ, outbox, database restore, interrupted delivery, and vhost-custody CI checks must continue passing **at this exact child head**.

The workflow requires the unique hosted `xerj-mtls-ci-GITHUB_RUN_ID-GITHUB_RUN_ATTEMPT` project and the existing loopback-only ephemeral PostgreSQL/RabbitMQ Compose environment. No secret values, token bytes, synthetic payloads, database dump bytes or keys are intentionally uploaded in artifacts.

## Explicitly unresolved security boundaries

- **Not wired into the real producer, relay, or Audit listener.** It is a test-side reference enforcement path, not a fixed vulnerability or runtime-access control.
- Synthetic generation authority, revocation and membership rows are managed by the same disposable superuser-equivalent test account; a privileged rollback or cloned database can recreate older `ACTIVE` state. The earlier S-B62-A independent monotonic witness and anti-rollback requirement stays **BLOCKED**.
- The gate serializes the **synthetic Audit-side write with its local state change**, not RabbitMQ publication with independent Identity, User and Audit database commits. No physical producer/consumer pause, ACK/drain ordering, broker-wide multi-vhost freeze, atomic cross-resource cutover, or exactly-once guarantee.
- Replay-time tenant membership is synthetic, not production identity-provider authorization or revocation federation; a signed bearer is not automatically a certified mTLS workload. The ephemeral administrator operates the test queue topology; application broker ACLs are not changed.
- Production recovery still needs a reviewed authority contract with independent monotonic proof, authenticated workload identities, real membership/revocation, broker/DB cutover orchestration, replay quarantine and per-event disposition, and independently witnessed actual infrastructure exercises.
- Stage 45 production release remains **NO_GO**, with nine independent attestations previously outstanding. Platform authorization issue #251 and Runtime issue #23 remain OPEN. XERJ Corpus Hub issue #360 remains **HOLD**, with publication stages 3–6 incomplete and no approval to submit.

**This PR must stay draft/unmerged.** A successful CI run establishes only a reproducible disposable signed-replay denial experiment, not production safety or publication approval. Do not change XERJ's public source pin, any production secret, main branch, broker ACL, or Runtime release.
