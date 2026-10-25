package dev.amg.payguard.domain.exception;

import domain.DomainException;

public class InvalidWalletStateException extends DomainException {
  private static final long serialVersionUID = 1L;

  public InvalidWalletStateException() {
    this("Invalid wallet state");
  }

  public InvalidWalletStateException(String message) {
    super(message);
  }

  @Override
  public String getErrorCode() {
    return "INVALID_WALLET_STATE";
  }

  @Override
  public String getTitle() {
    return "Invalid wallet state";
  }

  @Override
  public int getHttpStatus() {
    return 409;
  }
}
