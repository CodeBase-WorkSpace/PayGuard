package dev.amg.payguard.domain.exception;

import domain.DomainException;

public class DoubleEntryImbalanceException extends DomainException {
  private static final long serialVersionUID = 1L;

  public DoubleEntryImbalanceException() {
    this("Double-entry transaction is imbalanced");
  }

  public DoubleEntryImbalanceException(String message) {
    super(message);
  }

  @Override
  public String getErrorCode() {
    return "DOUBLE_ENTRY_IMBALANCE";
  }

  @Override
  public String getTitle() {
    return "Double-entry imbalance";
  }

  @Override
  public int getHttpStatus() {
    return 400;
  }
}
