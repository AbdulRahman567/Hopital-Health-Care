package com.healthcare.hms.authz.policy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.ResolvableType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Registry and single entry point for {@link ResourcePolicy} (ROADMAP P6.4, decision D7).
 *
 * <p>Two jobs, deliberately in one bean:
 *
 * <ul>
 *   <li><b>Registry.</b> Every {@code ResourcePolicy} bean is keyed by the {@code T} it declares,
 *       so a service can ask for {@code ResourcePolicies.requireRead(User.class, user)} without
 *       knowing which policy implements it. Two policies claiming the same type fail startup rather
 *       than leaving the winner to Spring's bean-ordering &mdash; an authorization rule that
 *       depends on instantiation order is not a rule.
 *   <li><b>Fail closed on a missing rule.</b> A resource type with no policy is a programming
 *       error, not a licence to read: {@link #policyFor} throws an {@link IllegalStateException}
 *       naming the type. The alternative (returning {@code null} and letting the caller decide) is
 *       how a "no policy configured" path quietly becomes "everybody may read".
 * </ul>
 *
 * <p>The instance overloads exist for two callers that legitimately hold a policy rather than a
 * type: services that inject their policy directly, and tests driving a policy double through the
 * same {@link ResourcePolicy#requireRead} / {@link ResourcePolicy#readPredicate} code the real one
 * uses (decision D7's test-double proof). Both overloads delegate, so there is exactly one
 * implementation of the enforcement rules.
 */
@Component
public class ResourcePolicies {

  private final Map<Class<?>, ResourcePolicy<?>> byType;

  /**
   * @param policies every {@link ResourcePolicy} bean; Spring supplies them, which is what makes
   *     the registry complete by construction
   * @throws IllegalStateException when two policies claim the same subject type
   */
  public ResourcePolicies(List<ResourcePolicy<?>> policies) {
    Map<Class<?>, ResourcePolicy<?>> registered = new LinkedHashMap<>();
    for (ResourcePolicy<?> policy : policies) {
      Class<?> subjectType =
          ResolvableType.forClass(policy.getClass())
              .as(ResourcePolicy.class)
              .getGeneric()
              .resolve();
      if (subjectType == null) {
        throw new IllegalStateException(
            "Cannot determine the subject type of resource policy "
                + policy.getClass().getName()
                + "; declare it as ResourcePolicy<ConcreteType>.");
      }
      ResourcePolicy<?> existing = registered.put(subjectType, policy);
      if (existing != null) {
        throw new IllegalStateException(
            "Two resource policies claim "
                + subjectType.getName()
                + ": "
                + existing.getClass().getName()
                + " and "
                + policy.getClass().getName());
      }
    }
    this.byType = Map.copyOf(registered);
  }

  /**
   * The policy registered for {@code type}.
   *
   * @throws IllegalStateException when none is — a missing rule is a defect, never a permissive
   *     default
   */
  public <T> ResourcePolicy<T> policyFor(Class<T> type) {
    ResourcePolicy<?> policy = byType.get(type);
    if (policy == null) {
      throw new IllegalStateException(
          "No resource policy is registered for " + type.getName() + ".");
    }
    @SuppressWarnings("unchecked")
    ResourcePolicy<T> typed = (ResourcePolicy<T>) policy;
    return typed;
  }

  /** Record-level enforcement for a registered subject type. */
  public <T> void requireRead(Class<T> type, T subject) {
    policyFor(type).requireRead(subject);
  }

  /** Record-level enforcement for a policy the caller already holds (see the class javadoc). */
  public <T> void requireRead(ResourcePolicy<T> policy, T subject) {
    policy.requireRead(subject);
  }

  /** The list-query form for a registered subject type, to be composed into the service's query. */
  public <T> Specification<T> readPredicate(Class<T> type) {
    return policyFor(type).readPredicate();
  }

  /** The list-query form for a policy the caller already holds (see the class javadoc). */
  public <T> Specification<T> readPredicate(ResourcePolicy<T> policy) {
    return policy.readPredicate();
  }
}
