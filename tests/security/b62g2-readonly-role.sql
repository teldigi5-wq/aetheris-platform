-- B62-G2: privilege separation inside disposable b62g_synthetic ONLY.
-- NOT a Flyway or live database migration; no production role is created.
BEGIN;
CREATE ROLE b62g_verifier NOLOGIN;
REVOKE ALL ON SCHEMA b62g_proof FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA b62g_proof FROM PUBLIC;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA b62g_proof FROM PUBLIC;
GRANT USAGE ON SCHEMA b62g_proof TO b62g_verifier;
GRANT SELECT ON b62g_proof.principal_epochs,
                b62g_proof.blocked_signer_kids TO b62g_verifier;
GRANT EXECUTE ON FUNCTION
  b62g_proof.synthetic_post_signature_allowed(bigint,text,text,bigint)
  TO b62g_verifier;
COMMIT;