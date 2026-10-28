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

/** Tests exact posting economics, terminal states, reversals and rejection atomicity. */
class TransactionTest {

  /** Creates an open transfer with no settlement or failure metadata. */
  @Test
  void shouldInitiateConsistentTransaction() {
    Transaction transaction = transaction("100", "2");
    assertEquals(TransactionStatus.INITIATED, transaction.getStatus());
    assertEquals(TRANSACTION_ID, transaction.getId());
    assertEquals(ACCOUNT_ID, transaction.getSourceAccountId());
    assertEquals(DESTINATION_ID, transaction.getDestinationAccountId());
    assertEquals(money("100"), transaction.getTotalAmount());
    assertEquals(money("2"), transaction.getFeeAmount());
    assertTrue(transaction.getFailureReason().isEmpty());
    assertTrue(transaction.getSettledAt().isEmpty());
    assertTrue(transaction.getReversalTransactionId().isEmpty());
    assertTrue(transaction.getReversedAt().isEmpty());
  }

  /** Settles a balanced principal-only posting. */
  @Test
  void shouldSettleBalancedPosting() {
    Transaction transaction = transaction("100", "0");
    transaction.markSettled(postings(transaction), NOW);
    assertEquals(TransactionStatus.SUCCESSFUL, transaction.getStatus());
    assertEquals(NOW, transaction.getSettledAt().orElseThrow());
  }

  /** Enforces debit principal-plus-fee and separate principal/fee credits. */
  @Test
  void shouldSettlePrincipalAndAdditionalFee() {
    Transaction transaction = transaction("100", "2");
    transaction.markSettled(postings(transaction), NOW);
    assertEquals(TransactionStatus.SUCCESSFUL, transaction.getStatus());
  }

  /** Pending settlement is a permitted open-state transition. */
  @Test
  void shouldSettleFromPending() {
    Transaction transaction = transaction("100", "0");
    transaction.markPendingSettlement();
    assertEquals(TransactionStatus.PENDING_SETTLEMENT, transaction.getStatus());
    transaction.markSettled(postings(transaction), NOW);
    assertEquals(TransactionStatus.SUCCESSFUL, transaction.getStatus());
  }

  /** Does not silently repeat lifecycle transitions. */
  @Test
  void shouldRejectRepeatedPendingTransition() {
    Transaction transaction = transaction("100", "0");
    transaction.markPendingSettlement();
    assertThrows(InvalidWalletStateException.class, transaction::markPendingSettlement);
  }

  /** Rejects empty money movements. */
  @Test
  void shouldRejectZeroPrincipal() {
    assertThrows(InvalidWalletStateException.class, () -> transaction("0", "0"));
  }

  /** Rejects negative transfer magnitudes. */
  @Test
  void shouldRejectNegativePrincipal() {
    assertThrows(NegativeAmountNotAllowedException.class, () -> transaction("-1", "0"));
  }

  /** Fees cannot be negative adjustments. */
  @Test
  void shouldRejectNegativeFee() {
    assertThrows(NegativeAmountNotAllowedException.class, () -> transaction("1", "-1"));
  }

  /** Even a zero fee must carry the principal currency. */
  @Test
  void shouldRejectMixedFeeCurrency() {
    assertThrows(CurrencyMismatchException.class, () -> Transaction.initiate(TRANSACTION_ID,
        IdempotencyKey.of("key"), TransactionType.P2P_TRANSFER, ACCOUNT_ID, DESTINATION_ID,
        money("1"), Money.zero(USD), CREATED));
  }

  /** Source and destination are distinct under the selected transfer contract. */
  @Test
  void shouldRejectSelfTransfer() {
    assertThrows(InvalidWalletStateException.class, () -> Transaction.initiate(TRANSACTION_ID,
        IdempotencyKey.of("key"), TransactionType.P2P_TRANSFER, ACCOUNT_ID, ACCOUNT_ID,
        money("1"), money("0"), CREATED));
  }

  /** Regression: a null time cannot leave status marked successful. */
  @Test
  void shouldRejectNullSettlementTimeWithoutMutation() {
    Transaction transaction = transaction("100", "0");
    assertThrows(InvalidDomainArgumentException.class, () -> transaction.markSettled(postings(transaction), null));
    assertEquals(TransactionStatus.INITIATED, transaction.getStatus());
    assertTrue(transaction.getSettledAt().isEmpty());
  }

