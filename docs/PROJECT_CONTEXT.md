# Healthcare-HMS — Project Context

> The first thing an AI agent or new developer reads. Keep it short, accurate and current. Update it at the end of every phase. **Current file: handoff snapshot 2026-09-30.**

## 1. What This Is
A **multi-tenant Healthcare Management SaaS platform**. Each hospital is a tenant. Doctors add patients and track full history: conditions, medicine history and durations, advice/recommendations, and which doctor at the same hospital prescribed what.

## 2. Stack
- **Frontend:** Next.js 15, React 19, TypeScript, Tailwind CSS, shadcn/ui, Redux Toolkit, TanStack Query, React Hook Form, Zod, Axios, Lucide React, Framer Motion
- **Backend:** Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Hibernate, Flyway, Redis, MySQL, MapStruct, Lombok, Jakarta Validation, OpenAPI
- **Infra:** Docker, Docker Compose, Nginx, MinIO (local), AWS S3 (prod)
- **Deploy target:** Vercel (frontend), AWS EC2 (backend), managed MySQL, S3

## 3. Document Map (authoritative — 13 files, all in `docs/`)
| Doc | Purpose |
|---|---|
| PRD.md | What and why |
| TDD.md | Detailed technical design |
| ARCHITECTURE.md | Structure and module boundaries |
| ENGINEERING_RULES.md | Mandatory rules |
| ROADMAP.md | Phases, 227-task checklist, exit criteria |
| DESIGN_SYSTEM.md | UI standards |
| DATABASE.md / API.md / SECURITY.md / TESTING.md / DEPLOYMENT.md | Focused standards |
| AI_DEVELOPMENT_GUIDE.md | Prompts and AI workflow |

## 4. Current State
| Item | Value |
|---|---|
| Current phase | Phase 1 — Repository & Infrastructure (◐ in progress) |
| Current task | P1.1 monorepo layout — `[x]` (2026-09-30). Next: **P1.2** `git init`, awaiting "continue" |
| Last completed task | P1.1 Create monorepo layout — `[x]`, verified against TDD §5 |
| Last completed phase | Phase 0 — Documentation & Architecture (2026-09-30) |
| Repository state | Planning only: 13 markdown files in `docs/`. **No code, no `backend/`, `frontend/`, `infra/`, no Docker, no CI** |
| Branch | None — not a git repository (`git status` → "fatal: not a git repository") |
| Last verified build | None — no build exists. Doc verification checks pass (see §6) |

## 5. Next action
Phase 1 in progress. Next task **P1.2**: `git init`, branch convention `phase/NN-short-name` (start with `phase/01-infra`), conventional-commit baseline, initial commit of the current tree — then P1.3 Docker Compose (infra default + `--profile full`, per CONF-2).

## 6. Working tree state (2026-09-30)
- **Git:** no repository, therefore **no branch, no commits, no `git status`** — the whole tree is unversioned. Git appears first at task P1.2.
- **Uncommitted changes:** everything (no git yet). This session: moved all 13 docs from repo root into `docs/` (CONF-1), closed P0.8 with the user's approval, decided CONF-1…6 + GAP-1 (§10), edited `ROADMAP.md`, `PROJECT_CONTEXT.md`, `TDD.md`, `DEPLOYMENT.md`, `PRD.md`, and created the P1.1 layout: `backend/`, `frontend/`, `infra/`, `.github/workflows/` (each with `.gitkeep`). Prior sessions authored the planning set.
- **Build / tests right now:** no build or test suite exists (no application code). Checks that *do* run, executed this session, all pass:
  - `(Get-ChildItem docs -Filter *.md).Count` → **13**; root `*.md` → **0**
  - `Select-String PROJECT_CONTEXT.md 'fill in'` → **0 matches** (excluding the self-referencing command line)
  - ROADMAP overview rows → **31** (phases 0–30); task lines → **227**; Phase headings → **31**
  - PRD §5.1 + §13 (OQ-1…6), TDD §21 + §23 (TQ-1…7), ARCHITECTURE §10 (6 prohibitions), ENGINEERING_RULES §1 (10 non-negotiables), DESIGN_SYSTEM §4 (11 components) — all present
- **Conclusion:** Phase 0 DoD **met** — P0.8 approved 2026-09-30. First commit still lands at P1.2 (`git init`), so nothing is proposed for commit yet.

