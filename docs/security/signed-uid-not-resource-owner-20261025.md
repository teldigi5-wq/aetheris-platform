# Signed Identity user ID is not User-table record ownership

**25 October 2026 — B4Q disposable real-container negative authorization contract.**

## Verified design fact

IdentityAccount and User are separate JPA entities, each with independent database-generated integer primary keys. An Identity JWT contains a signed `uid` referencing **IdentityAccount.id**, while `/api/users/{id}` resolves **User.id**. There is currently **no** verified foreign key, tenant ID, validated ownership relationship or self-service policy joining these entities. The existing direct backend authorization policy correctly requires `ADMIN` and the appropriate signed scope. It must not be relaxed because Identity `uid` happens to equal the requested User table primary key.

## What this change implements

The **real Identity production `JwtService.issue` code** now has a separate test-only JUnit fixture `NonOwnerNumericCollisionJwtIT`. It creates two synthetic signed JWTs — `API_CONSUMER` and `DEVELOPER` — each with Identity `uid=1`, under the same CI-only key as prior #271. They remain in ephemeral `identity-service/target`, never committed or uploaded.

The new **real Docker HTTP probe** `tests/security/probe_user_row_id_collision.py` connects over the internal Docker network to the running User service and actual Gateway. It uses a signed synthetic ADMIN JWT to create exactly one fake User record, verifies its auto-generated User ID is **also 1**, and then attempts the following as nonadmins with perfectly matching signed Identity ID:

- Direct and Gateway `GET /api/users/1`: **403**, despite same numeric ID and `users:read` scope.
- Direct and Gateway `GET /api/users`: **403**, preventing broader data enumeration.
- Direct and Gateway `GET /api/users/1` with forged ADMIN identity headers: **403**.
- Direct and Gateway `DELETE /api/users/1` with forged ADMIN headers: **403**, including DEVELOPER, whose genuine issuer token contains `users:write`.
- Direct and Gateway `POST /api/users` with forged ADMIN headers: **403**, with no synthetic record created.
- Valid signed ADMIN can still read the fake row, prove it was not changed by the denied attempts, delete the fake row, and observe 404 afterward.

The probe asserts **30 real HTTP statuses**, checks the JSON contents are unchanged after denied calls, and stops if the disposable database was not empty at the start or the synthetic row doesn't receive ID=1. It never considers a matching signed Identity ID proof of resource ownership. The test deletes the fake record through authorized ADMIN access (unlike previous read-only synthetic probes), **only inside temporary Postgres tmpfs**, then CI destroys the entire Compose environment.

## Verification model and limitations

The experiment extends the previously verified real container network test from PR #273 by adding one more **unprivileged read-only probe container** on the same internal-only Docker network. It reruns #273's **26 direct/Gateway JWT checks** before the additional row-collision checks. The app images are the actual current Gateway, User and Audit Docker builds. There are no host-published ports, host networking, named database volumes, production secrets or Docker socket mounts. Original application authorization code and production Compose are unchanged.

The new GitHub Actions workflow runs on Ubuntu 24.04, builds images from the exact PR head, generates all synthetic JWT fixtures with Identity's own code, checks no host port bindings, validates both 26/26 parent and 30/30 new assertions, and tears down every service after completion. These counts are **acceptance criteria pending exact-head CI**.

**Not proven:** This is a negative safety contract for the current **ADMIN-only** API. It is **not** a completed tenant isolation or resource ownership feature. Creating genuine self-service requires a carefully designed Identity-to-User mapping and a tested signed tenant resource model with additional negative cross-tenant cases, approved before any permission expansion. Also outstanding: Kubernetes network-policy probing, issuer/audience, revocation, token rotation, real accounts, database migration and production restore. Issue #251, Stage45 NO_GO and the nine release gates remain open. No protected-main merge or production deployment is authorized.
