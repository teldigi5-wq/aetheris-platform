"""B62-G2 real PostgreSQL proof, while simulated verifiers remain test-only.

Run only after installing b62g1 schema and b62g2 least-privilege role in a
disposable DB. No actual Gateway/User/Audit authorization is modified.
"""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from b62g2_signed_decision import (
    ReadonlyPsql, decide, fixture_jwt, ALLOW, DENY, COMPONENT_NAMES,
)

reader = ReadonlyPsql("tests/security/docker-compose.b62g-epoch-proof.yml")
NOW = 12200
CASES = [
    ("current-after-epoch", fixture_jwt(uid=42, iat=12001), ALLOW),
    ("current-at-cutoff", fixture_jwt(uid=42, iat=12000, nbf=12000, exp=12600), DENY),
    ("current-before-cutoff", fixture_jwt(uid=42, iat=11999, nbf=11999, exp=12599), DENY),
    ("unrelated-user", fixture_jwt(uid=43, iat=11999, nbf=11999, exp=12599), ALLOW),
    ("blocked-signer", fixture_jwt(uid=43, kid="key-compromised", iat=12001), DENY),
    ("wrong-audience", fixture_jwt(uid=42, audience="wrong", iat=12001), DENY),
    ("invalid-issuer", fixture_jwt(uid=42, issuer="other", iat=12001), DENY),
    ("premature", fixture_jwt(uid=42, iat=12001, nbf=13000, exp=13600), DENY),
    ("expired", fixture_jwt(uid=42, iat=11500, nbf=11500, exp=12199), DENY),
    ("overlong", fixture_jwt(uid=42, iat=12001, exp=15602), DENY),
    ("missing-uid", fixture_jwt(uid=0), DENY),
]

def check(stage: str, live_db: bool) -> int:
    count = 0
    for name in COMPONENT_NAMES:
        for description, jwt, expected in CASES:
            observed = decide(jwt, now=NOW, reader=reader)
            if not live_db:
                expected = DENY  # no cached allows across unavailable store
            if observed != expected:
                raise AssertionError(
                    f"B62G2_{stage}_{name}_{description}_expected_{expected}_observed_{observed}"
                )
            count += 1
    print(f"B62G2_{stage.upper()}_SIGNED_AND_REVOCATION_DECISIONS_PASS count={count}",
          flush=True)
    return count

if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in ("online", "restarted", "outage"):
        raise SystemExit("usage: live test [online|restarted|outage]")
    label = sys.argv[1]
    check(label, label != "outage")
