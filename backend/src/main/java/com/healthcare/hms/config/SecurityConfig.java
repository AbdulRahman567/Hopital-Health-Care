package com.healthcare.hms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.hms.common.api.ApiErrorWriter;
import com.healthcare.hms.common.exception.ErrorCodes;
import com.healthcare.hms.common.ratelimit.RateLimitProperties;
import com.healthcare.hms.common.ratelimit.RateLimiterService;
import com.healthcare.hms.tenant.TenantContextFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Deny-by-default security (ENGINEERING_RULES section 8): only the public health probe, the OpenAPI
 * switch and — since Phase 5 — the anonymous auth endpoints are open; every other request is
 * rejected with a standard 401/403 JSON envelope. Swagger/OpenAPI paths stay permitted so
 * springdoc's own switch decides visibility — enabled in dev (200), disabled in prod (404, P2.6).
 * Later phases replace {@code denyAll()} endpoint-by-endpoint with declared permissions.
 *
 * <p>P4.2 adds bearer-token verification (decision D1-A): an HS256 {@link JwtDecoder} built from
 * the existing {@code hms.security.jwt-secret}, so {@link TenantContextFilter} can read a verified
 * {@code tenantId} claim. Pipeline order stays TDD section 4.1 — authentication, then tenant
 * resolution, then authorization.
 *
 * <p>P5.2 opens {@code register-hospital}, {@code verify-email} and {@code resend-verification} and
 * switches the session to STATELESS with CSRF disabled (decision D5): a stateless bearer API gains
 * nothing from Spring's session-bound token, and leaving it on would 403 the very first POST before
 * P5.5 lands the custom-header guard that actually protects the cookie endpoints. P5.3 adds {@code
 * login} to the same list — it must be reachable with no token, since it is how a token is
 * obtained.
 *
 * <p>P6.2 adds {@code /api/v1/roles} and {@code /api/v1/permissions} as {@code .authenticated()}
 * routes: the first surface reachable by a signed-in caller at all. They are decided here only to
 * that depth; which permission each route needs is enforced on the handler by
 * {@code @RequirePermission} (P6.3), and {@code anyRequest().denyAll()} stays the answer for every
 * route no phase has declared.
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
      HttpSecurity http,
      ObjectMapper objectMapper,
      JwtDecoder jwtDecoder,
      RateLimiterService rateLimiter,
      RateLimitProperties rateLimitProperties)
      throws Exception {
    // D5: Spring's CSRF token is session-bound, and this app is stateless — it would reject every
    // POST from the first byte Phase 5 sends. The guard that does apply (a custom header on the
    // cookie endpoints) arrives with P5.5, where CookieLogoutTest asserts it.
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                    .permitAll()
                    // P5.2/P5.3/P5.5/P5.7: exactly these eight and nothing else. Anonymous by
                    // definition - they carry no bearer token, so TenantContext stays empty and
                    // they resolve their tenant through the D1 bootstrap lookups instead of a
                    // bearer claim. Login and refresh answer with a token P4.2 already knows how
                    // to verify; they do not need to be open to be reachable, they need to be open
                    // because they are the only way to obtain one. refresh/logout read the
                    // hms_refresh cookie instead, so their CSRF answer is CustomHeaderCsrfFilter's
                    // custom header, not Spring's session-bound token. forgot-password and
                    // reset-password are the two halves of FR-2.3: the first is a request any
                    // stranger may make, the second is authorised by the emailed token itself.
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/register-hospital",
                        "/api/v1/auth/verify-email",
                        "/api/v1/auth/resend-verification",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password")
                    .permitAll()
                    // P6.2 (decision D3): the first *authorized* surface. This is the
                    // endpoint-by-endpoint replacement of denyAll() the javadoc above promises,
                    // and it stops at exactly this depth — "somebody is signed in". Which
                    // permission each route needs is decided by @RequirePermission on the handler
                    // (P6.3), so no business rule migrates into configuration. Everything not
                    // named here keeps denyAll(), including any route a later phase forgets to
                    // declare.
                    .requestMatchers("/api/v1/roles", "/api/v1/roles/**")
                    .authenticated()
                    .requestMatchers("/api/v1/permissions")
                    .authenticated()
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
        // P5.6 (decision D7). TDD section 4.1's very first stage, so it is chained ahead of the
        // bearer filter: rate limit -> authentication -> tenant resolution -> csrf guard ->
        // authorization. Same inline construction as the two filters below, for the same reason.
        .addFilterBefore(
            new RateLimitFilter(rateLimiter, rateLimitProperties, objectMapper),
            BearerTokenAuthenticationFilter.class)
        // Deliberately constructed here, not exposed as a Filter bean: Spring Boot would otherwise
        // also register it in the servlet container at /* and run it a second time.
        .addFilterAfter(
            new TenantContextFilter(objectMapper), BearerTokenAuthenticationFilter.class)
        // P5.5 (decision D5). Positioned immediately before authorization, which is TDD section
        // 4.1's slot for it: rate limit -> authentication -> tenant resolution -> csrf guard ->
        // authorization. Same inline construction, same reason as the filter above.
        .addFilterBefore(new CustomHeaderCsrfFilter(objectMapper), AuthorizationFilter.class);
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
