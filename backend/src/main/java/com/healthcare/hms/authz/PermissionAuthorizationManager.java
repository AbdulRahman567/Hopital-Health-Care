package com.healthcare.hms.authz;

import java.lang.reflect.Method;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Decision <b>D3</b> — the comparison half of {@link RequirePermission}: does the principal this
 * request resolved to actually hold the code the method asked for?
 *
 * <p><b>Fail closed on every branch.</b> No annotation, no authentication, an anonymous principal
 * or an authority set that simply does not contain the code all answer "denied". There is no
 * role-name fallback and no "administrator gets in anyway": the catalog is the only thing compared,
 * and a system bundle wins only because it was seeded with the code like any other role.
 *
 * <p><b>The authorities arrive from the filter, not from the token.</b> {@code
 * PermissionAuthoritiesFilter} resolves {@code (tenantId, userId)} &rarr; permission codes from the
 * database on every request, so this class reads the {@code SecurityContextHolder} and nothing
 * else. That is decision D1's defining property: a role edit takes effect on the <i>next</i>
 * request with no re-login and no re-issuance, and a token can never carry a permission map that
 * outlives the row it was copied from.
 *
 * <p><b>Why the method is re-resolved.</b> Spring AOP hands the interceptor the most specific
 * method of the target class, but the annotation is searched with {@link AnnotatedElementUtils} so
 * a composed declaration still resolves instead of silently denying — a miss here must be a wrong
 * code or a missing annotation, never an accidental lookup failure.
 */
public class PermissionAuthorizationManager implements AuthorizationManager<MethodInvocation> {

  @Override
  public AuthorizationDecision check(
      Supplier<Authentication> authentication, MethodInvocation invocation) {
    RequirePermission required = requiredPermission(invocation);
    if (required == null) {
      return new AuthorizationDecision(false);
    }
    Authentication principal = authentication == null ? null : authentication.get();
    return new AuthorizationDecision(holds(principal, required.value()));
  }

  /**
   * The single authority test the whole phase rests on.
   *
   * <p>An anonymous principal is named explicitly rather than left to the authority comparison: it
   * is authenticated by Spring's own contract, so "authenticated" alone must never be the answer.
   *
   * @param authentication the principal the filter chain resolved; {@code null} means no request
   *     context at all
   * @param code a {@link PermissionCatalog} code, compared verbatim to the authority strings the
   *     filter set
   * @return {@code true} only when an authenticated, non-anonymous principal carries exactly that
   *     code
   */
  public static boolean holds(Authentication authentication, String code) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }
    if (authentication instanceof AnonymousAuthenticationToken) {
      return false;
    }
    for (GrantedAuthority authority : authentication.getAuthorities()) {
      if (code.equals(authority.getAuthority())) {
        return true;
      }
    }
    return false;
  }

  /**
   * The annotation this invocation must satisfy, resolved against the most specific method rather
   * than the one the proxy was built from.
   *
   * @return the annotation, or {@code null} when the method carries none — which the caller treats
   *     as "deny", never as "no opinion"
   */
  private RequirePermission requiredPermission(MethodInvocation invocation) {
    Method method = AopUtils.getMostSpecificMethod(invocation.getMethod(), targetClass(invocation));
    return AnnotatedElementUtils.findMergedAnnotation(method, RequirePermission.class);
  }

  private Class<?> targetClass(MethodInvocation invocation) {
    Object target = invocation.getThis();
    return target != null
        ? AopUtils.getTargetClass(target)
        : invocation.getMethod().getDeclaringClass();
  }
}
