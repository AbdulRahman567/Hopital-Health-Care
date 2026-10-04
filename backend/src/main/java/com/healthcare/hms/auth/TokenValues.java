package com.healthcare.hms.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Minting and hashing of the opaque secrets Phase 5 hands to clients (plan risk 11 / V4 header).
 *
 * <p>A token is 256 bits from {@link SecureRandom}, rendered base64url without padding — long
 * enough that guessing is not a threat model and URL-safe so it survives being carried in a link or
 * a cookie. Only its SHA-256 digest is ever persisted: high-entropy, single-use secrets are
 * compared by exact match, so a plain digest is correct here even though passwords need a slow KDF
 * instead (see the {@code V4__refresh_and_verification_tokens.sql} header for that reasoning).
 */
public final class TokenValues {

  private static final int TOKEN_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  private TokenValues() {}

  /** A fresh, unguessable token value as it is given to the client. */
  public static String newToken() {
    byte[] value = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(value);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  /**
   * The hex SHA-256 digest of {@code value} — the only form written to {@code token_hash}.
   *
   * @param value the raw token
   * @return 64 lowercase hex characters, matching {@code token_hash VARCHAR(64)}
   * @throws IllegalStateException if the JVM lacks SHA-256 (it never does; fails loudly rather than
   *     silently storing an unhashable token)
   */
  public static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        hex.append(Character.forDigit((b >> 4) & 0xf, 16));
        hex.append(Character.forDigit(b & 0xf, 16));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }
}
