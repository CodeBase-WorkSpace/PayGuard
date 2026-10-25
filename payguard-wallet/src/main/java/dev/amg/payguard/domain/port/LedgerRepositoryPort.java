package dev.amg.payguard.domain.port;

import dev.amg.payguard.domain.model.LedgerEntry;
import dev.amg.payguard.domain.value_object.AccountId;
import dev.amg.payguard.domain.value_object.Money;
import java.util.Currency;
import java.util.List;

public interface LedgerRepositoryPort {
  void saveEntries(List<LedgerEntry> entries);

  Money calculateBalance(AccountId accountId, Currency currency);

  long getNextSequenceNumber(AccountId accountId);
}
