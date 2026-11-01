package dev.amg.payguard.loan.application;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletSettlementPort {
  void disburse(
      UUID loanId, String walletId, BigDecimal amount, String currency, String idempotencyKey);

  void debit(
      UUID loanId, String walletId, BigDecimal amount, String currency, String idempotencyKey);
}
