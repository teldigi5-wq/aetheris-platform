# XERJ — pinned 40-file human review inventory (UNAPPROVED)

**Generated from the public Git tree at immutable SHA:** `57ad3e0e602efa6feb275328254151b959a3499b`  
**Proposed Lane A source:** 7 standard directory paths; **40 Git blobs / 66,617 Git-blob bytes**; no service `application.yml` included.  
**Status:** MACHINE-GENERATED INVENTORY ONLY — NO HUMAN PRIVACY, AUTHORSHIP, LICENCE, KEY-REUSE OR PUBLICATION CONSENT.

## Verified technical evidence

- Filtered corpus exact-head security/index run: https://github.com/teldigi5-wq/aetheris-platform/actions/runs/42158050510 — 2/2 workflow jobs successful; Git object equality, checksum-pinned Gitleaks, 40-file privacy metadata, XERJ local index and two Java line-citation assertions.
- The isolated review branch is a child of draft PR #361: https://github.com/teldigi5-wq/aetheris-platform/pull/376 . Do not merge it directly into main.
- A passing content scan does not clear old credentials or application vulnerabilities. Runtime issue #23 and Platform issue #251 remain open; Stage 45 NO_GO; XERJ HOLD.

## 2026-11-09 verified exact-head security status (review only)

