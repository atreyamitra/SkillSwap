package com.skillswap.app.ledger;

import java.util.Objects;

/**
 * The outcome of a successful {@code TransactionLedger.submit(...)} call: either the
 * result of freshly committing a new entry ({@code replayed == false}), or the result
 * handed back to a caller whose idempotency key had already been processed —
 * possibly by another thread, possibly moments or days earlier
 * ({@code replayed == true}). Both carry the exact same {@code transactionId},
 * {@code sequenceNumber}, and {@code entryHash} — replay never re-executes the
 * transaction, it only reports what already happened.
 */
public final class TransactionResult {

    private final String transactionId;
    private final long sequenceNumber;
    private final String entryHash;
    private final boolean replayed;

    public TransactionResult(String transactionId, long sequenceNumber, String entryHash, boolean replayed) {
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.sequenceNumber = sequenceNumber;
        this.entryHash = Objects.requireNonNull(entryHash, "entryHash");
        this.replayed = replayed;
    }

    /** Returns a copy of this result with {@code replayed} forced to {@code true},
     *  or {@code this} unchanged if it already was. Used by the ledger to mark the
     *  result handed to a caller that joined an in-flight or already-completed
     *  submission, without mutating the original (shared, possibly still-referenced)
     *  instance. */
    TransactionResult asReplay() {
        return replayed ? this : new TransactionResult(transactionId, sequenceNumber, entryHash, true);
    }

    public String getTransactionId() { return transactionId; }
    public long getSequenceNumber() { return sequenceNumber; }
    public String getEntryHash() { return entryHash; }
    public boolean isReplayed() { return replayed; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransactionResult)) return false;
        TransactionResult other = (TransactionResult) o;
        return sequenceNumber == other.sequenceNumber && transactionId.equals(other.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId, sequenceNumber);
    }

    @Override
    public String toString() {
        return "TransactionResult{transactionId='" + transactionId + "', sequenceNumber=" + sequenceNumber
                + ", replayed=" + replayed + '}';
    }
}
