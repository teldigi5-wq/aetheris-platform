-- MANUAL REVIEW-ONLY, ADDITIVE PostgreSQL 16 migration.
-- NEVER apply to production without owner-reviewed role/backup/change control.
-- Two NOLOGIN capability roles are required. No public HTTP replay endpoint.
BEGIN;

DO $roles$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='aetheris_outbox_approver') THEN
        CREATE ROLE aetheris_outbox_approver NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='aetheris_outbox_operator') THEN
        CREATE ROLE aetheris_outbox_operator NOLOGIN;
    END IF;
END
$roles$;

ALTER TABLE public.user_event_outbox
    ADD COLUMN IF NOT EXISTS recovery_generation SMALLINT NOT NULL DEFAULT 0;
-- Strict cap of TWO independently approved recoveries per event.
DO $constraint$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_constraint
                   WHERE conname='ck_user_event_outbox_recovery_generation'
                     AND conrelid='public.user_event_outbox'::regclass) THEN
        ALTER TABLE public.user_event_outbox
            ADD CONSTRAINT ck_user_event_outbox_recovery_generation
            CHECK (recovery_generation BETWEEN 0 AND 2);
    END IF;
END
$constraint$;

CREATE TABLE IF NOT EXISTS public.user_outbox_replay_approvals (
    approval_id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES public.user_event_outbox(event_id) ON DELETE RESTRICT,
    recovery_generation SMALLINT NOT NULL CHECK (recovery_generation BETWEEN 0 AND 1),
    ticket VARCHAR(48) NOT NULL,
    approved_by TEXT NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ NULL,
    UNIQUE (event_id, recovery_generation),
    CHECK (expires_at > approved_at)
);

CREATE TABLE IF NOT EXISTS public.user_outbox_replay_audit (
    audit_id UUID PRIMARY KEY,
    approval_id UUID NOT NULL UNIQUE
        REFERENCES public.user_outbox_replay_approvals(approval_id) ON DELETE RESTRICT,
    event_id UUID NOT NULL REFERENCES public.user_event_outbox(event_id) ON DELETE RESTRICT,
    ticket VARCHAR(48) NOT NULL,
    approved_by TEXT NOT NULL,
    executed_by TEXT NOT NULL,
    executed_at TIMESTAMPTZ NOT NULL,
    previous_generation SMALLINT NOT NULL,
    new_generation SMALLINT NOT NULL,
    CONSTRAINT ck_user_outbox_replay_separate_actor
        CHECK (approved_by <> executed_by),
    CONSTRAINT ck_user_outbox_replay_generation_increases
        CHECK (new_generation = previous_generation + 1)
);
CREATE INDEX IF NOT EXISTS ix_user_outbox_replay_audit_event
    ON public.user_outbox_replay_audit(event_id, executed_at);

CREATE OR REPLACE FUNCTION public.approve_parked_user_outbox_replay(
    p_event_id UUID, p_ticket TEXT
) RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog
AS $function$
DECLARE
    v_approval UUID := pg_catalog.gen_random_uuid();
    v_state TEXT;
    v_attempts INTEGER;
    v_generation SMALLINT;
BEGIN
    IF NOT pg_catalog.pg_has_role(session_user,'aetheris_outbox_approver','MEMBER')
       OR pg_catalog.pg_has_role(session_user,'aetheris_outbox_operator','MEMBER') THEN
        RAISE EXCEPTION 'Approver role required and duties must be separated'
            USING ERRCODE='42501';
    END IF;
    -- Ticket is a reference, NOT proof of external human approval by itself.
    IF p_event_id IS NULL OR p_ticket IS NULL OR
       p_ticket !~ '^(INC|SEC)-[0-9]{8}-[0-9]{4,8}$' THEN
        RAISE EXCEPTION 'Invalid event or external approval ticket reference'
            USING ERRCODE='22023';
    END IF;
    SELECT state,attempts,recovery_generation
       INTO v_state,v_attempts,v_generation
       FROM public.user_event_outbox
       WHERE event_id=p_event_id FOR UPDATE;
    IF NOT FOUND OR v_state <> 'parked' OR v_attempts < 12 OR v_generation >= 2 THEN
        RAISE EXCEPTION 'Event is not eligible for reviewed recovery'
            USING ERRCODE='55000';
    END IF;
    INSERT INTO public.user_outbox_replay_approvals
        (approval_id,event_id,recovery_generation,ticket,approved_by,approved_at,expires_at)
    VALUES
        (v_approval,p_event_id,v_generation,p_ticket,session_user,
         pg_catalog.clock_timestamp(),
         pg_catalog.clock_timestamp()+INTERVAL '15 minutes');
    RETURN v_approval;
