"""Offline-only checks: image pull must never precede local Docker context admission.
No Docker daemon, registry, PostgreSQL, or GitHub access is used.
"""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
GATE = ROOT / 'scripts/ci_image_pin_gate.sh'


class DockerContextBeforePull(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        fake = self.directory / 'docker'
        fake.write_text('''#!/usr/bin/env bash
printf '%s\\n' "$*" >> "$SIM_DOCKER_LOG"
if [[ "$1" == context && "$2" == show ]]; then
  [[ "${SIM_FAIL_SHOW:-}" == 1 ]] && exit 1
  printf '%s\\n' "$SIM_CONTEXT"; exit 0
fi
if [[ "$1" == context && "$2" == inspect ]]; then
  [[ "${SIM_FAIL_INSPECT:-}" == 1 ]] && exit 1
  printf '%s\\n' "$SIM_ENDPOINT"; exit 0
fi
exit 0
''')
        fake.chmod(0o700)
        self.logfile = self.directory / 'docker.log'
        self.env = dict(os.environ)
        self.env.update({
            'PATH': str(self.directory) + os.pathsep + self.env['PATH'],
            'SIM_DOCKER_LOG': str(self.logfile),
            'SIM_CONTEXT': 'default',
            'SIM_ENDPOINT': 'unix:///var/run/docker.sock',
            'S0E10_OWNER_APPROVED': 'YES_SYNTHETIC_FOUR_LOGIN_PG16',
            'S0E10_APPROVED_HEAD': 'da98f4ca282a61e655e78c00ed0e03080d8610cc',
            'S0E10_ACTUAL_HEAD': 'da98f4ca282a61e655e78c00ed0e03080d8610cc',
            'S0E10_PG16_IMAGE_DIGEST':
                'sha256:cbf42f0edc946c0bc1b8eeed62403734e1f25dc67a3491455d07a92fdb558b28',
        })
        self.env.pop('DOCKER_HOST', None)
        self.env.pop('DOCKER_CONTEXT', None)

    def run_gate(self):
        result = subprocess.run(['bash', str(GATE)], cwd=ROOT, env=self.env,
                                capture_output=True, text=True, timeout=10)
        lines = self.logfile.read_text().splitlines() if self.logfile.exists() else []
        return result, lines

    def assert_no_pull(self, lines):
        self.assertFalse(any(x.startswith('pull ') or x.startswith('image tag ')
                             for x in lines), lines)

    def test_named_remote_context_rejected_before_pull(self):
        self.env['SIM_CONTEXT'] = 'remote-over-ssh'
        result, lines = self.run_gate()
        self.assertEqual(result.returncode, 4)
        self.assert_no_pull(lines)

    def test_default_context_pointing_to_remote_rejected(self):
        self.env['SIM_ENDPOINT'] = 'tcp://remote.example.invalid:2376'
        result, lines = self.run_gate()
        self.assertEqual(result.returncode, 4)
        self.assertIn('context inspect default --format {{ .Endpoints.docker.Host }}', lines)
        self.assert_no_pull(lines)

    def test_unreadable_context_fails_closed(self):
        self.env['SIM_FAIL_SHOW'] = '1'
        result, lines = self.run_gate()
        self.assertEqual(result.returncode, 4)
        self.assert_no_pull(lines)

    def test_unreadable_endpoint_fails_closed(self):
        self.env['SIM_FAIL_INSPECT'] = '1'
        result, lines = self.run_gate()
        self.assertEqual(result.returncode, 4)
        self.assert_no_pull(lines)

    def test_explicit_remote_override_rejected_before_docker(self):
        self.env['DOCKER_HOST'] = 'tcp://remote.example.invalid:2376'
        result, lines = self.run_gate()
        self.assertEqual(result.returncode, 4)
        self.assertEqual(lines, [])

    def test_missing_owner_approval_rejected_before_docker(self):
        self.env.pop('S0E10_OWNER_APPROVED')
        result, lines = self.run_gate()
        self.assertEqual(result.returncode, 4)
        self.assertEqual(lines, [])

    def test_expected_local_context_admits_only_digest_pull_and_inspect(self):
        result, lines = self.run_gate()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len(lines), 4, lines)
        self.assertEqual(lines[0], 'context show')
        self.assertEqual(lines[1],
                         'context inspect default --format {{ .Endpoints.docker.Host }}')
        self.assertEqual(lines[2],
                         'pull docker.io/library/postgres@' +
                         self.env['S0E10_PG16_IMAGE_DIGEST'])
        self.assertEqual(lines[3],
                         'image inspect docker.io/library/postgres@' +
                         self.env['S0E10_PG16_IMAGE_DIGEST'])
        self.assertFalse(any(line.startswith('image tag ') for line in lines))


if __name__ == '__main__':
    unittest.main()
