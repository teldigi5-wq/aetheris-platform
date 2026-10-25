# Disposable real backend container authorization probe

**25 October 2026 — security research only; stacked after platform PR #272.**

## Purpose
Earlier draft PRs #267–#272 verified direct-service JWT filters, actual User/Audit HTTP tests, Identity-issued synthetic token compatibility, and real Gateway proxy routing to a **stub**. None demonstrated that a separate Docker container can call the *real running* User/Audit service instances directly and be independently refused, while the actual Gateway still routes valid requests.

This experiment uses the real application Dockerfiles for Gateway, User and Audit, in a dedicated Docker Compose project. A separate, unprivileged Python HTTP probe accesses both services directly and via actual Gateway route identifiers, using temporary Identity-issued JWT fixtures.

## Disposable topology and safeguards
- Dedicated internal-only Docker bridge; no published host ports, no privileged containers, no Docker socket mount and no host network. Preflight checks plus actual Docker port-binding inspection.
- New PostgreSQL storage uses tmpfs, Redis persistence is disabled, RabbitMQ is ephemeral, and no named/external volumes are declared.
- Hibernate create mode is enabled **only** in the fresh disposable PostgreSQL experiment. The production Compose file and its migration controls remain untouched.
- All credentials are obviously synthetic, and test JWTs are minted by the real Identity JwtService after Docker image builds (to avoid token expiration before probing). No production credentials, customers or traffic.
- The probe container is read-only, drops all Linux capabilities, cannot access external internet and mounts test fixture files read-only. It calls only the internal user-service, audit-service and Gateway URLs.
- On exit, CI destroys containers, networks and temporary data even if checks fail. Only Ubuntu 24.04 supports this Docker test; independent Windows/Ubuntu backend JVM tests already passed in earlier PRs.

## Exact security checks

The direct caller and Gateway must both accept a signed synthetic ADMIN GET with HTTP 200 and valid JSON from **both** real services. For each of the four route combinations (direct User, Gateway User, direct Audit, Gateway Audit), a request must return:

| Caller | Expected |
| --- | --- |
| Anonymous with forged Gateway ADMIN hints | 401 |
| Signed DEVELOPER with forged ADMIN hints | 403 |
| Signed API_CONSUMER with forged ADMIN hints | 403 |
| Expired ADMIN JWT | 401 |
| Signature-tampered ADMIN JWT | 401 |
| Signed valid ADMIN | 200 |

Additionally, a read-only ADMIN Audit POST must return 403 from both the Gateway and directly from the Audit container. These are **26 assertions** total, none performing an authorized mutation or deleting any row. Any mismatch fails the job.

## What a passing CI would and would not show

A successful run would prove the separately deployed real backend services enforce signed authorization even when another container on the shared network can reach them, and that the test topology's real Gateway forwards authorized requests. **It would not show Docker network isolation denies traffic:** this test intentionally permits connectivity so application authorization can be evaluated.

It cannot certify production routing, service identity, object ownership, tenants, real login/refresh/issuer/audience/revocation, TLS, Kubernetes CNI, rate-limiter resiliency, credential rotation or database restore/rollback. The service startup's disposable schema creation is **not** approval for automatic production migrations.

Issue #251 remains open, Stage45 is NO_GO, and the nine release gates and E3/E4–E9 remain separately pending. All changes are in a stacked draft PR, with no main merge and no production deployment.
