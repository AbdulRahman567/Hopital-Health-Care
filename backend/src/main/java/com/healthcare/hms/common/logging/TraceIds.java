package com.healthcare.hms.common.logging;

import org.slf4j.MDC;

/**
 * Access to the request correlation id carried in the MDC under {@value #TRACE_ID_KEY}. The value
 * is written by {@code TraceIdFilter} for every request; {@link #current()} returns {@code null}
 * outside a request context (e.g. unit tests or background jobs before tenant propagation).
 */
public final class TraceIds {

  /** MDC key holding the correlation id surfaced as {@code traceId} in envelopes and logs. */
  public static final String TRACE_ID_KEY = "traceId";

  private TraceIds() {}

  /** Returns the current request's correlation id, or {@code null} when unavailable. */
  public static String current() {
    return MDC.get(TRACE_ID_KEY);
  }
}
