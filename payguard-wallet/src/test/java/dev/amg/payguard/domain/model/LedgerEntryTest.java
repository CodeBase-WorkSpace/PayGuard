package dev.amg.payguard.domain.model;

import static dev.amg.payguard.domain.model.DomainFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.amg.payguard.domain.enums.*;
import dev.amg.payguard.domain.event.*;
import dev.amg.payguard.domain.exception.*;
import dev.amg.payguard.domain.value_object.*;
import domain.InvalidDomainArgumentException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests posting-local invariants and explicit entity equality. */
class LedgerEntryTest {

  /** Preserves all entry-local posting data without transformation. */
  @Test
  void shouldPreserveAllPostingComponents() {
    LedgerEntry entry = walletEntry(LedgerEntryType.DEBIT, "10.00", 10);
    assertAll(() -> assertEquals(TRANSACTION_ID, entry.transactionId()),
        () -> assertEquals(ACCOUNT_ID, entry.accountId()),
        () -> assertEquals(money("10.00"), entry.amount()),
        () -> assertEquals(LedgerEntryType.DEBIT, entry.entryType()),
        () -> assertEquals(10L, entry.sequenceNumber()),
        () -> assertEquals(NOW, entry.timestamp()));
  }

  /** Rejects a null Id before a valid entry can exist. */
  @Test
  void shouldRejectNullId() {
    assertThrows(InvalidDomainArgumentException.class, () -> new LedgerEntry(null, TRANSACTION_ID, ACCOUNT_ID, money("1"), LedgerEntryType.DEBIT, 0, NOW));
  }

  /** Rejects a null TransactionId before a valid entry can exist. */
  @Test
  void shouldRejectNullTransactionId() {
    assertThrows(InvalidDomainArgumentException.class, () -> new LedgerEntry(new LedgerEntryId(uuid(100)), null, ACCOUNT_ID, money("1"), LedgerEntryType.DEBIT, 0, NOW));
  }

  /** Rejects a null AccountId before a valid entry can exist. */
  @Test
  void shouldRejectNullAccountId() {
    assertThrows(InvalidDomainArgumentException.class, () -> new LedgerEntry(new LedgerEntryId(uuid(100)), TRANSACTION_ID, null, money("1"), LedgerEntryType.DEBIT, 0, NOW));
  }

  /** Rejects a null Amount before a valid entry can exist. */
  @Test
  void shouldRejectNullAmount() {
    assertThrows(InvalidDomainArgumentException.class, () -> new LedgerEntry(new LedgerEntryId(uuid(100)), TRANSACTION_ID, ACCOUNT_ID, null, LedgerEntryType.DEBIT, 0, NOW));
  }

  /** Rejects a null EntryType before a valid entry can exist. */
  @Test
  void shouldRejectNullEntryType() {
    assertThrows(InvalidDomainArgumentException.class, () -> new LedgerEntry(new LedgerEntryId(uuid(100)), TRANSACTION_ID, ACCOUNT_ID, money("1"), null, 0, NOW));
  }

  /** Rejects a null Timestamp before a valid entry can exist. */
  @Test
  void shouldRejectNullTimestamp() {
    assertThrows(InvalidDomainArgumentException.class, () -> new LedgerEntry(new LedgerEntryId(uuid(100)), TRANSACTION_ID, ACCOUNT_ID, money("1"), LedgerEntryType.DEBIT, 0, null));
  }

  /** Rejects zero-magnitude entries rather than posting meaningless ledger lines. */
  @Test
  void shouldRejectZeroPosting() {
    assertThrows(InvalidWalletStateException.class, () -> walletEntry(LedgerEntryType.DEBIT, "0", 10));
  }

  /** Rejects signed amounts because direction belongs to the entry type. */
  @Test
  void shouldRejectNegativePosting() {
    assertThrows(NegativeAmountNotAllowedException.class, () -> walletEntry(LedgerEntryType.DEBIT, "-1", 10));
  }

  /** Rejects invalid account sequence values. */
  @Test
  void shouldRejectNegativeSequence() {
    assertThrows(InvalidWalletStateException.class, () -> walletEntry(LedgerEntryType.CREDIT, "1", -1));
  }

  /** Accepts both ends of the non-negative sequence domain. */
  @Test
  void shouldAcceptZeroAndMaximumSequence() {
    assertEquals(0L, walletEntry(LedgerEntryType.DEBIT, "1", 0).sequenceNumber());
    assertEquals(Long.MAX_VALUE, walletEntry(LedgerEntryType.DEBIT, "1", Long.MAX_VALUE).sequenceNumber());
  }

  /** Preserves the existing entity-by-id equality contract, not content equivalence. */
  @Test
  void shouldCompareByIdentity() {
    LedgerEntry first = walletEntry(LedgerEntryType.DEBIT, "1", 10);
    LedgerEntry second = new LedgerEntry(first.id(), TRANSACTION_ID, ACCOUNT_ID,
        money("2"), LedgerEntryType.CREDIT, 11, NOW);
    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertNotEquals(first, walletEntry(LedgerEntryType.DEBIT, "1", 11));
    assertNotEquals(first, null);
  }
}
