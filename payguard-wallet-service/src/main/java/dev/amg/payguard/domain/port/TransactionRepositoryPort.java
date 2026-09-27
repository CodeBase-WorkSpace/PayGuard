package dev.amg.payguard.domain.port;

import dev.amg.payguard.domain.model.Transaction;
import dev.amg.payguard.domain.value_object.IdempotencyKey;
import dev.amg.payguard.domain.value_object.TransactionId;
import java.util.Optional;

public interface TransactionRepositoryPort {
  Optional<Transaction> findById(TransactionId id);

  Optional<Transaction> findByIdempotencyKey(IdempotencyKey key);

  Transaction save(Transaction transaction);
}
