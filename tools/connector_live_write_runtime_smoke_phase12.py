#!/usr/bin/env python3
"""Run the Phase 7 live-write proof under the Phase 12 account-continuity invariant.

Phase 7 originally used human-readable fixture labels as external account references.
Phase 12 deliberately requires those references to be the provider's stable account ID,
so this adapter keeps the original Phase 7 assertions unchanged while registering the
synthetic accounts with the stable identities returned by oauth_provider_stub.py.
"""
from __future__ import annotations

import connector_live_write_runtime_smoke as phase7

_STABLE_ACCOUNT_REFS = {
    "GITHUB": "424242",
    "GMAIL": "google-phase3-proof",
    "CALENDAR": "google-phase3-proof",
}

_original_register = phase7.register


def register_with_stable_account(
    provider: str,
    _legacy_external_ref: str,
    capabilities: list[str],
):
    try:
        external_ref = _STABLE_ACCOUNT_REFS[provider]
    except KeyError as error:
        raise AssertionError(f"unsupported Phase 7 proof provider: {provider}") from error
    return _original_register(provider, external_ref, capabilities)


def main() -> int:
    phase7.register = register_with_stable_account
    return phase7.main()


if __name__ == "__main__":
    raise SystemExit(main())
