# Aetheris direct-backend matrix-parameter authorization guard

**31 October 2026 — draft review only — RELEASE NO_GO**

## Proven finding

The User and Audit services' initial direct-JWT filters in [draft PR #323](https://github.com/teldigi5-wq/aetheris-platform/pull/323) used an exact raw-URI check to decide whether to run. That code recognized `/api/users` and `/api/events` (including plain nested paths), but did not account for matrix-parameter request paths such as `/api/users;variant=1`. Spring MVC may dispatch these decorated paths to the same controller after interpreting path parameters.

A **real JUnit/MockMvc test** against User's unmodified filter returned **HTTP 200** for a bearer-less decorated route where **HTTP 401** was required, proving the filter could be skipped in this Java request stack. Evidence: [Actions run 41201802212](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/41201802212), DirectApiJwtFilterTest.matrixParametersCannotSkipDirectBackendAuthorization (expected 401, actual 200).

This establishes a source-level authorization bypass **in an unmerged draft candidate**, not a known compromise or internet-reachable deployed service.

## Proposed fix

- The User and Audit DirectApiJwtFilter.shouldNotFilter methods now apply JWT verification to the **entire raw `/api` URI namespace**, including matrix-parameter route variants and future `/api` endpoints, instead of exact controller prefixes.
- Preserve non-API operational endpoints such as Actuator health/liveness for existing Compose/Kubernetes probes. These remain subject to their own exposure/security configuration.
- Authorized and unauthenticated requests to canonical supported routes continue to use existing signed JWT, role and scope controls. Signed requests to unrecognized decorated routes remain **403** instead of silently reaching controllers; missing bearer token returns **401**; missing signing key remains **503**.
- Both User and Audit servlet/MockMvc tests now cover decorated paths, signed decorated paths, future API routes, and a health-path filter exception, with zero service interactions on denied requests.

## Validation requirements

1. Full direct-backend JWT workflow at the **exact draft head** must pass both services' signed/unsigned servlet tests and all four Java modules, while retaining source-only release NO_GO proof.
2. Validate against an actual staged embedded servlet container, any reverse proxy/API Gateway URI rewriting, encoded/double-escaped path forms, Spring MVC path parsing, and actuator exposure; MockMvc does not prove every network path representation.
3. Confirm all current and future controllers intended for sensitive data are behind the `/api` namespace, or explicitly extend the filter's protected route policy and regression tests.
4. Independently reconcile the shared HMAC secret, issuer/audience/kid/rotation, workload-to-workload auth, tenant ownership and cross-service resource IDs with newer RS256/mTLS drafts. An API path guard alone is not sufficient for production authorization.
5. Never promote this isolated stacked PR independently of parent PR #323, credential hardening #315, and the release closure #322.

## Operational boundary

No live secret was used, no database or resource data was changed, no public exploitability was established, and no production service was contacted. **Protected main remains unchanged and production release remains NO_GO.**
