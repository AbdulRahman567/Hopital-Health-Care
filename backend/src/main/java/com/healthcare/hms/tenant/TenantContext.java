package com.healthcare.hms.tenant;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Request-scoped tenant holder (TDD section 6.2.2): the tenant resolved from the authenticated
 * principal is stored here for the duration of one request and is always cleared in a {@code
 * finally} block — never left behind for the next task a pooled thread picks up.
 *
 * <p>ENGINEERING_RULES section 1.2 ("never trust a tenant ID from the client"): the only writers of
 * this holder are the tenant resolution filter (P4.2) and the explicit job-payload helper (P4.7).
 * No request DTO, header or query parameter may reach {@link #set(UUID)}.
 *
 * <p>Deliberately a plain {@link ThreadLocal}, never an {@link InheritableThreadLocal}: Tomcat and
 * {@code @Async} reuse pooled threads, so inheritance would hand tenant A's context to a thread
 * that later serves tenant B. Cross-thread propagation is always explicit through {@link
 * TenantJobPayload}.
 *
 * <p>Fails closed: {@link #require()} throws rather than returning {@code null}, so a repository
 * call outside a tenant scope can never degrade into an unfiltered read (ENGINEERING_RULES section
 * 1.2, TDD section 6.3).
 */
public final class TenantContext {

  private static final ThreadLocal<UUID> HOLDER = new ThreadLocal<>();

  private TenantContext() {}

  /** Binds {@code tenantId} to the current thread. */
  public static void set(UUID tenantId) {
    HOLDER.set(Objects.requireNonNull(tenantId, "tenantId must not be null"));
  }

  /** Unbinds the tenant from the current thread; safe to call when nothing is bound. */
  public static void clear() {
    HOLDER.remove();
  }

  /** The tenant bound to the current thread, if any. */
  public static Optional<UUID> find() {
    return Optional.ofNullable(HOLDER.get());
  }

  /**
   * The tenant bound to the current thread.
   *
   * @throws IllegalStateException when no tenant is bound (fail closed)
   */
  public static UUID require() {
    UUID tenantId = HOLDER.get();
    if (tenantId == null) {
      throw new IllegalStateException(
          "No tenant context is bound to the current thread. Tenant-scoped work must run inside"
              + " TenantContext.set(...) or TenantContext.run(...).");
    }
    return tenantId;
  }

  /**
   * Runs {@code work} bound to {@code tenantId}, then restores whatever was bound before — which is
   * nothing, i.e. a cleared context, when this is the outermost scope. The previous value is
   * restored in {@code finally}, so a throwing {@code work} cannot leave this thread bound to the
   * wrong tenant.
   *
   * @param tenantId tenant to bind for the duration of {@code work}
   * @param work body to execute
   */
  public static void run(UUID tenantId, Runnable work) {
    Objects.requireNonNull(work, "work must not be null");
    UUID previous = HOLDER.get();
    HOLDER.set(Objects.requireNonNull(tenantId, "tenantId must not be null"));
    try {
      work.run();
    } finally {
      if (previous == null) {
        HOLDER.remove();
      } else {
        HOLDER.set(previous);
      }
    }
  }
}
