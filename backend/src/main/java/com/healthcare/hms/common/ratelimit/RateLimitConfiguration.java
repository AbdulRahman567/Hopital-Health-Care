package com.healthcare.hms.common.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables {@link RateLimitProperties}.
 *
 * <p>Deliberately lives next to the limiter rather than in {@code AuthConfiguration}: the IP scopes
 * are enforced by a filter in {@code config} that has never heard of authentication, and the
 * properties describe SECURITY section 12's table, not a Phase 5 token lifetime.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {}
