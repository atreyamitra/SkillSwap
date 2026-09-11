package com.skillswap.app.ledger;

import com.skillswap.app.ledger.exception.InvalidTransactionException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A request to move {@code amount} from {@code payerId} to {@code payeeId}, submitted
 * under {@code idempotencyKey}. Immutable and self-validating: there is no way to
 * construct one that violates the invariants below, and no setter that could put an
 * already-constructed instance into an invalid state afterward.
 *
 * <p><b>Amount normalization:</b> {@code amount} is normalized to exactly scale 2
 * (e.g. {@code "5"} and {@code "5.0"} both become {@code "5.00"}) at construction
 * time. This sidesteps a well-known {@link BigDecimal} trap at its source rather than
 * working around it at every comparison site: {@code new BigDecimal("5.0").equals(new
 * BigDecimal("5.00"))} is {@code false} (equals is scale-sensitive) even though the
 * two values are the same amount of money ({@code compareTo} treats them as equal).
 * Normalizing once here means every later {@code equals}/hashCode/canonical-byte
 * computation in this module can safely use plain {@code equals} instead of having to
 * remember to use {@code compareTo} instead — see {@code TransactionRequestTest} for
 * exactly this case.
 */
public final class TransactionRequest {

    /** Smallest transaction amount this ledger will accept. An explicit policy
     *  constant, not a claim about any real currency's smallest unit. */
    public static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");

    /** Largest transaction amount this ledger will accept in a single transaction.
     *  An explicit, arbitrary policy limit — exists so "boundary amount" has a
     *  concrete upper edge to test, not a real-world regulatory limit. */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");

    private static final int SCALE = 2;

    private final String idempotencyKey;
    private final String payerId;
    private final String payeeId;
    private final BigDecimal amount;
    private final String description;

    public TransactionRequest(String idempotencyKey, String payerId, String payeeId,
                               BigDecimal amount, String description) {
        this.idempotencyKey = requireNonBlank(idempotencyKey, "idempotencyKey");
        this.payerId = requireNonBlank(payerId, "payerId");
        this.payeeId = requireNonBlank(payeeId, "payeeId");
        if (this.payerId.equals(this.payeeId)) {
            throw new InvalidTransactionException(
                    "payerId and payeeId must differ (both were '" + this.payerId + "')");
        }
        this.amount = normalizeAmount(amount);
        this.description = description == null ? "" : description;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidTransactionException(fieldName + " must not be null or blank");
        }
        return value;
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidTransactionException("amount must not be null");
        }
        if (amount.scale() > SCALE) {
            throw new InvalidTransactionException(
                    "amount must not have more than " + SCALE + " decimal places (was " + amount + ")");
        }
        // Safe: scale() <= SCALE was just checked, so this only pads zeros, never rounds.
        BigDecimal normalized = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        if (normalized.compareTo(MIN_AMOUNT) < 0) {
            throw new InvalidTransactionException(
                    "amount must be at least " + MIN_AMOUNT + " (was " + amount + ")");
        }
        if (normalized.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidTransactionException(
                    "amount must not exceed " + MAX_AMOUNT + " (was " + amount + ")");
        }
        return normalized;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPayerId() { return payerId; }
    public String getPayeeId() { return payeeId; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }

    /**
     * Used to detect an idempotency-key conflict: two requests submitted under the
     * same key are only interchangeable (safe to treat as "the same retried request")
     * if every other field also matches.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransactionRequest)) return false;
        TransactionRequest other = (TransactionRequest) o;
        return idempotencyKey.equals(other.idempotencyKey)
                && payerId.equals(other.payerId)
                && payeeId.equals(other.payeeId)
                && amount.equals(other.amount) // safe: both sides normalized to scale 2 above
                && description.equals(other.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idempotencyKey, payerId, payeeId, amount, description);
    }

    @Override
    public String toString() {
        return "TransactionRequest{idempotencyKey='" + idempotencyKey + "', payerId='" + payerId
                + "', payeeId='" + payeeId + "', amount=" + amount + '}';
    }
}
