package com.healthcare.hms.authz;

import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Decision <b>D3</b> — turns {@link RequirePermission} from a comment into an enforced check.
 *
 * <p>Spring's method-security infrastructure is switched on here and the {@code
 * AuthorizationManagerBeforeMethodInterceptor} it works with is pointed at {@link
 * PermissionAuthorizationManager}, with a pointcut that matches <i>only</i> methods carrying {@link
 * RequirePermission}. Two consequences follow from that choice and both are deliberate:
 *
 * <ul>
 *   <li>nothing else in the application is proxied — a class with no annotated method is not a
 *       candidate at all, so services, repositories and the anonymous {@code AuthController} pay
 *       exactly nothing for this;
 *   <li>the advice runs <i>inside</i> the controller invocation, so a refusal leaves through the
 *       ordinary {@code ExceptionTranslationFilter} and comes back as the standard 403 envelope
 *       rather than as a framework-specific error page.
 * </ul>
 *
 * <p>The default {@code denyAll()} in {@code SecurityConfig} is the other half of decision D1 and
 * is untouched: this class decides <b>who may call an endpoint that already exists</b>, never which
 * endpoints exist.
 *
 * @see RequirePermission
 * @see PermissionAuthorizationManager
 */
@Configuration
@EnableMethodSecurity
public class AuthorizationConfiguration {

  /** The comparison itself; a bean only so it can be named, mocked and asserted on. */
  @Bean
  PermissionAuthorizationManager permissionAuthorizationManager() {
    return new PermissionAuthorizationManager();
  }

  /**
   * Registers the check as an advisor.
   *
   * <p>The pointcut is method-annotation only, which is what keeps "unannotated" a build failure
   * (see {@code EndpointPermissionArchUnitTest}) instead of a silent pass: a method without {@link
   * RequirePermission} is never advised, so it can only ever be reached through whatever {@code
   * SecurityConfig} already said about its route.
   */
  @Bean
  AuthorizationManagerBeforeMethodInterceptor requirePermissionInterceptor(
      PermissionAuthorizationManager permissionAuthorizationManager) {
    return new AuthorizationManagerBeforeMethodInterceptor(
        AnnotationMatchingPointcut.forMethodAnnotation(RequirePermission.class),
        permissionAuthorizationManager);
  }
}
