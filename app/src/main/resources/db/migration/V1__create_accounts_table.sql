-- Accounts referenced by the ledger. A row must exist here before it can appear as
-- a payer or payee in ledger_entries (see V2's foreign keys) -- you cannot record a
-- transaction against an account nobody created, by construction, not by convention.
--
-- is_system marks the ledger's own money-supply account (see
-- TransactionLedger.SYSTEM_ACCOUNT in the Java domain layer): it is exempt from the
-- non-negative balance rule below because it represents the source deposits are
-- drawn from, not a real constrained wallet.
CREATE TABLE accounts (
    account_id  VARCHAR(255)   NOT NULL,
    is_system   BOOLEAN        NOT NULL DEFAULT FALSE,
    balance     NUMERIC(14, 2) NOT NULL DEFAULT 0,
    created_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_accounts PRIMARY KEY (account_id),

    -- Defense in depth: even if every application-level check were somehow bypassed
    -- or buggy, the database itself refuses to let a real account's balance go
    -- negative. See docs/DATABASE_DESIGN.md "Consistency assumptions" for how this
    -- complements (and does not duplicate) JdbcLedgerStore's atomic guarded UPDATE.
    CONSTRAINT chk_accounts_balance_non_negative CHECK (is_system OR balance >= 0)
);
