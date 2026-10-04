package com.healthcare.hms.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.healthcare.hms.auth.AuthFixtures;
import com.healthcare.hms.auth.User;
import com.healthcare.hms.auth.UserStatus;
import com.healthcare.hms.tenant.TenantContext;
import com.healthcare.hms.tenant.TenantContextFilter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * P6.7 &mdash; the Phase 6 surface under a caller from the wrong tenant (plan section 8, decision
 * D11, ADR-006).
 *
 * <p>Phase 6 is the first phase with rows another tenant can <i>name</i>: a role id and a staff id
 * are both {@code BINARY(16)} values a client can put in a path, in a body or in a query string.
 * The property being re-proved here is the one {@code RoleManagementTest} proved for two freshly
 * created tenants, now on the fixture tenants every isolation suite shares, and against all three
 * legs a hint can travel on:
 *
 * <ol>
 *   <li><b>id leg</b> &mdash; a foreign {@code roleId} is <b>404</b>, never 403 and never 401, and
 *       is byte-for-byte the answer a caller would get for an id that does not exist at all, so the
 *       response cannot be used to prove the other hospital exists;
 *   <li><b>list leg</b> &mdash; listings and their page totals are the tenant's own count, so a
 *       total can never be made to grow by pointing at the other hospital;
 *   <li><b>hint legs</b> &mdash; {@code X-Tenant-ID} answers 404 <i>before</i> the controller runs
 *       (the write below it leaves no row behind), a body or query hint is refused outright or
 *       ignored, and the very same request with no token at all answers 401 &mdash; which is what
 *       separates "the tenant conflicts" from "you are not signed in".
 * </ol>
 *
 * <p>Fixtures are TESTING section 4's {@code hospital-a} / {@code hospital-b} (fixed ids, {@code
 * ACTIVE}), the same two rows {@code AuthTenantIsolationTest} and {@code TenantIsolationIT} use.
 * Both accounts are enrolled in ADMIN, so the caller always clears {@code @RequirePermission} and
 * every refusal below is the tenancy layer's and not the authority layer's. Rows are removed again
 * in {@code @AfterEach} in the order the V2 foreign keys require, because V4 declares no {@code ON
 * DELETE CASCADE} and the other two suites read row counts in exactly these tenants.
 *
 * <p>Rate-limit budgets match {@code RoleManagementTest}'s property pair on purpose: identical
 * properties share one cached application context, so this suite boots no context of its own.
 */
@SpringBootTest(
    properties = {"hms.ratelimit.login-ip-limit=1000000", "hms.ratelimit.email-ip-limit=1000000"})
@AutoConfigureMockMvc
class WrongTenantTest {

  /** TESTING.md section 4 fixtures &mdash; fixed ids so every suite and the docs agree. */
  private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");

  private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
  private static final String SLUG_A = "hospital-a";
  private static final String SLUG_B = "hospital-b";

  private static final String LOGIN = "/api/v1/auth/login";
  private static final String ROLES = "/api/v1/roles";
  private static final String STAFF = "/api/v1/staff";
  private static final String HINT = TenantContextFilter.TENANT_HINT_HEADER;
  private static final String PASSWORD = "Correct-Horse-Battery-9";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AuthFixtures fixtures;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Caller adminA;
  private Caller adminB;
  private User userA;

  @BeforeEach
  void createFixtures() throws Exception {
    ensureTenant(TENANT_A, "Hospital A", SLUG_A);
    ensureTenant(TENANT_B, "Hospital B", SLUG_B);
    clearFixtureRows();
    adminA = signInAdministrator(TENANT_A, SLUG_A, "p67-a-" + UUID.randomUUID());
    adminB = signInAdministrator(TENANT_B, SLUG_B, "p67-b-" + UUID.randomUUID());
    userA = adminA.user;
  }

  @AfterEach
  void cleanUp() {
    assertThat(TenantContext.find())
        .as("no ThreadLocal leak after any request (plan P6.7)")
        .isEmpty();
    TenantContext.clear();
    clearFixtureRows();
  }

  // -------------------------------------------------------------------------
  // 1. the id leg: 404, and 404 that cannot be told apart from "missing"
  // -------------------------------------------------------------------------

