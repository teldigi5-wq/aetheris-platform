//! Source-verified, *untrusted* SSE decoder for the external Aetheris runtime.
//! 
//! Does NOT fetch, authenticate, persist, connect to Tauri, or execute work.
//! An audited native authenticated network adapter must provide origin/session
//! authorization BEFORE any parsed event may be promoted to a trusted UI update.

use serde::Deserialize;
use std::collections::{HashSet, VecDeque};

pub const MAX_FRAME_BYTES: usize = 16 * 1024;
pub const MAX_BUFFER_BYTES: usize = 32 * 1024;
pub const MAX_SEEN_IDS: usize = 1024;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum DecodeError {
    Oversized,
    InvalidUtf8,
    InvalidFrame,
    InvalidEvent,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Provenance {
    // A parsed frame is NOT evidence of authentication or of executed work.
    UnauthenticatedBytes,
}

#[derive(Debug, PartialEq, Eq)]
pub struct TaskProgress {
    pub task_id: String,
    pub timestamp: String,
    pub state: String,
    pub agent_id: Option<String>,
    pub provenance: Provenance,
}

#[derive(Deserialize)]
struct BorrowedTaskEvent<'a> {
    #[serde(rename = "taskId")]
    task_id: &'a str,
    timestamp: &'a str,
    state: &'a str,
    #[serde(rename = "agentId", default)]
    agent_id: Option<&'a str>,
    // Deliberately do not deserialize `message`, `metadata`, task commands,
    // credentials, output, or other arbitrary untrusted fields.
}

#[derive(Default)]
struct Frame {
    event: Option<String>,
    id: Option<String>,
    data: Option<Vec<u8>>,
    bytes: usize,
}

#[derive(Default)]
pub struct TaskEventDecoder {
    pending: Vec<u8>,
    frame: Frame,
    seen: HashSet<String>,
    seen_order: VecDeque<String>,
}

impl TaskEventDecoder {
    pub fn new() -> Self { Self::default() }

    /// Bounded incremental SSE parsing. The caller handles network trust and
    /// reconnects; this decoder only accepts the precise task-event contract.
    pub fn push(&mut self, chunk: &[u8]) -> Result<Vec<TaskProgress>, DecodeError> {
        if chunk.len() > MAX_BUFFER_BYTES ||
            self.pending.len().saturating_add(chunk.len()) > MAX_BUFFER_BYTES {
            self.reset();
            return Err(DecodeError::Oversized);
        }
        self.pending.extend_from_slice(chunk);
        let mut events = Vec::new();
        while let Some(end) = self.pending.iter().position(|b| *b == b'\n') {
            let mut line: Vec<u8> = self.pending.drain(..=end).collect();
            line.pop();
            if line.last() == Some(&b'\r') { line.pop(); }
            if line.len().saturating_add(self.frame.bytes) > MAX_FRAME_BYTES {
                self.reset();
                return Err(DecodeError::Oversized);
            }
            self.frame.bytes += line.len();
            if line.is_empty() {
                match self.finish_frame() {
                    Ok(Some(progress)) => events.push(progress),
                    Ok(None) => {},
                    Err(error) => { self.reset(); return Err(error); }
                };
                continue;
            }
            if line[0] == b':' { continue; } // SSE heartbeat/comment
            let decoded = match std::str::from_utf8(&line) {
                Ok(value) => value,
                Err(_) => { self.reset(); return Err(DecodeError::InvalidUtf8); }
            };
            let Some((field, rest)) = decoded.split_once(':') else {
                self.reset();
                return Err(DecodeError::InvalidFrame);
            };
            let value = rest.strip_prefix(' ').unwrap_or(rest);
            match field {
                "event" if self.frame.event.is_none() && value.len() <= 40 =>
                    self.frame.event = Some(value.to_owned()),
                "id" if self.frame.id.is_none() && value.len() <= 100 =>
                    self.frame.id = Some(value.to_owned()),
                "data" if self.frame.data.is_none() =>
                    self.frame.data = Some(value.as_bytes().to_vec()),
                _ => { self.reset(); return Err(DecodeError::InvalidFrame); }
            }
        }
        if self.pending.len().saturating_add(self.frame.bytes) > MAX_FRAME_BYTES {
            self.reset();
            return Err(DecodeError::Oversized);
        }
        Ok(events)
    }

