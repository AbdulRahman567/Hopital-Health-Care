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
 * <p>{@code roles} carries the role <i>names</i> the account held when the token was minted
 * (decision D2, P6.3): display data for a sidebar and a header, alphabetically ordered so two
 * tokens issued for the same account compare equal. It is deliberately not the permission map —
 * permissions are read from the database on every request by {@code PermissionAuthoritiesFilter},
 * so this claim can go stale the instant a role is edited and nothing in the authorization path
 * ever reads it. A token naming a role is a hint about who the caller is; it is never a statement
 * about what they may do.
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
   * @param roleNames the role names this account holds in {@code tenantId}, already resolved by the
   *     caller so this class stays free of repository and tenant knowledge (ARCHITECTURE section 3:
   *     a module's public types do not reach into another module's persistence)
   * @return the compact JWT plus the response envelope fields
   */
  public LoginResponse issue(User user, UUID tenantId, List<String> roleNames) {
    Duration ttl = properties.getAccessTokenTtl();
    Instant issuedAt = Instant.now();

    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(user.getId().toString())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(ttl))
            .id(UUID.randomUUID().toString())
            .claim(TenantContextFilter.TENANT_CLAIM, tenantId.toString())
            .claim(ROLES_CLAIM, List.copyOf(roleNames))
            .build();

    Jwt token =
        jwtEncoder.encode(
            JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
    return new LoginResponse(token.getTokenValue(), TOKEN_TYPE, ttl.toSeconds());
  }
}
