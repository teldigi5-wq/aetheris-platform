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

Open Grafana at `http://localhost:3001` with local credentials `aetheris` / `aetheris`.

In Grafana, the provisioned datasources are Prometheus, Loki, and Tempo. The provisioned dashboard is **Aetheris Platform Overview**.
