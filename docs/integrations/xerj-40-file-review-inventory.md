# XERJ — pinned 40-file human review inventory (UNAPPROVED)

**Generated from the public Git tree at immutable SHA:** `57ad3e0e602efa6feb275328254151b959a3499b`  
**Proposed Lane A source:** 7 standard directory paths; **40 Git blobs / 66,617 Git-blob bytes**; no service `application.yml` included.  
**Status:** MACHINE-GENERATED INVENTORY ONLY — NO HUMAN PRIVACY, AUTHORSHIP, LICENCE, KEY-REUSE OR PUBLICATION CONSENT.

## Verified technical evidence

- Filtered corpus exact-head security/index run: https://github.com/teldigi5-wq/aetheris-platform/actions/runs/42158050510 — 2/2 workflow jobs successful; Git object equality, checksum-pinned Gitleaks, 40-file privacy metadata, XERJ local index and two Java line-citation assertions.
- The isolated review branch is a child of draft PR #361: https://github.com/teldigi5-wq/aetheris-platform/pull/376 . Do not merge it directly into main.
- A passing content scan does not clear old credentials or application vulnerabilities. Runtime issue #23 and Platform issue #251 remain open; Stage 45 NO_GO; XERJ HOLD.

## Current exact-head security verification (pending)

The earlier 40-file [successful run #42158502931](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/42158502931)
belongs **only** to commit `107fa7ade0407c74dfc2154c64e30bfe0ce2b11a`.
It is not proof for the newer filtered-branch head.

At `01f21b7f86574b15e1301ca39a5a367e13975f95`, the existing
branch incorporated the historical-fixture verifier from PR #361 and an
independent required `xerj-reachable-history-review-blocker` job.
The read-only source-scan and local retrieval jobs remain separate and
must run on the same exact commit. **As of this note, the new head has
no visible GitHub Actions run/check results.** Treat its CI status as
NOT VERIFIED, not SUCCESS; do not substitute results from older commits.

The known signing fixture has appeared in historical `src/test` blobs.
Test-path provenance is not proof the fixture was never used as a live
JWT signing key. The mandatory history job should remain fail-closed
until a real, redacted owner-controlled provenance/use determination
and relevant remediation are recorded. **Do not make this job optional,
allowlist its finding, or report the complete workflow as green merely
because the three independent mechanical jobs pass.**

Even if a later exact-head run verifies the filtered corpus, Stage 3
historical-key and privacy review and Stage 4 rights/licensing consent
are still unapproved. All file-level checkboxes below remain blank.
The 44-file primary review's measured 219 searchable records cannot
be reused as an indexed record count for this distinct 40-file artifact.
Publication, upstream amendment, branch merge and production deployment
remain unauthorized.

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
