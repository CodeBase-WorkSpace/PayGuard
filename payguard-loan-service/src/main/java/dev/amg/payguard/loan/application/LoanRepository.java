package dev.amg.payguard.loan.application;

import dev.amg.payguard.loan.domain.Loan;
import java.util.Optional;
import java.util.UUID;

public interface LoanRepository {
  Loan save(Loan loan);

  Optional<Loan> find(UUID id);
}
