package archunit.fixtures;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A deliberately incomplete controller, used to prove that {@code EndpointPermissionArchUnitTest}'s
 * rule 1 can actually fail.
 *
 * <p>A rule that has never rejected anything is a rule nobody can trust: if the assertion were
 * written against an empty set of controllers, or against a predicate that never matches, it would
 * pass forever while a real unannotated handler shipped straight through. This fixture is imported
 * by a second, test-including importer (the production importer is configured with {@code
 * DoNotIncludeTests}) and the same helper that walks production controllers must return at least
 * one violation for it — otherwise the test fails instead of the rule.
 *
 * <p>It lives outside {@code com.healthcare.hms} on purpose. Spring component scanning walks the
 * application's root package across the whole classpath, so a {@code @RestController} filed under
 * the application package would become a live handler in every {@code @SpringBootTest} — and would
 * then appear in P6.6's endpoint matrix as an endpoint nobody asked for.
 */
@RestController
public class UnannotatedControllerFixture {

  @GetMapping("/__archunit/unannotated")
  public String declaredNowhere() {
    return "this method has no @RequirePermission";
  }
}
