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
  if ! gitleaks git --redact --ignore-gitleaks-allow --log-opts="--diff-merges=first-parent $base..HEAD" . > /dev/null 2>&1; then
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
    --log-opts="--diff-merges=first-parent $previous..HEAD" --report-format json \
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

# Confirm a secret committed and later deleted is still detected in PR history.
# Its absence from the final worktree must never suppress the finding.
git -C "$audit_dir" rm -q disposable-private-key.pem
git -C "$audit_dir" commit -qm 'synthetic remove private-key fixture'

set +e
(
  cd "$audit_dir"
  gitleaks git --redact --ignore-gitleaks-allow \
    --log-opts="--diff-merges=first-parent $previous..HEAD" --report-format json \
    --report-path "$audit_dir/historical-findings.json" . >/dev/null 2>&1
)
history_status=$?
set -e

if [[ "$history_status" -ne 1 ]]; then
  echo "ERROR: deleted historical secret was not detected (exit $history_status)." >&2
  exit 1
fi

python3 - "$audit_dir/historical-findings.json" <<'PY'
import json
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
if not path.is_file():
    raise SystemExit("ERROR: missing historical detector report.")
matches = json.loads(path.read_text())
if not any(
    finding.get("RuleID") == "private-key"
    and pathlib.Path(finding.get("File", "")).name == "disposable-private-key.pem"
    for finding in matches
):
    raise SystemExit("ERROR: deleted historical private-key detection missing.")
PY

# A merge-conflict resolution can introduce a new secret without either
# parent commit containing it. Default git log -p omits that merge patch.
main_branch="$(git -C "$audit_dir" symbolic-ref --short HEAD)"
merge_base="$(git -C "$audit_dir" rev-parse HEAD)"
git -C "$audit_dir" checkout -qb synthetic-merge-side "$merge_base"
printf 'side of conflict\n' > "$audit_dir/README"
git -C "$audit_dir" add README
git -C "$audit_dir" commit -qm 'synthetic side change'
git -C "$audit_dir" checkout -q "$main_branch"
printf 'main of conflict\n' > "$audit_dir/README"
git -C "$audit_dir" add README
git -C "$audit_dir" commit -qm 'synthetic main change'

if git -C "$audit_dir" merge --no-ff synthetic-merge-side \
  -m 'synthetic merge test' > /dev/null 2>&1; then
  echo 'ERROR: expected a merge conflict in synthetic proof.' >&2
  exit 1
fi

printf 'resolved synthetic conflict\n' > "$audit_dir/README"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "$audit_dir/merge-only-private-key.pem" >/dev/null 2>&1
git -C "$audit_dir" add README merge-only-private-key.pem
git -C "$audit_dir" commit -qm 'resolve synthetic merge conflict'

# Construct the PEM header at runtime, so the test itself is not flagged
# as a leaked key in the PR history.
pem_header="$(printf '%s%s' '-----BEGIN PRIVATE' ' KEY-----')"
if git -C "$audit_dir" log -p "$merge_base..HEAD" | grep -q -- "$pem_header"; then
  echo 'ERROR: merge-only fixture unexpectedly visible in default git log patches.' >&2
  exit 1
fi
if ! git -C "$audit_dir" log -p --diff-merges=first-parent \
  "$merge_base..HEAD" | grep -q -- "$pem_header"; then
  echo 'ERROR: merge-only fixture missing from explicit Git merge diffs.' >&2
  exit 1
fi

set +e
(
  cd "$audit_dir"
  gitleaks git --redact --ignore-gitleaks-allow \
    --log-opts="--diff-merges=first-parent $merge_base..HEAD" \
    --report-format json --report-path "$audit_dir/merge-findings.json" \
    . >/dev/null 2>&1
)
merge_scan_status=$?
set -e

if [[ "$merge_scan_status" -ne 1 ]]; then
  echo "ERROR: merge-only secret was not detected (exit $merge_scan_status)." >&2
  exit 1
fi

python3 - "$audit_dir/merge-findings.json" <<'PY'
import json
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
if not path.is_file():
    raise SystemExit("ERROR: missing merge-only detector report.")
matches = json.loads(path.read_text())
if not any(
    finding.get("RuleID") == "private-key"
    and pathlib.Path(finding.get("File", "")).name == "merge-only-private-key.pem"
    for finding in matches
):
    raise SystemExit("ERROR: merge-resolution-only private key was not detected.")
PY

echo 'PASS: harmless, deleted-history and merge-resolution secret-detection proofs.'

