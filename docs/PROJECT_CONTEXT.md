# Healthcare-HMS — Project Context

> The first thing an AI agent or new developer reads. Keep it short, accurate and current. Update it at the end of every phase. **Current file: handoff snapshot 2026-09-30.**

## 1. What This Is
A **multi-tenant Healthcare Management SaaS platform**. Each hospital is a tenant. Doctors add patients and track full history: conditions, medicine history and durations, advice/recommendations, and which doctor at the same hospital prescribed what.

## 2. Stack
- **Frontend:** Next.js 15, React 19, TypeScript, Tailwind CSS, shadcn/ui, Redux Toolkit, TanStack Query, React Hook Form, Zod, Axios, Lucide React, Framer Motion
- **Backend:** Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Hibernate, Flyway, Redis, MySQL, MapStruct, Lombok, Jakarta Validation, OpenAPI
- **Infra:** Docker, Docker Compose, Nginx, MinIO (local), AWS S3 (prod)
- **Deploy target:** Vercel (frontend), AWS EC2 (backend), managed MySQL, S3

## 3. Document Map (authoritative — 13 design docs, all in `docs/`; plus `docs/progress.md`, the session tracker — not a Phase 0 deliverable)
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
| Current phase | Phase 3 — Database Foundation (**Done ☑** 2026-10-02 on `phase/03-database`; merge/push pending instruction) |
| Current task | P3.8 Docs close — `[x]` (2026-10-02). Next: **Phase 4 / P4.1** (`TenantContext`) — starts only on user instruction |
| Last completed task | P3.8 ROADMAP evidence + `DATABASE.md` deviations + PROJECT_CONTEXT + `progress.md` + `features.md` group D — `[x]` |
| Last completed phase | **Phase 3** (2026-10-02, branch complete, not merged); before it Phase 2 (2026-10-01, merged 2026-10-02), Phase 1 (2026-09-30) |
| Repository state | `main` = `aec06f8` (Phase 2 merge `a982918` + docs record); `phase/03-database` = `c92b851` → … → this docs commit; tree clean after commit; backend **76 tests green** (2026-10-02) |
| Branch | `main` (unchanged) · `phase/03-database` (P3.1…P3.8: `c92b851` `1048264` `cfb9b05` `06e757e` `06ec4f9` `0159597` `6f98beb` + this docs commit) |
| Last verified build | `backend` (2026-10-02): `./mvnw -q spotless:check` → 0 · `./mvnw test` → **76 tests, 0 failures, 0 skipped** · `./mvnw verify -Dtest=MigrationIT` → BUILD SUCCESS · `docker compose ps` 4/4 healthy · `frontend`: `npm run lint` / `typecheck` / `format:check` exit 0 |

## 5. Next action
**Phase 3 is complete on `phase/03-database` (2026-10-02, every `Verify:` run that day; 76 tests green) but not merged or pushed — the standing phase-close instruction applies.** Next phase: **Phase 4 — Multi-Tenancy**, starting at **P4.1** — `TenantContext` request-scoped holder cleared in `finally`; Verify: `./mvnw test -Dtest=TenantContextTest`. Do **not** start it automatically (ENGINEERING_RULES §2.1) — wait for the user's instruction; then use the Phase Prompt (AI_DEVELOPMENT_GUIDE §6).

