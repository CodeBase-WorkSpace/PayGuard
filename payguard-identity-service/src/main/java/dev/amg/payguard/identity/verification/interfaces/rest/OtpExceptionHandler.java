package dev.amg.payguard.identity.verification.interfaces.rest;

import dev.amg.payguard.identity.verification.application.OtpChallengeNotFoundException;
import dev.amg.payguard.identity.verification.application.OtpRateLimitExceededException;
import dev.amg.payguard.identity.verification.application.OtpVerificationException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OtpExceptionHandler {

  @ExceptionHandler(OtpChallengeNotFoundException.class)
  ResponseEntity<ErrorResponse> notFound(OtpChallengeNotFoundException exception) {
    return response(HttpStatus.NOT_FOUND, "challenge_not_found");
  }

  @ExceptionHandler(OtpRateLimitExceededException.class)
  ResponseEntity<ErrorResponse> rateLimited(OtpRateLimitExceededException exception) {
    return response(HttpStatus.TOO_MANY_REQUESTS, "rate_limit_exceeded");
  }

  @ExceptionHandler(OtpVerificationException.class)
  ResponseEntity<ErrorResponse> verificationFailed(OtpVerificationException exception) {
    HttpStatus status =
        switch (exception.result()) {
          case LOCKED -> HttpStatus.TOO_MANY_REQUESTS;
          case ALREADY_USED, INVALIDATED -> HttpStatus.CONFLICT;
          case EXPIRED -> HttpStatus.GONE;
          default -> HttpStatus.UNAUTHORIZED;
        };
    return response(status, exception.result().name().toLowerCase(java.util.Locale.ROOT));
  }

  private static ResponseEntity<ErrorResponse> response(HttpStatus status, String code) {
    return ResponseEntity.status(status).body(new ErrorResponse(code, Instant.now()));
  }

  @ExceptionHandler(infrastructure.vault.VaultUnavailableException.class)
  ResponseEntity<ErrorResponse> secretDependencyUnavailable(
      infrastructure.vault.VaultUnavailableException exception) {
    return response(HttpStatus.SERVICE_UNAVAILABLE, "secret_dependency_unavailable");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ErrorResponse> invalidInput(IllegalArgumentException exception) {
    return response(HttpStatus.BAD_REQUEST, "invalid_request");
  }

  record ErrorResponse(String code, Instant timestamp) {}
}
