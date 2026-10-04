package com.healthcare.hms.auth;

import com.healthcare.hms.auth.api.RegisterHospitalRequest;
import com.healthcare.hms.auth.repository.UserRepository;
import com.healthcare.hms.auth.repository.VerificationTokenRepository;
import com.healthcare.hms.common.api.FieldViolation;
import com.healthcare.hms.common.exception.ConflictException;
import com.healthcare.hms.common.exception.ErrorCodes;
import com.healthcare.hms.common.ratelimit.RateLimitKeys;
import com.healthcare.hms.common.ratelimit.RateLimitProperties;
import com.healthcare.hms.common.ratelimit.RateLimiterService;
import com.healthcare.hms.tenant.TenantContext;
import com.healthcare.hms.tenant.TenantRepository;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Hospital registration (FR-1.1, plan P5.2).
 *
 * <p><b>The D1 bootstrap in writing.</b> An anonymous POST must create a tenant it does not yet
 * have a context for, and Hibernate refuses to open a session until one is bound — so the order is
 * fixed: generate the id, bind it, open the transaction, then write. The {@code tenants} row is
 * therefore inserted by {@link JdbcTemplate} (a platform table with no {@code tenant_id}, and the
 * one write that cannot go through JPA because {@code @UuidGenerator} rejects a pre-assigned
 * identifier), while everything else in the same transaction goes through the normal repositories.
 * Both are one transaction: a failure cannot leave a tenant without its administrator.
 *
 * <p>Only the hash of the verification token is persisted; the raw value exists solely in the
 * outbound email (V4 header, plan risk 10).
 */
@Service
public class RegistrationService {

  private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

  /**
   * Writes the tenant registry row inside the already-open transaction.
   *
   * <p>{@code status/timezone} are written explicitly rather than defaulted by MySQL so the row is
   * byte-identical regardless of the server's session defaults; {@code created_by} stays NULL
   * because no acting user exists yet ({@code V1} comment: platform rows have none).
   */
  private static final String INSERT_TENANT =
      "INSERT INTO tenants (id, name, slug, status, timezone, created_at, updated_at, version)"
          + " VALUES (?, ?, ?, 'PENDING', 'UTC', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)";

  private static final String DUPLICATE_MESSAGE =
      "A hospital with this name is already registered.";

  private final SlugGenerator slugGenerator;
  private final TenantRepository tenantRepository;
  private final PasswordPolicy passwordPolicy;
  private final PasswordEncoder passwordEncoder;
  private final AuthProperties properties;
  private final UserRepository userRepository;
  private final VerificationTokenRepository verificationTokenRepository;
  private final VerificationMailer verificationMailer;
  private final TransactionTemplate transactionTemplate;
  private final JdbcTemplate jdbcTemplate;
  private final RateLimiterService rateLimiter;
  private final RateLimitProperties rateLimitProperties;

  public RegistrationService(
      SlugGenerator slugGenerator,
      TenantRepository tenantRepository,
      PasswordPolicy passwordPolicy,
      PasswordEncoder passwordEncoder,
      AuthProperties properties,
      UserRepository userRepository,
      VerificationTokenRepository verificationTokenRepository,
      VerificationMailer verificationMailer,
      TransactionTemplate transactionTemplate,
      JdbcTemplate jdbcTemplate,
      RateLimiterService rateLimiter,
      RateLimitProperties rateLimitProperties) {
    this.slugGenerator = slugGenerator;
    this.tenantRepository = tenantRepository;
    this.passwordPolicy = passwordPolicy;
    this.passwordEncoder = passwordEncoder;
    this.properties = properties;
    this.userRepository = userRepository;
    this.verificationTokenRepository = verificationTokenRepository;
    this.verificationMailer = verificationMailer;
    this.transactionTemplate = transactionTemplate;
    this.jdbcTemplate = jdbcTemplate;
    this.rateLimiter = rateLimiter;
    this.rateLimitProperties = rateLimitProperties;
  }

  /**
   * Registers a hospital and its first administrator, then sends the verification link.
   *
   * <p>Duplicate slugs are the one deliberate exception to D9's uniform 202: slugs derive from
   * hospital names, which are public information, so telling the registering admin that the name is
   * taken costs no enumeration surface and saves a dead registration. Everything else about an
   * email-shaped request stays uniform.
   *
   * @throws ConflictException 409 when the slug already exists
   * @throws com.healthcare.hms.common.exception.FieldValidationException 422 when the password
   *     breaks the policy
   * @throws com.healthcare.hms.common.ratelimit.RateLimitedException 429 when this address has
   *     asked three times in an hour (decision D7)
   */
  public void register(RegisterHospitalRequest request) {
    // Before everything else &mdash; before the policy check, before the slug, before any row is
    // read &mdash; so a repeated address costs the same budget whether or not it already exists
    // (decision D7). The per-address bucket is the anonymous one from RateLimitKeys because no
    // tenant exists yet to namespace under.
    rateLimiter.consume(
        RateLimitKeys.email(request.email()),
        rateLimitProperties.getEmailAccountLimit(),
        rateLimitProperties.getEmailAccountWindow());

    passwordPolicy.validate(request.password());
    String slug = slugGenerator.generate(request.hospitalName());

    String rawToken = TokenValues.newToken();
    String tokenHash = TokenValues.sha256Hex(rawToken);
    UUID newTenantId = UUID.randomUUID();

    try {
      TenantContext.run(
          newTenantId,
          () ->
              transactionTemplate.executeWithoutResult(
                  status -> {
                    if (tenantRepository.findBySlug(slug).isPresent()) {
                      throw duplicateSlug();
                    }
                    jdbcTemplate.update(
                        INSERT_TENANT, uuidBytes(newTenantId), request.hospitalName(), slug);
                    User admin = createAdmin(request);
                    verificationTokenRepository.save(verificationToken(admin.getId(), tokenHash));
                  }));
    } catch (DuplicateKeyException ex) {
      // uq_tenants_slug: two registrations of the same name raced past the pre-check.
      log.info("Registration rejected: slug '{}' already exists", slug);
      throw duplicateSlug();
    }

    verificationMailer.send(request.email(), request.hospitalName(), rawToken);
  }

  private User createAdmin(RegisterHospitalRequest request) {
    User admin = new User();
    admin.setEmail(request.email());
    admin.setPasswordHash(passwordEncoder.encode(request.password()));
    admin.setFirstName(request.firstName());
    admin.setLastName(request.lastName());
    admin.setStatus(UserStatus.PENDING);
    return userRepository.save(admin);
  }

  private VerificationToken verificationToken(UUID userId, String tokenHash) {
    VerificationToken token = new VerificationToken();
    token.setUserId(userId);
    token.setType(VerificationTokenType.VERIFY_EMAIL);
    token.setTokenHash(tokenHash);
    token.setExpiresAt(Instant.now().plus(properties.getVerificationTokenTtl()));
    return token;
  }

  private static ConflictException duplicateSlug() {
    return new ConflictException(
        ErrorCodes.DUPLICATE_RESOURCE,
        DUPLICATE_MESSAGE,
        List.of(new FieldViolation("hospitalName", DUPLICATE_MESSAGE)));
  }

  private static byte[] uuidBytes(UUID id) {
    return ByteBuffer.allocate(16)
        .putLong(id.getMostSignificantBits())
        .putLong(id.getLeastSignificantBits())
        .array();
  }
}
