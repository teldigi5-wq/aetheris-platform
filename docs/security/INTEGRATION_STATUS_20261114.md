# Integration status — 14 November 2026

These are separate statuses; none implies another. Status words: planned / coded / local test / exact-head CI / merged / deployed / accepted.

| Item | Status | Evidence |
|---|---|---|
| Syntra Desktop v0.4 (diagnostics and recovery) | **merged** | #206, merge `57ad3e0`; implementation `908ac0a`; unsigned development installer |
| Desktop hardening (I12) | **coded**, exact-head CI pending on this branch | no probe redirects; literal `127.0.0.1` only (Rust, JS, CSP); symlink-safe atomic diagnostics write; committed `Cargo.lock` required |
| XERJ candidate `teldigi5-wq--aetheris-platform` | **consented** (receipt `60b7605bbda3`); listed at `908ac0a` with no path scope | Re-pin to the merged hardened commit, scoped to `apps/syntra-desktop`, only after the owner approves |
| Platform security (I0–I12) | **integration-only** | exact-head CI per draft PRs #430–#438; not merged; independent review (G9) pending |
| Runtime security (S0-E6T stack) | **unmerged** | aetheris-ai-runtime #306, exact-head CI 36/36 at `d9c5f80` |
| Runtime certification | `65a6262` certified; runtime `main` `5e3b495` not released | release publishing disabled pending owner authorization |
| Physical owner PC | **BLOCKED_PENDING_HARDWARE** | unchanged |
| Production release | **NO_GO** (Stage 45) | unchanged |
