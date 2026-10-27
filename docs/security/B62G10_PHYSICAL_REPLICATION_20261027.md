# B62-G10 — Real PostgreSQL physical standby replication and fail-closed revocation

**27 October 2026 · REVIEW DRAFT · NOT INTEGRATED · NO_GO**

Base: B62-G9 PR #317, exact successful commit `35c3a68fd1be5e0689018d92ebcfa1a6719e5149`. No production database, real keys, JWT filter, history, secrets, migrations or protected main change.

## What is materially new

B62-G9 used a second **independent stale snapshot**, NOT a streaming replica. B62-G10 uses **real PostgreSQL 16 physical streaming replication** in disposable Docker infrastructure. A separate standby uses `pg_basebackup -X stream -R` from primary with a password-authenticated replication-only role. CI requires `standby.signal`, an active primary `pg_stat_replication` streaming connection, `pg_is_in_recovery()` true on standby/false on primary, and actual `pg_last_wal_replay_lsn()` catch-up against primary WAL LSN after resuming.

There are two independent, disposable PostgreSQL volumes and one private Docker network, with zero published ports or Docker socket. A third `auth-probe` container does only genuine network-password-authenticated `psql` reads and emergency writes; no local-loopback trust claim is accepted. Incorrect-password cases must fail on **both primary and standby**. The replication role has fixed **synthetic CI-only credentials** and obtains no broad application SQL permission; its HBA entry exists only inside disposable primary.

## Deterministic physical lag and required denial

This is not artificial stale state: test harness calls `pg_wal_replay_pause()` on the real physical standby, then commits a principal 42 cutoff on primary. The primary rejects the old JWT's **prevalidated synthetic claim tuple** while the standby still incorrectly ALLOWs it. Authorization must read the **primary only**, never fallback to standby or cache, so all authenticated readers deny. After `pg_wal_replay_resume()`, the test waits for the standby's WAL replay LSN to reach an authoritative primary WAL marker; all standby readers then reject. The same pause/commit/replay is repeated for a blocked signer kid. Concurrency is tested with nine independent authenticated primary lookups per scenario.

Finally, primary is stopped **without** standby promotion. The real standby stays readable and still ALLOWs an unrelated/unblocked signed-claim tuple, but the authoritative reader fails closed because the primary is unreachable. This proves **no unauthorized failover**, not automated failover or HA recovery. All lookups are bounded and errors deny. Both volumes/network are deleted unconditionally.

The `b62g10-physical-replication-client.py` script deliberately implements **no JWT cryptographic verification** and is *only* for validated synthetic tuples. Earlier B62-G3–G7 prove JJWT signature checks in separate test classes; it is not a single end-to-end deployed authenticated service chain.

## Certification blockers and governance

**NOT INTEGRATED:** active Gateway, User and Audit JWT filters do not query physical primary, standby, or any external revocation store. The test physical replica is **not a real multi-zone production deployment** and has no TLS/rotation/KMS/Vault, independent network partitions, automatic failover, consensus/leader fencing, standby promotion and anti-split-brain checks, backup restore or production incident runbook. While this test uses **physical streaming replication**, it does not prove production replication delay bounds or synchronous commit semantics. The temporary replay pause deliberately **demonstrates replication lag**, not a safe fallback read.

Before production: secure owner approval, historical migration/restore (#242), tenant isolation and direct object authorization (#251), provisioned distinct limited workload identities, audited emergency writer dual control, guaranteed fail-closed read routing, bounded async Gateway lookup, real multi-node HTTP/clock/partition/load verification, reconciliation of certificate trust and Compose hardening release PRs #309/#313/#315, and all nine runtime certification gates. Stage45 remains **NO_GO**; Syntra E3.9 and E4–E9 remain unfinished. Do not merge or deploy based on this isolated proof.
