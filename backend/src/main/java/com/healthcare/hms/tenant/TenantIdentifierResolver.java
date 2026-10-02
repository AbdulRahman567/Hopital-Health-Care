package com.healthcare.hms.tenant;

import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * The one value Hibernate is allowed to filter by (TQ-1 default {@code @TenantId}, TDD section
 * 6.3).
 *
 * <p>It reads {@link TenantContext}, which only the tenant resolution filter and the job-payload
 * helper may write — so the SQL a query generates can never be steered by a client-supplied value
 * (ENGINEERING_RULES section 1.2).
 *
 * <p>Fails closed on purpose: once every singleton exists an empty context throws instead of
 * returning {@code null}. A {@code null} identifier would leave Hibernate without a tenant to
 * filter on, which is the exact failure this phase exists to prevent (plan risk 4 and 6).
 *
 * <p><b>Context refresh is the one exception.</b> Hibernate asks for the tenant identifier on
 * <i>every</i> session creation, and Spring Data JPA creates an EntityManager while it validates
 * derived repository queries — i.e. during context refresh, before any request thread and before
 * any tenant can be bound. Until then the resolver hands out {@link #REFRESH_PHASE_TENANT}, a zero
 * UUID that no {@code tenants} row can ever carry: statements built in that window are scoped to a
 * tenant that does not exist (empty result, foreign-key violation on insert) instead of being
 * unfiltered. The switch to fail-closed happens in {@link #afterSingletonsInstantiated()}, which
 * Spring invokes after all singletons — repositories included — have been created and before the
 * web server starts accepting traffic.
 *
 * <p>Registered through {@link TenantHibernateConfiguration} — Spring Boot does not auto-register
 * beans of this type.
 */
public class TenantIdentifierResolver
    implements CurrentTenantIdentifierResolver<UUID>, SmartInitializingSingleton {

  /**
   * Handed to Hibernate while the application context is still refreshing. The all-zero UUID is not
   * a tenant: {@code users.tenant_id} (and every other tenant-owned table) carries a foreign key to
   * {@code tenants}, so an insert stamped with it fails loudly, and a read scoped to it matches
   * nothing.
   */
  private static final UUID REFRESH_PHASE_TENANT = new UUID(0L, 0L);

  private volatile boolean tenantContextRequired;

  @Override
  public UUID resolveCurrentTenantIdentifier() {
    if (tenantContextRequired) {
      return TenantContext.require();
    }
    return TenantContext.find().orElse(REFRESH_PHASE_TENANT);
  }

  /**
   * Flips this resolver to fail closed. Called once every non-lazy singleton — every repository,
   * and with it every startup-time query validation — has been created.
   */
  @Override
  public void afterSingletonsInstantiated() {
    tenantContextRequired = true;
  }

  /**
   * Hibernate re-checks sessions it has already opened against the identifier this resolver reports
   * now ({@code TenantIdentifierMismatchException} on a mismatch). There is one tenant per session,
   * so the check always passes; it stays enabled because a mismatch would be a real bug worth
   * failing on.
   */
  @Override
  public boolean validateExistingCurrentSessions() {
    return true;
  }
}
