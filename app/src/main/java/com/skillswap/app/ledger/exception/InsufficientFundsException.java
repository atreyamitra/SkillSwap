package com.skillswap.app.ledger.exception;

import java.math.BigDecimal;

/**
 * Thrown when a transaction would drive the payer's balance negative. Unlike
 * {@link InvalidTransactionException}, this can only be determined from ledger state
 * (the payer's current balance), not from the request in isolation — which is exactly
 * why the check has to happen inside the same locked section that performs the debit
 * (see {@code TransactionLedger#commit}); checking it earlier, outside the lock, would
 * be a classic check-then-act race between two concurrent debits against the same
 * account.
 */
public class InsufficientFundsException extends LedgerException {

    private static final long serialVersionUID = 1L;

    public InsufficientFundsException(String payerId, BigDecimal requested, BigDecimal available) {
        super("Payer '" + payerId + "' has insufficient funds: requested " + requested
                + ", available " + available);
    }
}
