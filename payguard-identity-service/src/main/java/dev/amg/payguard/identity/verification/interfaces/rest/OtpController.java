package dev.amg.payguard.identity.verification.interfaces.rest;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.application.IssueOtpCommand;
import dev.amg.payguard.identity.verification.application.IssuedOtp;
import dev.amg.payguard.identity.verification.application.OtpApplicationService;
import dev.amg.payguard.identity.verification.application.OtpVerificationCommand;
import dev.amg.payguard.identity.verification.application.VerifiedOtp;
import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/identity/challenges")
public class OtpController {

  private final OtpApplicationService service;

  public OtpController(OtpApplicationService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<IssueOtpResponse> issue(
      @Valid @RequestBody IssueOtpRequest request, HttpServletRequest httpRequest) {
    IssuedOtp issued =
        service.issue(
            new IssueOtpCommand(
                CustomerId.parse(request.userId()),
                request.purpose(),
                request.channel(),
                request.destinationRef(),
                request.deviceFingerprint(),
                sourceIp(httpRequest)));
    return ResponseEntity.ok(
        new IssueOtpResponse(
            issued.challengeId().value(), issued.purpose(), issued.channel(), issued.expiresAt()));
  }

  @PostMapping("/{challengeId}/verify")
  public ResponseEntity<VerifyOtpResponse> verify(
      @PathVariable String challengeId,
      @Valid @RequestBody VerifyOtpRequest request,
      HttpServletRequest httpRequest) {
    VerifiedOtp verified =
        service.verify(
            new OtpVerificationCommand(
                OtpId.parse(challengeId),
                CustomerId.parse(request.userId()),
                request.purpose(),
                request.code(),
                request.deviceFingerprint(),
                sourceIp(httpRequest)));
    return ResponseEntity.ok(
        new VerifyOtpResponse(
            verified.challengeId().value(), verified.purpose(), verified.verifiedAt()));
  }

  private static String sourceIp(HttpServletRequest request) {
    return request.getRemoteAddr();
  }

  public record IssueOtpRequest(
      @NotBlank String userId,
      @NotNull OtpPurpose purpose,
      @NotNull OtpChannel channel,
      String destinationRef,
      String deviceFingerprint) {}

  public record VerifyOtpRequest(
      @NotBlank String userId,
      @NotNull OtpPurpose purpose,
      @NotBlank @Pattern(regexp = "\\d{6}") String code,
      String deviceFingerprint) {
    @Override
    public String toString() {
      return "VerifyOtpRequest[code=[REDACTED]]";
    }
  }

  public record IssueOtpResponse(
      String challengeId, OtpPurpose purpose, OtpChannel channel, Instant expiresAt) {}

  public record VerifyOtpResponse(String challengeId, OtpPurpose purpose, Instant verifiedAt) {}
}
