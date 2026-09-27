package dev.amg.payguard.loan.application;

import java.util.Map;
import java.util.UUID;

public interface LoanDataExportPort {
  Map<String, Object> export(UUID loanId);

  void pseudonymize(UUID loanId, String retainedToken);
}
