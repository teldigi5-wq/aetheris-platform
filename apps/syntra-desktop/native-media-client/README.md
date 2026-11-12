# Syntra native Media Studio gateway contract

**Status:** typed Rust request/response logic with mock transport and unit tests. Production credential broker and real HTTP transport are **NOT WIRED**; Windows package still requires a clean independent build and physical PC verification.

This crate is a hardware-independent continuation of Media Studio Step 5, complementing the **unmerged** platform gateway staging API [PR #212](https://github.com/teldigi5-wq/aetheris-platform/pull/212). It intentionally does not merge that PR or change external AI-runtime certification. The gateway handles signed numeric JWT identity and owner isolation; this crate never accepts `ownerId`, bearer credentials, or an upstream URL from web JavaScript.

## Built

- `NativeMediaClient`: typed native-facing `GET /api/media/capabilities`, `POST /api/media/jobs`, owner-scoped `GET/DELETE /api/media/jobs/{id}`.
- Validates the same image-only staging restrictions and supported dimensions as gateway PR #212; the video path remains unimplemented.
- Requires canonical idempotency/job UUIDs, fixed relative paths and an explicit gateway port; path traversal and arbitrary URL input are impossible through the public API.
- Requires a native `NativeCredentialProvider` before any transport call. `NoNativeCredential` is the **only shipped source**; it always refuses and never reads an env var, JS state, plaintext file or CI secret.
- `NativeAccessToken` hides contents in `Debug` output and clears its owned byte buffer on drop (best effort, not a security substitute for OS-backed storage).
- HTTP remains a `NativeGatewayTransport` **test interface only**. Real reqwest loopback/no-proxy/no-redirect implementation has not been approved or connected, because native token lifecycle/OS trust is still pending.
- Rejects non-202/200 status, malformed/unbounded replies, any `gpuJobStarted:true`, `state!=STAGED_NOT_EXECUTED`, `physicalPcStatus!=BLOCKED_PENDING_HARDWARE` and contradictory media capabilities. Do not treat this as a generated artifact or upload proof.
- Tauri adds `native_media_bridge_status`, a **read-only** command with no credentials, prompt arguments or generation side effects. A front-end can ask it whether any native bridge has been authenticated but it cannot activate one.

## Tests (no laptop required)

```powershell
cd apps/syntra-desktop/native-media-client
cargo test
```

A dedicated GitHub Actions job compiles the standalone Rust library and checks credential/transport fail-closed rules independently of the existing Tauri installer, which currently needs its own compatibility fix in [PR #213](https://github.com/teldigi5-wq/aetheris-platform/pull/213). Do not bypass Windows installer failures; standalone crate tests are not a packaged-app proof.

## Next acceptance gates

1. Review and land security/dependency changes (#210, #212) through protected main, **not by this PR**.
2. Use a supported Windows OS-backed credential broker (e.g., Windows Credential Manager / DPAPI) with token expiry, refresh/revocation and no untrusted WebView token exposure.
3. Implement a narrowly scoped native HTTP adapter bound only to validated loopback Gateway; no proxy, redirects or secret logging. Prove it with adversarial test-server requests.
4. Only after authenticated staging is validated may Syntra's Media Studio UI expose a **staging**, not generation, action. All requests must remain owner-scoped and no GPU work can run until a separately certified execution path exists.
5. Signed installer and physical-PC benchmark remain `BLOCKED_PENDING_HARDWARE`.

No production, physical GPU readiness, cloud/paid provider capability, image generation in the desktop or video generation is claimed by this PR.
