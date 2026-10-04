package com.healthcare.hms.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import archunit.fixtures.UnannotatedControllerFixture;
import com.healthcare.hms.authz.PermissionCatalog;
import com.healthcare.hms.authz.RequirePermission;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import jakarta.persistence.Entity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.web.bind.annotation.RestController;

/**
 * P6.3's executable architecture contract (decision D9): the rules that make FR-3.1 "every endpoint
 * declares permission(s); default deny" a build failure instead of a review item.
 *
 * <p>Static analysis only — no Spring context, no database, no HTTP. That is the point: the whole
 * suite runs in well under a second and it cannot be green because a fixture was cleaned up
 * afterwards.
 *
 * <p><b>Four rules, one class.</b>
 *
 * <ol>
 *   <li><b>Deny by default, structurally.</b> Every public method of every {@code @RestController}
 *       carries {@code @RequirePermission}, except the eight anonymous auth endpoints that are
 *       allow-listed by class and method name (there is no permission for "log in"). A new
 *       controller from Phase 7+ is covered automatically, because the rule enumerates handlers by
 *       reflection rather than by a hand-written list.
 *   <li><b>Controllers are a thin layer.</b> No controller may reference a {@code ..repository..}
 *       type, and none may reference an {@code @Entity} type at all — signatures included, since a
 *       signature is a reference. ARCHITECTURE section 3 rule 2.
 *   <li><b>The module map is real.</b> Every module may depend only on what ARCHITECTURE section 3
 *       lists, plus {@code common} (a base layer the table takes for granted) and the three edges
 *       that exist in code and are recorded rather than ignored: {@code config &rarr; tenant}
 *       (ISO-8), {@code config &rarr; authz} (decision D3, this very filter) and {@code auth &rarr;
 *       config} (ISO-9, {@code AuthConfiguration} needs {@code JwtSecretValidator}).
 *   <li><b>ISO-1's standing follow-up.</b> {@code JdbcTemplate} and {@code nativeQuery = true} stay
 *       inside the four documented guardrail/key-directory classes.
 * </ol>
 *
 * <p>Rule 1 is additionally asserted to be <i>capable of failing</i>: {@code
 * UnannotatedControllerFixture} is imported with a second, test-including importer and must produce
 * a violation. A rule that has never rejected anything is a rule nobody can trust.
 */
class EndpointPermissionArchUnitTest {

  /** Production types only: test classes, fixtures above all, are excluded from every rule. */
  private static final JavaClasses PRODUCTION =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages("com.healthcare.hms");

  /**
   * The eight anonymous endpoints, keyed by controller simple name so a new controller cannot hide
   * behind them.
   *
   * <p>They are anonymous by design, not by omission: no permission exists for "log in", the routes
   * are {@code permitAll()} in {@code SecurityConfig}, and the response body of each is documented
   * in API.md section 7. Anything else on {@code AuthController} is a new handler and must declare
   * a permission like everybody else.
   */
  private static final Map<String, Set<String>> ANONYMOUS_ENDPOINTS =
      Map.of(
          "AuthController",
          Set.of(
              "registerHospital",
              "verifyEmail",
              "resendVerification",
              "login",
              "refresh",
              "logout",
              "forgotPassword",
              "resetPassword"));

  /**
   * What ARCHITECTURE section 3's "May depend on" column allows, plus {@code common} for every
   * module (the table lists it only where a module has nothing else to say) and the self edge.
   */
  private static final Map<String, Set<String>> ALLOWED_DEPENDENCIES = moduleDependencies();

  /**
   * The four classes that may touch SQL directly, each documented where it is written: {@code
   * UserRepository} is ISO-1's deliberately unscoped guardrail, the other three are the decision D1
   * key directories that must run before a tenant context exists (Hibernate will not open a session
   * with an empty one).
   */
  private static final Set<String> SQL_GUARDRAILS =
      Set.of(
          "com.healthcare.hms.auth.repository.UserRepository",
          "com.healthcare.hms.auth.RegistrationService",
          "com.healthcare.hms.auth.TokenBootstrapLookup",
          "com.healthcare.hms.tenant.TenantBootstrapLookup");

  // -------------------------------------------------------------------------
  // Rule 1 — every endpoint declares a permission, or is anonymous by design
  // -------------------------------------------------------------------------

  @Test
  void everyControllerMethodDeclaresAPermissionOrIsAnonymousByDesign() {
    List<String> violations = missingPermissionAnnotations(PRODUCTION);

    assertThat(controllers(PRODUCTION))
        .as("the importer actually found the controllers")
        .isNotEmpty();
    assertThat(violations)
        .as(
            "public controller methods with no @RequirePermission (add the annotation, or — only "
                + "for a genuinely anonymous endpoint — the allow-list in this class)")
        .isEmpty();
  }

