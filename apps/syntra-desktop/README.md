# Syntra Desktop Shell Contract — Stage 6

This directory defines the desktop boundary without pretending a Windows package has been built on hardware that is not available yet.

The future shell must consume the authenticated Aetheris gateway APIs, subscribe to `/api/orchestrator/live/events`, expose local deterministic STOP/PAUSE/TAKE CONTROL controls, and pair to the Windows host daemon using the Stage 6 challenge/response protocol.

The orchestrator API behind that gateway path is owned by the external `teldigi5-wq/aetheris-ai-runtime` integration. Its source is intentionally not duplicated in `aetheris-platform`; this desktop contract describes the platform-facing consumer boundary only.

Hard requirements:

- no embedded API secrets;
- OS-backed credential storage when Windows packaging starts;
- emergency stop remains local and does not depend on model inference;
- high-risk host actions require owner policy/approval;
- GPU-heavy visual effects must degrade before model inference or interaction responsiveness;
- the shell must show active model/provider, tool, task, approval, quota, host and checkpoint state;
- target high-refresh rendering when useful, while idling event-driven when static.

Actual `.exe` packaging, Windows service installation, hardware telemetry drivers and GPU tuning are deferred until the target PC is available.

**Physical-machine status remains `BLOCKED_PENDING_HARDWARE`.** This package is not presented as a production-certified owner-PC build.