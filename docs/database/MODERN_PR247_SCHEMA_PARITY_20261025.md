# B4S — Modern Identity/User vs prior PR #247 synthetic Flyway candidate

**25 October 2026 — migration-readiness cross-branch integration TEST ONLY.**

## Why this is different from completed work

[Older Flyway study #247](https://github.com/teldigi5-wq/aetheris-platform/pull/247) ran the actual Flyway Java engine with explicit synthetic baseline/history on its older stack and passed 5/5 workflows. [Current runtime stack #275](https://github.com/teldigi5-wq/aetheris-platform/pull/275) proved the modern User Docker image can recover an entirely synthetic PostgreSQL database, boot in `validate` mode, preserve schema and data and refuse drift. **Neither proved the V1/V2 SQL in #247 can bootstrap the current Identity AND User images.** This new test connects that evidence without rebasing older security PRs, weakening startup validation or touching any operational installation.

## Exact provenance and execution

Test-only snapshots under `tests/security/flyway-candidate-247` match **the exact Git blob hashes** from #247 HEAD `61d635fbbac0dc627d4165494367c7c6a75c8bd0`:
- `V1__ci_only_identity_user_tables.sql`: blob `8150f49ad8a3bb66fd3b1f96803119e435260c85`.
- `V2__ci_only_token_index.sql`: blob `79b19609516da1ce94d85e12e13d31ccb28f1757`.

CI verifies these hashes, and the files stay **outside** both applications' auto-discovered `src/main/resources/db/migration` directories. These are **not production migrations**. This B4S job executes the reviewed SQL through `psql` on one brand-new marked CI-only PostgreSQL database; it does NOT claim to invoke the Flyway history engine again (that separate capability was validated in #247).

The overlay `tests/security/docker-compose.flyway-parity-overlay.yml` adds two independent **actual current Java Docker images**, Identity and User, both configured `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`. They connect to the shared fresh candidate database. A separate drift clone intentionally deletes `identity_accounts.email` and must fail Identity startup for a specific Hibernate missing-column reason.

The original User image independently creates its schema in a **different** fresh temporary PostgreSQL database; CI reads only normalized `users` catalog metadata (column types, lengths, nullability, identity and name-independent PK/unique/FK semantics) from both databases and demands equality. This compares contemporary Hibernate-generated User shape to the historic candidate. A success does **not** mean all indexes, data migrations, service ownership or historical schemas have been reconciled; it is an explicit narrow comparison.

After the modern Identity and User apps start using the candidate SQL, a separate read-only probe checks Identity health, synthetic ADMIN access to User 200, signed DEVELOPER and API_CONSUMER 403, expired/tampered JWT 401 and anonymous 401. Identity test JWTs are minted by current production `JwtService.issue` on **synthetic** accounts. PostgreSQL full schema and data dumps are compared before vs after `validate` startups with only randomized pg_dump client `\\restrict`/`\\unrestrict` session markers removed.

## Isolation and release limits

- Docker Compose bridge has `internal: true`, zero published host ports, no named persistent volumes, and all credentials are literal CI-only placeholders. Tests use entirely new database names and temp container storage; teardown always destroys them.
- Nothing edits original production-style Compose files, startup Java classes, role/scope logic, runtime JWT code or deployed Flyway migrations. The current User Hibernate `create` is used *only in a brand-new disposable reference database*, never against retained data.
- Even if the CI succeeds, **the candidate still has no approved real historical schema baseline, concurrent migration owner, Flyway runtime wiring, real operator backup/restore or release authorization**. Audit Flyway ownership is not reconciled. Issue #242 stays open. Stage45 stays NO_GO; nine enterprise release gates and E3–E9 remain pending.
- If catalog parity or actual application validation fails, record the specific incompatibility and adjust a separate **reviewed test-only candidate** only after evidence. Do not relax Hibernate `validate` or delete existing production data to make the job green.

**CI acceptance:** exact-head Ubuntu Docker workflow green only when both current applications boot on immutable prior candidate SQL, all 7 HTTP assertions pass, catalog constraint semantics match, restored schema/data remain unchanged, and the intentionally drifted Identity app fails for the right cause. Do not equate this with deployable Flyway migration history.
