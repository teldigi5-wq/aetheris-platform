//! Native-only revocation/expiry supervisor for the pending Syntra stream.
//!
//! Fails closed: no production issuer of TrustedSessionLease exists, so no
//! authenticated session can be started by Tauri/JS or an untrusted JWT.
//! All successful lease creation in this slice is cfg(test) and synthetic.
//!
//! Logout: stop native network reads FIRST, then attempt WinCred refresh-slot
//! deletion. Local deletion is NOT server-side refresh-token revocation,
//! nor does native STOP cancel commands already executing on the backend.

use crate::async_transport::{InterruptibleMissionClient, NativeStopHandle};
use crate::{MissionUpdate, NativeAccessToken, NativeError, VerifiedNativeSession};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::time::{Duration, Instant};

/// This lease is a private capability for a future separately audited native
/// login/identity verifier. A token payload, UI flag, account name or claimed
/// expiry cannot construct it. There is intentionally NO production issuer.
pub struct TrustedSessionLease {
    epoch: u64,
    deadline: Instant,
}
impl std::fmt::Debug for TrustedSessionLease {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("TrustedSessionLease([REDACTED])")
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum LocalLogoutResult {
    LocalRefreshSlotCleared,
    LocalRefreshSlotClearFailed,
}

/// Only clears a local refresh-token slot. This does NOT revoke a remote
/// rotating refresh token at /api/auth/logout and is never called for an
/// unrelated Windows Credential Manager target.
pub trait RefreshSlotRevoker: Send + Sync {
    fn clear_local_refresh_slot(&self) -> Result<(), ()>;
}

pub struct WindowsRefreshSlotRevoker;
impl RefreshSlotRevoker for WindowsRefreshSlotRevoker {
    fn clear_local_refresh_slot(&self) -> Result<(), ()> {
        use syntra_credential_vault::{delete, Slot, VaultError};
        match delete(Slot::RefreshToken) {
            Ok(()) | Err(VaultError::NotFound) => Ok(()),
            Err(_) => Err(()),
        }
    }
}

pub struct NativeSessionSupervisor {
    client: InterruptibleMissionClient,
    stop: NativeStopHandle,
    revoked: AtomicBool,
    epoch: AtomicU64,
}

impl NativeSessionSupervisor {
    /// Construction is NOT an auth grant; the lease, token and session remain
    /// unconstructible from the app. No network requests start here.
    pub fn new_loopback(port: u16) -> Result<Self, NativeError> {
        let client = InterruptibleMissionClient::new_loopback(port)?;
        let stop = client.stop_handle();
        Ok(Self { client, stop, revoked: AtomicBool::new(false), epoch: AtomicU64::new(1) })
    }

    pub fn is_revoked(&self) -> bool {
        self.revoked.load(Ordering::SeqCst)
    }

    fn invalidate(&self) {
        self.revoked.store(true, Ordering::SeqCst);
        self.epoch.fetch_add(1, Ordering::SeqCst);
        self.stop.stop();
    }

    /// Explicit logout/STOP. Always closes the local network channel first,
    /// EVEN if the OS vault cannot delete a refresh token. Do not return
    /// remote-logout-success or a leaked Windows error code.
    pub fn logout<R: RefreshSlotRevoker>(&self, vault: &R) -> LocalLogoutResult {
        self.invalidate();
        match vault.clear_local_refresh_slot() {
            Ok(()) => LocalLogoutResult::LocalRefreshSlotCleared,
            Err(()) => LocalLogoutResult::LocalRefreshSlotClearFailed,
        }
    }

