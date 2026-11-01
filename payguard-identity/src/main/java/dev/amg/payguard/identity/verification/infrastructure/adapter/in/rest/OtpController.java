package dev.amg.payguard.identity.verification.infrastructure.adapter.in.rest;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.application.command.issue_otp.IssueOtpCommand;
import dev.amg.payguard.identity.verification.application.command.issue_otp.IssueOtpCommandResponse;
import dev.amg.payguard.identity.verification.application.command.issue_otp.IssueOtpHandler;
import dev.amg.payguard.identity.verification.application.command.otp_verification.OtpVerificationCommand;
import dev.amg.payguard.identity.verification.application.command.otp_verification.OtpVerificationHandler;
import dev.amg.payguard.identity.verification.infrastructure.adapter.in.rest.dto.IssueOtpRequest;
import dev.amg.payguard.identity.verification.infrastructure.adapter.in.rest.dto.VerifyOtpRequest;
import dev.amg.payguard.identity.verification.infrastructure.adapter.in.rest.dto.VerifyOtpResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP entry point for OTP issuance and verification.
 *
 * <p>Converts external representations into application commands. Challenge lifecycle rules and
 * persistence are owned by the corresponding application handlers and domain aggregate.
 */
@RestController
@RequestMapping("/identity/challenges")
public class OtpController {

  private final IssueOtpHandler issueOtpHandler;
  private final OtpVerificationHandler verificationHandler;

  public OtpController(
      IssueOtpHandler issueOtpHandler, OtpVerificationHandler verificationHandler) {

    this.issueOtpHandler = Objects.requireNonNull(issueOtpHandler);
    this.verificationHandler = Objects.requireNonNull(verificationHandler);
  }

  @PostMapping
  public ResponseEntity<IssueOtpCommandResponse> issue(
      @Valid @RequestBody IssueOtpRequest request, HttpServletRequest httpRequest) {

    var command =
        new IssueOtpCommand(
            CustomerId.parse(request.userId()),
            request.purpose(),
            request.channel(),
            request.destinationRef(),
            request.deviceFingerprint(),
            sourceIp(httpRequest));

    var result = issueOtpHandler.handle(command);

    return ResponseEntity.ok(
        new IssueOtpCommandResponse(
            result.otpId(),
            result.purpose(),
            result.channel(),
            command.destinationRef(),
            sourceIp(httpRequest),
            result.expiresAt()));
  }

  @PostMapping("/{challengeId}/verify")
  public ResponseEntity<VerifyOtpResponse> verify(
      @PathVariable("challengeId") String challengeId,
      @Valid @RequestBody VerifyOtpRequest request,
      HttpServletRequest httpRequest) {

    var command =
        new OtpVerificationCommand(
            OtpId.parse(challengeId),
            CustomerId.parse(request.userId()),
            request.purpose(),
            request.code(),
            request.deviceFingerprint(),
            sourceIp(httpRequest));

    var result = verificationHandler.handle(command);

    return ResponseEntity.ok(
        new VerifyOtpResponse(result.challengeId().value(), result.purpose(), result.verifiedAt()));
  }

  private static String sourceIp(HttpServletRequest request) {
    return request.getRemoteAddr();
  }
}
