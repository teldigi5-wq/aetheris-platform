## Summary

<!-- What problem does this change solve? Keep the claim proportional to the evidence. -->

## Change classification

- [ ] Documentation / presentation only
- [ ] Runtime behavior
- [ ] Security / identity / authorization
- [ ] Governance / approvals / policy
- [ ] Automation / workstation / privileged action
- [ ] Trading / financial logic
- [ ] Deployment / infrastructure / observability
- [ ] Dependency / build / CI

## Engineering context

<!-- Why this approach? Mention important architecture decisions, alternatives, and trade-offs. -->

## Changes

- 

## Validation evidence

<!-- List exact commands, tests, CI checks, screenshots, logs, metrics, or reproducibility evidence. -->

- [ ] Relevant tests/checks pass
- [ ] Documentation updated when behavior or operator expectations changed
- [ ] No credentials, tokens, private keys, personal data, or sensitive logs included
- [ ] Operational/deployment impact considered
- [ ] Compatibility/migration impact considered
- [ ] Rollback or disable path identified for non-trivial changes

## Safety and truth boundaries

Check every item that applies:

- [ ] Model output does not become policy authority
- [ ] `ALLOW` is treated as eligibility, not proof of execution
- [ ] Successful execution requires verification evidence or an explicit `UNVERIFIED` outcome
- [ ] Emergency precedence remains `STOP > TAKE_CONTROL > PAUSE > NORMAL`
- [ ] `ZERO-COST` and `PRIVATE` boundaries are preserved where relevant
- [ ] Live-money execution, withdrawals, and transfers remain outside the default trusted AI path
- [ ] Hosted CI is not presented as physical-PC validation
- [ ] Physical-machine status remains `BLOCKED_PENDING_HARDWARE` unless real hardware evidence supports a deliberate status change

## Risk / rollback

<!-- What could break? What is the worst credible impact? How can this change be reverted, disabled, or contained? -->

## Reviewer focus

<!-- Point reviewers to the files, invariants, or assumptions that deserve the most attention. -->

## Follow-up

<!-- Optional future work intentionally left out of this PR. -->