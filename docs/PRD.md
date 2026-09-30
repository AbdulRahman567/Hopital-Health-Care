# Healthcare-HMS — Product Requirements Document (PRD)

| | |
|---|---|
| **Document status** | Draft v1.0 — for approval before Phase 1 |
| **Product** | Healthcare-HMS |
| **Type** | Multi-tenant Healthcare Management SaaS platform |
| **Companion docs** | TDD.md, ARCHITECTURE.md, ENGINEERING_RULES.md, ROADMAP.md, PROJECT_CONTEXT.md, DESIGN_SYSTEM.md |

> **Authority note:** This PRD defines *what* is built and *why*. The TDD defines *how*. AI coding agents must treat both as authoritative and must not implement anything outside the current phase.

---

## 1. Product Overview

Healthcare-HMS is a **multi-tenant Healthcare Management SaaS platform** with strict tenant isolation, granular authorization, clinical workflows, auditability, secure document management and production-grade observability.

It is explicitly **not** a "hospital CRUD application". Each hospital is a tenant that manages its own users, doctors, patients, departments and medical records, and can never see another hospital's data.

```text
Platform
 ├── Hospital A (Tenant A) ── Users · Doctors · Patients · Departments · Medical Records
 ├── Hospital B (Tenant B) ── Users · Doctors · Patients · Medical Records
 └── Hospital C (Tenant C)
```

## 2. Goals and Non-Goals

### 2.1 Goals
1. **Tenant isolation** — Hospital A can never read or modify Hospital B's data, enforced server-side at every layer.
2. **Clinical continuity** — a doctor can see a patient's complete history (conditions, visits, prescriptions, advice, medicine durations, and which doctor prescribed what).
3. **Least-privilege access** — module, action, resource and field-level authorization.
4. **Trustworthy clinical records** — finalized records are versioned and amended, never silently overwritten.
5. **Auditability** — who accessed or changed what, and when.
6. **Operational readiness** — observable, testable, deployable, stateless backend that can evolve toward high availability.
7. **Usable at clinical speed** — fast, clear, accessible workflows that prevent errors.

### 2.2 Non-Goals (for the initial release)
- Claiming regulatory compliance (see §9.6).
- Message brokers (Kafka/RabbitMQ/SQS) — deferred until workload requires them.
- True multi-instance high availability — architecture must *allow* it; first deployment is single-instance.
- Advanced AI features (Phase 30).
- Native mobile applications.
- Insurance claims/EDI integrations and HL7/FHIR interoperability (future consideration).

## 3. Users and Roles

| Role | Primary responsibilities | Key restrictions |
|---|---|---|
| **Platform Admin** | Onboard/activate/suspend tenants, platform monitoring | No routine access to clinical data |
| **Hospital Admin** | Manage hospital settings, departments, staff, roles | Clinical data access only as granted |
| **Doctor** | Consult, diagnose, prescribe, order, view history | Access to patients per resource rules (§6.3) |
| **Nurse** | Record vitals, assist visits, queue management | No prescribing unless granted |
| **Receptionist** | Register patients, book appointments, manage queue | **No** diagnosis, prescriptions or clinical notes |
| **Lab / Imaging Staff** | Enter and upload results | Only their order-related data |
| **Billing Staff** | Invoices and payments | No clinical detail beyond billable items |
| **Patient (future)** | View own records/appointments | Own data only |

Roles are configurable bundles of **permissions**; permissions are the unit of enforcement.

## 4. Scope by Module

| # | Module | Summary |
|---|---|---|
| M1 | Tenant / Hospital Onboarding | Register hospital, verify email, activate, configure settings |
| M2 | Authentication & Session | Login, MFA-ready, refresh rotation, reset, lockout |
| M3 | Authorization | RBAC + permissions + resource + field-level access |
| M4 | Organization & Staff | Departments, staff, doctor invitations |
| M5 | Patients | Registration, demographics, search, allergies, history |
| M6 | Appointments & Queue | Booking, double-booking prevention, queue |
| M7 | Clinical Consultation | Visits, vitals, notes, diagnoses, orders, recommendations |
| M8 | Medical History | Conditions, family/surgical history, unified timeline |
| M9 | Prescriptions & Medicines | Versioned prescriptions, medicine duration, prescriber attribution |
| M10 | Lab & Imaging | Orders, results, imaging references |
| M11 | Documents & Files | Secure upload/download, MinIO (local) / S3 (prod) |
| M12 | Billing | Invoices, payments, line items |
| M13 | Notifications | Email and in-app notifications |
| M14 | Audit | Immutable audit trail and audit viewer |
| M15 | Search | Tenant-scoped search across core entities |
| M16 | Dashboards & Analytics | Operational and clinical summaries |

