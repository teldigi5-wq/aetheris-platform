//! Fail-closed owner mission visibility boundary.
 //!
 //! The external Aetheris runtime currently has a global SSE stream that calls
 //! subscribeAll(), and TaskEntity carries no owner identity. A bearer token with
 //! orchestrator:read by itself is NOT sufficient proof that individual task
 //! events belong to the desktop owner.
 //!
 //! This production module cannot mint an OwnerGrant or connect to HTTP/Tauri.
 //! Test-only grants exercise the eventual task-by-task filtering behavior.
 //! Do not connect to real SSE until the backend enforces owner + project scope
 //! and a separately audited native verifier can issue an opaque grant.

use std::collections::HashSet;
use syntra_native_mission_stream::{DecodeError, TaskEventDecoder, TaskProgress};

pub const MAX_OWNER_TASKS: usize = 24;
pub const MAX_ROUTE_CHARS: usize = 100;
pub const AGGREGATE_ROUTE: &str = "/api/orchestrator/live/events";
pub const HARDWARE_STATUS: &str = "BLOCKED_PENDING_HARDWARE";
pub const BACKEND_STATUS: &str = "BLOCKED_OWNER_BOUND_TASK_ISOLATION";

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Denied {
    RuntimeOwnerMappingMissing,
    GlobalFeedForbidden,
    SessionExpired,
    SessionRevoked,
    TaskNotAuthorized,
    UnsupportedEndpoint,
    MalformedEvent,
}

struct OwnerGrant {
    // These fields can only come from a future backend owner-binding verifier,
    // never from JS, a filename, a token payload or untrusted SSE bytes.
    owner_id: String,
    task_ids: HashSet<String>,
    expires_unix: u64,
}

pub struct MissionOwnerGate {
    grant: Option<OwnerGrant>,
    stream: TaskEventDecoder,
    revoked: bool,
}

impl Default for MissionOwnerGate {
    fn default() -> Self { Self::new_pre_pc() }
}

impl MissionOwnerGate {
    /// This is the ONLY production constructor. No backend ownership proof
    /// is available and all reads remain blocked, even if a JWT is present.
    pub fn new_pre_pc() -> Self {
        Self { grant: None, stream: TaskEventDecoder::new(), revoked: false }
    }

    pub fn status_code(&self) -> &'static str {
        if self.revoked { "BLOCKED_SESSION_REVOKED" }
        else { BACKEND_STATUS }
    }

    pub fn allow_live_events(&self) -> bool {
        // Never promote parsed bytes to trusted owner events on this build.
        false
    }

    pub fn route(&self, method: &str, path: &str, now_unix: u64) -> Result<(), Denied> {
        if self.revoked { return Err(Denied::SessionRevoked); }
        if path == AGGREGATE_ROUTE { return Err(Denied::GlobalFeedForbidden); }
        let grant = self.grant.as_ref().ok_or(Denied::RuntimeOwnerMappingMissing)?;
        if now_unix >= grant.expires_unix { return Err(Denied::SessionExpired); }
        if method != "GET" || path.len() > MAX_ROUTE_CHARS {
            return Err(Denied::UnsupportedEndpoint);
        }
        let prefix = "/api/orchestrator/tasks/";
        let suffix = "/events";
        let Some(id) = path.strip_prefix(prefix).and_then(|p| p.strip_suffix(suffix)) else {
            return Err(Denied::UnsupportedEndpoint);
        };
        if !valid_uuid(id) { return Err(Denied::UnsupportedEndpoint); }
        if !grant.task_ids.contains(&id.to_ascii_lowercase()) {
            return Err(Denied::TaskNotAuthorized);
        }
        Ok(())
    }

    /// Strict filtering after an *already trusted* owner-scope verifier.
    /// Actual production gate has no grant so it cannot parse live events.
    pub fn ingest(&mut self, route: &str, chunk: &[u8], now_unix: u64)
        -> Result<Vec<TaskProgress>, Denied> {
        self.route("GET", route, now_unix)?;
        let progress = self.stream.push(chunk).map_err(|_| Denied::MalformedEvent)?;
        let grant = self.grant.as_ref().ok_or(Denied::RuntimeOwnerMappingMissing)?;
        let expected = route
            .strip_prefix("/api/orchestrator/tasks/")
            .and_then(|p| p.strip_suffix("/events"))
            .ok_or(Denied::UnsupportedEndpoint)?
            .to_ascii_lowercase();
        // Refuse the *entire* chunk if any event is inconsistent with
        // the selected stream or grant. Never surface partial valid-looking data.
        if progress.iter().any(|e| e.task_id != expected ||
            !grant.task_ids.contains(&e.task_id)) {
            self.revoke();
            return Err(Denied::TaskNotAuthorized);
        }
        // TaskProgress provenance stays UnauthenticatedBytes. Native owner
        // verification is a different step, currently not wired.
        Ok(progress)
    }

    pub fn revoke(&mut self) {
        self.grant = None;
        self.stream = TaskEventDecoder::new();
        self.revoked = true;
    }

    #[cfg(test)]
    fn synthetic_owner_grant(owner: &str, tasks: &[&str], expires_unix: u64) -> Self {
        assert!(!owner.trim().is_empty());
        assert!(!tasks.is_empty() && tasks.len() <= MAX_OWNER_TASKS);
        let ids = tasks.iter().map(|id| {
            assert!(valid_uuid(id));
            id.to_ascii_lowercase()
        }).collect();
        let grant = OwnerGrant {owner_id: owner.into(), task_ids: ids, expires_unix};
        Self {grant: Some(grant), stream: TaskEventDecoder::new(), revoked: false}
    }
}

