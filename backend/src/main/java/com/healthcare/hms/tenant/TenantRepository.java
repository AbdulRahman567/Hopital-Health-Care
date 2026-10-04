package com.healthcare.hms.tenant;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for the {@code tenants} registry — a <b>platform</b> table with no {@code tenant_id}
 * (DATABASE section 1), so its queries are deliberately <i>not</i> tenant-filtered: slug uniqueness
 * is global, and a registration must be able to see that a slug is taken before the tenant it is
 * about to create exists.
 *
 * <p>Like every other JPA entry point it still needs a bound {@link TenantContext}: Hibernate
 * refuses to open a session without one, even for a platform table (decision D1's probe). Callers
 * therefore bind first — registration binds the id it is about to write — which is why the
 * duplicate-slug check lives inside that bound transaction rather than before it.
 *
 * <p>Anonymous endpoints that must resolve a slug <b>before</b> any context exists do not come
 * here: they use {@code TenantBootstrapLookup}, the documented {@code JdbcTemplate} key-directory
 * (plan-Phase-5 decision D1, slug leg).
 */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

  /** Slug is unique per {@code uq_tenants_slug}, so this returns at most one row. */
  Optional<Tenant> findBySlug(String slug);
}