## 5. Functional Requirements

Priority: **P0** = must-have for MVP, **P1** = important, **P2** = later.

### 5.1 M1 — Tenant / Hospital Onboarding
| ID | Requirement | Pri |
|---|---|---|
| FR-1.1 | A hospital can self-register with admin name, email, hospital name and password. | P0 |
| FR-1.2 | Registration triggers an email verification link; unverified tenants cannot log in. | P0 |
| FR-1.3 | Resending verification must not time out or redirect the user away from the flow, and must be rate limited. | P0 |
| FR-1.4 | Tenant activation is a defined workflow (automatic after verification, or Platform Admin approval — see Open Question OQ-1); it must never require manual database edits. | P0 |
| FR-1.5 | Hospital settings (name, contact, timezone, working hours) are editable by Hospital Admin. | P1 |
| FR-1.6 | Platform Admin can suspend/reactivate a tenant; suspension blocks all tenant logins. | P1 |

### 5.2 M2 — Authentication & Session
| ID | Requirement | Pri |
|---|---|---|
| FR-2.1 | Email + password login with secure password hashing. | P0 |
| FR-2.2 | Short-lived access tokens; refresh tokens with rotation and reuse detection. | P0 |
| FR-2.3 | Brute-force protection: rate limiting and temporary account lockout. | P0 |
| FR-2.4 | Password reset via time-limited, single-use token; responses must not reveal whether an account exists. | P0 |
| FR-2.5 | Email verification for invited and self-registered users. | P0 |
| FR-2.6 | Logout invalidates the refresh token server-side. | P0 |
| FR-2.7 | Architecture is MFA-ready (TOTP/OTP hooks); MFA enforcement itself is P2. | P2 |

### 5.3 M3 — Authorization
See §6 for the full model.
| ID | Requirement | Pri |
|---|---|---|
| FR-3.1 | Every endpoint declares required permission(s); default is deny. | P0 |
| FR-3.2 | Tenant ID is derived from the authenticated principal, never trusted from the client. | P0 |
| FR-3.3 | Resource-level rules restrict access to records (e.g., assigned patients). | P0 |
| FR-3.4 | Field-level rules hide sensitive fields from unauthorized roles. | P0 |
| FR-3.5 | Hospital Admin can create custom roles from the permission catalog. | P1 |

### 5.4 M4 — Organization & Staff
| ID | Requirement | Pri |
|---|---|---|
| FR-4.1 | Create, edit and deactivate departments; creation errors must return precise, field-level messages. | P0 |
| FR-4.2 | Invite doctors/nurses/staff by email with a role and department; invitee completes registration via a secure, expiring link. | P0 |
| FR-4.3 | Staff profiles (specialty, license number, department, availability). | P0 |
| FR-4.4 | Deactivating staff revokes sessions but preserves historical attribution. | P0 |

### 5.5 M5 — Patients
| ID | Requirement | Pri |
|---|---|---|
| FR-5.1 | Register a patient with demographics and contact details; required fields validated with field-specific errors. | P0 |
| FR-5.2 | Generate a human-readable, tenant-unique Medical Record Number (MRN). | P0 |
| FR-5.3 | Duplicate detection warning (e.g., same name + DOB + phone). | P1 |
| FR-5.4 | Record allergies, with severity, visible prominently in clinical views. | P0 |
| FR-5.5 | Paginated, filterable patient search scoped to the tenant. | P0 |
| FR-5.6 | Patients are deactivated/archived, never hard-deleted while clinical history exists. | P0 |

