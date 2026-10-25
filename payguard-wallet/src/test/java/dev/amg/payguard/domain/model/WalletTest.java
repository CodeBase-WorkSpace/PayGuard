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

/** Tests wallet invariants, status permissions, posting order and rejection atomicity. */
class WalletTest {

  /** Creates zero balances and exactly one creation event. */
  @Test
  void shouldCreateEmptyWalletWithExplicitAccount() {
    Wallet wallet = Wallet.create(WALLET_ID, ACCOUNT_ID, USER_ID, CHF, CREATED);
    assertEquals(ACCOUNT_ID, wallet.getAccountId());
    assertEquals(WALLET_ID, wallet.getId());
    assertEquals(USER_ID, wallet.getUserId());
    assertEquals(CHF, wallet.getCurrency());
    assertEquals(CREATED, wallet.getCreatedAt());
    assertEquals(WalletStatus.ACTIVE, wallet.getStatus());
    assertEquals(money("0"), wallet.getAvailableBalance());
    assertEquals(-1L, wallet.getLastAppliedSequenceNumber());
    assertEquals(0L, wallet.getVersion());
    var events = wallet.pullDomainEvents();
    assertEquals(1, events.size());
    WalletCreatedEvent event = assertInstanceOf(WalletCreatedEvent.class, events.getFirst());
    assertEquals(WALLET_ID, event.walletId());
    assertEquals(CREATED, event.occurredOn());
    assertTrue(wallet.pullDomainEvents().isEmpty());
  }

  /** Restoration preserves persisted values and does not replay creation. */
  @Test
  void shouldRestoreWithoutEvents() {
    Wallet wallet = wallet();
    assertTrue(wallet.pullDomainEvents().isEmpty());
    assertEquals(7L, wallet.getVersion());
    assertEquals(9L, wallet.getLastAppliedSequenceNumber());
  }

  /** Regression: a zero book balance must permit closure. */
  @Test
  void shouldCloseZeroBalanceWallet() {
    Wallet wallet = wallet(WalletStatus.ACTIVE, "0", "0");
    wallet.close(NOW);
    assertEquals(WalletStatus.CLOSED, wallet.getStatus());
    assertInstanceOf(WalletClosedEvent.class, wallet.pullDomainEvents().getFirst());
  }

  /** Regression: nonzero balances must prevent closure without mutation. */
  @Test
  void shouldRejectClosingFundedWalletWithoutMutation() {
    Wallet wallet = wallet();
    var before = snapshot(wallet);
    assertThrows(InvalidWalletStateException.class, () -> wallet.close(NOW));
    assertEquals(before, snapshot(wallet));
    assertTrue(wallet.pullDomainEvents().isEmpty());
  }

  /** Cannot close while reserved funds remain. */
  @Test
  void shouldRejectReservedFundsOnClosure() {
    Wallet wallet = wallet(WalletStatus.ACTIVE, "30", "30");
    assertThrows(InvalidWalletStateException.class, () -> wallet.close(NOW));
    assertEquals(WalletStatus.ACTIVE, wallet.getStatus());
  }

  /** Closure is terminal and must not emit a new transition. */
  @Test
  void shouldNotReactivateClosedWallet() {
    Wallet wallet = wallet(WalletStatus.CLOSED, "0", "0");
    assertThrows(InvalidWalletStateException.class, () -> wallet.activate(NOW));
    assertThrows(InvalidWalletStateException.class, () -> wallet.suspend("reason", NOW));
    assertThrows(InvalidWalletStateException.class, () -> wallet.close(NOW));
    assertTrue(wallet.pullDomainEvents().isEmpty());
  }

  /** Prevents restoring an overdraft under the explicit non-overdraft policy. */
  @Test
  void shouldRejectNegativeBookBalance() {
    assertThrows(NegativeAmountNotAllowedException.class, () -> wallet(WalletStatus.ACTIVE, "-1", "0"));
  }

  /** Rejects corrupt negative reservation snapshots. */
  @Test
  void shouldRejectNegativeReservedBalance() {
    assertThrows(NegativeAmountNotAllowedException.class, () -> wallet(WalletStatus.ACTIVE, "100", "-1"));
  }