  /** Regression: validation must precede FAILED status assignment. */
  @Test
  void shouldRejectInvalidFailureReasonWithoutMutation() {
    Transaction transaction = transaction("100", "0");
    assertThrows(InvalidDomainArgumentException.class, () -> transaction.markFailed(null));
    assertThrows(InvalidDomainArgumentException.class, () -> transaction.markFailed(" "));
    assertEquals(TransactionStatus.INITIATED, transaction.getStatus());
    assertTrue(transaction.getFailureReason().isEmpty());
  }

  /** A failed transfer cannot settle, fail again or become pending. */
  @Test
  void shouldMakeFailedStateTerminal() {
    Transaction transaction = transaction("100", "0");
    transaction.markFailed("Rejected by risk policy");
    assertEquals("Rejected by risk policy", transaction.getFailureReason().orElseThrow());
    assertThrows(InvalidWalletStateException.class, () -> transaction.markSettled(postings(transaction), NOW));
    assertThrows(InvalidWalletStateException.class, () -> transaction.markFailed("replace"));
    assertThrows(InvalidWalletStateException.class, transaction::markPendingSettlement);
  }

  /** A successful transfer cannot settle or fail again. */
  @Test
  void shouldPreventSecondSettlement() {
    Transaction transaction = settledTransaction();
    assertThrows(TransactionAlreadySettledException.class, () -> transaction.markSettled(postings(transaction), NOW));
    assertThrows(TransactionAlreadySettledException.class, () -> transaction.markFailed("late failure"));
  }

  /** Rejects a missing posting batch before settlement. */
  @Test
  void shouldRejectNullPostingBatch() {
    Transaction transaction = transaction("100", "0");
    assertThrows(InvalidDomainArgumentException.class, () -> transaction.markSettled(null, NOW));
    assertEquals(TransactionStatus.INITIATED, transaction.getStatus());
  }

  /** Rejects null entries with the established validation contract. */
  @Test
  void shouldRejectNullEntry() {
    Transaction transaction = transaction("100", "0");
    List<LedgerEntry> entries = new ArrayList<>(postings(transaction));
    entries.add(null);
    assertThrows(InvalidDomainArgumentException.class, () -> transaction.markSettled(entries, NOW));
  }

  /** A single entry cannot be a balanced financial posting. */
  @Test
  void shouldRejectEmptyOrSingleEntryBatch() {
    Transaction transaction = transaction("100", "0");
    assertThrows(DoubleEntryImbalanceException.class, () -> transaction.markSettled(List.of(), NOW));
    assertThrows(DoubleEntryImbalanceException.class, () -> transaction.markSettled(List.of(postings(transaction).getFirst()), NOW));
  }

  /** Unbalanced totals must not mark the transaction successful. */
  @Test
  void shouldRejectUnbalancedBatchWithoutMutation() {
    Transaction transaction = transaction("100", "0");
    List<LedgerEntry> entries = List.of(postings(transaction).getFirst(),
        entry(101, TRANSACTION_ID, DESTINATION_ID, money("99.99"), LedgerEntryType.CREDIT, 10, NOW));
    assertThrows(DoubleEntryImbalanceException.class, () -> transaction.markSettled(entries, NOW));
    assertEquals(TransactionStatus.INITIATED, transaction.getStatus());
    assertTrue(transaction.getSettledAt().isEmpty());
  }

  /** Balance alone is insufficient when posting amounts differ from the command. */
  @Test
  void shouldRejectBalancedButIncorrectPrincipal() {
    Transaction transaction = transaction("100", "0");
    List<LedgerEntry> entries = List.of(
        entry(100, TRANSACTION_ID, ACCOUNT_ID, money("99"), LedgerEntryType.DEBIT, 10, NOW),
        entry(101, TRANSACTION_ID, DESTINATION_ID, money("99"), LedgerEntryType.CREDIT, 10, NOW));
    assertThrows(InvalidWalletStateException.class, () -> transaction.markSettled(entries, NOW));
  }

