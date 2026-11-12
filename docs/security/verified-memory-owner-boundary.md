# Verified memory ownership across the gateway boundary

**Status:** gateway identity-mapping implementation proposed, pending CI and reviewer validation. Not an operational Syntra Desktop memory integration.

The memory controller in the external certified AI runtime expects `X-Aetheris-Owner-Id`. Sending this header from the desktop would let a caller select another user's memory namespace. The gateway is the only authority allowed to produce that header when forwarding `/api/orchestrator/memory/**`:

1. Verify the JWT signature, expiry, scope, and mandatory subject/role as before.
2. Require a **positive numeric JWT `uid`** for memory routes. Missing/non-numeric/invalid identity fails with HTTP 401. Do not infer identity from email or request headers.
3. Strip all client-supplied `X-Aetheris-Owner-Id` headers on every protected route.
4. For memory routes only, set `X-Aetheris-Owner-Id: user:<verified-uid>` on the forwarded request. This deterministic namespace is stable across sessions and different for different verified users. Existing memory data under other owner IDs is **not implicitly migrated**.
5. Preserve the existing `orchestrator:read` / `orchestrator:write` enforcement and operation controls.

**Important network trust boundary:** the external runtime's existing memory controller does **not** independently authenticate this owner header. Compose's orchestrator *published* port is restricted to `127.0.0.1:8090` for the existing Syntra read-only loopback health probe, but localhost access still exists. The gateway mapping is **not** a complete end-to-end authorization mechanism for requests sent directly to the runtime. Production deployment must isolate the runtime from all untrusted callers (or add independent service-to-service authentication/identity verification) **before** allowing memory reads or writes. Do not expose port 8090 publicly. Do not claim hostile-local-process protection.

The pre-PC Syntra authenticated contract in PR #207 is still `SOURCE_VERIFIED_NOT_WIRED`; it must be updated against the final merged source revision and the explicit gateway memory mapping in a separate reviewed change. No desktop session or memory calls are activated by this PR.

The certified AI runtime revision is unchanged. No model, browser, OS automation or hardware authority is added. `BLOCKED_PENDING_HARDWARE` remains authoritative.
