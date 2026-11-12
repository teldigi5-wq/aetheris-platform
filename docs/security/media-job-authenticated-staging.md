# Aetheris Media Studio — authenticated staging boundary (Step 5)

**Scope:** source and hosted CI verification only. This is NOT a desktop-to-GPU execution bridge, installed ComfyUI service, production endpoint, or physical device certification.

## Why this endpoint is gateway-local

The platform's existing `JwtAuthenticationFilter` is a Spring Cloud Gateway `GlobalFilter`, which applies to gateway-routed traffic. An annotated local controller must **not** assume that GlobalFilter verifies it. The new `MediaJobAuthWebFilter` is a **WebFlux WebFilter** covering exactly `/api/media` and its descendants, runs before handler selection and requires a valid signed JWT.

- `uid` must be a positive signed **numeric** claim. Never trust caller-supplied `ownerId`, `X-Aetheris-Owner-Id`, `X-Aetheris-User-Id` or a form field as the owner.
- Identity Service now issues `media:read` to all existing roles, `media:write` only to ADMIN and DEVELOPER. Tokens are time-limited and signed with the same configured JWT secret.
- Only requests marked `X-Aetheris-Client: syntra-native` with a bearer token are accepted. The marker is NOT authentication on its own; it is a defense-in-depth client contract. `Origin` and `Cookie` headers are rejected to avoid accidentally treating browser cookies as media authority. GET requires `media:read`, POST/DELETE require `media:write`. Only JSON is accepted for POST. Errors and results are `Cache-Control: no-store`.
- Unknown methods and encoded/suspicious media paths are rejected. Successful authorization is carried through a server-only `ServerWebExchange` attribute, not user-controlled HTTP headers.

## Current endpoints (owner-identity required)

`GET /api/media/capabilities` — returns only truthful staging/media readiness, `stagingEnabled`, `imageGenerationState: NOT_WIRED`, `videoGenerationState: NOT_WIRED`, `gpuJobStarted:false`, and `BLOCKED_PENDING_HARDWARE`.

`POST /api/media/jobs` — only for a correctly signed, scoped owner when `AETHERIS_MEDIA_STAGING_ENABLED=true`. Requires `Idempotency-Key` (canonical UUID) and a bounded object:

```json
{
  "kind": "IMAGE",
  "prompt": "a mountain at sunrise",
  "negativePrompt": "blur",
  "width": 512,
  "height": 512
}
```

The controller rejects any unexpected JSON field (including `ownerId`, `model`, `workflow`, URL/path fields) and any string/float coercion of numeric dimensions. This prevents silently expanding the staging surface when future generators are added.

Supported sizes: 512×512, 512×768, or 768×512. Returns `202` with `STAGED_NOT_EXECUTED`, UUID, creation time and no prompt text. Not a media generation response. A repeated owner+key+payload returns the original record; conflicting repeated content returns `409`. Video or unsupported resource sizes are rejected.

`GET /api/media/jobs/{uuid}` — returns only **the signed owner's** temporary staged metadata; a different owner gets the same `404` as an unknown ID.

`DELETE /api/media/jobs/{uuid}` — deletes the **signed owner's** pending staged metadata; does **not** cancel any GPU process (none is launched).

## Default and safety boundaries

- **Default:** `AETHERIS_MEDIA_STAGING_ENABLED=false` (POST/GET job mutations return `503`); this flag, even if explicitly enabled, only stages server-side metadata.
- The service does not call local runtime, ComfyUI, HTTP providers, model workers, file storage, subprocesses, shell, GPU or any external API.
- Fixed 10-minute expiry; up to eight owner records and 256 global records, with synchronized staging/idempotency operations. State is process-local; restart drops the data.
- Prompt text is used only for a per-process salted request fingerprint for idempotency and is **not retained** in job records, returned, logged, or persisted to disk.
- The browser is not connected. **Do not store native bearer tokens in localStorage or add direct calls to an unauthenticated loopback ComfyUI endpoint.**
- Never enable staging in a public deployment without a security and rate-limit review. Gateway-local endpoints do not inherit the orchestrator route's Redis rate limiter, so this metadata-only service uses hard in-memory quotas, not distributed production limits.
- Real authenticated **GPU execution** still requires a separately reviewed trust channel between gateway and runtime, server-side capability/tenant enforcement, cancellation, artifact ownership, retention, replay protection, node health and physical hardware evidence.

## Stage 23 frozen-contract inventory change (review required)

Stage 23 hashes **all annotated core HTTP routes**; four intentionally added local gateway media routes changed that approved inventory. We did **not** disable `contract_guard.py`, remove required jobs, weaken JWT checks, or alter certified external runtime metadata. The sole allowed API inventory delta is:

| Method | New path | Scope |
|---|---|---|
| GET | `/api/media/capabilities` | `media:read` |
| POST | `/api/media/jobs` | `media:write` |
| GET | `/api/media/jobs/{id}` | `media:read` |
| DELETE | `/api/media/jobs/{id}` | `media:write` |

Original Stage 23 contract hash: `31f345f0b0c882c5ed2faae4739747b4d18c1e0357592bc66be774c9bdd1d26f`.

Recomputed Stage 23 hash from the failed guard's canonical evidence (before updating the pin): `f3dbafffce15c4a1ae0e0671c9a36f58664315d760023f3cb23dc31202c7fc48`.

`scripts/stage23/expected-contract.sha256` was updated to the observed value **for review on this draft PR only**, with the hashing script and guard unchanged. The corresponding `configs/stage26-evidence-policy.json` pin now matches it, so Stage 26's nested checksum integrity test stays intact; no hard-boundary values, runtime certification, or public-ready claims changed. Reviewers must explicitly approve this four-route schema expansion and check the regenerated inventory. A green guard after modifying its pinned hash is **not independent authorization** to change the API.

## Hosted test strategy

`./mvnw -f gateway/pom.xml -Dtest=MediaJobAuthWebFilterTest,StagedMediaJobServiceTest,MediaEndpointWebFluxIntegrationTest test`

Tests cover missing/malformed/expired JWT, altered owner headers, non-numeric `uid`, missing media scopes, native client marker, browser-origin/cookie rejection, unsupported method/content-type, unauthorized direct controller access, cross-owner GET/DELETE, prompt-free responses, replay collision, expiry, capacity quotas and GPU truth status. They require neither a laptop, ComfyUI, CUDA, AI checkpoints, money, nor cloud generation.

**Status remains `BLOCKED_PENDING_HARDWARE`** even if source tests pass. Pre-existing reviewed platform dependency and memory authorization PRs (#208 and #210) must be reconciled by maintainers before merging; do not bypass protected checks.