  /** Rejects a restored snapshot with negative availability. */
  @Test
  void shouldRejectReservationsExceedingBookBalance() {
    assertThrows(InvalidWalletStateException.class, () -> wallet(WalletStatus.ACTIVE, "10", "11"));
  }

  /** Rejects internally inconsistent closed snapshots. */
  @Test
  void shouldRejectFundedClosedSnapshot() {
    assertThrows(InvalidWalletStateException.class, () -> wallet(WalletStatus.CLOSED, "1", "0"));
  }

  /** Reserving changes reserved and available balances, not the book balance. */
  @Test
  void shouldReserveAvailableFunds() {
    Wallet wallet = wallet();
    wallet.reserveFunds(money("30"), NOW);
    assertEquals(money("100"), wallet.getBookBalance());
    assertEquals(money("30"), wallet.getReservedBalance());
    assertEquals(money("70"), wallet.getAvailableBalance());
    assertEquals(NOW, wallet.getUpdatedAt());
    assertEquals(7L, wallet.getVersion());
  }

  /** Allows reserving the final available minor unit. */
  @Test
  void shouldAllowExactAvailableBalanceReservation() {
    Wallet wallet = wallet();
    wallet.reserveFunds(money("100"), NOW);
    assertTrue(wallet.getAvailableBalance().isZero());
  }

  /** A rejected reservation preserves balances, time, sequence and events. */
  @Test
  void shouldRejectOverReservationWithoutMutation() {
    Wallet wallet = wallet();
    var before = snapshot(wallet);
    assertThrows(InsufficientFundException.class, () -> wallet.reserveFunds(money("100.01"), NOW));
    assertEquals(before, snapshot(wallet));
    assertTrue(wallet.pullDomainEvents().isEmpty());
  }

  /** Operational freezing does not trap existing reservations. */
  @Test
  void shouldReleaseReservationWhileFrozen() {
    Wallet wallet = wallet(WalletStatus.FROZEN, "100", "30");
    wallet.unreserveFunds(money("30"), NOW);
    assertTrue(wallet.getReservedBalance().isZero());
    assertEquals(money("100"), wallet.getBookBalance());
    assertEquals(WalletStatus.FROZEN, wallet.getStatus());
  }

  /** Cannot release more than the reserved amount. */
  @Test
  void shouldRejectExcessReleaseWithoutMutation() {
    Wallet wallet = wallet(WalletStatus.ACTIVE, "100", "30");
    var before = snapshot(wallet);
    assertThrows(InvalidWalletStateException.class, () -> wallet.unreserveFunds(money("30.01"), NOW));
    assertEquals(before, snapshot(wallet));
  }

  /** Applies a credit to the explicit account and advances the sequence. */
  @Test
  void shouldCreditExplicitAccount() {
    Wallet wallet = wallet();
    wallet.applyLedgerEntry(walletEntry(LedgerEntryType.CREDIT, "15", 10), NOW);
    assertEquals(money("115"), wallet.getBookBalance());
    assertEquals(10L, wallet.getLastAppliedSequenceNumber());
  }

  /** Regression: matching the wallet UUID does not prove account ownership. */
  @Test
  void shouldRejectWalletUuidAsAccountShortcut() {
    Wallet wallet = wallet();
    LedgerEntry entry = entry(100, TRANSACTION_ID, new AccountId(WALLET_ID.value()),
        money("10"), LedgerEntryType.CREDIT, 10, NOW);
    var before = snapshot(wallet);
    assertThrows(InvalidWalletStateException.class, () -> wallet.applyLedgerEntry(entry, NOW));
    assertEquals(before, snapshot(wallet));
  }

  /** An ordinary debit cannot spend money held by another operation. */
  @Test
  void shouldDebitOnlyAvailableFunds() {
    Wallet wallet = wallet(WalletStatus.ACTIVE, "100", "30");
    var before = snapshot(wallet);
    assertThrows(InsufficientFundException.class,
        () -> wallet.applyLedgerEntry(walletEntry(LedgerEntryType.DEBIT, "70.01", 10), NOW));
    assertEquals(before, snapshot(wallet));
    wallet.applyLedgerEntry(walletEntry(LedgerEntryType.DEBIT, "70", 10), NOW);
    assertEquals(money("30"), wallet.getBookBalance());
    assertTrue(wallet.getAvailableBalance().isZero());
  }

