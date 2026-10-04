# Healthcare-HMS — Deployment

## 1. Environments
| Env | Purpose | Topology |
|---|---|---|
| Local | Development | Docker Compose — default profile: MySQL, Redis, MinIO, Nginx; `--profile full`: + backend, frontend (apps may also run natively) |
| Staging | Pre-production validation, E2E | Production-like, synthetic data only |
| Production | Live | See §3 |

Rules: separate secrets, databases and buckets per environment; no shared credentials; no production data in lower environments.

## 2. Local Setup (Docker Compose)
Default profile services: `mysql`, `redis`, `minio`, `nginx`. With `--profile full`: + `backend`, `frontend`.
- Health checks on every container; `depends_on` with `condition: service_healthy`.
- Volumes for MySQL and MinIO data.
- `.env.example` committed; real `.env` gitignored.
- MinIO console bound to localhost only.

## 3. Production v1
```text
Users → Vercel (Next.js) ──HTTPS──▶ Nginx (EC2) ▶ Spring Boot (container)
                                                   ├─▶ Managed MySQL (private subnet)
                                                   ├─▶ Redis (private)
                                                   └─▶ S3 (private bucket, SSE)
```
- **Honest note:** a single EC2 instance is **not** high availability. It is an acceptable first deployment.
- Backend runs as a container; Nginx terminates TLS (or an ALB does); HTTP → HTTPS redirect; HSTS.
- Database and Redis in private networking; security groups allow only the backend.
- Prometheus/actuator not routed publicly.

## 4. Path to High Availability
```text
Internet → Load Balancer → Backend A / B / C (stateless)
                        → Managed DB (Primary + Replica, Multi-AZ)
                        → Redis (replication/managed HA)
                        → S3
```
Prerequisites already built in: stateless app, DB-backed outbox jobs, Redis for shared rate limits, object storage for files.
Later: autoscaling group, blue/green or rolling deploys, read replica for reporting, WAF.

## 5. Configuration & Secrets
- Twelve-factor: configuration via environment variables.
- Secrets in a secret manager (AWS Secrets Manager / SSM) or CI/host secret store; injected at runtime.
- App **fails on startup** if secrets are placeholders or too weak.
- Production profile: dev CORS origins off, Swagger off/protected, debug logging off, secure cookies on.

## 6. Configuration Checklist (production)
- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] Strong JWT secret/key; unique per environment
- [ ] DB credentials least-privilege (`app_rw`)
- [ ] CORS allow-list = production frontend origin only
- [ ] `HMS_TRUSTED_PROXY` = the reverse proxy's address as a whole-string regex (`Matcher.matches`), so `X-Forwarded-For` is honoured from that hop only — per-IP rate limits otherwise key on the proxy and one bucket covers every visitor (SEC-1, P5.11)
- [ ] Cookies `Secure`, `HttpOnly`, correct `SameSite`/domain — and the SPA and the API are on **one registrable domain**, because `SameSite=Lax` is not sent cross-site and would silently kill the refresh leg (D6, ROADMAP P27.1)
- [ ] S3 bucket private, SSE enabled, IAM role scoped to `tenants/*` prefix
- [ ] Email provider configured; SPF/DKIM/DMARC set
- [ ] Actuator/Prometheus internal only; Swagger disabled/protected
- [ ] Log level INFO; PHI redaction verified

## 7. CI/CD (Phase 26)
```text
Push → Build → Unit/Integration/Isolation tests → Migration check → Scans
     → Build images → Deploy staging → E2E smoke → Manual approval → Deploy production → Smoke test
```
- Flyway migrations run as a controlled step before the new version receives traffic.
- Images tagged by commit SHA; rollback = redeploy previous tag.
- Database rollback strategy: forward-fix migrations; restore from backup for disasters.

## 8. Health, Readiness, Shutdown
- Liveness and readiness endpoints; readiness checks DB, Redis, storage.
- Container `HEALTHCHECK`.
- Graceful shutdown: stop accepting requests, finish in-flight, flush outbox worker.

## 9. Monitoring & Alerting
- Metrics: request latency/error rate/throughput, DB & Redis latency, JVM memory/GC/threads, connection pools.
- Alerts: high 5xx rate, p95 latency breach, DB connection saturation, failed jobs backlog, disk usage, certificate expiry, backup failure.
- Logs shipped centrally as structured JSON; retention set by policy.

## 10. Backup & Disaster Recovery (Phase 28)
| Item | Requirement |
|---|---|
| Database | Automated daily backups + point-in-time recovery; cross-region copy recommended |
| Object storage | Versioning enabled; lifecycle rules |
| Configuration | Infrastructure and config in version control |
| RPO / RTO | To be defined with stakeholders (proposed starting point: RPO ≤ 15 min, RTO ≤ 4 h) |
| Restore drill | Performed and documented before go-live and periodically |
| Runbooks | Restore DB, rotate secrets, revoke sessions, suspend tenant, rollback release |

## 11. Release Checklist
- [ ] CI green, including isolation suite
- [ ] Migrations reviewed and tested on a copy of staging
- [ ] Security review complete for security-sensitive changes
- [ ] Configuration checklist (§6) verified
- [ ] Backups verified recent
- [ ] Rollback plan noted
- [ ] Smoke tests defined and run after deploy

## 12. Production Readiness Gate
Phase 29 review returns **GO** or **NO-GO**. Any of the following is an automatic NO-GO: placeholder secrets, public Swagger/actuator/Prometheus, dev CORS origins, missing backups, untested restore, failing tenant-isolation tests, hardcoded credentials.
