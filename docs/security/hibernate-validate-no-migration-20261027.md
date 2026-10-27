# Disable Hibernate automatic schema mutation: draft migration safety candidate

**27 October 2026 · review only · not approved for production · NO_GO.**

This branch sits on the merged-in-review (but still **unmerged**) chain PR #315 → #322 → #323. All three Compose variants previously set `SPRING_JPA_HIBERNATE_DDL_AUTO: update` for the Identity and User services. An unreviewed application startup could silently mutate an existing database. This candidate **changes all six occurrences to `validate`**, which does not create or alter database tables. The independent Python test checks the rendered Compose JSON (with synthetic ephemeral CI environment values) and static source policy; full four-module Java unit tests must remain green.

### Important breakage and deployment boundary

**This is a deliberate fail-closed configuration, not a completed migration.** No fresh PostgreSQL database can be assumed to have the necessary Identity/User tables. Startup with `validate` can fail when schema history is missing or inconsistent; that is safer than silently recreating or changing real tables. DO NOT merge this branch as a turnkey new-install fix. Do not set `ddl-auto` back to `update`, delete persistent volumes, run an unreviewed `baseline-on-migrate`, or apply schema migration to user databases.

Before promotion, the DBA/owner must inventory actual historical Identity/User schemas and Flyway history; create a checked, versioned baseline and forward-only migrations; run **both** clean and existing-data integration; back up and restore actual representative data and rehearse rollback; verify concurrent startups and idempotency; independently sign off the production change. This remains issue #242.

PR #323 improves direct backend JWT authentication, but still uses the legacy shared HMAC key; separate service principal keys and tenant/object authorization remain unresolved (#251). Trust-store runtime, dependency SBOM #255/#259, emergency writer approvals and nine release gates require independent certification.

All CI success here confirms only **config/source test behavior**, never a real historical schema migration or production runtime availability. `main` unchanged, no data, credentials, deployment or migration touched. **NO_GO.**
