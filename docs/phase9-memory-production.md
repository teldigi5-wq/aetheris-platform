# Phase 9 — Memory Production Hardening

Phase 9 turns the existing semantic/vector memory foundation into an owner-visible, security-bounded durable memory path. It intentionally preserves the existing `SemanticMemoryService`, vector index, and Stage 9 ingestion behavior while adding a production-grade record boundary for sensitive and durable context.

## Guarantees

- **Owner isolation:** every durable record is keyed to the owner context supplied by the trusted caller boundary.
- **Project isolation:** `PROJECT` records require a project id, and direct retrieval/correction/deletion requires the matching project context.
- **Authorization before decryption:** encrypted payloads are never decrypted until owner/project checks pass.
- **Sensitive encryption at rest:** sensitive payloads use AES-256-GCM with a fresh 96-bit IV per write/correction.
- **Fail closed:** sensitive writes/decryption fail when the memory master key is absent or invalid.
- **Deterministic retrieval:** identical repository state and query input use explicit score, updated-time, and UUID tie-breaking.
- **Provenance:** every record carries a provenance type and optional reference, and search results explain the retrieval reason.
- **Retention:** expired records are excluded from retrieval and are physically purged by the scheduled retention sweep or explicit purge endpoint.
- **Correction/deletion:** owners can correct records without weakening encryption; delete physically removes the row.
- **Inspectable:** the guarded inspector endpoint exposes owner-scoped memory without direct database access.
- **Observable:** writes, retrieval hit/miss, authorization denials, correction, deletion, retention purge, and crypto failures emit Micrometer counters.

## API boundary

Base path: `/api/orchestrator/memory`

Trusted callers must send `X-Aetheris-Owner-Id`. Operations on `PROJECT` records also require the matching project context (`X-Aetheris-Project-Id` for id-addressed operations or `projectId` for search/inspector filtering).

Endpoints:

- `POST /records` — create durable memory.
- `GET /records/{id}` — retrieve after owner/project authorization.
- `PUT /records/{id}` — owner correction.
- `DELETE /records/{id}` — physical delete.
- `GET /search` — deterministic owner/project-scoped retrieval with provenance reason.
- `GET /inspector` — owner-visible filtering/inspection.
- `POST /retention/purge` — explicit physical purge of expired rows.
- `GET /capabilities` — reports Phase 9 security/runtime capabilities without exposing key material.

## Encryption key

Sensitive memory requires `aetheris.memory.master-key-b64` (normally supplied by environment/configuration as `AETHERIS_MEMORY_MASTER_KEY_B64`) containing Base64 for exactly 32 random bytes. The key must be injected from the deployment secret mechanism and must never be committed, logged, returned by APIs, or copied into evidence artifacts.

Example local key generation (prints a new key; store it in an approved secret store):

```bash
openssl rand -base64 32
```

Changing or losing the key makes existing encrypted records unreadable. Production key rotation therefore requires an explicit decrypt/re-encrypt migration before retiring the old key.

## Retention

The scheduled sweep uses `aetheris.memory.retention-sweep-ms` and defaults to 300000 ms (5 minutes). Retrieval also filters expired records immediately, so an expired item is unavailable even before the next physical purge.

## Security review notes

1. AES-GCM is supplied by the JDK; no custom cipher is implemented.
2. Sensitive plaintext is only present transiently in process memory and API request/response objects after authorization.
3. Ciphertext contains a format/version prefix and per-record IV but never the master key.
4. Query candidates are selected by owner before decryption; project boundaries are then enforced before project memory is returned.
5. Wrong owner/project requests increment denial telemetry and do not reveal plaintext.
6. Physical deletion is verified against the repository in the Phase 9 integration proof.
7. Existing semantic/vector memory APIs remain unchanged to minimize regression risk.

## Rollback

Phase 9 is additive. Rollback is:

1. stop callers from using `/api/orchestrator/memory`;
2. disable/remove the Phase 9 retention sweep if investigation requires preserving rows;
3. revert the Phase 9 commits/PR;
4. leave the existing semantic/vector memory path active;
5. preserve the encryption key while any Phase 9 encrypted rows may still need recovery or migration.

The new `aetheris_memory_records` table is isolated from the legacy knowledge/vector tables. Do **not** drop it during rollback unless the owner explicitly approves permanent data destruction.

## Acceptance proof

`Phase9MemoryProductionIntegrationTest` proves:

1. sensitive ciphertext-at-rest;
2. authorized decryption;
3. cross-owner denial;
4. cross-project denial;
5. deterministic scoped retrieval;
6. retrieval provenance explanation;
7. encrypted correction;
8. physical deletion;
9. expiry filtering;
10. physical retention purge;
11. owner-scoped inspection;
12. fail-closed sensitive encryption with no key.

The dedicated GitHub Actions gate is **Phase 9 Memory Production Proof**.
