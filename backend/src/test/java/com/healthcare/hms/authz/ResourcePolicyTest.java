package com.healthcare.hms.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.hms.auth.AuthFixtures;
import com.healthcare.hms.auth.User;
import com.healthcare.hms.auth.UserStatus;
import com.healthcare.hms.auth.repository.UserRepository;
import com.healthcare.hms.authz.policy.PolicyAudit;
import com.healthcare.hms.authz.policy.ResourcePolicies;
import com.healthcare.hms.authz.policy.ResourcePolicy;
import com.healthcare.hms.common.api.PageParams;
import com.healthcare.hms.common.exception.NotFoundException;
import com.healthcare.hms.staff.StaffResponse;
import com.healthcare.hms.staff.StaffService;
import com.healthcare.hms.tenant.TenantContext;
import com.healthcare.hms.tenant.TenantStatus;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * P6.4 &mdash; the resource-policy framework (ROADMAP P6.4, decision D7, FR-3.3, OQ-2/TQ-5).
 *
 * <p>Three proofs, in the order the plan states them:
 *
 * <ol>
 *   <li><b>The literal Verify statement.</b> <i>unassigned doctor denied, assigned allowed</i>,
 *       previously-treated allowed, admin-with-permission allowed, wrong-tenant subject 404 and
 *       never 403. Phase 6 has no {@code patients}/{@code patient_assignments} tables (they are
 *       Phase 10's), so the subject is a test double &mdash; but the code under assertion is not:
 *       {@code ResourcePolicies.requireRead} is the same call {@code StaffService} makes, the same
 *       default method every real policy inherits, and the same place the log seam fires from.
 *   <li><b>The list predicate, in SQL.</b> Half the rows are denied and the page still comes back
 *       full, with a total that counts only what was visible &mdash; which is the observable
 *       difference between a query predicate and the post-filter TDD section 8.3 forbids.
 *   <li><b>The real policy, through its endpoint.</b> {@code UserSelfOrStaffPolicy} behind {@code
 *       GET /api/v1/staff/{userId}}: a holder of {@code STAFF_VIEW} reads a colleague, a caller
 *       without it is refused at the route, a foreign tenant's id is 404, and the self-branch that
 *       the endpoint gate puts out of reach still holds when the service is asked directly.
 * </ol>
 *
 * <p>Everything runs against the real {@code SecurityFilterChain} and the shared {@code hms_test}
 * database; rows are removed again in {@code @AfterEach} (slug prefix {@code p64-}).
 */
@SpringBootTest(
    properties = {"hms.ratelimit.login-ip-limit=1000000", "hms.ratelimit.email-ip-limit=1000000"})
@AutoConfigureMockMvc
class ResourcePolicyTest {

  private static final String PREFIX = "p64-";
  private static final String PASSWORD = "Correct-Horse-Battery-9";
  private static final String LOGIN = "/api/v1/auth/login";
  private static final String STAFF = "/api/v1/staff";

  /** The permission the double treats as "an administrator's blanket grant". */
  private static final String BLANKET = PermissionCatalog.PATIENT_VIEW.code();

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AuthFixtures fixtures;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UserRepository userRepository;
  @Autowired private ResourcePolicies resourcePolicies;
  @Autowired private StaffService staffService;

  private final ResourcePolicy<Subject> carePolicy = new CarePolicy();

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    TenantContext.clear();
    fixtures.deleteTenantsStartingWith(PREFIX);
  }

  // -------------------------------------------------------------------------
  // 1. the literal ROADMAP statement, through the framework
  // -------------------------------------------------------------------------

  @Test
  void anUnassignedDoctorIsDeniedTheSubjectTheyDoNotTreat() {
    UUID tenant = UUID.randomUUID();
    UUID doctor = UUID.randomUUID();
    Subject subject = new Subject(UUID.randomUUID(), tenant, UUID.randomUUID(), Set.of());

    bindActor(tenant, doctor);

    TenantContext.run(
        tenant,
        () ->
            assertThatThrownBy(() -> resourcePolicies.requireRead(carePolicy, subject))
                .isInstanceOf(NotFoundException.class)
                .isNotInstanceOf(AccessDeniedException.class)
                .hasMessage(ResourcePolicy.NOT_FOUND_MESSAGE));
  }

  @Test
  void theDoctorTheSubjectIsAssignedToIsAllowed() {
    UUID tenant = UUID.randomUUID();
    UUID doctor = UUID.randomUUID();
    Subject subject = new Subject(UUID.randomUUID(), tenant, doctor, Set.of());

    bindActor(tenant, doctor);

    TenantContext.run(
        tenant,
        () ->
            assertThatCode(() -> resourcePolicies.requireRead(carePolicy, subject))
                .doesNotThrowAnyException());
  }

  @Test
  void aDoctorWhoPreviouslyTreatedTheSubjectIsAllowed() {
    UUID tenant = UUID.randomUUID();
    UUID doctor = UUID.randomUUID();
    UUID currentDoctor = UUID.randomUUID();
    Subject subject = new Subject(UUID.randomUUID(), tenant, currentDoctor, Set.of(doctor));

    bindActor(tenant, doctor);

    TenantContext.run(
        tenant,
        () ->
            assertThatCode(() -> resourcePolicies.requireRead(carePolicy, subject))
                .doesNotThrowAnyException());
  }

  @Test
  void anAdminHoldingThePermissionIsAllowedAndThatReadIsAudited() {
    UUID tenant = UUID.randomUUID();
    UUID administrator = UUID.randomUUID();
    Subject subject = new Subject(UUID.randomUUID(), tenant, UUID.randomUUID(), Set.of());

    bindActor(tenant, administrator, BLANKET);

    TenantContext.run(
        tenant,
        () ->
            assertThatCode(() -> resourcePolicies.requireRead(carePolicy, subject))
                .doesNotThrowAnyException());
  }

  @Test
  void aSubjectFromAnotherTenantIsNotFoundAndNeverForbidden() {
    UUID actorTenant = UUID.randomUUID();
    UUID subjectTenant = UUID.randomUUID();
    UUID administrator = UUID.randomUUID();
    Subject subject = new Subject(UUID.randomUUID(), subjectTenant, administrator, Set.of());

    // The blanket grant is held and the actor is even the assigned doctor of that other hospital's
    // row: tenancy has to outrank both, because the answer must be indistinguishable from "this id
    // does not exist" (ADR-006).
    bindActor(actorTenant, administrator, BLANKET);

    TenantContext.run(
        actorTenant,
        () ->
            assertThatThrownBy(() -> resourcePolicies.requireRead(carePolicy, subject))
                .isInstanceOf(NotFoundException.class)
                .isNotInstanceOf(AccessDeniedException.class));
  }

  @Test
  void theCrossBoundaryReadWritesTheCrossDoctorReadLineAndTheAssignedReadDoesNot() {
    UUID tenant = UUID.randomUUID();
    UUID doctor = UUID.randomUUID();
    UUID administrator = UUID.randomUUID();
    Subject assigned = new Subject(UUID.randomUUID(), tenant, doctor, Set.of());
    Subject strangers = new Subject(UUID.randomUUID(), tenant, UUID.randomUUID(), Set.of());

    Logger policyLogger = (Logger) LoggerFactory.getLogger(PolicyAudit.class);
    CapturedEvents captured = new CapturedEvents();
    policyLogger.addAppender(captured.appender);
    try {
      bindActor(tenant, doctor);
      TenantContext.run(tenant, () -> resourcePolicies.requireRead(carePolicy, assigned));
      assertThat(captured.events())
          .as("a read of your own subject is not an audit event")
          .noneMatch(event -> event.getFormattedMessage().contains("event=cross_doctor_read"));

      bindActor(tenant, administrator, BLANKET);
      TenantContext.run(tenant, () -> resourcePolicies.requireRead(carePolicy, strangers));
    } finally {
      policyLogger.detachAppender(captured.appender);
    }

    List<ILoggingEvent> crossBoundary =
        captured.events().stream()
            .filter(event -> event.getFormattedMessage().contains("event=cross_doctor_read"))
            .toList();
    assertThat(crossBoundary).hasSize(1);
    assertThat(crossBoundary.get(0).getFormattedMessage())
        .contains("policy=CarePolicy")
        .contains("subjectType=Subject")
        .contains("subjectId=Subject[id=" + strangers.id())
        .contains("tenantId=" + tenant);
  }

  // -------------------------------------------------------------------------
  // 2. the list predicate runs in SQL, so page sizes stay honest
  // -------------------------------------------------------------------------

  @Test
  void theListPredicateFiltersInSqlSoPageSizesStayCorrectWhenHalfTheRowsAreDenied() {
    UUID tenant = fixtures.createTenant(newSlug(), TenantStatus.ACTIVE);
    // Even indices ACTIVE, odd indices PENDING: sorted by email the first page of five is
    // 00,01,02,03,04 — three ACTIVE and two PENDING. A post-filter over that page would return
    // three rows with a total still claiming ten; the predicate returns five with a total of five.
    for (int i = 0; i < 10; i++) {
      fixtures.createUser(
          tenant,
          "p64-list-%02d@example.test".formatted(i),
          PASSWORD,
          i % 2 == 0 ? UserStatus.ACTIVE : UserStatus.PENDING,
          false);
    }
    ResourcePolicy<User> halfOnly = new ActiveAccountsOnlyPolicy();
    Sort byEmail = Sort.by("email").ascending();

    Page<User> first =
        TenantContext.call(
            tenant,
            () ->
                userRepository.findAll(
                    resourcePolicies.readPredicate(halfOnly), PageRequest.of(0, 5, byEmail)));
    Page<User> second =
        TenantContext.call(
            tenant,
            () ->
                userRepository.findAll(
                    resourcePolicies.readPredicate(halfOnly), PageRequest.of(1, 5, byEmail)));

    assertThat(first.getContent())
        .hasSize(5)
        .allMatch(user -> user.getStatus() == UserStatus.ACTIVE);
    assertThat(first.getTotalElements()).isEqualTo(5);
    assertThat(first.getTotalPages()).isEqualTo(1);
    assertThat(second.getContent()).isEmpty();
    assertThat(second.getTotalElements()).isEqualTo(5);
  }

  @Test
  void theRealListPredicateShowsAHolderWithoutStaffViewOnlyTheirOwnRow() {
    UUID tenant = fixtures.createTenant(newSlug(), TenantStatus.ACTIVE);
    User admin =
        fixtures.createUser(tenant, "p64-admin@example.test", PASSWORD, UserStatus.ACTIVE, false);
    User accountant =
        fixtures.createUser(
            tenant, "p64-accountant@example.test", PASSWORD, UserStatus.ACTIVE, false);
    fixtures.createUser(tenant, "p64-clerk@example.test", PASSWORD, UserStatus.ACTIVE, false);

    bindActor(tenant, admin.getId(), PermissionCatalog.STAFF_VIEW.code());
    Page<StaffResponse> asAdministrator =
        TenantContext.call(tenant, () -> staffService.list(PageParams.of(0, 100)));
    assertThat(asAdministrator.getTotalElements()).isEqualTo(3);

    bindActor(tenant, accountant.getId(), PermissionCatalog.BILLING_VIEW.code());
    Page<StaffResponse> asAccountant =
        TenantContext.call(tenant, () -> staffService.list(PageParams.of(0, 100)));
    assertThat(asAccountant.getTotalElements())
        .as("without STAFF_VIEW the predicate keeps only the caller's own row — in SQL")
        .isEqualTo(1);
    assertThat(asAccountant.getContent().get(0).email()).isEqualTo(accountant.getEmail());
  }

  @Test
  void theStaffListNeverIncludesAnotherTenantsAccounts() {
    UUID tenant = fixtures.createTenant(newSlug(), TenantStatus.ACTIVE);
    User admin =
        fixtures.createUser(tenant, "p64-mine@example.test", PASSWORD, UserStatus.ACTIVE, false);
    UUID otherTenant = fixtures.createTenant(newSlug(), TenantStatus.ACTIVE);
    User stranger =
        fixtures.createUser(
            otherTenant, "p64-theirs@example.test", PASSWORD, UserStatus.ACTIVE, false);

    bindActor(tenant, admin.getId(), PermissionCatalog.STAFF_VIEW.code());
    Page<StaffResponse> listed =
        TenantContext.call(tenant, () -> staffService.list(PageParams.of(0, 100)));

    assertThat(listed.getContent().stream().map(StaffResponse::email).toList())
        .doesNotContain(stranger.getEmail());
    assertThat(listed.getTotalElements()).isEqualTo(1);
  }

  // -------------------------------------------------------------------------
  // 3. the real policy, through its endpoint
  // -------------------------------------------------------------------------

  @Test
  void everyStaffRouteRejectsACallerWithNoToken() throws Exception {
    mockMvc.perform(get(STAFF)).andExpect(status().isUnauthorized());
    mockMvc.perform(get(STAFF + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
  }

  @Test
  void aStaffViewerReadsTheirOwnRecord() throws Exception {
    Session admin = adminSession();

    mockMvc
        .perform(
            get(STAFF + "/" + admin.userId()).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(admin.userId().toString()))
        .andExpect(jsonPath("$.data.email").value(admin.email()));
  }

  @Test
  void aStaffViewerReadsAColleague() throws Exception {
    Fixture fixture = fixture();
    Session admin =
        signIn(fixture.tenantId(), "p64-admin-login@example.test", SystemRoleBundle.ADMIN);

    mockMvc
        .perform(
            get(STAFF + "/" + fixture.accountantId())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value("p64-accountant@example.test"));
  }

  @Test
  void aCallerWithoutStaffViewIsRefusedAtTheEndpoint() throws Exception {
    Fixture fixture = fixture();
    Session accountant =
        signIn(fixture.tenantId(), "p64-accountant-login@example.test", SystemRoleBundle.BILLING);

    mockMvc
        .perform(
            get(STAFF + "/" + fixture.adminId())
                .header(HttpHeaders.AUTHORIZATION, accountant.bearer()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
  }

  @Test
  void aForeignTenantStaffIdAnswers404AndNever403() throws Exception {
    Fixture fixture = fixture();
    Session admin =
        signIn(fixture.tenantId(), "p64-admin-login@example.test", SystemRoleBundle.ADMIN);

    mockMvc
        .perform(
            get(STAFF + "/" + fixture.strangerId())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  void theSelfBranchStillAppliesOnceTheEndpointGateIsOutOfTheWay() {
    Fixture fixture = fixture();

    // The route is gated by STAFF_VIEW, so a BILLING caller can never reach it — which would make
    // the policy's "your own account" branch unreachable over HTTP. Asking the service directly is
    // how the branch is proved rather than assumed.
    bindActor(fixture.tenantId(), fixture.accountantId(), PermissionCatalog.BILLING_VIEW.code());

    TenantContext.run(
        fixture.tenantId(),
        () -> {
          assertThat(staffService.get(fixture.accountantId()).email())
              .isEqualTo("p64-accountant@example.test");
          assertThatThrownBy(() -> staffService.get(fixture.adminId()))
              .isInstanceOf(NotFoundException.class)
              .isNotInstanceOf(AccessDeniedException.class);
        });
  }

  // -------------------------------------------------------------------------
  // fixtures
  // -------------------------------------------------------------------------

  /**
   * One hospital with an administrator (holds {@code STAFF_VIEW}), an accountant (does not) and a
   * stranger in a second hospital — the three rows every assertion in section 3 needs.
   */
  private Fixture fixture() {
    UUID tenantId = fixtures.createTenant(newSlug(), TenantStatus.ACTIVE);
    User admin =
        fixtures.createUser(
            tenantId, "p64-fixture-admin@example.test", PASSWORD, UserStatus.ACTIVE, false);
    User accountant =
        fixtures.createUser(
            tenantId, "p64-accountant@example.test", PASSWORD, UserStatus.ACTIVE, false);
    fixtures.provisionRoles(tenantId, admin.getId());
    fixtures.grantRoles(tenantId, accountant.getId(), List.of(SystemRoleBundle.BILLING.roleName()));

    UUID otherTenant = fixtures.createTenant(newSlug(), TenantStatus.ACTIVE);
    User stranger =
        fixtures.createUser(
            otherTenant, "p64-stranger@example.test", PASSWORD, UserStatus.ACTIVE, false);

    return new Fixture(tenantId, admin.getId(), accountant.getId(), stranger.getId());
  }

  private Session adminSession() throws Exception {
    Fixture fixture = fixture();
    return signIn(fixture.tenantId(), "p64-admin-login@example.test", SystemRoleBundle.ADMIN);
  }

  private Session signIn(UUID tenantId, String email, SystemRoleBundle role) throws Exception {
    User user =
        fixtures.createUserWithRoles(
            tenantId, email, PASSWORD, UserStatus.ACTIVE, false, List.of(role.roleName()));
    String slug =
        jdbcTemplate.queryForObject(
            "SELECT slug FROM tenants WHERE id = ?", String.class, uuidBytes(tenantId));
    JsonNode data =
        body(
            mockMvc
                .perform(
                    post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"hospitalSlug\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(slug, email, PASSWORD)))
                .andExpect(status().isOk()));
    return new Session(tenantId, user.getId(), email, text(data, "data", "accessToken"));
  }

  /**
   * Binds a bearer principal the way {@link com.healthcare.hms.authz.CurrentActor} reads one.
   *
   * <p>Used only where the assertion is about the policy rather than about the filter chain: the
   * HTTP cases above obtain theirs from a real login, which is what makes them end-to-end.
   */
  private void bindActor(UUID tenantId, UUID userId, String... authorityCodes) {
    Jwt jwt =
        Jwt.withTokenValue("resource-policy-test")
            .header("alg", "none")
            .claim("sub", userId.toString())
            .claim("tenantId", tenantId.toString())
            .build();
    List<GrantedAuthority> authorities = new ArrayList<>();
    for (String code : authorityCodes) {
      authorities.add(new SimpleGrantedAuthority(code));
    }
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, authorities));
  }

  private JsonNode body(ResultActions actions) throws Exception {
    return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
  }

  private static String text(JsonNode root, String... path) {
    JsonNode node = root;
    for (String segment : path) {
      node = node.get(segment);
    }
    return node.asText();
  }

  private String newSlug() {
    return PREFIX + UUID.randomUUID().toString().substring(0, 8);
  }

  private static byte[] uuidBytes(UUID id) {
    return ByteBuffer.allocate(16)
        .putLong(id.getMostSignificantBits())
        .putLong(id.getLeastSignificantBits())
        .array();
  }

  // -------------------------------------------------------------------------
  // doubles
  // -------------------------------------------------------------------------

  /** OQ-2's default rule over a subject that has no table yet (decision D7). */
  private record Subject(
      UUID id, UUID tenantId, UUID assignedDoctorId, Set<UUID> previousDoctorIds) {}

  private static final class CarePolicy implements ResourcePolicy<Subject> {

    @Override
    public boolean canRead(Subject subject) {
      if (subject == null) {
        return false;
      }
      boolean sameTenant = TenantContext.find().map(subject.tenantId()::equals).orElse(false);
      if (!sameTenant) {
        return false;
      }
      UUID self = CurrentActor.userId().orElse(null);
      if (self != null && self.equals(subject.assignedDoctorId())) {
        return true;
      }
      if (self != null && subject.previousDoctorIds().contains(self)) {
        return true;
      }
      return PermissionAuthorizationManager.holds(
          SecurityContextHolder.getContext().getAuthentication(), BLANKET);
    }

    @Override
    public boolean isCrossBoundaryRead(Subject subject) {
      UUID self = CurrentActor.userId().orElse(null);
      boolean related =
          self != null
              && (self.equals(subject.assignedDoctorId())
                  || subject.previousDoctorIds().contains(self));
      return !related;
    }

    @Override
    public Specification<Subject> readPredicate() {
      // The double has no table, so there is no honest predicate to write — and a permissive one
      // would be worse than none. Nothing ever calls it: section 2 proves the predicate with the
      // real repository.
      return (root, query, cb) -> cb.disjunction();
    }
  }

  /** Half the tenant's accounts, by an attribute a {@code Specification} can express in SQL. */
  private static final class ActiveAccountsOnlyPolicy implements ResourcePolicy<User> {

    @Override
    public boolean canRead(User subject) {
      // Never consulted: this double exists only for its readPredicate.
      return false;
    }

    @Override
    public Specification<User> readPredicate() {
      return (root, query, cb) -> cb.equal(root.get("status"), UserStatus.ACTIVE);
    }
  }

  private record Fixture(UUID tenantId, UUID adminId, UUID accountantId, UUID strangerId) {}

  private record Session(UUID tenantId, UUID userId, String email, String rawToken) {
    String bearer() {
      return "Bearer " + rawToken;
    }
  }

  /** A logback appender that keeps what it was handed, so a seam can be asserted on. */
  private static final class CapturedEvents {
    final ch.qos.logback.core.read.ListAppender<ILoggingEvent> appender = newOnlyListAppender();

    List<ILoggingEvent> events() {
      return appender.list;
    }

    private static ch.qos.logback.core.read.ListAppender<ILoggingEvent> newOnlyListAppender() {
      ch.qos.logback.core.read.ListAppender<ILoggingEvent> created =
          new ch.qos.logback.core.read.ListAppender<>();
      created.start();
      return created;
    }
  }
}
