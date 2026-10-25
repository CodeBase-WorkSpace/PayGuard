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

/** Tests expiry boundaries, terminal states, event contents and restore invariants. */
class HoldTest {

  /** Creates a pending hold without terminal metadata or emitted transition events. */
  @Test
  void shouldCreatePendingPositiveHold() {
    Hold hold = hold();
    assertEquals(HOLD_ID, hold.getId());
    assertEquals(WALLET_ID, hold.getWalletId());
    assertEquals(money("30"), hold.getAmount());
    assertEquals("Purchase", hold.getReason());
    assertEquals("order-1", hold.getReferenceId());
    assertEquals(CREATED, hold.getCreatedAt());
    assertEquals(DEADLINE, hold.getExpiresAt());
    assertEquals(HoldStatus.PENDING, hold.getStatus());
    assertTrue(hold.getResolvedAt().isEmpty());
    assertTrue(hold.pullDomainEvents().isEmpty());
  }

  /** Regression: capture validity excludes the deadline itself. */
  @Test
  void shouldTreatExactDeadlineAsExpired() {
    Hold hold = hold();
    assertFalse(hold.isExpired(DEADLINE.minusNanos(1)));
    assertTrue(hold.isExpired(DEADLINE));
    assertTrue(hold.isExpired(DEADLINE.plusNanos(1)));
  }

  /** Captures at the last valid instant and emits the matching event. */
  @Test
  void shouldCaptureImmediatelyBeforeExpiry() {
    Hold hold = hold();
    Instant captureTime = DEADLINE.minusNanos(1);
    hold.capture(captureTime);
    assertEquals(HoldStatus.CAPTURED, hold.getStatus());
    assertEquals(captureTime, hold.getResolvedAt().orElseThrow());
    var event = assertInstanceOf(FundsHoldCapturedEvent.class, hold.pullDomainEvents().getFirst());
    assertEquals(HOLD_ID, event.holdId());
    assertEquals(WALLET_ID, event.walletId());
    assertEquals(money("30"), event.amount());
    assertEquals(captureTime, event.occurredOn());
  }

  /** Regression: rejection must neither expire the hold nor emit an expiry event. */
  @Test
  void shouldRejectExpiredCaptureWithoutMutation() {
    Hold hold = hold();
    assertThrows(HoldExpiredException.class, () -> hold.capture(DEADLINE));
    assertEquals(HoldStatus.PENDING, hold.getStatus());
    assertTrue(hold.getResolvedAt().isEmpty());
    assertTrue(hold.pullDomainEvents().isEmpty());
  }

  /** An explicit expiry succeeds at the exact boundary. */
  @Test
  void shouldExpireAtExactDeadline() {
    Hold hold = hold();
    hold.expire(DEADLINE);
    assertEquals(HoldStatus.EXPIRED, hold.getStatus());
    assertEquals(DEADLINE, hold.getResolvedAt().orElseThrow());
    assertInstanceOf(FundsHoldExpiredEvent.class, hold.pullDomainEvents().getFirst());
  }

  /** Premature expiry cannot change state or events. */
  @Test
  void shouldRejectEarlyExpiryWithoutMutation() {
    Hold hold = hold();
    assertThrows(InvalidHoldStateException.class, () -> hold.expire(DEADLINE.minusNanos(1)));
    assertEquals(HoldStatus.PENDING, hold.getStatus());
    assertTrue(hold.getResolvedAt().isEmpty());
    assertTrue(hold.pullDomainEvents().isEmpty());
  }

  /** The explicit cancellation policy allows releasing an overdue pending hold. */
  @Test
  void shouldReleaseOverduePendingHold() {
    Hold hold = hold();
    hold.release(DEADLINE.plusSeconds(1));
    assertEquals(HoldStatus.RELEASED, hold.getStatus());
    assertInstanceOf(FundsHoldReleasedEvent.class, hold.pullDomainEvents().getFirst());
  }

  /** Zero reservations are not valid domain operations. */
  @Test
  void shouldRejectZeroHold() {
    assertThrows(InvalidHoldStateException.class, () -> Hold.createNew(HOLD_ID, WALLET_ID,
        money("0"), "reason", "reference", DEADLINE, CREATED));
  }

