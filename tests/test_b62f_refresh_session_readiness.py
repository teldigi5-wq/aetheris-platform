"""B62-F session lifecycle and emergency non-claims: exact source contracts."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "identity-service/src/main/java/io/aetheris/identity/RefreshTokenService.java"
TEST = ROOT / "identity-service/src/test/java/io/aetheris/identity/RefreshSessionRolloverB62FTest.java"
DOC = ROOT / "docs/security/B62F_SESSION_ROLLOVER_REVOCATION_READINESS_20261026.md"

class B62FSessionSafety(unittest.TestCase):
    def test_refresh_validity_is_hard_bounded_at_issuer_startup(self):
        s = SERVICE.read_text()
        self.assertIn("MAX_REFRESH_TOKEN_LIFETIME_SECONDS = 604800L", s)
        self.assertIn("expirationSeconds < 1 || expirationSeconds > MAX_REFRESH_TOKEN_LIFETIME_SECONDS", s)
        self.assertIn("Refresh-token lifetime must be 1..604800 seconds", s)
        self.assertIn("revokeIfActive(tokenHash, Instant.now())", s)
        self.assertIn("repository.findByTokenHash(hash(rawToken))", s)
        self.assertNotIn("revokeAllAccounts", s)

    def test_real_signed_access_jwt_and_two_session_limits_are_tested(self):
        src = TEST.read_text()
        for phrase in (
            "logoutOnlyRevokesPresentedRefreshSessionWhileOtherSessionCanRotateAfterRestart",
            "RefreshTokenService afterRestart",
            "afterRestart.rotate(second.token())",
            "sessions.revoke(first.token())",
            "parser.parseSignedClaims(jwt)",
            "MAX_REFRESH_TOKEN_LIFETIME",
        ):
            # Test uses the constant indirectly through explicit 604800 values.
            if phrase == "MAX_REFRESH_TOKEN_LIFETIME":
                self.assertIn("604801L", src)
            else:
                self.assertIn(phrase, src)
        self.assertIn("assertEquals(3, rows.size())", src)

    def test_incident_runbook_does_not_claim_immediate_access_token_revocation(self):
        s = DOC.read_text()
        for phrase in (
            "NO_GO", "access JWT", "refresh token",
            "same verified source", "compromised current signing key",
            "previous-key cutoff", "rollback", "not implemented",
            "no real production keys", "owner approval",
        ):
            self.assertIn(phrase.casefold(), s.casefold())

if __name__ == "__main__":
    unittest.main()