### 5.6 M6 — Appointments & Queue
| ID | Requirement | Pri |
|---|---|---|
| FR-6.1 | Book, reschedule and cancel appointments for a patient with a doctor. | P0 |
| FR-6.2 | The system prevents double-booking of a doctor's time slot, including under concurrent requests. | P0 |
| FR-6.3 | Appointment views display patient and doctor **names**, never raw IDs. | P0 |
| FR-6.4 | Day queue with status (scheduled, checked-in, in-consultation, completed, no-show, cancelled). | P0 |
| FR-6.5 | Appointment reminders via notifications. | P2 |

### 5.7 M7 — Clinical Consultation
| ID | Requirement | Pri |
|---|---|---|
| FR-7.1 | Start a visit from an appointment or as a walk-in. | P0 |
| FR-7.2 | Record vitals, clinical notes, diagnoses, orders and recommendations/advice per visit. | P0 |
| FR-7.3 | A visit can be **finalized**; after finalization edits happen only via amendments/addenda. | P0 |
| FR-7.4 | Every clinical entry records author, timestamp and, for changes, a reason. | P0 |
| FR-7.5 | Consultation forms display doctor/patient names, never raw IDs. | P0 |

### 5.8 M8 — Medical History
| ID | Requirement | Pri |
|---|---|---|
| FR-8.1 | Maintain conditions/problems, family history and surgical history. | P0 |
| FR-8.2 | Unified, paginated **patient timeline** across visits, prescriptions, labs, imaging, documents. | P0 |
| FR-8.3 | Timeline entries show the responsible clinician and their department. | P0 |

### 5.9 M9 — Prescriptions & Medicines
| ID | Requirement | Pri |
|---|---|---|
| FR-9.1 | Create prescriptions with medicine, dose, frequency, **duration**, instructions. | P0 |
| FR-9.2 | Prescriptions are versioned (v1 → v2); previous versions remain retrievable. | P0 |
| FR-9.3 | Each prescription records prescribing doctor, so any authorized clinician in the same hospital can see **which doctor prescribed what**. | P0 |
| FR-9.4 | Allergy check warning when prescribing a medicine conflicting with a recorded allergy. | P1 |
| FR-9.5 | Medicine catalog per tenant (searchable). | P1 |
| FR-9.6 | Printable/PDF prescription. | P1 |

### 5.10 M10 — Lab & Imaging
| ID | Requirement | Pri |
|---|---|---|
| FR-10.1 | Create lab/imaging orders during a visit. | P1 |
| FR-10.2 | Lab staff enter results or upload reports; results link to the order and visit. | P1 |
| FR-10.3 | Results appear on the patient timeline; abnormal flags supported. | P1 |

### 5.11 M11 — Documents & Files
| ID | Requirement | Pri |
|---|---|---|
| FR-11.1 | Upload documents (PDF, images) linked to patient/visit/order. | P1 |
| FR-11.2 | Enforce file type allow-list, size limit and content validation; reject unsupported/oversized files with clear errors. | P1 |
| FR-11.3 | Downloads use short-lived, authorization-checked access — no permanent public URLs. | P1 |
| FR-11.4 | Storage is MinIO locally and AWS S3 in production behind one abstraction. | P1 |

### 5.12 M12 — Billing
| ID | Requirement | Pri |
|---|---|---|
| FR-12.1 | Generate an invoice from a visit/services with line items and totals. | P1 |
| FR-12.2 | Record payments (full/partial) and invoice status. | P1 |
| FR-12.3 | Billing staff see billable data only, not clinical detail. | P1 |

### 5.13 M13–M16 — Notifications, Audit, Search, Dashboards
| ID | Requirement | Pri |
|---|---|---|
| FR-13.1 | Transactional emails (verification, invite, reset) sent asynchronously with retry. | P0 |
| FR-13.2 | In-app notifications with read/unread state. | P2 |
| FR-14.1 | Audit log records authentication events, permission changes, clinical record create/update/finalize/amend, exports, downloads and sensitive reads. | P0 |
| FR-14.2 | Audit records are append-only and tenant-scoped; viewable by authorized admins. | P1 |
| FR-15.1 | Search across patients, doctors, appointments, tenant-scoped, paginated, permission-aware. | P1 |
| FR-16.1 | Role-appropriate dashboards (queue size, appointments, revenue, workload). | P2 |

## 6. Authorization Requirements

The system implements **RBAC + permission-based + resource-level + data-level** access.