## 7. Phase log
| Phase | Status | Commit | Notes / known issues |
|---|---|---|---|
| 0 | **Done ☑** | – (no git repository; first hash appears after P1.2) | P0.1–P0.8 all `[x]`; **approved by user 2026-09-30** — OQ-1…6 / TQ-1…7 accepted at defaults, CONF-1…6 + GAP-1 decided (§10); 13 docs moved from repo root into `docs/` |
| 1–30 | Not started ☐ | – | Phase 0 is approved, so Phase 1 may start when instructed (P1.1 first); checklists live in ROADMAP.md |

## 8. Decisions Locked
- Modular monolith, shared-schema multi-tenancy (`tenant_id`)
- Refresh token in HttpOnly cookie; access token in memory
- Versioned clinical records with addenda; no silent overwrite
- Outbox-based background jobs; no message broker for MVP
- "Compliance-ready" wording only — never claim HIPAA/GDPR/SOC 2 compliance

## 9. Decisions made (and why)
**Prior sessions:**
1. **Document count corrected 14 → 13** in ROADMAP P0.5 Verify and in this file's Document Map / Current State / Phase log (previous text) — `Get-ChildItem -Filter *.md` returns 13 and Phase 0's own deliverables list names exactly 13 documents; "14" was a counting error in the previous session, not a missing file (work stays `[x]`).
2. **P0.2 Verify wording corrected** ("maps every ROADMAP phase" → "maps phases 1–29") — TDD §21 verified to have rows 1…26–29 only; Phase 0 (docs) and Phase 30 (future) have no design section. Deliverable unchanged, stays `[x]`.
3. **All `[x]` tasks re-verified instead of trusted** (STEP 2 discipline): commands + results recorded in ROADMAP's Phase 0 evidence block.
4. **No application code, no new files, no commits** — handoff instruction was explicit.

**This session (2026-09-30) — user decisions at the Phase 0 sign-off:**
5. **Docs moved to `docs/`** (user instruction) — resolves CONF-1 in favour of TDD §5; all 13 `.md` files now in `docs/`, root has none.
6. **Phase 0 approved with defaults** — P0.8 → `[x]`, overview → ☑; OQ-1…OQ-6 and TQ-1…TQ-7 accepted as written (no overrides).
7. **CONF-2 → infra-only default + `--profile full`** — `docker compose up` starts MySQL/Redis/MinIO/Nginx; backend/frontend containers opt-in; apps may run natively. TDD §20.1 and DEPLOYMENT §1–2 amended.
8. **CONF-3 → keep Phase 7 depends-on 2** — foundation starts after Phase 2; P7.6/P7.7 wait on their own P5/P6 task deps. No overview change.
9. **CONF-4 → reword Phase 3 exit** — exit now requires empty-DB + Testcontainers runs; CI enforcement explicitly lands at P26.3. ROADMAP Phase 3 exit edited.
10. **CONF-5 → DATABASE §1 wins (`tenant_id` everywhere)** — `refresh_tokens`, `verification_tokens`, `user_roles`, `role_permissions` gain `tenant_id NOT NULL` + composite FKs; TDD §9.2 amended.
11. **CONF-6 → accept proposed default** — Phases 5/9 send through the SMTP abstraction with a local dev transport; async retries + outbox arrive in Phase 18.
12. **GAP-1 → reserved platform tenant** — platform admins are normal `users` rows under a fixed platform tenant id, excluded from tenant-scoped listings; reuses auth/RBAC.

## 10. Open questions and assumptions
**Assumptions I made (challenge if wrong):**
- A1: the 13-item Phase 0 deliverables list defines completeness, so the "14" was a slip (basis for decision §9.1).
- A2: Phase 0 approval happened 2026-09-30 (P0.8 `[x]`), so Phase 1 may start on instruction — but not before.
- A3: the OQ/TQ defaults below were **accepted as-is at sign-off** (no overrides), so they are in force as decided answers.
- A4: test/class names in ROADMAP `Verify:` fields (e.g. `ResponseEnvelopeTest`) are **targets to create**, not tests that exist today.

