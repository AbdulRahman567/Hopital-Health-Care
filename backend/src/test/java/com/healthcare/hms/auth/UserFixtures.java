package com.healthcare.hms.auth;

/**
 * Deterministic {@link User} factory for tests (TESTING.md section 10: builders, synthetic data
 * only).
 *
 * <p>Lives in the entity's own package because {@code User}'s no-arg constructor is deliberately
 * protected — JPA needs it, production code should go through a service. Everything here is
 * fixture-only: the password hash is a labelled fake and never verifies.
 */
public final class UserFixtures {

  private UserFixtures() {}

  /** An ACTIVE member with a fixture-only password hash. */
  public static User user(String email) {
    User user = new User();
    user.setEmail(email);
    user.setPasswordHash("$argon2id$v=19$m=65536,t=3,p=4$fixture$not-a-real-hash");
    user.setFirstName("Fixture");
    user.setLastName("Member");
    user.setStatus(UserStatus.ACTIVE);
    return user;
  }
}
