package com.skillswap.app.ledger.sql;

import com.skillswap.app.ledger.LedgerEntry;

import java.util.Objects;

/**
 * The result of {@link JdbcLedgerStore#append}: either the entry was newly persisted,
 * or an entry already existed for that idempotency key and is returned unchanged —
 * the SQL-layer mirror of {@code com.skillswap.app.ledger.TransactionResult}'s
 * {@code replayed} flag.
 */
public final class AppendOutcome {

    private final LedgerEntry entry;
    private final boolean alreadyExisted;

    public AppendOutcome(LedgerEntry entry, boolean alreadyExisted) {
        this.entry = Objects.requireNonNull(entry, "entry");
        this.alreadyExisted = alreadyExisted;
    }

    public LedgerEntry getEntry() { return entry; }

    /** True if this call did not insert a new row — an existing row for the same
     *  idempotency key, with an identical payload, was found and returned instead. */
    public boolean isAlreadyExisted() { return alreadyExisted; }
}
