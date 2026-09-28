package dev.amg.payguard.domain.exception;

import domain.DomainException;

public class InsufficientFundException extends DomainException {
  private static final long serialVersionUID = 1L;

  public InsufficientFundException() {
    this("Insufficient funds");
  }

  public InsufficientFundException(String message) {
    super(message);
  }

  @Override
  public String getErrorCode() {
    return "INSUFFICIENT_FUNDS";
  }

  @Override
  public String getTitle() {
    return "Insufficient funds";
  }

  @Override
  public int getHttpStatus() {
    return 422;
  }
}