The existing child draft PR #376 reconciled with parent PR #361 and its
historical-fixture controls at commit `0567ddb5b91940da30f4fe90fa68169b20c5087a`.
[Actions run #42499854080](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/42499854080)
completed on **that exact commit** with **3 successful jobs and 1 deliberately
failed mandatory historical-key review**:

- **PASS — `xerj-pinned-source-scan`:** original immutable pin has 44 files /
  75,323 Git-blob bytes; seven safe directory selectors reproduce the proposed
  **40 files / 66,617 Git-blob bytes**, excluding all four service
  `application.yml` files. Gitleaks v8.30.1, metadata/privacy preflight
  and four source-filter failure-mode tests passed on that filtered scope.
- **PASS — `xerj-local-retrieval-preview`:** actual XERJ rc.100 sparse consumer
  harvested **40/40** filtered files, indexed **213 searchable records** in
  one dataset, and passed two source file:line retrieval checks. The 66,617
  catalog-accounted content bytes are **not a physical index-size measurement**.
- **PASS — `xerj-upstream-registry-schema-negative-review`:** real upstream
  validator accepted its pristine registry and correctly rejected an
  unreviewed disposable manifest. This is not acceptance of an approved
  manifest or amendment to the existing upstream author candidate.
- **EXPECTED FAIL — `xerj-reachable-history-review-blocker`:** 10 additional
  distinct historical Git blob revisions matched the known synthetic JWT
  signing fixture across four test-only paths (zero detected non-test paths).
  Neither Git-path classification nor missing current Docker data establishes
  that the value was never a live signing key. **Do not waive or remove this
  failure without a defensible, owner-controlled disposition.**

Machine-screened source remains **NOT APPROVED** for publication. Root
Apache-2.0 licence text does not establish third-party rights or authorize
worldwide indexing; the historical-source and production-NO_GO limitations
are mandatory. Prior 44-file / 219-record results belong to a **different**
corpus scope and must not be substituted here.

## 2026-11-09 owner scope decision packet (all choices unapproved)

**Recommendation for consideration: retain the existing 40-file filtered
candidate**, rather than reverting to the 44-file version with local
development JWT/database/RabbitMQ configuration fallbacks. This is a
technical risk-reduction recommendation, **not owner consent or a legal
licence decision**. Before deciding, review these exact inclusion risks:

| Source category | Inclusion decision requiring owner review | Important limitation |
| --- | --- | --- |
| `gateway/src/main/java` (4 files) | Keep historical JWT/scope boundary logic for citations? | Gateway filters do not prove direct internal-service access is isolated in a real deployment; rate-limit identity assumptions need context. |
| `identity-service/src/main/java` (12 files) | Keep historical JWT and refresh-token contracts? | Runtime signing-key provenance is unknown; browser cookie protections depend on proxy/TLS configuration. |
| `user-service/src/main/java` (14 files) | Keep UserController and event/data DTOs? | Direct UserController has no independent method-level authorization annotations in this pin; user/event schemas include name and email fields, not live records. |
| `audit-service/src/main/java` and migration (7 files) | Keep AuditController and audit persistence/event schema? | Direct AuditController lacks independent method-level authorization annotations; audit schema stores user IDs, names and emails. |
| `architecture` (2 JSON manifests) | Keep internal extraction/certification reference metadata? | `CERTIFIED` refers to internal CI/source claims, not external certification, image registry publication or production readiness. |
| `docs/security/token-flow.md` (1 file) | Keep explanatory threat model and non-production caveats? | Described controls are historical source contracts; real deployed protection was not verified. |

**Owner-only decisions, all pending:**

- [ ] **Source scope:** retain exact 40 files / seven directory-only paths
      at source SHA `57ad3e0e602efa6feb275328254151b959a3499b`,
      *or* request specific additional exclusions and regenerate the manifest.
- [ ] **Public-use semantics:** explicitly classify this as **historical,
      non-production-certified reference source**, and document direct
      internal-service exposure, privacy/event-schema and JWT limitations.
- [ ] **Attribution and intellectual-property rights:** personally verify
      ownership/originality, any copied/generated or third-party material,
      licences and attribution across all 40 paths in the worksheet below.
- [ ] **Historical signing-key deployment disposition:** verify with
      owner-controlled evidence whether the known fixture was ever live;
      if live, coordinate credential rotation, session invalidation and
      incident assessment without disclosing a secret.
- [ ] **Exact-scope consent:** separately and explicitly authorize worldwide
      XERJ indexing/reuse for the reviewed snapshot. Do not reuse the older
      email-consent assertion for a different repository pin/scope.
- [ ] **Upstream handoff:** after the above, produce a real reviewed Lane A
      manifest and reconcile the **existing** XERJ author-candidate through
      maintainer review; do not create a duplicate or assert fake approvals.

No choice above is checked or inferred from a user's generic "NEXT"
instruction. Formal project status remains **2/6 XERJ stages completed;
four stages pending; public publication HOLD**. Production Aetheris
Stage 45 is separate and remains NO_GO.

## Reviewer checklist (not completed)

- [ ] Privately determine whether any public development JWT, database, RabbitMQ or other fallback credential was ever deployed; rotate any confirmed live reuse through an approved operational procedure.
- [ ] Read each of the **40 exact pinned files** for sensitive context/PII, third-party or copied code, real authorship, and redistribution rights. No automatic checkbox changes.
- [ ] Evaluate whether historical User/Audit endpoints without independently enforced authorization should be included as *unsafe historical examples* or further excluded. Do not label any of this code production-secure.
- [ ] Independently verify licence(s), attribution, and correct XERJ reuse classification (`adapt-with-attribution`, `approach-only`, or `mixed`) after inspecting actual licensing material.
- [ ] Approve this immutable **40-file** corpus and seven directory selectors for public XERJ reference use; confirm the reviewer identity and date. The older XERJ email record is for a different scope and does not grant consent here.
- [ ] After genuine approval, generate a Lane A manifest through the current XERJ tool, record measured manifest fields, run upstream validators, and submit through the existing author/maintainer review path.

## Machine-generated file-by-file approval worksheet

| # | Source file at immutable pin | Bytes | Git blob SHA-1 | Review area | Human reviewed |
|---:|---|---:|---|---|---|
| 1 | [`architecture/ai-runtime-certification-reference.json`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/architecture/ai-runtime-certification-reference.json) | 2122 | `7971ea424fc6f7ece3f671c6e8b38ae81b0f7826` | Architecture metadata | ☐ Pending |
| 2 | [`architecture/ai-runtime-extraction-manifest.json`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/architecture/ai-runtime-extraction-manifest.json) | 5833 | `659875973c928e39e30193daa1a0731c74a88f04` | Architecture metadata | ☐ Pending |
| 3 | [`audit-service/src/main/java/io/aetheris/audit/AetherisAuditServiceApplication.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/audit-service/src/main/java/io/aetheris/audit/AetherisAuditServiceApplication.java) | 348 | `b85a8d969ed5c6ad62dc2fcb1f803574e4b90ad0` | Audit API/data | ☐ Pending |
| 4 | [`audit-service/src/main/java/io/aetheris/audit/AuditController.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/audit-service/src/main/java/io/aetheris/audit/AuditController.java) | 550 | `a20ab3d64bb1c28dbf48f52b009bfd3de9355f5c` | Audit API/data | ☐ Pending |
| 5 | [`audit-service/src/main/java/io/aetheris/audit/AuditEventRepository.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/audit-service/src/main/java/io/aetheris/audit/AuditEventRepository.java) | 1749 | `c540866617630d211143e0d21408a5b0e967f4f5` | Audit API/data | ☐ Pending |
| 6 | [`audit-service/src/main/java/io/aetheris/audit/AuditEventStore.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/audit-service/src/main/java/io/aetheris/audit/AuditEventStore.java) | 1170 | `ff5a7eb038f08e2e93d534fd09af0613187c5772` | Audit API/data | ☐ Pending |
| 7 | [`audit-service/src/main/java/io/aetheris/audit/RabbitAuditConfig.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/audit-service/src/main/java/io/aetheris/audit/RabbitAuditConfig.java) | 1153 | `270192218d1c9bb0ecec32e955f838c34857455e` | Audit API/data | ☐ Pending |
| 8 | [`audit-service/src/main/java/io/aetheris/audit/UserDomainEvent.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/audit-service/src/main/java/io/aetheris/audit/UserDomainEvent.java) | 232 | `fa80c2110df71d9abeb6a8a3076ff68b454b9f9a` | Audit API/data | ☐ Pending |
| 9 | [`audit-service/src/main/resources/db/migration/V1__create_audit_events.sql`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/audit-service/src/main/resources/db/migration/V1__create_audit_events.sql) | 380 | `727fe44f369d7ff1e8d0db2dddf850215cdf13f1` | Audit API/data | ☐ Pending |
| 10 | [`docs/security/token-flow.md`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/docs/security/token-flow.md) | 8547 | `7efcf9e0e15924ce3524aeb7a1de2011610118b4` | Security documentation | ☐ Pending |
| 11 | [`gateway/src/main/java/io/aetheris/gateway/AetherisGatewayApplication.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/gateway/src/main/java/io/aetheris/gateway/AetherisGatewayApplication.java) | 340 | `35562a07a1aa68f71b5472d4fd062043e109fac8` | Gateway authentication | ☐ Pending |
| 12 | [`gateway/src/main/java/io/aetheris/gateway/FallbackController.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/gateway/src/main/java/io/aetheris/gateway/FallbackController.java) | 1751 | `9e98dc5534cc0272faa5003fa13b275c06dcc6d8` | Gateway authentication | ☐ Pending |
| 13 | [`gateway/src/main/java/io/aetheris/gateway/JwtAuthenticationFilter.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/gateway/src/main/java/io/aetheris/gateway/JwtAuthenticationFilter.java) | 6072 | `a7a6a0f7a721864ea90c25609f34c319227f1893` | Gateway authentication | ☐ Pending |
| 14 | [`gateway/src/main/java/io/aetheris/gateway/RateLimitConfig.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/gateway/src/main/java/io/aetheris/gateway/RateLimitConfig.java) | 853 | `47d858376ab53dd9ea2ca926ca55ab4bcaf3f5fd` | Gateway authentication | ☐ Pending |
| 15 | [`identity-service/src/main/java/io/aetheris/identity/AetherisIdentityServiceApplication.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/identity-service/src/main/java/io/aetheris/identity/AetherisIdentityServiceApplication.java) | 357 | `55884e5674eff884518595b7a2c63fec4f3bf685` | Identity/session security | ☐ Pending |
| 16 | [`identity-service/src/main/java/io/aetheris/identity/AuthController.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/identity-service/src/main/java/io/aetheris/identity/AuthController.java) | 2125 | `ccf1082e0dec271ab6edd9229750c1040bde6919` | Identity/session security | ☐ Pending |
| 17 | [`identity-service/src/main/java/io/aetheris/identity/AuthDtos.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/identity-service/src/main/java/io/aetheris/identity/AuthDtos.java) | 1539 | `55796f8a3e4daaabc1a660bfa7d5ba37dac19430` | Identity/session security | ☐ Pending |
| 18 | [`identity-service/src/main/java/io/aetheris/identity/BrowserAuthController.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/identity-service/src/main/java/io/aetheris/identity/BrowserAuthController.java) | 8394 | `3801a3178c615a11b4570f6f2a58deb7d1e81bb8` | Identity/session security | ☐ Pending |
| 19 | [`identity-service/src/main/java/io/aetheris/identity/IdentityAccount.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/identity-service/src/main/java/io/aetheris/identity/IdentityAccount.java) | 1113 | `a21c67e90a5643ffdeb37a9e52e49d8c5b0f4e4e` | Identity/session security | ☐ Pending |
| 20 | [`identity-service/src/main/java/io/aetheris/identity/IdentityAccountRepository.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/identity-service/src/main/java/io/aetheris/identity/IdentityAccountRepository.java) | 331 | `cfe79ca54d6a1dad15a4a50b58119f4185b2a8d6` | Identity/session security | ☐ Pending |
| 21 | [`identity-service/src/main/java/io/aetheris/identity/IdentityService.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/identity-service/src/main/java/io/aetheris/identity/IdentityService.java) | 3038 | `ba89bc8d3be239feaeb248b5a3e2c7655efbd61d` | Identity/session security | ☐ Pending |
| 22 | [`identity-service/src/main/java/io/aetheris/identity/JwtService.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/identity-service/src/main/java/io/aetheris/identity/JwtService.java) | 1541 | `9451cd5b930d6487c0bc4c038f77e19f484d0589` | Identity/session security | ☐ Pending |
| 23 | [`identity-service/src/main/java/io/aetheris/identity/RefreshToken.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/identity-service/src/main/java/io/aetheris/identity/RefreshToken.java) | 1437 | `28cc62b2be7b3557342b7336fbdea2e47576f13d` | Identity/session security | ☐ Pending |
| 24 | [`identity-service/src/main/java/io/aetheris/identity/RefreshTokenRepository.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/identity-service/src/main/java/io/aetheris/identity/RefreshTokenRepository.java) | 837 | `5b08c9ad16a7e6a1827e3f7f500e5ba591542a64` | Identity/session security | ☐ Pending |
| 25 | [`identity-service/src/main/java/io/aetheris/identity/RefreshTokenService.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/identity-service/src/main/java/io/aetheris/identity/RefreshTokenService.java) | 3050 | `33be008640f2c0be9376398b0eb2e3711fbeeb66` | Identity/session security | ☐ Pending |
| 26 | [`identity-service/src/main/java/io/aetheris/identity/Role.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/identity-service/src/main/java/io/aetheris/identity/Role.java) | 608 | `422ac4cb55f0053abf0363cc513593f5138be912` | Identity/session security | ☐ Pending |
| 27 | [`user-service/src/main/java/io/aetheris/users/AetherisUserServiceApplication.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/user-service/src/main/java/io/aetheris/users/AetherisUserServiceApplication.java) | 420 | `25a45569435078ae52a33401aea81b33ec30590b` | User API/authorization | ☐ Pending |
| 28 | [`user-service/src/main/java/io/aetheris/users/CacheConfig.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/user-service/src/main/java/io/aetheris/users/CacheConfig.java) | 943 | `452ae248eb74d5c112a79e6366eb153f2bdba29f` | User API/authorization | ☐ Pending |
| 29 | [`user-service/src/main/java/io/aetheris/users/dto/CreateUserRequest.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/user-service/src/main/java/io/aetheris/users/dto/CreateUserRequest.java) | 311 | `3d6c69c4cbb97e030c81b2fb731581245a21f52f` | User API/authorization | ☐ Pending |
| 30 | [`user-service/src/main/java/io/aetheris/users/dto/UserResponse.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/user-service/src/main/java/io/aetheris/users/dto/UserResponse.java) | 98 | `25f4f6317acaff72a9926d7179649dcda1dc873f` | User API/authorization | ☐ Pending |
| 31 | [`user-service/src/main/java/io/aetheris/users/events/RabbitEventConfig.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/user-service/src/main/java/io/aetheris/users/events/RabbitEventConfig.java) | 596 | `73bb30d948e1187efcc8c46ce0323c7334912de8` | User API/authorization | ☐ Pending |
| 32 | [`user-service/src/main/java/io/aetheris/users/events/UserDomainEvent.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/user-service/src/main/java/io/aetheris/users/events/UserDomainEvent.java) | 239 | `100a97c0d94499e145704b0a131605041c4f029d` | User API/authorization | ☐ Pending |
| 33 | [`user-service/src/main/java/io/aetheris/users/events/UserEventPublisher.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/user-service/src/main/java/io/aetheris/users/events/UserEventPublisher.java) | 1245 | `613cf5c36161d11388f59cabc68a816cf597670b` | User API/authorization | ☐ Pending |
| 34 | [`user-service/src/main/java/io/aetheris/users/exception/ApiError.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/user-service/src/main/java/io/aetheris/users/exception/ApiError.java) | 270 | `34f2203fc6e4aea4c1561c5b7bdcedd4d8c55f38` | User API/authorization | ☐ Pending |
| 35 | [`user-service/src/main/java/io/aetheris/users/exception/GlobalExceptionHandler.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/user-service/src/main/java/io/aetheris/users/exception/GlobalExceptionHandler.java) | 1808 | `664bacbfffc25a64aebaf4551ba966db8a1b9e32` | User API/authorization | ☐ Pending |
| 36 | [`user-service/src/main/java/io/aetheris/users/exception/UserNotFoundException.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/user-service/src/main/java/io/aetheris/users/exception/UserNotFoundException.java) | 192 | `d696d445b462b0f531721c38eb550e03689eb434` | User API/authorization | ☐ Pending |
| 37 | [`user-service/src/main/java/io/aetheris/users/User.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/user-service/src/main/java/io/aetheris/users/User.java) | 809 | `9674f90a33d5dfe31916c459685c789da46bb6d4` | User API/authorization | ☐ Pending |
| 38 | [`user-service/src/main/java/io/aetheris/users/UserController.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/user-service/src/main/java/io/aetheris/users/UserController.java) | 1355 | `cf1763f2edaacb940095be7b864e332b0d869a04` | User API/authorization | ☐ Pending |
| 39 | [`user-service/src/main/java/io/aetheris/users/UserRepository.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/user-service/src/main/java/io/aetheris/users/UserRepository.java) | 202 | `10083ba3ca8e510f15465f3af7c09eb91cac4ba5` | User API/authorization | ☐ Pending |
| 40 | [`user-service/src/main/java/io/aetheris/users/UserService.java`](https://github.com/teldigi5-wq/aetheris-platform/blob/57ad3e0e602efa6feb275328254151b959a3499b/user-service/src/main/java/io/aetheris/users/UserService.java) | 2659 | `25e69343093a1a9ad44f52c9176bf6674f9d17a6` | User API/authorization | ☐ Pending |

**Evidence limitation:** Git blob SHA-1, file byte counts, automated scanner outputs and public repository licensing are not proof that an individual file's intellectual-property rights, sensitive content, deployability or historical key exposure has been independently cleared. No secrets are stored here.