  @Test
  void aForeignRoleIdIs404OnReadUpdateAndDeleteAndTheRowIsNeverTouched() throws Exception {
    String roleA = createRole(adminA, "Tenant A Reader", PermissionCatalog.PATIENT_VIEW);

    JsonNode foreign =
        body(
            mockMvc
                .perform(
                    get(ROLES + "/" + roleA).header(HttpHeaders.AUTHORIZATION, adminB.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist()));

    mockMvc
        .perform(
            put(ROLES + "/" + roleA)
                .header(HttpHeaders.AUTHORIZATION, adminB.bearer())
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"name\":\"Hijacked By B\",\"permissionCodes\":["
                        + code(PermissionCatalog.VISIT_VIEW)
                        + "]}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

    mockMvc
        .perform(delete(ROLES + "/" + roleA).header(HttpHeaders.AUTHORIZATION, adminB.bearer()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

    JsonNode survived = body(getAs(ROLES + "/" + roleA, adminA));
    assertThat(text(survived, "data", "name"))
        .as("the foreign update and delete were refused before any row was written")
        .isEqualTo("Tenant A Reader");
    assertThat(stringArray(survived, "data", "permissionCodes"))
        .containsExactly(PermissionCatalog.PATIENT_VIEW.code());

    // ADR-006: a foreign id must be indistinguishable from one that never existed, otherwise the
    // 404 tells tenant B that tenant A owns this id.
    JsonNode missing =
        body(
            mockMvc
                .perform(
                    get(ROLES + "/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, adminB.bearer()))
                .andExpect(status().isNotFound()));
    assertThat(redacted(foreign))
        .as("same bytes as an id nobody owns, minus the per-request envelope fields")
        .isEqualTo(redacted(missing));
  }

  // -------------------------------------------------------------------------
  // 2. the list leg: rows and page totals are the tenant's own
  // -------------------------------------------------------------------------

  @Test
  void listingsAndTheirPageTotalsAreAlwaysTheCallersOwnTenants() throws Exception {
    // Deliberately unbalanced: A ends up with two custom roles and B with one, so an equal total
    // would already be evidence of a leak rather than of coincidence.
    String roleA1 = createRole(adminA, "Tenant A Reader", PermissionCatalog.PATIENT_VIEW);
    String roleA2 = createRole(adminA, "Tenant A Writer", PermissionCatalog.PATIENT_UPDATE);
    String roleB1 = createRole(adminB, "Tenant B Reader", PermissionCatalog.PATIENT_VIEW);
    int countA = roleCount(TENANT_A);
    int countB = roleCount(TENANT_B);
    assertThat(countA).isEqualTo(countB + 1);

    JsonNode listB = body(getAs(ROLES + "?page=0&size=100", adminB));
    assertThat(ids(listB))
        .as("B sees its own role and never A's")
        .contains(roleB1)
        .doesNotContain(roleA1, roleA2);
    assertThat(names(listB)).contains("Tenant B Reader").doesNotContain("Tenant A Reader");
    assertThat(intField(listB, "meta", "totalElements")).isEqualTo(countB);

    JsonNode paged = body(getAs(ROLES + "?page=0&size=1", adminB));
    assertThat(intField(paged, "meta", "totalElements"))
        .as("a small page must not make the total count the other tenant's rows")
        .isEqualTo(countB);

    JsonNode listA = body(getAs(ROLES + "?page=0&size=100", adminA));
    assertThat(ids(listA)).contains(roleA1, roleA2).doesNotContain(roleB1);
    assertThat(intField(listA, "meta", "totalElements")).isEqualTo(countA);
  }

  // -------------------------------------------------------------------------
  // 3. the body leg: no reference in a body can be answered with another tenant's row
  // -------------------------------------------------------------------------

  /**
   * No Phase 6 request body accepts a tenant-scoped id (API.md section 5), so the reference a body
   * can carry is at most an unknown property &mdash; and P2.3 already refuses that with 422 before
   * any row exists. The other half is the same statement about the path: naming the foreign role
   * there is 404, and the update in test 1 shows it is never applied. Both legs are asserted here
   * together so the two halves of "never applied" read as one rule.
   */
  @Test
  void aBodyCannotReferenceAnotherTenantsRowAndNothingIsEverWritten() throws Exception {
    String roleA = createRole(adminA, "Tenant A Reader", PermissionCatalog.PATIENT_VIEW);
    String roleB = createRole(adminB, "Tenant B Reader", PermissionCatalog.PATIENT_VIEW);

    mockMvc
        .perform(
            post(ROLES)
                .header(HttpHeaders.AUTHORIZATION, adminA.bearer())
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"name\":\"Smuggled\",\"permissionCodes\":[],\"roleId\":\"" + roleB + "\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.fields[0].field").value("roleId"));

    mockMvc
        .perform(
            post(ROLES)
                .header(HttpHeaders.AUTHORIZATION, adminA.bearer())
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"name\":\"Smuggled\",\"permissionCodes\":[],\"tenantId\":\""
                        + TENANT_B
                        + "\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.fields[0].field").value("tenantId"));

    assertThat(roleName(TENANT_A, "Smuggled"))
        .as("a refused reference leaves no half-created row behind")
        .isNull();
    assertThat(roleName(TENANT_A, "Tenant A Reader")).isEqualTo("Tenant A Reader");

    // The path leg on the other tenant's own resource, and the account leg under /staff: both 404.
    mockMvc
        .perform(
            put(ROLES + "/" + roleB)
                .header(HttpHeaders.AUTHORIZATION, adminA.bearer())
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"name\":\"Renamed By A\",\"permissionCodes\":["
                        + code(PermissionCatalog.VISIT_VIEW)
                        + "]}"))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            get(STAFF + "/" + userA.getId()).header(HttpHeaders.AUTHORIZATION, adminB.bearer()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

    // ...and both rows survive for their own tenant, so the 404s above are scoping rather than a
    // fixture that was never there.
    mockMvc
        .perform(get(ROLES + "/" + roleB).header(HttpHeaders.AUTHORIZATION, adminB.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Tenant B Reader"));

    mockMvc
        .perform(
            get(STAFF + "/" + userA.getId()).header(HttpHeaders.AUTHORIZATION, adminA.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value(userA.getEmail()));
  }

  // -------------------------------------------------------------------------
  // 4. the header leg: 404 ahead of the controller, 401 when nothing is signed in
  // -------------------------------------------------------------------------

  @Test
  void aConflictingTenantHintIsRejectedBeforeTheControllerAndWithoutATokenItIs401()
      throws Exception {
    // Without the hint the route answers normally, which is what makes the 404 below the hint's.
    mockMvc
        .perform(get(ROLES).header(HttpHeaders.AUTHORIZATION, adminA.bearer()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get(ROLES)
                .header(HttpHeaders.AUTHORIZATION, adminA.bearer())
                .header(HINT, TENANT_B.toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.data").doesNotExist());

    // A write with the same conflict: if the handler had run, a role would exist now.
    mockMvc
        .perform(
            post(ROLES)
                .header(HttpHeaders.AUTHORIZATION, adminA.bearer())
                .header(HINT, TENANT_B.toString())
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"name\":\"Hint Probe\",\"permissionCodes\":["
                        + code(PermissionCatalog.PATIENT_VIEW)
                        + "]}"))
        .andExpect(status().isNotFound());
    assertThat(roleName(TENANT_A, "Hint Probe"))
        .as("the rejection happens ahead of the controller, so nothing was created")
        .isNull();

    // A matching or absent hint is harmless: the header's only possible effect is rejection.
    mockMvc
        .perform(
            get(ROLES)
                .header(HttpHeaders.AUTHORIZATION, adminA.bearer())
                .header(HINT, TENANT_A.toString()))
        .andExpect(status().isOk());

    // The same request with no token at all: 401, so the 404 above is about tenancy and not about
    // authentication.
    mockMvc
        .perform(get(ROLES).header(HINT, TENANT_B.toString()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

    // The hint does not change what an unknown id means either: a matching hint passes the filter
    // and the id is still 404.
    mockMvc
        .perform(
            get(ROLES + "/" + UUID.randomUUID())
                .header(HINT, TENANT_A.toString())
                .header(HttpHeaders.AUTHORIZATION, adminA.bearer()))
        .andExpect(status().isNotFound());
  }

  // -------------------------------------------------------------------------
  // 5. the query leg, and the context never outliving a request
  // -------------------------------------------------------------------------

  @Test
  void aQueryLegCannotRebindTheTenantAndNoRequestLeavesAContextBehind() throws Exception {
    String roleB = createRole(adminB, "Tenant B Reader", PermissionCatalog.PATIENT_VIEW);

    JsonNode hinted = body(getAs(ROLES + "?page=0&size=100&tenantId=" + TENANT_B, adminA));
    assertThat(ids(hinted))
        .as("an unrecognised query parameter is ignored, never honoured")
        .doesNotContain(roleB)
        .containsAll(ids(body(getAs(ROLES + "?page=0&size=100", adminA))));
    assertThat(TenantContext.find()).isEmpty();

    mockMvc
        .perform(
            get(ROLES + "/" + roleB + "?tenantId=" + TENANT_B)
                .header(HttpHeaders.AUTHORIZATION, adminA.bearer()))
        .andExpect(status().isNotFound());
    assertThat(TenantContext.find()).isEmpty();

    mockMvc
        .perform(get(STAFF).header(HttpHeaders.AUTHORIZATION, adminB.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray());
    assertThat(TenantContext.find())
        .as("every request shape this suite makes leaves the thread unbound")
        .isEmpty();
  }

  // -------------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------------

  private record Caller(UUID tenantId, String token, User user) {
    String bearer() {
      return "Bearer " + token;
    }
  }

  /** An administrator of {@code tenantId}, enrolled in ADMIN and signed in over HTTP. */
  private Caller signInAdministrator(UUID tenantId, String slug, String localPart)
      throws Exception {
    String email = localPart + "@" + slug + ".test";
    User user =
        fixtures.createUserWithRoles(
            tenantId, email, PASSWORD, UserStatus.ACTIVE, false, List.of("ADMIN"));
    String token =
        body(mockMvc
                .perform(
                    post(LOGIN)
                        .contentType(APPLICATION_JSON)
                        .content(
                            "{\"hospitalSlug\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(slug, email, PASSWORD)))
                .andExpect(status().isOk()))
            .at("/data/accessToken")
            .asText();
    return new Caller(tenantId, token, user);
  }

  private String createRole(Caller as, String name, PermissionCatalog permission) throws Exception {
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
                                + code(permission)
                                + "]}"))
                .andExpect(status().isCreated()));
    return text(created, "data", "id");
  }

  private ResultActions getAs(String path, Caller as) throws Exception {
    return mockMvc
        .perform(get(path).header(HttpHeaders.AUTHORIZATION, as.bearer()))
        .andExpect(status().is2xxSuccessful());
  }

  private JsonNode body(ResultActions actions) throws Exception {
    return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
  }

  /** The response with the per-request envelope fields removed, for byte-level comparison. */
  private String redacted(JsonNode response) {
    ObjectNode copy = response.deepCopy();
    copy.remove("timestamp");
    copy.remove("traceId");
    return copy.toString();
  }

  private static String text(JsonNode root, String... path) {
    JsonNode node = root;
    for (String segment : path) {
      node = node.get(segment);
    }
    return node.asText();
  }

  private static int intField(JsonNode root, String... path) {
    return root.at(pointer(path)).asInt();
  }

  private static String pointer(String... path) {
    return "/" + String.join("/", path);
  }

  private static List<String> stringArray(JsonNode root, String... path) {
    JsonNode node = root.at(pointer(path));
    List<String> values = new ArrayList<>();
    for (JsonNode element : node) {
      values.add(element.asText());
    }
    return values;
  }

  private static List<String> ids(JsonNode page) {
    return field(page, "id");
  }

  private static List<String> names(JsonNode page) {
    return field(page, "name");
  }

  private static List<String> field(JsonNode page, String name) {
    List<String> values = new ArrayList<>();
    for (JsonNode element : page.get("data")) {
      values.add(element.get(name).asText());
    }
    return values;
  }

  private static String code(PermissionCatalog permission) {
    return "\"" + permission.code() + "\"";
  }

  private int roleCount(UUID tenantId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM roles WHERE tenant_id = ?", Integer.class, uuidBytes(tenantId));
    return count == null ? 0 : count;
  }

  private String roleName(UUID tenantId, String name) {
    List<String> found =
        jdbcTemplate.queryForList(
            "SELECT name FROM roles WHERE tenant_id = ? AND name = ?",
            String.class,
            uuidBytes(tenantId),
            name);
    return found.isEmpty() ? null : found.get(0);
  }

  private void ensureTenant(UUID id, String name, String slug) {
    Integer existing =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tenants WHERE id = ?", Integer.class, uuidBytes(id));
    if (existing != null && existing > 0) {
      return;
    }
    jdbcTemplate.update(
        "INSERT INTO tenants (id, name, slug, status, timezone, created_at, updated_at, version)"
            + " VALUES (?, ?, ?, 'ACTIVE', 'UTC', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)",
        uuidBytes(id),
        name,
        slug);
  }

  /**
   * Removes everything this suite and its neighbours put in the two fixture tenants, in the order
   * the V2 foreign keys require.
   *
   * <p>{@code AuthTenantIsolationTest} deletes only the accounts, so any role rows left behind here
   * would turn its {@code DELETE FROM users} into a foreign-key error rather than a clean up.
   */
  private void clearFixtureRows() {
    for (String table : List.of("refresh_tokens", "verification_tokens", "user_roles")) {
      jdbcTemplate.update(
          "DELETE FROM " + table + " WHERE tenant_id IN (?, ?)",
          uuidBytes(TENANT_A),
          uuidBytes(TENANT_B));
    }
    jdbcTemplate.update(
        "DELETE FROM role_permissions WHERE tenant_id IN (?, ?)",
        uuidBytes(TENANT_A),
        uuidBytes(TENANT_B));
    jdbcTemplate.update(
        "DELETE FROM roles WHERE tenant_id IN (?, ?)", uuidBytes(TENANT_A), uuidBytes(TENANT_B));
    jdbcTemplate.update(
        "DELETE FROM users WHERE tenant_id IN (?, ?)", uuidBytes(TENANT_A), uuidBytes(TENANT_B));
  }

  private static byte[] uuidBytes(UUID id) {
    return ByteBuffer.allocate(16)
        .putLong(id.getMostSignificantBits())
        .putLong(id.getLeastSignificantBits())
        .array();
  }
}
