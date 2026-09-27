package dev.amg.payguard.domain.exception;

import domain.DomainException;

public class InvalidHoldStateException extends DomainException {
  private static final long serialVersionUID = 1L;

  public InvalidHoldStateException() {
    this("Invalid hold state");
  }

  public InvalidHoldStateException(String message) {
    super(message);
  }

  @Override
  public String getErrorCode() {
    return "INVALID_HOLD_STATE";
  }

  @Override
  public String getTitle() {
    return "Invalid hold state";
  }

  @Override
  public int getHttpStatus() {
    return 409;
  }
}
