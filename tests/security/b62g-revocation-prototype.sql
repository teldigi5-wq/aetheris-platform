-- B62-G PROOF ONLY. This is NOT an Identity/Flyway migration.
-- Install only into disposable b62g_synthetic database in CI.
-- Signed JWT validation and account ownership MUST precede any lookup.
BEGIN;
CREATE SCHEMA b62g_proof;

CREATE TABLE b62g_proof.principal_epochs (
    principal_id bigint PRIMARY KEY CHECK (principal_id > 0),
    reject_issued_at_or_before_epoch bigint NOT NULL
        CHECK (reject_issued_at_or_before_epoch > 0),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE b62g_proof.blocked_signer_kids (
    issuer text NOT NULL CHECK (length(issuer) BETWEEN 1 AND 120),
    kid text NOT NULL CHECK (length(kid) BETWEEN 1 AND 64),
    reason_code text NOT NULL DEFAULT 'emergency'
        CHECK (reason_code IN ('emergency', 'compromise', 'retired')),
    blocked_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (issuer, kid)
);

-- Lock-safe, monotonic UPSERT, never LOWER an already-issued cutoff.
CREATE FUNCTION b62g_proof.raise_principal_epoch(
    p_principal_id bigint, p_cutoff bigint
) RETURNS bigint
LANGUAGE sql VOLATILE SECURITY INVOKER AS $function$
    INSERT INTO b62g_proof.principal_epochs AS existing
      (principal_id, reject_issued_at_or_before_epoch)
    VALUES (p_principal_id, p_cutoff)
    ON CONFLICT (principal_id) DO UPDATE
      SET reject_issued_at_or_before_epoch =
          GREATEST(existing.reject_issued_at_or_before_epoch,
                   EXCLUDED.reject_issued_at_or_before_epoch),
          updated_at = clock_timestamp()
    RETURNING reject_issued_at_or_before_epoch;
$function$;

-- No delete or unblock operation: lifting a compromised kid requires a
-- separately authorized, audited production design outside this proof.
CREATE FUNCTION b62g_proof.block_signer(
    p_issuer text, p_kid text, p_reason text
) RETURNS void LANGUAGE sql VOLATILE SECURITY INVOKER AS $function$
    INSERT INTO b62g_proof.blocked_signer_kids (issuer, kid, reason_code)
    VALUES (p_issuer, p_kid, p_reason)
    ON CONFLICT (issuer, kid) DO NOTHING;
$function$;

-- This is a synthetic post-signature decision ONLY; it does not verify JWTs.
-- A real verifier MUST treat store timeout/error as DENY, never ALLOW.
CREATE FUNCTION b62g_proof.synthetic_post_signature_allowed(
    p_principal_id bigint, p_issuer text, p_kid text, p_signed_iat bigint
) RETURNS boolean LANGUAGE sql STABLE SECURITY INVOKER AS $function$
    SELECT COALESCE(
        p_principal_id > 0 AND p_signed_iat > 0
        AND length(p_issuer) BETWEEN 1 AND 120
        AND length(p_kid) BETWEEN 1 AND 64
        AND NOT EXISTS (
            SELECT 1 FROM b62g_proof.blocked_signer_kids blocked
            WHERE blocked.issuer = p_issuer AND blocked.kid = p_kid
        )
        AND NOT EXISTS (
            SELECT 1 FROM b62g_proof.principal_epochs revoked
            WHERE revoked.principal_id = p_principal_id
              AND p_signed_iat <= revoked.reject_issued_at_or_before_epoch
        ), FALSE
    );
$function$;
COMMIT;
