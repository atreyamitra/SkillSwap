package com.skillswap.app.ledger.exception;

/**
 * Thrown when a caller reuses an idempotency key that is already associated with a
 * <em>different</em> request payload (different payer, payee, amount, or
 * description). This is the deliberate, load-bearing failure mode that makes
 * idempotency keys safe: silently accepting "the same key, a different amount" would
 * turn a client bug (or a key collision) into a wrong transaction that succeeds
 * without complaint. Failing loudly here is the whole point — see
 * {@code docs/INTEGRITY_AND_IDEMPOTENCY.md} §2.
 */
public class IdempotencyKeyConflictException extends LedgerException {

    private static final long serialVersionUID = 1L;

    public IdempotencyKeyConflictException(String idempotencyKey) {
        super("Idempotency key '" + idempotencyKey
                + "' was already used with a different request payload");
    }
}
