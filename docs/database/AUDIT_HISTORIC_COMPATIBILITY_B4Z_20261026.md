# B4Z — Audit restored-schema compatibility and archive-integrity denial

**26 October 2026. Draft security research stacked on [PR #282](https://github.com/teldigi5-wq/aetheris-platform/pull/282). NO production approval.**

## Problem

Previous tests establish genuine Audit Flyway V1 migration, a valid disposable `pg_dump` restore, and actual Flyway rejection of a wrong checksum. They do **not** show that a *valid-looking* Flyway history guarantees the physical PostgreSQL table still matches the application's V1 schema. An operator could have a copied history row alongside independently drifted database columns, defaults or indexes.

This stage protects the release workflow from confusing *one passed history check* with a full migration/ownership decision. It intentionally simulates historical-looking drift using invented CI-owned data, **not any real historical database**.

## Bounded implementation

- `scripts/audit_historic_compatibility_preflight.py` combines the existing private **B4W schema contract v2** and **B4X history contract v1**. Strictly rejects malformed schema/history reports, unsupported versions, nonboolean flags, unexpected keys, duplicate JSON keys and oversized inputs.
- It reads only two **local files**; there is no database connection, Internet access, secret input, migration repair or command execution.
- The best possible status is `AUDIT_COMPAT_REVIEW_PRIVATE_HISTORIC_RESTORE_AND_REAL_FLYWAY_VALIDATION` (exit **1**). Wrong schema/history produces `AUDIT_COMPAT_NO_GO_...` (exit **1**); invalid inputs produce a fixed token (exit **2**). There is no exit **0** or release GO. The checker does not echo untrusted input.
- The B4Y **network-internal, tmpfs PostgreSQL** workflow now verifies that a clean restored archive still needs review and a separately corrupted archive **fails the pinned SHA-256 gate** before promotion. It deliberately tests incorrect `recorded_at` defaults, ASC rather than V1 DESC index ordering, and failed history metadata on the **separate disposable clone**. All must be denied even though other Flyway metadata looks normal. The clone is restored to its original state before the inherited real Flyway checksum-tamper/startup-refusal test.
- The good restored database is never modified during negative controls; inherited B4Y assertions must again verify its real Audit startup, preserved schema/data/archive, anonymous 401 protection and unconditionally cleaned-up Docker resources.

## Private operator workflow (not currently executed)

A responsible DBA must obtain permission, determine exact database/schema ownership and acquire a **representative privately restored historical backup**. On this approved offline restore, run the read-only catalog and history queries from B4W and B4X using least-privileged credentials and save both JSON outputs outside source control. Run from the repository root:

```sh
python3 -m scripts.audit_historic_compatibility_preflight \
  PRIVATE_SCHEMA_FLAGS_JSON PRIVATE_HISTORY_FLAGS_JSON
```

No status authorizes production. An operator must separately validate the actual Flyway checksum through the **real exact-version Flyway application**, inspect historic migration lineage and owners, verify backup integrity and recovery, document change windows, and explicitly approve migration and rollback. Never upload private inventory files, credentials, customer rows, backup archives or raw logs to GitHub or ChatGPT.

## Initial measured implementation CI

The first B4Z [hosted Actions run #40277664769](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40277664769) **succeeded on its implementation head**. GitHub logs show **7/7 inherited B4Y tests plus 10/10 B4Z Python unit tests passed**. The real Audit Docker build, disposable PostgreSQL V1 seed, custom-format archive and two restores all succeeded. A copied and modified archive failed the trusted SHA-256 digest check without altering the clean archive. An invented restored clone with the V1 timestamp default removed, the index changed to ASC, or a failed history record was denied by the composite preflight, while the untouched restored DB remained REVIEW-only. Real Audit startup against the good restore preserved canonical schema/data and denied anonymous HTTP access; the other clone's deliberate later checksum alteration caused real Flyway startup refusal. CI cleanup succeeded.

**This is synthetic test evidence only; no historic customer/operator data was inspected.** The resulting documentation commit requires another **exact-final-head CI** before recording a verified final SHA.

## Safety and scope limits

The catalog checks cover only **known V1 columns, index properties and defaults**, not every possible PostgreSQL semantic difference (triggers, grants, RLS, ownership, search paths, incompatible extensions, unexpected neighboring tables, data content, or other migrations). The Flyway-history boolean summary does not compare actual checksum values against V1 SQL. Even successful composite checks therefore **cannot independently approve a schema**.

No operational Compose, real data, Flyway V1 SQL, Java services, secrets, persistent volumes, protected main or production release settings are modified. Synthetic CI cleanup is safe ONLY for the CI-owned tmpfs project.

**Remaining:** authorized historical restore; genuine operator Flyway validation and schema ownership; six Hibernate `ddl-auto:update` Compose overrides; issue #242 and independent issues #251/#255/#259; Stage45 NO_GO; all nine certification gates. Do not merge/deploy on a synthetic test pass.
