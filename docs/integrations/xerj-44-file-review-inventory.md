# XERJ source-only 44-file human review inventory

**Status: UNREVIEWED — NO PUBLICATION CONSENT.** This checklist is a complete
mechanically generated *Git object inventory*, not a statement that any source
file has been read or approved by a person. Checkboxes remain blank until an
actual scope-specific reviewer inspects the contents, authorship, licences,
third-party material, privacy and historical/security status.

- Repository: `https://github.com/teldigi5-wq/aetheris-platform`
- Immutable source commit: `57ad3e0e602efa6feb275328254151b959a3499b`
- Curated directories: `architecture · gateway/src/main · identity-service/src/main · user-service/src/main · audit-service/src/main · docs/security`
- Git blob inventory: **44 files / 75,323 bytes**.
- The raw Git blob OIDs and byte sizes below identify exact published Git
  objects. They **do not** prove safe reuse, lack of secrets or security.
- All test directories, root deployment configs, generated evidence and the
  AI Runtime are deliberately excluded.

## Exact file-by-file reviewer decisions

For every file, a human must verify confidentiality, privacy, original
ownership, compatible third-party terms, and whether its *historic and unmerged*
implementation should be publicly retrievable. A blank checkbox means
**NOT REVIEWED**. Never bulk-approve from Gitleaks results.

### `architecture/`

- [ ] `architecture/ai-runtime-certification-reference.json` — 2122 B · `7971ea424fc6f7ece3f671c6e8b38ae81b0f7826`
- [ ] `architecture/ai-runtime-extraction-manifest.json` — 5833 B · `659875973c928e39e30193daa1a0731c74a88f04`

### `gateway/src/main/`

- [ ] `gateway/src/main/java/io/aetheris/gateway/AetherisGatewayApplication.java` — 340 B · `35562a07a1aa68f71b5472d4fd062043e109fac8`
- [ ] `gateway/src/main/java/io/aetheris/gateway/FallbackController.java` — 1751 B · `9e98dc5534cc0272faa5003fa13b275c06dcc6d8`
- [ ] `gateway/src/main/java/io/aetheris/gateway/JwtAuthenticationFilter.java` — 6072 B · `a7a6a0f7a721864ea90c25609f34c319227f1893`
- [ ] `gateway/src/main/java/io/aetheris/gateway/RateLimitConfig.java` — 853 B · `47d858376ab53dd9ea2ca926ca55ab4bcaf3f5fd`
- [ ] `gateway/src/main/resources/application.yml` — 5256 B · `41292689d047943c30f26e93211570a41edc48b1`

### `identity-service/src/main/`

- [ ] `identity-service/src/main/java/io/aetheris/identity/AetherisIdentityServiceApplication.java` — 357 B · `55884e5674eff884518595b7a2c63fec4f3bf685`
- [ ] `identity-service/src/main/java/io/aetheris/identity/AuthController.java` — 2125 B · `ccf1082e0dec271ab6edd9229750c1040bde6919`
- [ ] `identity-service/src/main/java/io/aetheris/identity/AuthDtos.java` — 1539 B · `55796f8a3e4daaabc1a660bfa7d5ba37dac19430`
- [ ] `identity-service/src/main/java/io/aetheris/identity/BrowserAuthController.java` — 8394 B · `3801a3178c615a11b4570f6f2a58deb7d1e81bb8`
- [ ] `identity-service/src/main/java/io/aetheris/identity/IdentityAccount.java` — 1113 B · `a21c67e90a5643ffdeb37a9e52e49d8c5b0f4e4e`
- [ ] `identity-service/src/main/java/io/aetheris/identity/IdentityAccountRepository.java` — 331 B · `cfe79ca54d6a1dad15a4a50b58119f4185b2a8d6`
- [ ] `identity-service/src/main/java/io/aetheris/identity/IdentityService.java` — 3038 B · `ba89bc8d3be239feaeb248b5a3e2c7655efbd61d`
- [ ] `identity-service/src/main/java/io/aetheris/identity/JwtService.java` — 1541 B · `9451cd5b930d6487c0bc4c038f77e19f484d0589`
- [ ] `identity-service/src/main/java/io/aetheris/identity/RefreshToken.java` — 1437 B · `28cc62b2be7b3557342b7336fbdea2e47576f13d`
- [ ] `identity-service/src/main/java/io/aetheris/identity/RefreshTokenRepository.java` — 837 B · `5b08c9ad16a7e6a1827e3f7f500e5ba591542a64`
- [ ] `identity-service/src/main/java/io/aetheris/identity/RefreshTokenService.java` — 3050 B · `33be008640f2c0be9376398b0eb2e3711fbeeb66`
- [ ] `identity-service/src/main/java/io/aetheris/identity/Role.java` — 608 B · `422ac4cb55f0053abf0363cc513593f5138be912`
- [ ] `identity-service/src/main/resources/application.yml` — 1134 B · `4ee43491af1b84e45a001550e6a6a44b7c15e243`

### `user-service/src/main/`

