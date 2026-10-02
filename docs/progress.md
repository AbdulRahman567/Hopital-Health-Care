# Healthcare-HMS — Progress Tracker

> Session-crossing dashboard. **Update this file at every phase close (and after any significant fix).**
> Detail lives elsewhere: status truth = `ROADMAP.md` checkboxes · memory = `PROJECT_CONTEXT.md` · rules = `ENGINEERING_RULES.md` · prompts = `AI_DEVELOPMENT_GUIDE.md`.

**Last updated:** 2026-10-02 — **Phase 4 Multi-Tenancy complete on `phase/04-multitenancy`** (8/8 tasks, 147 tests green; not merged/pushed)

---

## 1. Where we are

| Phase | Status | Notes |
|---|---|---|
| 0 — Documentation & Architecture | ☑ **Done** | Approved 2026-09-30; 13 design docs in `docs/`; `b883039` |
| 1 — Repository & Infrastructure | ☑ **Done** | 8/8 tasks; merged `893dc26` → `ff90709`; pushed; deep tests green |
| 2 — Backend Foundation | ☑ **Done** | 9/9 tasks (2026-10-01); re-verified 2026-10-02; 30 tests green; merged `--no-ff` → `main` as `a982918` |
| 3 — Database Foundation | ☑ **Done** | 8/8 tasks (2026-10-02); 76 tests green; merged `--no-ff` → `main` as `493c6e0` + pushed |
| 4 — Multi-Tenancy | ☑ **Done** | 8/8 tasks (2026-10-02); 147 tests green; complete on `phase/04-multitenancy`, **not merged/pushed** |
| 5–30 | ☐ Not started | Next: **Phase 5 — Authentication** (starts at **P5.1** `refresh_tokens` migration) — only on user instruction |

**Verified snapshot (last run 2026-10-02):** `./mvnw spotless:check` **0** · `./mvnw test` **147 tests, 0 failures, 0 skipped** (76 Phases 1–3 + 71 Phase 4) · `./mvnw verify -Dtest=MigrationIT` **BUILD SUCCESS** · `npm run lint`/`typecheck`/`format:check` 0 · `docker compose ps` **4/4 healthy** · no `testcontainers/*` left in `docker ps -a` · tree clean after this docs commit · no secrets tracked (surefire uses a test-only JWT secret).

## 2. What Phase 4 delivered (2026-10-02)

