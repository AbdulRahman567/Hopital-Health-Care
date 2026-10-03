package com.healthcare.hms.auth;

import com.healthcare.hms.auth.api.LoginRequest;
import com.healthcare.hms.auth.api.LoginResponse;
import com.healthcare.hms.auth.repository.UserRepository;
import com.healthcare.hms.tenant.TenantBootstrapLookup;
import com.healthcare.hms.tenant.TenantContext;
import com.healthcare.hms.tenant.TenantRepository;
import com.healthcare.hms.tenant.TenantStatus;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Password login (FR-2.1, plan P5.3).
 *
 * <p><b>One failure for everything.</b> Unknown slug, unknown email, wrong password, a tenant that
 * has not been verified or has been suspended, an inactive account and an account with MFA enabled
 * all end in the same {@link InvalidCredentialsException}, so the 401 body is byte-identical across
 * branches (decision D9) and no caller can enumerate accounts. The order of the checks follows from
 * that: the password is always verified before any status is examined, so a suspended tenant with a
 * correct password costs the same work and looks the same as a wrong password.
 *
 * <p><b>No timing oracle.</b> An attempt that finds no account still runs one BCrypt check against
 * {@link #DUMMY_HASH} — the same cost 12 as a real one — because a login that returns in
 * microseconds tells an attacker the address does not exist. Exactly one check happens per attempt,
 * whichever branch it takes.
 *
 * <p>The tenant is resolved through decision D1's {@link TenantBootstrapLookup} <i>before</i> any
 * JPA work: with an empty {@link TenantContext} Hibernate will not open a session at all, so the
 * slug is read with {@code JdbcTemplate} first and only then is the scope bound for the rest of the
 * request.
 */
@Service
public class LoginService {

  private static final Logger log = LoggerFactory.getLogger(LoginService.class);

  /**
   * A real {@code {bcrypt}} hash, at the production cost, of a value that belongs to no account.
   *
   * <p>Used only to spend one password check when there is no stored hash to check against. It is
   * not a credential and knowing it grants nothing: it is the hash of a random UUID, and the
   * matching plaintext exists nowhere.
   */
  static final String DUMMY_HASH =
      "{bcrypt}$2a$12$t5bIqW4YVd8MvK6MdfHz3uUBO5j3iI2.G0GN1KVSWjTuiIiON4fba";

  private final TenantBootstrapLookup tenantBootstrapLookup;
  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenService jwtTokenService;

  public LoginService(
      TenantBootstrapLookup tenantBootstrapLookup,
      TenantRepository tenantRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenService jwtTokenService) {
    this.tenantBootstrapLookup = tenantBootstrapLookup;
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenService = jwtTokenService;
  }

  /**
   * Authenticates one hospital account and issues its access token.
   *
   * @throws InvalidCredentialsException 401 for every failure, always with the same body
   */
  public LoginResponse login(LoginRequest request) {
    UUID tenantId =
        tenantBootstrapLookup
            .findTenantIdBySlug(request.hospitalSlug())
            .orElseThrow(() -> reject(request.password()));
    return TenantContext.call(tenantId, () -> authenticate(request, tenantId));
  }

  private LoginResponse authenticate(LoginRequest request, UUID tenantId) {
    User user = userRepository.findByEmail(request.email()).orElse(null);
    if (user == null) {
      throw reject(request.password());
    }
    if (!passwordMatches(request.password(), user.getPasswordHash(), tenantId)) {
      throw new InvalidCredentialsException();
    }
    if (!tenantIsActive(tenantId) || user.getStatus() != UserStatus.ACTIVE) {
      log.debug("Login rejected: tenant or account is not active (tenantId={})", tenantId);
      throw new InvalidCredentialsException();
    }
    if (user.isMfaEnabled()) {
      // Decision D12: the second factor exists in the schema but its flow does not, so a MFA
      // account fails closed instead of being handed a token that skipped its factor.
      log.debug("Login rejected: MFA is enabled but not yet supported (tenantId={})", tenantId);
      throw new InvalidCredentialsException();
    }
    log.info("Login succeeded: userId={}, tenantId={}", user.getId(), tenantId);
    return jwtTokenService.issue(user, tenantId);
  }

  /**
   * The one password check a matching attempt makes.
   *
   * <p>{@link PasswordEncoder} refuses a stored value whose id it does not map — a labelless legacy
   * hash, a truncated row — by throwing rather than returning {@code false}. That must not escape:
   * one unreadable row would turn an anonymous endpoint into a 500 and break the uniformity
   * decision D9 exists to guarantee, so it is reported as the same rejection any wrong password is.
   * The dummy check keeps the branch at the same cost as every other failure.
   */
  private boolean passwordMatches(String rawPassword, String storedHash, UUID tenantId) {
    try {
      return passwordEncoder.matches(rawPassword, storedHash);
    } catch (RuntimeException ex) {
      log.warn("Login rejected: stored password hash is not decodable (tenantId={})", tenantId);
      passwordEncoder.matches(rawPassword, DUMMY_HASH);
      return false;
    }
  }

  private boolean tenantIsActive(UUID tenantId) {
    return tenantRepository
        .findById(tenantId)
        .map(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
        .orElse(false);
  }

  /**
   * Spends one password check and hands back the uniform failure.
   *
   * <p>Returned rather than thrown so call sites can write {@code throw reject(...)} and the work
   * is always done on the way out.
   */
  private InvalidCredentialsException reject(String password) {
    passwordEncoder.matches(password, DUMMY_HASH);
    log.debug("Login rejected: unknown slug or email");
    return new InvalidCredentialsException();
  }
}