  /** A balanced batch must not credit the fee to the principal destination. */
  @Test
  void shouldRejectWrongFeeAllocation() {
    Transaction transaction = transaction("100", "2");
    List<LedgerEntry> entries = List.of(postings(transaction).getFirst(),
        entry(101, TRANSACTION_ID, DESTINATION_ID, money("102"), LedgerEntryType.CREDIT, 10, NOW));
    assertThrows(InvalidWalletStateException.class, () -> transaction.markSettled(entries, NOW));
  }

  /** Supports an explicitly split fee across distinct fee accounts. */
  @Test
  void shouldAcceptSplitFeeCredits() {
    Transaction transaction = transaction("100", "2");
    List<LedgerEntry> entries = new ArrayList<>(postings(transaction).subList(0, 2));
    entries.add(entry(102, TRANSACTION_ID, FEE_ACCOUNT_ID, money("1"), LedgerEntryType.CREDIT, 10, NOW));
    entries.add(entry(103, TRANSACTION_ID, new AccountId(uuid(40)), money("1"), LedgerEntryType.CREDIT, 10, NOW));
    transaction.markSettled(entries, NOW);
    assertEquals(TransactionStatus.SUCCESSFUL, transaction.getStatus());
  }

  /** Rejects duplicate identities even when entry content differs. */
  @Test
  void shouldRejectDuplicateEntryIdentity() {
    Transaction transaction = transaction("100", "0");
    LedgerEntry debit = postings(transaction).getFirst();
    LedgerEntry credit = new LedgerEntry(debit.id(), TRANSACTION_ID, DESTINATION_ID,
        money("100"), LedgerEntryType.CREDIT, 10, NOW);
    assertThrows(InvalidWalletStateException.class, () -> transaction.markSettled(List.of(debit, credit), NOW));
  }

  /** Two distinct entries cannot claim the same sequence in one account. */
  @Test
  void shouldRejectDuplicateAccountSequence() {
    Transaction transaction = transaction("100", "0");
    List<LedgerEntry> entries = List.of(
        entry(100, TRANSACTION_ID, ACCOUNT_ID, money("40"), LedgerEntryType.DEBIT, 10, NOW),
        entry(102, TRANSACTION_ID, ACCOUNT_ID, money("60"), LedgerEntryType.DEBIT, 10, NOW),
        postings(transaction).get(1));
    assertThrows(InvalidWalletStateException.class, () -> transaction.markSettled(entries, NOW));
  }

  /** Every line must belong to this transaction. */
  @Test
  void shouldRejectForeignTransactionEntry() {
    Transaction transaction = transaction("100", "0");
    List<LedgerEntry> entries = List.of(postings(transaction).getFirst(),
        entry(101, new TransactionId(uuid(99)), DESTINATION_ID, money("100"), LedgerEntryType.CREDIT, 10, NOW));
    assertThrows(InvalidWalletStateException.class, () -> transaction.markSettled(entries, NOW));
  }

  /** Rejects mixed-currency ledger lines instead of summing them numerically. */
  @Test
  void shouldRejectPostingCurrencyMismatch() {
    Transaction transaction = transaction("100", "0");
    List<LedgerEntry> entries = List.of(postings(transaction).getFirst(),
        entry(101, TRANSACTION_ID, DESTINATION_ID, Money.of("100", USD), LedgerEntryType.CREDIT, 10, NOW));
    assertThrows(CurrencyMismatchException.class, () -> transaction.markSettled(entries, NOW));
  }

  /** A balanced posting cannot charge an unrequested source account. */
  @Test
  void shouldRejectDebitFromDifferentAccount() {
    Transaction transaction = transaction("100", "0");
    List<LedgerEntry> entries = List.of(
        entry(100, TRANSACTION_ID, FEE_ACCOUNT_ID, money("100"), LedgerEntryType.DEBIT, 10, NOW),
        postings(transaction).get(1));
    assertThrows(InvalidWalletStateException.class, () -> transaction.markSettled(entries, NOW));
  }

  /** Does not allow a debit/credit loop to masquerade as a transfer. */
  @Test
  void shouldRejectCreditBackToSource() {
    Transaction transaction = transaction("100", "0");
    List<LedgerEntry> entries = List.of(postings(transaction).getFirst(),
        entry(101, TRANSACTION_ID, ACCOUNT_ID, money("100"), LedgerEntryType.CREDIT, 11, NOW));
    assertThrows(InvalidWalletStateException.class, () -> transaction.markSettled(entries, NOW));
  }

