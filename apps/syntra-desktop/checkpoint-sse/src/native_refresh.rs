//! Review-only native /api/auth/refresh and /api/auth/logout HTTP contract.
//!
//! CRITICAL: This module is intentionally PRIVATE (not a public API, Tauri
//! command, or a credential/session issuer). The existing HMAC JWT contract
//! cannot be trusted by a desktop that only parses its claims. No received
//! value can mint NativeAccessToken or TrustedSessionLease. No real WinCred
//! refresh token is loaded, stored or deleted by this transport.
//!
//! Only synthetic #[cfg(test)] loopback sockets exercise these paths.

use crate::async_transport::NativeStopHandle;
use crate::NativeError;
use reqwest::header::{ACCEPT, CACHE_CONTROL, CONTENT_TYPE};
use reqwest::redirect::Policy;
use serde::Deserialize;
use std::time::Duration;
use syntra_credential_vault::Secret;
use tokio::sync::watch;
use zeroize::Zeroizing;

const MAX_JSON_BYTES: usize = 16 * 1024;
const MAX_TOKEN_BYTES: usize = 4096;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct WireIdentity {
    id: i64,
    name: String,
    email: String,
    role: String,
    scopes: Vec<String>,
}
#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct WireRefresh {
    access_token: String,
    refresh_token: String,
    token_type: String,
    expires_in_seconds: u64,
    refresh_expires_in_seconds: u64,
    account: WireIdentity,
}

/// UNTRUSTED response envelope, not a verified identity, login, session,
/// authorization grant, or credential-vault rotation. Secret fields zeroize
/// when dropped; reqwest transport-owned copies are separately OS-managed.
struct UntrustedRotation {
    access_token: Zeroizing<String>,
    refresh_token: Zeroizing<String>,
    account_id_claim: i64,
    access_lifetime_seconds: u64,
    refresh_lifetime_seconds: u64,
}
impl std::fmt::Debug for UntrustedRotation {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("UntrustedRotation([REDACTED_NOT_VERIFIED])")
    }
}

/// Conveys an HTTP 204 response, not a verified server state or revocation of
/// all sessions/tokens. Never use this to claim the owner is logged out.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum LogoutWireResult { AcceptedHttp204NotAttested }

/// Unlike the SSE parser, this module is private and deliberately has no
/// production construction path. The test-only factory can contact an
/// ephemeral localhost test listener using synthetic refresh credentials.
struct NativeRefreshWire {
    port: u16,
    client: reqwest::Client,
}

impl NativeRefreshWire {
    #[cfg(test)]
    fn synthetic_loopback(port: u16) -> Result<Self, NativeError> {
        if port == 0 { return Err(NativeError::UnsupportedOrigin); }
        let client = reqwest::Client::builder()
            .no_proxy()
            .redirect(Policy::none())
            .connect_timeout(Duration::from_secs(2))
            .timeout(Duration::from_secs(8))
            .build().map_err(|_| NativeError::Unavailable)?;
        Ok(Self { port, client })
    }

