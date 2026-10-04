package com.healthcare.hms.tenant;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

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
   * Captures the bound tenant as a job payload (P4.7), so an asynchronous worker can re-establish
   * the same scope with {@link #runWith(TenantJobPayload, Runnable)}.
   *
   * <p>The actor is left {@code null} on purpose: this holder stores the tenant only, and the audit
   * layer (Phase 19) is what knows the user behind the request.
   *
   * @return a payload carrying the current tenant and the capture time
   * @throws IllegalStateException when no tenant is bound (fail closed — a job without a tenant
   *     could not be filtered)
   */
  public static TenantJobPayload toJobPayload() {
    return new TenantJobPayload(require(), null, Instant.now());
  }

  /**
   * Runs {@code work} as {@code payload}'s tenant, then restores whatever was bound before —
   * nothing, i.e. a cleared context, when the worker had no scope of its own. The previous value is
   * restored in {@code finally}, so a throwing job cannot leave this thread bound to the wrong
   * tenant (the same discipline as the tenant resolution filter).
   *
   * @param payload tenant to bind for the duration of {@code work}
   * @param work body to execute
   */
  public static void runWith(TenantJobPayload payload, Runnable work) {
    Objects.requireNonNull(payload, "payload must not be null");
    run(payload.tenantId(), work);
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

  /**
   * Runs {@code work} bound to {@code tenantId} and returns its result, then restores whatever was
   * bound before — the same discipline as {@link #run(UUID, Runnable)}.
   *
   * <p>Phase 5's bootstrap legs need a return value (the row just written, the token just issued),
   * which a {@link Runnable} cannot carry without an array holder at every call site.
   *
   * @param tenantId tenant to bind for the duration of {@code work}
   * @param work body to execute
   * @param <T> result type
   * @return whatever {@code work} returned
   */
  public static <T> T call(UUID tenantId, Supplier<T> work) {
    Objects.requireNonNull(work, "work must not be null");
    Object[] box = new Object[1];
    run(
        tenantId,
        () -> {
          box[0] = work.get();
        });
    @SuppressWarnings("unchecked")
    T typed = (T) box[0];
    return typed;
  }
}
