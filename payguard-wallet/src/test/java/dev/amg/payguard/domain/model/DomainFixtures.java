package dev.amg.payguard.domain.model;

import dev.amg.payguard.domain.enums.*;
import dev.amg.payguard.domain.value_object.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/** Deterministic, test-only domain fixtures; no system clock, Spring or database. */
final class DomainFixtures {
  static final Currency CHF = Currency.getInstance("CHF");
  static final Currency USD = Currency.getInstance("USD");
  static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
  static final Instant NOW = CREATED.plusSeconds(10);
  static final Instant DEADLINE = CREATED.plusSeconds(60);
  static final WalletId WALLET_ID = new WalletId(uuid(1));
  static final AccountId ACCOUNT_ID = new AccountId(uuid(2));
  static final AccountId DESTINATION_ID = new AccountId(uuid(3));
  static final AccountId FEE_ACCOUNT_ID = new AccountId(uuid(4));
  static final UserId USER_ID = new UserId(uuid(5));
  static final HoldId HOLD_ID = new HoldId(uuid(6));
  static final TransactionId TRANSACTION_ID = new TransactionId(uuid(7));

  private DomainFixtures() {}

  /** Creates reproducible, distinct identifiers without relying on random collisions. */
  static UUID uuid(long value) { return new UUID(0L, value); }
  /** Creates exact CHF money. */
  static Money money(String value) { return Money.of(value, CHF); }
  /** Restores a funded active wallet with a known loaded version and posting high-water mark. */
  static Wallet wallet() { return wallet(WalletStatus.ACTIVE, "100.00", "0.00"); }
  /** Restores a requested wallet state for focused invariant tests. */
  static Wallet wallet(WalletStatus status, String book, String reserved) {
    return Wallet.restore(WALLET_ID, ACCOUNT_ID, USER_ID, CHF, status,
        money(book), money(reserved), 7L, 9L, CREATED, CREATED);
  }
  /** Creates a positive full-amount reservation with a deterministic deadline. */
  static Hold hold() {
    return Hold.createNew(HOLD_ID, WALLET_ID, money("30.00"), "Purchase", "order-1", DEADLINE, CREATED);
  }
  /** Creates an initiated transfer with an additional source-paid fee. */
  static Transaction transaction(String principal, String fee) {
    return Transaction.initiate(TRANSACTION_ID, IdempotencyKey.of("request-1"),
        TransactionType.P2P_TRANSFER, ACCOUNT_ID, DESTINATION_ID,
        money(principal), money(fee), CREATED);
  }
  /** Creates one independently identified posting. */
  static LedgerEntry entry(long entryId, TransactionId transactionId, AccountId accountId,
      Money amount, LedgerEntryType type, long sequence, Instant timestamp) {
    return new LedgerEntry(new LedgerEntryId(uuid(entryId)), transactionId, accountId,
        amount, type, sequence, timestamp);
  }
  /** Creates a posting for the standard wallet and transaction. */
  static LedgerEntry walletEntry(LedgerEntryType type, String amount, long sequence) {
    return entry(100 + sequence, TRANSACTION_ID, ACCOUNT_ID, money(amount), type, sequence, NOW);
  }
  /** Creates a balanced batch obeying the principal/fee allocation policy. */
  static List<LedgerEntry> postings(Transaction transaction) {
    List<LedgerEntry> entries = new ArrayList<>();
    Instant timestamp = transaction.getCreatedAt().plusSeconds(1);
    entries.add(entry(100, transaction.getId(), transaction.getSourceAccountId(),
        transaction.getTotalAmount().add(transaction.getFeeAmount()), LedgerEntryType.DEBIT, 10, timestamp));
    entries.add(entry(101, transaction.getId(), transaction.getDestinationAccountId(),
        transaction.getTotalAmount(), LedgerEntryType.CREDIT, 10, timestamp));
    if (!transaction.getFeeAmount().isZero()) {
      entries.add(entry(102, transaction.getId(), FEE_ACCOUNT_ID, transaction.getFeeAmount(),
          LedgerEntryType.CREDIT, 10, timestamp));
    }
    return entries;
  }
  /** Creates and settles a zero-fee original transaction for reversal tests. */
  static Transaction settledTransaction() {
    Transaction transaction = transaction("25.00", "0.00");
    transaction.markSettled(postings(transaction), NOW);
    return transaction;
  }
  /** Creates a distinct, fully settled compensating refund. */
  static Transaction refund(Transaction original) {
    Transaction refund = Transaction.initiate(new TransactionId(uuid(20)),
        IdempotencyKey.of("refund-1"), TransactionType.REFUND,
        original.getDestinationAccountId(), original.getSourceAccountId(),
        original.getTotalAmount(), money("0.00"), NOW.plusSeconds(1));
    refund.markSettled(postings(refund), NOW.plusSeconds(3));
    return refund;
  }
  /** Captures all mutable wallet state relevant to rejection atomicity. */
  static WalletSnapshot snapshot(Wallet wallet) {
    return new WalletSnapshot(wallet.getStatus(), wallet.getBookBalance(),
        wallet.getReservedBalance(), wallet.getUpdatedAt(), wallet.getVersion(),
        wallet.getLastAppliedSequenceNumber());
  }
  /** Immutable test snapshot for exact before/after comparison. */
  record WalletSnapshot(WalletStatus status, Money book, Money reserved, Instant updated,
      long version, long sequence) {}
}
