package com.skillswap.app.ledger.exception;

/**
 * Base type for failures in the ledger module. Kept deliberately separate from
 * {@code com.skillswap.app.exception.SkillSwapException} (the rest of the app's
 * business-rule exception hierarchy): the two things this hierarchy signals —
 * "this request violates a domain rule" ({@link InvalidTransactionException},
 * {@link InsufficientFundsException}, {@link IdempotencyKeyConflictException}) and
 * "the ledger's data has been tampered with or corrupted"
 * ({@link IntegrityViolationException}) — are different in kind. The first is normal,
 * expected control flow a caller can react to sensibly (show a validation error,
 * retry with a fixed payload). The second is a security event: something that should
 * never happen if the system and its secret key are intact, and a caller catching it
 * should treat it as "stop and investigate," not "handle and continue." Folding both
 * into one hierarchy would blur that distinction.
 */
public abstract class LedgerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected LedgerException(String message) {
        super(message);
    }
}
