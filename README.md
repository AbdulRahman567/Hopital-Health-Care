# Healthcare-HMS

**Multi-tenant Healthcare Management SaaS platform** — each hospital is a tenant; doctors manage patients, appointments, clinical records, prescriptions, labs and billing under strict tenant isolation and role-based authorization.

> Design controls that support HIPAA/GDPR/SOC 2-oriented security and privacy requirements, but never claim regulatory compliance.

**Read `docs/PROJECT_CONTEXT.md` first** (agent/developer memory), then `docs/ROADMAP.md` (the phase checklist). The 13 authoritative design documents live in [`docs/`](docs/).

---

## Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| Docker Desktop (with Compose v2) | current | infrastructure stack |
| Git | current | everything |
| JDK | 21+ | backend (Phase 2+) |
| Node.js + npm | 24+ / 11+ | frontend (Phase 7+) |

## Quick start — infrastructure stack

Paste into a fresh shell (PowerShell or bash):

```bash
git clone https://github.com/AbdulRahman567/Hopital-Health-Care.git
cd Hospital-Health-Care
cd infra

# First run only — create your local env file (skip if infra/.env already exists):
cp .env.example .env

# Edit infra/.env and replace every changeme_* value before first start:
#   MYSQL_ROOT_PASSWORD, MYSQL_PASSWORD, REDIS_PASSWORD, MINIO_ROOT_PASSWORD

docker compose up -d --wait
docker compose ps
```

Expected: `mysql`, `redis`, `minio`, `nginx` all report `(healthy)`.

### What's running (loopback only — nothing is public)

| Service | Address | Notes |
|---|---|---|
| Nginx | http://localhost/healthz | returns `ok`; `/api/*` proxies once the backend exists |
| MySQL 8.4 | `127.0.0.1:3306` | database `hms_dev`, user `hms` |
| Redis 7.4 | `127.0.0.1:6379` | password auth |
| MinIO API / Console | http://127.0.0.1:9000 / http://127.0.0.1:9001 | user `hms_minio` |

Every published port is bound to `127.0.0.1` (verify: `docker compose config | Select-String host_ip`).

### Stop and reset

```bash
cd infra
docker compose down        # stop containers, keep data volumes
docker compose down -v     # STOP + DELETE all data (irreversible)
```

### Full profile (backend/frontend containers)

```bash
cd infra
docker compose --profile full up -d --wait
```

Only valid once Phase 2/7 images exist — the default profile runs infrastructure only (CONF-2).

## Code quality checks

```bash
cd backend
./mvnw spotless:check
```

```bash
cd frontend
npm install
npm run lint
```

## Repository layout

```
docs/       # 13 authoritative design documents (start with PROJECT_CONTEXT.md)
backend/    # Java 21 + Spring Boot 3 (Maven Wrapper, Spotless)
frontend/   # Next.js 15 + TypeScript strict (ESLint, Prettier)
infra/      # docker-compose, nginx config, .env.example
.github/    # CI/CD (Phase 26)
```

## Working conventions

- Branch per phase: `phase/NN-short-name`; conventional commits (`feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`) — see `docs/ENGINEERING_RULES.md` §10.
- Never commit secrets: `infra/.env` is gitignored; only `infra/.env.example` (placeholders) is tracked.
- One phase at a time; a phase starts only when the previous one meets its Definition of Done.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `port is already allocated` / `address already in use` | Another local process owns the port (e.g., a native Redis/MySQL service). Stop it, or change the **host** side of the port mapping in `infra/docker-compose.yml`. |
| MySQL healthcheck fails after changing `.env` passwords | Passwords apply only at **first** database initialization. Either restore the old values or reset with `docker compose down -v` and start again. |
| MinIO container restart-loops | Ensure `infra/.env` exists (`cp .env.example .env`) — compose refuses to start without `MINIO_ROOT_PASSWORD`. |
