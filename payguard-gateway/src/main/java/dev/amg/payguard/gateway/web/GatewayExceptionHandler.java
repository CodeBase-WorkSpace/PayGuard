package dev.amg.payguard.gateway.web;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GatewayExceptionHandler {
  @ExceptionHandler(StatusRuntimeException.class)
  ResponseEntity<Problem> grpcFailure(StatusRuntimeException exception) {
    Status.Code code = exception.getStatus().getCode();
    HttpStatus status =
        switch (code) {
          case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
          case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
          case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
          case NOT_FOUND -> HttpStatus.NOT_FOUND;
          case ALREADY_EXISTS, ABORTED, FAILED_PRECONDITION -> HttpStatus.CONFLICT;
          case RESOURCE_EXHAUSTED -> HttpStatus.TOO_MANY_REQUESTS;
          case DEADLINE_EXCEEDED, UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
          default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    return ResponseEntity.status(status)
        .body(
            new Problem(
                "DOWNSTREAM_" + code.name(),
                safeMessage(exception),
                status.value(),
                Instant.now(),
                UUID.randomUUID().toString(),
                Map.of()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Problem> validation(MethodArgumentNotValidException exception) {
    var fields =
        exception.getBindingResult().getFieldErrors().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    FieldError::getField,
                    error -> String.valueOf(error.getDefaultMessage()),
                    (first, ignored) -> first));
    return ResponseEntity.badRequest()
        .body(
            new Problem(
                "VALIDATION_ERROR",
                "Request validation failed",
                400,
                Instant.now(),
                UUID.randomUUID().toString(),
                fields));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Problem> unexpected(Exception exception) {
    return ResponseEntity.internalServerError()
        .body(
            new Problem(
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                500,
                Instant.now(),
                UUID.randomUUID().toString(),
                Map.of()));
  }

  private String safeMessage(StatusRuntimeException exception) {
    String description = exception.getStatus().getDescription();
    return description == null || description.isBlank()
        ? "Downstream service request failed"
        : description;
  }

  public record Problem(
      String code,
      String message,
      int status,
      Instant timestamp,
      String correlationId,
      Map<String, String> fieldErrors) {}
}