    /// Must be awaited by the future native session owner. Expiry and STOP
    /// both drop the in-flight network future; no detached background read.
    /// A received SSE frame does NOT update a server acknowledgement.
    pub async fn guarded_read(
        &self, session: &VerifiedNativeSession,
        token: &NativeAccessToken, lease: &TrustedSessionLease,
    ) -> Result<Vec<MissionUpdate>, NativeError> {
        if self.is_revoked() || self.stop.is_stopped() {
            return Err(NativeError::Stopped);
        }
        if lease.epoch != self.epoch.load(Ordering::SeqCst) {
            return Err(NativeError::AccessDenied);
        }
        if Instant::now() >= lease.deadline {
            self.invalidate();
            return Err(NativeError::SessionExpired);
        }
        let wait = lease.deadline.saturating_duration_since(Instant::now());
        let output = tokio::select! {
            biased;
            _ = tokio::time::sleep(wait) => {
                self.invalidate();
                return Err(NativeError::SessionExpired);
            }
            result = self.client.read_once(session, token) => result,
        };
        if self.is_revoked() || self.stop.is_stopped()
            || lease.epoch != self.epoch.load(Ordering::SeqCst) {
            return Err(NativeError::Stopped);
        }
        if Instant::now() >= lease.deadline {
            self.invalidate();
            return Err(NativeError::SessionExpired);
        }
        output
    }

