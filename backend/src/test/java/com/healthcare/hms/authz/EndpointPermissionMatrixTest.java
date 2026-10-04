package com.healthcare.hms.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.healthcare.hms.auth.AuthFixtures;
import com.healthcare.hms.auth.JwtTokenService;
import com.healthcare.hms.auth.User;
import com.healthcare.hms.auth.UserStatus;
import com.healthcare.hms.common.exception.ErrorCodes;
import com.healthcare.hms.config.CustomHeaderCsrfFilter;
import com.healthcare.hms.tenant.TenantContext;
import com.healthcare.hms.tenant.TenantStatus;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * P6.6 — the permission matrix: every endpoint, every principal, the answer each combination
 * deserves (plan section 6, FR-3.1).
 *
 * <p><b>Why it is built programmatically.</b> The endpoints come from {@link
 * RequestMappingHandlerMapping} rather than from a list in this file, so a route added by Phase 7
 * or any later phase enters the matrix the moment it exists. A hand-written list degrades silently
 * — the new endpoint simply is not in it, and the suite stays green while saying nothing. Here the
 * only way to dodge the matrix is to have no mapping at all. Two hand-written facts remain, and
 * both fail loudly when they go stale: the count and location of the <i>anonymous</i> surface
 * ({@code SecurityConfig} names eight routes), and the shape of a probe body, which this test
 * generates from the handler's own request type.
 *
 * <p><b>What "allowed" means.</b> For a principal that holds the declared permission the assertion
 * is that the <i>authorization layer</i> is not what answers — status is neither 401 nor 403. It is
 * not "2xx", because a correct role still legitimately receives 404 for an id that does not exist,
 * 422 for a body that does not validate, or 409 for a name already taken: those are content
 * answers, and P6.7 owns the tenancy ones. Requiring 2xx would make this matrix depend on seeded
 * rows rather than on permissions, which is the thing it is here to prove.
 *
 * <p><b>Ordering of the chain matters.</b> Argument resolution (and therefore {@code @Valid})
 * happens before the {@code @RequirePermission} advisor runs, so a write endpoint is probed with a
 * body its own validator accepts — otherwise the denial cells would observe a 422 from bean
 * validation and prove nothing about permission. The anonymous cells deliberately probe with an
 * empty object instead: validation fails before any handler body executes, so probing {@code
 * register-hospital} cannot register a hospital into the shared database.
 *
 * <p>Runs against the real {@code SecurityFilterChain} on the shared {@code hms_test} database.
 * Rows are removed again in {@code @AfterAll} (slug prefix {@code p66-}) per TESTING section 4.
 */
