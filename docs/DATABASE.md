# Healthcare-HMS — Database Strategy

Engine: **MySQL 8** (InnoDB, `utf8mb4`). Migrations: **Flyway**. Access: Spring Data JPA / Hibernate. Detailed table list: TDD.md §9.

## 1. Multi-Tenancy
- Shared schema with `tenant_id BINARY(16) NOT NULL` on every tenant-owned table.
- Composite unique keys and foreign keys include `tenant_id` where applicable (prevents cross-tenant references).
- Hibernate tenant filtering on all entity queries; native queries must include `tenant_id` explicitly.
- Platform-level tables (`permissions`, `tenants`) are the only tables without `tenant_id`.
- Future option: move large tenants to dedicated schema/database (ADR-002).

## 2. Conventions
| Item | Rule |
|---|---|
| Table names | `snake_case`, plural |
| Primary keys | UUID (`BINARY(16)`), time-ordered (v7) preferred |
| Audit columns | `created_at`, `created_by`, `updated_at`, `updated_by`, `version` |
| Time | `DATETIME(6)` in UTC |
| Enums | `VARCHAR` + `CHECK` constraint, mapped as `EnumType.STRING` |
| Booleans | `TINYINT(1)` with defaults — MySQL 8.4 logs warning 1681 (deprecated display width) on every migration; cosmetic, keep `TINYINT(1)` |
| Money | `DECIMAL(19,4)` + currency code |
| Text | Explicit lengths; `TEXT` only for narrative fields |
| Naming | `pk_`, `fk_<table>_<ref>`, `uq_<table>_<cols>`, `idx_<table>_<cols>` — written into the DDL. **MySQL caveat (recorded at P3.8):** it always stores a primary key's index/constraint name as `PRIMARY`, whatever the DDL calls it, so `pk_` is documentation only and checks accept `PRIMARY` for primary keys. MySQL also auto-creates an `fk_<table>_<ref>` index for any FK no existing index can serve. |

## 3. Migration Rules
1. File names `V{n}__{description}.sql`; strictly sequential; never edit an applied migration.
2. Each migration is idempotent-safe on a clean database and tested in CI via Testcontainers.
3. Backward-compatible changes preferred (expand → migrate → contract) for zero-downtime.
4. Seed data (permissions, system roles) via versioned migrations, not application startup code.
5. Destructive migrations require explicit approval and a backup note.
6. `spring.jpa.hibernate.ddl-auto=validate` in all environments.

## 4. Indexing Strategy
- Index every FK column.
- Composite indexes lead with `tenant_id`, then the filter/sort columns.
- Cover common list filters: patients by name/MRN/phone; appointments by doctor/date; visits by patient/date; audit by entity/actor/time.
- Verify with `EXPLAIN`; remove unused or duplicate indexes periodically.
- Use keyset pagination for very large, append-heavy tables (timeline, audit) when offset pagination degrades.

## 5. Integrity & Concurrency
- Optimistic locking via `version` on mutable entities.
- Invariants enforced by constraints, not only code: unique MRN per tenant, unique active appointment slot per doctor, unique email per tenant.
- Foreign keys everywhere; **no cascading deletes on clinical data**.
- Transaction boundaries at service methods; short transactions; no remote calls inside.

## 6. Clinical Data Rules
- Prescriptions: header + immutable `prescription_versions` + `prescription_items`.
- Visits: editable while `OPEN`; read-only after `FINALIZED`; corrections via `addenda`.
- Entered-in-error status instead of deletion.
- Retention policy documented before any purge job exists.

## 7. Soft Delete & Archival
- Use status/`deleted_at` for business entities; repositories exclude deleted by default.
- Unique constraints must account for soft-deleted rows (e.g., include a generated column or status in the key).

## 8. Audit & Immutability
- `audit_logs` is append-only: the application DB user is granted `INSERT`/`SELECT` only.
- `audit_logs` carries only `created_at` (plus `actor_id`, the `created_by` equivalent) — the §2 audit column set `updated_at`/`updated_by`/`version` does **not** apply, because an append-only row is never updated. Recorded at P3.8.
- Partition or archive audit tables by time as they grow.

## 9. Performance Practices
- Pagination on all lists; capped page sizes.
- Avoid N+1: fetch joins, entity graphs or DTO projections.
- Prefer projections for list views; load full aggregates only for detail/edit.
- Connection pool (HikariCP) sized deliberately and monitored.
- Slow query log enabled in non-dev environments.

## 10. Security
- Least-privilege DB users: `app_rw` (runtime), `migrator` (Flyway), `readonly` (reporting).
- TLS to the database in production; credentials from secrets manager.
- Encryption at rest on volumes/managed DB; column-level encryption for MFA secrets and similar.
- No PHI in logs or slow-query output where avoidable.

## 11. Backup & Recovery
- Managed automated backups with point-in-time recovery.
- Restore drill documented and rehearsed (Phase 28); RPO/RTO defined in DEPLOYMENT.md.

## 12. Review Checklist (per migration/schema change)
- [ ] `tenant_id` present and indexed where needed
- [ ] FKs indexed; no dangerous cascades
- [ ] Constraints express invariants
- [ ] Audit columns present
- [ ] Migration runs on empty DB
- [ ] Query plans checked for new access paths
