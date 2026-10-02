package com.healthcare.hms.config;

import static org.springframework.security.config.Customizer.withDefaults;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.hms.common.api.ApiErrorWriter;
import com.healthcare.hms.common.exception.ErrorCodes;
import com.healthcare.hms.tenant.TenantContextFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Deny-by-default security (ENGINEERING_RULES section 8): only the public health probe is open;
 * every other request is rejected with a standard 401/403 JSON envelope. Swagger/OpenAPI paths stay
 * permitted so springdoc's own switch decides visibility — enabled in dev (200), disabled in prod
 * (404, P2.6). Later phases replace {@code denyAll()} endpoint-by-endpoint with declared
 * permissions.
 *
 * <p>P4.2 adds bearer-token verification (decision D1-A): an HS256 {@link JwtDecoder} built from
 * the existing {@code hms.security.jwt-secret}, so {@link TenantContextFilter} can read a verified
 * {@code tenantId} claim. Pipeline order stays TDD section 4.1 — authentication, then tenant
 * resolution, then authorization.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /**
   * HS256 decoder for bearer tokens.
   *
   * <p>No new secret, no new validator: the value is the one P2.7 already fail-fast validates and
   * surefire already injects. {@link JwtSecretValidator} is a required parameter on purpose — its
   * {@code afterPropertiesSet()} runs first, so an unusable secret still fails startup with the
   * documented message instead of a Nimbus key error.
   */
  @Bean
  JwtDecoder jwtDecoder(JwtSecretValidator failFastSecretValidation, Environment environment) {
    String secret = environment.getProperty(JwtSecretValidator.PROPERTY);
    SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, ObjectMapper objectMapper, JwtDecoder jwtDecoder) throws Exception {
    http.csrf(withDefaults())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            handling ->
                handling
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            writeJsonError(
                                response,
                                objectMapper,
                                401,
                                ErrorCodes.UNAUTHENTICATED,
                                "Authentication required."))
                    .accessDeniedHandler(
                        (request, response, exception) ->
                            writeJsonError(
                                response,
                                objectMapper,
                                403,
                                ErrorCodes.ACCESS_DENIED,
                                "Access denied.")))
        // P4.2: the resource server would otherwise answer an invalid bearer token with an empty
        // 401 + WWW-Authenticate (risk 3) — both handlers are re-pointed at the API.md section 3
        // envelope used everywhere else.
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer
                    .jwt(jwt -> jwt.decoder(jwtDecoder))
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            writeJsonError(
                                response,
                                objectMapper,
                                401,
                                ErrorCodes.UNAUTHENTICATED,
                                "Authentication required."))
                    .accessDeniedHandler(
                        (request, response, exception) ->
                            writeJsonError(
                                response,
                                objectMapper,
                                403,
                                ErrorCodes.ACCESS_DENIED,
                                "Access denied.")))
        // Deliberately constructed here, not exposed as a Filter bean: Spring Boot would otherwise
        // also register it in the servlet container at /* and run it a second time.
        .addFilterAfter(
            new TenantContextFilter(objectMapper), BearerTokenAuthenticationFilter.class);
    return http.build();
  }

  private void writeJsonError(
      HttpServletResponse response,
      ObjectMapper objectMapper,
      int status,
      String code,
      String message)
      throws IOException {
    ApiErrorWriter.write(response, objectMapper, status, code, message);
  }
}
