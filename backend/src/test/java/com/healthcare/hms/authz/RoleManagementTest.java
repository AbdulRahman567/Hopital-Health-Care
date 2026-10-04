package com.healthcare.hms.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.hms.auth.AuthFixtures;
import com.healthcare.hms.auth.UserStatus;
import com.healthcare.hms.tenant.TenantContext;
import com.healthcare.hms.tenant.TenantStatus;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * P6.2 — the first authorized surface of the application (plan section 6, FR-3.5, decisions D4 and
 * D5).
 *
 * <p>Everything runs through the real {@code SecurityFilterChain}, the real {@link RoleService} and
 * the real {@link SystemRoleProvisioner} on the shared {@code hms_test} database — no substitutions
 * anywhere. Rows are removed again in {@code @AfterEach} (slug prefix {@code p62-}) because the
 * database is JVM-scoped and shared with every other suite (TESTING section 4).
 *
 * <p>Permission-level enforcement ({@code @RequirePermission}) is deliberately <b>not</b> asserted
 * here: it lands at P6.3 with the authorities stage. What this class proves is everything that had
 * to be true first — that the routes exist behind an authenticated caller, that registration (D4)
 * makes somebody able to reach them, and that D5's five editing rules hold inside the service,
 * where they are enforced for <i>every</i> principal rather than only for a checked one.
 *
 * <p>The two rate-limit budgets below are raised for this context only: MockMvc shares one remote
 * address with every other suite and Redis is shared with them too, so a 20-per-minute IP budget
 * would make this class fail depending on which suite ran first rather than on anything it tests.
 */
@SpringBootTest(
    properties = {"hms.ratelimit.login-ip-limit=1000000", "hms.ratelimit.email-ip-limit=1000000"})
@AutoConfigureMockMvc
class RoleManagementTest {

