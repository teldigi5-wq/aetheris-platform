-- SYNTHETIC CI-ONLY rollback illustration. This is NOT a production rollback.
-- Refuse every database except the two specific disposable DR test databases.
-- Any event/recovery data => fail closed, retaining all original evidence.
BEGIN;
DO $guard$
BEGIN
    IF current_database() NOT IN ('xerj_dr_restored', 'xerj_dr_empty') THEN
        RAISE EXCEPTION 'Refuse rollback outside dedicated disposable DR databases'
            USING ERRCODE = '42501';
    END IF;
    IF current_user <> 'xerj_ci' THEN
        RAISE EXCEPTION 'Refuse rollback without disposable bootstrap owner'
            USING ERRCODE = '42501';
    END IF;
END
$guard$;
LOCK TABLE public.user_event_outbox,
           public.user_outbox_replay_approvals,
           public.user_outbox_replay_audit IN ACCESS EXCLUSIVE MODE;
DO $guard$
BEGIN
    IF EXISTS (SELECT 1 FROM public.user_event_outbox LIMIT 1)
       OR EXISTS (SELECT 1 FROM public.user_outbox_replay_approvals LIMIT 1)
       OR EXISTS (SELECT 1 FROM public.user_outbox_replay_audit LIMIT 1) THEN
        RAISE EXCEPTION 'Refuse destructive schema rollback while outbox or recovery evidence exists'
            USING ERRCODE = '55000';
    END IF;
END
$guard$;
DROP FUNCTION public.rearm_approved_parked_user_outbox(UUID);
DROP FUNCTION public.approve_parked_user_outbox_replay(UUID, TEXT);
DROP TABLE public.user_outbox_replay_audit;
DROP TABLE public.user_outbox_replay_approvals;
DROP TABLE public.user_event_outbox;
ALTER TABLE public.users DROP COLUMN tenant_id;
ALTER TABLE public.identity_accounts DROP COLUMN tenant_id;
COMMIT;
