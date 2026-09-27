package dev.amg.payguard.domain.exception;

import domain.DomainException;

public class TransactionAlreadySettledException extends DomainException {
  private static final long serialVersionUID = 1L;

  public TransactionAlreadySettledException() {
    this("Transaction has already been settled");
  }

  public TransactionAlreadySettledException(String message) {
    super(message);
  }

  @Override
  public String getErrorCode() {
    return "TRANSACTION_ALREADY_SETTLED";
  }

  @Override
  public String getTitle() {
    return "Transaction already settled";
  }

  @Override
  public int getHttpStatus() {
    return 409;
  }
}
