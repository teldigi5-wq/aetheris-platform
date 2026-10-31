#!/usr/bin/env bash
# Prove real Gitleaks accepts harmless changes and rejects a newly generated,
# disposable private key. No project credentials are used or printed.
set -euo pipefail
umask 077

if ! command -v gitleaks >/dev/null 2>&1 || ! command -v openssl >/dev/null 2>&1; then
  echo 'ERROR: Gitleaks and OpenSSL are required for the synthetic detector proof.' >&2
  exit 2
fi

audit_dir="$(mktemp -d "${RUNNER_TEMP:-/tmp}/gitleaks-synthetic.XXXXXXXX")"
trap 'rm -rf -- "$audit_dir"' EXIT
git -C "$audit_dir" init -q
git -C "$audit_dir" config user.email synthetic-ci@example.invalid
git -C "$audit_dir" config user.name 'Synthetic Gitleaks CI'

printf 'harmless seed\n' > "$audit_dir/README"
git -C "$audit_dir" add README
git -C "$audit_dir" commit -qm 'synthetic base'
base="$(git -C "$audit_dir" rev-parse HEAD)"

printf 'harmless change\n' >> "$audit_dir/README"
git -C "$audit_dir" add README
git -C "$audit_dir" commit -qm 'harmless change'

(
  cd "$audit_dir"
  if ! gitleaks git --redact --ignore-gitleaks-allow --log-opts="$base..HEAD" . > /dev/null 2>&1; then
    echo 'ERROR: Gitleaks rejected a harmless synthetic commit.' >&2
    exit 1
  fi
)

previous="$(git -C "$audit_dir" rev-parse HEAD)"
# Generate temporary key material ONLY in the disposable local Git repository.
# It is deleted by the EXIT trap and never committed to the Aetheris project.
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "$audit_dir/disposable-private-key.pem" >/dev/null 2>&1
git -C "$audit_dir" add disposable-private-key.pem
git -C "$audit_dir" commit -qm 'synthetic private-key fixture'

set +e
(
  cd "$audit_dir"
  gitleaks git --redact --ignore-gitleaks-allow \
    --log-opts="$previous..HEAD" --report-format json \
    --report-path "$audit_dir/findings.json" . >/dev/null 2>&1
)
status=$?
set -e

if [[ "$status" -ne 1 ]]; then
  echo "ERROR: expected secret-detection exit code 1, received $status." >&2
  exit 1
fi

python3 - "$audit_dir/findings.json" <<'PY'
import json
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
if not path.is_file():
    raise SystemExit("ERROR: Gitleaks returned detection status without a report.")
matches = json.loads(path.read_text())
if not any(
    finding.get("RuleID") == "private-key"
    and pathlib.Path(finding.get("File", "")).name == "disposable-private-key.pem"
    for finding in matches
):
    raise SystemExit("ERROR: expected disposable private-key detector finding not present.")
PY

echo 'PASS: real Gitleaks accepts harmless changes and rejects disposable synthetic key material.'
