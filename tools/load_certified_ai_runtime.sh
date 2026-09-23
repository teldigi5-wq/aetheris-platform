#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REFERENCE="${AETHERIS_AI_RUNTIME_REFERENCE:-$ROOT/architecture/ai-runtime-certification-reference.json}"

readarray -t runtime < <(
  python3 - "$REFERENCE" <<'PY'
import json
import sys
from pathlib import Path

reference = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))
assert reference['status'] == 'DESTINATION_RUNTIME_CERTIFIED', reference
assert reference['source_root_deletion_status'] == 'SOURCE_EXTRACTED_TO_CERTIFIED_DESTINATION', reference
runtime = reference['destination_runtime']
for key in ('repository', 'certified_sha', 'release_tag', 'image_ref', 'image_archive', 'image_archive_sha256'):
    value = runtime.get(key)
    assert isinstance(value, str) and value, (key, value)
print(runtime['repository'])
print(runtime['certified_sha'])
print(runtime['release_tag'])
print(runtime['image_ref'])
print(runtime['image_archive'])
print(runtime['image_archive_sha256'])
PY
)

REPOSITORY="${runtime[0]}"
REVISION="${runtime[1]}"
RELEASE_TAG="${runtime[2]}"
IMAGE_REF="${runtime[3]}"
ARCHIVE="${runtime[4]}"
EXPECTED_ARCHIVE_SHA256="${runtime[5]}"
BASE_URL="https://github.com/${REPOSITORY}/releases/download/${RELEASE_TAG}"
WORK="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/aetheris-certified-runtime-${REVISION}"
mkdir -p "$WORK"

curl --fail --silent --show-error --location --retry 4 --retry-all-errors \
  "$BASE_URL/$ARCHIVE" -o "$WORK/$ARCHIVE"
curl --fail --silent --show-error --location --retry 4 --retry-all-errors \
  "$BASE_URL/$ARCHIVE.sha256" -o "$WORK/$ARCHIVE.sha256"

published_sha256="$(awk '{print $1}' "$WORK/$ARCHIVE.sha256")"
actual_sha256="$(sha256sum "$WORK/$ARCHIVE" | awk '{print $1}')"
test "$published_sha256" = "$EXPECTED_ARCHIVE_SHA256"
test "$actual_sha256" = "$EXPECTED_ARCHIVE_SHA256"

gzip -dc "$WORK/$ARCHIVE" | docker load

image_id="$(docker image inspect "$IMAGE_REF" --format '{{.Id}}')"
image_revision="$(docker image inspect "$IMAGE_REF" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')"
image_source="$(docker image inspect "$IMAGE_REF" --format '{{index .Config.Labels "org.opencontainers.image.source"}}')"
evidence_class="$(docker image inspect "$IMAGE_REF" --format '{{index .Config.Labels "io.aetheris.evidence-class"}}')"

test -n "$image_id"
test "$image_revision" = "$REVISION"
test "$image_source" = "https://github.com/$REPOSITORY"
test "$evidence_class" = "HOSTED_RUNTIME_ARTIFACT"

if [[ -n "${GITHUB_ENV:-}" ]]; then
  {
    echo "AETHERIS_AI_RUNTIME_IMAGE=$IMAGE_REF"
    echo "AETHERIS_AI_RUNTIME_REVISION=$REVISION"
    echo "AETHERIS_AI_RUNTIME_IMAGE_ID=$image_id"
  } >> "$GITHUB_ENV"
fi

printf 'Certified AI runtime loaded: repo=%s revision=%s image=%s id=%s archive_sha256=%s\n' \
  "$REPOSITORY" "$REVISION" "$IMAGE_REF" "$image_id" "$actual_sha256"
