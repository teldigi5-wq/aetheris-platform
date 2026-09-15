# Proactive Executive Agent

This post-roadmap capability turns the existing Aetheris proactive, approval, notification, task and voice primitives into an overnight chief-of-staff workflow inspired by the supplied reference video.

## What it does

`POST /api/orchestrator/executive/overnight-runs` accepts connector-neutral overnight signals and produces a durable executive briefing. The first implementation deliberately keeps source adapters synthetic/connector-neutral so the platform does not claim access to email, DMs, billing systems or telephony that has not been connected.

The deterministic policy is:

- trusted low-risk FAQ, DM, inbox and approved product-interest signals may be auto-handled;
- untrusted external-message signals become drafts and explicitly do not claim an external send;
- signup/conversion signals are counted for the briefing;
- code/product updates are marked verified only with `PASS` plus at least three verification runs;
- billing and deployment signals create real Aetheris tasks and are paused behind the existing owner-approval system;
- each run creates a persisted voice session containing the briefing transcript;
- the next requested briefing time is retained with the durable briefing history.

`GET /api/orchestrator/executive/briefings` returns recent persisted briefing summaries.

## Why this is safer than a generic autonomous assistant

The executive layer composes Aetheris' existing control plane instead of bypassing it. Consequential work enters the same task and approval surfaces already used by the platform. Low-risk automation is explicit and input-scoped, while external sends, financial actions and deployment actions are not silently inferred as safe.

## Hosted runtime proof

`.github/workflows/proactive-executive-agent-runtime-proof.yml` starts the real Docker Compose orchestrator and runs `tools/proactive_executive_agent_smoke.py` against it. The proof exercises trusted low-risk handling, safe drafting, billing/deployment approval gates, signup tracking, repeated code-update verification, persisted voice-session handoff, retained next briefing time, pending approvals and briefing history.

## Truth boundary

The runtime evidence class is `HOSTED_RUNTIME`. Inputs are synthetic signals. This is **not** evidence that Aetheris has live access to Gmail, Outlook, WhatsApp, Instagram, Discord, billing providers, phone networks or production deployment systems. It is also not physical-PC validation. Real adapters can be connected later through the existing integration/MCP boundaries, while retaining these approval rules.

Target-PC-dependent validation remains `BLOCKED_PENDING_HARDWARE`. This work is a post-roadmap capability pass; there is no Stage 35.
