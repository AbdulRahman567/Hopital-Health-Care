package com.healthcare.hms.auth;

import com.healthcare.hms.auth.api.LoginResponse;
import com.healthcare.hms.tenant.TenantContextFilter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues the short-lived access token described by decision D10 and TDD section 7.
 *
 * <p>Claims are exactly {@code sub}, {@code tenantId}, {@code roles}, {@code jti}, {@code iat} and
 * {@code exp} — nothing about the user beyond their id, because the token travels to a browser and
 * every claim in it is readable by anyone who holds it. {@code tenantId} is the same name {@link
 * TenantContextFilter} reads back (plan D10: "must equal {@code TENANT_CLAIM}"), so a token issued
 * here is what lets the P4.2 pipeline resolve a tenant with no change to the decoder.
 *
 * <p>{@code roles} is an empty array until Phase 6 assigns any: the claim is declared now so the
 * shape is stable and a client can be written against it, and discovering a missing claim later
 * would be a silent contract change.
 *
 * <p>HS256 is named explicitly in the JWS header rather than left to the encoder's default, which
 * is RS256 — an RSA default would ask the {@code oct} key for a match it can never satisfy.
 */
@Service
public class JwtTokenService {

  /** Response media type for the bearer token (API.md section 7). */
  public static final String TOKEN_TYPE = "Bearer";

  /** Claim name fixed by TDD section 7 and read by {@link TenantContextFilter}. */
  public static final String ROLES_CLAIM = "roles";

  private final JwtEncoder jwtEncoder;
  private final AuthProperties properties;

  public JwtTokenService(JwtEncoder jwtEncoder, AuthProperties properties) {
    this.jwtEncoder = jwtEncoder;
    this.properties = properties;
  }

  /**
   * Signs an access token for {@code user}.
   *
   * @param user the authenticated account; only its id reaches the token
   * @param tenantId tenant the token is scoped to
   * @return the compact JWT plus the response envelope fields
   */
  public LoginResponse issue(User user, UUID tenantId) {
    Duration ttl = properties.getAccessTokenTtl();
    Instant issuedAt = Instant.now();

    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(user.getId().toString())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(ttl))
            .id(UUID.randomUUID().toString())
            .claim(TenantContextFilter.TENANT_CLAIM, tenantId.toString())
            .claim(ROLES_CLAIM, List.of())
            .build();

    Jwt token =
        jwtEncoder.encode(
            JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
    return new LoginResponse(token.getTokenValue(), TOKEN_TYPE, ttl.toSeconds());
  }
}
