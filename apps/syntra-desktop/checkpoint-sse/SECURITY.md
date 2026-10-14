# Syntra native checkpoint SSE client — Stage 39 draft

**Status: BLOCKED_NATIVE_AUTHENTICATED_SESSION_NOT_WIRED**

This is the first actual **Rust native loopback HTTP/SSE protocol client** for
the signed-owner checkpoint endpoint introduced by Aetheris runtime
[draft #38](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/38).
It is **not** linked into Syntra Tauri commands or the JS UI. Syntra's
earlier `mission-owner-gate` continues to reject all real subscriptions.
Do not enable backend SSE flags or put access tokens in front-end JS.

## Source-proven new backend contract

Only: `GET /api/orchestrator/owner-checkpoints/{checkpointId}/stream`
on an explicitly selected numeric IPv4 loopback port, and only after
a separately audited native identity session owns both that checkpoint
and task. Request uses `Authorization: Bearer` and
`Accept: text/event-stream`, not query-string credentials or
`Last-Event-ID`. The client prohibits HTTP redirects, proxies, DNS,
remote origins and caller-chosen paths; per-connection reading is bounded
to 128 KiB and at most 100 events.

The backend emits:

```text
event:task-recorded
id:44dd44dd-4444-4444-8444-444444444444
data:{"eventId":"44dd44dd-4444-4444-8444-444444444444","taskId":"11aa11aa-1111-4111-8111-111111111111","sequence":1,"eventAt":"2026-10-14T12:00:00Z","state":"RUNNING","agentId":"qa-engineer","provenance":"PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME"}

```

The old native `mission-stream` decoder expects `task-event`,
a task+timestamp combined event ID, and carries
`UnauthenticatedBytes`. **Do not repurpose it for this protocol.**
The new `checkpoint-sse` crate parses exactly `task-recorded` with
independent validation of task ID, event ID, journal provenance,
sequence (contiguous, no skipping), state and agent label. It rejects
cross-task records, unknown fields, malformed/oversized frames,
wrong provenance and duplicate-sequence claims. UI-facing structures
contain only task/event IDs, sequence, time, state, validated agent ID
and an explicit **journal claim, not verified execution** marker.

## Secrets and safety

`NativeAccessToken` stores secret bytes in a zeroizing string and redacts
its Debug representation; `VerifiedNativeSession` keeps only task ID,
checkpoint ID and previously declared acknowledgement sequence. **Both
types deliberately lack public constructors.** Only tests within this
crate can construct synthetic instances. No Tauri command,
Windows Credential Manager session issuer, JS bridge, persistent token
cache, automatic retry loop or production request is connected.

A fixed loopback HTTP client can be constructed, but `read_once`
cannot be invoked by the application without a private authenticated
session and access-token issuance boundary. Windows vault `Slot::RefreshToken`
is only a foundation; there is no authenticated refresh→access-token
handshake yet. In particular, **the frontend cannot supply an unverified
JWT, task ID or checkpoint and get access to a real stream**.

The native client provides `stop()` to prevent future work and to
abort between I/O chunks. This is *not* instantaneous cancellation of a
currently blocked socket read (the transport has a 35-second hard
request timeout); emergency STOP/logout propagation requires
independent lifecycle cancellation work. It has only bounded retry
*recommendations*, never an automatic network retry or activity
behind the user's back. HTTP 401/403/404, stream mismatch, task
mismatch, provenance failure, sequence gap or explicit STOP prohibit
blind reconnect.

A sent/parsed frame never updates the durable backend ACK. A future
reviewed native client must acknowledge **only after safe local
processing** via a separately signed `orchestrator:write` request;
never infer receipt or task completion from server send. Due to the
checkpoint's current read/write scope separation, the future native
session needs legitimate scoped capabilities and token renewal.

## Review-only interruptible transport (next stacked slice)

The new `async_transport::InterruptibleMissionClient` is an **additional**
native Tokio/reqwest implementation using the same private
`NativeAccessToken` and `VerifiedNativeSession` values. The earlier
blocking `LoopbackMissionClient` remains for backwards-compatible
synthetic contract tests, but its STOP limitation remains; **do not
activate the blocking transport for user-facing Mission Control**.

The new client offers a separate cloneable `NativeStopHandle`, without
credentials or user/session information. STOP uses Tokio's watch signal
and `tokio::select! { biased; ... }` to interrupt **both** waiting for
HTTP response headers and an in-progress asynchronous SSE body chunk
read. Dropping the pending network future/response stops delivering
updates. No detached request loop, automatic retries, background
execution, saved access tokens, frontend invocation, or durable ACK
operation is introduced.

Synthetic asynchronous loopback server tests intentionally stall
before response headers and after a streamed chunk. They verify native
STOP returns `Stopped` promptly rather than waiting for the older
35-second timeout. A successful async test also validates the exact
owner checkpoint route, bearer header, journal frame parsing and no
forwarded owner headers. Unauthorized HTTP 401 fails closed. The fixed
numeric loopback origin, no-proxy/no-redirect policy, existing size
and event limits, zeroized secret container, provenance checks and
private constructors are preserved.

**Limitations:** Passing cancellation tests does not establish a
system-wide emergency stop. It cancels the native transport's own
awaited requests, not remote agent commands, tools, queued tasks or
every desktop component. A future native session supervisor must
coordinate logout, token invalidation, task cancellation, native UI
notification, backend revocation and device-level approval. This
crate still has **no production native session issuer, Tauri/JS bridge
or real backend activation**. A synthetic localhost mock can only
validate transport behavior, not real JWT signature verification.

## Native session expiry and logout supervisor (review-only)

The new `session_lifecycle::NativeSessionSupervisor` ties the async owner
stream to a **short-lived, monotonic session deadline** and a single-use
terminal cancellation state. It holds a native `InterruptibleMissionClient`
and internal STOP handle. `guarded_read` races the native SSE future
against the lease deadline: expiry drops the request, invalidates the
session epoch, stops the network transport and returns `SessionExpired`.
Explicit logout stops the transport first, **even if local credential
cleanup fails**. Neither path advances the durable backend ACK.

`TrustedSessionLease` has only private fields, **no production minting
API** and no public constructor. `synthetic_lease()` exists only in
`#[cfg(test)]`. No JWT claim, UI flag, user-supplied timestamp, untrusted
gateway header or vault value is accepted as signed session evidence.
No app can call `guarded_read` with a real authenticated lease until a
separate issuer-verification and workstation review is completed.

`WindowsRefreshSlotRevoker` calls the existing isolated WinCred vault
`delete(Slot::RefreshToken)` fixed target
`com.aetheris.syntra.auth.refresh.v1`. No arbitrary credential names,
enumeration or unrelated Windows secrets are used. Logout results are
strictly `LocalRefreshSlotCleared` or `LocalRefreshSlotClearFailed`,
never "server logged out". **Deleting a locally saved refresh token
does not invalidate one already held by an attacker or a remote server**.
The identity service has a distinct `POST /api/auth/logout` revocation
contract, and its authenticated native client has NOT been implemented
yet. No live refresh token is read/stored by current Tauri.

CI tests use synthetic leases and credentials to prove: expiration
while waiting for HTTP headers; logout while a body read is blocked;
failed OS slot deletion still causing terminal native revocation;
replayed/old expiry not reactivating a session; no credential leakage or
new frontend access. The Windows job separately tests real WinCred read,
write and delete **only** in its dedicated disposable
`Slot::SmokeTest` when `SYNTRA_TEST_CREDENTIAL_VAULT=1`, never the
real `Slot::RefreshToken`.

### Authentication and emergency STOP still blocked

The platform's draft issuer/gateway PR #229 adds signed `iss/aud/uid`
contract, while identity `POST /api/auth/refresh` rotates refresh tokens.
Neither a trusted native token-refresh exchange nor independent
issuer verification and signed checkpoint ownership proof exists on the
desktop. Do **not** allow this crate to issue `TrustedSessionLease` from
an unverified access token payload, a native-only timer, a frontend claim
or an HTTP success status alone. The backend must revalidate its own
signed identity and owner/project permissions.

The supervisor currently revokes only its own native stream, not remote
AI agents, external tool invocations, other machines, or server-side
refresh tokens. Full emergency STOP requires coordinated backend task
cancellation, revocation propagation and verified UI acknowledgement.
The blocking transport's older STOP limitation is unchanged; the
async client remains the only candidate for a future user-facing
connection. No production service/feature flag, Tauri command, WinCred
real-user slot, remote account, merge or owner-PC execution is enabled.

## Native identity refresh/logout wire contract (review-only)

The private `native_refresh` module adds real, cancellation-aware
**HTTP wire-code** for the existing identity-service endpoints
`POST /api/auth/refresh` and `POST /api/auth/logout`.
Its only endpoint constructor is `#[cfg(test)]`, pinned to a numeric
`127.0.0.1` loopback port with redirects and proxies disabled. The
code is not accessible from Tauri/JavaScript, and it cannot make a
production request because **there is no production connector or
authenticated service-origin issuer**.

The synthetic refresh request transmits exactly
`{"refreshToken":"..."}`; the identity service currently implements
one-time refresh-token rotation. The native contract expects
`AuthResponse` with `accessToken`, rotated `refreshToken`,
`tokenType=Bearer`, bounded access/refresh lifetimes, and
`account{id,name,email,role,scopes}`. It strictly bounds response bytes
(16 KiB), refuses malformed/non-JSON, 401/403, unsupported fields,
impossible account ID/lifetimes, non-canonical refresh tokens, a
refreshed token identical to the original, and wrong recipient/type
shapes. Native STOP cancels pending HTTP reads/headers, and error types
never reveal credentials or arbitrary server error messages.

**Critical trust boundary:** JWT shape and HTTP 200 **do not
cryptographically verify identity**. The parsed result is named
`UntrustedRotation`, owns zeroizing strings and a redacted Debug
implementation, and cannot mint `NativeAccessToken`,
`VerifiedNativeSession`, or `TrustedSessionLease`. No result is
committed to real Windows Credential Manager, no identity state is
marked authenticated, and no stream is automatically opened. The
`/api/auth/logout` wire probe records only a **204 HTTP observation,
not remote revocation evidence**. In particular, local WinCred deletion
alone does not revoke an already stolen server refresh token.

The current platform JWT is **HS256** using a shared issuer/gateway
signing secret. Never ship that shared HMAC signing secret inside a
desktop app to verify JWT signatures; doing so would enable local
clients to mint arbitrary server-accepted tokens. Before adding a
production native session issuer, adopt a reviewed asymmetric verifier
(e.g. signed issuer access token + pinned verification keys) or a
server-attested session binding, with issuer/audience/account and
scope/expiry claims verified against an authorized backend. Also
resolve refresh-token recovery when the remote one-time rotation
succeeds but a local vault write fails (the old token may already be
spent). Consider encrypted atomic storage, slot fencing, manual
reauthentication and server-side revoke-on-error before activating.

### Synthetic test scope

The Windows and Ubuntu CI tests use synthetic fixed tokens and
disposable localhost HTTP servers to verify refresh JSON content,
response validation, non-attesting logout `204`, cancellation of a
stalled refresh and strict failure on identity/permission confusion.
No real Windows `Slot::RefreshToken` is loaded or altered. Windows CI
also preserves the existing separate fixed synthetic smoke-slot WinCred
roundtrip. All production feature flags remain off, no Tauri/UI wiring,
no protected-branch merge and no physical owner-PC test is performed.

## Validation and remaining blockers

New CI tests on Ubuntu and Windows cover strict SSE fragmentation,
legacy stream refusal, foreign-owner task IDs, replay/sequence gaps,
sensitive unknown-field refusal, bounded frames, manual STOP,
read-only local HTTP request construction with synthetic bearer tokens,
401/wrong content-type rejection, and static non-activation guarantees.
A local mock `TcpListener` never validates JWT signatures itself;
server-side JWT/DB isolation is separately tested in runtime #38.

Before enabling: coordinated issuer/gateway/runtime identity rollout,
isolated TLS/network path or verified loopback-only runtime,
real Windows vault native issuer and refresh/logout, per-task owner
authorization against real accounts, secure UI event routing, native
abortable async task and reconnect/backpressure handling, durable ACK
after rendering, multi-instance revocation, full tests on the owner's
physical Windows workstation, and protected-branch review.

**No live account accessed, no runtime deployed, no protected-main
merge and no physical PC validation.** This is a draft, tested
transport foundation only.
