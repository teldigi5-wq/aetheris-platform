# Aetheris / XERJ — existing Compose security hardening proposal

**Child draft of review-only #380.** No production systems, existing volumes, credential vaults or servers were changed. The current old XERJ source pin is still on HOLD.

The three main Compose variants and the default Compose override previously used hardcoded public development PostgreSQL and RabbitMQ passwords, broadly published host ports, JPA `ddl-auto=update` for User/Identity, and disabled secure refresh cookies in development. This candidate:

- Requires operator-supplied `AETHERIS_POSTGRES_PASSWORD`, `AETHERIS_RABBIT_PASSWORD`, `AETHERIS_JWT_SECRET`, and (when applicable) `AETHERIS_GRAFANA_PASSWORD` rather than the old hardcoded passwords.
- Binds existing published ports to **127.0.0.1** by default (real ingress/network policy must be configured separately). Ports are not opened externally by this patch.
- Makes User and Identity Compose use `ddl-auto=validate` and retains audit Flyway as it was; legacy data/schema preparation now **must be performed separately** before standard Compose startup. No automatic production migrations.
- Restores secure browser-refresh cookies in Compose. Plain-HTTP local browser auth may require a separate explicit disposable development overlay.
- Includes a real Docker Compose rendered-JSON CI contract that checks all variants, default override merge, strict missing-secret failure, and local-only port bindings without printing secret values.

**Important unresolved security limitations:** Redis remains unauthenticated inside the Docker network; production-ready Redis AUTH/mTLS, real network-policy segmentation, immutable image digest pinning/image SCA, Flyway-safe schema migration/backups/rollback, full Docker service startup acceptance, tenant mapping and RS256/mTLS integration are **not yet established**. These changes intentionally fail closed and may disrupt unconfigured old local demos; do not merge without owner operational approval and compatibility tests.

Stage45 = NO_GO. XERJ PR #376 = HOLD. Source is NOT production-certified or vulnerability-free.
