package dev.amg.payguard.loan.application;

import dev.amg.payguard.loan.domain.Loan;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

public final class LoanApplicationService {
  private final LoanRepository repository;
  private final WalletSettlementPort wallet;
  private final Clock clock;

  public LoanApplicationService(
      LoanRepository repository, WalletSettlementPort wallet, Clock clock) {
    this.repository = repository;
    this.wallet = wallet;
    this.clock = clock;
  }

  public Loan originate(
      String borrowerId,
      UUID collateralId,
      BigDecimal principal,
      BigDecimal rate,
      int term,
      LocalDate date) {
    Loan loan =
        Loan.originate(UUID.randomUUID(), borrowerId, collateralId, principal, rate, term, date);
    return repository.save(loan);
  }

  public Loan activate(UUID id, String walletId, String currency, String key) {
    Loan loan = repository.find(id).orElseThrow();
    loan.activate();
    wallet.disburse(id, walletId, loan.principal(), currency, key);
    return repository.save(loan);
  }

  public Loan repay(UUID id, String walletId, BigDecimal amount, String currency, String key) {
    Loan loan = repository.find(id).orElseThrow();
    loan.accrueUntil(LocalDate.now(clock));
    wallet.debit(id, walletId, amount, currency, key);
    loan.repay(amount, clock);
    return repository.save(loan);
  }
}
