# Syntra Desktop Shell Contract — Stage 6

This directory defines the desktop boundary without pretending a Windows package has been built on hardware that is not available yet.

The future shell must consume the authenticated Aetheris gateway APIs, subscribe to `/api/orchestrator/live/events`, expose local deterministic STOP/PAUSE/TAKE CONTROL controls, and pair to the Windows host daemon using the Stage 6 challenge/response protocol.

Hard requirements:

- no embedded API secrets;
- OS-backed credential storage when Windows packaging starts;
- emergency stop remains local and does not depend on model inference;
- high-risk host actions require owner policy/approval;
- GPU-heavy visual effects must degrade before model inference or interaction responsiveness;
- the shell must show active model/provider, tool, task, approval, quota, host and checkpoint state;
- target high-refresh rendering when useful, while idling event-driven when static.

Actual `.exe` packaging, Windows service installation, hardware telemetry drivers and GPU tuning are deferred until the target PC is available.
