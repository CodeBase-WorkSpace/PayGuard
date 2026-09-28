package dev.amg.payguard.domain.value_object;

import domain.DomainValidation;
import domain.ValueObject;
import java.util.UUID;

public record WalletId(UUID value) implements ValueObject {
  public WalletId {
    DomainValidation.requireNonNull(value, "WalletId value");
  }

  public static WalletId generate() {
    return new WalletId(UUID.randomUUID());
  }

  public static WalletId fromString(String uuidString) {
    return new WalletId(UUID.fromString(uuidString));
  }

  public AccountId toAccountId() {
    return new AccountId(this.value);
  }

  @Override
  public boolean sameValueAs(ValueObject other) {
    return equals(other);
  }
}
