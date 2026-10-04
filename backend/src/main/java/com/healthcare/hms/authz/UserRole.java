package com.healthcare.hms.authz;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of {@code user_roles}: "this tenant's account holds this role" (V2, CONF-5).
 *
 * <p>{@code grantedAt} is the association table's only non-key column and plays the {@code
 * created_at} role per V2's header; it is never used to make a decision.
 */
@Entity
@Table(name = "user_roles")
public class UserRole {

  @EmbeddedId private UserRoleKey id;

  @Column(name = "granted_at")
  private Instant grantedAt;

  protected UserRole() {}

  public UserRole(UserRoleKey id, Instant grantedAt) {
    this.id = id;
    this.grantedAt = grantedAt;
  }

  /** Builds a grant stamped with the current instant. */
  public static UserRole grant(UUID tenantId, UUID userId, UUID roleId) {
    return new UserRole(new UserRoleKey(tenantId, userId, roleId), Instant.now());
  }

  public UserRoleKey getId() {
    return id;
  }

  public UUID getTenantId() {
    return id.getTenantId();
  }

  public UUID getUserId() {
    return id.getUserId();
  }

  public UUID getRoleId() {
    return id.getRoleId();
  }

  public Instant getGrantedAt() {
    return grantedAt;
  }
}
