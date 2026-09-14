# Stage 10 — Owner Capability Policy

This policy defines the Stage 10 authority boundaries for Syntra + Aetheris. Passing a software test, benchmark or readiness probe never silently grants a broader capability.

## Policy classes

- `AUTO_SAFE` — local/low-risk/reversible behavior may run when all deterministic constraints pass.
- `OBSERVE` — inspect, measure, analyze or simulate only.
- `APPROVAL` — prepare/validate first; consequential execution requires explicit owner approval plus a legitimate connected adapter/capability.
- `HARDWARE_PENDING` — software contract exists, but target-PC evidence is still required.
- `DISABLED` — no Stage 10 execution path may perform this capability.

## Capability matrix

| Capability | Stage 10 state | Boundary |
|---|---|---|
| Bootstrap/rollback planning | `AUTO_SAFE` | Plan/evidence only; does not install software. |
| Workstation readiness probe | `OBSERVE` | Reports eligibility; cannot claim the host is installed/validated. |
| System telemetry handler | `HARDWARE_PENDING` | Least-privilege handler is allowlisted; actual PC validation required. |
| Process-status handler | `HARDWARE_PENDING` | Named process observation only; no process creation shell. |
| App launch | `APPROVAL` + `HARDWARE_PENDING` | Configured alias only; no arbitrary CLI arguments. |
| Workspace file open | `APPROVAL` + `HARDWARE_PENDING` | Must be under configured owner root. |
| Unrestricted shell / PowerShell / cmd execution | `DISABLED` | Not a default Stage 10 agent primitive. |
| UAC/admin/security bypass | `DISABLED` | Never bypass OS security. |
| Resource governor | `AUTO_SAFE` | May defer/reject heavy work to protect interaction quality. |
| Priority STOP/PAUSE/TAKE_CONTROL | existing deterministic authority | Must outrank normal model/agent scheduling. |
| Speech benchmark evaluation | `OBSERVE` | Only target-hardware evidence may become `VERIFIED`. |
| Real microphone/VAD/STT/TTS | `HARDWARE_PENDING` | Requires target-PC integration and measured benchmarks. |
| OS-vault readiness | `OBSERVE` | Environment/test vaults do not count as DPAPI proof. |
| Windows Credential Manager/DPAPI | `HARDWARE_PENDING` | Real OS-backed adapter and target-PC validation required. |
| Owner-root watcher registration | `APPROVAL` | Owner chooses root/extension scope. |
| Hash-based watcher events | `AUTO_SAFE` | Event must stay inside approved root; no arbitrary disk crawl. |
| Remote transport validation | `OBSERVE` | Passing policy means ready for deployment test, not deployed. |
| Remote session activation | `APPROVAL` + deployment evidence | TLS1.3, mutual auth, private tunnel, pairing/revocation and narrow scopes. |
| Raw public agent API | `DISABLED` | No unauthenticated/general public command surface. |
| Benchmark regression gate | `AUTO_SAFE` | Can block promotion; cannot self-grant new authority. |
| Runtime SLO assessment | `OBSERVE` | Synthetic evidence cannot be reported as healthy target runtime. |
| Recovery runbook | `APPROVAL` for consequential steps | STOP is immediate; rollback/revocation follows owner/security policy. |
| Signed installer/update promotion | `APPROVAL` + `HARDWARE_PENDING` | Signature/checksum/canary/regression/rollback evidence required. |
| Rich paper-order simulation | `OBSERVE` | MARKET/LIMIT/STOP/STOP_LIMIT/partial fill/cost simulation only. |
| Durable `PAPER_ONLY` position open | existing Stage 9 authority | Accepted analysis + consensus + deterministic Risk Officer. |
| Testnet credential readiness | `OBSERVE` | Alias/provider/presence only; never reveal secret values. |
| Testnet adapter connection | `APPROVAL` | Newly generated owner credentials via vault, minimum testnet permission. |
| Live-money exchange orders | `DISABLED` | No Stage 10 live-money path. |
| Withdrawals | `DISABLED` | Must remain outside AI trading capability. |
| Transfers | `DISABLED` | Must remain outside AI trading capability. |

## Workstation authority rules

Stage 10 narrows Windows actions rather than expanding them generically.

A workstation command may be issued only if:

1. the existing host registry considers the host executable/paired;
2. the host explicitly declares the capability;
3. Stage 10's least-privilege command guard accepts capability/action/arguments;
4. the signed short-lived host envelope can be created;
5. replay protection remains active; and
6. any action-class approval required by owner rules has already been obtained.

No LLM response, agent vote, benchmark score or UI button may bypass those gates.

## Application launch policy

Applications are addressed by configured aliases, not arbitrary executable paths supplied by a model.

