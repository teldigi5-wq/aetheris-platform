-- Seed one parked and one delivered event, including a consumed approval chain.
INSERT INTO public.user_event_outbox
 (event_id,event_type,occurred_at,user_id,name,email,tenant_id,
  state,attempts,available_at,recovery_generation) VALUES
 ('11111111-1111-4111-8111-111111111111','user.created',
  '2026-11-06T00:00:00Z',9001,'Synthetic parked','dr-parked@example.test',
  NULL,'parked',12,'2026-11-06T00:00:00Z',1),
 ('22222222-2222-4222-8222-222222222222','user.deleted',
  '2026-11-06T00:00:00Z',9002,'Synthetic delivered','dr-delivered@example.test',
  'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','delivered',1,
  '2026-11-06T00:00:00Z',0);
INSERT INTO public.user_outbox_replay_approvals
 (approval_id,event_id,recovery_generation,ticket,approved_by,approved_at,
  expires_at,consumed_at) VALUES
 ('33333333-3333-4333-8333-333333333333',
  '11111111-1111-4111-8111-111111111111',0,'SEC-20261106-9001',
  'synthetic_dr_approver','2026-11-06T00:00:00Z',
  '2026-11-06T00:15:00Z','2026-11-06T00:05:00Z');
INSERT INTO public.user_outbox_replay_audit
 (audit_id,approval_id,event_id,ticket,approved_by,executed_by,executed_at,
  previous_generation,new_generation) VALUES
 ('44444444-4444-4444-8444-444444444444',
  '33333333-3333-4333-8333-333333333333',
  '11111111-1111-4111-8111-111111111111',
  'SEC-20261106-9001','synthetic_dr_approver',
  'synthetic_dr_operator','2026-11-06T00:05:00Z',0,1);