fn valid_uuid(value: &str) -> bool {
    let raw = value.as_bytes();
    raw.len()==36 && raw.iter().enumerate().all(|(i,b)| {
        if [8,13,18,23].contains(&i) { *b == b'-' } else { b.is_ascii_hexdigit() }
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use syntra_native_mission_stream::Provenance;

    const A: &str = "11aa11aa-1111-4111-8111-111111111111";
    const B: &str = "22bb22bb-2222-4222-8222-222222222222";
    const A_ROUTE: &str = "/api/orchestrator/tasks/11aa11aa-1111-4111-8111-111111111111/events";
    const B_ROUTE: &str = "/api/orchestrator/tasks/22bb22bb-2222-4222-8222-222222222222/events";

    fn frame(id: &str, stamp: &str) -> Vec<u8> {
        format!("event: task-event\nid: {id}:{stamp}\ndata: {{\"taskId\":\"{id}\",\"timestamp\":\"{stamp}\",\"state\":\"RUNNING\",\"agentId\":\"qa-engineer\",\"message\":\"TOP_SECRET\",\"metadata\":{{\"refreshToken\":\"SENSITIVE\"}}}}\n\n").into_bytes()
    }

    #[test]
    fn pre_pc_gate_denies_every_live_or_synthetic_request() {
        let mut gate = MissionOwnerGate::new_pre_pc();
        assert_eq!(gate.status_code(), BACKEND_STATUS);
        assert!(!gate.allow_live_events());
        assert_eq!(gate.route("GET",AGGREGATE_ROUTE,1),Err(Denied::GlobalFeedForbidden));
        assert_eq!(gate.route("GET",A_ROUTE,1),Err(Denied::RuntimeOwnerMappingMissing));
        assert_eq!(gate.ingest(A_ROUTE,&frame(A,"2026-10-13T14:00:00Z"),1),
            Err(Denied::RuntimeOwnerMappingMissing));
    }

    #[test]
    fn hypothetical_owner_grant_only_exposes_its_exact_task() {
        let mut gate = MissionOwnerGate::synthetic_owner_grant("owner-a",&[A],300);
        assert_eq!(gate.route("GET",A_ROUTE,200),Ok(()));
        assert_eq!(gate.route("GET",B_ROUTE,200),Err(Denied::TaskNotAuthorized));
        assert_eq!(gate.route("GET",AGGREGATE_ROUTE,200),Err(Denied::GlobalFeedForbidden));
        let events = gate.ingest(A_ROUTE,&frame(A,"2026-10-13T14:00:00Z"),200).unwrap();
        assert_eq!(events.len(),1);
        assert_eq!(events[0].task_id,A);
        assert_eq!(events[0].provenance, Provenance::UnauthenticatedBytes);
        assert!(!format!("{events:?}").contains("TOP_SECRET"));
        assert!(!format!("{events:?}").contains("SENSITIVE"));
        assert!(!gate.allow_live_events());
    }

    #[test]
    fn cross_task_event_in_correct_stream_revokes_owner_grant() {
        let mut gate = MissionOwnerGate::synthetic_owner_grant("owner-a",&[A,B],300);
        assert_eq!(gate.ingest(A_ROUTE,&frame(B,"2026-10-13T14:00:00Z"),200),
            Err(Denied::TaskNotAuthorized));
        assert_eq!(gate.route("GET",A_ROUTE,200),Err(Denied::SessionRevoked));
        assert_eq!(gate.status_code(),"BLOCKED_SESSION_REVOKED");
    }

    #[test]
    fn stop_and_logout_clear_progress_and_revoke_future_access() {
        let mut gate = MissionOwnerGate::synthetic_owner_grant("owner-a",&[A],300);
        assert_eq!(gate.ingest(A_ROUTE,&frame(A,"2026-10-13T14:00:00Z"),200).unwrap().len(),1);
        gate.revoke();
        assert_eq!(gate.ingest(A_ROUTE,&frame(A,"2026-10-13T14:00:01Z"),201),
            Err(Denied::SessionRevoked));
        assert!(!gate.allow_live_events());
    }

    #[test]
    fn scope_expiry_blocks_at_exact_expiration_second() {
        let mut gate = MissionOwnerGate::synthetic_owner_grant("owner-a",&[A],300);
        assert_eq!(gate.route("GET",A_ROUTE,299),Ok(()));
        assert_eq!(gate.ingest(A_ROUTE,&frame(A,"2026-10-13T14:00:00Z"),300),
            Err(Denied::SessionExpired));
    }

    #[test]
    fn off_scope_post_query_encoding_and_global_route_are_denied() {
        let gate=MissionOwnerGate::synthetic_owner_grant("owner-a",&[A],300);
        for path in [AGGREGATE_ROUTE] {
            assert_eq!(gate.route("GET",path,200),Err(Denied::GlobalFeedForbidden));
        }
        for path in ["/api/orchestrator/tasks", "/api/orchestrator/tasks/../events",
            "/api/orchestrator/tasks/11aa11aa-1111-4111-8111-111111111111/events?all=1",
            "/api/orchestrator/tasks/11aa11aa-1111-4111-8111-111111111111/events/../x",
            "/api/orchestrator/tasks/11aa11aa-1111-4111-8111-111111111111/events#access"] {
            assert_eq!(gate.route("GET",path,200),Err(Denied::UnsupportedEndpoint));
        }
        assert_eq!(gate.route("POST",A_ROUTE,200),Err(Denied::UnsupportedEndpoint));
    }
}
