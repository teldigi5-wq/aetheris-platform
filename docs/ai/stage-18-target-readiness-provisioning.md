# Stage 18 — Target Readiness & Provisioning Orchestrator

Stage 18 prepares Syntra + Aetheris for a future real Windows workstation without pretending repository/CI evidence is physical deployment evidence. It extends the Stage 17 adapter-certification chain with a signed target bootstrap manifest, exact target evidence correlation, measured local benchmarks, provider/vault readiness and a consolidated readiness bundle.

## Objectives

1. Describe a target workstation with a signed, machine-readable bootstrap manifest.
2. Bind the target to one Stage 16/17 adapter identity without activating that adapter.
3. Require exact host-agent package SHA-256 and exact device-certificate fingerprint evidence.
4. Require target-measured DPAPI/OS-vault evidence before activation review.
5. Require target-measured RAM/VRAM, speech/VAD and local-model benchmark evidence.
6. Require private-transport evidence tied to the same device certificate.
7. Require each declared provider alias to have target-measured vault-only binding evidence.
8. Aggregate all evidence into a deterministic readiness score and SHA-256 evidence bundle.
9. Stop at owner activation review; Stage 18 never activates a real adapter automatically.

## Bootstrap manifest

`Stage18BootstrapManifestEntity` stores only non-secret deployment intent:

- target ID;
- Stage 16 adapter ID;
- platform (`WINDOWS` only in Stage 18);
- expected host-agent package SHA-256;
- expected device-certificate SHA-256 fingerprint;
- minimum RAM and VRAM;
- bounded target capabilities;
- provider credential aliases (names only, never values);
- trusted signer key ID;
- signature SHA-256;
- canonical manifest SHA-256;
- issuance timestamp.

The bootstrap manifest is accepted only when:

- its Stage 16 adapter exists, is enabled and is still simulation-only;
- the manifest is no more than ten minutes old and is not materially future-dated;
- target capabilities are inside the Stage 18 target-capability allowlist;
- forbidden authorities such as arbitrary shell, admin bypass, live orders, withdrawals, transfers, security disable, UAC bypass and credential export are absent;
- the supplied manifest SHA-256 exactly equals the canonical manifest content hash;
- a trusted Stage 16 Ed25519 public signer verifies the manifest signature.

The repository stores no private signing key.

## Target capability allowlist

Stage 18 currently permits only:

- `PROCESS_READ`
- `APP_LAUNCH`
- `FILE_OPEN`
- `PC_TELEMETRY`
- `OLLAMA`
- `DPAPI`
- `VAD`
- `STT`
- `TTS`
- `PRIVATE_TRANSPORT`

This is a provisioning/readiness declaration, not an execution permission grant.

## Evidence model

`Stage18TargetEvidenceEntity` records evidence for one target and component. Evidence includes:

- evidence kind;
- component/provider alias;
- pass/fail state;
- whether it was measured on the target;
- optional exact subject SHA-256;
- attestation SHA-256;
- non-secret source identifier;
- bounded, secret-filtered detail;
- observation time.

Repository/CI observations are stored as `PASS_SIMULATED` rather than `PASS`, so CI cannot self-promote a future workstation.

Supported evidence kinds:

- `DEVICE_IDENTITY`
- `OS_VAULT`
- `PACKAGE_INTEGRITY`
- `RESOURCE_BENCHMARK`
- `SPEECH_BENCHMARK`
- `LOCAL_MODEL_BENCHMARK`
- `PRIVATE_TRANSPORT`
- `PROVIDER_BINDING`

Generic APIs cannot self-assert benchmark evidence; resource, speech and local-model evidence must go through the deterministic benchmark evaluator.

## Exact correlation

Target readiness requires:

- device-identity `subjectSha256` exactly equals the certificate fingerprint signed into the bootstrap manifest;
- package-integrity `subjectSha256` exactly equals the host-agent package SHA-256 signed into the bootstrap manifest;
- private-transport `subjectSha256` exactly equals the same certificate fingerprint;
- provider-binding evidence component exactly equals a provider alias declared by the signed manifest.

Historical or unrelated evidence cannot satisfy those gates.

## Benchmark gates

### Resource benchmark

