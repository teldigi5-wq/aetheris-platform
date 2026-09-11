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

The chart uses `imagePullPolicy: IfNotPresent`, which allows Docker Desktop Kubernetes to use locally built images. For kind or minikube, load these images into that cluster explicitly before installation.

## Validate the Helm chart

```powershell
helm lint .\deploy\helm\aetheris
helm template aetheris .\deploy\helm\aetheris --namespace aetheris > $null
```

Both commands should complete without errors.

## Install

```powershell
helm upgrade --install aetheris .\deploy\helm\aetheris `
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

The dashboard Nginx container sends `/api/*` and `/actuator/*` traffic to the Kubernetes Service named `gateway`, so the browser continues to use a same-origin path while Kubernetes DNS handles internal service discovery.

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

After changing chart values or templates:

```powershell
helm upgrade aetheris .\deploy\helm\aetheris -n aetheris --wait --timeout 8m
```

Example replica override:

```powershell
helm upgrade aetheris .\deploy\helm\aetheris -n aetheris `
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

The values file contains development-only local defaults. Do not use the included passwords or JWT secret in a shared, internet-facing, staging, or production cluster. Production secrets should come from an external secret manager or a separately managed Kubernetes Secret.

## Interview concepts

Be able to explain Deployments vs Pods, Services and cluster DNS, desired state and reconciliation, readiness vs liveness probes, ConfigMaps vs Secrets, PersistentVolumes/PVCs, resource requests/limits, rolling updates, replica scaling, service-side load balancing, Helm templates/values/releases, and why Docker Compose remains useful even after adding Kubernetes.