  @Test
  void thePermissionRuleCanFail() {
    JavaClasses fixture = new ClassFileImporter().importClasses(UnannotatedControllerFixture.class);

    assertThat(fixture).hasSize(1);
    assertThat(missingPermissionAnnotations(fixture))
        .as("a rule that has never rejected anything is a rule nobody can trust")
        .containsExactly("UnannotatedControllerFixture#declaredNowhere()");
  }

  @Test
  void everyDeclaredPermissionIsAMemberOfTheCatalog() {
    Set<String> catalog =
        Arrays.stream(PermissionCatalog.values())
            .map(PermissionCatalog::code)
            .collect(Collectors.toSet());
    List<String> unknown = new ArrayList<>();

    for (JavaClass controller : controllers(PRODUCTION)) {
      for (JavaMethod method : controller.getMethods()) {
        if (!isDeclaredPublicHandler(controller, method)) {
          continue;
        }
        Optional<JavaAnnotation<JavaMethod>> annotation =
            method.tryGetAnnotationOfType(RequirePermission.class.getName());
        if (annotation.isEmpty()) {
          continue;
        }
        Object value = annotation.get().get("value").orElse(null);
        if (!(value instanceof String code) || !catalog.contains(code)) {
          unknown.add(controller.getSimpleName() + "#" + method.getName() + "() -> " + value);
        }
      }
    }

    assertThat(unknown)
        .as("@RequirePermission values must be PermissionCatalog codes, never a raw spelling")
        .isEmpty();
  }

  // -------------------------------------------------------------------------
  // Rule 2 — controllers call services only
  // -------------------------------------------------------------------------

  @Test
  void controllersNeverTouchRepositoriesOrEntities() {
    ArchRule noRepositories =
        noClasses()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..repository..")
            .because(
                "ARCHITECTURE.md section 3 rule 2: controllers talk to services, never to the "
                    + "persistence layer");
    ArchRule noEntities =
        noClasses()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .dependOnClassesThat(isAnEntityType())
            .because(
                "ARCHITECTURE.md section 3 rule 2: an entity in a signature hands the whole "
                    + "row to the caller, including columns the DTO deliberately omits");

    noRepositories.check(PRODUCTION);
    noEntities.check(PRODUCTION);
  }

  // -------------------------------------------------------------------------
  // Rule 3 — module dependencies per ARCHITECTURE.md section 3
  // -------------------------------------------------------------------------

  @Test
  void modulesDependOnlyOnWhatArchitectureSectionThreeAllows() {
    List<String> violations = new ArrayList<>();

    ALLOWED_DEPENDENCIES.forEach(
        (module, allowed) -> {
          Set<String> forbidden = new LinkedHashSet<>(ALLOWED_DEPENDENCIES.keySet());
          forbidden.removeAll(allowed);
          forbidden.remove(module);
          if (forbidden.isEmpty()) {
            return;
          }
          ArchRule rule =
              noClasses()
                  .that()
                  .resideInAPackage("com.healthcare.hms." + module + "..")
                  .should()
                  .dependOnClassesThat(residesInModules(forbidden))
                  .because("ARCHITECTURE.md section 3: '" + module + "' may depend on " + allowed);
          EvaluationResult result = rule.evaluate(PRODUCTION);
          if (result.hasViolation()) {
            violations.addAll(result.getFailureReport().getDetails());
          }
        });

    assertThat(ALLOWED_DEPENDENCIES).hasSize(18);
    assertThat(violations).isEmpty();
  }

  // -------------------------------------------------------------------------
  // Rule 4 (decision D9's stretch) — raw SQL stays in the guardrail classes
  // -------------------------------------------------------------------------

  @Test
  void rawSqlStaysInTheDocumentedGuardrailClasses() {
    List<String> violations = new ArrayList<>();

    for (JavaClass type : PRODUCTION) {
      boolean usesJdbcTemplate =
          type.getDirectDependenciesFromSelf().stream()
              .anyMatch(
                  dependency ->
                      "org.springframework.jdbc.core.JdbcTemplate"
                          .equals(dependency.getTargetClass().getName()));
      if (usesJdbcTemplate && !SQL_GUARDRAILS.contains(type.getName())) {
        violations.add(type.getName() + " uses JdbcTemplate");
      }
      for (JavaMethod method : type.getMethods()) {
        if (!method.getOwner().equals(type)) {
          continue;
        }
        Optional<JavaAnnotation<JavaMethod>> query =
            method.tryGetAnnotationOfType(Query.class.getName());
        boolean nativeQuery =
            query.isPresent()
                && Boolean.TRUE.equals(query.get().get("nativeQuery").orElse(Boolean.FALSE));
        if (nativeQuery && !SQL_GUARDRAILS.contains(type.getName())) {
          violations.add(
              type.getName() + "#" + method.getName() + "() declares nativeQuery = true");
        }
      }
    }

    assertThat(violations)
        .as(
            "ISO-1's standing follow-up: unscoped SQL is a cross-tenant read, so it lives in a "
                + "named, documented list rather than anywhere")
        .isEmpty();
  }

