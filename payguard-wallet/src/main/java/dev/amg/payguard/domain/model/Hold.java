package dev.amg.payguard.domain.model;

import dev.amg.payguard.domain.enums.HoldStatus;
import dev.amg.payguard.domain.event.FundsHoldCapturedEvent;
import dev.amg.payguard.domain.event.FundsHoldExpiredEvent;
import dev.amg.payguard.domain.event.FundsHoldReleasedEvent;
import dev.amg.payguard.domain.exception.HoldExpiredException;
import dev.amg.payguard.domain.exception.InvalidHoldStateException;
import dev.amg.payguard.domain.value_object.HoldId;
import dev.amg.payguard.domain.value_object.Money;
import dev.amg.payguard.domain.value_object.WalletId;
import domain.AggregateRoot;
import domain.DomainEvent;
import domain.DomainValidation;

import java.time.Instant;
import java.util.Optional;

/**
 * A full-amount reservation, with a half-open validity interval [createdAt, expiresAt).
 *
 * <p>Mutable and command-confined. Capture, release and expiry are mutually exclusive
 * terminal transitions. All business validation precedes state/event mutation. A failed
 * expired capture does not expire the hold implicitly; expiry is a separate successful
 * command that must release wallet funds in the same database transaction.
 */
public final class Hold extends AggregateRoot<HoldId, DomainEvent> {
    private final WalletId walletId;
    private final Money amount;
    private final String reason;
    private final String referenceId;
    private final Instant expiresAt;
    private final Instant createdAt;
    private HoldStatus status;
    private Instant resolvedAt;

    private Hold(HoldId id, WalletId walletId, Money amount, String reason, String referenceId,
                 Instant expiresAt, Instant createdAt, HoldStatus status, Instant resolvedAt) {
        super(id);
        this.walletId = DomainValidation.requireNonNull(walletId, "Wallet id");
        this.amount = DomainValidation.requireNonNull(amount, "Amount");
        this.reason = DomainValidation.requireText(reason, "Reason");
        this.referenceId = DomainValidation.requireText(referenceId, "Reference id");
        this.expiresAt = DomainValidation.requireNonNull(expiresAt, "Expiration time");
        this.createdAt = DomainValidation.requireNonNull(createdAt, "Creation time");
        this.status = DomainValidation.requireNonNull(status, "Hold status");
        amount.assertNonNegative();
        if (amount.isZero()) {
            throw new InvalidHoldStateException("Hold amount must be positive");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new InvalidHoldStateException("Expiration must be strictly after creation");
        }
        if ((status == HoldStatus.PENDING) != (resolvedAt == null)) {
            throw new InvalidHoldStateException("Resolution time is inconsistent with hold status");
        }
        if (resolvedAt != null) {
            validateTime(resolvedAt);
            if (status == HoldStatus.CAPTURED && !resolvedAt.isBefore(expiresAt)) {
                throw new InvalidHoldStateException("A captured hold must have resolved before expiry");
            }
            if (status == HoldStatus.EXPIRED && resolvedAt.isBefore(expiresAt)) {
                throw new InvalidHoldStateException("An expired hold cannot resolve before expiry");
            }
        }
        this.resolvedAt = resolvedAt;
    }

    /**
     * Creates a pending reservation with a strictly positive amount and future deadline.
     */
    public static Hold createNew(HoldId id, WalletId walletId, Money amount, String reason,
                                 String referenceId, Instant expiresAt, Instant now) {
        return new Hold(id, walletId, amount, reason, referenceId, expiresAt, now,
                HoldStatus.PENDING, null);
    }

    /**
     * Restores a consistent snapshot without generating domain events.
     */
    public static Hold restore(HoldId id, WalletId walletId, Money amount, String reason,
                               String referenceId, Instant expiresAt, Instant createdAt, HoldStatus status,
                               Instant resolvedAt) {
        return new Hold(id, walletId, amount, reason, referenceId, expiresAt, createdAt,
                status, resolvedAt);
    }

    /**
     * Captures a live pending hold; rejected captures leave state and events unchanged.
     */
    public void capture(Instant now) {
        validateTime(now);
        assertPending();
        if (isExpired(now)) {
            throw new HoldExpiredException("Cannot capture an expired hold");
        }
        FundsHoldCapturedEvent event =
                FundsHoldCapturedEvent.now(getId(), walletId, amount, now);
        status = HoldStatus.CAPTURED;
        resolvedAt = now;
        registerEvent(event);
    }

    /**
     * Releases any pending hold, including an overdue one, without pretending it was captured.
     */
    public void release(Instant now) {
        validateTime(now);
        assertPending();
        FundsHoldReleasedEvent event =
                FundsHoldReleasedEvent.now(getId(), walletId, amount, now);
        status = HoldStatus.RELEASED;
        resolvedAt = now;
        registerEvent(event);
    }

    /**
     * Expires a pending hold at or after its deadline; requires transactional fund release.
     */
    public void expire(Instant now) {
        validateTime(now);
        assertPending();
        if (!isExpired(now)) {
            throw new InvalidHoldStateException("The hold deadline has not been reached");
        }
        FundsHoldExpiredEvent event =
                FundsHoldExpiredEvent.now(getId(), walletId, amount, now);
        status = HoldStatus.EXPIRED;
        resolvedAt = now;
        registerEvent(event);
    }

    /**
     * Tests the deadline, not the stored lifecycle status; equality with the deadline is expired.
     */
    public boolean isExpired(Instant now) {
        DomainValidation.requireNonNull(now, "Evaluation time");
        return !now.isBefore(expiresAt);
    }

    private void validateTime(Instant now) {
        DomainValidation.requireNonNull(now, "Operation time");
        if (now.isBefore(createdAt)) {
            throw new InvalidHoldStateException("Operation time precedes hold creation");
        }
    }

    private void assertPending() {
        if (status != HoldStatus.PENDING) {
            throw new InvalidHoldStateException("Hold is not pending: " + status);
        }
    }

    /**
     * Returns the owning wallet identity.
     */
    public WalletId getWalletId() {
        return walletId;
    }

    /**
     * Returns the immutable full reservation amount.
     */
    public Money getAmount() {
        return amount;
    }

    /**
     * Returns the business reason for the reservation.
     */
    public String getReason() {
        return reason;
    }

    /**
     * Returns the caller's business reference, not an idempotency guarantee.
     */
    public String getReferenceId() {
        return referenceId;
    }

    /**
     * Returns the exclusive capture deadline.
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Returns the immutable creation timestamp.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the stored lifecycle state, which may be pending after the deadline.
     */
    public HoldStatus getStatus() {
        return status;
    }

    /**
     * Returns the terminal transition time, absent for pending holds.
     */
    public Optional<Instant> getResolvedAt() {
        return Optional.ofNullable(resolvedAt);
    }
}
