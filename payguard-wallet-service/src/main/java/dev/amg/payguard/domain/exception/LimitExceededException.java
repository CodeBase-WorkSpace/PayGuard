package dev.amg.payguard.domain.exception;

import domain.DomainException;

public class LimitExceededException extends DomainException {
  private static final long serialVersionUID = 1L;

  public LimitExceededException() {
    this("Wallet limit exceeded");
  }

  public LimitExceededException(String message) {
    super(message);
  }

  @Override
  public String getErrorCode() {
    return "LIMIT_EXCEEDED";
  }

  @Override
  public String getTitle() {
    return "Limit exceeded";
  }

  @Override
  public int getHttpStatus() {
    return 409;
  }
}
