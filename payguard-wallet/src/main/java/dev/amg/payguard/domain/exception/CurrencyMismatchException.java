package dev.amg.payguard.domain.exception;

import domain.DomainException;

public class CurrencyMismatchException extends DomainException {
  private static final long serialVersionUID = 1L;

  public CurrencyMismatchException() {
    this("Currency mismatch");
  }

  public CurrencyMismatchException(String message) {
    super(message);
  }

  @Override
  public String getErrorCode() {
    return "CURRENCY_MISMATCH";
  }

  @Override
  public String getTitle() {
    return "Currency mismatch";
  }

  @Override
  public int getHttpStatus() {
    return 400;
  }
}