  /** Rejects replay in the same loaded aggregate. */
  @Test
  void shouldRejectDuplicateSequenceWithoutMutation() {
    Wallet wallet = wallet();
    LedgerEntry entry = walletEntry(LedgerEntryType.DEBIT, "10", 10);
    wallet.applyLedgerEntry(entry, NOW);
    var before = snapshot(wallet);
    assertThrows(InvalidWalletStateException.class, () -> wallet.applyLedgerEntry(entry, NOW));
    assertEquals(before, snapshot(wallet));
  }

  /** Rejects an earlier posting sequence even when entry identities differ. */
  @Test
  void shouldRejectOutOfOrderSequence() {
    Wallet wallet = wallet();
    assertThrows(InvalidWalletStateException.class,
        () -> wallet.applyLedgerEntry(walletEntry(LedgerEntryType.CREDIT, "10", 8), NOW));
  }

  /** Does not assume sequences are gap-free. */
  @Test
  void shouldAllowSequenceGaps() {
    Wallet wallet = wallet();
    wallet.applyLedgerEntry(walletEntry(LedgerEntryType.CREDIT, "1", 100), NOW);
    assertEquals(100L, wallet.getLastAppliedSequenceNumber());
  }

  /** Capture reduces book and reserved balances while preserving availability. */
  @Test
  void shouldCaptureReservedFundsAtomically() {
    Wallet wallet = wallet(WalletStatus.ACTIVE, "100", "30");
    Money beforeAvailable = wallet.getAvailableBalance();
    wallet.captureReservedFunds(walletEntry(LedgerEntryType.DEBIT, "30", 10), NOW);
    assertEquals(money("70"), wallet.getBookBalance());
    assertTrue(wallet.getReservedBalance().isZero());
    assertEquals(beforeAvailable, wallet.getAvailableBalance());
    assertEquals(10L, wallet.getLastAppliedSequenceNumber());
  }

  /** A credit cannot be used to consume a reservation. */
  @Test
  void shouldRejectCaptureUsingCredit() {
    Wallet wallet = wallet(WalletStatus.ACTIVE, "100", "30");
    var before = snapshot(wallet);
    assertThrows(InvalidWalletStateException.class,
        () -> wallet.captureReservedFunds(walletEntry(LedgerEntryType.CREDIT, "30", 10), NOW));
    assertEquals(before, snapshot(wallet));
  }

  /** Capture cannot consume more than the reserved total. */
  @Test
  void shouldRejectCaptureAboveReservation() {
    Wallet wallet = wallet(WalletStatus.ACTIVE, "100", "30");
    var before = snapshot(wallet);
    assertThrows(InvalidWalletStateException.class,
        () -> wallet.captureReservedFunds(walletEntry(LedgerEntryType.DEBIT, "30.01", 10), NOW));
    assertEquals(before, snapshot(wallet));
  }

  /** The explicit policy blocks capture when all debits are frozen. */
  @Test
  void shouldRejectFrozenCapture() {
    Wallet wallet = wallet(WalletStatus.FROZEN, "100", "30");
    assertThrows(WalletFrozenException.class,
        () -> wallet.captureReservedFunds(walletEntry(LedgerEntryType.DEBIT, "30", 10), NOW));
  }

  /** A null mutation timestamp cannot leave a changed balance. */
  @Test
  void shouldRejectNullTimeBeforeChangingBalance() {
    Wallet wallet = wallet();
    var before = snapshot(wallet);
    assertThrows(InvalidDomainArgumentException.class, () -> wallet.reserveFunds(money("1"), null));
    assertThrows(InvalidDomainArgumentException.class, () -> wallet.applyLedgerEntry(walletEntry(LedgerEntryType.CREDIT, "1", 10), null));
    assertEquals(before, snapshot(wallet));
  }

  /** Enforces non-decreasing aggregate mutation timestamps. */
  @Test
  void shouldRejectBackdatedMutation() {
    Wallet wallet = wallet();
    wallet.reserveFunds(money("1"), NOW);
    var before = snapshot(wallet);
    assertThrows(InvalidWalletStateException.class, () -> wallet.unreserveFunds(money("1"), CREATED));
    assertEquals(before, snapshot(wallet));
  }

