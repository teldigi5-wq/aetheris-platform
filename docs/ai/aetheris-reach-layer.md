# Aetheris Reach Layer v1

Aetheris Reach is the governed external-information and connector routing layer for Syntra × Aetheris.

It takes inspiration from the useful operational pattern demonstrated by the open-source Agent Reach project — channel catalogs, preferred/fallback backends, runtime health reporting and local-first setup — but it is implemented as an Aetheris-native control plane rather than vendoring or blindly executing third-party installer logic.

## Purpose

The Reach layer gives Aetheris one place to reason about external information channels while preserving the platform's existing rules:

- models are workers, never policy authority;
- owner rules and deterministic modes are evaluated before a route becomes eligible;
- `ZERO_COST` can reject billable backends;
- `PRIVATE` prevents protected context from being sent off-device;
- credentials and browser sessions are never assumed to exist;
- planned adapters are never reported as working integrations;
- system-level installation is never performed by the v1 planner;
- hosted CI is repository evidence, not physical-PC validation.

## Channel catalog

The v1 catalog models 15 external channel families:

1. web pages
2. web search
3. RSS / Atom
4. GitHub
5. YouTube
6. Reddit
7. X / Twitter
8. Facebook
9. Instagram
10. LinkedIn
11. Bilibili
12. XiaoHongShu
13. V2EX
14. Xueqiu
15. Xiaoyuzhou Podcast

Each channel has ordered backend descriptors with:

- transport type;
- capabilities such as read, search, transcript, profile, feed and repository access;
- priority for preferred/fallback routing;
- cost classification;
- off-device behavior;
- credential requirement;
- implementation status;
- installation scope;
- setup guidance.

## Current implementation truth

`github.official-api` is the only Reach backend marked `IMPLEMENTED` in v1 because Aetheris already has a governed GitHub adapter.

The remaining channel backends are deliberately marked `ADAPTER_REQUIRED` until a dedicated execution adapter exists and has passed repository validation. Supplying a fake runtime-ready flag cannot make an unimplemented backend routable.

This is intentional. Aetheris must not convert a roadmap entry into a capability claim.

## Routing

`POST /api/orchestrator/reach/route`

A route is eligible only when all of the following are true:

1. the channel supports the requested capability;
2. the backend is marked `IMPLEMENTED`;
3. the runtime reports the backend available;
4. required owner-controlled authentication/session state is reported ready;
5. deterministic owner policy allows the route.

The highest-priority eligible backend becomes the primary route. Any additional eligible backends become fallbacks.

Policy metadata includes the backend, channel, capability, transport, protected-data flag and whether the transport is network/off-device. This allows future owner rules to target Reach operations directly with scope `reach`.

### Private mode

The router distinguishes ordinary public internet access from protected context. For a request with `protectedData=true`, an off-device backend is evaluated as sending protected data off-device and therefore remains subject to the existing `PRIVATE` hard boundary.

## Reach Doctor

`POST /api/orchestrator/reach/doctor`

The doctor evaluates a supplied runtime snapshot and reports one of:

- `READY`
- `AUTH_REQUIRED`
- `RUNTIME_UNAVAILABLE`
- `ADAPTER_REQUIRED`
- `PHYSICAL_VALIDATION_REQUIRED`

The doctor never treats repository planning as physical-machine evidence.

Current physical-machine status remains:

`BLOCKED_PENDING_HARDWARE`

## Install planning

`POST /api/orchestrator/reach/install-plan`

The install planner is intentionally non-mutating. It can identify the preferred backend for requested channels and explain what is missing, but v1 does not:

- install packages;
- use `sudo` / administrator elevation;
- change firewall/security settings;
- extract browser cookies;
- write credentials;
- clone third-party tools into the Aetheris workspace;
- bypass owner approval.

User-local tools should eventually live outside the project workspace and be connected through dedicated adapters.

## Status and discovery

- `GET /api/orchestrator/reach/status`
- `GET /api/orchestrator/reach/channels`

The status endpoint reports the Reach layer version, channel/backend counts, implemented-backend count and physical-machine truth boundary.

## Planned adapter order

The preferred engineering order remains:

1. official API/connectors;
2. owner-approved MCP servers;
3. narrowly scoped CLI adapters;
4. bounded HTTP/RSS readers;
5. accessibility/browser automation;
6. vision-driven UI interaction only when structured integration is unavailable.

Each adapter should ship with tests for policy evaluation, credential boundaries, input validation, timeout/size limits, audit evidence and failure behavior before its catalog state is promoted from `ADAPTER_REQUIRED`.

## Next adapter candidates

The strongest next implementations after v1 are:

- allowlisted web-page reader with SSRF and response-size defenses;
- web-search MCP bridge;
- YouTube transcript/metadata adapter;
- RSS/Atom reader;
- generic MCP Reach bridge using the existing MCP registry and capability grants;
- governed browser bridge after physical-PC validation.

## Relationship to physical-PC activation

The Reach control plane can be repository-tested now. Platform CLIs, browser profiles, cookies, local executables, network behavior and GUI automation must be validated later on the owner's real target machine.

Until that evidence exists, physical status remains `BLOCKED_PENDING_HARDWARE` and **Physical-PC validation remains pending**.
