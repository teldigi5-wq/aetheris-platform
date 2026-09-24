# Aetheris Stage 7 — Local Kubernetes + Helm

Stage 7 packages the Aetheris core platform as a Helm chart for a local Kubernetes cluster. Docker Compose remains the fastest development path; Kubernetes is the cloud-native deployment and scaling path.

## What the chart deploys

- PostgreSQL with a PersistentVolumeClaim
- Redis with append-only persistence
- RabbitMQ with durable storage and management port
- User Service
- Identity Service
- Audit Service
- Gateway
- React/Nginx dashboard
- ClusterIP Services for Kubernetes DNS/service discovery
- Kubernetes Secret for development credentials/JWT signing secret
- ConfigMap for non-secret service configuration
- Readiness and liveness probes
- Resource requests/limits
- Configurable replica counts

The Stage 5 observability stack remains available through Docker Compose while Stage 7 first validates the core platform on Kubernetes. A later chart revision can move the telemetry stack into Kubernetes as well.

## Deployment safety boundary

The chart now **fails closed by default**. The bundled credentials and mutable local image tags exist only for the Stage 7 learning/demo workflow and cannot render unless the installer makes an **explicit local-development opt-in** with `values-local.yaml`.

For a non-local/shared/staging render, leave `global.allowInsecureLocalDefaults=false`, replace every bundled demo credential, and provide digest-pinned image references containing `@sha256:` for every image in `images`. The safety gate rejects known demo passwords/JWT material and any non-digest image reference.

This chart still creates a Kubernetes Secret from Helm values. That is acceptable for the documented local demo, but it is **not** a claim of production-grade secret management. Before any real staging/production use, integrate an approved external secret-management path and keep secret material out of source control, shell history, CI logs, and reusable values files.

## Recommended local cluster: Docker Desktop Kubernetes

On Windows with Docker Desktop:

1. Open Docker Desktop.
2. Settings -> Kubernetes.
3. Enable Kubernetes and wait for the cluster to report Running.
4. Verify:

```powershell
kubectl version --client
kubectl cluster-info
kubectl get nodes
helm version
```

If Helm is not installed, install Helm 3 before continuing.

## Build the local application images

From the repository root:

```powershell
docker build -t aetheris-platform-gateway:latest .\gateway
docker build -t aetheris-platform-user-service:latest .\user-service
docker build -t aetheris-platform-identity-service:latest .\identity-service
docker build -t aetheris-platform-audit-service:latest .\audit-service
docker build -t aetheris-platform-dashboard:latest .\dashboard
```

The local values file keeps `imagePullPolicy: IfNotPresent`, which allows Docker Desktop Kubernetes to use locally built images. For kind or minikube, load these images into that cluster explicitly before installation.

## Validate the Helm chart locally

The plain/default chart intentionally fails when it sees the bundled demo credentials or mutable image tags. Use the explicit local-development values file for the Stage 7 demo validation:

```powershell
helm lint .\deploy\helm\aetheris -f .\deploy\helm\aetheris\values-local.yaml
helm template aetheris .\deploy\helm\aetheris -f .\deploy\helm\aetheris\values-local.yaml --namespace aetheris > $null
```

Both local-demo commands should complete without errors. A non-local render must instead supply strong credentials and digest-pinned images while leaving `global.allowInsecureLocalDefaults=false`.

## Install locally

```powershell
helm upgrade --install aetheris .\deploy\helm\aetheris `
  -f .\deploy\helm\aetheris\values-local.yaml `
  --namespace aetheris `
  --create-namespace `
  --wait `
  --timeout 8m
```

Check the rollout:

```powershell
kubectl get pods -n aetheris
kubectl get svc -n aetheris
kubectl get pvc -n aetheris
```

Wait until the application pods show `Running` and readiness is `1/1`.

## Open the dashboard

```powershell
kubectl port-forward -n aetheris svc/dashboard 3000:80
```

Open `http://localhost:3000`.

The dashboard Nginx container sends `/api/*` traffic and the single browser health check `/actuator/health` to the Kubernetes Service named `gateway`, so the browser continues to use same-origin paths while Kubernetes DNS handles internal service discovery. Other `/actuator/*` paths are intentionally not exposed through the dashboard proxy.

## Validate the platform

In a second PowerShell window:

```powershell
$login = Invoke-RestMethod `
  -Uri "http://localhost:3000/api/auth/login" `
  -Method POST `
  -ContentType "application/json" `
  -Body '{"email":"poojana@aetheris.local","password":"Aetheris123!"}'

$token = $login.accessToken

Invoke-RestMethod `
  -Uri "http://localhost:3000/api/users" `
  -Headers @{ Authorization = "Bearer $token" }
```

## Demonstrate replicas and Kubernetes load balancing

Scale the user service:

```powershell
kubectl scale deployment user-service -n aetheris --replicas=2
kubectl rollout status deployment/user-service -n aetheris
kubectl get pods -n aetheris -l app=user-service -o wide
```

The `user-service` Kubernetes Service keeps a stable DNS name while routing requests across ready replicas.

Return to the lower-memory default afterward:

```powershell
kubectl scale deployment user-service -n aetheris --replicas=1
```

## Demonstrate self-healing

List the user-service pods and delete one:

```powershell
kubectl get pods -n aetheris -l app=user-service
kubectl delete pod -n aetheris -l app=user-service
kubectl get pods -n aetheris -l app=user-service -w
```

The Deployment controller creates a replacement pod automatically.

## Helm upgrade

After changing chart values or templates in the local demo:

```powershell
helm upgrade aetheris .\deploy\helm\aetheris -f .\deploy\helm\aetheris\values-local.yaml -n aetheris --wait --timeout 8m
```

Example replica override:

```powershell
helm upgrade aetheris .\deploy\helm\aetheris -f .\deploy\helm\aetheris\values-local.yaml -n aetheris `
  --set replicas.gateway=2 `
  --set replicas.userService=2 `
  --wait
```

## Uninstall

```powershell
helm uninstall aetheris -n aetheris
```

PersistentVolumeClaims intentionally remain Kubernetes-managed data resources until explicitly deleted:

```powershell
kubectl delete namespace aetheris
```

Only delete the namespace when its persisted local test data is no longer needed.

## Security note

`values-local.yaml` is an explicit acknowledgement that the bundled credentials and mutable local image tags are for a single-developer learning/demo cluster only. Do not use it for a shared, internet-facing, staging, or production cluster.

The default chart safety gate rejects those local defaults. Non-local deployments must use strong replacement credentials, digest-pinned images, and an approved secret-management design before any production-readiness claim.

The dashboard Nginx layer emits baseline browser security headers and limits its Actuator proxy to `/actuator/health`. The current Stage 8–23 static consoles still contain inline script/style blocks, so the dashboard CSP explicitly permits inline script/style execution for compatibility. Removing that allowance requires first extracting those legacy inline blocks into external assets; the current CSP should not be described as strict nonce/hash-based CSP.

## Interview concepts

Be able to explain Deployments vs Pods, Services and cluster DNS, desired state and reconciliation, readiness vs liveness probes, ConfigMaps vs Secrets, PersistentVolumes/PVCs, resource requests/limits, rolling updates, replica scaling, service-side load balancing, Helm templates/values/releases, deployment safety gates, immutable image digests, and why Docker Compose remains useful even after adding Kubernetes.
