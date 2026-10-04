package com.healthcare.hms.auth.repository;

import com.healthcare.hms.auth.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Tenant-scoped access to {@link User} (decision D6: a repository only — no service, no controller,
 * no token logic; Phase 5 owns those).
 *
 * <p>Every method declared here is filtered by Hibernate because {@code User} inherits
 * {@code @TenantId}: the caller must have bound a tenant to {@code TenantContext}, and rows of any
 * other tenant are invisible — {@code findById} of a foreign row returns {@link Optional#empty()}
 * rather than a 403 (ADR-006).
 *
 * <p>The two native statements at the bottom are deliberate guardrails (ENGINEERING_RULES section
 * 5, TDD section 6.3): native SQL is passed to MySQL verbatim and is <b>not</b> tenant-filtered, so
 * it must state {@code tenant_id} itself. They exist for {@code TenantIsolationIT} and are never to
 * be called from application code.
 *
 * <p>P6.4 adds {@link JpaSpecificationExecutor} so a service can hand the repository a resource
 * policy's {@link org.springframework.data.jpa.domain.Specification} together with the page request
 * (TDD section 8.3: denied rows are removed in SQL, before the page is cut). It changes no existing
 * method, and the generated SQL still carries the {@code @TenantId} predicate, because the filter
 * is applied by Hibernate rather than by the query.
 */
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

  /** Email uniqueness is per tenant ({@code uq_users_tenant_email}). */
  Optional<User> findByEmail(String email);

  /** Deterministic listing order for isolation assertions. */
  List<User> findAllByOrderByEmailAsc();

  /**
   * Guardrail A — a native statement that carries its own tenant predicate and therefore returns
   * only the given tenant's rows.
   *
   * @param tenantId tenant to scope to, as the canonical UUID string
   * @return that tenant's users only
   */
  @Query(
      value = "SELECT * FROM users WHERE tenant_id = UNHEX(REPLACE(:tenantId, '-', ''))",
      nativeQuery = true)
  List<User> nativeQueryStatingTenantId(@Param("tenantId") String tenantId);

  /**
   * Guardrail B — the same statement without a tenant predicate. It returns <b>every</b> tenant's
   * rows, which is exactly why the rule exists. Test-only; never call it from application code.
   *
   * @return all users of all tenants
   */
  @Query(value = "SELECT * FROM users", nativeQuery = true)
  List<User> nativeQueryWithoutTenantId();
}
