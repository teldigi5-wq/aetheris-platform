#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use serde::{Deserialize, Serialize};
use std::time::Duration;

const RUNTIME_PROBE_TIMEOUT_SECONDS: u64 = 3;

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

fn runtime_health_url(port: u16) -> String {
    format!("http://127.0.0.1:{port}/actuator/health")
}

fn is_healthy(status: reqwest::StatusCode, reported_status: Option<&str>) -> bool {
    status.is_success() && reported_status == Some("UP")
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
            let status_label = reported_status.as_deref().unwrap_or("UNKNOWN");

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

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![probe_aetheris_runtime])
        .run(tauri::generate_context!())
        .expect("failed to run Syntra desktop shell");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn runtime_health_url_is_loopback_only_and_uses_proven_path() {
        assert_eq!(
            runtime_health_url(8090),
            "http://127.0.0.1:8090/actuator/health"
        );
    }

    #[test]
    fn health_requires_success_and_explicit_up_status() {
        assert!(is_healthy(reqwest::StatusCode::OK, Some("UP")));
        assert!(!is_healthy(
            reqwest::StatusCode::SERVICE_UNAVAILABLE,
            Some("DOWN")
        ));
        assert!(!is_healthy(reqwest::StatusCode::OK, None));
    }
}
