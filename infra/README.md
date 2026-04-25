# Infra

## Local development

```powershell
# bring up Postgres+PostGIS, Redis, MinIO
.\scripts\dev_docker_up.ps1

# add the backend container too:
docker compose -f infra/docker-compose.local.yml --profile full up -d

# add OpenSearch:
docker compose -f infra/docker-compose.local.yml --profile search up -d

# add Qdrant:
docker compose -f infra/docker-compose.local.yml --profile vector up -d
```

Default ports:
- Postgres `5432` (db `osint`, user `osint`, pass `osint`)
- Redis `6379`
- MinIO `9000` (S3 API), `9001` (console; `minio` / `minio123`)
- Backend `8011` (when `--profile full`) — Rescue911 dedicated local port; 8000 is reserved on this machine
- OpenSearch `9200` (when `--profile search`)
- Qdrant `6333` (when `--profile vector`)

## Staging

`docker-compose.staging.yml` is a skeleton. Provide `.env.staging` with real secrets,
TLS certs in `nginx/`, and run behind a real reverse proxy.

## Production

Out of scope for milestone 1. Suggested path: orchestrated deploy (k8s or compose
on a hardened VM) with secrets in a vault, daily DB backups, audit log shipped to
SIEM, and RBAC enforced at the API gateway.
