-- V4: fixed-function, read/lock-only entry for the ingest role.
-- Postgres SELECT FOR SHARE requires UPDATE table privileges. Do not grant
-- these to the Audit listener. This function instead borrows the narrowly
-- scoped migration owner's row lock while exposing no mutation surface.
-- SECURITY DEFINER hardening: no dynamic SQL, fully qualified relations,
-- trusted search_path, no default PUBLIC EXECUTE.
CREATE OR REPLACE FUNCTION public.audit_membership_can_ingest(
    p_tenant UUID, p_user BIGINT, p_occurred TIMESTAMPTZ
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $function$
DECLARE
    grant_row RECORD;
BEGIN
    IF p_tenant IS NULL OR p_user IS NULL OR p_user <= 0
            OR p_occurred IS NULL THEN
        RETURN FALSE;
    END IF;
    SELECT m.status, m.valid_since, m.revision
      INTO grant_row
      FROM public.audit_user_tenant_membership_fence AS m
     WHERE m.tenant_id = p_tenant AND m.user_id = p_user
     FOR SHARE;
    RETURN FOUND
        AND grant_row.status = 'ACTIVE'
        AND grant_row.revision > 0
        AND p_occurred >= grant_row.valid_since;
END;
$function$;
REVOKE ALL ON FUNCTION public.audit_membership_can_ingest(
    UUID, BIGINT, TIMESTAMPTZ) FROM PUBLIC;
COMMENT ON FUNCTION public.audit_membership_can_ingest(
    UUID, BIGINT, TIMESTAMPTZ) IS
'Fixed, owner-executed membership admission row lock. EXECUTE may be granted to an Audit ingest role only after independent operator review.';