  /** Invalid reasons must not freeze the aggregate or append events. */
  @Test
  void shouldRejectNullOrBlankFreezeReasonBeforeMutation() {
    Wallet wallet = wallet();
    var before = snapshot(wallet);
    assertThrows(InvalidDomainArgumentException.class, () -> wallet.suspend(null, NOW));
    assertThrows(InvalidDomainArgumentException.class, () -> wallet.suspend(" ", NOW));
    assertEquals(before, snapshot(wallet));
    assertTrue(wallet.pullDomainEvents().isEmpty());
  }

  /** Suspending both directions results in a frozen wallet. */
  @Test
  void shouldCombineDirectionalSuspensions() {
    Wallet wallet = wallet();
    wallet.suspendDebits("review", NOW);
    assertEquals(WalletStatus.SUSPENDED_DEBIT, wallet.getStatus());
    wallet.suspendCredits("review", NOW);
    assertEquals(WalletStatus.FROZEN, wallet.getStatus());
    assertEquals(2, wallet.pullDomainEvents().size());
  }

  /** Repeated identical status commands do not invent additional transitions. */
  @Test
  void shouldAvoidDuplicateStatusEvents() {
    Wallet wallet = wallet();
    wallet.suspend("review", NOW);
    wallet.suspend("review", NOW.plusSeconds(1));
    assertEquals(1, wallet.pullDomainEvents().size());
    assertEquals(NOW, wallet.getUpdatedAt());
    wallet.activate(NOW.plusSeconds(2));
    wallet.activate(NOW.plusSeconds(3));
    assertEquals(1, wallet.pullDomainEvents().size());
  }

  /** Requires positive amounts for fund-changing operations. */
  @Test
  void shouldRejectZeroForReserveFunds() {
    Wallet wallet = wallet();
    assertThrows(InvalidWalletStateException.class, () -> wallet.reserveFunds(money("0"), NOW));
  }

  /** Requires positive amounts for fund-changing operations. */
  @Test
  void shouldRejectZeroForUnreserveFunds() {
    Wallet wallet = wallet();
    assertThrows(InvalidWalletStateException.class, () -> wallet.unreserveFunds(money("0"), NOW));
  }

  /** Requires positive amounts for fund-changing operations. */
  @Test
  void shouldRejectZeroForAssertCanDebit() {
    Wallet wallet = wallet();
    assertThrows(InvalidWalletStateException.class, () -> wallet.assertCanDebit(money("0")));
  }

  /** Requires positive amounts for fund-changing operations. */
  @Test
  void shouldRejectZeroForAssertCanCredit() {
    Wallet wallet = wallet();
    assertThrows(InvalidWalletStateException.class, () -> wallet.assertCanCredit(money("0")));
  }

  /** Checks both debit and credit permissions for ACTIVE. */
  @Test
  void shouldEnforcePermissionsForActive() {
    Wallet wallet = wallet(WalletStatus.ACTIVE, "100", "0");
    assertDoesNotThrow(() -> wallet.assertCanDebit(money("1")));
    assertDoesNotThrow(() -> wallet.assertCanCredit(money("1")));
  }

  /** Checks both debit and credit permissions for SUSPENDED_DEBIT. */
  @Test
  void shouldEnforcePermissionsForSuspendedDebit() {
    Wallet wallet = wallet(WalletStatus.SUSPENDED_DEBIT, "100", "0");
    assertThrows(WalletFrozenException.class, () -> wallet.assertCanDebit(money("1")));
    assertDoesNotThrow(() -> wallet.assertCanCredit(money("1")));
  }

  /** Checks both debit and credit permissions for SUSPENDED_CREDIT. */
  @Test
  void shouldEnforcePermissionsForSuspendedCredit() {
    Wallet wallet = wallet(WalletStatus.SUSPENDED_CREDIT, "100", "0");
    assertDoesNotThrow(() -> wallet.assertCanDebit(money("1")));
    assertThrows(WalletFrozenException.class, () -> wallet.assertCanCredit(money("1")));
  }

