# Running Content Calendar on Kubernetes (minikube)

This guide covers how the Content Calendar app (Spring Boot 4.1.2) and its PostgreSQL database run on a local **minikube** cluster (Windows). The manifests are in the `k8s/` folder. **ArgoCD** applies them automatically from GitHub, and **Jenkins** builds the image and updates its tag.

> **Use PowerShell, not Git Bash, for these commands.** `minikube` commands don't work properly in Git Bash on Windows.

---

## 1. The files

The files are numbered so they are easy to read in order.

| File | What it does |
|---|---|
| `01-postgres-pvc.yaml` | Requests 1 GiB of disk for the database, so the data survives pod restarts. minikube creates the volume automatically. |
| `02-postgres-deployment.yaml` | Runs one `postgres:18-alpine` pod (`strategy: Recreate`, because the volume can be used by only one pod). It reads `POSTGRES_USER` and `POSTGRES_PASSWORD` from the `app-secrets` Secret. |
| `03-postgres-service.yaml` | Gives the database the fixed name **`db`** on port 5432, reachable only inside the cluster (ClusterIP). |
| `04-app-configmap.yaml` | Non-secret settings: `SPRING_PROFILES_ACTIVE=production`, `PGHOST=db`, `PGPORT=5432`, `PGDATABASE=postgres`. |
| `05-app-deployment.yaml` | Runs **2 replicas** of `ahmedelhawary1/content-calendar:<tag>` from Docker Hub. It reads `PGUSER`, `PGPASSWORD` and `JWT_SECRET` from `app-secrets`, and has health probes on `/actuator/health`. |
| `06-app-service.yaml` | Opens the app outside the cluster on **NodePort 30080**. |

**How the app finds the database:** `application-production.properties` builds the URL from the environment:
```
jdbc:postgresql://${PGHOST}:${PGPORT}/${PGDATABASE}
```
which here becomes `jdbc:postgresql://db:5432/postgres`.

---

## 2. Secrets (not in Git)

The database password and the JWT secret are **not** stored in the repo. They live in a Secret called **`app-secrets`**, which you create once from your local `.env` file.

1. Copy `.env.example` to `.env` and put real values in it. `.env` is in `.gitignore`, so it is never committed.
2. Create the Secret:
   ```powershell
   kubectl create secret generic app-secrets --from-env-file=.env
   kubectl describe secret app-secrets     # should list JWT_SECRET, POSTGRES_PASSWORD, POSTGRES_USER
   ```

ArgoCD does **not** manage `app-secrets` (it isn't in `k8s/`), so it never changes or deletes it.

### Changing a secret later

Pods read the Secret only when they start, so restart them after updating it:
```powershell
kubectl create secret generic app-secrets --from-env-file=.env --dry-run=client -o yaml | kubectl apply -f -
kubectl rollout restart deployment/content-calendar
```

**Changing the database password** needs one extra step first. Postgres reads `POSTGRES_PASSWORD` only the first time it creates the database on the PVC, so change it inside the database before updating the Secret:
```powershell
kubectl exec -it deployment/postgres -- psql -U postgres
# then inside psql:  \password postgres   and   \q
```

---

## 3. How a change reaches the cluster

1. You push code to `main`.
2. **Jenkins** (polls every 2 minutes) runs the tests, builds the image, pushes it to Docker Hub as `ahmedelhawary1/content-calendar:<build number>`, updates the `image:` line in `k8s/05-app-deployment.yaml`, and pushes a `ci: update image to N` commit.
3. **ArgoCD** (checks every 3 minutes, or press REFRESH) sees the change in `k8s/` and applies it.
4. Kubernetes does a **rolling update**: a new pod starts, and the old one stops only when the new one is ready.

Because ArgoCD has `selfHeal` and `prune` on, change things **in Git, not with `kubectl apply`**. Manual changes are reverted, and resources whose files are removed from `k8s/` are deleted.

---

## 4. The order things start in

1. The PVC, ConfigMap and Services are created immediately.
2. **The Postgres pod** starts and becomes ready once `pg_isready` succeeds.
3. **Each app pod** starts with an init container, **`wait-for-db`**, which runs `pg_isready -h db` every 2 seconds until the database answers. Without it, Spring Boot would start too early and crash (`CrashLoopBackOff`).
4. **Spring Boot starts**, connects to `db:5432`, runs `schema.sql` and loads the sample data.
5. **The readiness probe** checks `/actuator/health` after 30 seconds. When it answers `UP`, the pod receives traffic.
6. **The liveness probe** starts after 60 seconds. If it fails 3 times in a row, Kubernetes restarts the container.

---

## 5. Commands

### Start minikube
```powershell
minikube start
minikube status
```

### Check the pods
```powershell
kubectl get pods                 # READY 1/1, STATUS Running
kubectl get pods -w              # watch live (Ctrl+C to stop)
kubectl get pvc                  # STATUS should be Bound
kubectl describe pod -l app=content-calendar   # events if something is wrong
kubectl get deployment content-calendar -o jsonpath="{.spec.template.spec.containers[0].image}"   # current image
```
While the app waits for the database, its STATUS shows **`Init:0/1`**.

### See the logs
```powershell
kubectl logs deployment/content-calendar
kubectl logs deployment/content-calendar -c wait-for-db
kubectl logs deployment/postgres
```

### Open the app

**Option 1:**
```powershell
minikube service content-calendar --url
```
With the Docker driver, keep that window open (it's a tunnel). With a VM driver, you can also open `http://<minikube ip>:30080` directly.

**Option 2:**
```powershell
kubectl port-forward service/content-calendar 9091:8080
```
Then open http://localhost:9091.

Useful paths: `/` (welcome message), `/api/content`, `/actuator/health`.

### Open ArgoCD
```powershell
kubectl port-forward svc/argocd-server -n argocd 8443:443
```
Then open https://localhost:8443.

### Validate a manifest before pushing
```powershell
kubectl apply --dry-run=client -f k8s/
```

---

## 6. Good to know

- **A Kubernetes Secret isn't encrypted**, only base64-encoded. That's fine for local learning. A real cluster would use something like Sealed Secrets or a cloud secret manager.
- **The probes use `/actuator/health`, which includes the database check.** If Postgres goes down, the app pods are restarted until it's back. Spring Boot also offers `/actuator/health/liveness` and `/actuator/health/readiness` if you later want to separate them.
- **Sample data with 2 replicas:** `DataLoader` runs in every app pod at startup, so on an empty database it can load the sample data more than once.
- **Deleting the PVC deletes the database data.** Postgres must stay at 1 replica because its volume (`ReadWriteOnce`) can be used by only one pod.