### 6.1 Level 1 — Module
`PATIENTS`, `APPOINTMENTS`, `PRESCRIPTIONS`, `BILLING`, `LAB`, `DOCUMENTS`, and others.

### 6.2 Level 2 — Action
`VIEW`, `CREATE`, `UPDATE`, `DELETE`, `EXPORT`, `DOWNLOAD`, `APPROVE` → composite permissions such as `PATIENT_VIEW`, `PATIENT_CREATE`, `PATIENT_UPDATE`, `PATIENT_EXPORT`.

### 6.3 Level 3 — Resource
Being a doctor does not grant access to every patient. Access is granted via a defined relationship (assigned doctor, active appointment/visit, care-team membership, or an explicit hospital-configured policy). The exact policy is Open Question **OQ-2**; the default is *assigned or previously treated*, with all cross-doctor reads audited.

### 6.4 Level 4 — Field / Data Sensitivity
| Field | Receptionist | Nurse | Doctor | Billing |
|---|---|---|---|---|
| Name, phone, address | ✓ | ✓ | ✓ | ✓ |
| Vitals | ✗ | ✓ | ✓ | ✗ |
| Diagnosis | ✗ | Configurable | ✓ | ✗ |
| Prescription | ✗ | View only | ✓ | ✗ |
| Clinical notes | ✗ | Configurable | ✓ | ✗ |

## 7. Clinical Data Requirements

1. **Separate concepts, not one giant `patients` table:** Patient → Allergies, Conditions, Family History, Surgical History, Visits (Vitals, Notes, Diagnoses, Prescriptions, Orders, Recommendations), Lab Results, Imaging, Documents, Appointments, Timeline.
2. **Immutability of finalized records:** corrections create a new version or an addendum with `createdBy`, `createdAt`, `updatedBy`, `updatedAt`, `reason`. No silent overwrite.
3. **No hard delete** of clinical history unless a documented retention policy explicitly permits it.
4. **Attribution:** every clinical record identifies its author and hospital.

## 8. Critical User Flows

```text
Register hospital → Verify email → Admin login → Create department → Invite doctor
→ Doctor accepts & logs in → Register patient → Book appointment → Consult patient
→ Create prescription → Generate invoice
```
Additional flows: document upload, patient history review, cross-doctor prescription review, password reset, logout, tenant suspension.

## 9. Non-Functional Requirements

### 9.1 Security & Privacy
Authentication with refresh rotation; MFA-ready; RBAC and resource/field authorization; tenant isolation; secure cookies; CSRF protection where applicable; rate limiting, brute-force protection, lockout; secure headers, CSP, CORS allow-list; input validation and output encoding; XSS, SQLi and SSRF protection; secure file uploads; secrets management; TLS in transit, encryption at rest; audit logging.

**Hard rules:** never trust client-supplied tenant IDs; frontend checks are never the only boundary; no secrets in source or committed `.env`; no publicly exposed Swagger, actuator or Prometheus in production; no placeholder JWT secrets.

### 9.2 Performance
- List endpoints always paginated; no unbounded queries (e.g., patient timeline).
- Target p95 API latency ≤ 500 ms for standard reads under expected load (to be validated in Phase 22).
- Indexes on tenant-scoped access paths; no N+1 queries.
- Redis used selectively (rate limits, OTP, short-lived tokens, selective caching, notification state) — not a blanket cache.

### 9.3 Availability & Scalability
Stateless backend so that a load-balanced, multi-instance deployment with primary/replica database and HA Redis is a later evolution, not a rewrite. A single EC2 instance is **not** described as high availability.

### 9.4 Observability
Structured JSON logs; Prometheus metrics (latency, error rate, throughput, DB/Redis latency, JVM memory/CPU/GC, thread and connection pools); OpenTelemetry tracing later. Monitoring endpoints are access-restricted.

### 9.5 Usability & Accessibility
- IDs are never shown where names are expected.
- Required fields are visibly marked; validation errors identify the exact field.
- Destructive actions require confirmation; buttons disable during submission to prevent duplicates.
- Long histories are paginated; tables work on small screens.
- WCAG-oriented keyboard navigation and contrast; loading, empty and error states everywhere.

