package dev.amg.payguard.domain.exception;

import domain.DomainException;

public class WalletFrozenException extends DomainException {
  private static final long serialVersionUID = 1L;

  public WalletFrozenException() {
    this("Wallet is frozen");
  }

  public WalletFrozenException(String message) {
    super(message);
  }

  @Override
  public String getErrorCode() {
    return "WALLET_FROZEN";
  }

  @Override
  public String getTitle() {
    return "Wallet frozen";
  }

  @Override
  public int getHttpStatus() {
    return 409;
  }
}
