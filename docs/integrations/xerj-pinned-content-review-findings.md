# XERJ Stage 3/4 — pinned source-content triage (not human approval)

**Review classification: OBSERVED PUBLIC SOURCE / HUMAN RIGHTS AND PRIVACY REVIEW PENDING.**

This is a technical triage of the exact **44 source files at Git commit**
`57ad3e0e602efa6feb275328254151b959a3499b` across the six
sparse path prefixes, obtained from the original Git blobs. All 44 were
inspected for candidate publication concerns. This is **not** an assertion
that a rights-holder independently reviewed every line or that publication
is authorized. The file-by-file owner signoff remains in
`docs/integrations/xerj-44-file-review-inventory.md`, all boxes unchecked.

## Concrete findings from pinned source content

| Source and exact line | Verified observation | Publication disposition |
| --- | --- | --- |
| `gateway/src/main/resources/application.yml:123-124` | Gateway JWT verifier uses an environment-configurable secret with a literal local-development fallback. | **WARN**: never recommend that fallback for production. This is not evidence of a leaked deployed signing key. |
| `identity-service/src/main/resources/application.yml:19-20` | Identity JWT issuer uses the same environment variable with a local-development fallback. | **WARN**: deployed settings must supply a real, rotated/managed secret and agree with the gateway. No actual deployment values inspected here. |
| `identity-service/src/main/resources/application.yml:8-10` | Local database connection provides default URL, username and password. | **WARN**: example-only; not a production credential policy. |
| `user-service/src/main/resources/application.yml:8-10,16-19` | PostgreSQL and RabbitMQ connections have local default names/passwords. | **WARN**: treat as local development placeholders, not verified secure production broker/database authentication. |
| `audit-service/src/main/resources/application.yml:8-10,17-20` | PostgreSQL and RabbitMQ similarly have local default names/passwords. | **WARN**: source snapshot predates unmerged, later role-isolation work; do not describe it as protected by those PRs. |
| `gateway/src/main/resources/application.yml:16-22` | Localhost CORS origin with wildcard allowed methods/headers in pinned Gateway configuration. | **CONTEXT**: policy behavior must be evaluated before any production reuse; a code example is not a completed CORS threat assessment. |
| `docs/security/token-flow.md:79-88` | Explicit threat-model table and development/production warning already identify secret-manager and cookie-handling improvements. | **RETAIN** alongside configuration examples: it identifies local/hosted proof limitations and next engineering controls. |
| `architecture/ai-runtime-certification-reference.json` and `architecture/ai-runtime-extraction-manifest.json` | Historical runtime evidence and transfer metadata, including certified destination commit and artifact digests, but no claim of registry publication. | **CONTEXT**: “certified” is internal engineering status, not independent third-party certification or current production attestation. |
| Selected identity/Gateway Java files | Verify/issue JWT and manage refresh tokens, authentication headers, cookies and identity metadata. | **WARN**: references for historical implementation details only; not a secure-by-default template or production approval. |

The selected source includes explicit application configuration and
architecture references, but the content inspection did **not** establish
any real production JWT secret, user database, deployment settings, current
tenant authorization or credential rotation. No secret literal is reproduced
in this review document. Source-tree Gitleaks and the existing metadata
scanner previously passed and are retained unchanged; their limitations
remain important.

## Historical signing-value finding, scoped precisely

The separate reachable-tree scan examined **2,633 reachable root trees**
at the prior verified XERJ head, finding **10 extra matching Git blob
revisions across four distinct `src/test/` paths**, and **zero**
non-test reachable paths. No matched test directories are part of the
44-file sparse corpus. This supports **test-only Git path provenance**
within available refs, not absence of deployment or external reuse.
A human with access to the actual environments must still determine
whether the historical signing value ever authorized real tokens,
and undertake scoped rotation/incident response if so. The mandatory
history CI job must stay **red** pending that decision.

## Required owner/maintainer actions before publication

1. Use the existing 44-entry checklist to inspect confidentiality, PII,
   generated/derived work, copyright and third-party reuse rights, with
   legitimate reviewer identity and date. Nothing here checks those boxes.
2. Confirm whether the exact historical 32-byte signing test value was
   ever configured in real development/staging/production environments
   or external secrets. Do not share its bytes publicly. If uncertainty
   remains, keep security-review status blocked.
3. Require the final XERJ candidate to disclose a historical,
   non-production-verified snapshot and to treat YAML fallback values
   as unsafe outside local development.
4. The released **v1.0.0-rc.100** consumer has been shown in disposable CI
   to respect sparse paths. Older `corpus-hub` parser implementations
   may ignore `paths`. Specify a tested minimum consumer version and
   reject clients that would harvest excluded test/deployment material.
5. With reviewed licensing, ownership and new owner-specific consent,
   generate the actual Lane A manifest and obtain XERJ maintainer approval
   for changing the *existing auto-generated draft*, not a duplicate.

**No approval is given.** Formal publication progress remains 2/6,
Stages 3–6 pending; Aetheris Stage 45 remains a separate operational
NO_GO. No upstream submission, re-pin, merge, deployment or rotation
follows from this triage.
