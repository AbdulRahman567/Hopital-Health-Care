package com.healthcare.hms.authz.repository;

import com.healthcare.hms.authz.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Tenant-scoped reads of {@code roles}.
 *
 * <p>Every method here inherits the {@code tenant_id = ?} predicate Hibernate adds through
 * {@code @TenantId} on {@link com.healthcare.hms.common.entity.TenantOwnedEntity}, so a foreign id
 * resolves to {@code Optional.empty()} and a listing can never include another tenant's rows —
 * which is what makes a cross-tenant {@code roleId} answer 404 rather than 403 (ADR-006).
 */
public interface RoleRepository extends JpaRepository<Role, UUID> {

  Optional<Role> findByName(String name);

  Page<Role> findAllByOrderByNameAsc(Pageable pageable);

  long countBySystemFlag(boolean systemFlag);

  boolean existsByName(String name);
}
