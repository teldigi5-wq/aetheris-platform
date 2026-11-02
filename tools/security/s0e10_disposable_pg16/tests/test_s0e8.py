"""Offline-only S0-E.8 contract checks. Not PostgreSQL runtime tests."""
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = (ROOT / 'scripts/run_disposable_pg16_logins.sh').read_text()
S7 = { 'sql/00_bootstrap.sql': '5ad3a37251cf32b7bc38cdb2ee9315a807b6c7d419c28e4e7ef9bcf4ec5bf332',
       'sql/01_synthetic_tables_grants.sql': '1b209ee25903462c9e87af0c712e250d7bf9d03708fd0364f8c3551005f5db29',
       'scripts/source_gate.py': '6a73ba2006d03b616c0bbd1e684e86c4c6212f037329752b39be53bb9dc52430' }

class VerifiedBaseline(unittest.TestCase):
    def test_three_exact_e7_source_blobs(self):
        for file, expected in S7.items():
            with self.subTest(file=file):
                self.assertEqual(hashlib.sha256((ROOT / file).read_bytes()).hexdigest(), expected)

    def test_sql_scripts_never_use_real_aetheris_database(self):
        for file in S7:
            if not file.endswith('.sql'): continue
            data = (ROOT/file).read_text()
            self.assertIn("s0e7_disposable", data)
            self.assertIn("BEGIN", data)
            self.assertIn('current_database()', data)
            self.assertNotIn('DROP DATABASE', data)
            self.assertNotIn('DROP TABLE', data)

    def test_source_pin_checks_exact_branch_head(self):
        source = (ROOT/'scripts/source_gate.py').read_text()
        self.assertIn('d44d82f19d578c1c8e32b91911374a41bcea4aa8', source)
        self.assertIn('5f409670c960aa543a40948c57bcde86f54bb85e', source)
        self.assertIn('BLOCKED_SHARED_BOOTSTRAP_ROLE', source)

class EnvironmentSafety(unittest.TestCase):
    def test_opt_in_and_second_review_mandatory(self):
        self.assertIn('S0E8_EXPLICIT_DISPOSABLE_APPROVAL', SCRIPT)
        self.assertIn('YES_FRESH_PG16_LOGIN_TEST', SCRIPT)
        self.assertIn('S0E8_LOCAL_DAEMON_AND_IMAGE_REVIEWED', SCRIPT)
        self.assertIn('S0E8_APPROVED_IMAGE_DIGEST', SCRIPT)
        self.assertIn('[[ "${S0E8_LOCAL_DAEMON_AND_IMAGE_REVIEWED:-}" == \'YES\' ]]', SCRIPT)

    def test_rejects_endpoint_overrides_and_remote_context(self):
        self.assertIn('DOCKER_HOST', SCRIPT)
        self.assertIn('DOCKER_CONTEXT', SCRIPT)
        self.assertIn('context inspect', SCRIPT)
        self.assertIn('unix:///var/run/docker.sock', SCRIPT)
        self.assertIn('unreviewed Docker context', SCRIPT)

    def test_disposable_no_external_network_or_mount(self):
        self.assertIn('--network none', SCRIPT)
        self.assertIn('--tmpfs /var/lib/postgresql/data:', SCRIPT)
        self.assertIn('--pull=never', SCRIPT)
        self.assertIn('docker.io/library/postgres@${S0E8_APPROVED_IMAGE_DIGEST}', SCRIPT)
        self.assertIn('--pull=never', SCRIPT)
        self.assertNotIn('postgres:16-alpine', SCRIPT)
        self.assertNotIn('docker compose', SCRIPT.lower())
        self.assertNotIn('aetheris_pg:', SCRIPT)
        self.assertNotIn(' -p 5432:5432', SCRIPT)
        self.assertNotIn(' --mount ', SCRIPT)
        self.assertNotIn(' --volume ', SCRIPT)
        self.assertNotIn('docker volume rm', SCRIPT.lower())
        self.assertNotIn('docker system prune', SCRIPT.lower())

    def test_only_owned_labeled_id_removed(self):
        self.assertIn('aetheris.s0e8.nonce=', SCRIPT)
        self.assertIn('if [[ "$actual" == "$nonce" ]]', SCRIPT)
        self.assertIn('docker rm -f "$id"', SCRIPT)
        self.assertNotIn('docker rm -f "$container"', SCRIPT)
        self.assertNotIn('docker stop $(docker ps', SCRIPT)

    def test_discovery_never_connects(self):
        s=(ROOT/'scripts/host_discovery.sh').read_text()
        self.assertIn('command -v', s)
        for command in ['docker info', 'docker ps', 'docker run', 'docker exec', 'psql -h', 'pg_isready', 'docker context inspect']:
            self.assertNotIn(command, '\n'.join(line for line in s.splitlines() if not line.lstrip().startswith('#')))

    def test_no_opt_in_never_calls_docker(self):
        with tempfile.TemporaryDirectory() as d:
            mock=Path(d)/'docker'
            touched=Path(d)/'touched'
            mock.write_text('#!/bin/sh\necho touched > "'+str(touched)+'"\nexit 99\n')
            mock.chmod(0o755)
            env = dict(os.environ, PATH=d+':'+os.environ['PATH'])
            env.pop('S0E8_EXPLICIT_DISPOSABLE_APPROVAL',None)
            env.pop('S0E8_LOCAL_DAEMON_AND_IMAGE_REVIEWED',None)
            p=subprocess.run(['bash',str(ROOT/'scripts/run_disposable_pg16_logins.sh')],
                cwd=ROOT,env=env,capture_output=True,text=True,timeout=10)
            self.assertEqual(p.returncode,4)
            self.assertFalse(touched.exists())
            self.assertIn('approval absent', p.stderr)