  /** A preflight validation is not a settlement operation. */
  @Test
  void shouldValidatePostingWithoutMutatingState() {
    Transaction transaction = transaction("100", "0");
    transaction.validatePosting(postings(transaction), NOW);
    assertEquals(TransactionStatus.INITIATED, transaction.getStatus());
    assertTrue(transaction.getSettledAt().isEmpty());
  }

  /** Reversal retains original settlement and records the compensating identity. */
  @Test
  void shouldLinkSuccessfulCompensatingRefund() {
    Transaction original = settledTransaction();
    Transaction refund = refund(original);
    original.markReversed(refund);
    assertEquals(TransactionStatus.REVERSED, original.getStatus());
    assertEquals(NOW, original.getSettledAt().orElseThrow());
    assertEquals(refund.getId(), original.getReversalTransactionId().orElseThrow());
    assertEquals(refund.getSettledAt(), original.getReversedAt());
  }

  /** Regression: reversed transactions must not be settled or failed again. */
  @Test
  void shouldKeepReversedStateTerminal() {
    Transaction original = settledTransaction();
    Transaction refund = refund(original);
    original.markReversed(refund);
    assertThrows(InvalidWalletStateException.class, () -> original.markSettled(postings(original), NOW));
    assertThrows(InvalidWalletStateException.class, () -> original.markFailed("late"));
    assertThrows(InvalidWalletStateException.class, () -> original.markReversed(refund));
    assertThrows(InvalidWalletStateException.class, original::markPendingSettlement);
    assertEquals(TransactionStatus.REVERSED, original.getStatus());
  }

  /** A transaction cannot be its own compensating financial movement. */
  @Test
  void shouldRejectSelfReversal() {
    Transaction transaction = settledTransaction();
    assertThrows(InvalidWalletStateException.class, () -> transaction.markReversed(transaction));
    assertEquals(TransactionStatus.SUCCESSFUL, transaction.getStatus());
  }

  /** Fails closed rather than silently discarding original fee economics. */
  @Test
  void shouldRejectFeeBearingReversalUntilPolicyExists() {
    Transaction transaction = transaction("25", "1");
    transaction.markSettled(postings(transaction), NOW);
    Transaction refund = refund(transaction);
    assertThrows(InvalidWalletStateException.class, () -> transaction.markReversed(refund));
    assertEquals(TransactionStatus.SUCCESSFUL, transaction.getStatus());
  }

  /** An initiated refund cannot establish reversal of settled money. */
  @Test
  void shouldRejectUnsettledRefund() {
    Transaction original = settledTransaction();
    Transaction refund = Transaction.initiate(new TransactionId(uuid(20)), IdempotencyKey.of("refund"),
        TransactionType.REFUND, DESTINATION_ID, ACCOUNT_ID, money("25"), money("0"), NOW);
    assertThrows(InvalidWalletStateException.class, () -> original.markReversed(refund));
  }

  /** A partial refund cannot be marked as a full reversal. */
  @Test
  void shouldRejectWrongRefundAmount() {
    Transaction original = settledTransaction();
    Transaction refund = Transaction.initiate(new TransactionId(uuid(20)), IdempotencyKey.of("refund"),
        TransactionType.REFUND, DESTINATION_ID, ACCOUNT_ID, money("24"), money("0"), NOW);
    refund.markSettled(postings(refund), NOW.plusSeconds(2));
    assertThrows(InvalidWalletStateException.class, () -> original.markReversed(refund));
  }

  /** A compensating movement cannot predate the original settlement. */
  @Test
  void shouldRejectRefundCreatedBeforeOriginalSettlement() {
    Transaction original = settledTransaction();
    Transaction refund = Transaction.initiate(new TransactionId(uuid(20)), IdempotencyKey.of("refund"),
        TransactionType.REFUND, DESTINATION_ID, ACCOUNT_ID, money("25"), money("0"), CREATED);
    refund.markSettled(postings(refund), NOW.plusSeconds(2));
    assertThrows(InvalidWalletStateException.class, () -> original.markReversed(refund));
  }

