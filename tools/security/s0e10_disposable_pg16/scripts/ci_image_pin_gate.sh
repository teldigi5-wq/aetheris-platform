#!/usr/bin/env bash
# Synthetic-only CI approval and immutable-image gate; never uses production credentials.
# Fail before reaching Docker if review consent or image provenance is absent.
set -euo pipefail
if [[ "${S0E10_OWNER_APPROVED:-}" != 'YES_SYNTHETIC_FOUR_LOGIN_PG16' ]]; then
  echo 'S0-E.10 DENIED: owner CI consent absent' >&2
  exit 4
fi
# Approval is bound to the immutable exact candidate PR head, not a reusable blanket switch.
if [[ ! "${S0E10_APPROVED_HEAD:-}" =~ ^[a-f0-9]{40}$ ]] ||
   [[ "${S0E10_APPROVED_HEAD}" != "${S0E10_ACTUAL_HEAD:-}" ]]; then
  echo 'S0-E.10 DENIED: no head-bound owner authorization' >&2
  exit 4
fi
if [[ ! "${S0E10_PG16_IMAGE_DIGEST:-}" =~ ^sha256:[a-f0-9]{64}$ ]]; then
  echo 'S0-E.10 DENIED: immutable PostgreSQL image digest not reviewed' >&2
  exit 4
fi
if [[ -n "${DOCKER_HOST:-}" || -n "${DOCKER_CONTEXT:-}" ]]; then
  echo 'S0-E.10 DENIED: Docker endpoint override' >&2
  exit 4
fi
if ! command -v docker >/dev/null 2>&1; then
  echo 'S0-E.10 DENIED: Docker unavailable' >&2
  exit 4
fi
# Never contact an unreviewed remote daemon. Environment variables alone
# cannot prove that Docker's selected context resolves to a local socket.
ctx="$(docker context show 2>/dev/null)" || {
  echo 'S0-E.10 DENIED: cannot identify Docker context' >&2
  exit 4
}
[[ "$ctx" == 'default' || "$ctx" == 'desktop-linux' ]] || {
  echo 'S0-E.10 DENIED: unreviewed Docker context' >&2
  exit 4
}
endpoint="$(docker context inspect "$ctx" --format '{{ .Endpoints.docker.Host }}' 2>/dev/null)" || {
  echo 'S0-E.10 DENIED: cannot inspect Docker endpoint' >&2
  exit 4
}
[[ "$endpoint" == 'unix:///var/run/docker.sock' ]] || {
  echo 'S0-E.10 DENIED: not the expected local Docker socket' >&2
  exit 4
}
# This checks the selected endpoint, not the physical ownership of the daemon.
# Intentionally restrict pull source to Docker Hub official PostgreSQL image.
image="docker.io/library/postgres@${S0E10_PG16_IMAGE_DIGEST}"
# A reviewed digest may still be unsuitable (wrong arch/version): the
# subsequent PostgreSQL-16 bootstrap SQL guard must independently verify it.
docker pull "$image" >/dev/null
# E.8 harness does not pull images: tag this exact reviewed immutable image.
docker image tag "$image" postgres:16-alpine
printf 'S0-E.10 PINNED_IMAGE_READY: SHA256 digest supplied by owner-controlled CI variable\n'