    #[cfg(test)]
    fn synthetic_lease(&self, ttl: Duration) -> TrustedSessionLease {
        TrustedSessionLease {
            epoch: self.epoch.load(Ordering::SeqCst),
            deadline: Instant::now() + ttl,
        }
    }
}

impl Drop for NativeSessionSupervisor {
    fn drop(&mut self) {
        self.invalidate();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::VerifiedNativeSession;
    use std::sync::atomic::{AtomicUsize, Ordering};
    use tokio::io::{AsyncReadExt, AsyncWriteExt};
    use tokio::net::TcpListener;
    use tokio::sync::oneshot;
    use zeroize::Zeroizing;

    const TASK: &str = "11aa11aa-1111-4111-8111-111111111111";
    const CHECKPOINT: &str = "33cc33cc-3333-4333-8333-333333333333";

    fn fake_session() -> VerifiedNativeSession {
        VerifiedNativeSession { task_id: TASK.into(),
            checkpoint_id: CHECKPOINT.into(), last_ack_sequence: 0 }
    }
    fn fake_token() -> NativeAccessToken {
        NativeAccessToken(Zeroizing::new("synthetic.header.signature".into()))
    }
    struct TestVault {
        calls: AtomicUsize,
        fail: bool,
    }
    impl TestVault {
        fn new(fail: bool) -> Self { Self { calls: AtomicUsize::new(0), fail } }
    }
    impl RefreshSlotRevoker for TestVault {
        fn clear_local_refresh_slot(&self) -> Result<(), ()> {
            self.calls.fetch_add(1, Ordering::SeqCst);
            if self.fail { Err(()) } else { Ok(()) }
        }
    }

    async fn wait_request(socket: &mut tokio::net::TcpStream) {
        let mut buf=[0u8;2048]; let mut collected=Vec::new();
        while !collected.windows(4).any(|w|w==b"\r\n\r\n") {
            let n=socket.read(&mut buf).await.unwrap();
            assert!(n>0 && collected.len()+n<8192);
            collected.extend_from_slice(&buf[..n]);
        }
    }

    #[tokio::test]
    async fn expired_lease_aborts_a_blocked_response_without_waiting_for_network_timeout() {
        let listener=TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port=listener.local_addr().unwrap().port();
        let (tx,rx)=oneshot::channel();
        let server=tokio::spawn(async move {
            let (mut socket,_) = listener.accept().await.unwrap();
            wait_request(&mut socket).await;
            tx.send(()).unwrap();
            tokio::time::sleep(Duration::from_secs(4)).await;
        });
        let supervisor=NativeSessionSupervisor::new_loopback(port).unwrap();
        let lease=supervisor.synthetic_lease(Duration::from_millis(650));
        let synthetic_session=fake_session();
        let synthetic_token=fake_token();
        let read=supervisor.guarded_read(&synthetic_session,&synthetic_token,&lease);
        let (ready,result)=tokio::join!(
            tokio::time::timeout(Duration::from_secs(2),rx),
            tokio::time::timeout(Duration::from_secs(2),read)
        );
        ready.unwrap().unwrap();
        assert_eq!(result.unwrap().unwrap_err(),NativeError::SessionExpired);
        assert!(supervisor.is_revoked());
        assert_eq!(supervisor.guarded_read(&fake_session(),&fake_token(),&lease).await.unwrap_err(),
            NativeError::Stopped);
        server.abort();
    }

    #[tokio::test]
    async fn logout_cancels_stalled_body_before_vault_clear_and_does_not_claim_remote_revoke() {
        let listener=TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port=listener.local_addr().unwrap().port();
        let (tx,rx)=oneshot::channel();
        let server=tokio::spawn(async move {
            let (mut socket,_) = listener.accept().await.unwrap();
            wait_request(&mut socket).await;
            socket.write_all(b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nTransfer-Encoding: chunked\r\n\r\n").await.unwrap();
            tx.send(()).unwrap();
            tokio::time::sleep(Duration::from_secs(4)).await;
        });
        let supervisor=std::sync::Arc::new(NativeSessionSupervisor::new_loopback(port).unwrap());
        let lease=supervisor.synthetic_lease(Duration::from_secs(10));
        let running=supervisor.clone();
        let read=tokio::spawn(async move {
            running.guarded_read(&fake_session(),&fake_token(),&lease).await
        });
        tokio::time::timeout(Duration::from_secs(2),rx).await.unwrap().unwrap();
        let vault=TestVault::new(false);
        let start=Instant::now();
        assert_eq!(supervisor.logout(&vault),LocalLogoutResult::LocalRefreshSlotCleared);
        assert_eq!(tokio::time::timeout(Duration::from_secs(2),read).await.unwrap()
            .unwrap().unwrap_err(),NativeError::Stopped);
        assert!(start.elapsed()<Duration::from_secs(2));
        assert_eq!(vault.calls.load(Ordering::SeqCst),1);
        assert!(supervisor.is_revoked());
        server.abort();
    }

    #[tokio::test]
    async fn failed_os_vault_delete_still_invalidates_all_native_streams() {
        let supervisor=NativeSessionSupervisor::new_loopback(8090).unwrap();
        let lease=supervisor.synthetic_lease(Duration::from_secs(60));
        let vault=TestVault::new(true);
        assert_eq!(supervisor.logout(&vault),LocalLogoutResult::LocalRefreshSlotClearFailed);
        assert!(supervisor.is_revoked());
        assert_eq!(supervisor.guarded_read(&fake_session(),&fake_token(),&lease)
            .await.unwrap_err(),NativeError::Stopped);
        assert_eq!(vault.calls.load(Ordering::SeqCst),1);
        assert_eq!(format!("{:?}",supervisor.logout(&vault)),
            "LocalRefreshSlotClearFailed");
    }

    #[tokio::test]
    async fn expired_or_invalid_epoch_cannot_reactivate_native_channel() {
        let supervisor=NativeSessionSupervisor::new_loopback(8090).unwrap();
        let expiry=supervisor.synthetic_lease(Duration::from_millis(0));
        assert_eq!(supervisor.guarded_read(&fake_session(),&fake_token(),&expiry)
            .await.unwrap_err(),NativeError::SessionExpired);
        let fresh=supervisor.synthetic_lease(Duration::from_secs(60));
        assert_eq!(supervisor.guarded_read(&fake_session(),&fake_token(),&fresh)
            .await.unwrap_err(),NativeError::Stopped);
        assert!(supervisor.is_revoked());
    }

    #[test]
    fn production_os_revoke_adapter_touches_only_fixed_refresh_slot() {
        let target=syntra_credential_vault::Slot::RefreshToken.target();
        assert_eq!(target,"com.aetheris.syntra.auth.refresh.v1");
        assert!(!target.contains("credential-smoke"));
        assert!(!format!("{:?}",TrustedSessionLease {
            epoch: 1, deadline:Instant::now()+Duration::from_secs(60)
        }).contains("epoch"));
    }
}
