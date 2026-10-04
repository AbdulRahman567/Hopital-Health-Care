package com.healthcare.hms.common.logging;

/**
 * Masks personal data before it reaches the log stream (SEC-3).
 *
 * <p>Rate-limit and lockout keys embed the identifier they are keyed by — an address being
 * registered, the peer address of a login attempt — and those keys are what a failing Redis call
 * logs. Nothing here changes what is <i>stored</i>: the full value still goes into Redis, because
 * it is the bucket name; only the log line is masked.
 *
 * <p>Deliberately lossy in both directions of usefulness: enough left to tell "the same address
 * again" from "a different one" while correlating an incident, and no more than that.
 */
public final class Pii {

  private Pii() {}

  /**
   * {@code jane.doe@example.com} becomes {@code j***@e***}.
   *
   * @param value address, or anything that is not an address
   * @return the masked address, or {@code *} when there is nothing to mask
   */
  public static String maskEmail(String value) {
    if (value == null || value.isBlank()) {
      return "*";
    }
    int at = value.indexOf('@');
    if (at < 0) {
      return star(value);
    }
    return star(value.substring(0, at)) + "@" + star(value.substring(at + 1));
  }

  /**
   * An IPv4 address keeps its network part and loses its host: {@code 192.0.2.7} becomes {@code
   * 192.0.2.*}. Anything else — an IPv6 address, a bare word — is dropped entirely, since an IPv6
   * suffix can carry a host identifier.
   *
   * @param value address as the container reported it
   * @return the masked address, or {@code *} when there is nothing to mask
   */
  public static String maskAddress(String value) {
    if (value == null || value.isBlank()) {
      return "*";
    }
    if (value.indexOf(':') >= 0) {
      return star(value);
    }
    int firstDot = value.indexOf('.');
    int lastDot = value.lastIndexOf('.');
    if (firstDot > 0 && lastDot > firstDot) {
      return value.substring(0, lastDot + 1) + "*";
    }
    return star(value);
  }

  /**
   * Masks the identifier at the end of a rate-limit or lockout key while keeping everything that
   * identifies the scope: {@code rl:ip:192.0.2.7} becomes {@code rl:ip:192.0.2.*}, and {@code
   * t:<tid>:rl:login:jane@example.com} keeps its tenant and scope and loses only the address.
   *
   * @param key the Redis key a limiter or lockout call is about to log
   * @return the masked key, or {@code *} when there is nothing to mask
   */
  public static String maskKey(String key) {
    if (key == null || key.isBlank()) {
      return "*";
    }
    // The only key whose identifier may itself contain ':' — an IPv6 peer address.
    String ipPrefix = "rl:ip:";
    if (key.startsWith(ipPrefix)) {
      return ipPrefix + maskAddress(key.substring(ipPrefix.length()));
    }
    int cut = key.lastIndexOf(':');
    if (cut < 0) {
      return star(key);
    }
    String identifier = key.substring(cut + 1);
    if (identifier.indexOf('@') >= 0) {
      return key.substring(0, cut + 1) + maskEmail(identifier);
    }
    return key.substring(0, cut + 1) + star(identifier);
  }

  private static String star(String value) {
    if (value.isEmpty()) {
      return "*";
    }
    if (value.length() == 1) {
      return "*";
    }
    return value.charAt(0) + "***";
  }
}
