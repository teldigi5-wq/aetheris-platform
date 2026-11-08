# Observability stack

Run the full stack with:

```bash
docker compose --profile observability up --build
```

Then verify:

```bash
curl http://localhost:8080/actuator/prometheus
curl http://localhost:9090/-/ready
curl http://localhost:3100/ready
curl http://localhost:3200/ready
```

Open Grafana at `http://localhost:3001` with user `aetheris` and the distinct secret supplied in your private `.env` as `AETHERIS_GRAFANA_ADMIN_PASSWORD`. Never use or commit the previous default password.

In Grafana, the provisioned datasources are Prometheus, Loki, and Tempo. The provisioned dashboard is **Aetheris Platform Overview**.

## Least-privilege log forwarding

Alloy no longer mounts `/var/run/docker.sock`. Metrics and traces work without file logs.
Only operator-selected files under the **ignored** `observability/logs/` folder
are mounted read-only into Alloy. To export container logs on a trusted local
host (review and redact sensitive content before sharing):

```sh
mkdir -p observability/logs
docker compose logs --no-color --timestamps --tail=200 gateway > observability/logs/gateway.log
```

Repeat for other services as necessary. This example is a **snapshot**, not
continuous streaming. For ongoing logs run a trusted host-managed exporter;
never pass Docker's control socket into a monitoring container. Protect
operator log files from unauthorized access and apply a retention policy.
