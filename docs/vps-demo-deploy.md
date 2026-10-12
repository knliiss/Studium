# Studium demo deployment to VPS

Deploy separately from Mnemos using Docker Compose project `studium-lms`, its own isolated infrastructure, and a separate Cloudflare Tunnel. Public hostname: **https://lms.knalis.rest**.

## Cloudflare Zero Trust

Create a new remotely managed Cloudflare Tunnel (for example, `studium-lms`). Copy the connector **token** only. Add a published hostname on that tunnel:

- Subdomain `lms`, zone `knalis.rest`
- Service type HTTP
- URL `http://service-lb:3000`

This is the Docker-internal HAProxy frontend port, not localhost on the Cloudflare side. Cloudflare automatically creates the DNS route. Do not change the existing Mnemos tunnel.

## SSH into the VPS (Ubuntu 24.04 example)

```bash
sudo apt update && sudo apt install -y git python3 curl openssl openjdk-21-jdk-headless
sudo mkdir -p /opt/studium && sudo chown "$USER":"$USER" /opt/studium
git clone --branch deploy/lms-vps https://github.com/knliiss/Studium.git /opt/studium
cd /opt/studium

mkdir -p .secrets && chmod 700 .secrets
nano .secrets/cloudflare-tunnel.token
chmod 600 .secrets/cloudflare-tunnel.token

bash infra/scripts/vps/deploy-vps.sh
```

Requires Docker Engine and Docker Compose **2.24.4+** (`docker compose version`). The SSH user must have permission to run Docker (test `docker ps` before starting). The first build checks for about **10 GiB available RAM and 15 GiB available disk** because Mnemos is already running on this VPS. If these resources are unavailable, use a separate VPS instead of risking Mnemos. For a dedicated server with an intentionally smaller resource budget, `STUDIUM_SKIP_RESOURCE_PREFLIGHT=true` disables this protective check. JVM heaps and Docker container limits are configured in the VPS Compose override. The script generates strong values in `.env` on first run, ensures RSA keys with the existing start script, builds the backend JARs, starts the stack and seeds initial test data once. It uses loopback-bound ports 18080 (gateway) and 13000 (frontend) for diagnostics; database, Kafka, Redis and MinIO are never published to the host.

If Studium is already cloned, update the deployment branch and rerun:

```bash
cd /opt/studium && git pull --ff-only
bash infra/scripts/vps/deploy-vps.sh
```

Explicit reseeding (may modify demo scenarios):

```bash
STUDIUM_RESEED=true bash infra/scripts/vps/deploy-vps.sh
```

## Verify

```bash
cd /opt/studium
docker compose --env-file .env -f infra/docker/docker-compose.local.yml -f infra/docker/docker-compose.vps.yml ps
docker compose --env-file .env -f infra/docker/docker-compose.local.yml -f infra/docker/docker-compose.vps.yml logs --tail=100 cloudflared gateway frontend
curl -fsS http://127.0.0.1:18080/actuator/health
curl -I http://127.0.0.1:13000/
```

Demo credentials: `admin.demo`, `teacher.alpha`, `student.one`, all with password `DemoPass123!`. The owner password is *random* and stored privately in `.env`; do not post the file. The public frontend and API use the same HTTPS origin, with internal Nginx reverse proxy routes `/api/` and `/ws/`.

Protect test accounts with Cloudflare Access if the deployment is not meant for the general public. Avoid `--fresh`: it deletes Studium volumes. Do not run Mnemos cleanup/admin scripts for Studium.
