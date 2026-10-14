#![forbid(unsafe_code)]
//! Native, review-gated Syntra SSE client for the NEW durable journal protocol.
//!
//! This is not wired into Tauri, JS, the WinCred vault or production services.
//! The two types required to authorize a network request have NO public
//! constructors in this milestone. Only crate-local tests can create them.
//! Never use /api/orchestrator/live/events or old /tasks/.../events.
//!
//! An event is a persisted journal claim, not evidence of an AI action,
//! actual tool execution, human consent, or successful desktop receipt.

pub mod async_transport;
pub mod session_lifecycle;

use reqwest::blocking::Client;
use reqwest::header::{HeaderValue, ACCEPT, AUTHORIZATION, CONTENT_TYPE};
use reqwest::redirect::Policy;
use serde::Deserialize;
use std::io::Read;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;
use zeroize::Zeroizing;

pub const MAX_SSE_FRAME: usize = 16 * 1024;
pub const MAX_SSE_BUFFER: usize = 32 * 1024;
pub const MAX_RESPONSE_BYTES: usize = 128 * 1024;
pub const MAX_EVENTS_PER_CONNECTION: usize = 100;
pub const LEGACY_GLOBAL_ROUTE: &str = "/api/orchestrator/live/events";
pub const PHYSICAL_OWNER_STATUS: &str = "BLOCKED_PENDING_HARDWARE";
pub const LIVE_ACTIVATION_STATUS: &str = "BLOCKED_NATIVE_AUTHENTICATED_SESSION_NOT_WIRED";

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum NativeError {
    MissingNativeSession,
    InvalidCheckpoint,
    InvalidTask,
    InvalidToken,
    InvalidStream,
    CrossTaskEvent,
    InvalidSequence,
    TooLarge,
    UnsupportedOrigin,
    AccessDenied,
    Unavailable,
    Stopped,
    SessionExpired,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum EventProvenance {
    ServerJournalClaimNotVerifiedExecution,
}

/// Only sanitized owner-scoped event fields; no arbitrary event metadata.
#[derive(Debug, PartialEq, Eq)]
pub struct MissionUpdate {
    pub event_id: String,
    pub task_id: String,
    pub sequence: u64,
    pub timestamp: String,
    pub state: String,
    pub agent_id: Option<String>,
    pub provenance: EventProvenance,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct WireUpdate {
    event_id: String,
    task_id: String,
    sequence: u64,
    event_at: String,
    state: String,
    agent_id: Option<String>,
    provenance: String,
}

#[derive(Default)]
struct Frame {
    event: Option<String>,
    id: Option<String>,
    data: Option<Vec<u8>>,
    size: usize,
}

/// Strict chunked UTF-8 SSE framing, distinct from the legacy "task-event"
/// parser. These bytes are untrusted until the native transport and server
/// ownership have been independently certified.
pub struct JournalFrameDecoder {
    selected_task: String,
    buffer: Vec<u8>,
    frame: Frame,
    last_sequence: u64,
    last_event_id: Option<String>,
}

impl JournalFrameDecoder {
    pub fn new(selected_task: &str, last_declared_ack: u64) -> Result<Self, NativeError> {
        if !uuid(selected_task) {
            return Err(NativeError::InvalidTask);
        }
        Ok(Self {
            selected_task: selected_task.to_ascii_lowercase(),
            buffer: Vec::new(),
            frame: Frame::default(),
            last_sequence: last_declared_ack,
            last_event_id: None,
        })
    }

    pub fn push(&mut self, bytes: &[u8]) -> Result<Vec<MissionUpdate>, NativeError> {
        if bytes.len() > MAX_SSE_BUFFER || self.buffer.len().saturating_add(bytes.len()) > MAX_SSE_BUFFER {
            self.clear_frame();
            return Err(NativeError::TooLarge);
        }
        self.buffer.extend_from_slice(bytes);
        let mut accepted = Vec::new();
        while let Some(pos) = self.buffer.iter().position(|b| *b == b'\n') {
            let mut line: Vec<u8> = self.buffer.drain(..=pos).collect();
            line.pop();
            if line.last() == Some(&b'\r') { line.pop(); }
            if self.frame.size.saturating_add(line.len()) > MAX_SSE_FRAME {
                self.clear_frame();
                return Err(NativeError::TooLarge);
            }
            self.frame.size += line.len();
            if line.is_empty() {
                let parsed = self.complete();
                match parsed {
                    Ok(Some(event)) => accepted.push(event),
                    Ok(None) => (),
                    Err(error) => { self.clear_frame(); return Err(error); }
                }
                continue;
            }
            if line[0] == b':' { continue; } // heartbeat, not a trust signal
            let line = std::str::from_utf8(&line).map_err(|_| NativeError::InvalidStream)?;
            let (field, raw) = line.split_once(':').ok_or(NativeError::InvalidStream)?;
            let value = raw.strip_prefix(' ').unwrap_or(raw);
            match field {
                "event" if self.frame.event.is_none() && value.len() <= 40 =>
                    self.frame.event = Some(value.to_owned()),
                "id" if self.frame.id.is_none() && value.len() <= 80 =>
                    self.frame.id = Some(value.to_owned()),
                "data" if self.frame.data.is_none() =>
                    self.frame.data = Some(value.as_bytes().to_vec()),
                _ => { self.clear_frame(); return Err(NativeError::InvalidStream); }
            }
        }
        if self.buffer.len().saturating_add(self.frame.size) > MAX_SSE_FRAME {
            self.clear_frame();
            return Err(NativeError::TooLarge);
        }
        Ok(accepted)
    }

    fn complete(&mut self) -> Result<Option<MissionUpdate>, NativeError> {
        let frame = std::mem::take(&mut self.frame);
        if frame.event.is_none() && frame.id.is_none() && frame.data.is_none() {
            return Ok(None);
        }
        if frame.event.as_deref() != Some("task-recorded") {
            return Err(NativeError::InvalidStream);
        }
        let id = frame.id.ok_or(NativeError::InvalidStream)?;
        if !uuid(&id) { return Err(NativeError::InvalidStream); }
        let data: WireUpdate = serde_json::from_slice(
            &frame.data.ok_or(NativeError::InvalidStream)?)
            .map_err(|_| NativeError::InvalidStream)?;
        if data.task_id.to_ascii_lowercase() != self.selected_task {
            return Err(NativeError::CrossTaskEvent);
        }
        if !uuid(&data.task_id) || !uuid(&data.event_id)
            || !id.eq_ignore_ascii_case(&data.event_id)
            || !timestamp(&data.event_at)
            || !matches!(data.state.as_str(),
                "QUEUED" | "PLANNING" | "AWAITING_APPROVAL" |
                "RUNNING" | "VERIFYING" | "PAUSED" | "ROLLING_BACK" |
                "COMPLETED" | "FAILED" | "CANCELLED")
            || data.agent_id.as_deref().is_some_and(|id| !agent(id))
            || data.provenance != "PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME"
        {
            return Err(NativeError::InvalidStream);
        }
        if data.sequence <= self.last_sequence {
            // Only a literal last-event duplicate can be silently ignored.
            if data.sequence == self.last_sequence &&
                self.last_event_id.as_deref().is_some_and(
                    |prior| prior.eq_ignore_ascii_case(&data.event_id)) {
                return Ok(None);
            }
            return Err(NativeError::InvalidSequence);
        }
        if self.last_sequence == u64::MAX || data.sequence != self.last_sequence + 1 {
            return Err(NativeError::InvalidSequence);
        }
        self.last_sequence = data.sequence;
        self.last_event_id = Some(data.event_id.to_ascii_lowercase());
        Ok(Some(MissionUpdate {
            event_id: data.event_id.to_ascii_lowercase(),
            task_id: self.selected_task.clone(),
            sequence: data.sequence,
            timestamp: data.event_at,
            state: data.state,
            agent_id: data.agent_id,
            provenance: EventProvenance::ServerJournalClaimNotVerifiedExecution,
        }))
    }

    fn clear_frame(&mut self) {
        self.buffer.clear();
        self.frame = Frame::default();
    }
}

fn uuid(input: &str) -> bool {
    let bytes = input.as_bytes();
    bytes.len() == 36 && bytes.iter().enumerate().all(|(i, b)| {
        if [8,13,18,23].contains(&i) { *b == b'-' }
        else { b.is_ascii_hexdigit() }
    })
}
fn agent(input: &str) -> bool {
    !input.is_empty() && input.len() <= 80 &&
    input.as_bytes().iter().enumerate().all(|(i,b)| {
        b.is_ascii_lowercase() || b.is_ascii_digit() || (i > 0 && *b == b'-')
    })
}
fn timestamp(input: &str) -> bool {
    let bytes = input.as_bytes();
    (20..=40).contains(&bytes.len()) && bytes[4] == b'-'
        && bytes[7] == b'-' && bytes[10] == b'T'
        && bytes[13] == b':' && bytes[16] == b':'
        && bytes.last() == Some(&b'Z')
        && bytes[0..4].iter().all(u8::is_ascii_digit)
        && bytes[5..7].iter().all(u8::is_ascii_digit)
        && bytes[8..10].iter().all(u8::is_ascii_digit)
        && bytes[11..13].iter().all(u8::is_ascii_digit)
        && bytes[14..16].iter().all(u8::is_ascii_digit)
        && bytes[17..19].iter().all(u8::is_ascii_digit)
        && {
            let fraction = &bytes[19..bytes.len()-1];
            fraction.is_empty() || (fraction[0] == b'.' && fraction.len() > 1
                && fraction[1..].iter().all(u8::is_ascii_digit))
        }
}

/// A native access token can ONLY be minted from the future audited
/// Windows Credential Manager + signed identity session bridge.
/// No public constructor and no Tauri IPC exposure are provided.
pub struct NativeAccessToken(Zeroizing<String>);
impl std::fmt::Debug for NativeAccessToken {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("NativeAccessToken([REDACTED])")
    }
}

pub struct VerifiedNativeSession {
    task_id: String,
    checkpoint_id: String,
    last_ack_sequence: u64,
}
impl std::fmt::Debug for VerifiedNativeSession {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("VerifiedNativeSession([REDACTED])")
    }
}