@SpringBootTest(
    properties = {"hms.ratelimit.login-ip-limit=1000000", "hms.ratelimit.email-ip-limit=1000000"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndpointPermissionMatrixTest {

  private static final String PREFIX = "p66-";
  private static final String PASSWORD = "Correct-Horse-Battery-9";
  private static final String LOGIN = "/api/v1/auth/login";

  /** The two answers the security chain writes itself (see {@code SecurityConfig}). */
  private static final String CHAIN_UNAUTHENTICATED = "Authentication required.";

  private static final String CHAIN_DENIED = "Access denied.";

  /** API.md section 3's envelope: the only top-level keys any response may carry. */
  private static final Set<String> ENVELOPE_KEYS =
      Set.of("success", "data", "meta", "timestamp", "traceId", "error");

  private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^}]+)\\}");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AuthFixtures fixtures;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JwtDecoder jwtDecoder;

  @Autowired
  private @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlers;

  private UUID tenantId;
  private String slug;
  private final Map<SystemRoleBundle, String> bearerByBundle =
      new EnumMap<>(SystemRoleBundle.class);
  private String noRoleBearer;

  @BeforeAll
  void provisionTheMatrixFixtures() throws Exception {
    TenantContext.clear();
    tenantId = fixtures.createTenant(newSlug(), TenantStatus.ACTIVE);
    User administrator =
        fixtures.createUser(tenantId, newEmail("admin"), PASSWORD, UserStatus.ACTIVE, false);
    fixtures.provisionRoles(tenantId, administrator.getId());
    slug =
        jdbcTemplate.queryForObject(
            "SELECT slug FROM tenants WHERE id = ?", String.class, uuidBytes(tenantId));

    for (SystemRoleBundle bundle : SystemRoleBundle.values()) {
      if (bundle == SystemRoleBundle.ADMIN) {
        bearerByBundle.put(bundle, login(administrator.getEmail()));
        continue;
      }
      User member =
          fixtures.createUserWithRoles(
              tenantId,
              newEmail(bundle.roleName().toLowerCase()),
              PASSWORD,
              UserStatus.ACTIVE,
              false,
              List.of(bundle.roleName()));
      bearerByBundle.put(bundle, login(member.getEmail()));
    }

    User withoutRoles =
        fixtures.createUser(tenantId, newEmail("norole"), PASSWORD, UserStatus.ACTIVE, false);
    noRoleBearer = login(withoutRoles.getEmail());
  }

  @AfterAll
  void cleanUp() {
    TenantContext.clear();
    fixtures.deleteTenantsStartingWith(PREFIX);
  }

  // -------------------------------------------------------------------------
  // 1. the matrix itself
  // -------------------------------------------------------------------------

  @Test
  void everyDeclaredEndpointAnswersTheFourPrincipalsItsPermissionRequires() throws Exception {
    List<Endpoint> gated =
        endpoints().stream().filter(endpoint -> endpoint.permission() != null).toList();
    assertThat(gated)
        .as("no endpoint declares a permission, so the matrix would prove nothing at all")
        .isNotEmpty();

    for (Endpoint endpoint : gated) {
      String path = resolve(endpoint);

      Reply unauthenticated = send(endpoint, path, null);
      assertThat(unauthenticated.status())
          .as("%s %s with no token", endpoint.method(), path)
          .isEqualTo(401);
      assertThat(unauthenticated.error("code"))
          .as("%s %s with no token", endpoint.method(), path)
          .isEqualTo(ErrorCodes.UNAUTHENTICATED);

      Reply noAuthorities = send(endpoint, path, noRoleBearer);
      assertThat(noAuthorities.status())
          .as("%s %s signed in with zero role rows", endpoint.method(), path)
          .isEqualTo(403);
      assertThat(noAuthorities.error("code"))
          .as("%s %s signed in with zero role rows", endpoint.method(), path)
          .isEqualTo(ErrorCodes.ACCESS_DENIED);

      Optional<SystemRoleBundle> wrongRole =
          Arrays.stream(SystemRoleBundle.values())
              .filter(bundle -> !bundle.permissionCodes().contains(endpoint.permission()))
              .findFirst();
      // Vacuous rather than failed: a permission every bundle holds has no principal that could be
      // denied, and the two cells above still prove the route is gated.
      if (wrongRole.isPresent()) {
        SystemRoleBundle bundle = wrongRole.get();
        Reply denied = send(endpoint, path, bearerByBundle.get(bundle));
        assertThat(denied.status())
            .as(
                "%s %s as %s, which does not hold %s",
                endpoint.method(), path, bundle.roleName(), endpoint.permission())
            .isEqualTo(403);
        assertThat(denied.error("code"))
            .as(
                "%s %s as %s, which does not hold %s",
                endpoint.method(), path, bundle.roleName(), endpoint.permission())
            .isEqualTo(ErrorCodes.ACCESS_DENIED);
      }

      SystemRoleBundle rightRole =
          Arrays.stream(SystemRoleBundle.values())
              .filter(bundle -> bundle.permissionCodes().contains(endpoint.permission()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new AssertionError(
                          "no system bundle holds "
                              + endpoint.permission()
                              + " — either no endpoint may ask for it, or SystemRoleBundle is out"
                              + " of step with PermissionCatalog"));

      Reply allowed = send(endpoint, path, bearerByBundle.get(rightRole));
      assertThat(allowed.status())
          .as(
              "%s %s as %s, which does hold %s",
              endpoint.method(), path, rightRole.roleName(), endpoint.permission())
          .isNotIn(401, 403);
    }
  }

  // -------------------------------------------------------------------------
  // 2. the anonymous surface — SecurityConfig section 5 names eight routes
  // -------------------------------------------------------------------------

  @Test
  void theAnonymousSurfaceIsExactlyTheEightAuthRoutesAndTheApplicationAnswersEachOfThem()
      throws Exception {
    List<Endpoint> anonymous =
        endpoints().stream().filter(endpoint -> endpoint.permission() == null).toList();

    assertThat(anonymous)
        .as(
            "a ninth anonymous route is a decision, not an accident — SecurityConfig names eight,"
                + " and ArchUnit rule 1 allow-lists them by class and method")
        .hasSize(8);
    assertThat(anonymous)
        .as("the only anonymous surface is the auth one")
        .allMatch(endpoint -> endpoint.pattern().startsWith("/api/v1/auth/"));

    for (Endpoint endpoint : anonymous) {
      String path = resolve(endpoint);
      Reply reply = send(endpoint, path, null);

      assertThat(reply.status())
          .as(
              "%s %s answered 403, so the permission layer reached an anonymous route",
              endpoint.method(), path)
          .isNotEqualTo(403);
      if (reply.status() >= 400) {
        assertThat(reply.error("message"))
            .as(
                "%s %s was answered by the security chain, so the handler never ran",
                endpoint.method(), path)
            .isNotIn(CHAIN_UNAUTHENTICATED, CHAIN_DENIED);
      }
      List<String> keys = new ArrayList<>();
      reply.body().fieldNames().forEachRemaining(keys::add);
      assertThat(keys)
          .as("%s %s responded outside API.md section 3's envelope", endpoint.method(), path)
          .isSubsetOf(ENVELOPE_KEYS);
    }
  }

  // -------------------------------------------------------------------------
  // 3. decision D2 — the roles claim
  // -------------------------------------------------------------------------

  @Test
  void theRolesClaimInTheIssuedTokenIsTheRoleNamesTheAccountActuallyHolds() throws Exception {
    User nurseAndDoctor =
        fixtures.createUserWithRoles(
            tenantId,
            newEmail("two-roles"),
            PASSWORD,
            UserStatus.ACTIVE,
            false,
            List.of(SystemRoleBundle.DOCTOR.roleName(), SystemRoleBundle.NURSE.roleName()));
    String token = login(nurseAndDoctor.getEmail());

    List<String> claim = jwtDecoder.decode(token).getClaimAsStringList(JwtTokenService.ROLES_CLAIM);
    List<String> held =
        jdbcTemplate.queryForList(
            "SELECT r.name FROM user_roles ur"
                + " JOIN roles r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id"
                + " WHERE ur.tenant_id = ? AND ur.user_id = ?",
            String.class,
            uuidBytes(tenantId),
            uuidBytes(nurseAndDoctor.getId()));

    assertThat(claim)
        .as("the token must carry the enrolments, not a guess (decision D2)")
        .containsExactlyInAnyOrderElementsOf(held);
    assertThat(claim).containsExactlyInAnyOrder("DOCTOR", "NURSE");
  }

  // -------------------------------------------------------------------------
  // 4. decision D1 — a permission edit is live on the very next request
  // -------------------------------------------------------------------------

  @Test
  void aPermissionChangeTakesEffectOnTheNextRequestWithoutReSignIn() throws Exception {
    String roleId = createRole("Matrix Reader", PermissionCatalog.PATIENT_VIEW.code());
    User reader =
        fixtures.createUserWithRoles(
            tenantId,
            newEmail("live-reader"),
            PASSWORD,
            UserStatus.ACTIVE,
            false,
            List.of("Matrix Reader"));
    String bearer = login(reader.getEmail());

    Reply before =
        send(
            get("/api/v1/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer),
            "/api/v1/roles");
    assertThat(before.status()).as("PATIENT_VIEW alone must not open the role list").isEqualTo(403);
    assertThat(before.error("code")).isEqualTo(ErrorCodes.ACCESS_DENIED);

    Reply grant =
        send(
            put("/api/v1/roles/" + roleId)
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + bearerByBundle.get(SystemRoleBundle.ADMIN))
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"name\":\"Matrix Reader\",\"permissionCodes\":["
                        + "\"PATIENT_VIEW\",\"ROLE_VIEW\"]}"),
            "/api/v1/roles/" + roleId);
    assertThat(grant.status()).as("the administrator edits the role").isEqualTo(200);

    Reply after =
        send(
            get("/api/v1/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer),
            "/api/v1/roles");
    assertThat(after.status())
        .as("the same token, unchanged, must see the grant on the next request (decision D1)")
        .isEqualTo(200);
  }

  // -------------------------------------------------------------------------
  // enumeration
  // -------------------------------------------------------------------------

  private record Endpoint(
      RequestMethod method, String pattern, String permission, HandlerMethod handler) {}

  private record Reply(int status, JsonNode body) {
    String error(String field) {
      return body.path("error").path(field).asText();
    }
  }

  /** Every {@code /api/v1} handler method, one entry per declared path pattern. */
  private List<Endpoint> endpoints() {
    List<Endpoint> found = new ArrayList<>();
    handlers
        .getHandlerMethods()
        .forEach(
            (mapping, handler) -> {
              RequirePermission annotation = handler.getMethodAnnotation(RequirePermission.class);
              String permission = annotation == null ? null : annotation.value();
              Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
              for (String pattern : mapping.getPatternValues()) {
                if (!pattern.startsWith("/api/v1/")) {
                  continue;
                }
                if (methods.isEmpty()) {
                  found.add(new Endpoint(RequestMethod.GET, pattern, permission, handler));
                } else {
                  methods.forEach(
                      method -> found.add(new Endpoint(method, pattern, permission, handler)));
                }
              }
            });
    return found.stream()
        .sorted(
            Comparator.comparing(Endpoint::pattern)
                .thenComparing(endpoint -> endpoint.method().name()))
        .toList();
  }

  /** The declared pattern with its path variables replaced by a typed sample value. */
  private String resolve(Endpoint endpoint) {
    Matcher matcher = PATH_VARIABLE.matcher(endpoint.pattern());
    StringBuffer path = new StringBuffer();
    while (matcher.find()) {
      String name = matcher.group(1);
      int colon = name.indexOf(':');
      if (colon > 0) {
        name = name.substring(0, colon);
      }
      matcher.appendReplacement(
          path, Matcher.quoteReplacement(sampleFor(endpoint.handler(), name)));
    }
    matcher.appendTail(path);
    return path.toString();
  }

  private static String sampleFor(HandlerMethod handler, String name) {
    for (Parameter parameter : handler.getMethod().getParameters()) {
      PathVariable variable = parameter.getAnnotation(PathVariable.class);
      if (variable != null && (variable.value().isEmpty() || variable.value().equals(name))) {
        return sampleOfType(parameter.getType());
      }
      if (variable == null && parameter.isNamePresent() && parameter.getName().equals(name)) {
        return sampleOfType(parameter.getType());
      }
    }
    return UUID.randomUUID().toString();
  }

  private static String sampleOfType(Class<?> type) {
    if (type == UUID.class) {
      return UUID.randomUUID().toString();
    }
    if (type == int.class || type == long.class || type == Integer.class || type == Long.class) {
      return "1";
    }
    if (type.isEnum()) {
      return ((Object[]) type.getEnumConstants())[0].toString();
    }
    return "matrix";
  }

  // -------------------------------------------------------------------------
  // probes
  // -------------------------------------------------------------------------

  private Reply send(Endpoint endpoint, String path, String bearer) throws Exception {
    MockHttpServletResponse response =
        mockMvc.perform(request(endpoint, path, bearer)).andReturn().getResponse();
    return reply(response);
  }

  private Reply send(MockHttpServletRequestBuilder builder, String path) throws Exception {
    MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
    return reply(response);
  }

  private Reply reply(MockHttpServletResponse response) throws Exception {
    String content = response.getContentAsString();
    JsonNode body =
        content.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(content);
    return new Reply(response.getStatus(), body);
  }

  private MockHttpServletRequestBuilder request(Endpoint endpoint, String path, String bearer) {
    MockHttpServletRequestBuilder builder =
        switch (endpoint.method()) {
          case POST -> post(path);
          case PUT -> put(path);
          case DELETE -> delete(path);
          default -> get(path);
        };
    builder.header(CustomHeaderCsrfFilter.HEADER, "endpoint-matrix").accept(APPLICATION_JSON);
    if (bearer != null) {
      builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
    }
    Class<?> bodyType = requestBodyType(endpoint.handler());
    if (bodyType != null && endpoint.method() != RequestMethod.GET) {
      // Anonymous cells get {}: validation then fails before any handler body can act on it, so
      // probing register-hospital cannot register a hospital. Gated cells get a body their own
      // validator accepts, because @Valid runs before the @RequirePermission advisor.
      String content = endpoint.permission() == null ? "{}" : sampleObject(bodyType).toString();
      builder.contentType(APPLICATION_JSON).content(content);
    }
    return builder;
  }

  private static Class<?> requestBodyType(HandlerMethod handler) {
    for (Parameter parameter : handler.getMethod().getParameters()) {
      if (parameter.getAnnotation(RequestBody.class) != null) {
        return parameter.getType();
      }
    }
    return null;
  }

  private ObjectNode sampleObject(Class<?> type) {
    ObjectNode node = objectMapper.createObjectNode();
    if (!type.isRecord()) {
      return node;
    }
    for (RecordComponent component : type.getRecordComponents()) {
      node.set(component.getName(), sampleValue(component.getName(), component.getGenericType()));
    }
    return node;
  }

  private JsonNode sampleValue(String name, Type type) {
    Class<?> raw = rawType(type);
    String field = name.toLowerCase();
    if (raw == String.class || raw == CharSequence.class) {
      if (field.contains("email")) {
        return TextNode.valueOf(name + "@example.test");
      }
      if (field.contains("slug")) {
        return TextNode.valueOf("matrix");
      }
      return TextNode.valueOf("matrix-probe");
    }
    if (raw == UUID.class) {
      return TextNode.valueOf(UUID.randomUUID().toString());
    }
    if (raw == boolean.class || raw == Boolean.class) {
      return BooleanNode.FALSE;
    }
    if (raw.isPrimitive() || Number.class.isAssignableFrom(raw)) {
      return IntNode.valueOf(1);
    }
    if (raw.isEnum()) {
      return TextNode.valueOf(((Object[]) raw.getEnumConstants())[0].toString());
    }
    if (Collection.class.isAssignableFrom(raw)) {
      return objectMapper.createArrayNode();
    }
    if (raw.isRecord()) {
      return sampleObject(raw);
    }
    return NullNode.getInstance();
  }

  private static Class<?> rawType(Type type) {
    if (type instanceof Class<?> typeClass) {
      return typeClass;
    }
    if (type instanceof java.lang.reflect.ParameterizedType parameterized
        && parameterized.getRawType() instanceof Class<?> raw) {
      return raw;
    }
    return Object.class;
  }

  // -------------------------------------------------------------------------
  // fixtures
  // -------------------------------------------------------------------------

  private String createRole(String name, String... codes) throws Exception {
    String body =
        "{\"name\":\""
            + name
            + "\",\"permissionCodes\":["
            + String.join(",", Arrays.stream(codes).map(code -> "\"" + code + "\"").toList())
            + "]}";
    MockHttpServletResponse response =
        mockMvc
            .perform(
                post("/api/v1/roles")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + bearerByBundle.get(SystemRoleBundle.ADMIN))
                    .contentType(APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse();
    return objectMapper.readTree(response.getContentAsString()).path("data").path("id").asText();
  }

  private String login(String email) throws Exception {
    MockHttpServletResponse response =
        mockMvc
            .perform(
                post(LOGIN)
                    .contentType(APPLICATION_JSON)
                    .content(
                        "{\"hospitalSlug\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}"
                            .formatted(slug, email, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    return objectMapper
        .readTree(response.getContentAsString())
        .path("data")
        .path("accessToken")
        .asText();
  }

  private String newEmail(String hint) {
    return PREFIX + hint + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
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
}
