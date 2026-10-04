package com.healthcare.hms.authz;

import com.healthcare.hms.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The two facts an authorization decision needs about the caller: <b>who</b> (the verified token's
 * {@code sub}) and <b>where</b> (the already-bound {@link TenantContext}).
 *
 * <p>Neither can come from the request. The user id is read from the signature-checked principal
 * the P4.2 chain produced, and the tenant from the context {@code TenantContextFilter} bound from
 * the same token's claim — so no header, path segment or body field can move either of them (TDD
 * section 6.2, ENGINEERING_RULES section 1.2).
 */
public final class CurrentActor {

  private CurrentActor() {}

  /**
   * The authenticated account id.
   *
   * @return the {@code sub} claim as a UUID, or empty when there is no bearer principal — which
   *     every caller treats as "no authority", never as "any authority"
   */
  public static Optional<UUID> userId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwt)) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(jwt.getToken().getSubject()));
    } catch (IllegalArgumentException | NullPointerException ex) {
      return Optional.empty();
    }
  }

  /**
   * The authenticated account id, required.
   *
   * @throws IllegalStateException when there is no bearer principal — a programming error, because
   *     every route that reaches a service is behind the authenticated matcher
   */
  public static UUID requireUserId() {
    return userId()
        .orElseThrow(
            () -> new IllegalStateException("No authenticated account is bound to this request."));
  }

  /** The tenant this request is scoped to; throws when the context is unbound (fail closed). */
  public static UUID tenantId() {
    return TenantContext.require();
  }

  /**
   * The tenant this request is scoped to, or empty when nothing is bound.
   *
   * <p>The read-only counterpart to {@link #tenantId()} for callers on the decision path rather
   * than the action path: a resource policy that cannot say where it is must answer "no", not fail
   * the read (decision D7).
   */
  public static Optional<UUID> findTenantId() {
    return TenantContext.find();
  }
}
