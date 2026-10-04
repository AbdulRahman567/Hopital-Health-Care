package com.healthcare.hms.auth.api;

import java.util.List;

/**
 * The signed-in user, as decision D10 defines it: enough to render a header, and nothing else.
 *
 * <p>Deliberately not the whole {@code users} row. Every field here is something the application
 * already shows the account owner; password hash, MFA secret, lockout counters and audit columns
 * stay server-side because a response body is written once and read everywhere the token reaches.
 *
 * @param id the account id, as a string because it crosses a JSON boundary
 * @param email login address
 * @param firstName given name
 * @param lastName family name
 * @param roles the role <b>names</b> this account holds, alphabetically ordered (decision D2,
 *     populated from P6.3): display data for a header and a sidebar, and the same list the JWT
 *     {@code roles} claim carries. Empty for an account enrolled in no role. It is never read by an
 *     authorization decision — that reads permission codes from the database on every request
 *     instead, so a role edit is visible on the next request even though this snapshot is not
 * @param tenantName display name of the hospital this session belongs to
 */
public record SessionProfile(
    String id,
    String email,
    String firstName,
    String lastName,
    List<String> roles,
    String tenantName) {}
