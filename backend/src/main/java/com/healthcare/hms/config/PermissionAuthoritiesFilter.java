package com.healthcare.hms.config;

import com.healthcare.hms.authz.CurrentActor;
import com.healthcare.hms.authz.PermissionResolver;
import com.healthcare.hms.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Decision <b>D1</b> — the stage that turns "somebody is signed in" into "somebody holds these
 * permissions": {@code (tenantId, userId)} &rarr; permission codes &rarr; the request's {@link
 * org.springframework.security.core.GrantedAuthority} set.
 *
 * <p><b>Placed after {@code TenantContextFilter}, on purpose.</b> The lookup is two statements
 * whose predicates lead with {@code tenant_id}, so it cannot run before the tenant is bound — and
 * it must run before authorization, because everything downstream reads the authorities this filter
 * set. The chain therefore stays TDD section 4.1's order: rate limit &rarr; authentication &rarr;
 * tenant resolution &rarr; <b>this</b> &rarr; CSRF guard &rarr; authorization.
 *
 * <p><b>Fail closed, without exception.</b> No token, an anonymous principal, an unbound tenant or
 * an account with no roles all leave (or produce) an empty authority set. An empty set is a 403 for
 * every route that asks for a permission, which is the whole point: there is no default grant, and
 * "no row" is an answer, not an error.
 *
 * <p><b>Not a cache.</b> The two reads happen on every authenticated request so that a role edit
 * takes effect on the <i>next</i> request with no re-login (decision D1). Redis caching of this
 * result is sanctioned by TDD section 13 and deliberately deferred to P22.4 — a stale permission is
 * a security bug, not just a latency bug.
 *
 * <p>Constructed inline in {@code SecurityFilterChain} rather than exposed as a bean, exactly like
 * the two filters beside it: Spring Boot would otherwise also register it in the servlet container
 * at {@code /*} and run it a second time.
 */
public class PermissionAuthoritiesFilter extends OncePerRequestFilter {

  private final PermissionResolver permissionResolver;

  public PermissionAuthoritiesFilter(PermissionResolver permissionResolver) {
    this.permissionResolver = permissionResolver;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication existing = SecurityContextHolder.getContext().getAuthentication();
    if (existing instanceof JwtAuthenticationToken jwt && existing.isAuthenticated()) {
      SecurityContextHolder.getContext().setAuthentication(withAuthorities(jwt, codes(jwt)));
    }
    filterChain.doFilter(request, response);
  }

  /**
   * The codes this caller holds right now.
   *
   * <p>Both facts come from places a request cannot forge: the tenant from the context {@link
   * com.healthcare.hms.tenant.TenantContextFilter} bound off the signed claim, the user from the
   * same token's {@code sub} (see {@link CurrentActor}). Either missing &rarr; empty, never "all".
   */
  private Set<String> codes(JwtAuthenticationToken jwt) {
    Optional<UUID> tenantId = TenantContext.find();
    Optional<UUID> userId = CurrentActor.userId();
    if (tenantId.isEmpty() || userId.isEmpty()) {
      return Set.of();
    }
    return permissionResolver.permissionCodes(tenantId.get(), userId.get());
  }

  /**
   * The same bearer token, re-issued with the authorities just resolved.
   *
   * <p>The principal, its {@code sub} and every other claim stay exactly as the decoder produced
   * them — only the authorities change, because they are the one part of an authentication that is
   * a per-request fact rather than a per-token one.
   */
  private Authentication withAuthorities(JwtAuthenticationToken jwt, Set<String> codes) {
    List<GrantedAuthority> authorities =
        codes.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toUnmodifiableList());
    JwtAuthenticationToken enriched =
        new JwtAuthenticationToken(jwt.getToken(), authorities, jwt.getName());
    enriched.setDetails(jwt.getDetails());
    return enriched;
  }

  /**
   * Only bearer principals get an authority set.
   *
   * <p>No token and an anonymous principal pass through untouched — the entry point still answers
   * 401 for anything that requires signing in, and an anonymous authority set ({@code
   * ROLE_ANONYMOUS}) is not a catalog code, so the manager denies it either way. Skipping here also
   * keeps the anonymous auth routes off the database entirely: a login attempt would otherwise pay
   * two lookups to decide nothing.
   */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return !(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated();
  }
}
