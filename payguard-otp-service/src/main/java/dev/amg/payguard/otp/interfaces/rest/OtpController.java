package dev.amg.payguard.otp.interfaces.rest;

import dev.amg.payguard.otp.application.IssueOtpCommand;
import dev.amg.payguard.otp.application.IssuedOtp;
import dev.amg.payguard.otp.application.OtpApplicationService;
import dev.amg.payguard.otp.application.VerifiedOtp;
import dev.amg.payguard.otp.application.VerifyOtpCommand;
import dev.amg.payguard.otp.domain.OtpChannel;
import dev.amg.payguard.otp.domain.OtpPurpose;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/otp/challenges")
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
                request.userId(),
                request.purpose(),
                request.channel(),
                request.destinationRef(),
                request.deviceFingerprint(),
                sourceIp(httpRequest)));
    return ResponseEntity.ok(
        new IssueOtpResponse(
            issued.challengeId(), issued.purpose(), issued.channel(), issued.expiresAt()));
  }

  @PostMapping("/{challengeId}/verify")
  public ResponseEntity<VerifyOtpResponse> verify(
      @PathVariable UUID challengeId,
      @Valid @RequestBody VerifyOtpRequest request,
      HttpServletRequest httpRequest) {
    VerifiedOtp verified =
        service.verify(
            new VerifyOtpCommand(
                challengeId,
                request.userId(),
                request.purpose(),
                request.code(),
                request.deviceFingerprint(),
                sourceIp(httpRequest)));
    return ResponseEntity.ok(
        new VerifyOtpResponse(verified.stepUpToken(), verified.purpose(), verified.expiresAt()));
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
      String deviceFingerprint) {}

  public record IssueOtpResponse(
      UUID challengeId, OtpPurpose purpose, OtpChannel channel, Instant expiresAt) {}

  public record VerifyOtpResponse(String stepUpToken, OtpPurpose purpose, Instant expiresAt) {}
}
