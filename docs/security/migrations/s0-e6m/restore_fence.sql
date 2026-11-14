-- S0-E6M: review-only PostgreSQL 16 restored-session deny fence.
-- NEVER apply to owner or production databases without approved operators.
-- Stop ALL Identity, Runtime, Gateway and worker access before restoring.
-- This script must be executed as the scoped recovery authority with an
-- operator-approved, separately audited maintenance change.
--
-- A point-in-time recovery can RESURRECT already revoked JTI sessions and
-- previously active signing keys. This fence denies EVERY restored session
-- and signing key before the restored DB is permitted to serve traffic.
-- Operators must issue new signer keys and establish new sessions only after
-- reviewed restore consistency and authorization reconciliation.
--
-- CI guarantees this runs ONLY in a newly restored disposable database.
BEGIN;
SET LOCAL lock_timeout = '3000ms';
SET LOCAL statement_timeout = '15000ms';
LOCK TABLE
    e6j_runtime_auth.signer_key_states,
    e6j_runtime_auth.principal_epochs,
    e6j_runtime_auth.session_registrations,
    e6j_runtime_auth.authority_audit
    IN ACCESS EXCLUSIVE MODE;

-- Audit each signer whose authority is being invalidated in this restore.
-- DB-local audit is not immutable external evidence; external incident/change
-- reporting and backup proof remain mandatory production release blockers.
INSERT INTO e6j_runtime_auth.authority_audit
    (request_id, action, actor_class, session_jti, identity_uid)
SELECT gen_random_uuid(), 'REVOKE_SIGNER', 'EMERGENCY_REVOKER',
       NULL, NULL
FROM e6j_runtime_auth.signer_key_states
WHERE state = 'ACTIVE';

-- Record each recovered session that will be forcibly denied. This is
-- still DB-local audit; it does not replace external tamper-resistant logs.
INSERT INTO e6j_runtime_auth.authority_audit
    (request_id, action, actor_class, session_jti, identity_uid)
SELECT gen_random_uuid(), 'REVOKE_SESSION', 'EMERGENCY_REVOKER',
       jti, uid
FROM e6j_runtime_auth.session_registrations
WHERE state = 'ACTIVE';

UPDATE e6j_runtime_auth.signer_key_states
   SET state = 'REVOKED'
 WHERE state = 'ACTIVE';
UPDATE e6j_runtime_auth.session_registrations
   SET state = 'REVOKED', revoked_at = clock_timestamp()
 WHERE state = 'ACTIVE';

-- Every pre-restoration session is now fail-closed. Keep all traffic OFF
-- until the transaction commits and an independent verifier rechecks it.
COMMIT;
