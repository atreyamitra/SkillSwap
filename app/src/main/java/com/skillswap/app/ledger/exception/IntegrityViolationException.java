package com.skillswap.app.ledger.exception;

/**
 * Reserved for callers that want tampering treated as an exception rather than an
 * {@code IntegrityReport} to inspect (see {@code TransactionLedger#verifyIntegrity},
 * which returns a report rather than throwing, so a caller can decide how to react —
 * log, alert, halt — rather than having that decision made for them). A caller that
 * wants "corrupt ledger data must never be silently tolerated" can throw this itself
 * from a failing {@code IntegrityReport}.
 */
public class IntegrityViolationException extends LedgerException {

    private static final long serialVersionUID = 1L;

    public IntegrityViolationException(String message) {
        super(message);
    }
}
