package dev.amg.payguard.domain.exception;

import domain.DomainException;

public class HoldExpiredException extends DomainException {
  private static final long serialVersionUID = 1L;

  public HoldExpiredException() {
    this("Hold has expired");
  }

  public HoldExpiredException(String message) {
    super(message);
  }

  @Override
  public String getErrorCode() {
    return "HOLD_EXPIRED";
  }

  @Override
  public String getTitle() {
    return "Hold expired";
  }

  @Override
  public int getHttpStatus() {
    return 409;
  }
}
