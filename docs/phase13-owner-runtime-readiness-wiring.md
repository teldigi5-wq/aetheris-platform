# Phase 13 Slice 16 — Owner-Machine Runtime Readiness and Configuration Wiring

## Certified baseline

Slice 16 starts from certified Slice 15 canonical merge SHA `29a40ef5801082ff90bf116dde162575096cb18a`.

That SHA was observed with **46/46 push-triggered workflows successful**, zero failures, and no unfinished workflows before this slice was created.

## Objective

Turn Slice 15's concrete loopback Ollama adapter bridge into an explicitly deployable Spring configuration surface and provide truthful, read-only owner-machine runtime readiness evidence.

Slice 16 does **not** benchmark hardware, install Ollama, download a model, train an adapter, activate an adapter, reconcile lifecycle truth, or generate model output during startup/readiness checks.

The deployment path is:

`explicit owner configuration -> loopback endpoint validation -> Spring runtime bean wiring -> read-only /api/tags discovery -> typed readiness evidence -> Actuator health projection`

## Disabled-by-default deployment boundary

The feature is controlled by:

`aetheris.syntra.owner-runtime.enabled`

The committed default is `false` and can be enabled with `AETHERIS_SYNTRA_OWNER_RUNTIME_ENABLED=true`.

When disabled, the Slice 16 configuration does not create the runtime bridge, readiness service, or health indicator. Existing application-context tests therefore do not acquire a local-Ollama dependency.

The endpoint defaults to `http://127.0.0.1:11434`, but enabling the feature still passes it through the already-certified `LocalRuntimeEndpoint` contract. Non-loopback hosts, HTTPS, missing explicit ports, credentials, query strings, fragments, or path-bearing endpoints fail closed.

## Explicit model and adapter configuration

Slice 16 deliberately does not commit a routable model or adapter identity as fact.

When enabled, at least one base model must be supplied. Spring relaxed binding supports environment-backed indexed configuration such as:

- `AETHERIS_SYNTRA_OWNER_RUNTIME_BASE_MODELS_0_MODEL_ID`
- `AETHERIS_SYNTRA_OWNER_RUNTIME_BASE_MODELS_0_CAPABILITIES_0`
- `AETHERIS_SYNTRA_OWNER_RUNTIME_BASE_MODELS_0_CONTEXT_WINDOW_TOKENS`
- `AETHERIS_SYNTRA_OWNER_RUNTIME_BASE_MODELS_0_REQUIRED_RAM_MB`
- `AETHERIS_SYNTRA_OWNER_RUNTIME_BASE_MODELS_0_CPU_FALLBACK_SUPPORTED`

Optional adapter bindings additionally require the exact approved experiment/candidate/promoted/base-model/dataset-hash/artifact-hash identity plus the pre-provisioned Ollama provider alias. The logical adapter routing model ID and `aetheris-adapter-artifact://sha256/...` identity are derived from those exact values through the already-certified contracts.

Runtime candidates created from deployment configuration intentionally carry zero observational performance values. Slice 16 does not turn configuration into fake latency, throughput, quality, VRAM, or failure-rate measurements.

## Spring wiring

`OwnerLocalRuntimeConfiguration` is conditional on the explicit enable flag and creates:

- `LocalRuntimeEndpoint`;
- safe default `LocalRuntimeBounds`;
- `OllamaAdapterRuntimeBridge`;
- `OwnerLocalRuntimeReadinessService`; and
- `OwnerLocalRuntimeHealthIndicator`.

Creating the application context performs no provider request. The bridge constructor only validates typed configuration and builds local transport objects.

No automatic adapter activation or lifecycle reconciliation is performed. Stage 32/33 authority and Slice 9 `ACTIVATED_VERIFIED` truth remain mandatory before an adapter can enter routing.

## Read-only readiness evidence

`OwnerLocalRuntimeReadinessService.check()` delegates only to the bridge's existing `discoverModels()` path, which uses loopback `/api/tags` discovery.

Readiness requires:

1. the configured provider is reachable;
2. every explicitly configured base model ID is visible; and
3. every explicitly configured adapter provider alias is visible.

The report records visible IDs, missing base IDs, missing adapter aliases, provider error state, observation time, and an `aetheris-runtime-readiness://ollama-local/...` evidence reference.

The evidence reference is a typed logical evidence address. It is **not** a cryptographic attestation of hardware, model bytes, or adapter bytes.

The conditional Actuator `HealthIndicator` projects the same report as UP/DOWN. Calling health/readiness can therefore probe the loopback provider, but application startup itself does not.

## Security and authority boundary

Slice 16 adds no authority to:

- activate or detach adapters;
- mint or reconcile lifecycle approval;
- generate model output as part of readiness;
- install/download/delete models or artifacts;
- execute shell commands or subprocesses;
- mutate files;
- call non-loopback model providers;
- access credentials; or
- invoke tools, connectors, browser, or computer-use surfaces.

The existing Slice 15 runtime remains the only model transport in this path.

## Truth boundary

A green readiness report means only that the explicitly configured loopback Ollama-compatible endpoint answered discovery and reported the explicitly configured model/alias names at that observation time.

It does **not** prove:

- that Ollama was installed by Aetheris;
- model or adapter file cryptographic identity;
- provider-native LoRA/QLoRA semantics;
- GPU presence, CUDA/driver state, VRAM/RAM capacity, or thermal state;
- TTFT, tokens/sec, latency, throughput, semantic quality, or failure rate; or
- long-term runtime availability.

Actual owner-hardware and performance certification remains a separate later gate that must be measured on the real target machine.

## Acceptance evidence

`Phase13OwnerRuntimeReadinessWiringTest` proves that:

1. owner-runtime wiring is disabled by default and performs no discovery;
2. enabled wiring creates the concrete Slice 15 bridge without provider calls during context startup;
3. health/readiness performs read-only discovery and never generation;
4. exact visible base model + adapter alias produces UP readiness;
5. a missing adapter alias fails readiness closed; and
6. a non-loopback configured endpoint is rejected before any provider call.

The Slice 16 workflow also reruns the Slice 15 concrete runtime proof and selected Slice 11–14 continuity proofs.

## Next dependency

After exact-head and canonical certification, the next Phase 13 slice should add an **owner-machine validation/export command or deployment profile that can capture this readiness evidence on the actual target PC without fabricating performance numbers**. Performance benchmarking remains blocked until that real machine is available and explicitly measured.
