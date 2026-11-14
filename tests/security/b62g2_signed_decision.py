#!/usr/bin/env python3
"""B62-G2 TEST-ONLY: signed JWT decision plus a read-only PostgreSQL check.

NOT imported by production Gateway, Identity, User or Audit. These synthetic
HS256 keys and fixed fixture values must never be used for real credentials.
The actual Java verifiers and tenant/resource authorization are NOT integrated.
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import json
import re
import subprocess
from typing import Callable

ALLOW, DENY = "ALLOW", "DENY"
ISSUER, AUDIENCE = "b62g-identity", "aetheris-api"
# Deliberately visible fixed, nonproduction fixture keys, never host secrets.
TEST_SIGNING_KEYS = {
    "key-current": b"b62g2-ci-only-current-signer-not-real-credentials",
    "key-compromised": b"b62g2-ci-only-compromised-signer-not-real-key",
}
COMPONENT_NAMES = ("gateway", "user", "audit")
_SEGMENT = re.compile(r"[A-Za-z0-9_-]+")
MAX_JWT_CHARS = 4096


def _b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def _unb64(encoded: str) -> bytes:
    if not _SEGMENT.fullmatch(encoded):
        raise ValueError("unsafe JWT segment")
    decoded = base64.urlsafe_b64decode(encoded + "=" * ((-len(encoded)) % 4))
    if _b64(decoded) != encoded:
        raise ValueError("noncanonical JWT segment")
    return decoded


def _unique_object(pairs: list[tuple[str, object]]) -> dict:
    obj = {}
    for key, value in pairs:
        if key in obj:
            raise ValueError("duplicate signed JSON member")
        obj[key] = value
    return obj


def fixture_jwt(*, uid: int = 42, kid: str = "key-current",
                iat: int = 12001, nbf: int = 12001, exp: int = 12601,
                issuer: str = ISSUER, audience: str = AUDIENCE) -> str:
    """Test signer. Explicit allowlisted kid; no exportable real private keys."""
    if kid not in TEST_SIGNING_KEYS:
        raise ValueError("test-only kid not found")
    head = _b64(json.dumps({"typ": "JWT", "alg": "HS256", "kid": kid},
                           separators=(",", ":"), sort_keys=True).encode())
    body = _b64(json.dumps({"iss": issuer, "aud": audience, "uid": uid,
                            "iat": iat, "nbf": nbf, "exp": exp,
                            "role": "ADMIN"}, separators=(",", ":"),
                           sort_keys=True).encode())
    signing_input = f"{head}.{body}"
    tag = _b64(hmac.digest(TEST_SIGNING_KEYS[kid],
                           signing_input.encode("ascii"), hashlib.sha256))
    return f"{signing_input}.{tag}"


def verify_synthetic_signature_and_claims(jwt: str, now: int) -> tuple[int, str, int]:
    if not isinstance(jwt, str) or len(jwt) > MAX_JWT_CHARS or not jwt.isascii():
        raise ValueError("unsafe synthetic JWT")
    pieces = jwt.split(".")
    if len(pieces) != 3:
        raise ValueError("not a JWT")
    header = json.loads(_unb64(pieces[0]), object_pairs_hook=_unique_object)
    body = json.loads(_unb64(pieces[1]), object_pairs_hook=_unique_object)
    if not isinstance(header, dict) or not isinstance(body, dict):
        raise ValueError("invalid JSON shapes")
    if set(header) != {"typ", "alg", "kid"} or header["alg"] != "HS256" or header["typ"] != "JWT":
        raise ValueError("unexpected signing algorithm/header")
    kid = header["kid"]
    if not isinstance(kid, str) or kid not in TEST_SIGNING_KEYS:
        raise ValueError("unknown signing key")
    signed_input = f"{pieces[0]}.{pieces[1]}".encode("ascii")
    expected = hmac.digest(TEST_SIGNING_KEYS[kid], signed_input, hashlib.sha256)
    if not hmac.compare_digest(expected, _unb64(pieces[2])):
        raise ValueError("invalid signature")
    if body.get("iss") != ISSUER or type(body.get("aud")) is not str or body["aud"] != AUDIENCE:
        raise ValueError("unexpected signed issuer/audience")
    if body.get("role") != "ADMIN":
        raise ValueError("unexpected signed test role")
    uid, iat, nbf, exp = (body.get(x) for x in ("uid", "iat", "nbf", "exp"))
    if any(type(x) is not int for x in (uid, iat, nbf, exp, now)):
        raise ValueError("JWT integers must be exact")
    if not (0 < uid < 2**63 and 0 < iat <= nbf <= exp and 0 < now):
        raise ValueError("invalid signed time/uid")
    if exp - iat > 3600 or iat > now + 30 or nbf > now or exp <= now:
        raise ValueError("expired, premature or overlong JWT")
    return uid, kid, iat


def decide(jwt: str, *, now: int, reader: Callable[[int, str, int], bool]) -> str:
    """Per-request, no cache; any failed signature, lookup or timeout DENIES."""
    try:
        uid, kid, iat = verify_synthetic_signature_and_claims(jwt, now)
        response = reader(uid, kid, iat)
        # Require an actual bool; not a truthy string, error, stale placeholder.
        return ALLOW if response is True else DENY
    except (Exception, BaseException) as exc:
        if isinstance(exc, (KeyboardInterrupt, SystemExit)):
            raise
        return DENY


class ReadonlyPsql:
    """CI adapter queries *only* the isolated synthetic database via Docker.

    Real production services must use independently reviewed authenticated,
    consistent, time-bounded DB connectivity and service identity, not this CLI.
    """
    def __init__(self, compose_file: str, timeout_seconds: float = 5.0):
        if compose_file != "tests/security/docker-compose.b62g-epoch-proof.yml":
            raise ValueError("only the test-only topology is allowed")
        if not 0.1 <= timeout_seconds <= 5.0:
            raise ValueError("bounded test lookup only")
        self.compose_file = compose_file
        self.timeout_seconds = timeout_seconds

    def __call__(self, uid: int, kid: str, iat: int) -> bool:
        if type(uid) is not int or not (0 < uid < 2**63) or kid not in TEST_SIGNING_KEYS:
            return False
        if type(iat) is not int or not (0 < iat < 2**63):
            return False
        # These interpolated fragments have already been strictly constrained
        # to canonical decimal integers and two hardcoded alphabetic kid strings.
        # No untrusted URL, SQL name or shell value is accepted.
        query = ("SELECT b62g_proof.synthetic_post_signature_allowed("
                 f"{uid}, '{ISSUER}', '{kid}', {iat});")
        args = ["docker", "compose", "-f", self.compose_file,
                "exec", "-T", "postgres", "psql", "-X", "-qAt",
                "-v", "ON_ERROR_STOP=1", "-U", "b62g_synthetic",
                "-d", "b62g_synthetic",
                "-c", "BEGIN READ ONLY;",
                "-c", "SET LOCAL ROLE b62g_verifier;",
                "-c", query, "-c", "COMMIT;"]
        try:
            result = subprocess.run(args, capture_output=True, text=True,
                                    check=False, timeout=self.timeout_seconds)
        except (subprocess.TimeoutExpired, OSError):
            return False
        return result.returncode == 0 and result.stdout.strip() == "t"
