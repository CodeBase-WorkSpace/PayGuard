package dev.amg.payguard.domain.exception;

import domain.DomainException;

public class NegativeAmountNotAllowedException extends DomainException {
  private static final long serialVersionUID = 1L;

  public NegativeAmountNotAllowedException() {
    this("Negative amounts are not allowed");
  }

  public NegativeAmountNotAllowedException(String message) {
    super(message);
  }

  @Override
  public String getErrorCode() {
    return "NEGATIVE_AMOUNT_NOT_ALLOWED";
  }

  @Override
  public String getTitle() {
    return "Negative amount not allowed";
  }

  @Override
  public int getHttpStatus() {
    return 400;
  }
}
