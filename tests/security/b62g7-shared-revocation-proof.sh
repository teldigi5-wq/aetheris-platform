#!/usr/bin/env bash
# B62-G7: disposable, external-less, loopback-only synthetic HTTP fixture.
# Real Gateway/User/Audit filter JUnit integration, NOT production enforcement.
set -euo pipefail
cd "$(dirname "$0")/../.."
AUTHORITY_PID=""
cleanup() {
  if [[ -n "$AUTHORITY_PID" ]]; then
    kill "$AUTHORITY_PID" >/dev/null 2>&1 || true
    wait "$AUTHORITY_PID" >/dev/null 2>&1 || true
    AUTHORITY_PID=""
  fi
}
trap cleanup EXIT
start_fixture() {
  local phase="$1"
  python3 -u tests/security/b62g7-loopback-authority.py --stage "$phase" &
  AUTHORITY_PID="$!"
  python3 - "$AUTHORITY_PID" <<'PY'
import sys,time,urllib.request
pid=int(sys.argv[1])
for _ in range(90):
    try:
        with urllib.request.urlopen("http://127.0.0.1:15377/health",timeout=0.5) as response:
            if response.status==200 and response.read(50)==b"B62G7_TEST_ONLY_READY":
                print("B62G7_PINNED_LOOPBACK_FIXTURE_READY",flush=True)
                break
    except (OSError,TimeoutError):
        time.sleep(0.1)
else:
    raise SystemExit("B62G7_LOOPBACK_FIXTURE_FAILED_TO_START")
PY
  kill -0 "$AUTHORITY_PID" || { echo B62G7_FIXTURE_PROCESS_DEAD; exit 1; }
}
run_phase() {
  local phase="$1"
  ./mvnw -B -ntp -f pom.xml \
    -Dtest=B62G7SharedHttpContractIT \
    -Dsurefire.failIfNoSpecifiedTests=false \
    "-Db62g7.stage=${phase}" test
  echo "B62G7_THREE_REAL_FILTERS_PHASE_${phase^^}_PASSED"
}
for phase in baseline epoch blocked; do
  start_fixture "$phase"
  run_phase "$phase"
  cleanup
done
# Truly unavailable authority: not a synthetic boolean returned in-process.
run_phase outage
echo B62G7_REAL_FILTERS_SHARED_LOOPBACK_HTTP_CONTRACT_COMPLETE
echo B62G7_NO_PRODUCTION_JWT_REVOCATION_INTEGRATION
