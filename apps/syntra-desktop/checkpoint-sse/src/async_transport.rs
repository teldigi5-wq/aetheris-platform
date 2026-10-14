//! Cancel-safe native asynchronous SSE transport, isolated from real Syntra
//! sessions and Tauri. A STOP handle aborts pending request headers and a
//! currently blocked body read using tokio::select!, not polling a sync flag.
//!
//! No credential/session constructors, HTTP activation, task execution,
//! detached retry loop, frontend command, or durable acknowledgement exists.

use super::{
    JournalFrameDecoder, MissionUpdate, NativeAccessToken, NativeError,
    VerifiedNativeSession, MAX_EVENTS_PER_CONNECTION, MAX_RESPONSE_BYTES,
};
use reqwest::header::{HeaderValue, ACCEPT, AUTHORIZATION, CONTENT_TYPE};
use reqwest::redirect::Policy;
use std::time::Duration;
use tokio::sync::watch;

#[derive(Clone)]
pub struct NativeStopHandle {
    sender: watch::Sender<bool>,
}

impl NativeStopHandle {
    /// Synchronous STOP/logout signal. Idempotent and safe to call even when
    /// no native stream is running. No session or token is exposed.
    pub fn stop(&self) {
        self.sender.send_replace(true);
    }
    pub fn is_stopped(&self) -> bool {
        *self.sender.borrow()
    }
}
impl std::fmt::Debug for NativeStopHandle {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("NativeStopHandle([REDACTED])")
    }
}

pub struct InterruptibleMissionClient {
    port: u16,
    client: reqwest::Client,
    stop: NativeStopHandle,
}

impl InterruptibleMissionClient {
    /// Creation is not an owner grant. Network access still requires two
    /// private, unconstructible-outside-crate native token/session types.
    pub fn new_loopback(port: u16) -> Result<Self, NativeError> {
        if port == 0 { return Err(NativeError::UnsupportedOrigin); }
        let client = reqwest::Client::builder()
            .no_proxy()
            .redirect(Policy::none())
            .connect_timeout(Duration::from_secs(2))
            .timeout(Duration::from_secs(35))
            .build().map_err(|_| NativeError::Unavailable)?;
        let (sender, _) = watch::channel(false);
        Ok(Self {
            port, client,
            stop: NativeStopHandle { sender },
        })
    }

    pub fn stop_handle(&self) -> NativeStopHandle { self.stop.clone() }

