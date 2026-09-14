# Stage 23 Owner Policy

Stage 23 freezes repository-visible contracts; it does not transfer owner authority to CI or to an agent.

## Owner authority

- The owner remains the authority for any intentional contract-baseline change.
- A contract-hash update must be explicit in source control and reviewed together with the contract diff that caused it.
- A green Stage 23 job is compatibility evidence, not permission to merge PR #7.
- Stage 22 merge blockers and GitHub branch-protection requirements remain independent gates.

## Non-negotiable boundaries

- Stage 21 remains `BLOCKED_PENDING_HARDWARE` until genuine physical evidence exists.
- Repository/CI evidence cannot mark a physical pilot complete.
- No unrestricted shell or Windows admin/UAC/security bypass.
- No credential export or signing-private-key persistence.
- Live-money orders remain disabled.
- Withdrawals and transfers remain outside AI authority.
- Testnet execution must continue to default to false.
- No Stage 23 action may force-update `main`, auto-merge PR #7, activate a target or mutate a physical workstation.

## Compatibility changes

An intentional endpoint, persistence-table, agent-ID, required-workflow-job or evidence-console change may legitimately alter the contract SHA. The correct procedure is:

1. review the generated contract diff;
2. confirm safety boundaries still pass;
3. deliberately update `scripts/stage23/expected-contract.sha256`;
4. require a fresh green Stage 23 CI run;
5. keep hardware/merge decisions separate.