Passes only when target evidence reports:

- RAM >= signed `minimumRamMb`;
- VRAM >= signed `minimumVramMb`;
- zero benchmark crashes.

### Speech benchmark

Current Stage 18 gate:

- STT p95 latency > 0 and <= 900 ms;
- barge-in p95 latency > 0 and <= 300 ms;
- zero benchmark crashes.

### Local-model benchmark

Current Stage 18 gate:

- p95 time-to-first-token > 0 and <= 2500 ms;
- throughput >= 8 tokens/second;
- zero benchmark crashes.

These thresholds can be revised in a later target-hardware tuning stage using measured evidence; Stage 18 does not fabricate hardware numbers.

## Readiness score

The readiness score is deterministic and totals 100 points:

| Gate | Points |
| --- | ---: |
| Signed Stage 18 bootstrap manifest | 10 |
| Current Stage 17 `CERTIFIED_SIMULATION_ONLY` adapter certification | 10 |
| Exact target device identity | 10 |
| Exact host-agent package integrity | 10 |
| DPAPI/OS-vault target evidence | 10 |
| RAM/VRAM resource benchmark | 10 |
| Speech/VAD benchmark | 10 |
| Local-model benchmark | 10 |
| Private transport bound to device certificate | 10 |
| All required provider aliases target-bound through vault evidence | 10 |

Possible readiness states:

- `STAGE17_CERTIFICATION_REQUIRED`
- `TARGET_EVIDENCE_REQUIRED`
- `READY_FOR_OWNER_ACTIVATION_REVIEW`

Even a score of 100 leaves `productionActivationAllowed=false`.

## Evidence bundle

`bundle(targetId)` produces a deterministic SHA-256 over:

- target ID;
- signed manifest SHA-256;
- readiness score;
- ordered evidence kind/component/status/attestation references.

This bundle is designed to become an input to a later owner-approved target activation stage. It is not itself an activation token.

## Provisioning rehearsal

`rehearse(targetId)` lists the future activation sequence without performing any side effect:

1. verify signed bootstrap manifest;
2. verify Stage 17 simulation certification;
3. verify exact device-certificate fingerprint;
4. verify host-agent package integrity;
5. verify DPAPI/OS-vault binding;
6. verify resource, model and speech benchmarks;
7. verify private transport;
8. verify provider aliases via vault metadata/evidence;
9. assemble target evidence bundle;
10. stop for explicit owner activation review.

The rehearsal always reports:

- `targetMutated=false`
- `productionActivationAllowed=false`
- `externalActionAttempted=false`

## API surface

Base path: `/api/orchestrator/stage18`

- `POST /manifests`
- `GET /manifests`
- `GET /manifests/{targetId}/latest`
- `POST /evidence`
- `POST /benchmarks`
- `GET /evidence`
- `GET /evidence/{targetId}`
- `GET /readiness/{targetId}`
- `GET /bundle/{targetId}`
- `POST /rehearse/{targetId}`
- `GET /overview`

The Stage 18 console is intentionally read-only even though governed APIs exist for future target agents.

## Evidence honesty

Repository/CI completion does not mean:

- the owner has the target PC;
- Windows host-agent installation has happened;
- DPAPI works in the owner's Windows profile;
- GPU/RAM values have been measured on the future machine;
- microphone/speaker/VAD/STT/TTS latency has been measured on that machine;
- Ollama/local-model performance has been measured there;
- a private tunnel is active;
- provider credentials are present;
- a physical Stage 16/17 adapter is active.

CI evidence is useful for schema, policy, signature, correlation and state-machine validation only.

## Validation

`Stage18IntegrationTest` verifies:

- valid Ed25519 bootstrap signatures are accepted;
- tampered signatures are rejected;
- forbidden target authority is rejected;
- CI evidence and CI benchmark results cannot self-promote target readiness;
- exact package/certificate correlation remains required;
- evidence bundles are SHA-256-addressed;
- secret-like evidence detail is rejected.

## Future target-PC activation

A later stage may consume a 100/100 Stage 18 evidence bundle to create an explicit owner activation proposal. That future stage must independently re-check freshness, package integrity, device identity, adapter certification, private transport and owner approval before any physical adapter is activated.
