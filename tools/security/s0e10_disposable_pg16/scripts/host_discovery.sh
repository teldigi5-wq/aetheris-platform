#!/usr/bin/env bash
# Pure local PATH/config inspection. Does NOT contact Docker or a database.
set -euo pipefail
printf 'S0-E.8 LOCAL CAPABILITY INVENTORY (NO CONNECTIONS)\n'
for tool in docker docker.exe podman psql pg_ctl initdb postgres python3; do
  if command -v "$tool" >/dev/null 2>&1; then
    printf 'AVAILABLE %s\n' "$tool"
  else
    printf 'UNAVAILABLE %s\n' "$tool"
  fi
done
if [[ -n "${DOCKER_HOST:-}" || -n "${DOCKER_CONTEXT:-}" ]]; then
  printf 'DOCKER_ENDPOINT_OVERRIDE_PRESENT: YES — no auto-rehearsal\n'
else
  printf 'DOCKER_ENDPOINT_OVERRIDE_PRESENT: NO\n'
fi
# This script must never execute docker info, ps, inspect or network probes.
printf 'RESULT: ENVIRONMENT ONLY; RUNTIME POSTGRESQL NOT TESTED; NO_GO\n'
