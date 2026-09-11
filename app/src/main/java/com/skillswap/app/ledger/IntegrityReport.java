package com.skillswap.app.ledger;

/**
 * The result of {@code LedgerIntegrityVerifier.verify(...)}: either "the whole chain
 * is internally consistent" or a specific, located failure. Returned as data rather
 * than thrown as an exception so a caller can decide how to react (log it, alert,
 * halt the process) — see {@code IntegrityViolationException}'s Javadoc for the case
 * where a caller wants to throw instead.
 */
public final class IntegrityReport {

    /**
     * The kind of tampering/corruption detected, if any.
     * <ul>
     *   <li>{@code HASH_MISMATCH} — an entry's stored {@code entryHash} does not match
     *       the HMAC recomputed from its own fields. Means either the entry's data was
     *       altered after being hashed, or the hash itself was corrupted/replaced.</li>
     *   <li>{@code CHAIN_BROKEN} — an entry's {@code previousHash} does not match the
     *       previous entry's {@code entryHash} (or, for the first entry, does not
     *       equal {@code LedgerEntry.GENESIS_HASH}). Means an entry was reordered,
     *       deleted, or inserted, even if every individual entry's own hash is
     *       internally valid.</li>
     * </ul>
     */
    public enum Failure { NONE, HASH_MISMATCH, CHAIN_BROKEN }

    private final boolean valid;
    private final Failure failure;
    private final long firstBadIndex;

    private IntegrityReport(boolean valid, Failure failure, long firstBadIndex) {
        this.valid = valid;
        this.failure = failure;
        this.firstBadIndex = firstBadIndex;
    }

    public static IntegrityReport ok() {
        return new IntegrityReport(true, Failure.NONE, -1);
    }

    public static IntegrityReport failure(Failure failure, long index) {
        if (failure == Failure.NONE) {
            throw new IllegalArgumentException("a failing report needs a real Failure, not NONE");
        }
        return new IntegrityReport(false, failure, index);
    }

    public boolean isValid() { return valid; }
    public Failure getFailure() { return failure; }

    /** The index of the first entry (0-based, in ledger order) where corruption was
     *  detected, or -1 if {@link #isValid()}. */
    public long getFirstBadIndex() { return firstBadIndex; }

    @Override
    public String toString() {
        return valid ? "IntegrityReport{valid}" : "IntegrityReport{" + failure + " at index " + firstBadIndex + "}";
    }
}
