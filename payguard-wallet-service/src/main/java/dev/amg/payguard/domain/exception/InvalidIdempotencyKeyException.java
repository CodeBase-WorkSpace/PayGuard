package dev.amg.payguard.domain.exception;

import domain.DomainException;

public final class InvalidIdempotencyKeyException extends DomainException {
  private static final long serialVersionUID = 1L;

  public InvalidIdempotencyKeyException(String message) {
    super(message);
  }

  @Override
  public String getErrorCode() {
    return "INVALID_IDEMPOTENCY_KEY";
  }

  @Override
  public String getTitle() {
    return "Invalid idempotency key";
  }

  @Override
  public int getHttpStatus() {
    return 400;
  }
}