    /// This is a WIRE-ONLY exchange. It neither verifies a JWT signature
    /// nor stores a rotated credential nor creates an authenticated session.
    async fn prepare_refresh(
        &self, old: &Secret, stop: &NativeStopHandle,
    ) -> Result<UntrustedRotation, NativeError> {
        let old_refresh = valid_native_refresh(old)?;
        let body = Zeroizing::new(format!(r#"{{"refreshToken":"{}"}}"#, old_refresh.as_str()));
        let url = format!("http://127.0.0.1:{}/api/auth/refresh", self.port);
        let request = self.client.post(url)
            .header(CONTENT_TYPE, "application/json")
            .header(ACCEPT, "application/json")
            .header(CACHE_CONTROL, "no-store")
            .body(body.as_bytes().to_vec()).send();
        let mut cancel = stop_receiver(stop);
        if *cancel.borrow() { return Err(NativeError::Stopped); }
        let mut response = tokio::select! {
            biased;
            _ = cancel.changed() => return Err(NativeError::Stopped),
            result = request => result.map_err(|_| NativeError::Unavailable)?,
        };
        if *cancel.borrow() { return Err(NativeError::Stopped); }
        if response.status().as_u16() == 401 || response.status().as_u16() == 403 {
            return Err(NativeError::AccessDenied);
        }
        if response.status().as_u16() != 200 ||
                !json_content_type(response.headers().get(CONTENT_TYPE)) {
            return Err(NativeError::InvalidStream);
        }
        let mut bytes = Zeroizing::new(Vec::<u8>::new());
        loop {
            let chunk = tokio::select! {
                biased;
                _ = cancel.changed() => return Err(NativeError::Stopped),
                result = response.chunk() => result.map_err(|_| NativeError::Unavailable)?,
            };
            let Some(chunk) = chunk else { break; };
            if bytes.len().saturating_add(chunk.len()) > MAX_JSON_BYTES {
                return Err(NativeError::TooLarge);
            }
            bytes.extend_from_slice(&chunk);
        }
        if *cancel.borrow() { return Err(NativeError::Stopped); }
        let mut wire: WireRefresh = serde_json::from_slice(&bytes)
            .map_err(|_| NativeError::InvalidStream)?;
        // Even rejected response tokens are zeroized on drop.
        let access = Zeroizing::new(std::mem::take(&mut wire.access_token));
        let refresh = Zeroizing::new(std::mem::take(&mut wire.refresh_token));
        if wire.token_type != "Bearer" ||
                !(1..=3600).contains(&wire.expires_in_seconds) ||
                !(1..=604800).contains(&wire.refresh_expires_in_seconds) ||
                wire.account.id <= 0 ||
                wire.account.name.len() > 100 ||
                wire.account.email.len() > 180 ||
                !wire.account.email.contains('@') ||
                !matches!(wire.account.role.as_str(), "ADMIN" | "DEVELOPER" | "API_CONSUMER") ||
                wire.account.scopes.len() > 32 ||
                !wire.account.scopes.iter().any(|x| x == "orchestrator:read") ||
                !signed_jwt_shape(&access) ||
                !opaque_refresh_shape(&refresh) ||
                refresh.as_str() == old_refresh.as_str()
        {
            return Err(NativeError::InvalidStream);
        }
        Ok(UntrustedRotation {
            access_token: access,
            refresh_token: refresh,
            account_id_claim: wire.account.id,
            access_lifetime_seconds: wire.expires_in_seconds,
            refresh_lifetime_seconds: wire.refresh_expires_in_seconds,
        })
    }

    /// Independent wire-only logout request; not an authenticated guarantee
    /// of remote revocation. A caller must cancel native streams regardless
    /// of this transport's outcome, as NativeSessionSupervisor does locally.
    async fn request_remote_logout(
        &self, old: &Secret, stop: &NativeStopHandle,
    ) -> Result<LogoutWireResult, NativeError> {
        let old_refresh = valid_native_refresh(old)?;
        let body = Zeroizing::new(format!(r#"{{"refreshToken":"{}"}}"#, old_refresh.as_str()));
        let url = format!("http://127.0.0.1:{}/api/auth/logout", self.port);
        let request = self.client.post(url)
            .header(CONTENT_TYPE, "application/json")
            .header(CACHE_CONTROL, "no-store")
            .body(body.as_bytes().to_vec()).send();
        let mut cancel = stop_receiver(stop);
        if *cancel.borrow() { return Err(NativeError::Stopped); }
        let response = tokio::select! {
            biased;
            _ = cancel.changed() => return Err(NativeError::Stopped),
            result = request => result.map_err(|_| NativeError::Unavailable)?,
        };
        if *cancel.borrow() { return Err(NativeError::Stopped); }
        if response.status().as_u16() == 204 {
            Ok(LogoutWireResult::AcceptedHttp204NotAttested)
        } else if matches!(response.status().as_u16(), 401 | 403) {
            Err(NativeError::AccessDenied)
        } else {
            Err(NativeError::Unavailable)
        }
    }
}

fn stop_receiver(stop: &NativeStopHandle) -> watch::Receiver<bool> {
    stop.subscribe()
}
fn valid_native_refresh(secret: &Secret) -> Result<Zeroizing<String>, NativeError> {
    let bytes = secret.expose_for_native_use();
    if bytes.len() != 43 || !bytes.iter().all(|b| b.is_ascii_alphanumeric()
            || *b == b'-' || *b == b'_') {
        return Err(NativeError::InvalidToken);
    }
    let raw = String::from_utf8(bytes.to_vec()).map_err(|_| NativeError::InvalidToken)?;
    Ok(Zeroizing::new(raw))
}
fn opaque_refresh_shape(raw: &str) -> bool {
    raw.len() == 43 && raw.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
}
fn signed_jwt_shape(raw: &str) -> bool {
    raw.len() >= 40 && raw.len() <= MAX_TOKEN_BYTES &&
    raw.split('.').count() == 3 &&
    raw.split('.').all(|part| !part.is_empty() &&
        part.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_'))
}
fn json_content_type(raw: Option<&reqwest::header::HeaderValue>) -> bool {
    raw.and_then(|v| v.to_str().ok())
        .and_then(|v| v.split(';').next())
        == Some("application/json")
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::async_transport::InterruptibleMissionClient;
    use std::sync::Arc;
    use tokio::io::{AsyncReadExt, AsyncWriteExt};
    use tokio::net::TcpListener;
    use tokio::sync::oneshot;
    
    const OLD: &str = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    const NEW: &str = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    const ACCESS: &str = "header.payload.signature-with-synthetic-material";

    fn old() -> Secret { Secret::new(OLD.as_bytes().to_vec()).unwrap() }
    fn stop() -> NativeStopHandle {
        InterruptibleMissionClient::new_loopback(8090).unwrap().stop_handle()
    }
    fn success_json(refresh: &str) -> String {
        format!(r#"{{"accessToken":"{ACCESS}","refreshToken":"{refresh}","tokenType":"Bearer","expiresInSeconds":300,"refreshExpiresInSeconds":3600,"account":{{"id":101,"name":"Synthetic","email":"synthetic@example.test","role":"API_CONSUMER","scopes":["orchestrator:read"]}}}}"#)
    }
    async fn accept_request(s: &mut tokio::net::TcpStream) -> String {
        let mut out = Vec::new();
        let mut buf=[0u8;4096];
        while !out.windows(4).any(|w|w==b"\r\n\r\n") {
            let n=s.read(&mut buf).await.unwrap();
            assert!(n>0 && out.len()+n < 8192);
            out.extend_from_slice(&buf[..n]);
        }
        let headers=String::from_utf8(out.clone()).unwrap();
        let length=headers.split("\r\n").find_map(|l| {
            l.strip_prefix("content-length: ")
                .or_else(||l.strip_prefix("Content-Length: "))
                .and_then(|v|v.parse::<usize>().ok())
        }).unwrap();
        let sep=out.windows(4).position(|w|w==b"\r\n\r\n").unwrap()+4;
        while out.len()-sep < length {
            let n=s.read(&mut buf).await.unwrap();
            assert!(n>0 && out.len()+n<8192);
            out.extend_from_slice(&buf[..n]);
        }
        String::from_utf8(out).unwrap()
    }

    async fn mock_response(code: &'static str, mime: &'static str, body: Vec<u8>)
        -> (u16, tokio::task::JoinHandle<String>) {
        let server=TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port=server.local_addr().unwrap().port();
        let handle=tokio::spawn(async move {
            let (mut conn,_) = server.accept().await.unwrap();
            let request=accept_request(&mut conn).await;
            let hdr=format!("HTTP/1.1 {code}\r\nContent-Type: {mime}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",body.len());
            conn.write_all(hdr.as_bytes()).await.unwrap();
            conn.write_all(&body).await.unwrap();
            request
        });
        (port,handle)
    }

    #[tokio::test]
    async fn rotation_uses_exact_native_post_contract_but_never_issues_a_trusted_session() {
        let (port, server) = mock_response("200 OK","application/json",
            success_json(NEW).into_bytes()).await;
        let wire = NativeRefreshWire::synthetic_loopback(port).unwrap();
        let candidate = wire.prepare_refresh(&old(),&stop()).await.unwrap();
        let request=server.await.unwrap();
        assert!(request.starts_with("POST /api/auth/refresh HTTP/1.1"));
        assert!(request.contains(&format!(r#""refreshToken":"{OLD}""#)));
        assert!(!request.contains("Authorization: Bearer"));
        assert_eq!(candidate.account_id_claim,101);
        assert_eq!(candidate.access_lifetime_seconds,300);
        assert_eq!(candidate.refresh_lifetime_seconds,3600);
        assert_eq!(candidate.refresh_token.as_str(),NEW);
        assert_eq!(candidate.access_token.as_str(),ACCESS);
        assert!(!format!("{candidate:?}").contains(NEW));
        assert!(!format!("{candidate:?}").contains(ACCESS));
        // No TrustedSessionLease, NativeAccessToken or WinCred write returned.
    }

    #[tokio::test]
    async fn rejects_same_refresh_token_and_malformed_identity_claims() {
        let malformed=[
            success_json(OLD),
            success_json(NEW).replace(r#""tokenType":"Bearer""#,r#""tokenType":"basic""#),
            success_json(NEW).replace(r#""id":101"#,r#""id":-1"#),
            success_json(NEW).replace(r#""orchestrator:read""#,r#""users:read""#),
            success_json(NEW).replace(r#""accessToken":"header.payload.signature-with-synthetic-material""#,
                                      r#""accessToken":"not-jwt""#),
        ];
        for text in malformed {
            let (port,server)=mock_response("200 OK","application/json",text.into_bytes()).await;
            let protocol=NativeRefreshWire::synthetic_loopback(port).unwrap();
            assert!(matches!(protocol.prepare_refresh(&old(),&stop()).await,
                Err(NativeError::InvalidStream)));
            server.await.unwrap();
        }
    }

    #[tokio::test]
    async fn access_denial_and_non_json_response_do_not_rotate_any_credential() {
        let (port,server)=mock_response("401 Unauthorized","application/json",b"{}".to_vec()).await;
        let protocol=NativeRefreshWire::synthetic_loopback(port).unwrap();
        assert!(matches!(protocol.prepare_refresh(&old(),&stop()).await,
            Err(NativeError::AccessDenied)));
        server.await.unwrap();
        let (port,server)=mock_response("200 OK","text/html",b"<html/>".to_vec()).await;
        let protocol=NativeRefreshWire::synthetic_loopback(port).unwrap();
        assert!(matches!(protocol.prepare_refresh(&old(),&stop()).await,
            Err(NativeError::InvalidStream)));
        server.await.unwrap();
    }

    #[tokio::test]
    async fn remote_logout_contract_observes_http_204_without_attesting_revocation() {
        let (port,server)=mock_response("204 No Content","application/json",Vec::new()).await;
        let protocol=NativeRefreshWire::synthetic_loopback(port).unwrap();
        assert!(matches!(protocol.request_remote_logout(&old(),&stop()).await,
            Ok(LogoutWireResult::AcceptedHttp204NotAttested)));
        let request=server.await.unwrap();
        assert!(request.starts_with("POST /api/auth/logout HTTP/1.1"));
        assert!(request.contains(&format!(r#""refreshToken":"{OLD}""#)));
    }

    #[tokio::test]
    async fn native_stop_interrupts_stalled_refresh_before_any_rotation_candidate() {
        let listener=TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port=listener.local_addr().unwrap().port();
        let (ready_tx,ready_rx)=oneshot::channel();
        let server=tokio::spawn(async move {
            let (mut socket,_) = listener.accept().await.unwrap();
            accept_request(&mut socket).await;
            ready_tx.send(()).unwrap();
            tokio::time::sleep(Duration::from_secs(5)).await;
        });
        let client=NativeRefreshWire::synthetic_loopback(port).unwrap();
        let stopper=Arc::new(stop());
        let cloned=stopper.clone();
        let task=tokio::spawn(async move {
            client.prepare_refresh(&old(),&cloned).await
        });
        tokio::time::timeout(Duration::from_secs(2),ready_rx).await.unwrap().unwrap();
        stopper.stop();
        let answer=tokio::time::timeout(Duration::from_secs(2),task)
            .await.unwrap().unwrap();
        assert!(matches!(answer,Err(NativeError::Stopped)));
        server.abort();
    }

    #[test]
    fn refuses_noncanonical_native_refresh_bytes_and_no_public_constructor_exists() {
        for bytes in [vec![], vec![b' ';43], vec![b'a';42], vec![0;43],vec![b'a';44]] {
            let secret=Secret::new(bytes);
            if let Ok(secret)=secret {
                assert!(matches!(valid_native_refresh(&secret),Err(NativeError::InvalidToken)));
            }
        }
        // NativeRefreshWire::synthetic_loopback exists under #[cfg(test)].
        assert!(NativeRefreshWire::synthetic_loopback(0).is_err());
    }
}
