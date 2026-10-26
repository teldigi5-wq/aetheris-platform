# B57 — Coordinated signed JWT issuer and audience contract

**26 October 2026 · DRAFT stacked on [B56 PR #290](https://github.com/teldigi5-wq/aetheris-platform/pull/290) · NOT merged, deployed, or enabled in production.**

## Prior work and design decision

Existing older [PR #229](https://github.com/teldigi5-wq/aetheris-platform/pull/229) proposes issuer/audience signing in Identity and checking in Gateway. Its old version of Gateway **predates** multiple stronger B4X–B56 ADMIN/scope/UID and service-level authorization guards. Copying the PR #229 Gateway implementation would overwrite newer controls. B57 **selectively carries forward only its issuer/audience concept** onto B56 while independently updating BOTH direct User/Audit JWT parsers as well. No changes are merged from #229; this candidate needs coordinated review against it.

## What B57 changes

All four Java components accept the same configured issuer/audience (via `AETHERIS_JWT_ISSUER`, default `aetheris-identity`, and `AETHERIS_JWT_AUDIENCE`, default `aetheris-api`):

- **Identity** signs JJWT access tokens with `iss` and `aud`, in addition to the existing `sub`, integral `uid`, `role`, `scopes`, `iat`, and `exp`. It preserves the actual prior signing-algorithm selection and test fixture creation; no new crypto material is introduced.
- **Gateway** requires verified signature AND matching issuer/audience for all protected User/Audit/Orchestrator requests before its existing role, scope, UID, time and header-sanitization rules. It still forwards the original Authorization bearer to each service; unsigned `X-Aetheris-*` hints never grant access.
- **User and Audit** require those exact signed claims via independent JJWT parsing before the existing ADMIN/scope and UID checks. Direct container access cannot bypass the claim contract by avoiding Gateway.
- All four app configuration files explicitly map the two environment values. Existing constructors for isolated tests enforce the same strict defaults; Spring injection uses the configured issuer/audience.
- New behavioral tests prove signed *wrong/missing* issuer/audience return **401**, while a correct signed ADMIN token succeeds, and blank configured values refuse startup.

## CI acceptance model

The standalone B57 GitHub Actions job checks out the exact stacked PR head, runs the full four-module Java test reactor (including these new signed claim regressions), builds real Gateway/User/Audit images on the previously verified Corretto 21.0.13, runs genuine Identity token fixtures through actual Gateway/User/Audit consumer tests, and reruns the **26 direct-container/Gateway** plus **30 numeric UID User-row nonowner** real HTTP assertions using disposable tmpfs PostgreSQL, synthetic accounts, no host ports or persistent volumes. CI must pass on the **final exact HEAD**, not just an earlier implementation SHA.

## Initial hosted exact-implementation security evidence

[GitHub Actions #40301483259](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40301483259) **completed SUCCESS** on the initial B57 implementation head `7728167e014d3da492460b352c164007426e75fa`:
- **8/8** B57 offline source and strict-verifier regression checks plus **10/10** pre-existing B56 offline checks.
- Real four-module Maven reactor: **Gateway 24/24, User 27/27, Identity 17/17, Audit 19/19 = 87/87 tests passed** with zero failures/errors/skips. The new Java behavioral tests deny *wrong and absent* signed issuer/audience claims.
- Real `IdentityIssuedJwtContractIT` synthetic issuer/consumer tests: **Gateway 4/4, User 3/3, Audit 3/3 = 10/10 passed** with the actual Identity-issued tokens, not hand-written substitutes.
- Real Gateway/User/Audit patched Corretto 21.0.13 containers built and ran as `10001:10001` on one internal-only network, zero published host ports, PostgreSQL tmpfs.
- Independent probe containers returned **26/26** backend/Gateway JWT route checks and **30/30** nonowner signed Identity UID/User-row collision checks, including authorized ADMIN create/read/delete and prohibited nonadmin same-numeric-ID access. CI cleanup succeeded.

The implementation commit is verified, but document-only follow-up changes require a final exact-head rerun before the final B57 checkpoint can be marked complete. This is **not** a release GO, production JWT migration or key rotation.

## Intentional compatibility break — production rollout blocker

Previously issued tokens contain **no** `iss`/`aud` and will be denied by the strict verifier. Do **not** deploy the Gateway, User or Audit changes without the Identity issuer at the same time. Preflight every existing desktop/browser/mobile/agent consumer that stores JWTs or connects directly; coordinate sign-in/session renewal and refresh-cookie continuity. Never enable indefinite dual-format acceptance to hide migration failures. Test staged rollback of the entire coordinated source change; rolling back only one verifier will produce inconsistent auth across the fleet.

**No secret or key rotation happens in B57.** The existing common symmetric HMAC key remains powerful enough for any holder to mint tokens; a later project-wide identity-owned asymmetric signer with JWKS/public-only verifier keys and a bounded two-key rotation window is preferable. It must include distinct service audiences, key IDs, expiry/revocation policy and actual integration testing before rollout. Do not invent or commit signing secrets/JWKS entries.

**Machine-to-machine and Syntra runtime trust remain separate:** those agents must validate an explicitly approved audience/workload identity contract and object/mission ownership, not blindly accept `aetheris-api` as universal access.

## Outstanding release NO_GO

Strict issuer/audience is not ownership or tenant isolation. Issue #251 still needs a reviewed Identity ↔ User resource model and object authorization, workload identities, key-rotation/revocation plan, actual Kubernetes bypass tests and browser/mobile/agent compatibility. Issue #242 historic DBA restore, #255 source-image release acceptance, #259 final SBOM/provenance approval, Stage45 and all nine runtime certification gates remain unresolved. This PR performs **no** production/main changes, secrets, Kubernetes configuration, registry publication or actual users' session rotation.
