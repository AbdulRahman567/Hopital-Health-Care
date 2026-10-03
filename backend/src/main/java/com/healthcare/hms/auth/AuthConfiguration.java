package com.healthcare.hms.auth;

import com.healthcare.hms.config.JwtSecretValidator;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Authentication wiring shared by every Phase 5 task (decisions D3, D8 and D10).
 *
 * <p>Registration lands the {@link PasswordEncoder} here because it has to hash a password on the
 * very first request; login and reset reuse the same bean, so there is exactly one definition (plan
 * P5.2: "keep one definition"). The same argument puts the issuer here: login (P5.3) and refresh
 * (P5.4) must sign with the one secret the P4.2 decoder already verifies against.
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

  /**
   * HS256 token issuer over the existing {@code hms.security.jwt-secret} — decision D10.
   *
   * <p>One secret for signing and verification: {@link JwtSecretValidator} is a required parameter
   * so its fail-fast check runs before the key is ever used, exactly as {@code SecurityConfig} does
   * for the decoder. {@link ImmutableSecret} wraps the raw bytes as an {@code oct} JWK, which is
   * what lets {@link NimbusJwtEncoder} sign without introducing a keystore, a key id or a new
   * dependency; the P4.2 decoder accepts the result unchanged because it is the same key and the
   * same algorithm.
   */
  @Bean
  JwtEncoder jwtEncoder(JwtSecretValidator failFastSecretValidation, Environment environment) {
    String secret = environment.getProperty(JwtSecretValidator.PROPERTY);
    SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }
}