pub struct LoopbackMissionClient {
    port: u16,
    client: Client,
    stopped: AtomicBool,
}

impl LoopbackMissionClient {
    /// The ONLY permissible origin at this stage is numeric IPv4 loopback.
    /// No DNS, proxy, HTTP redirect, user-supplied URL or token query param.
    pub fn new_loopback(port: u16) -> Result<Self, NativeError> {
        if port == 0 { return Err(NativeError::UnsupportedOrigin); }
        let client = Client::builder()
            .no_proxy()
            .redirect(Policy::none())
            .connect_timeout(Duration::from_secs(2))
            .timeout(Duration::from_secs(35))
            .build().map_err(|_| NativeError::Unavailable)?;
        Ok(Self { port, client, stopped: AtomicBool::new(false) })
    }

    pub fn stop(&self) { self.stopped.store(true, Ordering::SeqCst); }
    pub fn is_stopped(&self) -> bool { self.stopped.load(Ordering::SeqCst) }

    /// Bounded network read of one SSE connection; NO automatic retries,
    /// durable ACK, token refresh, UI event or task action. Only a trusted
    /// future native session issuer can provide the necessary private types.
    pub fn read_once(
        &self, session: &VerifiedNativeSession, token: &NativeAccessToken,
    ) -> Result<Vec<MissionUpdate>, NativeError> {
        if self.is_stopped() { return Err(NativeError::Stopped); }
        if !uuid(&session.task_id) || !uuid(&session.checkpoint_id) {
            return Err(NativeError::InvalidCheckpoint);
        }
        let auth = format!("Bearer {}", token.0.as_str());
        let mut header = HeaderValue::from_str(&auth).map_err(|_| NativeError::InvalidToken)?;
        header.set_sensitive(true);
        let url = format!("http://127.0.0.1:{}/api/orchestrator/owner-checkpoints/{}/stream",
            self.port, session.checkpoint_id);
        let mut response = self.client.get(url).header(ACCEPT, "text/event-stream")
            .header(AUTHORIZATION, header).send()
            .map_err(|_| NativeError::Unavailable)?;
        if self.is_stopped() { return Err(NativeError::Stopped); }
        if !response.status().is_success() {
            return if matches!(response.status().as_u16(), 401 | 403 | 404) {
                Err(NativeError::AccessDenied)
            } else { Err(NativeError::Unavailable) };
        }
        let mime = response.headers().get(CONTENT_TYPE)
            .and_then(|value| value.to_str().ok()).unwrap_or("");
        if mime.split(';').next() != Some("text/event-stream") {
            return Err(NativeError::InvalidStream);
        }
        let mut decoder = JournalFrameDecoder::new(
            &session.task_id, session.last_ack_sequence)?;
        let mut updates = Vec::new();
        let mut total = 0usize;
        let mut chunk = [0u8; 4096];
        loop {
            if self.is_stopped() { return Err(NativeError::Stopped); }
            let count = response.read(&mut chunk).map_err(|_| NativeError::Unavailable)?;
            if count == 0 { break; }
            total = total.saturating_add(count);
            if total > MAX_RESPONSE_BYTES { return Err(NativeError::TooLarge); }
            let events = decoder.push(&chunk[..count])?;
            if updates.len().saturating_add(events.len()) > MAX_EVENTS_PER_CONNECTION {
                return Err(NativeError::TooLarge);
            }
            updates.extend(events);
        }
        if self.is_stopped() { return Err(NativeError::Stopped); }
        Ok(updates)
    }
}

