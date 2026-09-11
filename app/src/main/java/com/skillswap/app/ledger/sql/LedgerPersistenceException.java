package com.skillswap.app.ledger.sql;

import com.skillswap.app.ledger.exception.LedgerException;

/**
 * Wraps a checked {@link java.sql.SQLException} as unchecked, consistent with the
 * rest of the ledger module's exception style (see {@code LedgerException}'s
 * Javadoc): every caller here is a class that can only log/report a persistence
 * failure, not recover differently based on a checked exception's type, so forcing
 * {@code throws SQLException} through every method signature would add ceremony
 * without adding safety. Reserved for failures that are NOT one of the two specific,
 * meaningful outcomes {@link JdbcLedgerStore} already distinguishes (a duplicate
 * idempotency key, or insufficient funds) — those get their own, more specific
 * exception types instead of this catch-all.
 */
public final class LedgerPersistenceException extends LedgerException {

    private static final long serialVersionUID = 1L;

    public LedgerPersistenceException(String message, Throwable cause) {
        super(message);
        // Throwable's own public initCause(); no (message, cause) super-constructor
        // exists on LedgerException. Safe to call from the constructor here (unlike
        // the general case the compiler warns about) because this class is final —
        // there is no subclass that could override initCause and observe a
        // partially-constructed `this`.
        initCause(cause);
    }
}