    fn finish_frame(&mut self) -> Result<Option<TaskProgress>, DecodeError> {
        let frame = std::mem::take(&mut self.frame);
        if frame.event.is_none() && frame.id.is_none() && frame.data.is_none() {
            return Ok(None);
        }
        if frame.event.as_deref() != Some("task-event") {
            return Err(DecodeError::InvalidEvent);
        }
        let id = frame.id.ok_or(DecodeError::InvalidFrame)?;
        let bytes = frame.data.ok_or(DecodeError::InvalidFrame)?;
        let raw: BorrowedTaskEvent<'_> =
            serde_json::from_slice(&bytes).map_err(|_| DecodeError::InvalidEvent)?;
        if !valid_uuid(raw.task_id) || !valid_instant(raw.timestamp) ||
            !matches!(raw.state, "QUEUED" | "PLANNING" | "AWAITING_APPROVAL" |
                "RUNNING" | "VERIFYING" | "PAUSED" | "ROLLING_BACK" |
                "COMPLETED" | "FAILED" | "CANCELLED") ||
            raw.agent_id.is_some_and(|agent| !valid_agent(agent)) ||
            id != format!("{}:{}", raw.task_id, raw.timestamp) {
            return Err(DecodeError::InvalidEvent);
        }
        if self.seen.contains(&id) { return Ok(None); }
        self.seen.insert(id.clone());
        self.seen_order.push_back(id);
        while self.seen_order.len() > MAX_SEEN_IDS {
            if let Some(removed) = self.seen_order.pop_front() {
                self.seen.remove(&removed);
            }
        }
        Ok(Some(TaskProgress {
            task_id: raw.task_id.to_ascii_lowercase(),
            timestamp: raw.timestamp.to_owned(),
            state: raw.state.to_owned(),
            agent_id: raw.agent_id.map(str::to_owned),
            provenance: Provenance::UnauthenticatedBytes,
        }))
    }

    fn reset(&mut self) {
        self.pending.clear();
        self.frame = Frame::default();
        // Keep replay IDs after a malformed frame; malformed data must not
        // clear the deduplication record for an otherwise active session.
    }
}

fn valid_uuid(value: &str) -> bool {
    let b = value.as_bytes();
    b.len() == 36 && b.iter().enumerate().all(|(i, ch)| {
        if [8, 13, 18, 23].contains(&i) { *ch == b'-' }
        else { ch.is_ascii_hexdigit() }
    })
}
fn valid_agent(agent: &str) -> bool {
    !agent.is_empty() && agent.len() <= 80 &&
    agent.as_bytes().iter().enumerate().all(|(i, b)| {
        b.is_ascii_lowercase() || b.is_ascii_digit() || (i > 0 && *b == b'-')
    })
}
fn valid_instant(value: &str) -> bool {
    let b = value.as_bytes();
    if b.len() < 20 || b.len() > 30 || b[4] != b'-' || b[7] != b'-' ||
        b[10] != b'T' || b[13] != b':' || b[16] != b':' ||
        b[b.len()-1] != b'Z' { return false; }
    let fixed = [(0,4),(5,7),(8,10),(11,13),(14,16),(17,19)];
    if !fixed.iter().all(|(a,z)| b[*a..*z].iter().all(u8::is_ascii_digit)) { return false; }
    let fraction = &b[19..b.len()-1];
    if !fraction.is_empty() && (fraction[0] != b'.' ||
        fraction.len() < 2 || fraction.len() > 10 ||
        !fraction[1..].iter().all(u8::is_ascii_digit)) { return false; }
    let num = |a: usize, z: usize| -> u32 {
        b[a..z].iter().fold(0_u32,|total,d| total*10 + u32::from(*d-b'0'))
    };
    let year=num(0,4); let month=num(5,7); let day=num(8,10);
    let hour=num(11,13); let minute=num(14,16); let second=num(17,19);
    if year==0 || month==0 || month>12 || hour>23 || minute>59 || second>59 { return false; }
    let leap = year%4==0 && (year%100!=0 || year%400==0);
    let days = match month {
        4 | 6 | 9 | 11 => 30,
        2 if leap => 29,
        2 => 28,
        _ => 31
    };
    day >= 1 && day <= days
}

#[cfg(test)]
mod tests {
    use super::*;
    const ID: &str = "11aa11aa-1111-4111-8111-111111111111";
    fn event(state: &str, timestamp: &str) -> Vec<u8> {
        format!("event: task-event\nid: {ID}:{timestamp}\ndata: {{\"taskId\":\"{ID}\",\"timestamp\":\"{timestamp}\",\"state\":\"{state}\",\"agentId\":\"backend-engineer\",\"message\":\"CREDENTIAL_DO_NOT_RENDER\",\"metadata\":{{\"accessToken\":\"SECRET\"}}}}\n\n").into_bytes()
    }