END
$function$;

CREATE OR REPLACE FUNCTION public.rearm_approved_parked_user_outbox(
    p_event_id UUID
) RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog
AS $function$
DECLARE
    v_state TEXT;
    v_attempts INTEGER;
    v_generation SMALLINT;
    v_approval public.user_outbox_replay_approvals%ROWTYPE;
    v_audit UUID := pg_catalog.gen_random_uuid();
BEGIN
    IF NOT pg_catalog.pg_has_role(session_user,'aetheris_outbox_operator','MEMBER')
       OR pg_catalog.pg_has_role(session_user,'aetheris_outbox_approver','MEMBER') THEN
        RAISE EXCEPTION 'Operator role required and duties must be separated'
            USING ERRCODE='42501';
    END IF;
    IF p_event_id IS NULL THEN
        RAISE EXCEPTION 'Event ID is required' USING ERRCODE='22023';
    END IF;
    SELECT state,attempts,recovery_generation
       INTO v_state,v_attempts,v_generation
       FROM public.user_event_outbox WHERE event_id=p_event_id FOR UPDATE;
    IF NOT FOUND OR v_state <> 'parked' OR v_attempts < 12 OR v_generation >= 2 THEN
        RAISE EXCEPTION 'Event is not currently parked and eligible'
            USING ERRCODE='55000';
    END IF;
    SELECT * INTO v_approval FROM public.user_outbox_replay_approvals
       WHERE event_id=p_event_id AND recovery_generation=v_generation
         AND consumed_at IS NULL AND expires_at > pg_catalog.clock_timestamp()
       FOR UPDATE;
    IF NOT FOUND OR v_approval.approved_by=session_user THEN
        RAISE EXCEPTION 'Fresh independent approval required'
            USING ERRCODE='42501';
    END IF;
    -- Approval consumption, state transition and append-only audit are in
    -- exactly one database transaction. Repeated execution cannot succeed.
    UPDATE public.user_event_outbox
       SET state='retry', attempts=0,
           available_at=pg_catalog.clock_timestamp()+INTERVAL '5 seconds',
           recovery_generation=recovery_generation+1
       WHERE event_id=p_event_id AND state='parked' AND attempts >= 12
         AND recovery_generation=v_generation
         AND lease_token IS NULL AND lease_until IS NULL;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Recovery compare-and-set was not authorized'
            USING ERRCODE='55000';
    END IF;
    UPDATE public.user_outbox_replay_approvals
       SET consumed_at=pg_catalog.clock_timestamp()
       WHERE approval_id=v_approval.approval_id AND consumed_at IS NULL;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Approval already consumed' USING ERRCODE='55000';
    END IF;
    INSERT INTO public.user_outbox_replay_audit
        (audit_id,approval_id,event_id,ticket,approved_by,executed_by,
         executed_at,previous_generation,new_generation)
    VALUES
        (v_audit,v_approval.approval_id,p_event_id,v_approval.ticket,
         v_approval.approved_by,session_user,pg_catalog.clock_timestamp(),
         v_generation,v_generation+1);
    RETURN v_audit;
END
$function$;

-- PL/pgSQL SECURITY DEFINER functions otherwise inherit PUBLIC EXECUTE.
-- Deny PUBLIC, grant only corresponding NOLOGIN role capabilities.
REVOKE ALL ON FUNCTION public.approve_parked_user_outbox_replay(UUID,TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.rearm_approved_parked_user_outbox(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.approve_parked_user_outbox_replay(UUID,TEXT)
    TO aetheris_outbox_approver;
GRANT EXECUTE ON FUNCTION public.rearm_approved_parked_user_outbox(UUID)
    TO aetheris_outbox_operator;
COMMIT;
