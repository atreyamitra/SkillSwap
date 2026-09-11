-- The durable, append-only ledger log. Mirrors com.skillswap.app.ledger.LedgerEntry
-- field-for-field. This table is only ever INSERTed into by application code
-- (see JdbcLedgerStore) -- there is no UPDATE or DELETE statement anywhere against
-- ledger_entries in this codebase, by design: an audit log that can be edited after
-- the fact isn't one.
--
-- Two different keys serve two different purposes, deliberately not conflated:
--   id              -- a technical surrogate primary key (this row's identity in
--                      THIS table; means nothing outside the database).
--   sequence_number -- the ledger's own domain-assigned chain position, computed
--                      and hash-chained by the existing, already-tested Java layer
--                      (com.skillswap.app.ledger.TransactionLedger) BEFORE the entry
--                      is ever handed to this table. See docs/DATABASE_DESIGN.md
--                      "Tradeoffs" for why these are two separate columns instead of
--                      letting the database's IDENTITY column double as the chain
--                      position (short answer: the cryptographic hash of an entry
--                      has to cover that entry's position, but an IDENTITY value
--                      isn't known until after the INSERT commits -- a chicken-and-egg
--                      problem the domain layer already solves correctly, so this
--                      table persists that decision rather than re-deriving it).
CREATE TABLE ledger_entries (
    id               BIGINT         GENERATED ALWAYS AS IDENTITY,
    sequence_number  BIGINT         NOT NULL,
    transaction_id   VARCHAR(36)    NOT NULL,
    idempotency_key  VARCHAR(255)   NOT NULL,
    payer_id         VARCHAR(255)   NOT NULL,
    payee_id         VARCHAR(255)   NOT NULL,
    amount           NUMERIC(14, 2) NOT NULL,
    description      VARCHAR(1000)  NOT NULL DEFAULT '',
    previous_hash    CHAR(64)       NOT NULL,
    entry_hash       CHAR(64)       NOT NULL,
    -- Explicit microsecond precision: java.time.Instant is truncated to microseconds
    -- before it's hashed (see LedgerEntry.canonicalBytes), specifically because
    -- PostgreSQL's timestamp type never preserves more than microsecond precision
    -- regardless of what's declared -- this column's precision is written to match
    -- that hashing contract exactly, not left to an implementation default.
    recorded_at      TIMESTAMP(6)   NOT NULL,

    CONSTRAINT pk_ledger_entries PRIMARY KEY (id),

    -- The idempotency key is the actual duplicate/replay-detection mechanism at the
    -- database layer: a second INSERT reusing a key already present fails this
    -- constraint (SQLSTATE 23505), which JdbcLedgerStore.append(...) catches and
    -- turns into either a replay (identical payload) or IdempotencyKeyConflictException
    -- (different payload) -- the exact same two outcomes the in-memory
    -- TransactionLedger produces for the same situation. See
    -- docs/INTEGRITY_AND_IDEMPOTENCY.md for that side of the story.
    CONSTRAINT uq_ledger_entries_idempotency_key UNIQUE (idempotency_key),

    -- A domain chain position must be unique: two entries can never legitimately
    -- occupy the same position in the ledger's history.
    CONSTRAINT uq_ledger_entries_sequence_number UNIQUE (sequence_number),

    -- Each committed transaction has its own generated id; a collision here would
    -- indicate a broken UUID generator, not a normal business condition -- worth
    -- catching loudly rather than silently overwriting a prior transaction's record.
    CONSTRAINT uq_ledger_entries_transaction_id UNIQUE (transaction_id),

    -- Two entries producing the identical HMAC would mean either a hash collision
    -- (practically impossible for HMAC-SHA256) or, more realistically, a bug that
    -- hashed the same content twice -- either way, worth surfacing as a hard failure
    -- rather than allowing it silently.
    CONSTRAINT uq_ledger_entries_entry_hash UNIQUE (entry_hash),

    -- Referential integrity: you cannot record a transaction for an account this
    -- database doesn't know exists. There is deliberately NO foreign key from
    -- previous_hash back to another row's entry_hash -- see
    -- docs/DATABASE_DESIGN.md "Tradeoffs" for why that specific, tempting-looking FK
    -- would be actively misleading here.
    CONSTRAINT fk_ledger_entries_payer FOREIGN KEY (payer_id) REFERENCES accounts (account_id),
    CONSTRAINT fk_ledger_entries_payee FOREIGN KEY (payee_id) REFERENCES accounts (account_id),

    -- Mirrors TransactionRequest's application-level "payer != payee" rule at the
    -- database layer too -- a second, independent line of defense, not a duplicate
    -- of the same code path (see docs/DATABASE_DESIGN.md "Consistency assumptions").
    CONSTRAINT chk_ledger_entries_payer_not_payee CHECK (payer_id <> payee_id),

    -- Mirrors TransactionRequest.MIN_AMOUNT / MAX_AMOUNT. Deliberately the same
    -- policy bounds as the Java layer, enforced independently.
    CONSTRAINT chk_ledger_entries_amount_range CHECK (amount > 0 AND amount <= 1000000.00)
);
