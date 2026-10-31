#!/usr/bin/env bash
# Fail-closed Gitleaks regression gate for new commits. No reports or secret contents are persisted.
set -euo pipefail

base_sha="${1:-}"
if [[ ! "$base_sha" =~ ^[0-9a-f]{40}$ ]] || [[ "$base_sha" =~ ^0{40}$ ]]; then
  echo 'ERROR: missing or invalid nonzero base commit SHA; refusing to skip secret scan.' >&2
  exit 2
fi

if ! git rev-parse --is-inside-work-tree | grep -qx true; then
  echo 'ERROR: not inside a Git repository.' >&2
  exit 2
fi

if ! git cat-file -e "${base_sha}^{commit}" 2>/dev/null; then
  echo 'ERROR: base commit absent; checkout must fetch full history.' >&2
  exit 2
fi

if ! git merge-base --is-ancestor "$base_sha" HEAD; then
  echo 'ERROR: base commit is not an ancestor of checked-out HEAD.' >&2
  exit 2
fi

# Fail closed on repository-provided ignore/allowlist files. Otherwise a PR
# could add a broad exclusion that hides its own leaked credentials.
for override in .gitleaks.toml .gitleaksignore; do
  if [[ -e "$override" ]]; then
    echo "ERROR: repository Gitleaks configuration override is not permitted: $override" >&2
    exit 2
  fi
done

count="$(git rev-list --count "${base_sha}..HEAD")"
if [[ "$count" -eq 0 ]]; then
  echo 'ERROR: empty scan range; refusing to report a successful scan.' >&2
  exit 2
fi

echo "Scanning $count newly introduced commits (no secret values printed)."
# Include merge-resolution diffs: ordinary git log -p omits merge commit patches.
# Force text patches: .gitattributes -diff or Git's binary heuristic can hide secrets.
# Disable external diff/textconv execution and preserve redacted scanner output.
# No inline gitleaks:allow suppression; default rules apply to all paths.
"${GITLEAKS_BIN:-gitleaks}" git \
  --redact \
  --ignore-gitleaks-allow \
  --log-opts="--diff-merges=first-parent --text --no-ext-diff --no-textconv ${base_sha}..HEAD" \
  .