**PRD §13 — accepted at Phase 0 sign-off (2026-09-30); change only via a recorded override:**
| ID | Question | Decision (= default, accepted) |
|---|---|---|
| OQ-1 | New tenants: auto-activated after verification, or Platform Admin approval? | Automatic after verification, with Platform Admin suspend ability |
| OQ-2 | Doctor-to-patient access policy? | Assigned/previously treated, cross-doctor reads audited |
| OQ-3 | Multiple hospitals/branches per tenant? | Single hospital per tenant for MVP; model must not preclude branches |
| OQ-4 | Data retention / deletion? | No hard delete of clinical history |
| OQ-5 | Email provider? | SMTP abstraction; provider chosen in Phase 18 |
| OQ-6 | Patient portal timing? | Post-MVP |

**TDD §23 — accepted at Phase 0 sign-off (2026-09-30); change only via an ADR:**
| ID | Question | Decision (= default, accepted) |
|---|---|---|
| TQ-1 | Hibernate `@TenantId` vs `@Filter` | `@TenantId` (Hibernate 6.x) + repository isolation tests |
| TQ-2 | Password hasher | Argon2id if library approved, else bcrypt |
| TQ-3 | Email provider | SMTP abstraction; choose in Phase 18 |
| TQ-4 | Tenant activation flow | Auto after email verification |
| TQ-5 | Doctor–patient access policy | Assigned/previously treated; audit cross-doctor reads |
| TQ-6 | Sensitive column encryption scope | MFA secrets and tokens initially; extend per SECURITY.md |
| TQ-7 | Multi-branch hospitals | Not in MVP; schema keeps room (`hospital_id` nullable-ready) |

**Doc conflicts — all decided by the user 2026-09-30 (do not re-open or change silently):**
| ID | Conflicts | Decision / blocks |
|---|---|---|
| CONF-1 | TDD §5 puts docs in `docs/` (root named `healthcare-hms/`); reality was 13 files at repo root (`HealthCare`) | **Decided — `docs/` wins.** Files moved to `docs/` 2026-09-30; P1.1 keeps them there |
| CONF-2 | PROJECT_CONTEXT ran compose for infra only + native backend/frontend; DEPLOYMENT §2 / TDD §20.1 said compose also runs backend+frontend containers | **Decided — infra-only default + `--profile full`** for the app containers. TDD §20.1, DEPLOYMENT §1–2 amended. Blocks: P1.3, P1.6 (now unblocked) |
| CONF-3 | ROADMAP overview says Phase 7 depends on 2, but its Exit needs Phase 5 auth and its Scope needs Phase 6 permissions | **Decided — keep depends-on 2.** Task-level deps P7.6→P5.10 and P7.7→P6.3 carry the real ordering. Blocks: P7.6 (now unblocked) |
| CONF-4 | Phase 3 Exit required migrations "in CI"; CI/CD is Phase 26 and Phase 1 scope has no workflow files | **Decided — reworded Phase 3 exit** to empty-DB + Testcontainers; CI enforcement lands in P26.3. Blocks: P1 scope / P3 exit (now unblocked) |
| CONF-5 | DATABASE §1 said only `permissions`/`tenants` lack `tenant_id`; TDD §9.2 also showed `refresh_tokens`, `verification_tokens`, `user_roles`, `role_permissions` without it | **Decided — DATABASE §1 wins: `tenant_id` everywhere** except `tenants`/`permissions`, with composite FKs. TDD §9.2 amended. Blocks: P3.2, P3.3 (now unblocked) |
| CONF-6 | Email is P0 from Phase 5/9; notification+outbox module is Phase 18 | **Decided — proposed default accepted:** Phase 5/9 send via SMTP abstraction + local dev transport; retries/outbox arrive in Phase 18. Blocks: P5.2, P9.3, P18.3 (now unblocked) |
| GAP-1 | PRD §3 defines Platform Admin but `users.tenant_id` is mandatory — reserved platform tenant or separate table? | **Decided — reserved platform tenant** (fixed id, excluded from tenant listings; reuses auth/RBAC). Blocks: P8.5 (now unblocked) |

## 11. Known issues
| ID | Severity | Location | How to reproduce / evidence | Status |
|---|---|---|---|---|
| DOC-1 | Low | ROADMAP P0.5 Verify; PROJECT_CONTEXT Current State + Phase log (previous text) | `(Get-ChildItem -Filter *.md).Count` → 13 vs recorded "14" | **Fixed 2026-09-30** — all texts now say 13 |
| DOC-2 | Low | ROADMAP P0.2 Verify (previous text) | `Select-String TDD.md '^\| \d'` → first row is phase 1, no rows for 0/30, so "every ROADMAP phase" was false | **Fixed 2026-09-30** — wording now "phases 1–29" |
| CONF-1…6, GAP-1 | see §10 | cross-doc | decided by user 2026-09-30 (see §9.5–9.12) | **Decided — no open conflicts** |
| – | – | application code | no code exists, so no runtime bugs yet | n/a |