    #[test]
    fn parses_exact_external_sse_contract_without_sensitive_data() {
        let mut d=TaskEventDecoder::new();
        let list=d.push(&event("RUNNING","2026-10-13T12:00:00.123456789Z")).unwrap();
        assert_eq!(list.len(),1);
        assert_eq!(list[0].task_id,ID);
        assert_eq!(list[0].state,"RUNNING");
        assert_eq!(list[0].agent_id.as_deref(),Some("backend-engineer"));
        assert_eq!(list[0].provenance,Provenance::UnauthenticatedBytes);
        assert!(!format!("{list:?}").contains("CREDENTIAL_DO_NOT_RENDER"));
        assert!(!format!("{list:?}").contains("SECRET"));
    }

    #[test]
    fn handles_split_packets_and_crlf_frames() {
        let mut d=TaskEventDecoder::new();
        let bytes=event("VERIFYING","2026-10-13T12:00:01Z");
        let mut out=Vec::new();
        for piece in bytes.chunks(3) { out.extend(d.push(piece).unwrap()); }
        assert_eq!(out.len(),1);
        let mut d2=TaskEventDecoder::new();
        let crlf=String::from_utf8(bytes).unwrap().replace("\n","\r\n");
        assert_eq!(d2.push(crlf.as_bytes()).unwrap().len(),1);
    }

    #[test]
    fn duplicate_ids_are_ignored_even_after_an_invalid_frame() {
        let mut d=TaskEventDecoder::new();
        let evt=event("RUNNING","2026-10-13T12:00:00Z");
        assert_eq!(d.push(&evt).unwrap().len(),1);
        assert_eq!(d.push(&evt).unwrap().len(),0);
        assert_eq!(d.push(b"event: junk\nid: abc\ndata: {}\n\n"),
                   Err(DecodeError::InvalidEvent));
        assert_eq!(d.push(&evt).unwrap().len(),0);
    }

    #[test]
    fn frame_requires_exact_id_event_and_object_shape() {
        for broken in [
            b"data: {}\n\n".to_vec(),
            b"event: task-event\nid: mismatch\ndata: {}\n\n".to_vec(),
            event("FAKE_SUCCESS","2026-10-13T12:00:00Z"),
            event("COMPLETED","2026-02-30T12:00:00Z"),
            event("RUNNING","2026-10-13T12:00:00.1234567890Z"),
            b"event: task-event\nid: x\ndata: {}\ndata: {}\n\n".to_vec(),
        ] {
            assert!(TaskEventDecoder::new().push(&broken).is_err());
        }
    }

    #[test]
    fn never_accepts_oversized_frames_or_pending_buffers() {
        let mut d=TaskEventDecoder::new();
        let mut huge=b"event: task-event\ndata: ".to_vec();
        huge.extend(vec![b'A';MAX_FRAME_BYTES+3]);
        assert_eq!(d.push(&huge),Err(DecodeError::Oversized));
        assert!(d.push(&event("RUNNING","2026-10-13T12:00:00Z")).is_ok());
    }

    #[test]
    fn malformed_utf8_fails_closed_and_decoder_can_recover() {
        let mut d=TaskEventDecoder::new();
        assert_eq!(d.push(&[0xFF, b'\\n']),Err(DecodeError::InvalidUtf8));
        let good=event("QUEUED","2026-10-13T12:00:00Z");
        assert_eq!(d.push(&good).unwrap().len(),1);
    }

    #[test]
    fn agent_ids_uuid_and_calendar_time_are_validated() {
        assert!(valid_uuid(ID));
        assert!(!valid_uuid("../private/path"));
        assert!(valid_agent("qa-engineer"));
        assert!(!valid_agent("qa.engineer"));
        assert!(!valid_agent("-qa-engineer"));
        assert!(valid_instant("2024-02-29T23:59:59Z"));
        for value in ["2026-02-29T12:00:00Z","2026-13-01T00:00:00Z",
            "2026-10-13T24:00:00Z","2026-10-13T12:00:00+00:00",
            "2026-10-13T12:00:00.1234567890Z"] {
            assert!(!valid_instant(value),"{value}");
        }
    }
}
