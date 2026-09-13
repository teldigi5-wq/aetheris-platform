# Stage 29 — Trading Intelligence & Execution

Date: 2026-09-14

## Objective

Stage 29 adds a deterministic, policy-gated trading intelligence layer to Aetheris. The repository milestone covers signal scoring, risk sizing, TP/SL planning, leverage ceilings, execution-mode gating, and auditable paper/testnet decision output.

This stage **does not enable unrestricted live-money execution**.

## Modes

Stage 29 recognizes three explicit execution modes:

- `PAPER` — simulated fills only; default and CI-safe.
- `TESTNET` — exchange test environment only, and only when separately enabled by policy.
- `LIVE` — hard-blocked in Stage 29.

CI must reject any configuration that attempts to enable live trading.

## Decision model

Each candidate setup is evaluated using deterministic inputs:

- symbol;
- direction (`LONG` or `SHORT`);
- entry price;
- stop-loss price;
- take-profit price;
- setup score;
- account balance;
- configured risk per trade;
- configured maximum leverage;
- current open-position count;
- current daily drawdown.

The planner derives:

- stop distance percentage;
- reward/risk ratio;
- risk capital;
- maximum notional position;
- capped leverage;
- approval status;
- explicit rejection reasons when policy is not satisfied.

## Safety boundaries

Stage 29 is intentionally conservative.

Hard boundaries:

- `LIVE` execution is rejected;
- leverage above policy maximum is rejected/capped;
- risk per trade above policy maximum is rejected;
- setups below minimum score are rejected;
- excessive daily loss blocks new trades;
- excessive open positions block new trades;
- invalid TP/SL geometry is rejected;
- no API secrets are required by CI;
- no network requests are required by CI;
- no order is submitted from certification tests.

## Human approval boundary

A qualifying signal is a **proposal**, not permission to spend real money.

The output may state proposed entry, stop-loss, take-profit, leverage, risk capital and notional size, but Stage 29 does not turn those values into a live exchange order.

Future live execution, if ever enabled, must require an explicit owner-controlled production policy and a separate reviewed stage.

## Evidence

Stage 29 includes synthetic fixtures for:

- approved high-quality paper setup;
- low-score rejection;
- invalid stop geometry;
- daily-loss lockout;
- open-position lockout;
- live-mode rejection.

The certification workflow runs the planner twice and byte-compares the outputs to prove deterministic behavior.

## Completion criteria

Repository-side Stage 29 is complete when:

1. the trading policy validates fail-closed;
2. paper-mode decision planning is deterministic;
3. TP/SL geometry is validated;
4. risk sizing and leverage caps are enforced;
5. daily-loss and open-position lockouts are enforced;
6. live execution is blocked;
7. CI requires no secrets or network access;
8. Stage 29 certification is green.
