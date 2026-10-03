package com.healthcare.hms.common.ratelimit;

/**
 * The two <i>anonymous</i> Redis key shapes of TDD section 13, as pure string builders.
 *
 * <p>Decision <b>D7</b>: {@code rl:ip:{ip}} and {@code rl:email:{email}} are the only rate-limit
 * keys that are <i>not</i> tenant-namespaced, because neither an inbound address nor an address
 * being registered belongs to a tenant yet. Everything that already knows its tenant &mdash; {@code
 * t:<tid>:rl:<scope>:<id>} for login and password reset &mdash; goes through {@code TenantKeys}
 * instead, which stays tenant-only.
 *
 * <p>No Redis client, no Spring: the same reasoning as {@code TenantKeys}, so the key shapes of TDD
 * section 13 exist in exactly one place each and every limiter bucket is built the same way.
 *
 * <p>The two prefixes never collide, so a caller cannot cross the scopes by choosing an address
 * that looks like the other one: {@code rl:email:1.2.3.4} and {@code rl:ip:1.2.3.4} are different
 * keys, and the tenant-prefixed keys are different again.
 */
public final class RateLimitKeys {

  private RateLimitKeys() {}

  /**
   * The per-address bucket: {@code rl:ip:{ip}}.
   *
   * <p>The value is {@code HttpServletRequest.getRemoteAddr()} &mdash; the peer address the
   * container observed. {@code X-Forwarded-For} is deliberately <b>not</b> read here (decision D7):
   * honouring it blindly makes the IP limit spoofable by sending a forged header, so a reverse
   * proxy is handled by Spring's {@code server.forward-headers-strategy}, which only trusts the
   * header when the peer is a configured proxy.
   *
   * @param remoteAddress peer address, or {@code null} when the container did not report one
   * @return {@code rl:ip:} followed by a normalised address; {@code rl:ip:unknown} when absent
   */
  public static String ip(String remoteAddress) {
    return "rl:ip:" + normalize(remoteAddress, "unknown");
  }

  /**
   * The per-address-being-registered bucket: {@code rl:email:{email}}.
   *
   * <p>Covers SECURITY section 12's "3/hour per email" for both registration and resend, which
   * share one window because they are two ways of asking for the same email. Built before any
   * lookup, so a request for an address that does not exist consumes exactly the same budget as one
   * that does &mdash; the anti-enumeration rule decision D7 makes explicit.
   *
   * @param email address from the request body; must not be blank
   * @return {@code rl:email:} followed by the trimmed, lower-cased address
   * @throws IllegalArgumentException when the address is missing
   */
  public static String email(String email) {
    String normalized = normalize(email, null);
    if (normalized == null) {
      throw new IllegalArgumentException("email must not be null or blank");
    }
    return "rl:email:" + normalized;
  }

  private static String normalize(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim().toLowerCase();
  }
}
