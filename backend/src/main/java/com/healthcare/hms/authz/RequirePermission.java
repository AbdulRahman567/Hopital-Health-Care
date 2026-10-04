package com.healthcare.hms.authz;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Decision <b>D3</b> — the one place an endpoint states what it needs (ROADMAP FR-3.1, "every
 * endpoint declares permission(s); default deny").
 *
 * <p>The value is a {@link PermissionCatalog} code verbatim: {@code "ROLE_VIEW"}, never {@code
 * "ROLE_VIEW"} spelled again by hand elsewhere. ArchUnit fails the build on a value that is not a
 * member of the enum, so the annotation cannot drift away from the seed either.
 *
 * <p><b>Deny by default is structural, not a convention.</b> Two independent mechanisms have to
 * agree for a request to succeed:
 *
 * <ol>
 *   <li>{@code SecurityConfig} answers whether the route is reachable at all — {@code
 *       authenticated()} for the roles and permissions surface, {@code denyAll()} for everything no
 *       phase has declared;
 *   <li>this annotation answers whether <i>that caller</i> may perform <i>that action</i>, enforced
 *       by {@link PermissionAuthorizationManager} on every method that carries it.
 * </ol>
 *
 * <p>Nothing reads the annotation to decide <i>which</i> route exists, and nothing in the service
 * layer re-checks it — the endpoint declares, the filter supplies the authorities (decision D1) and
 * the manager compares them. A method with no annotation is simply not proxied, which is exactly
 * why {@code EndpointPermissionArchUnitTest} makes an unannotated controller a build failure: an
 * anonymous hole in the handler layer would otherwise be reachable by anyone {@code SecurityConfig}
 * lets through.
 *
 * @see PermissionAuthorizationManager
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

  /**
   * The {@link PermissionCatalog} code the caller must hold.
   *
   * <p>Exactly one code per method: a handler that needs "any of" two permissions is two different
   * operations, not one.
   */
  String value();
}