- [ ] `user-service/src/main/java/io/aetheris/users/AetherisUserServiceApplication.java` — 420 B · `25a45569435078ae52a33401aea81b33ec30590b`
- [ ] `user-service/src/main/java/io/aetheris/users/CacheConfig.java` — 943 B · `452ae248eb74d5c112a79e6366eb153f2bdba29f`
- [ ] `user-service/src/main/java/io/aetheris/users/dto/CreateUserRequest.java` — 311 B · `3d6c69c4cbb97e030c81b2fb731581245a21f52f`
- [ ] `user-service/src/main/java/io/aetheris/users/dto/UserResponse.java` — 98 B · `25f4f6317acaff72a9926d7179649dcda1dc873f`
- [ ] `user-service/src/main/java/io/aetheris/users/events/RabbitEventConfig.java` — 596 B · `73bb30d948e1187efcc8c46ce0323c7334912de8`
- [ ] `user-service/src/main/java/io/aetheris/users/events/UserDomainEvent.java` — 239 B · `100a97c0d94499e145704b0a131605041c4f029d`
- [ ] `user-service/src/main/java/io/aetheris/users/events/UserEventPublisher.java` — 1245 B · `613cf5c36161d11388f59cabc68a816cf597670b`
- [ ] `user-service/src/main/java/io/aetheris/users/exception/ApiError.java` — 270 B · `34f2203fc6e4aea4c1561c5b7bdcedd4d8c55f38`
- [ ] `user-service/src/main/java/io/aetheris/users/exception/GlobalExceptionHandler.java` — 1808 B · `664bacbfffc25a64aebaf4551ba966db8a1b9e32`
- [ ] `user-service/src/main/java/io/aetheris/users/exception/UserNotFoundException.java` — 192 B · `d696d445b462b0f531721c38eb550e03689eb434`
- [ ] `user-service/src/main/java/io/aetheris/users/User.java` — 809 B · `9674f90a33d5dfe31916c459685c789da46bb6d4`
- [ ] `user-service/src/main/java/io/aetheris/users/UserController.java` — 1355 B · `cf1763f2edaacb940095be7b864e332b0d869a04`
- [ ] `user-service/src/main/java/io/aetheris/users/UserRepository.java` — 202 B · `10083ba3ca8e510f15465f3af7c09eb91cac4ba5`
- [ ] `user-service/src/main/java/io/aetheris/users/UserService.java` — 2659 B · `25e69343093a1a9ad44f52c9176bf6674f9d17a6`
- [ ] `user-service/src/main/resources/application.yml` — 1258 B · `b6894049f3b3878f047330b1e9ac042e0819a42b`

### `audit-service/src/main/`

- [ ] `audit-service/src/main/java/io/aetheris/audit/AetherisAuditServiceApplication.java` — 348 B · `b85a8d969ed5c6ad62dc2fcb1f803574e4b90ad0`
- [ ] `audit-service/src/main/java/io/aetheris/audit/AuditController.java` — 550 B · `a20ab3d64bb1c28dbf48f52b009bfd3de9355f5c`
- [ ] `audit-service/src/main/java/io/aetheris/audit/AuditEventRepository.java` — 1749 B · `c540866617630d211143e0d21408a5b0e967f4f5`
- [ ] `audit-service/src/main/java/io/aetheris/audit/AuditEventStore.java` — 1170 B · `ff5a7eb038f08e2e93d534fd09af0613187c5772`
- [ ] `audit-service/src/main/java/io/aetheris/audit/RabbitAuditConfig.java` — 1153 B · `270192218d1c9bb0ecec32e955f838c34857455e`
- [ ] `audit-service/src/main/java/io/aetheris/audit/UserDomainEvent.java` — 232 B · `fa80c2110df71d9abeb6a8a3076ff68b454b9f9a`
- [ ] `audit-service/src/main/resources/application.yml` — 1058 B · `7c16b87fa13bce3239bdf91166ff145daa71045a`
- [ ] `audit-service/src/main/resources/db/migration/V1__create_audit_events.sql` — 380 B · `727fe44f369d7ff1e8d0db2dddf850215cdf13f1`

### `docs/security/`

- [ ] `docs/security/token-flow.md` — 8547 B · `7efcf9e0e15924ce3524aeb7a1de2011610118b4`

## Evidence and mandatory human release decision

The prior exact-pin Gitleaks, privacy-indicator and XERJ retrieval scans
passed as **mechanical checks only**. Live local XERJ indexing counted
44 source files, 219 records and 75,323 catalog content bytes. This
historical fixture still appears in 10 extra reachable blob revisions
outside the original Gateway test file; provenance and actual runtime
credential reuse are not yet independently certified.

**Before changing any checkbox or submitting a corpus:** inspect every file;
record exact reviewer handle, review date, rights/attribution exceptions,
secret-use/deployment determination and any remediation evidence in
`xerj-owner-publication-approval.md`. An authorized human must explicitly
approve **this exact source pin and the six paths**, not rely on earlier
consent for a different full-repo snapshot. A passing CI inventory checker
means only that these names/OIDs/sizes are correct. No upstream PR, owner
override, source repin, historic key waiver or Aetheris deployment is
authorized by this document.

**Formal tracker: XERJ 2/6 complete, Stages 3–6 OPEN; Aetheris Stage45 NO_GO.**
