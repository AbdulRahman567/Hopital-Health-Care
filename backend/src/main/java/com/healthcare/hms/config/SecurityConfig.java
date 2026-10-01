package com.healthcare.hms.config;

import static org.springframework.security.config.Customizer.withDefaults;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.hms.common.api.ApiErrorResponse;
import com.healthcare.hms.common.api.ErrorDetail;
import com.healthcare.hms.common.exception.ErrorCodes;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Deny-by-default security (ENGINEERING_RULES section 8): only the public health probe is open;
 * every other request is rejected with a standard 401/403 JSON envelope. Swagger/OpenAPI paths stay
 * permitted so springdoc's own switch decides visibility — enabled in dev (200), disabled in prod
 * (404, P2.6). Later phases replace {@code denyAll()} endpoint-by-endpoint with declared
 * permissions.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper)
      throws Exception {
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
                                "Access denied.")));
    return http.build();
  }

  private void writeJsonError(
      HttpServletResponse response,
      ObjectMapper objectMapper,
      int status,
      String code,
      String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(), ApiErrorResponse.of(ErrorDetail.of(code, message)));
  }
}
