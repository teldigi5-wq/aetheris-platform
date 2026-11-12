//! Pre-hardware Syntra -> Gateway media job contract, not GPU generation.
//! Native-side only: never pass bearer tokens to/from the WebView.
//! Transport and credential storage are deliberately NOT wired in production.
use serde_json::{json, Value};
use std::fmt;

const STAGING_TRUTH: &str = "BLOCKED_PENDING_HARDWARE";
const STAGED_ONLY: &str = "STAGED_NOT_EXECUTED";
const MAX_REPLY_BYTES: usize = 8_192;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ClientError {
    NoNativeCredential,
    InvalidGatewayPort,
    InvalidRequest,
    InvalidJobId,
    InvalidResponse,
    UnsupportedExecution,
    TransportUnavailable,
}

/// Opaque secret for native credential brokers. Neither its value nor Debug
/// output is part of any WebView/Tauri command result, timeline, or log.
pub struct NativeAccessToken(Vec<u8>);
impl fmt::Debug for NativeAccessToken {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("NativeAccessToken(REDACTED)")
    }
}
impl NativeAccessToken {
    pub fn from_native_broker(value: Vec<u8>) -> Result<Self, ClientError> {
        if !(16..=4096).contains(&value.len())
            || !value.iter().all(|ch| ch.is_ascii_graphic())
            || value.iter().any(|ch| *ch == b'"' || *ch == b'\\')
        {
            return Err(ClientError::NoNativeCredential);
        }
        Ok(Self(value))
    }
    pub fn as_bytes(&self) -> &[u8] { &self.0 }
}
impl Drop for NativeAccessToken {
    fn drop(&mut self) { self.0.fill(0); }
}

/// In the real desktop, this must use an explicitly reviewed OS-backed
/// credential broker (and token lifecycle); never a WebView string parameter.
pub trait NativeCredentialProvider {
    fn access_token(&self) -> Result<NativeAccessToken, ClientError>;
}

/// Shipping/default credential source. It cannot silently read an env var,
/// localStorage, the filesystem, or a developer's CI credentials.
pub struct NoNativeCredential;
impl NativeCredentialProvider for NoNativeCredential {
    fn access_token(&self) -> Result<NativeAccessToken, ClientError> {
        Err(ClientError::NoNativeCredential)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Verb { Get, Post, Delete }

/// Contains the credential only in native process memory. Debug deliberately
/// excludes the secret (even when a developer accidentally logs the request).
pub struct NativeRequest<'a> {
    pub method: Verb,
    pub path: &'a str,
    pub content_type_json: bool,
    pub idempotency_key: Option<&'a str>,
    pub body: Option<&'a [u8]>,
    pub bearer: &'a NativeAccessToken,
}
impl fmt::Debug for NativeRequest<'_> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("NativeRequest")
            .field("method", &self.method).field("path", &self.path)
            .field("body", &"<REDACTED>").field("bearer", &"<REDACTED>")
            .finish()
    }
}

