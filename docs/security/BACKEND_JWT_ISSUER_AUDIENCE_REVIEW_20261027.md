# Direct-backend JWT issuer and audience hardening — review-only

**27 October 2026 · Stacked draft after #331 · Stage45 NO_GO · no production rollout**

## Problem confirmed in source

Identity currently signs a legacy shared-HMAC JWT containing `sub`, `uid`,
`role`, `scopes`, `iat`, `exp`, **but no JWT issuer or audience**.
Gateway and both direct User/Audit backend servlet filters accept the valid
HMAC token without an independently scoped target audience. This leaves
confused-deputy and token reuse concerns in addition to unresolved common-key
custody, issuer authenticity, tenant binding and object-level authorization.

## New real policy, not another isolated algorithm-only fixture

Both **actual servlet filters** now accept two *optional* reviewed environment
settings, neither added to deployment configs or production secret stores:

- `AETHERIS_BACKEND_JWT_ISSUER` — exact trusted issuer string.
- `AETHERIS_BACKEND_JWT_AUDIENCE` — exact single recipient ID for that service.

**Both unset or empty:** legacy behavior preserved for backward compatibility
(and still NOT production certified). **Either provided, even whitespace:**
strict review policy applies; missing/empty/whitespace-only/malformed/incomplete
configuration returns **503** for protected routes, rather than silently
downgrading. **Both valid:** a cryptographically verified signed JWT must have
the exact issuer, and an audience set containing **exactly one** member
equal to that backend's expected audience. Missing, wrong, extra/multi-audience
claims fail **401**, before any controller call. Role/scopes and consumers'
continued object-access denial are not relaxed.

Java MockMvc exercises the **real controllers and servlet filters** for User
and Audit, including valid one-audience privileged requests, cross-audience
denial, missing issuer, missing audience, extra audience, malformed settings,
spoofed identity headers, and API_CONSUMER denial.

## Explicit non-certification and required rollout

No production environment values are set, no changes are made to Identity
`JwtService`, Gateway, Docker Compose/Helm, persistent schemas, keys or
protected `main`. Enabling strict mode **today** with existing Identity JWTs
will intentionally reject all protected calls (401). An issuer-side coordinated
migration plus real multi-service regression and a rollback plan are required
BEFORE enabling strict mode anywhere beyond the disposable tests.

This remains the *same legacy shared HMAC secret*. It does NOT provide
asymmetric signing, independent workload identities or proven issuer trust
against another service holding the shared secret. Real owner-scoped tenant
identity and explicit database ownership have not been activated; issue #251
must remain open. The integration stack is draft/unmerged and Stage45 is
**NO_GO**, with nine outstanding independent release attestations.