  // -------------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------------

  /** Controllers of the given import, in a stable order for readable failure messages. */
  private static List<JavaClass> controllers(JavaClasses classes) {
    List<JavaClass> found = new ArrayList<>();
    for (JavaClass type : classes) {
      if (type.isAnnotatedWith(RestController.class)) {
        found.add(type);
      }
    }
    found.sort(Comparator.comparing(JavaClass::getName));
    return found;
  }

  /** Public methods this class declares, which is exactly the set rule 1 covers. */
  private static boolean isDeclaredPublicHandler(JavaClass controller, JavaMethod method) {
    if (!method.getOwner().equals(controller)) {
      return false;
    }
    if (!method.getModifiers().contains(JavaModifier.PUBLIC)) {
      return false;
    }
    Set<JavaModifier> modifiers = method.getModifiers();
    return !modifiers.contains(JavaModifier.SYNTHETIC) && !modifiers.contains(JavaModifier.BRIDGE);
  }

  /**
   * Rule 1 as data, so the production assertion and the fixture assertion are the same code path —
   * if the fixture ever passes, the production assertion is worthless too.
   */
  private static List<String> missingPermissionAnnotations(JavaClasses classes) {
    List<String> violations = new ArrayList<>();
    for (JavaClass controller : controllers(classes)) {
      for (JavaMethod method : controller.getMethods()) {
        if (!isDeclaredPublicHandler(controller, method)) {
          continue;
        }
        if (anonymousByDesign(controller, method)) {
          continue;
        }
        if (method.tryGetAnnotationOfType(RequirePermission.class.getName()).isPresent()) {
          continue;
        }
        violations.add(controller.getSimpleName() + "#" + method.getName() + "()");
      }
    }
    violations.sort(Comparator.naturalOrder());
    return violations;
  }

  private static boolean anonymousByDesign(JavaClass controller, JavaMethod method) {
    return ANONYMOUS_ENDPOINTS
        .getOrDefault(controller.getSimpleName(), Set.of())
        .contains(method.getName());
  }

  private static DescribedPredicate<JavaClass> isAnEntityType() {
    return new DescribedPredicate<>("a persistence entity") {
      @Override
      public boolean test(JavaClass input) {
        return input.isAnnotatedWith(Entity.class.getName());
      }
    };
  }

  private static DescribedPredicate<JavaClass> residesInModules(Set<String> modules) {
    List<String> packages = new ArrayList<>();
    for (String module : modules) {
      packages.add("com.healthcare.hms." + module);
    }
    return new DescribedPredicate<>("a type of module " + modules) {
      @Override
      public boolean test(JavaClass input) {
        String packageName = input.getPackageName();
        if (!packageName.startsWith("com.healthcare.hms.")) {
          return false;
        }
        return packages.stream()
            .anyMatch(owned -> packageName.equals(owned) || packageName.startsWith(owned + "."));
      }
    };
  }

  private static Map<String, Set<String>> moduleDependencies() {
    Map<String, Set<String>> allowed = new LinkedHashMap<>();
    allowed.put("common", Set.of());
    allowed.put("config", Set.of("common", "tenant", "authz"));
    allowed.put("tenant", Set.of("common"));
    allowed.put("auth", Set.of("tenant", "authz", "audit", "notification", "common", "config"));
    allowed.put("authz", Set.of("tenant", "common"));
    allowed.put("organization", Set.of("tenant", "authz", "audit", "common"));
    allowed.put("staff", Set.of("organization", "auth", "common"));
    allowed.put("patient", Set.of("tenant", "authz", "audit", "common"));
    allowed.put("appointment", Set.of("patient", "staff", "common"));
    allowed.put("clinical", Set.of("patient", "appointment", "common"));
    allowed.put("history", Set.of("patient", "clinical", "common"));
    allowed.put("prescription", Set.of("clinical", "common"));
    allowed.put("lab", Set.of("clinical", "document", "common"));
    allowed.put("document", Set.of("tenant", "authz", "common"));
    allowed.put("billing", Set.of("clinical", "patient", "common"));
    allowed.put("notification", Set.of("tenant", "common"));
    allowed.put("audit", Set.of("tenant", "common"));
    allowed.put("search", Set.of("patient", "staff", "appointment", "common"));
    return allowed;
  }
}
