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
| Current phase | Phase 1 — Repository & Infrastructure (**Done ☑** 2026-09-30) |
| Current task | P1.8 Phase close — `[x]` (2026-09-30). Next: **Phase 2 / P2.1** (Spring Boot skeleton) — starts only on user instruction |
| Last completed task | P1.8 PROJECT_CONTEXT local setup + phase log + ROADMAP overview — `[x]` |
| Last completed phase | **Phase 1** (2026-09-30); before it Phase 0 (2026-09-30) |
| Repository state | `main` = `893dc26` (merge of `phase/01-infra`, 23 files +2521); tree clean; compose stack **4× healthy**, every port 127.0.0.1; lint green |
| Branch | `main` (Phase 1 merged @ `893dc26`, pushed) · `phase/01-infra` (task commits `f5625a8`…`af1c339`) |
| Last verified build | `backend`: `./mvnw spotless:check` exit 0 · `frontend`: `npm run lint` / `typecheck` / `format:check` exit 0 — no application build yet (arrives Phase 2/7) |

## 5. Next action
**Phase 1 is complete (2026-09-30).** Next phase: **Phase 2 — Backend Foundation**, starting at **P2.1** — Spring Boot 3 / Java 21 skeleton with the TDD §5 package layout (`backend/`, root package `com.healthcare.hms`); Verify: `./mvnw -q compile` exits 0. Do **not** start it automatically (ENGINEERING_RULES §2.1) — wait for the user's instruction; then use the Phase Prompt (AI_DEVELOPMENT_GUIDE §6).

## 6. Working tree state (2026-09-30, phase close)
- **Git:** `b883039 "Initial project setup"` on `main`. Phase 1 on **`phase/01-infra`**, one conventional commit per task: `f5625a8` (P1.3 compose) · `5d71eaa` (P1.4 env template + gitignore) · `4e4b6c3` (P1.5 lint configs) · `607e6f9` (P1.6 smoke test) · `79274f1` (P1.7 README) · `af1c339` (P1.8 close) — merged `--no-ff` into `main` as `893dc26` (user-granted permissions at phase close) and pushed with the phase branch.
- **Build / checks right now (all green):** `./mvnw spotless:check` → 0 · `npm run lint` → 0 · `npm run typecheck` → 0 · `npm run format:check` → 0 · `docker compose ps` → 4/4 healthy · README quick-start re-run (P1.7) → healthy · `git grep` for local secret values → 0 matches · `git check-ignore -v .env` → matched (`.gitignore:2`).
- **Doc checks:** `docs/` holds the 13 design docs; root holds `README.md` (P1.7) only.
- **Build / tests right now:** no build or test suite exists (no application code). Checks that *do* run, executed this session, all pass:
  - `(Get-ChildItem docs -Filter *.md).Count` → **13**; root `*.md` → **0**
  - `Select-String PROJECT_CONTEXT.md 'fill in'` → **0 matches** (excluding the self-referencing command line)
  - ROADMAP overview rows → **31** (phases 0–30); task lines → **227**; Phase headings → **31**
  - PRD §5.1 + §13 (OQ-1…6), TDD §21 + §23 (TQ-1…7), ARCHITECTURE §10 (6 prohibitions), ENGINEERING_RULES §1 (10 non-negotiables), DESIGN_SYSTEM §4 (11 components) — all present
- **Conclusion:** Phase 0 DoD **met** (P0.8 approved); initial commit exists (`b883039`). Phase 1 work proceeds on `phase/01-infra`; merge/push cadence is the user's call at each phase's DoD (P1.8).

