-- B62-G: executable synthetic PostgreSQL policy boundaries.
-- Fatal exceptions fail CI, not informational counters.
DO $check$
DECLARE
    latest bigint;
BEGIN
    IF NOT b62g_proof.synthetic_post_signature_allowed(
        42, 'b62g-identity', 'key-current', 5001
    ) THEN
        RAISE EXCEPTION 'B62G_BASELINE_VALID_PRINCIPAL_DENIED';
    END IF;
    PERFORM b62g_proof.raise_principal_epoch(42, 5000);
    latest := b62g_proof.raise_principal_epoch(42, 4999);
    IF latest <> 5000 THEN
        RAISE EXCEPTION 'B62G_EPOCH_WENT_BACKWARD';
    END IF;
    IF b62g_proof.synthetic_post_signature_allowed(
        42, 'b62g-identity', 'key-current', 4999
    ) OR b62g_proof.synthetic_post_signature_allowed(
        42, 'b62g-identity', 'key-current', 5000
    ) THEN
        RAISE EXCEPTION 'B62G_REVOKED_IAT_ACCEPTED';
    END IF;
    IF NOT b62g_proof.synthetic_post_signature_allowed(
        42, 'b62g-identity', 'key-current', 5001
    ) THEN
        RAISE EXCEPTION 'B62G_NEW_ISSUANCE_INCORRECTLY_DENIED';
    END IF;
    IF NOT b62g_proof.synthetic_post_signature_allowed(
        43, 'b62g-identity', 'key-current', 4999
    ) THEN
        RAISE EXCEPTION 'B62G_UNRELATED_USER_DENIED';
    END IF;
    PERFORM b62g_proof.block_signer('b62g-identity', 'key-compromised', 'compromise');
    PERFORM b62g_proof.block_signer('b62g-identity', 'key-compromised', 'retired');
    IF b62g_proof.synthetic_post_signature_allowed(
        43, 'b62g-identity', 'key-compromised', 9000
    ) THEN
        RAISE EXCEPTION 'B62G_COMPROMISED_KID_ACCEPTED';
    END IF;
    IF (SELECT count(*) FROM b62g_proof.blocked_signer_kids
        WHERE issuer='b62g-identity' AND kid='key-compromised'
    ) <> 1 THEN
        RAISE EXCEPTION 'B62G_DUPLICATE_SIGNER_REVOCATION';
    END IF;
    IF b62g_proof.synthetic_post_signature_allowed(
        NULL, 'b62g-identity', 'key-current', 9000
    ) OR b62g_proof.synthetic_post_signature_allowed(
        42, NULL, 'key-current', 9000
    ) OR b62g_proof.synthetic_post_signature_allowed(
        42, 'b62g-identity', NULL, 9000
    ) OR b62g_proof.synthetic_post_signature_allowed(
        42, 'b62g-identity', 'key-current', NULL
    ) THEN
        RAISE EXCEPTION 'B62G_MISSING_CLAIM_ACCEPTED';
    END IF;
END
$check$;
SELECT 'B62G_SYNTHETIC_SQL_BOUNDARIES_PASS' AS result;
