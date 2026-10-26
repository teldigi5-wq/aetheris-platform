"""B62-G2 offline: strict test JWT verification precedes lookup; fail closed."""
import sys
import unittest
import subprocess
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "security"))
from b62g2_signed_decision import (
    ALLOW, DENY, ReadonlyPsql, decide, fixture_jwt,
    verify_synthetic_signature_and_claims,
)


class AdapterPolicy(unittest.TestCase):
    NOW = 12200

    def test_valid_signed_fixture_calls_consumer_only_after_verify(self):
        called = []
        def reader(uid, kid, iat):
            called.append((uid, kid, iat))
            return True
        self.assertEqual(decide(fixture_jwt(), now=self.NOW, reader=reader), ALLOW)
        self.assertEqual(called, [(42, "key-current", 12001)])

    def test_signature_and_claim_rejection_do_not_touch_database(self):
        def never(*args):
            raise AssertionError("DB must not be consulted")
        good = fixture_jwt()
        invalid = [
            good[:-2] + "aa",
            fixture_jwt(issuer="wrong"),
            fixture_jwt(audience="another-service"),
            fixture_jwt(audience=["aetheris-api", "another-service"]),
            fixture_jwt(iat=15000, nbf=15000, exp=15600),
            fixture_jwt(iat=12001, nbf=12300, exp=12601),
            fixture_jwt(iat=10000, nbf=10000, exp=12000),
            fixture_jwt(iat=12001, nbf=12001, exp=15602),
            fixture_jwt(uid=0),
            fixture_jwt(uid=True),
            good.replace("a", "@", 1),
            "x.y.z",
        ]
        for case in invalid:
            with self.subTest(token_index=invalid.index(case)):
                self.assertEqual(decide(case, now=self.NOW, reader=never), DENY)

    def test_explicit_false_malformed_result_timeout_and_error_fail_closed(self):
        token = fixture_jwt()
        for result in [False, None, "t", 1, 0, {}, []]:
            with self.subTest(result=result):
                self.assertEqual(decide(token, now=self.NOW, reader=lambda *_: result), DENY)
        for exc in [OSError("offline"), TimeoutError("deadline"),
                    subprocess.TimeoutExpired(["psql"], 0.1), RuntimeError("bad row")]:
            with self.subTest(exc=type(exc).__name__):
                def raises(*args):
                    raise exc
                self.assertEqual(decide(token, now=self.NOW, reader=raises), DENY)

    def test_bounded_pinned_sql_client_rejects_nonfixture_topologies(self):
        with self.assertRaises(ValueError):
            ReadonlyPsql("docker-compose.yml")
        with self.assertRaises(ValueError):
            ReadonlyPsql("tests/security/docker-compose.b62g-epoch-proof.yml", 30)
        self.assertEqual(verify_synthetic_signature_and_claims(fixture_jwt(), self.NOW),
                         (42, "key-current", 12001))


if __name__ == "__main__":
    unittest.main()
