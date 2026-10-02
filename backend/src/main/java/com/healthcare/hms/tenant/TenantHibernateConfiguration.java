package com.healthcare.hms.tenant;

import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Hibernate's tenant support for the TQ-1 default ({@code @TenantId} on {@code
 * TenantOwnedEntity}).
 *
 * <p>Spring Boot only auto-registers a handful of JPA beans: a {@link
 * org.hibernate.context.spi.CurrentTenantIdentifierResolver} is <b>not</b> among them, and neither
 * is a {@code MultiTenantConnectionProvider}. Both have to be applied through a {@link
 * HibernatePropertiesCustomizer}, which is the supported extension point (plan risk 5). Should
 * Hibernate ever insist on a connection provider, the shared-schema one belongs here too — same
 * class, same justification.
 *
 * <p>No schema change is involved: the resolver only decides the predicate Hibernate generates.
 */
@Configuration
public class TenantHibernateConfiguration {

  @Bean
  TenantIdentifierResolver tenantIdentifierResolver() {
    return new TenantIdentifierResolver();
  }

  @Bean
  HibernatePropertiesCustomizer tenantIdentifierResolverCustomizer(
      TenantIdentifierResolver resolver) {
    return properties ->
        properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
  }
}