    /// No background tasks: the owner of this Future must await or explicitly
    /// cancel it. Cancelled requests are dropped, including their HTTP body.
    /// Only fully accepted, sanitized journal events can be returned.
    pub async fn read_once(
        &self, session: &VerifiedNativeSession, token: &NativeAccessToken,
    ) -> Result<Vec<MissionUpdate>, NativeError> {
        let mut stop_receiver = self.stop.sender.subscribe();
        if *stop_receiver.borrow() { return Err(NativeError::Stopped); }
        if !super::uuid(&session.task_id) || !super::uuid(&session.checkpoint_id) {
            return Err(NativeError::InvalidCheckpoint);
        }
        let mut authorization = HeaderValue::from_str(
            &format!("Bearer {}", token.0.as_str()))
            .map_err(|_| NativeError::InvalidToken)?;
        authorization.set_sensitive(true);
        let url = format!(
            "http://127.0.0.1:{}/api/orchestrator/owner-checkpoints/{}/stream",
            self.port, session.checkpoint_id
        );
        let request = self.client.get(url)
            .header(ACCEPT, "text/event-stream")
            .header(AUTHORIZATION, authorization)
            .send();
        let mut response = tokio::select! {
            biased;
            changed = stop_receiver.changed() => {
                let _ = changed;
                return Err(NativeError::Stopped);
            }
            result = request => result.map_err(|_| NativeError::Unavailable)?,
        };
        if *stop_receiver.borrow() { return Err(NativeError::Stopped); }
        if !response.status().is_success() {
            return if matches!(response.status().as_u16(), 401 | 403 | 404) {
                Err(NativeError::AccessDenied)
            } else { Err(NativeError::Unavailable) };
        }
        if response.headers().get(CONTENT_TYPE)
            .and_then(|h| h.to_str().ok())
            .unwrap_or("").split(';').next() != Some("text/event-stream") {
            return Err(NativeError::InvalidStream);
        }
        let mut decoder = JournalFrameDecoder::new(
            &session.task_id, session.last_ack_sequence)?;
        let mut events = Vec::new();
        let mut total = 0usize;
        loop {
            if *stop_receiver.borrow() { return Err(NativeError::Stopped); }
            let chunk = tokio::select! {
                biased;
                changed = stop_receiver.changed() => {
                    let _ = changed;
                    return Err(NativeError::Stopped);
                }
                result = response.chunk() => result.map_err(|_| NativeError::Unavailable)?,
            };
            let Some(chunk) = chunk else { break; };
            total = total.saturating_add(chunk.len());
            if total > MAX_RESPONSE_BYTES { return Err(NativeError::TooLarge); }
            let accepted = decoder.push(&chunk)?;
            if events.len().saturating_add(accepted.len()) > MAX_EVENTS_PER_CONNECTION {
                return Err(NativeError::TooLarge);
            }
            events.extend(accepted);
        }
        if *stop_receiver.borrow() { return Err(NativeError::Stopped); }
        Ok(events)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use super::super::{EventProvenance, NativeAccessToken, VerifiedNativeSession};
    use std::time::Instant;
    use tokio::io::{AsyncReadExt, AsyncWriteExt};
    use tokio::net::TcpListener;
    use tokio::sync::oneshot;
    use zeroize::Zeroizing;

    const TASK: &str = "11aa11aa-1111-4111-8111-111111111111";
    const FOREIGN: &str = "22bb22bb-2222-4222-8222-222222222222";
    const CHECKPOINT: &str = "33cc33cc-3333-4333-8333-333333333333";
    const EVENT: &str = "44dd44dd-4444-4444-8444-444444444444";

    fn session() -> VerifiedNativeSession {
        VerifiedNativeSession {
            task_id: TASK.to_owned(),
            checkpoint_id: CHECKPOINT.to_owned(),
            last_ack_sequence: 0,
        }
    }
    fn token() -> NativeAccessToken {
        NativeAccessToken(Zeroizing::new("synthetic.header.signature".into()))
    }
    fn event(task: &str) -> String {
        format!(
            "event:task-recorded\nid:{EVENT}\ndata:{{\"eventId\":\"{EVENT}\",\"taskId\":\"{task}\",\"sequence\":1,\"eventAt\":\"2026-10-14T12:00:00Z\",\"state\":\"RUNNING\",\"agentId\":\"qa-engineer\",\"provenance\":\"PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME\"}}\n\n"
        )
    }
    async fn accept_request(socket: &mut tokio::net::TcpStream) -> String {
        let mut buf = [0u8; 2048];
        let mut bytes = Vec::new();
        while !bytes.windows(4).any(|w| w == b"\r\n\r\n") {
            let len = socket.read(&mut buf).await.unwrap();
            assert!(len > 0 && bytes.len() + len < 8192);
            bytes.extend_from_slice(&buf[..len]);
        }
        String::from_utf8(bytes).unwrap()
    }

    #[tokio::test]
    async fn native_async_sse_reads_signed_loopback_request_and_sanitized_record() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let mock = tokio::spawn(async move {
            let (mut socket, _) = listener.accept().await.unwrap();
            let request = accept_request(&mut socket).await;
            let body = event(TASK);
            socket.write_all(format!(
                "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
                body.len()).as_bytes()).await.unwrap();
            socket.write_all(body.as_bytes()).await.unwrap();
            request
        });
        let client = InterruptibleMissionClient::new_loopback(port).unwrap();
        let output = client.read_once(&session(), &token()).await.unwrap();
        let request = mock.await.unwrap();
        assert_eq!(output.len(),1);
        assert_eq!(output[0].sequence,1);
        assert_eq!(output[0].provenance,EventProvenance::ServerJournalClaimNotVerifiedExecution);
        assert!(request.starts_with(&format!(
            "GET /api/orchestrator/owner-checkpoints/{CHECKPOINT}/stream HTTP/1.1")));
        assert!(request.contains("Bearer synthetic.header.signature"));
        assert!(!request.contains("Last-Event-ID:"));
        assert!(!request.contains("X-Aetheris-User-Id:"));
    }