  /** Rejects postings outside the transaction interval. */
  @Test
  void shouldRejectFutureEntryTime() {
    Transaction transaction = transaction("100", "0");
    List<LedgerEntry> entries = List.of(postings(transaction).getFirst(),
        entry(101, TRANSACTION_ID, DESTINATION_ID, money("100"), LedgerEntryType.CREDIT, 10, NOW.plusNanos(1)));
    assertThrows(InvalidWalletStateException.class, () -> transaction.markSettled(entries, NOW));
  }

  /** Rejects postings outside the transaction interval. */
  @Test
  void shouldRejectPreCreationEntryTime() {
    Transaction transaction = transaction("100", "0");
    List<LedgerEntry> entries = List.of(postings(transaction).getFirst(),
        entry(101, TRANSACTION_ID, DESTINATION_ID, money("100"), LedgerEntryType.CREDIT, 10, CREATED.minusNanos(1)));
    assertThrows(InvalidWalletStateException.class, () -> transaction.markSettled(entries, NOW));
  }

  /** Rejects inconsistent status metadata during restoration. */
  @Test
  void shouldRejectOpenWithSettlementSnapshot() {
    assertThrows(InvalidWalletStateException.class, () -> Transaction.restore(TRANSACTION_ID, IdempotencyKey.of("key"),
        TransactionType.P2P_TRANSFER, ACCOUNT_ID, DESTINATION_ID, money("100"), money("0"),
        TransactionStatus.INITIATED, null, CREATED, NOW, null, null));
  }

  /** Rejects inconsistent status metadata during restoration. */
  @Test
  void shouldRejectPendingWithFailureSnapshot() {
    assertThrows(InvalidWalletStateException.class, () -> Transaction.restore(TRANSACTION_ID, IdempotencyKey.of("key"),
        TransactionType.P2P_TRANSFER, ACCOUNT_ID, DESTINATION_ID, money("100"), money("0"),
        TransactionStatus.PENDING_SETTLEMENT, "failure", CREATED, null, null, null));
  }

  /** Rejects inconsistent status metadata during restoration. */
  @Test
  void shouldRejectFailedWithoutReasonSnapshot() {
    assertThrows(InvalidDomainArgumentException.class, () -> Transaction.restore(TRANSACTION_ID, IdempotencyKey.of("key"),
        TransactionType.P2P_TRANSFER, ACCOUNT_ID, DESTINATION_ID, money("100"), money("0"),
        TransactionStatus.FAILED, null, CREATED, null, null, null));
  }

  /** Rejects inconsistent status metadata during restoration. */
  @Test
  void shouldRejectFailedWithSettlementSnapshot() {
    assertThrows(InvalidWalletStateException.class, () -> Transaction.restore(TRANSACTION_ID, IdempotencyKey.of("key"),
        TransactionType.P2P_TRANSFER, ACCOUNT_ID, DESTINATION_ID, money("100"), money("0"),
        TransactionStatus.FAILED, "failure", CREATED, NOW, null, null));
  }

  /** Rejects inconsistent status metadata during restoration. */
  @Test
  void shouldRejectSuccessfulWithoutSettlementSnapshot() {
    assertThrows(InvalidWalletStateException.class, () -> Transaction.restore(TRANSACTION_ID, IdempotencyKey.of("key"),
        TransactionType.P2P_TRANSFER, ACCOUNT_ID, DESTINATION_ID, money("100"), money("0"),
        TransactionStatus.SUCCESSFUL, null, CREATED, null, null, null));
  }

  /** Rejects inconsistent status metadata during restoration. */
  @Test
  void shouldRejectSuccessfulWithFailureSnapshot() {
    assertThrows(InvalidWalletStateException.class, () -> Transaction.restore(TRANSACTION_ID, IdempotencyKey.of("key"),
        TransactionType.P2P_TRANSFER, ACCOUNT_ID, DESTINATION_ID, money("100"), money("0"),
        TransactionStatus.SUCCESSFUL, "failure", CREATED, NOW, null, null));
  }

  /** Rejects inconsistent status metadata during restoration. */
  @Test
  void shouldRejectReversedWithoutReferenceSnapshot() {
    assertThrows(InvalidWalletStateException.class, () -> Transaction.restore(TRANSACTION_ID, IdempotencyKey.of("key"),
        TransactionType.P2P_TRANSFER, ACCOUNT_ID, DESTINATION_ID, money("100"), money("0"),
        TransactionStatus.REVERSED, null, CREATED, NOW, null, NOW));
  }

