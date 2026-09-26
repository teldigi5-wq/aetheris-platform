#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use serde::{Deserialize, Serialize};
use std::{fs, path::PathBuf, time::{Duration, SystemTime, UNIX_EPOCH}};

const RUNTIME_PROBE_TIMEOUT_SECONDS: u64 = 3;
const PHYSICAL_PC_STATUS: &str = "BLOCKED_PENDING_HARDWARE";
const BACKEND_WIRING_STATUS: &str = "not-wired-no-success-claims";

#[derive(Debug, Deserialize)]
struct ActuatorHealth {
    status: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct RuntimeHealthProbe {
    endpoint: String,
    reachable: bool,
    healthy: bool,
    http_status: Option<u16>,
    reported_status: Option<String>,
    evidence: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DiagnosticsPayload {
    control_state: String,
    gateway_base: String,
    runtime_port: u16,
    gateway_state: String,
    runtime_state: String,
    privacy_profile: String,
    startup_behavior: String,
    prompt_count: usize,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct DiagnosticsReport {
    schema_version: &'static str,
    syntra_version: &'static str,
    generated_unix_seconds: u64,
    physical_pc_status: &'static str,
    backend_command_wiring_status: &'static str,
    control_state: String,
    gateway_base: String,
    runtime_port: u16,
    gateway_state: String,
    runtime_state: String,
    privacy_profile: String,
    startup_behavior: String,
    prompt_count: usize,
    secret_material_included: bool,
    prompt_content_included: bool,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct DiagnosticsExport {
    path: String,
    generated_unix_seconds: u64,
    evidence: String,
}

fn runtime_health_url(port: u16) -> String {
    format!("http://127.0.0.1:{port}/actuator/health")
}

fn is_healthy(status: reqwest::StatusCode, reported_status: Option<&str>) -> bool {
    status.is_success() && reported_status == Some("UP")
}

fn is_loopback_http_base(value: &str) -> bool {
    let Ok(url) = reqwest::Url::parse(value) else { return false; };
    let host_ok = matches!(url.host_str(), Some("127.0.0.1") | Some("localhost"));
    url.scheme() == "http"
        && host_ok
        && url.path() == "/"
        && url.query().is_none()
        && url.fragment().is_none()
        && url.username().is_empty()
        && url.password().is_none()
}

fn allowed_label(value: &str, allowed: &[&str]) -> bool {
    allowed.iter().any(|candidate| *candidate == value)
}

fn diagnostics_path() -> PathBuf {
    std::env::temp_dir()
        .join("SyntraDiagnostics")
        .join("syntra-diagnostics-latest.json")
}

#[tauri::command]
async fn probe_aetheris_runtime(port: u16) -> RuntimeHealthProbe {
    let endpoint = runtime_health_url(port);
    if port == 0 {
        return RuntimeHealthProbe {
            endpoint,
            reachable: false,
            healthy: false,
            http_status: None,
            reported_status: None,
            evidence: "Runtime probe rejected port 0; a valid loopback port is required.".to_string(),
        };
    }

    let client = match reqwest::Client::builder()
        .timeout(Duration::from_secs(RUNTIME_PROBE_TIMEOUT_SECONDS))
        .build()
    {
        Ok(client) => client,
        Err(error) => {
            return RuntimeHealthProbe {
                endpoint,
                reachable: false,
                healthy: false,
                http_status: None,
                reported_status: None,
                evidence: format!("Failed to create the local runtime probe client: {error}"),
            };
        }
    };

    match client.get(&endpoint).send().await {
        Ok(response) => {
            let status = response.status();
            let http_status = status.as_u16();
            let reported_status = response
                .json::<ActuatorHealth>()
                .await
                .ok()
                .and_then(|health| health.status);
            let healthy = is_healthy(status, reported_status.as_deref());
            let status_label = reported_status.clone().unwrap_or_else(|| "UNKNOWN".to_string());

            RuntimeHealthProbe {
                endpoint,
                reachable: true,
                healthy,
                http_status: Some(http_status),
                reported_status,
                evidence: format!(
                    "Loopback runtime health probe returned HTTP {http_status} with actuator status {status_label}."
                ),
            }
        }
        Err(error) => RuntimeHealthProbe {
            endpoint,
            reachable: false,
            healthy: false,
            http_status: None,
            reported_status: None,
            evidence: format!("No HTTP response was received from the loopback runtime health endpoint: {error}"),
        },
    }
}

#[tauri::command]
fn export_diagnostics(payload: DiagnosticsPayload) -> Result<DiagnosticsExport, String> {
    if !is_loopback_http_base(&payload.gateway_base) {
        return Err("Diagnostics export rejected a non-loopback gateway base.".to_string());
    }
    if payload.runtime_port == 0 {
        return Err("Diagnostics export requires a valid runtime port.".to_string());
    }
    if !allowed_label(&payload.control_state, &["NORMAL", "STOPPED", "PAUSED", "OWNER_CONTROL"]) {
        return Err("Diagnostics export rejected an unknown local control state.".to_string());
    }
    if !allowed_label(&payload.gateway_state, &["NOT_PROBED", "UP", "UNAVAILABLE", "DEGRADED", "ERROR"]) ||
       !allowed_label(&payload.runtime_state, &["NOT_PROBED", "UP", "UNAVAILABLE", "DEGRADED", "ERROR"]) {
        return Err("Diagnostics export rejected an unknown health state.".to_string());
    }
    if !allowed_label(&payload.privacy_profile, &["private", "balanced"]) ||
       !allowed_label(&payload.startup_behavior, &["manual", "remember"]) {
        return Err("Diagnostics export rejected an unknown local preference.".to_string());
    }

    let generated_unix_seconds = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_err(|error| format!("System clock is before the Unix epoch: {error}"))?
        .as_secs();

    let report = DiagnosticsReport {
        schema_version: "1",
        syntra_version: env!("CARGO_PKG_VERSION"),
        generated_unix_seconds,
        physical_pc_status: PHYSICAL_PC_STATUS,
        backend_command_wiring_status: BACKEND_WIRING_STATUS,
        control_state: payload.control_state,
        gateway_base: payload.gateway_base,
        runtime_port: payload.runtime_port,
        gateway_state: payload.gateway_state,
        runtime_state: payload.runtime_state,
        privacy_profile: payload.privacy_profile,
        startup_behavior: payload.startup_behavior,
        prompt_count: payload.prompt_count,
        secret_material_included: false,
        prompt_content_included: false,
    };

    let path = diagnostics_path();
    let parent = path.parent().ok_or_else(|| "Diagnostics path has no parent directory.".to_string())?;
    fs::create_dir_all(parent).map_err(|error| format!("Failed to create diagnostics directory: {error}"))?;
    let json = serde_json::to_string_pretty(&report)
        .map_err(|error| format!("Failed to serialize diagnostics report: {error}"))?;
    fs::write(&path, format!("{json}\n"))
        .map_err(|error| format!("Failed to write diagnostics report: {error}"))?;

    Ok(DiagnosticsExport {
        path: path.to_string_lossy().into_owned(),
        generated_unix_seconds,
        evidence: "Sanitized diagnostics report written to the current user's temporary directory; prompt content and secret material are excluded.".to_string(),
    })
}

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![probe_aetheris_runtime, export_diagnostics])
        .run(tauri::generate_context!())
        .expect("failed to run Syntra desktop shell");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn runtime_health_url_is_loopback_only_and_uses_proven_path() {
        assert_eq!(runtime_health_url(8090), "http://127.0.0.1:8090/actuator/health");
    }

    #[test]
    fn health_requires_success_and_explicit_up_status() {
        assert!(is_healthy(reqwest::StatusCode::OK, Some("UP")));
        assert!(!is_healthy(reqwest::StatusCode::SERVICE_UNAVAILABLE, Some("DOWN")));
        assert!(!is_healthy(reqwest::StatusCode::OK, None));
    }

    #[test]
    fn diagnostics_gateway_validation_rejects_remote_or_credentialed_urls() {
        assert!(is_loopback_http_base("http://127.0.0.1:8080"));
        assert!(is_loopback_http_base("http://localhost:8080"));
        assert!(!is_loopback_http_base("https://127.0.0.1:8080"));
        assert!(!is_loopback_http_base("http://example.com:8080"));
        assert!(!is_loopback_http_base("http://user:pass@localhost:8080"));
        assert!(!is_loopback_http_base("http://localhost:8080/path"));
    }

    #[test]
    fn diagnostics_labels_are_explicit_allowlists() {
        assert!(allowed_label("UP", &["NOT_PROBED", "UP", "UNAVAILABLE"]));
        assert!(!allowed_label("token=secret", &["NOT_PROBED", "UP", "UNAVAILABLE"]));
    }
}