### 9.6 Compliance Positioning
The system is designed with **controls that support HIPAA / GDPR / SOC 2-oriented security and privacy requirements**. It is **compliance-ready, not certified or "compliant"**. No document, UI copy or marketing text may claim regulatory compliance. Actual compliance depends on infrastructure, policies, contracts, procedures, retention and incident response outside the codebase.

### 9.7 Reliability & Data Protection
Database migrations via Flyway; managed database backups; documented restore procedure; health checks and graceful shutdown.

## 10. Delivery Roadmap (Summary)

Detailed phase scope lives in ROADMAP.md.

| Phase | Name | Phase | Name |
|---|---|---|---|
| 0 | Documentation & Architecture | 16 | Documents & Files |
| 1 | Repository & Infrastructure | 17 | Billing |
| 2 | Backend Foundation | 18 | Notifications |
| 3 | Database Foundation | 19 | Audit & Compliance Controls |
| 4 | Multi-Tenancy | 20 | Search |
| 5 | Authentication | 21 | Dashboards & Analytics |
| 6 | Authorization / RBAC | 22 | Performance Optimization |
| 7 | Frontend Foundation | 23 | Security Hardening |
| 8 | Hospital & Organization Mgmt | 24 | Automated Testing |
| 9 | Staff & Doctor Management | 25 | Observability |
| 10 | Patient Management | 26 | CI/CD |
| 11 | Appointments & Queue | 27 | Production Deployment |
| 12 | Clinical Consultation | 28 | Disaster Recovery |
| 13 | Medical History | 29 | Production Readiness |
| 14 | Prescriptions & Medicines | 30 | Future AI / Advanced Features |
| 15 | Lab & Imaging | | |

**Phase 0 deliverables:** PRD, Architecture, Engineering Rules, Roadmap, Project Context, Design System, Database Strategy, API Standards, Security Specification, Testing Strategy, AI Development Guide — all approved before Phase 1 begins.

## 11. Definition of Done (Every Phase)

- [ ] Code implemented, no unrelated modifications
- [ ] Backend compiles; frontend builds
- [ ] Unit and (where applicable) integration tests pass
- [ ] Database migration succeeds
- [ ] Authorization tested; tenant isolation tested where applicable
- [ ] Error states tested; UI manually tested; no console errors
- [ ] No unnecessary new compiler warnings
- [ ] Security review completed where applicable
- [ ] Documentation updated; Git commit created

Then **stop** — the next phase does not start automatically.

## 12. Success Metrics

| Metric | Target |
|---|---|
| Tenant-isolation test suite | 100% pass; zero known cross-tenant leaks |
| Critical E2E flow (§8) | Passing in CI |
| Endpoints with declared permission + tests | 100% |
| Unauthenticated/unauthorized access tests | 100% denied |
| Pages with raw IDs shown instead of names | 0 |
| Production readiness review | GO |

## 13. Assumptions, Open Questions and Risks

### Open Questions
**Status: OQ-1…OQ-6 accepted at their defaults at the Phase 0 sign-off (2026-09-30). Change only via a recorded override.**
| ID | Question | Decision (= default) |
|---|---|---|
| OQ-1 | Are new tenants activated automatically after email verification, or approved by a Platform Admin? | Automatic after verification, with Platform Admin suspend ability |
| OQ-2 | Exact doctor-to-patient access policy (assigned only vs. any doctor in the hospital with audit)? | Assigned/previously treated, cross-doctor reads audited |
| OQ-3 | Can one tenant own multiple hospitals/branches? | Single hospital per tenant for MVP; model must not preclude branches |
| OQ-4 | Data retention periods and deletion policy? | No hard delete of clinical history |
| OQ-5 | Which email provider (SES, SMTP, other)? | SMTP abstraction; provider chosen in Phase 18 |
| OQ-6 | Patient-facing portal timing? | Post-MVP |

### Risks
| Risk | Mitigation |
|---|---|
| AI agent produces inconsistent or duplicated code | Phase-by-phase prompts, authoritative docs, verification before "done" |
| Tenant escape / IDOR | Tenant context from auth, DB-level tenant column on every entity, dedicated isolation tests |
| Over-building infrastructure (Kafka etc.) | Start with in-process background jobs; add a broker on evidence |
| Overstating compliance | Compliance-ready language only |
| Building on inaccurate assumptions of prior code | Audit-before-build if any legacy code exists |