## 6. Working tree state (2026-10-02, phase 3 close)
- **Git:** Phase 3 on **`phase/03-database`** (branched from `main` @ `aec06f8`), one conventional commit per task: `c92b851` (P3.1 Flyway + `ddl-auto=validate`) · `1048264` (P3.2 V1 `tenants`/`users` + `BaseEntity`/`TenantOwnedEntity`) · `cfb9b05` (P3.3 V2 roles/permissions + 53-row seed) · `06e757e` (P3.4 V3 `audit_logs`) · `06ec4f9` (P3.5 `IndexConventionIT`) · `0159597` (P3.6 `TenantIdNotNullIT`) · `6f98beb` (P3.7 `MigrationIT`) + this docs commit (P3.8). **Not merged, not pushed** — the standing phase-close instruction is outstanding.
- **Build / checks right now (all green, 2026-10-02):** `./mvnw test` → **76 tests, 0 failures, 0 skipped** (30 Phase 2 unchanged + 46 Phase 3) · `./mvnw spotless:check` → 0 · `./mvnw verify -Dtest=MigrationIT` → BUILD SUCCESS · `npm run lint` / `typecheck` / `format:check` → 0 · `docker compose ps` → 4/4 healthy · `docker ps -a` shows **no** leftover Testcontainers containers (every suite is ephemeral).
- **Backend now has a real schema:** `V1__tenants_and_users.sql`, `V2__roles_permissions_and_seed.sql`, `V3__audit_logs.sql` (Flyway, `ddl-auto: validate`), 7 schema-guard test classes (46 tests: `FlywayStartupTest`, `MigrationV1/2/3IT`, `IndexConventionIT`, `TenantIdNotNullIT`, `MigrationIT`), `hms.test.datasource.override` escape hatch for the D1 initializer.
- **Conclusion:** Phase 3 DoD met — all 8 tasks `[x]`, every `Verify:` re-run, 7 deviations recorded in ROADMAP + `DATABASE.md` §2/§8. Merge/push cadence follows the user's standing phase-close instruction.

