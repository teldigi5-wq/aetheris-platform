# Stage 21 Owner Policy

Stage 21 is a hardware-dependent pilot and must not be declared physically complete from repository or CI evidence alone.

## Owner authority

The owner remains the only authority who may decide whether a real target proceeds beyond review eligibility. `READY_FOR_OWNER_RESTRICTED_PILOT_REVIEW` is evidence status, not permission to activate.

## Mandatory boundaries

- Simulated/CI evidence never satisfies physical readiness.
- A target-measured Stage 20 receipt must correlate to the same authorization, package and device certificate.
- Package or certificate mismatch fails closed.
- Pilot capabilities may only narrow Stage 20 authority.
- `STOP ALL`, revocation and rollback drills must be validated on the real target before any later activation stage.
- No unrestricted shell, UAC/admin/security bypass or credential export.
- No live-money trading, withdrawals or transfers.
- No private key or secret value may be persisted in Stage 21 evidence payloads.
- Evidence older than 24 hours is not accepted for readiness.
- Repository code exposes no production activation transition.

## Evidence honesty

A CI test may construct synthetic rows marked `measuredOnTarget=true` in order to validate correlation logic. Those rows are test fixtures only. They are not proof that the owner's workstation exists, is installed, is connected or has passed a physical drill.

Until the owner's Windows PC is available, the operational Stage 21 status is **`BLOCKED_PENDING_HARDWARE`**.