class LiveHarnessContract(unittest.TestCase):
    def test_restricts_tcp_to_password_auth(self):
        self.assertIn('--auth-host=scram-sha-256', SCRIPT)
        self.assertIn('POSTGRES_HOST_AUTH_METHOD=scram-sha-256', SCRIPT)
        self.assertIn('pg_hba_file_rules', SCRIPT)
        self.assertIn("auth_method = 'scram-sha-256'", SCRIPT)
        self.assertIn('127.0.0.1', SCRIPT)

    def test_all_four_distinct_logins(self):
        self.assertIn('roles=(identity user audit orchestrator)', SCRIPT)
        self.assertIn('CREATE ROLE s0e8_%s_login LOGIN INHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS', SCRIPT)
        self.assertIn('INHERIT TRUE, SET FALSE', SCRIPT)
        self.assertIn('GRANT CONNECT ON DATABASE s0e7_disposable', SCRIPT)

    def test_verified_identity_not_set_role(self):
        self.assertIn('SELECT current_user || chr(124) || session_user', SCRIPT)
        self.assertIn('SET ROLE s0e7_${role}_owner', SCRIPT)
        self.assertNotIn('SET ROLE $role; SELECT', SCRIPT)
        self.assertIn('authenticated as its own identity', SCRIPT)

    def test_sqlstate_specific_permission_denial(self):
        self.assertIn("*'42501'*", SCRIPT)
        self.assertIn('unexpected privilege', SCRIPT)
        self.assertIn('not a PostgreSQL 42501 permission denial', SCRIPT)

    def test_cross_domain_read_write_denied(self):
        self.assertIn('for other in "${roles[@]}"', SCRIPT)
        self.assertIn('SELECT * FROM s0e7_${other}.records', SCRIPT)
        self.assertIn('INSERT INTO s0e7_${other}.records', SCRIPT)

    def test_owner_and_catalog_and_ddl_denied(self):
        self.assertIn('CREATE TABLE s0e7_${role}.forbidden_ddl', SCRIPT)
        self.assertIn('ALTER TABLE s0e7_${role}.records', SCRIPT)
        self.assertIn('CREATE ROLE s0e8_forbidden_', SCRIPT)
        self.assertIn('SELECT * FROM pg_authid', SCRIPT)

    def test_audit_append_only(self):
        self.assertIn('INSERT INTO s0e7_${role}.records', SCRIPT)
        self.assertIn('expect_priv_denied audit "UPDATE', SCRIPT)
        self.assertIn("expect_priv_denied audit 'DELETE", SCRIPT)

    def test_wrong_password_distinguishes_auth_error(self):
        self.assertIn('wrong password unexpectedly authenticated', SCRIPT)
        self.assertIn('password authentication failed', SCRIPT)
        self.assertIn('non-authentication reason', SCRIPT)

    def test_no_expanded_creds_in_docker_exec_command_arguments(self):
        self.assertIn("'IFS= read -r PGPASSWORD; export PGPASSWORD; exec psql", SCRIPT)
        self.assertIn('printf \'%s\\n\' "$password" | docker exec -i', SCRIPT)
        self.assertNotIn('docker exec -e PGPASSWORD', SCRIPT)

    def test_role_flags_not_just_table_acl(self):
        self.assertIn('rolcanlogin', SCRIPT)
        self.assertIn('rolsuper', SCRIPT)
        self.assertIn('rolcreaterole', SCRIPT)
        self.assertIn('rolbypassrls', SCRIPT)

    def test_enforces_test_is_not_release(self):
        self.assertIn('RELEASE: NO_GO',SCRIPT)
        self.assertIn('NOT A REAL SERVICE OR HISTORIC DATABASE TEST',SCRIPT)

if __name__=='__main__': unittest.main()