  /** Checks both debit and credit permissions for FROZEN. */
  @Test
  void shouldEnforcePermissionsForFrozen() {
    Wallet wallet = wallet(WalletStatus.FROZEN, "100", "0");
    assertThrows(WalletFrozenException.class, () -> wallet.assertCanDebit(money("1")));
    assertThrows(WalletFrozenException.class, () -> wallet.assertCanCredit(money("1")));
  }

  /** Checks both debit and credit permissions for CLOSED. */
  @Test
  void shouldEnforcePermissionsForClosed() {
    Wallet wallet = wallet(WalletStatus.CLOSED, "0", "0");
    assertThrows(WalletFrozenException.class, () -> wallet.assertCanDebit(money("1")));
    assertThrows(WalletFrozenException.class, () -> wallet.assertCanCredit(money("1")));
  }

  /** Checks currency even during eligibility-only validation. */
  @Test
  void shouldRejectCurrencyMismatchOnCredit() {
    Wallet wallet = wallet();
    assertThrows(CurrencyMismatchException.class, () -> wallet.assertCanCredit(Money.of("1", USD)));
  }

  /** Checks currency even during eligibility-only validation. */
  @Test
  void shouldRejectCurrencyMismatchOnDebit() {
    Wallet wallet = wallet();
    assertThrows(CurrencyMismatchException.class, () -> wallet.assertCanDebit(Money.of("1", USD)));
  }

  /** Rejects invalid restored metadata before exposing an aggregate. */
  @Test
  void shouldRejectBookCurrencyOnRestore() {
    assertThrows(CurrencyMismatchException.class, () -> Wallet.restore(WALLET_ID, ACCOUNT_ID, USER_ID, CHF,
        WalletStatus.ACTIVE, Money.of("100", USD), money("0"), 0, -1, CREATED, CREATED));
  }

  /** Rejects invalid restored metadata before exposing an aggregate. */
  @Test
  void shouldRejectReservedCurrencyOnRestore() {
    assertThrows(CurrencyMismatchException.class, () -> Wallet.restore(WALLET_ID, ACCOUNT_ID, USER_ID, CHF,
        WalletStatus.ACTIVE, money("100"), Money.of("0", USD), 0, -1, CREATED, CREATED));
  }

  /** Rejects invalid restored metadata before exposing an aggregate. */
  @Test
  void shouldRejectNegativeVersionOnRestore() {
    assertThrows(InvalidWalletStateException.class, () -> Wallet.restore(WALLET_ID, ACCOUNT_ID, USER_ID, CHF,
        WalletStatus.ACTIVE, money("100"), money("0"), -1, -1, CREATED, CREATED));
  }

  /** Rejects invalid restored metadata before exposing an aggregate. */
  @Test
  void shouldRejectInvalidSequenceOnRestore() {
    assertThrows(InvalidWalletStateException.class, () -> Wallet.restore(WALLET_ID, ACCOUNT_ID, USER_ID, CHF,
        WalletStatus.ACTIVE, money("100"), money("0"), 0, -2, CREATED, CREATED));
  }

  /** Rejects invalid restored metadata before exposing an aggregate. */
  @Test
  void shouldRejectBackdatedSnapshotOnRestore() {
    assertThrows(InvalidWalletStateException.class, () -> Wallet.restore(WALLET_ID, ACCOUNT_ID, USER_ID, CHF,
        WalletStatus.ACTIVE, money("100"), money("0"), 0, -1, NOW, CREATED));
  }

  /** Rejects posting timestamps outside the wallet/application interval. */
  @Test
  void shouldRejectFuturePostingTime() {
    Wallet wallet = wallet();
    LedgerEntry entry = entry(100, TRANSACTION_ID, ACCOUNT_ID, money("1"), LedgerEntryType.CREDIT, 10, NOW.plusNanos(1));
    assertThrows(InvalidWalletStateException.class, () -> wallet.applyLedgerEntry(entry, NOW));
  }

  /** Rejects posting timestamps outside the wallet/application interval. */
  @Test
  void shouldRejectBeforeCreationPostingTime() {
    Wallet wallet = wallet();
    LedgerEntry entry = entry(100, TRANSACTION_ID, ACCOUNT_ID, money("1"), LedgerEntryType.CREDIT, 10, CREATED.minusNanos(1));
    assertThrows(InvalidWalletStateException.class, () -> wallet.applyLedgerEntry(entry, NOW));
  }
}
