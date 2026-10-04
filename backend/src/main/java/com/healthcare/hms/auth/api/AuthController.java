package com.healthcare.hms.auth.api;

import com.healthcare.hms.auth.LoginService;
import com.healthcare.hms.auth.LoginSession;
import com.healthcare.hms.auth.PasswordResetService;
import com.healthcare.hms.auth.RefreshCookieBuilder;
import com.healthcare.hms.auth.RefreshService;
import com.healthcare.hms.auth.RefreshSession;
import com.healthcare.hms.auth.RegistrationService;
import com.healthcare.hms.auth.VerificationService;
import com.healthcare.hms.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The first HTTP surface of the application (plans P5.2, P5.3, P5.5 and P5.7): eight anonymous
 * endpoints, every other route still {@code denyAll()} in {@code SecurityConfig}.
 *
 * <p>The four email-shaped routes answer with D9's uniform 202 except verification itself, which is
 * an explicit success (200) because the caller sees the activation happen. {@code login} is the one
 * credential-shaped route: 200 with a token, or the single 401 every failure shares. {@code
 * reset-password} is the other 200 &mdash; the caller is holding the token that proves which
 * account it is, so there is nothing left to hide.
 *
 * <p>P5.5 adds the two routes that are authenticated by cookie instead of bearer token. They are
 * the only handlers here that touch {@code Set-Cookie}: {@code login} and {@code refresh} each hand
 * out a successor value, {@code logout} clears it. The raw refresh token appears in no body
 * anywhere — an {@code HttpOnly} cookie that also sits in a JSON field would be readable by the
 * exact script the flag exists to keep out.
 *
 * <p>{@code resend-verification} is not in API.md section 11's indicative map; PRD FR-1.3 ("never
 * times out or leaves the flow") requires it, which is recorded in the P5.2 evidence.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  /** What every registration attempt is told, known slug or not (D9). */
  public static final String REGISTRATION_ACCEPTED =
      "Registration received. Check your email to verify this hospital.";

  /** What a successful verification is told — the one non-uniform auth response. */
  public static final String EMAIL_VERIFIED = "Email verified. The hospital can now sign in.";

  /** What every resend attempt is told, existing address or not (D9 / FR-1.3). */
  public static final String RESEND_ACCEPTED =
      "If that address belongs to an unverified account, a verification email has been sent.";

  /** What logout tells the caller. It is true even when the family was already dead (FR-2.6). */
  public static final String LOGGED_OUT = "Signed out.";

  /**
   * What every forgot-password attempt is told, known slug/address or not (D9): the only observable
   * difference is the email itself.
   */
  public static final String FORGOT_ACCEPTED =
      "If that address belongs to an account, a password reset link has been sent.";

  /** What a successful reset is told. It says nothing about which account changed. */
  public static final String PASSWORD_UPDATED = "Password updated. Sign in with the new password.";

  private final RegistrationService registrationService;
  private final VerificationService verificationService;
  private final LoginService loginService;
  private final RefreshService refreshService;
  private final PasswordResetService passwordResetService;
  private final RefreshCookieBuilder cookieBuilder;

  public AuthController(
      RegistrationService registrationService,
      VerificationService verificationService,
      LoginService loginService,
      RefreshService refreshService,
      PasswordResetService passwordResetService,
      RefreshCookieBuilder cookieBuilder) {
    this.registrationService = registrationService;
    this.verificationService = verificationService;
    this.loginService = loginService;
    this.refreshService = refreshService;
    this.passwordResetService = passwordResetService;
    this.cookieBuilder = cookieBuilder;
  }

  @PostMapping("/register-hospital")
  public ResponseEntity<ApiResponse<MessageResponse>> registerHospital(
      @Valid @RequestBody RegisterHospitalRequest request) {
    registrationService.register(request);
    return accepted(REGISTRATION_ACCEPTED);
  }

  @PostMapping("/verify-email")
  public ResponseEntity<ApiResponse<MessageResponse>> verifyEmail(
      @Valid @RequestBody VerifyEmailRequest request) {
    verificationService.verifyEmail(request.token());
    return ResponseEntity.ok(ApiResponse.ok(new MessageResponse(EMAIL_VERIFIED)));
  }

  @PostMapping("/resend-verification")
  public ResponseEntity<ApiResponse<MessageResponse>> resendVerification(
      @Valid @RequestBody ResendVerificationRequest request) {
    verificationService.resendVerification(request.email(), request.hospitalSlug());
    return accepted(RESEND_ACCEPTED);
  }

  /**
   * Signs in and starts a refresh family: the access token in the body, the first refresh token in
   * an {@code HttpOnly} cookie (decision D6).
   */
  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    LoginSession session = loginService.login(request, userAgent);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE, cookieBuilder.issue(session.refresh().rawToken()).toString())
        .body(ApiResponse.ok(session.response()));
  }

  /**
   * Rotates the cookie and answers with a fresh access token plus the profile decision D10 asks
   * for. Guarded by {@code CustomHeaderCsrfFilter} because it acts on the cookie alone.
   */
  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
      @CookieValue(name = RefreshCookieBuilder.COOKIE_NAME, required = false) String refreshToken,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    RefreshSession session = refreshService.refresh(refreshToken, userAgent);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE, cookieBuilder.issue(session.refresh().rawToken()).toString())
        .body(ApiResponse.ok(session.response()));
  }

  /** Revokes the family server-side and drops the cookie. Idempotent (FR-2.6). */
  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<MessageResponse>> logout(
      @CookieValue(name = RefreshCookieBuilder.COOKIE_NAME, required = false) String refreshToken) {
    refreshService.logout(refreshToken);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookieBuilder.cleared().toString())
        .body(ApiResponse.ok(new MessageResponse(LOGGED_OUT)));
  }

  /**
   * Requests a reset link &mdash; always the same 202 (decision D9), because a different answer for
   * an unknown address would turn an anonymous endpoint into an account-existence oracle.
   */
  @PostMapping("/forgot-password")
  public ResponseEntity<ApiResponse<MessageResponse>> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request) {
    passwordResetService.forgotPassword(request.email(), request.hospitalSlug());
    return accepted(FORGOT_ACCEPTED);
  }

  /**
   * Consumes a reset link and changes the password. The one flow whose success is reported, since
   * the caller is holding the token that proves which account it is.
   */
  @PostMapping("/reset-password")
  public ResponseEntity<ApiResponse<MessageResponse>> resetPassword(
      @Valid @RequestBody ResetPasswordRequest request) {
    passwordResetService.resetPassword(request.token(), request.newPassword());
    return ResponseEntity.ok(ApiResponse.ok(new MessageResponse(PASSWORD_UPDATED)));
  }

  private static ResponseEntity<ApiResponse<MessageResponse>> accepted(String message) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.ok(new MessageResponse(message)));
  }
}