  /** Negative reservation amounts are rejected. */
  @Test
  void shouldRejectNegativeHold() {
    assertThrows(NegativeAmountNotAllowedException.class, () -> Hold.createNew(HOLD_ID, WALLET_ID,
        money("-1"), "reason", "reference", DEADLINE, CREATED));
  }

  /** A newly created hold must have a nonempty validity interval. */
  @Test
  void shouldRejectDeadlineEqualToCreation() {
    assertThrows(InvalidHoldStateException.class, () -> Hold.createNew(HOLD_ID, WALLET_ID,
        money("1"), "reason", "reference", CREATED, CREATED));
  }

  /** Rejects a reversed validity interval. */
  @Test
  void shouldRejectDeadlineBeforeCreation() {
    assertThrows(InvalidHoldStateException.class, () -> Hold.createNew(HOLD_ID, WALLET_ID,
        money("1"), "reason", "reference", CREATED.minusNanos(1), CREATED));
  }

  /** Restores valid terminal state without replaying a captured event. */
  @Test
  void shouldRestoreCapturedWithoutEvents() {
    Hold hold = Hold.restore(HOLD_ID, WALLET_ID, money("30"), "reason", "reference",
        DEADLINE, CREATED, HoldStatus.CAPTURED, NOW);
    assertEquals(HoldStatus.CAPTURED, hold.getStatus());
    assertTrue(hold.pullDomainEvents().isEmpty());
  }

  /** Null time rejection leaves the aggregate unchanged. */
  @Test
  void shouldRejectNullTimeForCapture() {
    Hold hold = hold();
    assertThrows(InvalidDomainArgumentException.class, () -> hold.capture(null));
    assertEquals(HoldStatus.PENDING, hold.getStatus());
    assertTrue(hold.getResolvedAt().isEmpty());
    assertTrue(hold.pullDomainEvents().isEmpty());
  }

  /** Rejects transitions predating the hold itself. */
  @Test
  void shouldRejectPreCreationTimeForCapture() {
    Hold hold = hold();
    assertThrows(InvalidHoldStateException.class, () -> hold.capture(CREATED.minusNanos(1)));
    assertEquals(HoldStatus.PENDING, hold.getStatus());
  }

  /** Null time rejection leaves the aggregate unchanged. */
  @Test
  void shouldRejectNullTimeForRelease() {
    Hold hold = hold();
    assertThrows(InvalidDomainArgumentException.class, () -> hold.release(null));
    assertEquals(HoldStatus.PENDING, hold.getStatus());
    assertTrue(hold.getResolvedAt().isEmpty());
    assertTrue(hold.pullDomainEvents().isEmpty());
  }

  /** Rejects transitions predating the hold itself. */
  @Test
  void shouldRejectPreCreationTimeForRelease() {
    Hold hold = hold();
    assertThrows(InvalidHoldStateException.class, () -> hold.release(CREATED.minusNanos(1)));
    assertEquals(HoldStatus.PENDING, hold.getStatus());
  }

  /** Null time rejection leaves the aggregate unchanged. */
  @Test
  void shouldRejectNullTimeForExpire() {
    Hold hold = hold();
    assertThrows(InvalidDomainArgumentException.class, () -> hold.expire(null));
    assertEquals(HoldStatus.PENDING, hold.getStatus());
    assertTrue(hold.getResolvedAt().isEmpty());
    assertTrue(hold.pullDomainEvents().isEmpty());
  }

  /** Rejects transitions predating the hold itself. */
  @Test
  void shouldRejectPreCreationTimeForExpire() {
    Hold hold = hold();
    assertThrows(InvalidHoldStateException.class, () -> hold.expire(CREATED.minusNanos(1)));
    assertEquals(HoldStatus.PENDING, hold.getStatus());
  }

  /** Terminal hold states cannot transition a second time. */
  @Test
  void shouldRejectTransitionsAfterCaptured() {
    Hold hold = hold();
    hold.capture(NOW);
    hold.clearDomainEvents();
    assertThrows(InvalidHoldStateException.class, () -> hold.capture(DEADLINE));
    assertThrows(InvalidHoldStateException.class, () -> hold.release(DEADLINE));
    assertThrows(InvalidHoldStateException.class, () -> hold.expire(DEADLINE));
    assertEquals(HoldStatus.CAPTURED, hold.getStatus());
    assertTrue(hold.pullDomainEvents().isEmpty());
  }

