# B62-G9 — Real PostgreSQL authoritative-primary versus stale-snapshot counterexample

**27 October 2026 · REVIEW DRAFT · NOT INTEGRATED · NO_GO**

This evidence builds on B62-G8 draft PR #316, exact successful SHA `9b161452eaf73f530f8a3f63c3732a3806351dfc`. No production database, credential, JWT filter, customer resource, historical migration or protected main branch changes.

## Precise scope

A true production revocation decision must never authorize a revoked access JWT by using a stale cache or lagging replica after a committed emergency revocation. The previous B62-G8 test proved three independent password-authenticated readers see revocation on **one PostgreSQL primary**. It did not establish a stale-read failure counterexample. B62-G9 now creates two deliberately separate PostgreSQL 16 **test databases**, not a replica cluster:

- `postgres`: the **authoritative primary** synthetic role schema, with three reader logins and one limited emergency-writer login.
- `stale-snapshot`: a **deliberately stale independent** database initialized with identical test schema and credentials, then deliberately **not updated** after the primary changes.

**The stale-snapshot database is NOT a streaming replica, logical replica, genuine physical standby, failover service, or certified PostgreSQL replication topology.** Its only purpose is to show an observable stale-ALLOW decision that the test client must reject. Both containers are on an internal Docker network with **no published ports**, no live data, independent disposable volumes and unconditional cleanup.

## Real bounded executable proof

`b62g9-primary-consistency-client.py` performs actual TCP/password-authenticated `psql` lookups using each B62-G8 reader identity, and provides a **primary-only** `primary_authoritative` decision that converts all non-exact ALLOW results (including timeout, invalid result and absent primary) to DENY. The independent stale database is queried **only to prove the counterexample**, never used as the authorization source. Every read creates a separate PostgreSQL TCP session; test deadlines bound subprocess execution. A unit test patches a timeout to verify DENY, rejects malformed fixture roles and input types, and proves no fallback call occurs.

The isolated CI harness installs existing prototype schema and distinct password-authenticated roles on both copies, then:
1. Verifies same initial allow from three real readers and checks invalid password rejection.
2. Commits an emergency signed-`iat` cutoff to **primary only**. Stale copy still returns ALLOW for principal 42, while 12 independent concurrent primary read sessions across three identities deny the revoked token; principal 43 remains allowed and a new token for 42 is allowed.
3. Races eight separately authenticated emergency writer sessions, verifies final highest epoch, and commits a primary-only compromised signer block.
4. Verifies 12 primary reader sessions deny signer-blocked tokens while the independent stale database still ALLOWs the same signed claim.
5. Restarts primary with disposable persistence and repeats the negative result.
6. **Stops the primary** while leaving stale database online and explicitly ALLOWing; confirms all test readers still DENY, with no stale fallback, despite otherwise valid synthetic JWT claim tuples.

The full four-module Java regression is independent and remains mandatory. Earlier B62-G3–G7 independently tested genuine RSA-3072 JJWT signature checks and real Gateway, User and Audit filter behavior. This new script does **not** perform Java JWT signing, connect production filters, establish actual multi-host network consistency or prove general real-world performance.

## Certification gaps still NO_GO

**NOT INTEGRATED:** No real Gateway/User/Audit filter is wired to this PostgreSQL cluster. The PostgreSQL `stale-snapshot` side is **NOT a streaming replica**, so true replication lag behavior is still untested. The synthetic readers and writer retain static dummy passwords; production requires issued identities, secret rotation, TLS/authn, least-privilege audited writer approval, independent **owner approval**, and full historical migration / restore approval (#242). The test demonstrates primary **read-after-write** after a confirmed commit, **not** an operational cross-node guarantee; a real deployment would need explicit prevention of **stale replicas** in every read route, network partitions/latency/clock/replica promotion rehearsal, reviewed resource ownership (#251), audit trails and threat-model acceptance. Java **certificate** trust hardening PRs #309, #313 and #315 are separate release work.

Stage45 production **NO_GO**, all nine runtime release gates, and the broader Syntra E3.9/E4–E9 roadmap remain unresolved. No emergency key action, protected-main merge, or production deployment is authorized by passing this test.
