# Healthcare-HMS — Security Specification

> **Compliance positioning:** the system is designed with controls that *support* HIPAA / GDPR / SOC 2-oriented security and privacy requirements. It is **compliance-ready**, not certified or "compliant". Actual compliance also depends on infrastructure, policies, contracts, procedures, retention rules and incident response outside the codebase. Never claim compliance in code, UI or docs.

## 1. Security Objectives
1. Confidentiality of patient data and strict tenant isolation.
2. Integrity of clinical records (versioned, attributable).
3. Availability sufficient for clinical use.
4. Accountability through audit logging.

## 2. Threat Model (summary)
| Threat actor | Goal | Primary controls |
|---|---|---|
| Malicious tenant user | Read another hospital's data | Tenant isolation chain, 404 on foreign IDs, isolation tests |
| Over-privileged internal role | See clinical data outside duty | Permissions, resource and field policies, audit |
| External attacker | Account takeover, data theft | Rate limits, lockout, refresh rotation, TLS, CSP |
| Compromised token | Session abuse | Short-lived access tokens, rotation with reuse detection |
| Malicious upload | Malware, path traversal, XSS | Validation, private storage, download authorization |
| Insider with DB/infra access | Bulk exfiltration | Least-privilege DB users, encryption, audit, secrets management |

## 3. Authentication
- Passwords hashed with Argon2id (or bcrypt with tuned cost); minimum length and breached-password check.
- Access token: JWT, 10–15 min; refresh token: opaque, hashed at rest, rotated on every use; reuse revokes the token family.
- Refresh token in `HttpOnly; Secure; SameSite` cookie; access token in memory only.
- Lockout after repeated failures with progressive delay; per-IP and per-account rate limits.
- Uniform responses for login, reset and verification (no account enumeration).
- Email verification and password reset via single-use, hashed, expiring tokens.
- MFA-ready data model and flow; enforcement is a later phase.
- Startup fails if JWT secret is missing, too short, or a known placeholder.

## 4. Authorization
- Default deny; every endpoint declares a permission.
- Tenant ID always derived from the authenticated principal.
- Resource-level policies (assigned/treated patients) enforced in services and list queries.
- Field-level masking driven by permissions.
- Cross-tenant and cross-doctor sensitive reads are audited.
- Frontend checks are UX only.

## 5. Tenant Isolation Controls
| Layer | Control |
|---|---|
| API | No tenant identifiers accepted as authority |
| Service | Tenant from `TenantContext` only |
| Repository | Hibernate tenant filter; native query review |
| Database | `tenant_id NOT NULL`; composite keys/FKs |
| Cache | `t:{tenantId}:` key prefix |
| Storage | `tenants/{tenantId}/` object prefix |
| Jobs | Tenant in payload; context restored |
| Logs/Audit | `tenantId` on every entry |

## 6. Input, Output and Web Security
| Area | Requirement |
|---|---|
| Validation | Jakarta Validation on all DTOs; reject unknown fields |
| SQL injection | Parameterized JPA queries; no string-built SQL |
| XSS | Output encoding; no raw HTML rendering of user data; sanitize rich text; strict CSP |
| CSRF | SameSite cookies plus CSRF token/header on cookie-authenticated endpoints |
| CORS | Explicit per-environment origin allow-list; no wildcard with credentials |
| SSRF | No user-controlled outbound URLs; allow-listed integrations only |
| Headers | HSTS, `X-Content-Type-Options: nosniff`, `frame-ancestors 'none'`, `Referrer-Policy`, `Permissions-Policy` |
| Mass assignment | Dedicated request DTOs; server-controlled fields never bound |
| Error handling | No stack traces, SQL or class names to clients; `traceId` only |

## 7. File Upload Security
- Extension **and** MIME allow-list; magic-byte verification; max size per type.
- Generated storage keys; original filename kept as metadata only.
- Private buckets; server-side encryption; short-lived pre-signed downloads after authorization.
- Optional antivirus scanning hook.
- Every upload/download audited.

## 8. Secrets & Configuration
- Secrets only from environment variables or a secret manager; never in source.
- `.env` files gitignored; provide `.env.example` with non-secret placeholders that **cannot** boot the app in production.
- Separate secrets per environment; rotation procedure documented.
- Secret scanning in CI.

## 9. Data Protection
- TLS 1.2+ everywhere (browser→edge, edge→backend, backend→DB/Redis/S3).
- Encryption at rest: DB volumes, S3 SSE, Redis persistence if enabled.
- Column-level encryption for MFA secrets and other high-sensitivity values.
- PHI never in logs, URLs, metrics labels or error messages.
- Data minimization in API responses and exports.

## 10. Audit Logging
Log: authentication events, token reuse, password changes, role/permission changes, patient and clinical record creation/change/finalize/amend, prescription versions, document access, exports, cross-doctor reads, tenant suspension.
Requirements: append-only, tenant-scoped, includes actor, action, entity reference, timestamp, IP, user agent, `traceId`; no secrets or full clinical payloads.

## 11. Management Surface
- Swagger/OpenAPI: disabled or authenticated in production.
- Actuator: only `health` liveness/readiness exposed publicly if needed; everything else internal-only.
- Prometheus: reachable only from the internal network/monitoring host.
- Admin/debug endpoints never enabled in production.

## 12. Rate Limiting (Redis-backed)
| Scope | Example limit |
|---|---|
| Login | 5/min per account, 20/min per IP |
| Registration / resend verification | 3/hour per email, 10/hour per IP |
| Password reset | 3/hour per account |
| Search/export | Stricter per-user limits |
Auth endpoints **fail closed** if the limiter is unavailable.

## 13. Dependency & Supply Chain
- Automated dependency scanning (backend and frontend) in CI; critical/high fixed before release.
- Pin versions; review new dependencies; keep framework versions patched (e.g., Next.js security advisories).
- Container images from minimal, maintained bases; scanned in CI.

## 14. Security Testing
- Unit/integration: authentication abuse cases, wrong role, wrong tenant, expired/reused tokens.
- Automated tenant-isolation suite (see TESTING.md).
- ArchUnit rule: every endpoint carries a permission.
- Periodic AI-assisted security review after each security-sensitive phase (AI_DEVELOPMENT_GUIDE.md).
- Before production: full hardening pass (Phase 23) and independent penetration test recommended.

## 15. Incident Response (baseline)
- `traceId` correlation across logs.
- Ability to revoke all sessions for a user/tenant.
- Tenant suspension switch.
- Documented runbook: detect → contain → eradicate → recover → review. Legal/regulatory notification duties are handled by the operating organization.

## 16. Security Review Checklist (per phase)
- [ ] Endpoints authenticated and permissioned
- [ ] Tenant isolation verified with tests
- [ ] Inputs validated; outputs encoded
- [ ] No sensitive data in logs/errors
- [ ] Rate limits on abuse-prone endpoints
- [ ] Audit events emitted
- [ ] No new public management endpoints
- [ ] Dependencies scanned