  /** Terminal hold states cannot transition a second time. */
  @Test
  void shouldRejectTransitionsAfterReleased() {
    Hold hold = hold();
    hold.release(NOW);
    hold.clearDomainEvents();
    assertThrows(InvalidHoldStateException.class, () -> hold.capture(DEADLINE));
    assertThrows(InvalidHoldStateException.class, () -> hold.release(DEADLINE));
    assertThrows(InvalidHoldStateException.class, () -> hold.expire(DEADLINE));
    assertEquals(HoldStatus.RELEASED, hold.getStatus());
    assertTrue(hold.pullDomainEvents().isEmpty());
  }

  /** Terminal hold states cannot transition a second time. */
  @Test
  void shouldRejectTransitionsAfterExpired() {
    Hold hold = hold();
    hold.expire(DEADLINE);
    hold.clearDomainEvents();
    assertThrows(InvalidHoldStateException.class, () -> hold.capture(DEADLINE));
    assertThrows(InvalidHoldStateException.class, () -> hold.release(DEADLINE));
    assertThrows(InvalidHoldStateException.class, () -> hold.expire(DEADLINE));
    assertEquals(HoldStatus.EXPIRED, hold.getStatus());
    assertTrue(hold.pullDomainEvents().isEmpty());
  }

  /** Restoration enforces the same lifecycle invariants as normal commands. */
  @Test
  void shouldRejectPendingWithResolution() {
    assertThrows(InvalidHoldStateException.class, () -> Hold.restore(HOLD_ID, WALLET_ID,
        money("30"), "reason", "reference", DEADLINE, CREATED, HoldStatus.PENDING, NOW));
  }

  /** Restoration enforces the same lifecycle invariants as normal commands. */
  @Test
  void shouldRejectTerminalWithoutResolution() {
    assertThrows(InvalidHoldStateException.class, () -> Hold.restore(HOLD_ID, WALLET_ID,
        money("30"), "reason", "reference", DEADLINE, CREATED, HoldStatus.CAPTURED, null));
  }

  /** Restoration enforces the same lifecycle invariants as normal commands. */
  @Test
  void shouldRejectLateCapturedSnapshot() {
    assertThrows(InvalidHoldStateException.class, () -> Hold.restore(HOLD_ID, WALLET_ID,
        money("30"), "reason", "reference", DEADLINE, CREATED, HoldStatus.CAPTURED, DEADLINE));
  }

  /** Restoration enforces the same lifecycle invariants as normal commands. */
  @Test
  void shouldRejectPrematureExpiredSnapshot() {
    assertThrows(InvalidHoldStateException.class, () -> Hold.restore(HOLD_ID, WALLET_ID,
        money("30"), "reason", "reference", DEADLINE, CREATED, HoldStatus.EXPIRED, NOW));
  }

  /** Restoration enforces the same lifecycle invariants as normal commands. */
  @Test
  void shouldRejectPreCreationResolution() {
    assertThrows(InvalidHoldStateException.class, () -> Hold.restore(HOLD_ID, WALLET_ID,
        money("30"), "reason", "reference", DEADLINE, CREATED, HoldStatus.RELEASED, CREATED.minusNanos(1)));
  }

  /** Rejects missing descriptive metadata before construction. */
  @Test
  void shouldRejectBlankReason() {
    assertThrows(InvalidDomainArgumentException.class, () -> Hold.createNew(HOLD_ID, WALLET_ID,
        money("1"), " ", "ref", DEADLINE, CREATED));
  }

  /** Rejects missing descriptive metadata before construction. */
  @Test
  void shouldRejectBlankReference() {
    assertThrows(InvalidDomainArgumentException.class, () -> Hold.createNew(HOLD_ID, WALLET_ID,
        money("1"), "reason", " ", DEADLINE, CREATED));
  }

  /** Rejects missing descriptive metadata before construction. */
  @Test
  void shouldRejectNullReason() {
    assertThrows(InvalidDomainArgumentException.class, () -> Hold.createNew(HOLD_ID, WALLET_ID,
        money("1"), null, "ref", DEADLINE, CREATED));
  }
}