## 12. Reusable Building Blocks
Registry of shared code to consult **before writing anything new**.
- **Added this session:** none — no application code was written.
- Repository still contains **zero** reusable backend utilities/services or frontend components/hooks.

| Name | Path | Purpose |
|---|---|---|
| *(no entries yet)* | | |

## 13. Gotchas (read before running anything)
1. **Shell is Windows PowerShell 5.1.** `&&` is not supported — use `cmd1; if ($?) { cmd2 }`. UTF-8 glyphs (☐ ◐ — §) render as mojibake in the console; files are fine, verify with the Read tool, not `Get-Content`.
2. **Nothing runs yet:** `docker compose up`, `./mvnw …`, `npm run dev`, `npm test` all fail — no compose file, no `backend/`, no `frontend/`. ROADMAP `Verify:` commands are future targets (assumption A4).
3. **No git:** every "commit" instruction (P1.2+) is premature until the repo exists; commit hashes in §7 stay `–` until then. Do not `git init` before Phase 0 approval (P1.2 is the task that does it, on branch `phase/01-…`).
4. **File count is 13, not 14** — trust a directory listing, not any older note.
5. **Docs live in `docs/`** (moved there 2026-09-30, per user instruction — CONF-1 decided: follow TDD §5); `.github/`, `infra/` do not exist yet (created in Phase 1).
6. **Phase 0 is closed (approved 2026-09-30)** — Phase 1 starts only when instructed (P1.1 first). All CONF-*/GAP-1 were decided by the user (§10); never change a decided answer silently — raise it instead.
7. **14 docs claim anywhere else → it's the same DOC-1 bug**, not a second source of truth.

## 14. Handoff Notes
1. Read order: PROJECT_CONTEXT → PRD → TDD → ARCHITECTURE → ENGINEERING_RULES → ROADMAP → DESIGN_SYSTEM → (DATABASE, API, SECURITY, TESTING, DEPLOYMENT, AI_DEVELOPMENT_GUIDE).
2. One phase at a time. Stop at the Definition of Done; never start the next phase automatically; stop after each task and wait for "continue".
3. Work from the ROADMAP checklist for the current phase only; keep task markers (`[ ] [~] [x] [!]`) and the overview status table in sync; re-verify any `[x]` you cannot vouch for.
4. Before writing code, check §12 Reusable Building Blocks; add anything reusable when you create it.
5. All doc conflicts CONF-1…CONF-6 and GAP-1 were decided 2026-09-30 (§10) — follow those answers; raise a new conflict instead of picking silently.
6. Never claim "done" without the task's `Verify:` output.
7. Compliance wording: controls *support* HIPAA/GDPR/SOC 2-oriented requirements; never claim compliance.
8. Keep this file current — it is the agent's memory between sessions.

## 15. Lessons Carried Forward
Earlier iterations of this kind of project hit recurring problems. Guard against them from day one:
- Generic "unexpected error" instead of field-level validation messages
- Raw IDs shown instead of names in selects and lists (shared Select component bug)
- Required vs optional fields not visually distinguished
- New tenants requiring manual database activation
- Email resend timing out or redirecting away from the flow
- Broken invitation flow for staff
- Public Swagger/Prometheus, ineffective rate limiting, placeholder JWT secrets
- Tokens kept in `sessionStorage`
- Unbounded endpoints (e.g., patient timeline)
- Features marked "complete" without verification

## 16. Local Setup (provisional — NOT runnable today)
```bash
docker compose up -d        # needs compose file from P1.3 (infra default; --profile full adds backend/frontend per CONF-2)
cd backend && ./mvnw spring-boot:run   # backend/ does not exist yet (Phase 2)
cd frontend && npm install && npm run dev   # frontend/ does not exist yet (Phase 7)
```

## 17. How to Work Here
1. Read the docs in §3.
2. Work on the current phase only.
3. Follow ENGINEERING_RULES.md.
4. Verify, report, commit, stop.