    #[tokio::test]
    async fn stop_interrupts_stalled_response_headers_without_waiting_for_timeout() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let (ready_tx, ready_rx) = oneshot::channel();
        let mock = tokio::spawn(async move {
            let (mut socket, _) = listener.accept().await.unwrap();
            accept_request(&mut socket).await;
            ready_tx.send(()).unwrap();
            // Simulate stuck headers. This must NOT delay native STOP.
            tokio::time::sleep(Duration::from_secs(4)).await;
        });
        let client = InterruptibleMissionClient::new_loopback(port).unwrap();
        let stop = client.stop_handle();
        let future = tokio::spawn(async move { client.read_once(&session(),&token()).await });
        tokio::time::timeout(Duration::from_secs(3), ready_rx).await.unwrap().unwrap();
        let start = Instant::now();
        stop.stop();
        let result = tokio::time::timeout(Duration::from_secs(2), future).await.unwrap().unwrap();
        assert_eq!(result.unwrap_err(),NativeError::Stopped);
        assert!(start.elapsed() < Duration::from_secs(2));
        assert!(stop.is_stopped());
        mock.abort();
    }

    #[tokio::test]
    async fn stop_interrupts_blocked_body_read_without_releasing_partial_events() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let (ready_tx,ready_rx) = oneshot::channel();
        let mock = tokio::spawn(async move {
            let (mut socket, _) = listener.accept().await.unwrap();
            accept_request(&mut socket).await;
            socket.write_all(
                b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n"
            ).await.unwrap();
            let body = event(TASK);
            socket.write_all(format!("{:X}\r\n", body.len()).as_bytes()).await.unwrap();
            socket.write_all(body.as_bytes()).await.unwrap();
            socket.write_all(b"\r\n").await.unwrap();
            ready_tx.send(()).unwrap();
            tokio::time::sleep(Duration::from_secs(4)).await;
        });
        let client = InterruptibleMissionClient::new_loopback(port).unwrap();
        let stop = client.stop_handle();
        let future = tokio::spawn(async move { client.read_once(&session(),&token()).await });
        tokio::time::timeout(Duration::from_secs(3),ready_rx).await.unwrap().unwrap();
        tokio::time::sleep(Duration::from_millis(50)).await;
        let start=Instant::now();
        stop.stop();
        let result=tokio::time::timeout(Duration::from_secs(2),future).await.unwrap().unwrap();
        assert_eq!(result.unwrap_err(),NativeError::Stopped);
        assert!(start.elapsed() < Duration::from_secs(2));
        mock.abort();
    }

    #[tokio::test]
    async fn terminal_errors_do_not_become_native_progress_and_stop_before_call_is_final() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let mock=tokio::spawn(async move {
            let (mut socket,_) = listener.accept().await.unwrap();
            accept_request(&mut socket).await;
            socket.write_all(
                b"HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nContent-Length: 2\r\n\r\n{}"
            ).await.unwrap();
        });
        let client = InterruptibleMissionClient::new_loopback(port).unwrap();
        assert_eq!(client.read_once(&session(),&token()).await.unwrap_err(),
            NativeError::AccessDenied);
        mock.await.unwrap();

        let client=InterruptibleMissionClient::new_loopback(8090).unwrap();
        client.stop_handle().stop();
        assert_eq!(client.read_once(&session(),&token()).await.unwrap_err(),
            NativeError::Stopped);
        assert_eq!(InterruptibleMissionClient::new_loopback(0).err(),
            Some(NativeError::UnsupportedOrigin));
        let _ = FOREIGN; // private synthetic counterpart used by parser tests
    }
}