Stage 10 rejects arbitrary launch arguments because an apparently safe application can become a shell/command escape if arbitrary CLI parameters are accepted.

A future host implementation may add narrowly typed arguments per application, but each argument family must have its own deterministic schema and test coverage.

## Workspace policy

`workspace.open` is restricted to configured owner roots.

The following are not valid substitutes for an owner root:

- an entire drive;
- the Windows directory;
- ProgramData;
- a complete user home; or
- AppData/credential/browser-profile locations.

Parent traversal is rejected.

## Resource policy

Interactive control has priority over local inference throughput.

The governor may defer/reject heavy work when:

- speech is active;
- the UI is interactive under high CPU load;
- VRAM lacks safe headroom; or
- CPU/RAM/VRAM pressure becomes critical.

`PRIORITY_CONTROL` remains locally deterministic and may not be queued behind an LLM request.

## Speech policy

A benchmark is `VERIFIED` only when marked as measured on the target workstation and all configured latency/resource targets pass.

Synthetic CI values must return `EVIDENCE_REQUIRED`.

This prevents an interface contract from being misrepresented as real low-latency voice capability.

## Credential policy

Secret values must remain inside the vault resolution boundary.

Readiness APIs may expose only:

- alias;
- provider/backend label; and
- availability/readiness state.

They must not expose secret values, partial secrets or hashes intended as secret substitutes.

Testnet credentials must be newly generated by the owner for the testnet purpose and stored through the approved vault path. Credentials seen in chat history/logs/docs are not eligible for reuse.

## Watcher policy

File/repository watchers are opt-in per root.

They may process event metadata/hash evidence only inside registered roots and allowed extensions. They do not gain permission to scan the rest of a disk, browser profile, credential store or unrelated project.

Deleted/changed source handling should continue through the Stage 9 lineage/tombstone system when the future host filesystem bridge is connected.

## Remote policy

A production remote channel must be encrypted, mutually authenticated, paired, revocable and capability-scoped.

Stage 10 requires TLS 1.3 policy evidence, a private network/tunnel, short-lived session credentials and an explicit prohibition on a raw public agent API.

A readiness result is not proof that the network transport is deployed. Deployment remains target-environment work.

## Promotion policy

Regression gates may **block** promotion automatically. They may not grant broader authority automatically.

A candidate is blocked when functional tests fail, quality falls below the established PASS threshold, regression exceeds the allowed delta, hallucination penalty violates policy or a critical safety failure exists.

Even a passing candidate must still obey owner rules, deployment approvals and capability classes.

## Recovery policy

Emergency recovery starts with deterministic `STOP ALL`.

Recovery may preserve/restore:

- owner workspaces;
- owner rules/capability policy;
- knowledge lineage;
- audit/evaluation evidence;
- paper journals/equity evidence; and
- host/device revocation state.

Recovery must not:

- delete owner work simply to make health checks green;
- erase evidence of an incident;
- weaken UAC/firewall/vault/policy controls;
- silently install a replacement package; or
- enable live-money execution.

## Trading policy

Stage 10's richer order simulator is not a second execution engine.

It can simulate trigger/fill/cost behavior, but it returns `PAPER_SIMULATION_ONLY` and never sends an exchange order.

Any durable paper position still requires the established chain:

1. trade analysis exists;
2. owner explicitly accepted the analysis;
3. market consensus passes freshness/skew/divergence rules;
4. deterministic Paper Risk Officer allows the trade; and
5. the Stage 9 simulator opens only a `PAPER_ONLY` position.

Live money, withdrawals and transfers stay disabled regardless of confidence, setup score, agent consensus or benchmark quality.

## Testnet policy

A future testnet adapter may be enabled only after:

- newly generated testnet credentials are stored in the vault;
- credentials have minimum required permissions;
- withdrawal/transfer permissions are absent;
- testnet connectivity and order behavior are separately validated;
- every testnet order is auditable; and
- owner approval enables the adapter/action class.

Testnet enablement is not live-money enablement.

## Stage 10 hardware promotion gates

Before `HARDWARE_PENDING` can become operational, require:

- target PC/device present and identified;
- real adapter/handler installed;
- dedicated integration tests on that hardware;
- measurable latency/resource evidence where relevant;
- rollback/recovery evidence;
- owner-visible status;
- full prior-stage regression suite green; and
- explicit owner approval for consequential capabilities.

## Non-negotiable disabled capabilities

Stage 10 cannot be configured to enable these through a normal setting:

- UAC/security bypass;
- credential theft/exfiltration;
- raw public unrestricted agent control;
- unrestricted shell as an autonomous primitive;
- live-money exchange orders;
- withdrawals; or
- transfers.

Changing those boundaries would require a separately designed later stage where appropriate, and some (such as security bypass/credential theft) should remain prohibited rather than becoming future features.