/// A bounded retry suggestion, not a background loop. Auth/owner denial,
/// malformed stream, sequence gaps and STOP never trigger blind reconnect.
pub fn retry_delay(attempt: u8, failure: NativeError) -> Option<Duration> {
    if !matches!(failure, NativeError::Unavailable) || attempt >= 5 {
        return None;
    }
    Some(Duration::from_secs(1u64 << attempt.min(4)))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{Read, Write};
    use std::net::TcpListener;
    use std::thread;

    const TASK: &str = "11aa11aa-1111-4111-8111-111111111111";
    const FOREIGN: &str = "22bb22bb-2222-4222-8222-222222222222";
    const CHECKPOINT: &str = "33cc33cc-3333-4333-8333-333333333333";
    const EVENT: &str = "44dd44dd-4444-4444-8444-444444444444";
    const TOKEN: &str = "synthetic.header.signature";

    fn frame(task: &str, sequence: u64) -> Vec<u8> {
        format!("event:task-recorded\nid:{EVENT}\ndata:{{\"eventId\":\"{EVENT}\",\"taskId\":\"{task}\",\"sequence\":{sequence},\"eventAt\":\"2026-10-14T12:00:00Z\",\"state\":\"RUNNING\",\"agentId\":\"qa-engineer\",\"provenance\":\"PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME\"}}\n\n").into_bytes()
    }
    fn test_session() -> VerifiedNativeSession {
        VerifiedNativeSession { task_id: TASK.into(), checkpoint_id: CHECKPOINT.into(),
            last_ack_sequence: 0 }
    }
    fn test_token() -> NativeAccessToken {
        NativeAccessToken(Zeroizing::new(TOKEN.into()))
    }
    fn server(status: &str, content_type: &str, body: Vec<u8>) -> (u16, thread::JoinHandle<String>) {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let port = listener.local_addr().unwrap().port();
        let status = status.to_owned();
        let content_type = content_type.to_owned();
        let h = thread::spawn(move || {
            let (mut socket, _) = listener.accept().unwrap();
            socket.set_read_timeout(Some(Duration::from_secs(3))).unwrap();
            let mut buf = [0u8; 4096];
            let mut request_bytes = Vec::new();
            while !request_bytes.windows(4).any(|w| w == b"\r\n\r\n") {
                let read = socket.read(&mut buf).unwrap();
                if read == 0 || request_bytes.len() + read > 8192 { break; }
                request_bytes.extend_from_slice(&buf[..read]);
            }
            let request = String::from_utf8(request_bytes).unwrap();
            let reply = format!("HTTP/1.1 {status}\r\nContent-Type: {content_type}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n", body.len());
            socket.write_all(reply.as_bytes()).unwrap();
            socket.write_all(&body).unwrap();
            request
        });
        (port, h)
    }

    #[test]
    fn parses_new_backend_contract_in_fragments_with_no_payload_escape() {
        let mut d = JournalFrameDecoder::new(TASK, 0).unwrap();
        let first = frame(TASK, 1);
        assert!(d.push(&first[..30]).unwrap().is_empty());
        let result = d.push(&first[30..]).unwrap();
        assert_eq!(result.len(), 1);
        assert_eq!(result[0].event_id, EVENT);
        assert_eq!(result[0].sequence, 1);
        assert_eq!(result[0].provenance, EventProvenance::ServerJournalClaimNotVerifiedExecution);
        assert!(!format!("{result:?}").contains("accessToken"));
        assert!(d.push(&first).unwrap().is_empty()); // exact duplicate
    }
    #[test]
    fn foreign_task_wrong_provenance_and_gaps_fail_closed() {
        let mut d=JournalFrameDecoder::new(TASK,0).unwrap();
        assert_eq!(d.push(&frame(FOREIGN,1)).unwrap_err(),NativeError::CrossTaskEvent);
        let mut d=JournalFrameDecoder::new(TASK,0).unwrap();
        assert_eq!(d.push(&frame(TASK,3)).unwrap_err(),NativeError::InvalidSequence);
        let mut wrong=String::from_utf8(frame(TASK,1)).unwrap();
        wrong=wrong.replace("PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME","VERIFIED_AGENT_EXECUTION");
        let mut d=JournalFrameDecoder::new(TASK,0).unwrap();
        assert_eq!(d.push(wrong.as_bytes()).unwrap_err(),NativeError::InvalidStream);
        let mut leaked=String::from_utf8(frame(TASK,1)).unwrap();
        leaked=leaked.replace("\"provenance\"", "\"secret\":\"private-token\",\"provenance\"");
        let mut d=JournalFrameDecoder::new(TASK,0).unwrap();
        assert_eq!(d.push(leaked.as_bytes()).unwrap_err(),NativeError::InvalidStream);
    }
    #[test]
    fn oversized_legacy_and_mismatched_frame_id_refused() {
        let mut d=JournalFrameDecoder::new(TASK,0).unwrap();
        assert_eq!(d.push(&vec![b'A';MAX_SSE_BUFFER+1]).unwrap_err(),NativeError::TooLarge);
        let old=String::from_utf8(frame(TASK,1)).unwrap().replace("task-recorded","task-event");
        let mut d=JournalFrameDecoder::new(TASK,0).unwrap();
        assert_eq!(d.push(old.as_bytes()).unwrap_err(),NativeError::InvalidStream);
        let other=String::from_utf8(frame(TASK,1)).unwrap()
            .replace(&format!("id:{EVENT}"),&format!("id:{FOREIGN}"));
        let mut d=JournalFrameDecoder::new(TASK,0).unwrap();
        assert_eq!(d.push(other.as_bytes()).unwrap_err(),NativeError::InvalidStream);
    }
    #[test]
    fn synthetic_native_transport_sends_bearer_header_to_pinned_loopback_only() {
        let (port, server)=server("200 OK","text/event-stream; charset=UTF-8",frame(TASK,1));
        let client=LoopbackMissionClient::new_loopback(port).unwrap();
        let output=client.read_once(&test_session(),&test_token()).unwrap();
        assert_eq!(output.len(),1);
        assert_eq!(output[0].sequence,1);
        let request=server.join().unwrap();
        assert!(request.starts_with(&format!(
            "GET /api/orchestrator/owner-checkpoints/{CHECKPOINT}/stream HTTP/1.1")));
        assert!(request.contains(&format!("Bearer {TOKEN}")));
        assert!(!request.contains("Last-Event-ID:"));
        assert!(!request.contains("X-Aetheris-User-Id:"));
        assert!(!format!("{:?}", test_token()).contains(TOKEN));
    }
    #[test]
    fn failed_auth_and_wrong_content_type_are_never_promoted() {
        let (port, forbidden_server)=server("403 Forbidden","application/json",b"{}".to_vec());
        let client=LoopbackMissionClient::new_loopback(port).unwrap();
        assert_eq!(client.read_once(&test_session(),&test_token()).unwrap_err(),
            NativeError::AccessDenied);
        forbidden_server.join().unwrap();
        let (port, json_server)=server("200 OK","application/json",frame(TASK,1));
        let client=LoopbackMissionClient::new_loopback(port).unwrap();
        assert_eq!(client.read_once(&test_session(),&test_token()).unwrap_err(),
            NativeError::InvalidStream);
        json_server.join().unwrap();
    }
    #[test]
    fn stop_and_retry_bounds_are_fail_closed() {
        assert_eq!(LoopbackMissionClient::new_loopback(0).err(),
            Some(NativeError::UnsupportedOrigin));
        let client=LoopbackMissionClient::new_loopback(8090).unwrap();
        client.stop();
        assert_eq!(client.read_once(&test_session(),&test_token()).unwrap_err(),NativeError::Stopped);
        assert_eq!(retry_delay(0,NativeError::Unavailable),Some(Duration::from_secs(1)));
        assert_eq!(retry_delay(4,NativeError::Unavailable),Some(Duration::from_secs(16)));
        assert_eq!(retry_delay(5,NativeError::Unavailable),None);
        assert_eq!(retry_delay(0,NativeError::AccessDenied),None);
        assert_eq!(retry_delay(0,NativeError::InvalidSequence),None);
    }
}