  /** Rejects inconsistent status metadata during restoration. */
  @Test
  void shouldRejectReversedWithSelfReferenceSnapshot() {
    assertThrows(InvalidWalletStateException.class, () -> Transaction.restore(TRANSACTION_ID, IdempotencyKey.of("key"),
        TransactionType.P2P_TRANSFER, ACCOUNT_ID, DESTINATION_ID, money("100"), money("0"),
        TransactionStatus.REVERSED, null, CREATED, NOW, TRANSACTION_ID, NOW));
  }

  /** Rejects inconsistent status metadata during restoration. */
  @Test
  void shouldRejectReversedWithEarlierTimeSnapshot() {
    assertThrows(InvalidWalletStateException.class, () -> Transaction.restore(TRANSACTION_ID, IdempotencyKey.of("key"),
        TransactionType.P2P_TRANSFER, ACCOUNT_ID, DESTINATION_ID, money("100"), money("0"),
        TransactionStatus.REVERSED, null, CREATED, NOW, new TransactionId(uuid(20)), CREATED));
  }

  /** Accepts a consistent stored state without transition events. */
  @Test
  void shouldRestoreInitiatedSnapshot() {
    Transaction transaction = Transaction.restore(TRANSACTION_ID, IdempotencyKey.of("key"),
        TransactionType.P2P_TRANSFER, ACCOUNT_ID, DESTINATION_ID, money("100"), money("0"),
        TransactionStatus.INITIATED, null, CREATED, null, null, null);
    assertEquals(TransactionStatus.INITIATED, transaction.getStatus());
    assertTrue(transaction.pullDomainEvents().isEmpty());
  }

  /** Accepts a consistent stored state without transition events. */
  @Test
  void shouldRestorePendingSettlementSnapshot() {
    Transaction transaction = Transaction.restore(TRANSACTION_ID, IdempotencyKey.of("key"),
        TransactionType.P2P_TRANSFER, ACCOUNT_ID, DESTINATION_ID, money("100"), money("0"),
        TransactionStatus.PENDING_SETTLEMENT, null, CREATED, null, null, null);
    assertEquals(TransactionStatus.PENDING_SETTLEMENT, transaction.getStatus());
    assertTrue(transaction.pullDomainEvents().isEmpty());
  }

  /** Accepts a consistent stored state without transition events. */
  @Test
  void shouldRestoreFailedSnapshot() {
    Transaction transaction = Transaction.restore(TRANSACTION_ID, IdempotencyKey.of("key"),
        TransactionType.P2P_TRANSFER, ACCOUNT_ID, DESTINATION_ID, money("100"), money("0"),
        TransactionStatus.FAILED, "failure", CREATED, null, null, null);
    assertEquals(TransactionStatus.FAILED, transaction.getStatus());
    assertTrue(transaction.pullDomainEvents().isEmpty());
  }

  /** Accepts a consistent stored state without transition events. */
  @Test
  void shouldRestoreSuccessfulSnapshot() {
    Transaction transaction = Transaction.restore(TRANSACTION_ID, IdempotencyKey.of("key"),
        TransactionType.P2P_TRANSFER, ACCOUNT_ID, DESTINATION_ID, money("100"), money("0"),
        TransactionStatus.SUCCESSFUL, null, CREATED, NOW, null, null);
    assertEquals(TransactionStatus.SUCCESSFUL, transaction.getStatus());
    assertTrue(transaction.pullDomainEvents().isEmpty());
  }

  /** Accepts a consistent stored state without transition events. */
  @Test
  void shouldRestoreReversedSnapshot() {
    Transaction transaction = Transaction.restore(TRANSACTION_ID, IdempotencyKey.of("key"),
        TransactionType.P2P_TRANSFER, ACCOUNT_ID, DESTINATION_ID, money("100"), money("0"),
        TransactionStatus.REVERSED, null, CREATED, NOW, new TransactionId(uuid(20)), NOW);
    assertEquals(TransactionStatus.REVERSED, transaction.getStatus());
    assertTrue(transaction.pullDomainEvents().isEmpty());
  }
}