pub struct NativeResponse {
    pub status: u16,
    pub body: Vec<u8>,
}
pub trait NativeGatewayTransport {
    /// Must constrain network connection to http://127.0.0.1:<explicit-port>,
    /// disable system proxies and redirects, and bound transport time/size.
    /// This trait is mock-only until a reviewed implementation is added.
    fn send(&self, port: u16, request: &NativeRequest<'_>) -> Result<NativeResponse, ClientError>;
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct JobDraft {
    pub prompt: String,
    pub negative_prompt: String,
    pub width: u16,
    pub height: u16,
}
impl JobDraft {
    pub fn validate(&self) -> Result<(), ClientError> {
        let size = matches!((self.width, self.height),
            (512, 512) | (512, 768) | (768, 512));
        if !size || self.prompt.trim().is_empty()
            || self.prompt.chars().count() > 1200
            || self.negative_prompt.chars().count() > 500
        { return Err(ClientError::InvalidRequest); }
        Ok(())
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StageReceipt {
    pub id: String,
    pub status: String,
    pub hardware_status: String,
    pub gpu_job_started: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CapabilityReceipt {
    pub staging_enabled: bool,
    pub image_generation_state: String,
    pub video_generation_state: String,
    pub physical_pc_status: String,
    pub gpu_job_started: bool,
}

/// Strict ASCII canonical UUID shape matching the staged-job API. A job id
/// supplied from JS can only form one local gateway path; never a URL.
pub fn is_canonical_job_uuid(value: &str) -> bool {
    if value.len() != 36 { return false; }
    value.bytes().enumerate().all(|(i, byte)| {
        if [8, 13, 18, 23].contains(&i) { byte == b'-' }
        else { byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte) }
    })
}
fn safe_json(reply: NativeResponse, expected_status: u16) -> Result<Value, ClientError> {
    if reply.status != expected_status || reply.body.len() > MAX_REPLY_BYTES {
        return Err(ClientError::InvalidResponse);
    }
    let value: Value = serde_json::from_slice(&reply.body).map_err(|_| ClientError::InvalidResponse)?;
    if !value.is_object() { return Err(ClientError::InvalidResponse); }
    Ok(value)
}
fn never_claim_gpu(value: &Value) -> Result<(), ClientError> {
    if value.get("physicalPcStatus").and_then(Value::as_str) != Some(STAGING_TRUTH)
        || value.get("gpuJobStarted").and_then(Value::as_bool) != Some(false)
    { return Err(ClientError::UnsupportedExecution); }
    Ok(())
}
pub struct NativeMediaClient<C, T> {
    port: u16,
    credential: C,
    transport: T,
}
impl<C: NativeCredentialProvider, T: NativeGatewayTransport> NativeMediaClient<C, T> {
    pub fn new(port: u16, credential: C, transport: T) -> Result<Self, ClientError> {
        if port == 0 { return Err(ClientError::InvalidGatewayPort); }
        Ok(Self { port, credential, transport })
    }
    fn send(&self, method: Verb, path: &str, key: Option<&str>, body: Option<&[u8]>) -> Result<NativeResponse, ClientError> {
        let token = self.credential.access_token()?; // auth failure BEFORE network access
        self.transport.send(self.port, &NativeRequest {
            method, path, idempotency_key: key,
            content_type_json: body.is_some(), body, bearer: &token,
        })
    }
    pub fn capabilities(&self) -> Result<CapabilityReceipt, ClientError> {
        let v = safe_json(self.send(Verb::Get, "/api/media/capabilities", None, None)?, 200)?;
        never_claim_gpu(&v)?;
        let image = v.get("imageGenerationState").and_then(Value::as_str).ok_or(ClientError::InvalidResponse)?;
        let video = v.get("videoGenerationState").and_then(Value::as_str).ok_or(ClientError::InvalidResponse)?;
        if image != "NOT_WIRED" || video != "NOT_WIRED" { return Err(ClientError::UnsupportedExecution); }
        let staging = v.get("stagingEnabled").and_then(Value::as_bool).ok_or(ClientError::InvalidResponse)?;
        Ok(CapabilityReceipt {
            staging_enabled: staging, image_generation_state: image.to_string(),
            video_generation_state: video.to_string(),
            physical_pc_status: STAGING_TRUTH.to_string(), gpu_job_started: false,
        })
    }
    pub fn stage_image(&self, draft: &JobDraft, idempotency_key: &str) -> Result<StageReceipt, ClientError> {
        draft.validate()?;
        if !is_canonical_job_uuid(idempotency_key) { return Err(ClientError::InvalidRequest); }
        let body = json!({
            "kind": "IMAGE", "prompt": draft.prompt,
            "negativePrompt": draft.negative_prompt, "width": draft.width, "height": draft.height,
        });
        let body = serde_json::to_vec(&body).map_err(|_| ClientError::InvalidRequest)?;
        if body.len() > 4096 { return Err(ClientError::InvalidRequest); }
        let response = self.send(Verb::Post, "/api/media/jobs", Some(idempotency_key), Some(&body))?;
        self.parse_job(response, 202)
    }
    pub fn read_job(&self, id: &str) -> Result<StageReceipt, ClientError> {
        if !is_canonical_job_uuid(id) { return Err(ClientError::InvalidJobId); }
        let path = format!("/api/media/jobs/{id}");
        self.parse_job(self.send(Verb::Get, &path, None, None)?, 200)
    }
    pub fn discard_job(&self, id: &str) -> Result<(), ClientError> {
        if !is_canonical_job_uuid(id) { return Err(ClientError::InvalidJobId); }
        let path = format!("/api/media/jobs/{id}");
        let reply = self.send(Verb::Delete, &path, None, None)?;
        if reply.status != 204 || !reply.body.is_empty() { return Err(ClientError::InvalidResponse); }
        Ok(())
    }
    fn parse_job(&self, response: NativeResponse, status: u16) -> Result<StageReceipt, ClientError> {
        let v = safe_json(response, status)?;
        never_claim_gpu(&v)?;
        let id = v.get("id").and_then(Value::as_str).ok_or(ClientError::InvalidResponse)?;
        if !is_canonical_job_uuid(id) { return Err(ClientError::InvalidResponse); }
        let state = v.get("state").and_then(Value::as_str).ok_or(ClientError::InvalidResponse)?;
        if state != STAGED_ONLY { return Err(ClientError::UnsupportedExecution); }
        // Never return prompt contents, raw error bodies, or arbitrary gateway output to JS.
        Ok(StageReceipt {
            id: id.into(), status: STAGED_ONLY.into(),
            hardware_status: STAGING_TRUTH.into(), gpu_job_started: false,
        })
    }
}
/// This is the only live desktop-compatible provider presently shipped.
pub fn bridge_status() -> &'static str {
    "NO_OS_CREDENTIAL_BROKER_NOT_WIRED"
}


#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;
    use std::sync::atomic::{AtomicUsize, Ordering};

    const ID: &str = "123e4567-e89b-12d3-a456-426614174000";
    fn draft() -> JobDraft {
        JobDraft {
            prompt: "sunrise on a hill".into(), negative_prompt: "blur".into(),
            width: 512, height: 512,
        }
    }
    #[derive(Default)]
    struct Counts(AtomicUsize);
    struct FakeTransport {
        sent: Arc<Counts>,
        expected_path: String,
        expected_verb: Verb,
        reply: NativeResponse,
    }
    impl NativeGatewayTransport for FakeTransport {
        fn send(&self, port: u16, request: &NativeRequest<'_>) -> Result<NativeResponse, ClientError> {
            assert_eq!(port, 8080);
            assert_eq!(request.path, self.expected_path);
            assert_eq!(request.method, self.expected_verb);
            assert!(request.bearer.as_bytes().starts_with(b"test-token"));
            assert!(!format!("{request:?}").contains("test-token"));
            self.sent.0.fetch_add(1, Ordering::SeqCst);
            Ok(NativeResponse { status: self.reply.status, body: self.reply.body.clone() })
        }
    }
    fn transport(method: Verb, path: &str, status: u16, body: &str, counts: Arc<Counts>) -> FakeTransport {
        FakeTransport { sent: counts, expected_path: path.to_owned(), expected_verb: method,
            reply: NativeResponse { status, body: body.as_bytes().to_vec() } }
    }
    struct MockNativeBroker;
    impl NativeCredentialProvider for MockNativeBroker {
        fn access_token(&self) -> Result<NativeAccessToken, ClientError> {
            NativeAccessToken::from_native_broker(b"test-token-from-fake-vault".to_vec())
        }
    }
    fn reply(id: &str) -> String {
        json!({"id":id,"state":"STAGED_NOT_EXECUTED","gpuJobStarted":false,
            "physicalPcStatus":"BLOCKED_PENDING_HARDWARE"}).to_string()
    }

    #[test]
    fn real_desktop_credential_source_always_refuses_before_transport() {
        let counts = Arc::new(Counts::default());
        let c = NativeMediaClient::new(8080, NoNativeCredential,
            transport(Verb::Get, "/api/media/capabilities", 200, "{}", counts.clone())).unwrap();
        assert!(matches!(c.capabilities(), Err(ClientError::NoNativeCredential)));
        assert_eq!(counts.0.load(Ordering::SeqCst), 0);
        assert_eq!(bridge_status(), "NO_OS_CREDENTIAL_BROKER_NOT_WIRED");
    }
    #[test]
    fn invalid_port_and_no_external_url_parameter_possible() {
        let counts = Arc::new(Counts::default());
        assert!(matches!(NativeMediaClient::new(0, MockNativeBroker,
            transport(Verb::Get, "", 200, "{}", counts)), Err(ClientError::InvalidGatewayPort)));
    }
    #[test]
    fn invalid_drafts_and_oversized_payloads_refuse_without_network() {
        let counts = Arc::new(Counts::default());
        let c = NativeMediaClient::new(8080, MockNativeBroker,
            transport(Verb::Post, "/api/media/jobs", 202, &reply(ID), counts.clone())).unwrap();
        for p in [
            JobDraft{prompt:"".into(),..draft()},
            JobDraft{prompt:"x".repeat(1201),..draft()},
            JobDraft{negative_prompt:"x".repeat(501),..draft()},
            JobDraft{width:8192,..draft()},
            JobDraft{height:4096,..draft()},
        ] {
            assert!(matches!(c.stage_image(&p, ID), Err(ClientError::InvalidRequest)));
        }
        assert!(matches!(c.stage_image(&draft(), "../bypass"), Err(ClientError::InvalidRequest)));
        assert_eq!(counts.0.load(Ordering::SeqCst), 0);
    }
    #[test]
    fn bad_ids_cannot_become_arbitrary_paths() {
        for id in ["../../etc/passwd", "http://evil.com/path", "deadbeef", 
                   "123e4567-e89b-12d3-a456-426614174000/../../evil",
                   "123E4567-E89B-12D3-A456-426614174000"] {
            assert!(!is_canonical_job_uuid(id));
        }
        assert!(is_canonical_job_uuid(ID));
    }
    #[test]
    fn valid_native_mock_roundtrip_never_claims_generation() {
        let counts = Arc::new(Counts::default());
        let c = NativeMediaClient::new(8080, MockNativeBroker,
            transport(Verb::Post, "/api/media/jobs", 202, &reply(ID), counts.clone())).unwrap();
        let receipt = c.stage_image(&draft(), ID).unwrap();
        assert_eq!(receipt.id, ID);
        assert_eq!(receipt.status, "STAGED_NOT_EXECUTED");
        assert_eq!(receipt.hardware_status, "BLOCKED_PENDING_HARDWARE");
        assert!(!receipt.gpu_job_started);
        assert_eq!(counts.0.load(Ordering::SeqCst), 1);
    }
    #[test]
    fn rejects_fake_completed_or_hardware_ready_gateway_responses() {
        for body in [
            json!({"id":ID,"state":"SUCCEEDED","gpuJobStarted":false,
                "physicalPcStatus":"BLOCKED_PENDING_HARDWARE"}).to_string(),
            json!({"id":ID,"state":"STAGED_NOT_EXECUTED","gpuJobStarted":true,
                "physicalPcStatus":"BLOCKED_PENDING_HARDWARE"}).to_string(),
            json!({"id":ID,"state":"STAGED_NOT_EXECUTED","gpuJobStarted":false,
                "physicalPcStatus":"READY"}).to_string()
        ] {
            let c = NativeMediaClient::new(8080, MockNativeBroker,
                transport(Verb::Post, "/api/media/jobs", 202, &body, Arc::new(Counts::default()))).unwrap();
            assert!(matches!(c.stage_image(&draft(), ID), Err(ClientError::UnsupportedExecution)));
        }
    }
    #[test]
    fn read_only_capabilities_remain_not_wired() {
        let c = NativeMediaClient::new(8080, MockNativeBroker, transport(
            Verb::Get, "/api/media/capabilities", 200,
            r#"{"stagingEnabled":false,"imageGenerationState":"NOT_WIRED",
               "videoGenerationState":"NOT_WIRED","physicalPcStatus":"BLOCKED_PENDING_HARDWARE",
               "gpuJobStarted":false}"#, Arc::new(Counts::default()))).unwrap();
        let v = c.capabilities().unwrap();
        assert!(!v.staging_enabled);
        assert_eq!(v.video_generation_state, "NOT_WIRED");
        assert!(!v.gpu_job_started);
    }
    #[test]
    fn never_return_unbounded_or_untrusted_gateway_response() {
        let x = NativeMediaClient::new(8080, MockNativeBroker, transport(
            Verb::Post, "/api/media/jobs", 202, &"x".repeat(9000),
            Arc::new(Counts::default()))).unwrap();
        assert!(matches!(x.stage_image(&draft(), ID), Err(ClientError::InvalidResponse)));
        let x = NativeMediaClient::new(8080, MockNativeBroker, transport(
            Verb::Post, "/api/media/jobs", 500, &reply(ID),
            Arc::new(Counts::default()))).unwrap();
        assert!(matches!(x.stage_image(&draft(), ID), Err(ClientError::InvalidResponse)));
    }
    #[test]
    fn credentials_and_request_debug_output_are_redacted() {
        let value = NativeAccessToken::from_native_broker(b"secret-native-token-test".to_vec()).unwrap();
        assert!(!format!("{value:?}").contains("secret-native-token-test"));
        assert_eq!(value.as_bytes(), b"secret-native-token-test");
        assert!(NativeAccessToken::from_native_broker(b"bad\nsecret".to_vec()).is_err());
    }
    #[test]
    fn read_and_discard_are_owner_scope_delegated_to_gateway() {
        let c = NativeMediaClient::new(8080, MockNativeBroker, transport(
            Verb::Get, &format!("/api/media/jobs/{ID}"), 200, &reply(ID), Arc::new(Counts::default()))).unwrap();
        assert_eq!(c.read_job(ID).unwrap().status, "STAGED_NOT_EXECUTED");
        let c = NativeMediaClient::new(8080, MockNativeBroker, transport(
            Verb::Delete, &format!("/api/media/jobs/{ID}"), 204, "", Arc::new(Counts::default()))).unwrap();
        assert!(c.discard_job(ID).is_ok());
        assert!(matches!(c.discard_job("../x"), Err(ClientError::InvalidJobId)));
    }
}
