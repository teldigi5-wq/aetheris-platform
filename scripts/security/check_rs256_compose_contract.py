#!/usr/bin/env python3
"""Review-only auth-mode preflight. Prints no key material; never deploys."""
import argparse
import os
import re
import sys

KID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
TARGETS = (
    "AETHERIS_IDENTITY_JWT_ISSUER",
    "AETHERIS_IDENTITY_JWT_AUDIENCE",
    "AETHERIS_USER_JWT_AUDIENCE",
    "AETHERIS_AUDIT_JWT_AUDIENCE",
    "AETHERIS_ORCHESTRATOR_JWT_AUDIENCE",
    "AETHERIS_BACKEND_JWT_ISSUER",
)
PUBLIC = "AETHERIS_JWT_RS256_PUBLIC_KEY_PEM"
PRIVATE = "AETHERIS_IDENTITY_JWT_RS256_PRIVATE_KEY_PEM"

def validate(mode: str, env: dict[str, str]) -> list[str]:
    errors: list[str] = []
    if mode not in ("legacy", "rs256"):
        return ["Mode must be 'legacy' or 'rs256'"]
    if mode == "legacy":
        if len(env.get("AETHERIS_JWT_SECRET", "")) < 32:
            errors.append("Legacy mode requires a JWT HMAC secret of at least 32 characters")
        if any(env.get(k, "") for k in (PRIVATE, PUBLIC,
                "AETHERIS_JWT_RS256_STAGED_PUBLIC_KEY_PEM")):
            errors.append("Legacy mode rejects mixed asymmetric key configuration")
        return errors

    if env.get("AETHERIS_JWT_SECRET", ""):
        errors.append("RSA-only mode refuses distribution of a legacy shared JWT secret")
    for key in TARGETS:
        value = env.get(key, "")
        if not value or value != value.strip() or len(value) > 255:
            errors.append(f"RSA-only mode requires valid {key}")
    if env.get("AETHERIS_BACKEND_JWT_ISSUER", "") != env.get("AETHERIS_IDENTITY_JWT_ISSUER", ""):
        errors.append("Backend and Identity issuers must match")
    for key in (PRIVATE, PUBLIC):
        pem = env.get(key, "")
        if not pem or "-----BEGIN " not in pem or "-----END " not in pem:
            errors.append(f"RSA-only mode requires {key} in PEM format")
    for key in ("AETHERIS_IDENTITY_JWT_RS256_KEY_ID", "AETHERIS_JWT_RS256_KEY_ID"):
        if not KID.fullmatch(env.get(key, "")):
            errors.append(f"RSA-only mode requires a valid {key}")
    if env.get("AETHERIS_IDENTITY_JWT_RS256_KEY_ID") != env.get("AETHERIS_JWT_RS256_KEY_ID"):
        errors.append("Signing and verification active key IDs must match")
    staged = env.get("AETHERIS_JWT_RS256_STAGED_PUBLIC_KEY_PEM", "")
    stage_id = env.get("AETHERIS_JWT_RS256_STAGED_KEY_ID", "")
    if bool(staged) != bool(stage_id):
        errors.append("Staged public key and key ID must be configured together")
    if staged and (not KID.fullmatch(stage_id) or stage_id == env.get("AETHERIS_JWT_RS256_KEY_ID", "")):
        errors.append("Staged key ID must be valid and distinct")
    if len({env.get("AETHERIS_USER_JWT_AUDIENCE"),
            env.get("AETHERIS_AUDIT_JWT_AUDIENCE"),
            env.get("AETHERIS_ORCHESTRATOR_JWT_AUDIENCE")}) != 3:
        errors.append("User, Audit and Orchestrator audiences must be distinct")
    if env.get("AETHERIS_IDENTITY_JWT_AUDIENCE") not in (
        env.get("AETHERIS_USER_JWT_AUDIENCE"),
        env.get("AETHERIS_AUDIT_JWT_AUDIENCE"),
        env.get("AETHERIS_ORCHESTRATOR_JWT_AUDIENCE"),
    ):
        errors.append("Identity's default audience must match exactly one recipient")
    return errors

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", required=True, choices=("legacy", "rs256"))
    args = parser.parse_args()
    problems = validate(args.mode, dict(os.environ))
    for problem in problems:
        print("PRECHECK_REJECTED: " + problem, file=sys.stderr)
    if problems:
        sys.exit(1)
    print("AETHERIS_JWT_COMPOSE_CONFIGURATION_CONTRACT_PASS; NOT DEPLOYMENT APPROVAL")
