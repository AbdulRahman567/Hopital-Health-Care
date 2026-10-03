package com.healthcare.hms.auth;

import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Authentication wiring shared by every Phase 5 task (decisions D3 and D8).
 *
 * <p>Registration lands the {@link PasswordEncoder} here because it has to hash a password on the
 * very first request; login and reset reuse the same bean, so there is exactly one definition (plan
 * P5.2: "keep one definition").
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

  /**
   * {@link DelegatingPasswordEncoder} over BCrypt at strength 12 — decision D3.
   *
   * <p>The stored value keeps its {@code {bcrypt}} id, so a later cost bump or algorithm change is
   * a rehash-on-login rather than a migration. Only {@code bcrypt} is registered on purpose: {@code
   * argon2} would pull in the JNI native binaries TQ-2 flags as a risk, and nothing in the database
   * carries an {@code {argon2}} id yet.
   */
  @Bean
  PasswordEncoder passwordEncoder() {
    return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(12)));
  }
}
