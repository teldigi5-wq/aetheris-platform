-- B62-G13: CI-only, SECURITY DEFINER least-privileged emergency operations.
-- NOT a Flyway migration, production role, KMS signature or operator approval.
-- Install only AFTER G1 prototype, G2 reader role and G8 test logins into
-- disposable b62g_synthetic. Old G8 writer remains for inherited tests only.
BEGIN;
CREATE ROLE b62g13_exec_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE ROLE b62g13_emergency_writer LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE
  NOREPLICATION PASSWORD 'b62g13-writer-ci-synthetic-only';
CREATE ROLE b62g13_audit_reviewer LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE
  NOREPLICATION PASSWORD 'b62g13-auditor-ci-synthetic-only';

CREATE TABLE b62g_proof.b62g13_emergency_audit (
  request_id text PRIMARY KEY CHECK (request_id ~ '^ci-[a-z0-9-]{6,56}$'),
  action text NOT NULL CHECK (action IN ('raise_epoch','block_signer')),
  principal_id bigint,
  signer_issuer text,
  signer_kid text,
  signed_iat_cutoff bigint,
  actor text NOT NULL CHECK (actor = 'b62g13_emergency_writer'),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  CHECK ((action = 'raise_epoch' AND principal_id > 0 AND signed_iat_cutoff > 0
          AND signer_issuer IS NULL AND signer_kid IS NULL)
      OR (action = 'block_signer' AND principal_id IS NULL
          AND signed_iat_cutoff IS NULL AND signer_issuer IS NOT NULL
          AND signer_kid IS NOT NULL))
);

GRANT USAGE ON SCHEMA b62g_proof TO b62g13_exec_owner,
  b62g13_emergency_writer,b62g13_audit_reviewer;
GRANT SELECT, INSERT, UPDATE ON b62g_proof.principal_epochs TO b62g13_exec_owner;
GRANT SELECT, INSERT ON b62g_proof.blocked_signer_kids TO b62g13_exec_owner;
GRANT INSERT ON b62g_proof.b62g13_emergency_audit TO b62g13_exec_owner;
GRANT SELECT ON b62g_proof.b62g13_emergency_audit TO b62g13_audit_reviewer;
GRANT CONNECT ON DATABASE b62g_synthetic TO b62g13_emergency_writer,b62g13_audit_reviewer;

-- Atomically audit and apply a monotonically increasing cutoff. The
-- request_id uniqueness blocks replay across either reviewed operation.
-- No arbitrary dynamic SQL and no attacker-controllable search_path.
CREATE FUNCTION b62g_proof.b62g13_raise_epoch(
    p_principal bigint,p_cutoff bigint,p_request_id text)
RETURNS bigint LANGUAGE plpgsql VOLATILE SECURITY DEFINER
SET search_path = pg_catalog, pg_temp AS $func$
DECLARE current_cutoff bigint;
BEGIN
  IF p_principal IS NULL OR p_principal <= 0
     OR p_cutoff IS NULL OR p_cutoff <= 0
     OR p_request_id IS NULL
     OR p_request_id !~ '^ci-[a-z0-9-]{6,56}$'
     OR session_user <> 'b62g13_emergency_writer' THEN
     RAISE EXCEPTION 'Invalid synthetic emergency request';
  END IF;

  INSERT INTO b62g_proof.b62g13_emergency_audit
    (request_id,action,principal_id,signed_iat_cutoff,actor)
  VALUES (p_request_id,'raise_epoch',p_principal,p_cutoff,session_user);

  INSERT INTO b62g_proof.principal_epochs AS existing
    (principal_id,reject_issued_at_or_before_epoch)
  VALUES (p_principal,p_cutoff)
  ON CONFLICT (principal_id) DO UPDATE
    SET reject_issued_at_or_before_epoch =
      GREATEST(existing.reject_issued_at_or_before_epoch,
               EXCLUDED.reject_issued_at_or_before_epoch),
        updated_at = clock_timestamp()
  RETURNING reject_issued_at_or_before_epoch INTO current_cutoff;
  RETURN current_cutoff;
END;
$func$;

CREATE FUNCTION b62g_proof.b62g13_block_signer(
    p_issuer text,p_kid text,p_request_id text)
RETURNS void LANGUAGE plpgsql VOLATILE SECURITY DEFINER
SET search_path = pg_catalog, pg_temp AS $func$
BEGIN
  IF p_issuer IS NULL OR length(p_issuer) NOT BETWEEN 1 AND 120
     OR p_kid IS NULL OR length(p_kid) NOT BETWEEN 1 AND 64
     OR p_request_id IS NULL
     OR p_request_id !~ '^ci-[a-z0-9-]{6,56}$'
     OR session_user <> 'b62g13_emergency_writer' THEN
     RAISE EXCEPTION 'Invalid synthetic signer block request';
  END IF;
  INSERT INTO b62g_proof.b62g13_emergency_audit
    (request_id,action,signer_issuer,signer_kid,actor)
  VALUES (p_request_id,'block_signer',p_issuer,p_kid,session_user);
  INSERT INTO b62g_proof.blocked_signer_kids(issuer,kid,reason_code)
  VALUES (p_issuer,p_kid,'compromise')
  ON CONFLICT (issuer,kid) DO NOTHING;
END;
$func$;

-- New functions have PUBLIC EXECUTE by default. Revoke BEFORE transaction
-- commit; owner is deliberately NOLOGIN and has no superuser powers.
REVOKE ALL ON FUNCTION b62g_proof.b62g13_raise_epoch(bigint,bigint,text) FROM PUBLIC;
REVOKE ALL ON FUNCTION b62g_proof.b62g13_block_signer(text,text,text) FROM PUBLIC;
ALTER FUNCTION b62g_proof.b62g13_raise_epoch(bigint,bigint,text)
  OWNER TO b62g13_exec_owner;
ALTER FUNCTION b62g_proof.b62g13_block_signer(text,text,text)
  OWNER TO b62g13_exec_owner;
GRANT EXECUTE ON FUNCTION b62g_proof.b62g13_raise_epoch(bigint,bigint,text)
  TO b62g13_emergency_writer;
GRANT EXECUTE ON FUNCTION b62g_proof.b62g13_block_signer(text,text,text)
  TO b62g13_emergency_writer;
REVOKE ALL ON TABLE b62g_proof.b62g13_emergency_audit FROM PUBLIC;
COMMIT;

SELECT 'B62G13_SYNTHETIC_FUNCTION_ONLY_WRITER_INSTALLED';
