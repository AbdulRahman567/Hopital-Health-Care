package com.healthcare.hms.auth.api;

import com.healthcare.hms.auth.LoginService;
import com.healthcare.hms.auth.RegistrationService;
import com.healthcare.hms.auth.VerificationService;
import com.healthcare.hms.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The first HTTP surface of the application (plans P5.2 and P5.3): four anonymous endpoints, every
 * other route still {@code denyAll()} in {@code SecurityConfig}.
 *
 * <p>The three email-shaped routes answer with D9's uniform 202 except verification itself, which
 * is an explicit success (200) because the caller sees the activation happen. {@code login} is the
 * one credential-shaped route: 200 with a token, or the single 401 every failure shares.
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

  private final RegistrationService registrationService;
  private final VerificationService verificationService;
  private final LoginService loginService;

  public AuthController(
      RegistrationService registrationService,
      VerificationService verificationService,
      LoginService loginService) {
    this.registrationService = registrationService;
    this.verificationService = verificationService;
    this.loginService = loginService;
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

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(loginService.login(request)));
  }

  private static ResponseEntity<ApiResponse<MessageResponse>> accepted(String message) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.ok(new MessageResponse(message)));
  }
}
