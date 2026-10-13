#![forbid(unsafe_op_in_unsafe_fn)]
//! Isolated Windows Credential Manager backend for future native Syntra sessions.
//! Not linked into the desktop shell, no Tauri commands, and no HTTP requests.
//! Review and hardware validation are required before handling real login tokens.

use zeroize::Zeroize;

pub const MAX_SECRET_BYTES: usize = 2560;

/// A fixed slot prevents callers from choosing or enumerating arbitrary Windows secrets.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Slot {
    RefreshToken,
    #[cfg(test)]
    SmokeTest,
}
impl Slot {
    pub const fn target(self) -> &'static str {
        match self {
            Self::RefreshToken => "com.aetheris.syntra.auth.refresh.v1",
            #[cfg(test)]
            Self::SmokeTest => "com.aetheris.syntra.tests.credential-smoke.v1",
        }
    }
}

/// A single error category is intentionally exposed for backend failures. Neither
/// secrets nor raw Windows error details can reach the renderer through this type.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum VaultError {
    EmptyOrOversized,
    NotFound,
    Unavailable,
    UnsupportedPlatform,
}

/// Do not derive Debug, Clone, Display, Serialize, or logging for secret material.
/// The owned buffer is zeroized upon drop; OS copies are managed by Credential Manager.
pub struct Secret(Vec<u8>);

impl Secret {
    pub fn new(bytes: Vec<u8>) -> Result<Self, VaultError> {
        let protected = Self(bytes);
        if protected.0.is_empty() || protected.0.len() > MAX_SECRET_BYTES {
            return Err(VaultError::EmptyOrOversized);
        }
        Ok(protected)
    }

    pub fn expose_for_native_use(&self) -> &[u8] {
        &self.0
    }
}

impl Drop for Secret {
    fn drop(&mut self) {
        self.0.zeroize();
    }
}

/// Store generic credential in the current Windows user's **logon session**.
/// The narrower session lifetime intentionally avoids cross-logon persistence.
pub fn store(slot: Slot, mut secret: Secret) -> Result<(), VaultError> {
    #[cfg(windows)]
    {
        use windows_sys::Win32::Security::Credentials::{
            CRED_PERSIST_SESSION, CRED_TYPE_GENERIC, CREDENTIALW, CredWriteW
        };
        let mut target = wide_target(slot);
        // A fully zeroed Windows CREDENTIALW struct is valid: its pointers and
        // integer flags are all nullable/zero until explicitly assigned below.
        let mut credential: CREDENTIALW = unsafe { std::mem::zeroed() };
        credential.Type = CRED_TYPE_GENERIC;
        credential.TargetName = target.as_mut_ptr();
        credential.CredentialBlobSize = secret.0.len() as u32;
        credential.CredentialBlob = secret.0.as_mut_ptr();
        credential.Persist = CRED_PERSIST_SESSION;
        let success = unsafe { CredWriteW(&credential, 0) };
        if success == 0 { return Err(VaultError::Unavailable); }
        return Ok(());
    }
    #[cfg(not(windows))]
    {
        let _ = (slot, &mut secret);
        Err(VaultError::UnsupportedPlatform)
    }
}

/// Read only one static credential slot; never enumerate unrelated OS secrets.
pub fn load(slot: Slot) -> Result<Secret, VaultError> {
    #[cfg(windows)]
    {
        use std::ptr;
        use windows_sys::Win32::Foundation::{GetLastError, ERROR_NOT_FOUND};
        use windows_sys::Win32::Security::Credentials::{
            CRED_TYPE_GENERIC, CREDENTIALW, CredFree, CredReadW
        };
        let target = wide_target(slot);
        let mut raw: *mut CREDENTIALW = ptr::null_mut();
        let success = unsafe {
            CredReadW(target.as_ptr(), CRED_TYPE_GENERIC, 0, &mut raw)
        };
        if success == 0 {
            // No OS error codes or target names are returned to the UI.
            let missing = unsafe { GetLastError() } == ERROR_NOT_FOUND;
            return Err(if missing { VaultError::NotFound } else { VaultError::Unavailable });
        }
        if raw.is_null() { return Err(VaultError::Unavailable); }
        // Regardless of validation results and allocation errors below, free the
        // OS-owned credential exactly once using the matching WinCred allocator.
        struct CredentialGuard(*mut CREDENTIALW);
        impl Drop for CredentialGuard {
            fn drop(&mut self) {
                unsafe { CredFree(self.0.cast()); }
            }
        }
        let guard = CredentialGuard(raw);
        let credential = unsafe { &*guard.0 };
        let len = credential.CredentialBlobSize as usize;
        if len == 0 || len > MAX_SECRET_BYTES || credential.CredentialBlob.is_null() {
            return Err(VaultError::Unavailable);
        }
        let bytes = unsafe { std::slice::from_raw_parts(credential.CredentialBlob, len) }.to_vec();
        Secret::new(bytes)
    }
    #[cfg(not(windows))]
    {
        let _ = slot;
        Err(VaultError::UnsupportedPlatform)
    }
}

