# B4U — Audit shipped Flyway automatic baseline default denial

**25 October 2026. Draft security remediation, NOT deployment authorization.**

## Previous evidence and the actual gap

[PR #277](https://github.com/teldigi5-wq/aetheris-platform/pull/277) demonstrated with the **real current Audit Docker image** that disabling `baseline-on-migrate` is compatible with its bundled V1 migration in an entirely fresh Audit-owned database, works across an independent second startup with one Flyway V1 history row, preserves 12/12 HTTP authentication cases, and **refuses to auto-baseline** an unrelated nonempty Identity/User synthetic database with no audit history.

However, #277 reached that result only by injecting `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false` into disposable CI service environments. The **shipped `audit-service/src/main/resources/application.yml` still contained `baseline-on-migrate: true`**, which would quietly baseline certain existing nonempty databases and can conceal absence of reviewed schema ownership. That was a confirmed unsafe default, not evidence of an actual production incident.

## Review candidate

This **draft, unmerged branch** makes the minimum default-deny edit in the actual Audit configuration:

```yaml
spring:
  flyway:
    enabled: true
    table: flyway_audit_schema_history
    baseline-on-migrate: false
    baseline-version: "0"
```

It does **not** change Audit V1 SQL, its history table, Java source, authorization, credentials, Identity/User settings, any operator database or any of the three operational Compose files. Explicit legitimate baseline work remains possible only through a separately reviewed, approved procedure; no background automatic baseline is allowed merely because a database is nonempty.

## Crucial deployment risk and non-go decision

Switching this default can make an **existing deployment using an unreviewed populated shared database without `flyway_audit_schema_history` refuse to start**. That is the intended fail-closed safety behavior, but it can cause an availability interruption if deployed before assessing existing Flyway history. **Do not merge or deploy this candidate until operators provide an approved historical schema inventory, existing Audit history state, database backup/restore evidence and a rollback window.** Do not force baseline or disable Flyway to make an unknown schema boot.

User/Identity `SPRING_JPA_HIBERNATE_DDL_AUTO: update` in the three operational Compose variants remains a separate unresolved risk. Issue #242 remains open, release Stage45 stays NO_GO and nine certification gates remain unapproved.

## First measured implementation results

The complete [Ubuntu Docker job #40189654039](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40189654039) passed against the **actual changed Audit `application.yml`**, with no Flyway environment overrides in its three synthetic Audit containers. Five static regression tests passed, and the real application created exactly one successful V1 history row with no baseline, reused it through an independent second service, passed **12/12 actual signed-JWT HTTP tests**, and rejected startup on a deliberately nonempty unrelated Identity/User database without Audit Flyway history, leaving no Audit tables/history. The first experimental workflow revision contained an invalid CI preflight and GitHub did not run test jobs; this was corrected by preserving parent PR #277's known-good workflow unchanged and adding a separate validated PR-specific workflow plus a five-test source guard. CI passes on that proper PR-triggered workflow. **The source change is a draft remediation, not a real operator-data compatibility verification or production deployment approval.**

## Exact tests

The B4U workflow branches from #277 and reruns its **actual real Docker Flyway test** against a fresh disposable PostgreSQL with no host ports or persistent volume. It reuses the known synthetic V1/V2 candidate in a separate nonempty database and the real current Audit images.

- All three Audit test containers deliberately have **NO** `SPRING_FLYWAY_BASELINE_ON_MIGRATE` and **NO** `SPRING_FLYWAY_TABLE` environment overrides; Docker Compose preflight rejects either. The tested behavior is therefore the exact production-image application default.
- CI asserts the shipped property appears exactly once as `baseline-on-migrate: false`, its history table remains `flyway_audit_schema_history`, and no operational Compose entrypoint injects a Flyway baseline environment override.
- Fresh Audit database: exactly one successful V1 SQL history row, no BASELINE row, no unintended User/Identity schema.
- Second independently started current Audit instance: same single V1 history; **12/12** actual HTTP signed-token cases.
- Separate nonempty synthetic Identity/User database lacking Audit history: the Audit image must refuse startup for an explicit Flyway nonempty-schema/no-history reason, with **no** Audit table/history created and existing synthetic Identity table intact.
- Unconditional `docker compose down --volumes` is allowed **only inside this dedicated disposable CI project**, never against a retained production/dev database. Synthetic infrastructure is deleted after success or failure.

**Accept only successful exact-head CI for this draft; success proves a fail-closed modern application default in synthetic environments, NOT that a particular historical operator database has an approved migration baseline or that it is safe to promote.**

Additional related evidence: [#247](https://github.com/teldigi5-wq/aetheris-platform/pull/247) actual isolated Flyway engine lab, [#275](https://github.com/teldigi5-wq/aetheris-platform/pull/275) disposable Postgres restore/validate, [#276](https://github.com/teldigi5-wq/aetheris-platform/pull/276) modern Identity/User candidate schema compatibility, and [#277](https://github.com/teldigi5-wq/aetheris-platform/pull/277) Audit CI-only default-denial experiment.
