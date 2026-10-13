//! Pure native authentication-readiness policy. No credentials, HTTP, or Tauri IPC.
//! TrustedEvidence values must only be derived from future audited native controls.
//! No frontend-provided value is accepted as a proof of authentication.

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ReadGate {
    OsVaultUnavailable,
    SignedSessionMissing,
    SignedSessionExpired,
    ReadScopeUnverified,
    GatewayProofMissing,
    OwnerConsentMissing,
    HardwareUncertified,
    ReadOnlyPolicySatisfied,
}

impl ReadGate {
    pub fn code(self) -> &'static str {
        match self {
            Self::OsVaultUnavailable => "BLOCKED_NATIVE_CREDENTIAL_BOUNDARY",
            Self::SignedSessionMissing => "BLOCKED_SIGNED_SESSION",
            Self::SignedSessionExpired => "BLOCKED_EXPIRED_SESSION",
            Self::ReadScopeUnverified => "BLOCKED_READ_SCOPE",
            Self::GatewayProofMissing => "BLOCKED_GATEWAY_PROOF",
            Self::OwnerConsentMissing => "BLOCKED_OWNER_CONSENT",
            Self::HardwareUncertified => "BLOCKED_PENDING_HARDWARE",
            Self::ReadOnlyPolicySatisfied => "READ_POLICY_ELIGIBLE_NOT_EXECUTED",
        }
    }

    pub fn can_attempt_read(self) -> bool {
        matches!(self, Self::ReadOnlyPolicySatisfied)
    }
}

/// Hypothetical evidence for testing the *policy*, not a bearer token or login.
/// Real evidence providers, credential vault, and native HTTP client do not exist yet.
#[derive(Clone, Copy, Debug, Default)]
pub struct TrustedEvidence {
    pub os_vault_verified: bool,
    pub signed_session_verified: bool,
    pub session_expires_at_unix: Option<u64>,
    pub read_scope_verified: bool,
    pub gateway_auth_verified: bool,
    pub owner_consent_verified: bool,
    pub hardware_certified: bool,
}

pub fn evaluate(e: &TrustedEvidence, now_unix: u64) -> ReadGate {
    if !e.os_vault_verified {
        return ReadGate::OsVaultUnavailable;
    }
    if !e.signed_session_verified {
        return ReadGate::SignedSessionMissing;
    }
    let Some(expires) = e.session_expires_at_unix else {
        return ReadGate::SignedSessionExpired;
    };
    if expires <= now_unix {
        return ReadGate::SignedSessionExpired;
    }
    if !e.read_scope_verified {
        return ReadGate::ReadScopeUnverified;
    }
    if !e.gateway_auth_verified {
        return ReadGate::GatewayProofMissing;
    }
    if !e.owner_consent_verified {
        return ReadGate::OwnerConsentMissing;
    }
    if !e.hardware_certified {
        return ReadGate::HardwareUncertified;
    }
    ReadGate::ReadOnlyPolicySatisfied
}

/// Only this fail-closed snapshot is exposed by the current Tauri command.
/// Do not feed UI input, localStorage or remote JSON into the native trust policy.
pub fn pre_pc_report() -> ReadGate {
    evaluate(&TrustedEvidence::default(), 0)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn hypothetical_complete() -> TrustedEvidence {
        TrustedEvidence {
            os_vault_verified: true,
            signed_session_verified: true,
            session_expires_at_unix: Some(500),
            read_scope_verified: true,
            gateway_auth_verified: true,
            owner_consent_verified: true,
            hardware_certified: true,
        }
    }

    #[test]
    fn actual_native_readiness_is_blocked_without_os_vault() {
        let gate = pre_pc_report();
        assert_eq!(gate, ReadGate::OsVaultUnavailable);
        assert_eq!(gate.code(), "BLOCKED_NATIVE_CREDENTIAL_BOUNDARY");
        assert!(!gate.can_attempt_read());
    }

    #[test]
    fn fail_closed_on_every_independent_missing_trust_proof() {
        let complete = hypothetical_complete();
        let expected = [
            (TrustedEvidence { os_vault_verified: false, ..complete }, ReadGate::OsVaultUnavailable),
            (TrustedEvidence { signed_session_verified: false, ..complete }, ReadGate::SignedSessionMissing),
            (TrustedEvidence { session_expires_at_unix: None, ..complete }, ReadGate::SignedSessionExpired),
            (TrustedEvidence { session_expires_at_unix: Some(100), ..complete }, ReadGate::SignedSessionExpired),
            (TrustedEvidence { read_scope_verified: false, ..complete }, ReadGate::ReadScopeUnverified),
            (TrustedEvidence { gateway_auth_verified: false, ..complete }, ReadGate::GatewayProofMissing),
            (TrustedEvidence { owner_consent_verified: false, ..complete }, ReadGate::OwnerConsentMissing),
            (TrustedEvidence { hardware_certified: false, ..complete }, ReadGate::HardwareUncertified),
        ];
        for (e, reason) in expected {
            assert_eq!(evaluate(&e, 100), reason);
            assert!(!evaluate(&e, 100).can_attempt_read());
        }
    }

    #[test]
    fn hypothetical_complete_policy_is_not_a_real_authenticated_session() {
        let e = hypothetical_complete();
        assert_eq!(evaluate(&e, 100), ReadGate::ReadOnlyPolicySatisfied);
        assert!(evaluate(&e, 100).can_attempt_read());
        assert_eq!(evaluate(&e, 500), ReadGate::SignedSessionExpired);
        // This test only uses mock proofs; the exposed pre-PC snapshot always remains blocked.
        assert_eq!(pre_pc_report(), ReadGate::OsVaultUnavailable);
    }

    #[test]
    fn expiry_at_now_or_missing_is_not_accepted() {
        let e = hypothetical_complete();
        for exp in [None, Some(0), Some(100), Some(499)] {
            let e2 = TrustedEvidence { session_expires_at_unix: exp, ..e };
            assert_eq!(evaluate(&e2, 500), ReadGate::SignedSessionExpired);
        }
    }
}