  private static final String PREFIX = "p62-";
  private static final String PASSWORD = "Correct-Horse-Battery-9";
  private static final String LOGIN = "/api/v1/auth/login";
  private static final String REGISTER = "/api/v1/auth/register-hospital";
  private static final String ROLES = "/api/v1/roles";
  private static final String PERMISSIONS = "/api/v1/permissions";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AuthFixtures fixtures;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    TenantContext.clear();
    fixtures.deleteTenantsStartingWith(PREFIX);
  }

  // -------------------------------------------------------------------------
  // 1. closed before anything else is true
  // -------------------------------------------------------------------------

  @Test
  void everyRoleAndPermissionRouteRejectsACallerWithNoToken() throws Exception {
    String unknownId = UUID.randomUUID().toString();

    mockMvc.perform(get(ROLES)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post(ROLES)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"X\",\"permissionCodes\":[]}"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get(ROLES + "/" + unknownId)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            put(ROLES + "/" + unknownId)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"X\",\"permissionCodes\":[]}"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(delete(ROLES + "/" + unknownId)).andExpect(status().isUnauthorized());
    mockMvc.perform(get(PERMISSIONS)).andExpect(status().isUnauthorized());
  }

  // -------------------------------------------------------------------------
  // 2. decision D4 — registration provisions, end to end
  // -------------------------------------------------------------------------

  @Test
  void registrationProvisionsSixSystemBundlesAndEnrollsTheRegisteringAdministrator()
      throws Exception {
    String slug = newSlug();
    String hospitalName = "P62 " + slug;
    register(hospitalName, slug + "@example.test");

    UUID tenantId = tenantIdOf(hospitalName);
    assertThat(count("SELECT COUNT(*) FROM roles WHERE tenant_id = ?", tenantId))
        .as("ADMIN + DOCTOR + NURSE + RECEPTIONIST + LAB_TECHNICIAN + BILLING")
        .isEqualTo(SystemRoleBundle.values().length);
    assertThat(
            count("SELECT COUNT(*) FROM roles WHERE tenant_id = ? AND system_flag = 1", tenantId))
        .isEqualTo(SystemRoleBundle.values().length);
    assertThat(count("SELECT COUNT(*) FROM role_permissions WHERE tenant_id = ?", tenantId))
        .as("every bundle's codes, written once each")
        .isEqualTo(
            Arrays.stream(SystemRoleBundle.values())
                .mapToInt(bundle -> bundle.permissionCodes().size())
                .sum());

    assertThat(count("SELECT COUNT(*) FROM user_roles WHERE tenant_id = ?", tenantId))
        .as("the registering administrator is enrolled in ADMIN and nothing else")
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT COUNT(*) FROM user_roles ur"
                    + " JOIN roles r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id"
                    + " WHERE ur.tenant_id = ? AND r.name = 'ADMIN'",
                tenantId))
        .isEqualTo(1);
  }

  @Test
  void provisioningIsIdempotent() throws Exception {
    UUID tenantId = fixtures.createTenant(newSlug(), TenantStatus.ACTIVE);
    UUID adminId =
        fixtures
            .createUser(tenantId, "p62-idem@example.test", PASSWORD, UserStatus.ACTIVE, false)
            .getId();

    fixtures.provisionRoles(tenantId, adminId);
    fixtures.provisionRoles(tenantId, adminId);
    fixtures.provisionBundles(tenantId);

    assertThat(count("SELECT COUNT(*) FROM roles WHERE tenant_id = ?", tenantId))
        .isEqualTo(SystemRoleBundle.values().length);
    assertThat(count("SELECT COUNT(*) FROM user_roles WHERE tenant_id = ?", tenantId))
        .as("the administrator is enrolled once, however often provisioning runs")
        .isEqualTo(1);
  }

  // -------------------------------------------------------------------------
  // 3. CRUD
  // -------------------------------------------------------------------------

  @Test
  void anAdministratorCanCreateReadUpdateAndDeleteACustomRole() throws Exception {
    Tenant admin = signedInAdministrator();

    JsonNode created =
        body(
            mockMvc
                .perform(
                    post(ROLES)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(
                            "{\"name\":\"Clinic Reader\",\"permissionCodes\":["
                                + code(PermissionCatalog.PATIENT_VIEW)
                                + ","
                                + code(PermissionCatalog.VISIT_VIEW)
                                + "]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith(ROLES + "/")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Clinic Reader"))
                .andExpect(jsonPath("$.data.system").value(false)));
    String roleId = text(created, "data", "id");
    assertThat(stringArray(created, "data", "permissionCodes"))
        .as("codes come back sorted, so a client can compare them without re-sorting")
        .containsExactly("PATIENT_VIEW", "VISIT_VIEW");

    assertThat(
            count(
                "SELECT COUNT(*) FROM role_permissions WHERE tenant_id = ? AND role_id = ?",
                admin.tenantId,
                uuid(roleId)))
        .as("the codes a create returns are the rows role_permissions holds")
        .isEqualTo(2);

    JsonNode fetched = body(getAs(ROLES + "/" + roleId, admin).andExpect(status().isOk()));
    assertThat(text(fetched, "data", "id")).isEqualTo(roleId);

    JsonNode listed = body(getAs(ROLES + "?page=0&size=100", admin).andExpect(status().isOk()));
    assertThat(names(listed)).contains("Clinic Reader", SystemRoleBundle.ADMIN.roleName());

    JsonNode updated =
        body(
            mockMvc
                .perform(
                    put(ROLES + "/" + roleId)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(
                            "{\"name\":\"Clinic Reader v2\",\"permissionCodes\":["
                                + code(PermissionCatalog.PATIENT_VIEW)
                                + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Clinic Reader v2")));
    assertThat(stringArray(updated, "data", "permissionCodes")).containsExactly("PATIENT_VIEW");

    mockMvc
        .perform(delete(ROLES + "/" + roleId).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get(ROLES + "/" + roleId).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void aDuplicateNameIsA409NamingTheNameField() throws Exception {
    Tenant admin = signedInAdministrator();
    createRole(admin, "Duplicator", code(PermissionCatalog.PATIENT_VIEW));

    mockMvc
        .perform(
            post(ROLES)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"name\":\"Duplicator\",\"permissionCodes\":["
                        + code(PermissionCatalog.VISIT_VIEW)
                        + "]}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"))
        .andExpect(jsonPath("$.error.fields[0].field").value("name"));
  }

  // -------------------------------------------------------------------------
  // 4. decision D5 — the editing rules
  // -------------------------------------------------------------------------

  @Test
  void anUnknownPermissionCodeIs422AndWritesNothingAtAll() throws Exception {
    Tenant admin = signedInAdministrator();

    mockMvc
        .perform(
            post(ROLES)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"name\":\"Half Right\",\"permissionCodes\":["
                        + code(PermissionCatalog.PATIENT_VIEW)
                        + ",\"PATIENT_TELEPORT\"]}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.fields[0].field").value("permissionCodes"));

    assertThat(
            count(
                "SELECT COUNT(*) FROM roles WHERE tenant_id = ? AND name = 'Half Right'",
                admin.tenantId))
        .as("a rejected grant leaves no role behind, so no half-written row can be granted later")
        .isZero();
  }

  @Test
  void aPlatformOnlyPermissionIsRefusedOnATenantRole() throws Exception {
    Tenant admin = signedInAdministrator();

    mockMvc
        .perform(
            post(ROLES)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"name\":\"Self Suspension\",\"permissionCodes\":["
                        + code(PermissionCatalog.TENANT_SUSPEND)
                        + "]}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.fields[0].field").value("permissionCodes"))
        .andExpect(
            jsonPath("$.error.fields[0].message").value(RoleService.PLATFORM_ONLY_PERMISSION));

    assertThat(
            count(
                "SELECT COUNT(*) FROM roles WHERE tenant_id = ? AND name = 'Self Suspension'",
                admin.tenantId))
        .isZero();
  }

  @Test
  void aCallerWithoutTheDeclaredPermissionIsDeniedAtTheEndpoint() throws Exception {
    // P6.3: the annotation is enforced before the service runs, so the anti-escalation rules
    // below are never even reachable without ROLE_CREATE.
    Tenant admin = signedInAdministrator();
    createRole(
        admin, "Viewer", code(PermissionCatalog.PATIENT_VIEW), code(PermissionCatalog.ROLE_VIEW));
    Tenant viewer =
        signIn(
            admin.tenantId, "p63-viewer-" + UUID.randomUUID() + "@example.test", List.of("Viewer"));

    mockMvc
        .perform(
            post(ROLES)
                .header(HttpHeaders.AUTHORIZATION, viewer.bearer())
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"name\":\"Never Created\",\"permissionCodes\":["
                        + code(PermissionCatalog.PATIENT_VIEW)
                        + "]}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));

    assertThat(
            count(
                "SELECT COUNT(*) FROM roles WHERE tenant_id = ? AND name = 'Never Created'",
                admin.tenantId))
        .isZero();
  }

  @Test
  void aCreatorMayNotGrantAPermissionTheyDoNotHold() throws Exception {
    Tenant admin = signedInAdministrator();
    // A deliberately narrow bundle — enough to reach the endpoint (ROLE_CREATE), and no more:
    // PATIENT_VIEW only, so this caller's authority set is provably smaller than the catalog it is
    // about to be asked to grant from.
    createRole(
        admin, "Reader", code(PermissionCatalog.PATIENT_VIEW), code(PermissionCatalog.ROLE_CREATE));
    Tenant reader =
        signIn(
            admin.tenantId, "p62-reader-" + UUID.randomUUID() + "@example.test", List.of("Reader"));

    mockMvc
        .perform(
            post(ROLES)
                .header(HttpHeaders.AUTHORIZATION, reader.bearer())
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"name\":\"Escalation Ladder\",\"permissionCodes\":["
                        + code(PermissionCatalog.STAFF_UPDATE)
                        + "]}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.fields[0].message").value(RoleService.BEYOND_GRANTER));

    assertThat(
            count(
                "SELECT COUNT(*) FROM roles WHERE tenant_id = ? AND name = 'Escalation Ladder'",
                admin.tenantId))
        .isZero();
  }

  @Test
  void systemRolesCannotBeRenamedOrDeletedAndTheirPermissionSetIsFixed() throws Exception {
    Tenant admin = signedInAdministrator();
    UUID adminRoleId = roleId(admin.tenantId, "ADMIN");
    int before =
        count(
            "SELECT COUNT(*) FROM role_permissions WHERE tenant_id = ? AND role_id = ?",
            admin.tenantId,
            adminRoleId);

    mockMvc
        .perform(
            put(ROLES + "/" + adminRoleId)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"name\":\"ADMIN RENAMED\",\"permissionCodes\":["
                        + code(PermissionCatalog.AUDIT_VIEW)
                        + "]}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("RECORD_FINALIZED"));

    mockMvc
        .perform(
            delete(ROLES + "/" + adminRoleId).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("RECORD_FINALIZED"));

    assertThat(
            count(
                "SELECT COUNT(*) FROM roles WHERE tenant_id = ? AND name = 'ADMIN RENAMED'",
                admin.tenantId))
        .isZero();
    assertThat(
            count(
                "SELECT COUNT(*) FROM role_permissions WHERE tenant_id = ? AND role_id = ?",
                admin.tenantId,
                adminRoleId))
        .as("a rejected rename never reached the permission set")
        .isEqualTo(before);
  }

  @Test
  void aRoleStillAssignedToSomebodyCannotBeDeleted() throws Exception {
    Tenant admin = signedInAdministrator();
    String heldRoleId = createRole(admin, "Held By Somebody", code(PermissionCatalog.PATIENT_VIEW));
    signIn(
        admin.tenantId,
        "p62-held-" + UUID.randomUUID() + "@example.test",
        List.of("Held By Somebody"));
    String freeRoleId = createRole(admin, "Held By Nobody", code(PermissionCatalog.VISIT_VIEW));

    mockMvc
        .perform(delete(ROLES + "/" + heldRoleId).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("RESOURCE_IN_USE"))
        .andExpect(jsonPath("$.error.message").value(RoleService.ROLE_IN_USE));

    mockMvc
        .perform(delete(ROLES + "/" + freeRoleId).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
        .andExpect(status().isNoContent());
  }

  // -------------------------------------------------------------------------
  // 5. tenant scope — the ADR-006 property P6.7 re-proves on other endpoints
  // -------------------------------------------------------------------------

  @Test
  void aRoleBelongsToExactlyOneTenantAndAForeignIdIsA404() throws Exception {
    Tenant first = signedInAdministrator();
    String firstRoleId = createRole(first, "Shared Name", code(PermissionCatalog.PATIENT_VIEW));

    Tenant second = signedInAdministrator();
    String secondRoleId = createRole(second, "Shared Name", code(PermissionCatalog.PATIENT_VIEW));

    assertThat(firstRoleId).isNotEqualTo(secondRoleId);
    assertThat(first.tenantId).isNotEqualTo(second.tenantId);

    mockMvc
        .perform(get(ROLES + "/" + firstRoleId).header(HttpHeaders.AUTHORIZATION, second.bearer()))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            delete(ROLES + "/" + firstRoleId).header(HttpHeaders.AUTHORIZATION, second.bearer()))
        .andExpect(status().isNotFound());

    JsonNode listed = body(getAs(ROLES + "?page=0&size=100", second).andExpect(status().isOk()));
    assertThat(ids(listed))
        .as("the same name may exist in both tenants; the id may not cross between them")
        .contains(secondRoleId)
        .doesNotContain(firstRoleId);
  }

  // -------------------------------------------------------------------------
  // 6. the permission catalog, and the "never 500" rule
  // -------------------------------------------------------------------------

  @Test
  void thePermissionCatalogIsServedWholeAndInDeclarationOrder() throws Exception {
    Tenant admin = signedInAdministrator();

    JsonNode page =
        body(
            getAs(PERMISSIONS + "?page=0&size=100", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(PermissionCatalog.SIZE)));

    assertThat(page.get("data").size()).isEqualTo(PermissionCatalog.SIZE);
    assertThat(field(page.get("data"), "code"))
        .containsExactlyElementsOf(
            Arrays.stream(PermissionCatalog.values()).map(PermissionCatalog::code).toList());

    assertThat(platformOnly(page, PermissionCatalog.TENANT_SUSPEND.code())).isTrue();
    assertThat(platformOnly(page, PermissionCatalog.TENANT_ACTIVATE.code())).isTrue();
    assertThat(platformOnly(page, PermissionCatalog.PATIENT_VIEW.code())).isFalse();
  }

  @Test
  void malformedAndUnknownInputIs4xxAndNeverA500() throws Exception {
    Tenant admin = signedInAdministrator();

    mockMvc
        .perform(
            post(ROLES)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

    mockMvc
        .perform(
            post(ROLES)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Smuggled\",\"permissionCodes\":[],\"systemFlag\":true}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.fields[0].field").value("systemFlag"))
        .andExpect(jsonPath("$.error.fields[0].message").value("Unknown property."));

    mockMvc
        .perform(get(ROLES + "/not-a-uuid").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

    mockMvc
        .perform(
            get(ROLES + "/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
        .andExpect(status().isNotFound());
  }

  // -------------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------------

  private record Tenant(UUID tenantId, String slug, String bearerToken) {
    String bearer() {
      return "Bearer " + bearerToken;
    }
  }

  /** A tenant and an administrator enrolled in ADMIN, signed in and ready to call the API. */
  private Tenant signedInAdministrator() throws Exception {
    UUID tenantId = fixtures.createTenant(newSlug(), TenantStatus.ACTIVE);
    String email = "p62-admin-" + UUID.randomUUID() + "@example.test";
    return signIn(tenantId, email, List.of(SystemRoleBundle.ADMIN.roleName()));
  }

  private Tenant signIn(UUID tenantId, String email, List<String> roles) throws Exception {
    fixtures.createUserWithRoles(tenantId, email, PASSWORD, UserStatus.ACTIVE, false, roles);
    String slug =
        jdbcTemplate.queryForObject(
            "SELECT slug FROM tenants WHERE id = ?", String.class, uuidBytes(tenantId));
    JsonNode data =
        body(
            mockMvc
                .perform(
                    post(LOGIN)
                        .contentType(APPLICATION_JSON)
                        .content(
                            "{\"hospitalSlug\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(slug, email, PASSWORD)))
                .andExpect(status().isOk()));
    return new Tenant(tenantId, slug, text(data, "data", "accessToken"));
  }

  private String createRole(Tenant as, String name, String... codes) throws Exception {
    JsonNode created =
        body(
            mockMvc
                .perform(
                    post(ROLES)
                        .header(HttpHeaders.AUTHORIZATION, as.bearer())
                        .contentType(APPLICATION_JSON)
                        .content(
                            "{\"name\":\""
                                + name
                                + "\",\"permissionCodes\":["
                                + String.join(",", codes)
                                + "]}"))
                .andExpect(status().isCreated()));
    return text(created, "data", "id");
  }

  private void register(String hospitalName, String email) throws Exception {
    String body =
        ("{\"hospitalName\":\"%s\",\"email\":\"%s\",\"password\":\"%s\","
                + "\"firstName\":\"Ada\",\"lastName\":\"Lovelace\"}")
            .formatted(hospitalName, email, PASSWORD);
    mockMvc
        .perform(post(REGISTER).contentType(APPLICATION_JSON).content(body))
        .andExpect(status().isAccepted());
  }

  private ResultActions getAs(String path, Tenant as) throws Exception {
    return mockMvc
        .perform(get(path).header(HttpHeaders.AUTHORIZATION, as.bearer()))
        .andExpect(status().is2xxSuccessful());
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

  private static List<String> stringArray(JsonNode root, String... path) {
    JsonNode node = root;
    for (String segment : path) {
      node = node.get(segment);
    }
    List<String> values = new ArrayList<>();
    for (JsonNode element : node) {
      values.add(element.asText());
    }
    return values;
  }

  private static List<String> field(JsonNode array, String name) {
    List<String> values = new ArrayList<>();
    for (JsonNode element : array) {
      values.add(element.get(name).asText());
    }
    return values;
  }

  private static List<String> names(JsonNode page) {
    return field(page.get("data"), "name");
  }

  private static List<String> ids(JsonNode page) {
    return field(page.get("data"), "id");
  }

  private static boolean platformOnly(JsonNode page, String code) {
    return StreamSupport.stream(page.get("data").spliterator(), false)
        .filter(node -> code.equals(node.get("code").asText()))
        .findFirst()
        .map(node -> node.get("platformOnly").asBoolean())
        .orElseThrow(() -> new AssertionError("catalog row missing: " + code));
  }

  /**
   * Reads a {@code BINARY(16)} primary key.
   *
   * <p>Every id column in this schema is {@code BINARY(16)}, so the parameter has to reach JDBC as
   * a {@code byte[]} — the same {@code bytesOf} discipline {@code TenantIsolationIT} uses. Handing
   * {@code JdbcTemplate} a {@code java.util.UUID} makes the driver bind it as a string, the
   * comparison silently matches nothing, and a query that "found no rows" is indistinguishable from
   * a query that ran against the wrong value.
   */
  private UUID roleId(UUID tenantId, String name) {
    return uuidOf(
        jdbcTemplate.queryForObject(
            "SELECT id FROM roles WHERE tenant_id = ? AND name = ?",
            byte[].class,
            uuidBytes(tenantId),
            name));
  }

  private UUID tenantIdOf(String hospitalName) {
    return uuidOf(
        jdbcTemplate.queryForObject(
            "SELECT id FROM tenants WHERE name = ?", byte[].class, hospitalName));
  }

  private int count(String sql, Object... args) {
    Object[] bound = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      bound[i] = args[i] instanceof UUID id ? uuidBytes(id) : args[i];
    }
    Integer value = jdbcTemplate.queryForObject(sql, Integer.class, bound);
    return value == null ? 0 : value;
  }

  private static String code(PermissionCatalog permission) {
    return "\"" + permission.code() + "\"";
  }

  private String newSlug() {
    return PREFIX + UUID.randomUUID().toString().substring(0, 8);
  }

  private static UUID uuid(String id) {
    return UUID.fromString(id);
  }

  private static byte[] uuidBytes(UUID id) {
    return ByteBuffer.allocate(16)
        .putLong(id.getMostSignificantBits())
        .putLong(id.getLeastSignificantBits())
        .array();
  }

  private static UUID uuidOf(byte[] bytes) {
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    return new UUID(buffer.getLong(), buffer.getLong());
  }
}