- **Tenant context** (`tenant/TenantContext`): ThreadLocal holder with `set`/`clear`/`find`/`require`/`run`; `require()` throws when empty (fail closed); deliberately **not** `InheritableThreadLocal`; `run()` restores the previous value in `finally` — 10 tests (`TenantContextTest`)
- **Tenant resolution** (`tenant/TenantContextFilter` + `config/SecurityConfig`): **D1-A** HS256 `NimbusJwtDecoder` from the existing `hms.security.jwt-secret` (P2.7 validator kept as a bean dependency so its message still wins); filter added after `BearerTokenAuthenticationFilter` (TDD §4.1 order); claim missing/unparseable → **401**, valid claim → 403 from `denyAll()` (the wiring proof); `ApiErrorWriter` renders the API.md §3 envelope from outside `DispatcherServlet` — 11 tests (`TenantContextFilterTest`), full suite back to 97
- **Client hints** (`X-Tenant-ID`): absent/equal → continue, different (incl. a different valid UUID) → **404 and the chain never runs**; only evaluated when a tenant was resolved — 6 tests (`TenantHintRejectionTest`)
- **Hibernate filtering (TQ-1):** `@TenantId` on `TenantOwnedEntity` + `TenantIdentifierResolver` registered via `HibernatePropertiesCustomizer` (Spring Boot auto-registers neither); `findAll`/`count`/`findById`/`findByEmail` scoped, foreign row = `Optional.empty()` (ADR-006 primitive), `save()` stamps `tenant_id`, **empty context throws** — 8 tests (`HibernateTenantFilterTest`); no migration, `ddl-auto: validate` stayed green
- **Isolation suite:** `hospital-a` (3 rows) vs `hospital-b` (2 rows) through `UserRepository` only — listings/ids/counts/page totals scoped; native guardrail **with** `tenant_id` → one tenant, **without** it → both tenants even with a tenant bound — 7 tests (`TenantIsolationIT`)
- **Key helpers (D7):** `TenantKeys.redis(...)` → `t:<tid>:…`, `TenantKeys.storage(...)` → `tenants/<tid>/…`; `..`/`/`/`\` rejected, prefix unomissable — 22 tests (`TenantKeyPrefixTest`)
- **Async contract:** `TenantJobPayload(tenantId, actorId, capturedAt)` + `TenantContext.toJobPayload()`/`runWith()`; clears in `finally` even when the job throws; no executor/queue/broker (outbox worker = Phase 18) — 7 tests (`JobTenantPayloadTest`)
- **Review (P4.8):** AI_DEVELOPMENT_GUIDE §7/§8 run read-only — 0 P0/P1/P2, 3 P3 (ISO-1…3), 4 informational (ISO-4…7) in `PROJECT_CONTEXT` §11; **no ADR raised** (TQ-1 `@TenantId`, D1-A, D5 fail-closed intent all unchanged); 7 plan deviations recorded in ROADMAP
- **Gates:** `./mvnw test` → **147 tests, 0 failures**; `./mvnw spotless:check` → 0

### What Phase 3 delivered (2026-10-02) — for reference

- **Flyway + validate** (`pom.xml`, `application{,-dev,-prod}.yml`): `flyway-core` + `flyway-mysql`, `spring.flyway.locations=db/migration`, **`ddl-auto: validate` in all three profiles** (dev/prod re-pinned over the shared default), `DataSourceSecretValidator` `@Profile("prod")` on D7 names (`HMS_DB_URL`/`HMS_DB_USERNAME`/`HMS_DB_PASSWORD`) — 5 tests (`FlywayStartupTest`)
- **Migrations:** `V1__tenants_and_users.sql` · `V2__roles_permissions_and_seed.sql` (16 modules, **53 permission rows**, plus `uq_roles_tenant_id`/`uq_users_tenant_id`) · `V3__audit_logs.sql` (append-only, no `updated_at`/`updated_by`/`version`) — 8 + 11 + 8 tests, Flyway history order `1,2,3`
- **Entities (D2):** `BaseEntity` + `TenantOwnedEntity` (`common/entity`) — `@TenantId` filtering deliberately deferred to **P4.4**; `Tenant`/`TenantStatus`, `User`/`UserStatus` (D4: `first_name`/`last_name`)
- **DB guards:** `IndexConventionIT` (every FK indexed · composite indexes lead with `tenant_id` except platform tables · no `ON DELETE CASCADE` · `pk_/fk_/uq_/idx_/chk_` + `PRIMARY`) — 4 tests; `TenantIdNotNullIT` (`tenant_id NOT NULL` on 5 tables, positive control) — 7 tests; `MigrationIT` (clean schema → `migrationsExecuted=3` → real `SpringApplication` boots with `hibernate.hbm2ddl.auto=validate` → second run executes 0) — 3 tests
- **Test wiring (D1, option B):** one JVM-scoped `hms_test` MySQL injected into **every** context through `src/test/resources/META-INF/spring.factories` (`ApplicationContextInitializer` **and** `ContextCustomizerFactory`) — **no pre-existing test class was edited**; `TestDatabaseProperties` + `hms.test.datasource.override` let `MigrationIT` opt out
- **Test data isolation:** `MigrationTestSupport` = one shared migration container per JVM + a fresh empty schema per suite, deliberately separate from `hms_test`
- **Gates:** `./mvnw test` → **76 tests, 0 failures**; `./mvnw spotless:check` → 0; 7 deviations recorded (ROADMAP evidence block + `DATABASE.md` §2/§8)

### What Phase 2 delivered (2026-10-01) — for reference

- **Skeleton** (`backend/`): Spring Boot **3.5.16** / Java 21, root package `com.healthcare.hms`, TDD §5 module layout seeded via 20 `package-info.java`, profile configs `application{,-dev,-prod}.yml`, `logback-spring.xml`
- **Envelopes** (`common/api`): `ApiResponse<T>` NON_NULL per API.md §3, `PageMeta`/`PaginationMapper`/`PageParams` (default 20, max 100, page ≥0), `TraceIds` — 8 tests
- **Errors** (`common/exception`): `@RestControllerAdvice` with 422 + `error.fields[]` per field, unknown-property 422 (mapper fails-on-unknown like the app), 400/404/409/500 envelopes, traceId surfaced — 10 tests
- **Logging** (`common/logging`): `TraceIdFilter` (X-Request-Id sanitize/generate → MDC), JSON via `LogstashEncoder` 9.0, `request_completed` debug line (no query string) — 2 tests
- **Security** (`config/SecurityConfig`): deny-by-default (`anyRequest().denyAll()`), public `/actuator/health` + `/error` + swagger (dev), CSRF kept on, JSON 401/403 — 4 tests
- **OpenAPI**: swagger-ui + `/v3/api-docs` 200 in dev, **404 in prod** (springdoc `enabled=false`) — 2 tests
- **Fail-fast secret** (`config/JwtSecretValidator`): min 32 chars, placeholder deny-list, never echoes the secret; missing/placeholder/short all kill startup — 4 tests
- **Gates:** `./mvnw test` → **30 tests, 0 failures**; surefire injects a test-only `hms.security.jwt-secret` so every `@SpringBootTest` context passes validation

### What Phase 1 delivered (2026-09-30 → 10-01) — for reference

- **Compose stack** (mysql 8.4, redis 7.4, minio openvidu mirror, nginx 1.27): healthchecks, `127.0.0.1`-only ports, `--profile full` opt-in (CONF-2) · env hygiene (`.env.example`, `.gitignore`, secret scans clean)
- **Lint gates:** backend Spotless via Maven Wrapper (no system `mvn`) · frontend ESLint 9 + Prettier 3 + `tsc` strict · `README.md` quick start verified in a clean shell
- **Deep tests 3/3:** fresh-clone E2E · Git Bash `./mvnw` (LF) · post-merge gate re-run → found + fixed CRLF bug `ff90709` (`frontend/** text eol=lf`)

## 3. Commit history (Phases 0–3 pushed to `origin` — Phase 3 merged as `493c6e0`; **Phase 4 = local only on `phase/04-multitenancy`, not pushed**)

| Commit | What |
|---|---|
| `b883039` | Initial project setup (Phase 0 docs) — by user |
| `f5625a8` | P1.3 compose stack with healthchecks |
| `5d71eaa` | P1.4 env template + complete gitignore |
| `4e4b6c3` | P1.5 lint/format config (Spotless, ESLint/Prettier/tsc) |
| `607e6f9` | P1.6 stack smoke-test evidence |
| `79274f1` | P1.7 README (verified runnable) |
| `af1c339` | P1.8 phase close (PROJECT_CONTEXT + ROADMAP ☑) |
| `893dc26` | merge `--no-ff` phase/01-infra → main |
| `0bf724b` | docs: record merge |
| `ff90709` | fix: LF endings for frontend files (deep-test finding) |
| `3c35b31` | docs: add progress tracker for session handoff |
| `43dd558` | P2.1 Spring Boot skeleton + TDD §5 package layout |
| `85596ae` | P2.2 response envelope + pagination helpers |
| `557a8df` | P2.3 centralized exception handler (field-level 422) |
| `d7ae274` | P2.4 structured JSON logging + traceId filter |
| `08b838f` | P2.5 deny-by-default security, health public |
| `b8f66fe` | P2.6 OpenAPI dev-only (404 in prod) |
| `5cab819` | P2.7 fail-fast JWT secret validation |
| `620e161` | P2.9 docs: ROADMAP ☑ + PROJECT_CONTEXT + progress.md (P2.8 suite green: 30 tests) |
| `a1d51ff` | P2.9 docs: `features.md` group C ticked + 2026-10-02 re-verification evidence |
| `a982918` | merge `--no-ff` phase/02-backend → main (2026-10-02) |
| `aec06f8` | docs: record Phase 2 merge a982918 and 2026-10-02 re-verification (main) |
| `c92b851` | P3.1 Flyway + JPA `ddl-auto=validate` in all profiles + D1 test wiring + `DataSourceSecretValidator` |
| `1048264` | P3.2 migration V1 tenants/users (+ `BaseEntity`, `TenantOwnedEntity`, `MigrationTestSupport`) |
| `cfb9b05` | P3.3 migration V2 roles/permissions + 53-row permission seed |
| `06e757e` | P3.4 migration V3 append-only `audit_logs` |
| `06ec4f9` | P3.5 permanent index + naming convention guard (`IndexConventionIT`) |
| `0159597` | P3.6 `tenant_id NOT NULL` proof (`TenantIdNotNullIT`) |
| `6f98beb` | P3.7 clean-database one-command migration IT (`MigrationIT`) + `hms.test.datasource.override` |
| `fb6e68f` | P3.8 docs close: ROADMAP ☑ + evidence, `DATABASE.md` deviations, PROJECT_CONTEXT, progress, `features.md` group D |
| `493c6e0` | merge `--no-ff` phase/03-database → main + pushed (2026-10-02) |
| `5a2145e` | P4.1 `TenantContext` request-scoped holder (fail-closed `require()`, `run` restores in `finally`) |
| `fd28bf6` | P4.2 HS256 `JwtDecoder` + `TenantContextFilter` (claim → context; missing claim → 401) |
| `deb8b02` | P4.3 `X-Tenant-ID` hint guard rejects mismatch with 404, never switches tenant |
| `b2976ee` | P4.4 `@TenantId` on `TenantOwnedEntity` + fail-closed `TenantIdentifierResolver` |
| `ed1646f` | P4.5 cross-tenant repository isolation suite (`TenantIsolationIT`, hospital-a vs hospital-b) |
| `9cfebc9` | P4.6 `TenantKeys` tenant-prefixed Redis/storage key builders |
| `b31dad7` | P4.7 `TenantJobPayload` capture/restore for async workers |
| (this commit) | P4.8 docs close: ROADMAP ☑ + evidence, isolation review findings, PROJECT_CONTEXT, progress, `features.md` group E |

## 4. Next session — how to resume

1. Read order: **this file → `PROJECT_CONTEXT.md` (§4 state, §5 next action, §13 gotchas) → `ROADMAP.md`** current phase.
2. Start with "continue", or paste the Phase Prompt (`AI_DEVELOPMENT_GUIDE.md` §6) for **Phase 5**.
3. **Close Phase 4 first if not yet done:** the standing phase-close instruction (`--no-ff` merge `phase/04-multitenancy` → `main`, push, record the hash) has **not** been executed — ask the user before doing it.
4. New phase branch: `git checkout -b phase/05-authentication main` (ENGINEERING_RULES §10); conventional commits; one task per "continue"; stop at DoD. `Plans/` stays untracked — never `git add Plans/`.
5. **First task P5.1** — Migration `refresh_tokens` / `verification_tokens` (hashed, expiring, single-use, `tenant_id NOT NULL` per CONF-5); Verify: `./mvnw test -Dtest=MigrationAuthIT`.
6. Stack is running (4× healthy). Fresh start: `cd infra && docker compose up -d --wait`. Full commands = `PROJECT_CONTEXT` §16 / `README.md`.

## 5. Top constraints for the new session

1. **PowerShell 5.1:** no `&&`; console mangles UTF-8 glyphs (`☑`, `§`) — trust files via Read tool, not `Get-Content`.
2. **Host ports must be free:** 3306, 6379, 9000, 9001, 80. Native Windows Redis service was stopped+disabled for 6379 (revert: `sc.exe start Redis`).
3. **MinIO image = `openvidu/minio:RELEASE.2026-07-17T12-07-51Z`** (official images deleted everywhere) — one-line swap in compose if a better source appears; prod stays AWS S3.
4. **Backend test rule:** surefire injects a test-only `hms.security.jwt-secret`; running the app itself needs `--hms.security.jwt-secret=<48+ chars>` (or `HMS_JWT_SECRET`). Spring CLI args in tests must have the `--` prefix. Keep `mvnw`/`*.sh`/`frontend/**` LF (`.gitattributes`).
5. **Never commit `infra/.env`** (gitignored). Never start the next phase automatically (ENGINEERING_RULES §2.1).
6. **Two test databases, never conflate them:** `hms_test` (per-JVM `TestDatabase`, injected into every context) vs `MigrationTestSupport`'s separate container with a fresh schema per suite. Per-migration ITs assert `db.migrationSucceeded("N")`, **not** `currentVersion()` (a later `Vx` lands, `migrate()` applies everything).
7. **MySQL quirks (recorded in `DATABASE.md` §2):** PKs always surface as `PRIMARY`; FKs get an auto `fk_*` index; `TINYINT(1)` logs warning 1681 (leave it). Full `./mvnw test` costs ~2–3 min because each suite starts a Testcontainers MySQL (~35–45 s).
8. **Tenant context is mandatory for any repository work:** after startup, a JPA call on an empty `TenantContext` fails with `No tenant context is bound…` (by design — see `PROJECT_CONTEXT` §13.21). Wrap work in `TenantContext.run(...)` / `runWith(payload, …)`; native SQL is **never** tenant-filtered and must state `tenant_id` itself.

## 6. Session log

| Date | Outcome |
|---|---|
| ≤2026-09-30 | Phase 0 authored, cross-reviewed, approved (OQ/TQ defaults, CONF-1…6 + GAP-1 decided) |
| 2026-09-30 | Phase 1 started: layout, git init/push, compose stack (MinIO source + Redis port blockers resolved with user approval) |
| 2026-10-01 | Phase 1 P1.4–P1.8 complete → merged & pushed; 3 deep tests pass; CRLF fix `ff90709` — **phase closed** |
| 2026-10-01 | Phase 2 P2.1–P2.9 complete on `phase/02-backend`; 30 tests green; docs closed |
| 2026-10-02 | Phase 2 plan executed (option A verify & reuse): all `Verify:` commands re-run green, boot smoke + fail-fast checked, `features.md` group C committed, merged `a982918` → **phase closed** |
| 2026-10-02 | Phase 3 P3.1–P3.8 complete on `phase/03-database` (D1–D7 + CONF-5 accepted up front): Flyway V1–V3, `ddl-auto=validate`, 53-permission seed, index/`tenant_id`/`MigrationIT` guards, 76 tests green, 7 deviations recorded → **merged `493c6e0` + pushed (phase closed)** |
| 2026-10-02 | Phase 4 P4.1–P4.8 complete on `phase/04-multitenancy` (D1–D8 accepted up front): tenant claim → `TenantContext` → `@TenantId` filtering, `X-Tenant-ID` mismatch → 404, isolation suite, `TenantKeys`/`TenantJobPayload`, security review (0 P0/P1/P2, **no ADR**), 147 tests green, 7 deviations recorded → **phase complete, merge/push pending** |
