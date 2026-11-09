-- S0-E6J. DISPOSABLE PostgreSQL 16 REVIEW CONTRACT ONLY.
-- NOT a Flyway migration; never apply to production or shared databases.
-- The executor must first prove an isolated CI database and review this DDL.
BEGIN;
CREATE ROLE e6j_reader NOLOGIN;
CREATE ROLE e6j_issuer NOLOGIN;
CREATE ROLE e6j_revoker NOLOGIN;
CREATE ROLE e6j_outsider NOLOGIN;
CREATE SCHEMA e6j_runtime_auth;
REVOKE ALL ON SCHEMA e6j_runtime_auth FROM PUBLIC;

CREATE TABLE e6j_runtime_auth.signer_key_states (
    issuer varchar(255) NOT NULL,
    kid varchar(64) NOT NULL,
    state varchar(16) NOT NULL CHECK (state IN ('ACTIVE','REVOKED')),
    PRIMARY KEY (issuer,kid)
);
CREATE TABLE e6j_runtime_auth.principal_epochs (
    principal_id bigint PRIMARY KEY CHECK (principal_id > 0),
    reject_issued_at_or_before_epoch bigint NOT NULL DEFAULT 0
        CHECK (reject_issued_at_or_before_epoch >= 0)
);
CREATE TABLE e6j_runtime_auth.session_registrations (
    issuer varchar(255) NOT NULL,
    kid varchar(64) NOT NULL,
    jti varchar(128) NOT NULL,
    uid bigint NOT NULL CHECK (uid > 0),
    iat_seconds bigint NOT NULL CHECK (iat_seconds > 0),
    exp_seconds bigint NOT NULL CHECK (exp_seconds > iat_seconds),
    state varchar(16) NOT NULL DEFAULT 'ACTIVE'
        CHECK (state IN ('ACTIVE','REVOKED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    PRIMARY KEY (issuer,kid,jti),
    FOREIGN KEY (issuer,kid)
        REFERENCES e6j_runtime_auth.signer_key_states(issuer,kid),
    FOREIGN KEY (uid)
        REFERENCES e6j_runtime_auth.principal_epochs(principal_id),
    CHECK ((state='ACTIVE' AND revoked_at IS NULL)
         OR (state='REVOKED' AND revoked_at IS NOT NULL))
);
CREATE TABLE e6j_runtime_auth.authority_audit (
    request_id uuid PRIMARY KEY,
    action varchar(24) NOT NULL CHECK (
        action IN ('ENROLL','REVOKE_SESSION','REVOKE_SIGNER','ADVANCE_EPOCH')),
    actor_class varchar(24) NOT NULL CHECK (
        actor_class IN ('IDENTITY_ISSUER','EMERGENCY_REVOKER')),
    session_jti varchar(128),
    identity_uid bigint,
    occurred_at timestamptz NOT NULL DEFAULT now()
);

REVOKE ALL ON ALL TABLES IN SCHEMA e6j_runtime_auth FROM PUBLIC;

-- In production these SECURITY DEFINER functions must be owned by a
-- separate NOLOGIN minimum-privilege role, not by the migration superuser.
-- Identity signer private keys and signed-JWT verification are NOT inside SQL.
CREATE FUNCTION e6j_runtime_auth.enroll_signed_session(
    p_issuer varchar, p_kid varchar, p_jti varchar,
    p_uid bigint, p_iat bigint, p_exp bigint, p_request uuid)
RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE v_cutoff bigint;
BEGIN
    IF p_request IS NULL OR p_issuer IS NULL OR length(p_issuer) < 8
       OR p_kid IS NULL OR length(p_kid) < 1 OR p_jti IS NULL
       OR length(p_jti) < 16 OR p_uid IS NULL OR p_uid <= 0
       OR p_iat IS NULL OR p_iat <= 0 OR p_exp IS NULL
       OR p_exp <= p_iat OR p_exp > p_iat + 300
       OR p_exp <= extract(epoch FROM clock_timestamp())::bigint
       OR length(p_issuer) > 255 OR length(p_kid) > 64
       OR length(p_jti) > 128 THEN
        RETURN false;
    END IF;
    -- The approved Identity-owned trusted issuer role must supply facts
    -- derived from a genuinely signed token, never client claims.
    PERFORM 1 FROM e6j_runtime_auth.signer_key_states
      WHERE issuer=p_issuer AND kid=p_kid AND state='ACTIVE'
      FOR SHARE;
    IF NOT FOUND THEN RETURN false; END IF;
    SELECT reject_issued_at_or_before_epoch INTO v_cutoff
      FROM e6j_runtime_auth.principal_epochs
      WHERE principal_id=p_uid FOR SHARE;
    IF NOT FOUND OR p_iat <= v_cutoff THEN RETURN false; END IF;
    INSERT INTO e6j_runtime_auth.session_registrations(
        issuer,kid,jti,uid,iat_seconds,exp_seconds,state)
      VALUES(p_issuer,p_kid,p_jti,p_uid,p_iat,p_exp,'ACTIVE');
    INSERT INTO e6j_runtime_auth.authority_audit(
        request_id,action,actor_class,session_jti,identity_uid)
      VALUES(p_request,'ENROLL','IDENTITY_ISSUER',p_jti,p_uid);
    RETURN true;
END;
$$;

CREATE FUNCTION e6j_runtime_auth.revoke_signed_session(
    p_issuer varchar, p_kid varchar, p_jti varchar, p_request uuid)
RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE v_uid bigint;
BEGIN
    IF p_request IS NULL OR p_issuer IS NULL OR p_kid IS NULL
       OR p_jti IS NULL THEN RETURN false; END IF;
    UPDATE e6j_runtime_auth.session_registrations
       SET state='REVOKED', revoked_at=clock_timestamp()
     WHERE issuer=p_issuer AND kid=p_kid AND jti=p_jti
       AND state='ACTIVE'
     RETURNING uid INTO v_uid;
    IF NOT FOUND THEN RETURN false; END IF;
    INSERT INTO e6j_runtime_auth.authority_audit(
        request_id,action,actor_class,session_jti,identity_uid)
      VALUES(p_request,'REVOKE_SESSION','EMERGENCY_REVOKER',p_jti,v_uid);
    RETURN true;
END;
$$;

CREATE FUNCTION e6j_runtime_auth.block_signing_key(
    p_issuer varchar, p_kid varchar, p_request uuid)
RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    IF p_request IS NULL OR p_issuer IS NULL OR p_kid IS NULL
        THEN RETURN false; END IF;
    UPDATE e6j_runtime_auth.signer_key_states SET state='REVOKED'
      WHERE issuer=p_issuer AND kid=p_kid AND state='ACTIVE';
    IF NOT FOUND THEN RETURN false; END IF;
    INSERT INTO e6j_runtime_auth.authority_audit(
        request_id,action,actor_class)
      VALUES(p_request,'REVOKE_SIGNER','EMERGENCY_REVOKER');
    RETURN true;
END;
$$;

CREATE FUNCTION e6j_runtime_auth.advance_principal_epoch(
    p_uid bigint, p_new_cutoff bigint, p_request uuid)
RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    IF p_request IS NULL OR p_uid IS NULL OR p_uid <= 0
       OR p_new_cutoff IS NULL OR p_new_cutoff <= 0
       OR p_new_cutoff > extract(epoch FROM clock_timestamp())::bigint
       THEN RETURN false; END IF;
    UPDATE e6j_runtime_auth.principal_epochs
       SET reject_issued_at_or_before_epoch=p_new_cutoff
     WHERE principal_id=p_uid
       AND reject_issued_at_or_before_epoch < p_new_cutoff;
    IF NOT FOUND THEN RETURN false; END IF;
    INSERT INTO e6j_runtime_auth.authority_audit(
        request_id,action,actor_class,identity_uid)
      VALUES(p_request,'ADVANCE_EPOCH','EMERGENCY_REVOKER',p_uid);
    RETURN true;
END;
$$;

-- PUBLIC executes functions by default in PostgreSQL: explicitly revoke.
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA e6j_runtime_auth FROM PUBLIC;
GRANT USAGE ON SCHEMA e6j_runtime_auth
  TO e6j_reader,e6j_issuer,e6j_revoker;
GRANT SELECT ON e6j_runtime_auth.session_registrations,
  e6j_runtime_auth.signer_key_states,
  e6j_runtime_auth.principal_epochs TO e6j_reader;
GRANT EXECUTE ON FUNCTION e6j_runtime_auth.enroll_signed_session(
  varchar,varchar,varchar,bigint,bigint,bigint,uuid) TO e6j_issuer;
GRANT EXECUTE ON FUNCTION e6j_runtime_auth.revoke_signed_session(
  varchar,varchar,varchar,uuid) TO e6j_revoker;
GRANT EXECUTE ON FUNCTION e6j_runtime_auth.block_signing_key(
  varchar,varchar,uuid) TO e6j_revoker;
GRANT EXECUTE ON FUNCTION e6j_runtime_auth.advance_principal_epoch(
  bigint,bigint,uuid) TO e6j_revoker;
COMMIT;
