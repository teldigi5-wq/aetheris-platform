# Aetheris Operator v2 — Site Skills and Autonomous Web Tasks

Aetheris Operator v2 extends the generic-browser control plane with deterministic, reusable **site skills**. It is a post-roadmap capability layer; the canonical repository roadmap remains **Stage 34 / 34 COMPLETE**. There is no Stage 35.

## Purpose

The generic browser can operate arbitrary HTTP/HTTPS sites, but raw selector workflows are too low-level for repeated owner tasks. Operator v2 adds a catalog and compiler so Syntra can refer to a stable task such as `linkedin.profile.inspect` or `vercel.deployment.trigger` instead of inventing a browser workflow from scratch every time.

Models remain workers, not policy authority. A model may propose a skill and its parameters, but deterministic Aetheris code decides whether the skill exists, whether the target stays in its domain boundary, whether owner policy permits the generated browser actions, whether approval is required, and whether physical validation exists.

## Repository APIs

Base path: `/api/orchestrator/operator/site-skills`

- `GET /api/orchestrator/operator/site-skills` — list registered skill descriptors and validation state.
- `POST /api/orchestrator/operator/site-skills/compile` — compile a structured web task into a governed `BrowserPlanRequest` and evaluate it through Operator v1.
- `POST /api/orchestrator/operator/site-skills/execute` — execute only when the site skill, browser runtime, owner policy and approval state all allow it.

## Starter skill catalog

### `linkedin.profile.inspect`

Read-only LinkedIn profile inspection. It navigates to an explicitly supplied LinkedIn URL, extracts bounded text from the main content area and captures screenshot-hash evidence.

### `linkedin.profile.update-about`

LinkedIn About-section update template. The text arrives only through the ephemeral value reference `linkedin.about`; it is not embedded into the task plan or repository. The skill captures pre-change evidence, opens the editor, types through the ephemeral reference, saves, and requires post-change evidence before a success claim.

### `vercel.deployment.inspect`

Read-only Vercel deployment/dashboard inspection with bounded text and screenshot-hash evidence.

### `vercel.deployment.trigger`

Vercel deployment-trigger template with pre/post evidence and bounded status extraction. It is a mutation and therefore receives **no blind automatic retry**.

## Validation gate

All starter site skills default to `PHYSICAL_VALIDATION_REQUIRED`.

The repository intentionally does not claim that current LinkedIn or Vercel DOM selectors work on the owner's target browser. Real websites change. A template can compile and can be policy-checked without being eligible to execute.

After evidence-driven physical validation, exact skill IDs may be supplied through:

`aetheris.browser.validated-site-skills`

The value is a comma-separated allowlist of exact skill IDs. Validation is intentionally per skill; validating one LinkedIn flow does not validate every LinkedIn flow and does not validate Vercel.

The browser runtime still has its independent gates:

- `aetheris.browser.runtime-enabled`
- `aetheris.browser.physical-validated`

All of these remain false/unset by default in the pre-PC repository state.

## Retry and recovery rules

Operator v2 uses bounded recovery rather than open-ended autonomous retry.

Read-only (`OBSERVE`) skills may receive at most **two total attempts** when the browser adapter returns an ordinary execution failure. They are not retried for policy blocks, owner-approval requirements, emergency cancellation, runtime unavailability or physical-validation blocks.

Mutation (`MUTATE`) skills receive **one attempt only**. A partially successful external mutation can make a blind retry unsafe or duplicate a side effect. A later recovery system must first inspect evidence and resulting state before proposing another mutation.

## Approval and evidence

Mutation steps compile to Browser Operator actions with external-change risk. Owner policy therefore remains responsible for approval. The site-skill layer cannot downgrade Browser Operator risk.

Mutation skill descriptors also declare evidence requirements. Repository templates place screenshot and bounded text verification steps after the external action. A model is not allowed to replace those evidence requirements with a textual claim that an action probably worked.

`ALLOW` still means **eligible**, not executed. `SUCCEEDED` requires the actual Browser Operator execution result and its declared evidence path.

## Privacy and sessions

Site skills never contain passwords, cookies, bearer tokens or browser-profile data. Authenticated workflows assume owner-controlled session state will eventually be provided by the local browser/runtime boundary.

The compiler passes `protectedData` and the current operation mode directly to Browser Operator v1. In `PRIVATE` mode, protected data cannot be sent to remote sites unless an exact deterministic owner-policy exception exists.

Literal typed values remain outside plan/audit metadata and are supplied through ephemeral value references at execution time.

## Domain containment

Each site skill owns an explicit domain allowlist. For example, LinkedIn skills are limited to `linkedin.com` and Vercel skills to `vercel.com`. The compiled workflow then passes through Browser Operator v1, which also checks navigation and off-allowlist redirects.

A site skill cannot broaden its own domain boundary through task parameters.

## Current truth boundary

Repository implementation: **implemented**.

Starter site-skill physical validation: **pending**.

Generic browser physical runtime validation: **pending**.

Physical-machine status: **`BLOCKED_PENDING_HARDWARE`**.

Hosted CI verifies deterministic compilation, policy integration and fail-closed behavior. It does not prove authenticated LinkedIn/Vercel sessions, current production-site selectors, ChromeDriver/EdgeDriver behavior or the owner's eventual browser environment.
