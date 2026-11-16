# I7 — JWT HMAC secret policy on the B62-F integration line (12 November 2026)

**State:** coded on `integration/i7-secret-policy-20261112`. Local Python suite passes; Java runs in CI only. Not merged, not deployed.

## Starting point (B-line)
- The HMAC secret is already required: Compose uses `${AETHERIS_JWT_SECRET:?…}` and the yml has an empty default.
- At least 32 bytes are enforced in all four HMAC modules: gateway and user/audit verifiers, and the identity signer.

## Added (from closed #423, extended to four modules)
`JwtSecretPolicy.requireStrong(secret)` runs inside each HMAC branch. It refuses:
- a blank secret
- a secret shorter than 32 characters
- any value whose SHA-256 matches a secret ever published in this repository: the former `change-me…` default and the former gateway unit-test fixture, also when padded with whitespace
- a secret with fewer than 10 distinct characters

The policy class is duplicated per module, because the modules share no library. `tests/test_jwt_secret_no_default.py` fails if the copies drift.

## Test fixtures
- The gateway unit-test secret is now random per run; the old fixed fixture is one of the published values.
- The probe Compose stacks under `tests/security/` keep their synthetic secret, which passes the policy.

## Not claimed
- Rotation of any secret already deployed somewhere.
- RS256 being the default. It is still opt-in, and HMAC remains the default.
