# Running Content Calendar on Kubernetes (minikube)

This guide deploys the Content Calendar app (Spring Boot 4.1.2) and its PostgreSQL database on a local **minikube** cluster (Windows, Docker driver). All the manifests are in the `k8s/` folder, and one command deploys everything:

```powershell
kubectl apply -f k8s/
```

> **Use PowerShell, not Git Bash, for these commands.** `minikube` commands, such as `minikube image load` and `minikube service`, don't work properly in Git Bash on Windows. `kubectl` works in both, but it's simpler to use PowerShell for everything.

---

## 1. The files

The files are numbered because `kubectl apply -f k8s/` reads them in alphabetical order.

| File | What it does |
|---|---|
| `01-postgres-secret.yaml` | Stores the database username and password (`postgres` / `password`). Postgres uses it to create the user, and the app uses it to log in. |
| `02-postgres-pvc.yaml` | Requests 1 GiB of disk for the database, so the data survives pod restarts. minikube creates the volume automatically. |
| `03-postgres-deployment.yaml` | Runs one `postgres:18-alpine` pod, with its data volume at `/var/lib/postgresql`. The pod counts as ready once `pg_isready` succeeds. |
| `04-postgres-service.yaml` | Gives the database the fixed name **`db`** on port 5432. It's only reachable inside the cluster (ClusterIP). |
| `05-app-configmap.yaml` | Holds the non-secret app settings: `SPRING_PROFILES_ACTIVE=production`, `PGHOST=db`, `PGPORT=5432` and `PGDATABASE=postgres`. |
| `06-app-deployment.yaml` | Runs one pod of the app image `content-calendar:0.0.1-SNAPSHOT` (`imagePullPolicy: Never`). It reads `PGUSER` and `PGPASSWORD` from the Secret, has health probes on `/actuator/health`, and sets small CPU and memory limits. |
| `07-app-service.yaml` | Opens the app outside the cluster on **NodePort 30080**. |

**How the app finds the database:** `application-production.properties` builds the database URL from the environment:
```
jdbc:postgresql://${PGHOST}:${PGPORT}/${PGDATABASE}
```
which here becomes `jdbc:postgresql://db:5432/postgres`.

---

## 2. The order things start in

`kubectl apply` creates all seven resources almost at once. The pods then start in this order:

1. **Secret, PVC, ConfigMap and Services** are created immediately. They're just configuration.
2. **The Postgres pod** starts. minikube creates the volume for the PVC, and Postgres initializes the database on the first start.
3. **Postgres becomes ready** once `pg_isready` succeeds. Only then does the `db` Service send traffic to it.
4. **The app pod** starts with its init container, **`wait-for-db`**, which repeats `pg_isready -h db` every 2 seconds until the database answers. Without it, Spring Boot would start too early, fail to connect and restart several times (`CrashLoopBackOff`).
5. **Spring Boot starts.** It connects to `db:5432`, runs `schema.sql` and loads the data.
6. **The readiness probe** checks `/actuator/health` after 30 seconds. When it answers `UP`, the pod becomes ready and the NodePort Service sends it traffic.
7. **The liveness probe** starts after 60 seconds and runs every 20 seconds. If it fails 3 times in a row, Kubernetes restarts the container.

---

## 3. Commands

### Start minikube (if it isn't running)
```powershell
minikube start --driver=docker
```

### Load the app image into minikube
Build the image with Docker first, then copy it into minikube. minikube has its own image store, separate from Docker Desktop's.
```powershell
docker compose build app                                # or: docker build -t content-calendar:0.0.1-SNAPSHOT .
minikube image load content-calendar:0.0.1-SNAPSHOT
minikube image ls | Select-String content-calendar     # check that it's there
```
After you rebuild the image, load it again and restart the app:
```powershell
minikube image load content-calendar:0.0.1-SNAPSHOT
kubectl rollout restart deployment/content-calendar
```

### Deploy
```powershell
kubectl apply -f k8s/
```

### Check the pods
```powershell
kubectl get pods                 # STATUS should become Running, READY 1/1
kubectl get pods -w              # watch live (Ctrl+C to stop)
kubectl get all                  # pods, deployments, services
kubectl get pvc                  # STATUS should be Bound
kubectl describe pod -l app=content-calendar   # details and events if something is wrong
```
While the app waits for the database, its STATUS shows **`Init:0/1`**.

### See the logs
```powershell
kubectl logs deployment/content-calendar             # app logs
kubectl logs deployment/content-calendar -f          # follow live (Ctrl+C to stop)
kubectl logs deployment/content-calendar -c wait-for-db   # the init container
kubectl logs deployment/postgres                     # database logs
```

### Open the app

**Option 1: `minikube service`.** With the Docker driver on Windows, the node's IP isn't reachable directly, so minikube opens a tunnel and prints a URL:
```powershell
minikube service content-calendar --url
```
Keep that window open, because the tunnel closes when you stop it. Open the printed URL, for example `http://127.0.0.1:54321`, and add a path:
- `/` shows the welcome message.
- `/api/content` shows the content list.
- `/actuator/health` shows the health status.

**Option 2: `kubectl port-forward`:**
```powershell
kubectl port-forward service/content-calendar 8080:8080
```
Then open http://localhost:8080/api/content. Stop it with Ctrl+C. If port 8080 is already in use, for example by Docker Compose, use another local port: `kubectl port-forward service/content-calendar 9090:8080`.

### Scale to 2 replicas
```powershell
kubectl scale deployment/content-calendar --replicas=2
kubectl get pods -l app=content-calendar        # now 2 app pods
```
The Service shares requests between both pods. To go back to one, use `--replicas=1`.

Only the **app** should be scaled. Postgres must stay at 1 replica, because its volume can be used by only one pod.

### Delete everything
```powershell
kubectl delete -f k8s/
```
This also deletes the PVC, **including the database data**. To keep the data, delete everything except the PVC:
```powershell
kubectl delete -f k8s/01-postgres-secret.yaml -f k8s/03-postgres-deployment.yaml -f k8s/04-postgres-service.yaml -f k8s/05-app-configmap.yaml -f k8s/06-app-deployment.yaml -f k8s/07-app-service.yaml
```

### Stop minikube
```powershell
minikube stop
```

---

## 4. Good to know

- **The Secret isn't encrypted.** `stringData` is stored base64-encoded, which anyone with cluster access can decode. That's fine for local learning. A real cluster would use something like Sealed Secrets or a cloud secret manager.
- **`imagePullPolicy: Never`** means Kubernetes uses only the image already inside minikube. If you forget `minikube image load`, the pod shows `ErrImageNeverPull`.
- **The probes use `/actuator/health`, which includes the database check.** If Postgres goes down, the liveness probe fails and the app restarts until the database is back. Spring Boot also offers separate endpoints, `/actuator/health/liveness` and `/actuator/health/readiness`, if you later want the app to stay up while the database is down.
- **Sample data with 2 replicas:** `DataLoader` runs in **every** app pod when it starts, so scaling up can run it more than once.
- **Port conflict with Docker Compose:** NodePort 30080 is inside minikube, so it never conflicts with Docker Compose. Only `kubectl port-forward ... 8080:8080` can clash, if the Docker Compose app is already using port 8080 on your PC. In that case, use another local port, such as `9090:8080`.