/// Idempotent deletion; the only allowed target name comes from the Slot enum.
pub fn delete(slot: Slot) -> Result<(), VaultError> {
    #[cfg(windows)]
    {
        use windows_sys::Win32::Foundation::{GetLastError, ERROR_NOT_FOUND};
        use windows_sys::Win32::Security::Credentials::{CRED_TYPE_GENERIC, CredDeleteW};
        let target = wide_target(slot);
        let success = unsafe { CredDeleteW(target.as_ptr(), CRED_TYPE_GENERIC, 0) };
        if success != 0 || unsafe { GetLastError() } == ERROR_NOT_FOUND {
            return Ok(());
        }
        Err(VaultError::Unavailable)
    }
    #[cfg(not(windows))]
    {
        let _ = slot;
        Err(VaultError::UnsupportedPlatform)
    }
}

#[cfg(windows)]
fn wide_target(slot: Slot) -> Vec<u16> {
    slot.target().encode_utf16().chain(std::iter::once(0)).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_empty_and_oversized_secret_without_leaking_contents() {
        assert!(matches!(Secret::new(vec![]), Err(VaultError::EmptyOrOversized)));
        assert!(matches!(Secret::new(vec![42; MAX_SECRET_BYTES + 1]), Err(VaultError::EmptyOrOversized)));
        let secret = Secret::new(vec![7; MAX_SECRET_BYTES]).unwrap();
        assert_eq!(secret.expose_for_native_use().len(), MAX_SECRET_BYTES);
    }

    #[test]
    fn fixed_vault_slots_are_not_user_supplied_paths() {
        assert_eq!(Slot::RefreshToken.target(), "com.aetheris.syntra.auth.refresh.v1");
        assert!(Slot::SmokeTest.target().starts_with("com.aetheris.syntra.tests."));
        assert_ne!(Slot::RefreshToken.target(), Slot::SmokeTest.target());
    }

    #[test]
    fn errors_never_contain_secret_or_windows_error_detail() {
        let error = VaultError::Unavailable;
        assert_eq!(format!("{error:?}"), "Unavailable");
        assert!(!format!("{error:?}").contains("token"));
    }

    #[cfg(not(windows))]
    #[test]
    fn no_credential_backend_is_available_outside_windows() {
        assert_eq!(load(Slot::RefreshToken).err(), Some(VaultError::UnsupportedPlatform));
        assert_eq!(delete(Slot::RefreshToken), Err(VaultError::UnsupportedPlatform));
        assert_eq!(
            store(Slot::RefreshToken, Secret::new(b"test-only".to_vec()).unwrap()),
            Err(VaultError::UnsupportedPlatform)
        );
    }

    #[cfg(windows)]
    #[test]
    fn credential_manager_roundtrip_uses_disposable_ci_slot_only() {
        // No real tokens or owner credentials. Opt-in only on an isolated hosted runner.
        if std::env::var("SYNTRA_TEST_CREDENTIAL_VAULT").as_deref() != Ok("1") { return; }
        struct Cleanup;
        impl Drop for Cleanup { fn drop(&mut self) { let _ = delete(Slot::SmokeTest); } }
        let _cleanup = Cleanup;
        delete(Slot::SmokeTest).unwrap();
        store(Slot::SmokeTest, Secret::new(b"synthetic-ci-only-refresh".to_vec()).unwrap()).unwrap();
        let read = load(Slot::SmokeTest).unwrap();
        assert_eq!(read.expose_for_native_use(), b"synthetic-ci-only-refresh");
        drop(read);
        delete(Slot::SmokeTest).unwrap();
        assert_eq!(load(Slot::SmokeTest).err(), Some(VaultError::NotFound));
    }
}
