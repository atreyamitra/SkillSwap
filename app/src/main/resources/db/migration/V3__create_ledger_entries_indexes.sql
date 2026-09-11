-- Indexes chosen for the two real access patterns this store is built to serve --
-- not "index everything" and not decorative. See docs/DATABASE_DESIGN.md
-- "Indexes and why they exist" for the full justification of each one, including
-- why entry_hash/previous_hash and description are deliberately NOT indexed.

-- Access pattern 1: "give me this account's transaction history, in ledger order"
-- (used to reconstruct an account's activity, or to recompute its balance by
-- replaying the log -- the SQL-layer equivalent of
-- TransactionLedger.reconcileBalances()). Leading column is the equality filter
-- (payer_id / payee_id); trailing column supports the ORDER BY without a separate
-- sort step.
CREATE INDEX idx_ledger_entries_payer_sequence ON ledger_entries (payer_id, sequence_number);
CREATE INDEX idx_ledger_entries_payee_sequence ON ledger_entries (payee_id, sequence_number);

-- Access pattern 2 (idempotency-key lookup and the chain-position lookups used by
-- LedgerIntegrityVerifier / "find the current tip of the chain") is already served
-- by the UNIQUE constraints on idempotency_key and sequence_number in V2 -- a
-- UNIQUE constraint creates its own supporting index, so no separate CREATE INDEX
-- is needed for those and adding one would just be a redundant, unused duplicate.
