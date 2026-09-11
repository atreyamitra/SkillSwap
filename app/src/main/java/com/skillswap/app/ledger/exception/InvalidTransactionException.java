package com.skillswap.app.ledger.exception;

/**
 * Thrown when a {@code TransactionRequest} fails basic validation: a null/blank
 * identifier, a self-transaction (payer == payee), or an amount outside the
 * ledger's accepted range/scale. Always thrown from the constructor, before an
 * idempotency key is ever recorded — so an invalid request never consumes or
 * "poisons" a key, and a corrected retry with the same key succeeds normally.
 */
public class InvalidTransactionException extends LedgerException {

    private static final long serialVersionUID = 1L;

    public InvalidTransactionException(String message) {
        super(message);
    }
}
