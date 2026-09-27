package dev.amg.payguard.otp.interfaces.rest;

import dev.amg.payguard.otp.domain.OtpPurpose;
import dev.amg.payguard.otp.domain.StepUpTokenPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/otp/step-up-tokens")
public class StepUpTokenController {

  private final StepUpTokenPort tokens;
  private final Clock clock;

  public StepUpTokenController(StepUpTokenPort tokens, Clock clock) {
    this.tokens = tokens;
    this.clock = clock;
  }

  @PostMapping("/consume")
  public ResponseEntity<Void> consume(@Valid @RequestBody ConsumeTokenRequest request) {
    boolean accepted =
        tokens.consume(request.token(), request.userId(), request.purpose(), clock.instant());
    return accepted
        ? ResponseEntity.noContent().build()
        : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }

  public record ConsumeTokenRequest(
      @NotBlank String token, @NotBlank String userId, @NotNull OtpPurpose purpose) {}
}
