package com.healthcare.hms.auth;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Derives a hospital's public slug from its name (plan P5.2, decision D9).
 *
 * <p>The slug is what an anonymous caller sends instead of a tenant id (decision D2): it is a
 * <i>lookup key</i>, not a binding, so it must be stable, guessable-by-the-owner and safe to echo
 * back. Deriving it deterministically from the hospital name is what makes D9's duplicate-slug 409
 * meaningful — registering the same hospital twice reports the conflict the admin can act on.
 *
 * <p>The result is lowercase, ASCII-alphanumeric with single {@code -} separators, trimmed at both
 * ends and capped at the {@code tenants.slug} column width (63).
 */
@Component
public class SlugGenerator {

  /** {@code tenants.slug VARCHAR(63)} — {@code V1__tenants_and_users.sql}. */
  static final int MAX_LENGTH = 63;

  private static final Pattern ILLEGAL = Pattern.compile("[^a-z0-9]+");
  private static final Pattern EDGE_DASHES = Pattern.compile("(^-+|-+$)");
  private static final String FALLBACK = "hospital";

  /**
   * @param hospitalName the hospital's display name
   * @return the canonical slug for that name; never empty, never longer than {@link #MAX_LENGTH}
   */
  public String generate(String hospitalName) {
    if (hospitalName == null) {
      return FALLBACK;
    }
    String slug = ILLEGAL.matcher(hospitalName.toLowerCase(Locale.ROOT)).replaceAll("-");
    slug = EDGE_DASHES.matcher(slug).replaceAll("");
    if (slug.isEmpty()) {
      return FALLBACK;
    }
    if (slug.length() > MAX_LENGTH) {
      slug = EDGE_DASHES.matcher(slug.substring(0, MAX_LENGTH)).replaceAll("");
    }
    return slug.isEmpty() ? FALLBACK : slug;
  }
}