## 7. Phase log
| Phase | Status | Commit | Notes / known issues |
|---|---|---|---|
| 0 | **Done ☑** | `b883039` (all Phase 0 docs committed by the user's initial commit) | P0.1–P0.8 all `[x]`; **approved by user 2026-09-30** — OQ-1…6 / TQ-1…7 accepted at defaults, CONF-1…6 + GAP-1 decided (§10); 13 docs moved from repo root into `docs/` |
| 1 | **Done ☑** | `b883039` base; `f5625a8` → `af1c339` on `phase/01-infra`; merged to `main` as `893dc26` (pushed) | P1.1–P1.8 all `[x]` (2026-09-30). Compose 4× healthy, loopback-only; MinIO via openvidu mirror (§9.13); native Redis service stopped+disabled (§9.14); Spotless + ESLint/Prettier/tsc green; README commands verified runnable; no secrets committed |
| 2–30 | Not started ☐ | – | Checklists live in ROADMAP.md; a phase starts only when the previous one meets its DoD |

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

**Phase 1 (2026-09-30) — infra decisions made with the user:**
13. **MinIO source for local dev → `openvidu/minio:RELEASE.2026-07-17T12-07-51Z`** — MinIO's official images were deleted from Docker Hub (repo archived), revoked on Quay (Sept 2026), and `dl.min.io` binaries return 410 Gone; user chose the openvidu mirror. Production remains AWS S3 (DEPLOYMENT §3) — unaffected. Swap is one line in `infra/docker-compose.yml` if a better source appears.
14. **Native Windows Redis service → stopped + disabled** (user-approved) — it occupied 127.0.0.1:6379, blocking the compose Redis. Re-enable with `sc.exe start Redis` if ever needed; project Redis is the compose container.
15. **MinIO volume path = image-declared `/bitnami/minio/data`** — openvidu image runs as uid 1001 with bitnami entrypoint; `/data` doesn't exist in the image, so Docker's root-owned mountpoint caused access-denied crash loops.

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
- **Added in Phase 1** (build/lint scaffolding, no runtime code): Maven Wrapper (`backend/mvnw`, `backend/mvnw.cmd`, `.mvn/wrapper/` — no system `mvn` required), `infra/.env.example`, root `README.md`.

| Name | Path | Purpose |
|---|---|---|
| Maven Wrapper + Spotless | `backend/mvnw*`, `backend/pom.xml` | `./mvnw spotless:check` / `spotless:apply` (google-java-format 1.22.0) |
| Frontend lint/format | `frontend/eslint.config.mjs`, `.prettierrc`, `tsconfig.json` | `npm run lint` / `format:check` / `typecheck` (strict) |
| Compose stack | `infra/docker-compose.yml` | mysql/redis/minio/nginx; `--profile full` adds backend/frontend (CONF-2) |

## 13. Gotchas (read before running anything)
1. **Shell is Windows PowerShell 5.1.** `&&` is not supported — use `cmd1; if ($?) { cmd2 }` or separate lines. UTF-8 glyphs (☐ ◐ — §) render as mojibake in the console; files are fine, verify with the Read tool, not `Get-Content`. Native stderr (e.g., `java -version`) shows up as a red `NativeCommandError` — not a failure.
2. **What runs today (Phase 1 complete):** `docker compose up -d --wait` (4 healthy), `./mvnw spotless:check`, `npm run lint`. What does **NOT** yet exist: app code — `mvn spring-boot:run` (Phase 2), `npm run dev`/`build` (Phase 7), `--profile full` images (P2/P7). ROADMAP `Verify:` names for future tests are targets to create (A4).
3. **Git:** repo pushed at `b883039` on `main`; Phase 1 committed on `phase/01-infra` (`f5625a8`…P1.8). Push/merge cadence is the user's call — never force-push or rewrite pushed history. Conventional commits from here on (ENGINEERING_RULES §10).
4. **Design-doc count is 13, not 14** — that is `docs/*.md` only; the root `README.md` (P1.7) is separate and does not change the 13.
5. **Docs live in `docs/`** (CONF-1); `infra/` exists (compose, nginx, `.env.example`); `backend/` and `frontend/` hold lint scaffolding only.
6. **Phase 0 and Phase 1 are closed** (both approved/done 2026-09-30). Next phase starts only on instruction; never change a decided answer silently — raise it instead.
7. **14 docs claimed anywhere** → it's the same DOC-1 counting bug, not a second source of truth.
8. **First `./mvnw` run downloads Maven 3.9.9** from Central (~1 min, network required); there is no system `mvn` on this machine. `.gitattributes` keeps `mvnw`/`*.sh` LF so Git Bash/CI work.
9. **Host ports must be free:** 3306, 6379, 9000, 9001, 80 — the native Windows Redis service was stopped+disabled for :6379 (§9.14, reversible `sc.exe start Redis`).
10. **`infra/.env` is gitignored and never in `git status`** — if it ever appears, stop and fix `.gitignore` before committing.

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

## 16. Local Setup (verified 2026-09-30 — these exact commands ran successfully; mirrors README.md)

**Infrastructure stack** (PowerShell or bash):
```bash
cd infra
cp .env.example .env        # FIRST RUN ONLY — then replace the changeme_* values in infra/.env
docker compose up -d --wait # ~40 s; all four services report (healthy)
docker compose ps
```

**Stop / reset:**
```bash
cd infra
docker compose down         # stop containers, keep data volumes
docker compose down -v      # stop AND delete data volumes (irreversible)
```

**Lint / format checks:**
```bash
cd backend
./mvnw spotless:check       # exit 0; first run downloads Maven 3.9.9
```
```bash
cd frontend
npm install
npm run lint                # exit 0 (also: npm run typecheck, npm run format:check)
```

Application runs (`./mvnw spring-boot:run`, `npm run dev`) arrive with Phases 2/7 — they do not exist yet.

## 17. How to Work Here
1. Read the docs in §3.
2. Work on the current phase only.
3. Follow ENGINEERING_RULES.md.
4. Verify, report, commit, stop.
