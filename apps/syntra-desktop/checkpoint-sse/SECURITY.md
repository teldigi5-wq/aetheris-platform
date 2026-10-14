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