## 7. Phase log
| Phase | Status | Commit | Notes / known issues |
|---|---|---|---|
| 0 | **Done ☑** | `b883039` (all Phase 0 docs committed by the user's initial commit) | P0.1–P0.8 all `[x]`; **approved by user 2026-09-30** — OQ-1…6 / TQ-1…7 accepted at defaults, CONF-1…6 + GAP-1 decided (§10); 13 docs moved from repo root into `docs/` |
| 1 | **Done ☑** | `b883039` base; `f5625a8` → `af1c339` on `phase/01-infra`; merged to `main` as `893dc26` (pushed) | P1.1–P1.8 all `[x]` (2026-09-30). Compose 4× healthy, loopback-only; MinIO via openvidu mirror (§9.13); native Redis service stopped+disabled (§9.14); Spotless + ESLint/Prettier/tsc green; README commands verified runnable; no secrets committed |
| 2 | **Done ☑** | `phase/02-backend`: `43dd558` → `a1d51ff`; merged `--no-ff` to `main` as **`a982918`** (2026-10-02) | P2.1–P2.9 all `[x]` (2026-10-01; re-verified 2026-10-02). 30 tests green; deny-by-default `SecurityConfig`; swagger dev-only (404 in prod); fail-fast `JwtSecretValidator`; surefire injects test-only JWT secret (§13); evidence in ROADMAP Phase 2 block; base entities deferred to Phase 3 with the first migration (plan §10.2) |
| 3 | **Done ☑** | `phase/03-database`: `c92b851` → this docs commit; **not yet merged** | P3.1–P3.8 all `[x]` (2026-10-02). 76 tests green (30 + 46); Flyway `V1`–`V3` + `ddl-auto: validate` in all profiles; 53-row permission seed; `tenant_id NOT NULL` on all 5 tenant tables; index/naming guard reads `information_schema` for every table; D1 test wiring + `hms.test.datasource.override`; 7 deviations recorded (ROADMAP evidence + `DATABASE.md` §2/§8). Merge/push pending user instruction |
| 4–30 | Not started ☐ | – | Checklists live in ROADMAP.md; a phase starts only when the previous one meets its DoD |

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
- **Added in Phase 1** (build/lint scaffolding): Maven Wrapper (`backend/mvnw`, `backend/mvnw.cmd`, `.mvn/wrapper/` — no system `mvn` required), `infra/.env.example`, root `README.md`.
- **Added in Phase 2** (backend runtime — consult before duplicating): `ApiResponse<T>`/`PageMeta`/`PaginationMapper`/`PageParams` (envelopes, `common/api`), `ApiExceptionHandler` + `ApiException`/`ErrorCodes` (errors, `common/exception`), `TraceIdFilter`/`TraceIds` (traceId MDC, `common/logging`), `SecurityConfig` (deny-by-default HTTP rules), `JwtSecretValidator` (startup secret check), `package-info.java` in every TDD §5 module.

- **Added in Phase 3** (DB foundation — consult before duplicating): `BaseEntity`/`TenantOwnedEntity` (`common/entity`, P4.4 will add `@TenantId`), `Tenant`/`TenantStatus`, `User`/`UserStatus` (`common/entity`), Flyway `V1`–`V3`, `DataSourceSecretValidator` (`@Profile("prod")`), `TestDatabaseProperties` (+ the `hms.test.datasource.override` guard), `MigrationTestSupport`, `MigrationIT`.

| Name | Path | Purpose |
|---|---|---|
| Maven Wrapper + Spotless | `backend/mvnw*`, `backend/pom.xml` | `./mvnw spotless:check` / `spotless:apply` (google-java-format 1.22.0); `./mvnw test` runs the suite (76 tests) |
| Response envelope + pagination | `backend/.../common/api/` | `ApiResponse.ok/fail`, `PageMeta`, `PageParams` (default 20, max 100) per API.md §3 |
| Error handling | `backend/.../common/exception/` | `@RestControllerAdvice`, `ErrorCodes`, field-level 422 `fields[]` |
| Trace + JSON logging | `backend/.../common/logging/`, `logback-spring.xml` | `X-Request-Id` → MDC → `traceId` in every log line (LogstashEncoder) |
| Security baseline | `backend/.../config/SecurityConfig.java` | deny-by-default, public: `/actuator/health`, `/error`, swagger (dev) |
| Entities + Flyway migrations | `backend/.../common/entity/`, `backend/src/main/resources/db/migration/` | `BaseEntity`, `TenantOwnedEntity`, `User`, `Tenant`; `V1` tenants/users · `V2` roles/permissions + 53-row seed · `V3` audit_logs |
| Test DB wiring (D1) | `backend/src/test/java/com/healthcare/hms/db/` + `backend/src/test/resources/META-INF/spring.factories` | one JVM-scoped `hms_test` MySQL injected into **every** context; `TestDatabaseProperties.apply` (bail out when `hms.test.datasource.override=true`) |
| Migration test support | `backend/src/test/java/com/healthcare/hms/db/MigrationTestSupport.java` | one shared migration MySQL per JVM + a fresh empty schema per suite; `migrationSucceeded()`/`columnExists()`/`indexColumns()`/`username()`/`password()` |
| Schema guards | `backend/src/test/java/com/healthcare/hms/.../` (`FlywayStartupTest`, `MigrationV1/2/3IT`, `IndexConventionIT`, `TenantIdNotNullIT`, `MigrationIT`) | Flyway runs from empty, `ddl-auto=validate`, index/naming conventions, `tenant_id NOT NULL`, clean-DB one-command path |
| Frontend lint/format | `frontend/eslint.config.mjs`, `.prettierrc`, `tsconfig.json` | `npm run lint` / `format:check` / `typecheck` (strict) |
| Compose stack | `infra/docker-compose.yml` | mysql/redis/minio/nginx; `--profile full` adds backend/frontend (CONF-2) |

## 13. Gotchas (read before running anything)
1. **Shell is Windows PowerShell 5.1.** `&&` is not supported — use `cmd1; if ($?) { cmd2 }` or separate lines. UTF-8 glyphs (☐ ◐ — §) render as mojibake in the console; files are fine, verify with the Read tool, not `Get-Content`. Native stderr (e.g., `java -version`) shows up as a red `NativeCommandError` — not a failure.
2. **What runs today (Phase 3 complete):** `docker compose up -d --wait` (4 healthy), `./mvnw spotless:check`, `./mvnw test` (76 tests, 0 failures — starts a Testcontainers `mysql:8.4` the first time, ~35–45 s), `./mvnw verify -Dtest=MigrationIT`, `npm run lint`. What does **NOT** yet exist: `mvn spring-boot:run` needs a real DB **plus** the app also fail-fast demands `hms.security.jwt-secret` (see 11) — no boot smoke in Phase 3, `npm run dev`/`build` (Phase 7), `--profile full` images (P2/P7), any HTTP layer (Phase 4+).
3. **Git:** Phase 1 merged `893dc26`; Phase 2 merged `--no-ff` to `main` as `a982918`; Phase 3 on **`phase/03-database`** (`c92b851` → docs close), **not merged/pushed yet**. Push/merge cadence follows the standing phase-close instruction — never force-push or rewrite pushed history. Conventional commits (ENGINEERING_RULES §10).
4. **Design-doc count is 13, not 14** — the 13 = Phase 0 deliverables. `docs/` also holds `progress.md` (session tracker, added 2026-10-01 → directory now has 14 `.md` files); root holds `README.md` (P1.7). Neither changes the 13 deliverables.
5. **Docs live in `docs/`** (CONF-1); `infra/` exists (compose, nginx, `.env.example`); `backend/` and `frontend/` hold lint scaffolding only.
6. **Phases 0, 1, 2 and 3 are closed** (Phase 0/1 2026-09-30, Phase 2 2026-10-01, Phase 3 2026-10-02 — Phase 3 branch not merged yet). Next phase starts only on instruction; never change a decided answer silently — raise it instead.
7. **14 docs claimed anywhere** → it's the same DOC-1 counting bug, not a second source of truth.
8. **First `./mvnw` run downloads Maven 3.9.9** from Central (~1 min, network required); there is no system `mvn` on this machine. `.gitattributes` keeps `mvnw`/`*.sh` LF so Git Bash/CI work.
9. **Host ports must be free:** 3306, 6379, 9000, 9001, 80 — the native Windows Redis service was stopped+disabled for :6379 (§9.14, reversible `sc.exe start Redis`).
10. **`infra/.env` is gitignored and never in `git status`** — if it ever appears, stop and fix `.gitignore` before committing.
11. **JWT secret:** surefire injects a test-only `hms.security.jwt-secret` so `@SpringBootTest` contexts pass `JwtSecretValidator`; running the app itself needs `--hms.security.jwt-secret=<48+ chars>` (or `HMS_JWT_SECRET`) — missing/placeholder/short secrets fail startup by design (P2.7). Never commit a real secret.
12. **Spring CLI-style args in tests need the `--` prefix** (`runApp("--key=value")`) — without it Spring ignores the arg (P2.7 burned an hour on this).
13. **Compiler needs `-parameters`** (set in `pom.xml`) or `@RequestParam` names are lost — if param-name errors appear, run `./mvnw clean` once; the flag only applies to recompiled classes.
14. **Dependency names:** `spring-data-commons` (there is no `spring-boot-starter-data-commons`); logstash encoder 9.0 exposes `LogstashEncoder` (no `LoggingEventEncoder`).
15. **MySQL always reports a primary key as `PRIMARY`** in `information_schema` — `CONSTRAINT pk_… PRIMARY KEY` is parsed but the name is discarded. `pk_` in the DDL is documentation only; schema checks must accept `PRIMARY` (see `IndexConventionIT`). MySQL also auto-creates an `fk_<table>_<ref>` index for any FK no existing index can service.
16. **`TINYINT(1)` logs MySQL 8.4 warning 1681** (integer display width deprecated) on every migration. Cosmetic — `TINYINT(1)` is what DATABASE §2 mandates for booleans; do not "fix" it to `BOOLEAN`.
17. **Two different test databases, deliberately:** `hms_test` (`TestDatabase`, one container per JVM, injected into every context) is **never** migrated by the migration suites; `MigrationTestSupport` starts a *separate* container and gives each suite a fresh empty schema. Do not conflate them or expect `hms_test` to contain tables.
18. **Tests that bypass the D1 initializer must set `hms.test.datasource.override=true`** (`TestDatabaseProperties.OVERRIDE_DATASOURCE_PROPERTY`). The helper uses `addFirst`, so it otherwise wins over command-line/builder properties — this is how `MigrationIT` boots a real `SpringApplication` on its own migrated schema.
19. **Per-migration ITs assert `db.migrationSucceeded("N")`, not `currentVersion()`.** `db.migrate()` applies *every* pending migration, so `currentVersion()` is only meaningful in `MigrationIT` (which owns the whole lifecycle) — an older `V1`/`V2` assertion broke the moment the next `Vx` file landed.
20. **Testcontainers costs ~35–45 s per suite** and each `./mvnw test` re-pulls nothing (image cached). `docker ps -a` must be empty of `testcontainers/*` afterwards — if one lingers, RYUK/the shutdown hook was interrupted; remove it before trusting a run.

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
