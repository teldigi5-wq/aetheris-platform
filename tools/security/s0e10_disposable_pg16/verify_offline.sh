#!/usr/bin/env bash
# Safe offline check ONLY. No Docker/database commands invoked.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
[[ $# == 1 ]] || { echo 'S0-E.8: pinned Platform Git snapshot required' >&2; exit 3; }
sha256sum -c MANIFEST.sha256 >/dev/null
printf 'S0-E.8 PACKAGE_SHA256_PASS\n'
bash -n scripts/run_disposable_pg16_logins.sh scripts/host_discovery.sh verify_offline.sh
python3 -m unittest discover -s tests -p 'test_*.py' -v
set +e
report="$(python3 scripts/source_gate.py "$1")"
status=$?
set -e
if [[ "$status" != 2 || "$report" != *'"gate":"BLOCKED_SHARED_BOOTSTRAP_ROLE"'* ]]; then
  echo 'S0-E.8 PINNED_SOURCE_UNVERIFIED — NO_GO' >&2
  exit 3
fi
echo 'S0-E.8 PINNED_CONFIG_BLOCKER_CONFIRMED'
echo 'S0-E.8 OFFLINE LOGIN HARNESS VERIFIED — POSTGRESQL NOT EXECUTED; RELEASE NO_GO'
