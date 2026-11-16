# B62-G11 — Real Docker network partitions, bounded revocation lookup and recovery

**27 October 2026 · TEST ONLY · REVIEW DRAFT · NOT INTEGRATED · NO_GO**

This stage stacks on B62-G10 PR #318 at `0527b8adb6787ed7423d50c9cb16f6ac4f6f0939`, a verified disposable physical PostgreSQL 16 streaming standby. The test remains isolated and changes neither deployed Gateway, User, Audit, Identity, credentials, production schemas nor protected main.

## What is new

B62-G10 proved a real streaming standby could have a stale ALLOW when WAL replay was paused. B62-G11 adds actual **Docker network partition** / rejoin events to that *same physical streaming topology*, without corrupting production traffic or configuring a new mock primary. Docker host orchestrates a disconnect of the physical replica from its ONLY private network, then reconnects it with its DNS alias; it separately disconnects the password-authenticated client container and reconnects it. Both PostgreSQL servers remain unmodified, and the replica is never promoted.

The G10 script sources `b62g11-partition-proof.sh` only when `B62G11_TEST_ONLY_FAULTS=1`. Normal B62-G10 CI remains unchanged. A strict preflight checks the expected private Docker network name and that connections are truly severed/rejoined. No containers publish host ports and no process mounts Docker socket. The same two short-lived named volumes are destroyed on every run.

## Executable security decisions

After B62-G10 has proven physical standby recovery from principal cutoff 12000, B62-G11:
1. Pauses real standby WAL replay, disconnects its Docker network attachment, then commits a newer **principal 42 cutoff 13000** on the authoritative primary. All three authenticated readers must reject signed `iat=12001` from primary, while unrelated principal 43 remains allowed. Standby cannot be contacted during partition.
2. Reconnects the physical standby but **does not resume WAL replay**. The standby must still genuinely ALLOW revoked principal 42 due to lag, while the primary-authoritative reader refuses authorization.
3. Resumes real WAL replay and waits for the physical standby replay LSN to catch up to the primary's committed WAL marker; standby and primary must both reject the revoked signed tuple.
4. Disconnects the independent authenticated client container from the network while primary remains healthy. Every reader must reject an otherwise valid principal because SQL is unreachable; missing responses, timeouts and exceptions are DENY.
5. Reconnects the client and verifies that the previously revoked principal remains rejected while unaffected principal 43 resumes ALLOW, without replaying or caching the older permit decision.
6. Continues original B62-G10 signer-block WAL replay and real primary shutdown tests, without standby promotion.

The test adapter wraps each PostgreSQL process lookup in a **bounded** per-call wall-clock guard (9 seconds including CI process overhead); the underlying G10 reader has 5-second subprocess timeout and 1-second libpq connect timeout. This is not a production p99/SLO or a service-side deadline. It does not modify real JWT verifier classes, and no real signed token is checked in this stage: B62-G3–G7 established separate signed-token verification and Java filter seams.

## Critical boundaries

**NOT INTEGRATED:** No live service reads revocation from PostgreSQL; no real deployed network partition, TLS termination, secret provisioning, end-to-end HTTP revocation, cross-host HA, real DNS outage, multi-AZ topology, or reliable automatic failover is certified. There is **no automatic failover**, leadership consensus, split-brain prevention, promotion, fencing, synchronous commit guarantee or rollback approval. Docker network cuts are single-runner fault injections, not production disaster-recovery tests.

Before merging any production capability: written **owner approval**, audited incident controls, historical schema **migration** and restore (#242), true owner/tenant object authorization (#251), provisioned least-privilege workload identities, primary-only read-after-write enforcement (never fallback to stale reader or cache), bounded nonblocking Gateway network client, verified service API/DB privilege boundaries, certificate trust and hardening candidates #309/#313/#315, and independent rollout/rollback/clock/load tests. Stage45 remains **NO_GO**, all nine runtime release gates uncertified, and Syntra E3.9 / E4–E9 remain unfinished.
