# B56 — re-prove genuine direct User/Audit authorization on patched Corretto runtime

**26 October 2026. Draft stacked on [B55 PR #289](https://github.com/teldigi5-wq/aetheris-platform/pull/289). No production go-live, no merge.**

## Existing source and earlier evidence

Issue [#251](https://github.com/teldigi5-wq/aetheris-platform/issues/251) still describes an older release snapshot where direct User/Audit controllers accepted unauthenticated access. **This is no longer an accurate statement of the latest unmerged development branch**: both services already contain their own `security/DirectApiJwtFilter.java`, introduced in earlier draft security work. They parse/validate the original signed Bearer JWT with independent HMAC verifier, require signed ADMIN and the proper read/write scope, and refuse unsigned identity headers. Gateway has parallel rules. [Previous CI evidence](signed-uid-not-resource-owner-20261025.md) covers 26 direct/Gateway JWT cases and 30 Identity-uid-vs-User-row-id negative cases using truly running disposable backends.

**But those earlier proofs predate the four-image B54 security changes**, which upgraded runtime Java to Corretto 21.0.13, updated AL2023 RPMs, embedded Tomcat to 11.0.27 and RabbitMQ Java client to 5.37.0. B55's hash/SBOM/Maven tests did not rerun the complete direct-Gateway HTTP authorization matrix after this vendor/OS/app dependency transition.

## B56 implemented regression

The dedicated `.github/workflows/b56-patched-direct-backend-auth.yml`:
1. Checks out the exact source PR head; runs ten Python CI/runtime/authorization contract regressions; verifies all test files compile.
2. Enforces **one internal Docker network, no host ports, no named persistent DB volumes**, no host PID/net/devices, and nonprivileged read-only client probes. PostgreSQL data is in tmpfs. No production accounts, domains or credentials.
3. Builds the actual **current** patched Gateway, User and Audit Corretto 21.0.13 images. Runs the genuine services as UID/GID `10001:10001`; verifies running JVM `21.0.13` and Docker host port mappings in actual Docker containers.
4. Mints five ephemeral synthetic JWT role/expiry cases from **production Identity JwtService** test machinery and two non-admin tokens whose signed Identity uid=1 deliberately equals a synthetic User.id=1.
5. Runs `tests/security/probe_backend_auth.py` unchanged from a separate unprivileged container for **26 actual HTTP checks** direct-to-User/Audit and through Gateway: unsigned/fake-header, expired, tampered, signed lower-role and scope-denied requests fail with 401/403; signed allowed admin GET works; read-only Audit admin POST fails.
6. Runs `tests/security/probe_user_row_id_collision.py` unchanged from a separate unprivileged container: only real signed ADMIN can create/read/delete exactly one invented User row; signed DEVELOPER and CONSUMER uid=1 **cannot** read, list, modify or delete User.id=1, even with forged Gateway headers. **30 HTTP/status/data-integrity assertions**. Cleanup deletes the synthetic row and removes the tmpfs database.
7. Tears down all ephemeral containers/networks even on failures. No production Compose, customer rows or real secrets touched.

## What passing B56 would NOT certify

The User entity and Identity Account entity remain separate with NO verified ownership mapping, so these endpoints are intentionally **single-domain ADMIN-only**. A role and scope check is not a new tenant/owner authorization design. The current Identity issuer still uses common HMAC without mandatory `iss`/`aud` claims; coordinate any shared issuer, service-specific audience and key-rotation change with the previous issuer/audience PR #229, not as an isolated patch. JWT revocation, machine workload identity, multi-tenant access, CNI/NetworkPolicy bypass in an actual cluster, browser/mobile end-to-end, token/key rotation, staging rollback and owner review all remain open.

This is scoped **real Docker/Gateway/backend authorization** regression for patched images, not issue #251 closure, Stage45 promotion, or the nine release gates. Evidence must be from successful exact-head hosted CI before claiming the test assertions passed. Keep draft/unmerged.
