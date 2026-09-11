# Database Design — `com.skillswap.app.ledger.sql`

This document describes the SQL persistence layer added to the ledger module (see
`docs/INTEGRITY_AND_IDEMPOTENCY.md` for the in-memory engine it durably backs, and
`docs/THREAT_MODEL.md` for the module's security posture). Schema files live in
`app/src/main/resources/db/migration/`; the Java code that runs and uses them lives
in `app/src/main/java/com/skillswap/app/ledger/sql/`.

## Why this exists, and why it doesn't rebuild the app

SkillSwap's only persistence before this pass was Firebase Realtime Database — no
SQL anywhere in the repository (see `AUDIT.md` §8, where this was flagged as an
honest gap rather than something to fake). Rather than retrofitting SQL into the
Android app's own data model (which is genuinely Firebase-shaped: a JSON tree with
path-based security rules, not a relational schema — see `BUILD_NOTES.md` for why
that choice was made), this pass adds a real, minimal, standalone relational
persistence layer for the ledger module — the one part of this codebase whose domain
(transactions, balances, referential relationships, uniqueness) actually calls for a
relational database. Nothing about the Android app changed.

## Entities and tables

Two tables, plus one bookkeeping table for the migration mechanism itself:

```
accounts
  account_id   VARCHAR(255)   PK
  is_system    BOOLEAN
  balance      NUMERIC(14,2)
  created_at   TIMESTAMP

ledger_entries
  id               BIGINT         PK (surrogate, IDENTITY)
  sequence_number  BIGINT         UNIQUE  (the chain's logical position)
  transaction_id   VARCHAR(36)    UNIQUE
  idempotency_key  VARCHAR(255)   UNIQUE  <-- the idempotency mechanism
  payer_id         VARCHAR(255)   FK -> accounts.account_id
  payee_id         VARCHAR(255)   FK -> accounts.account_id
  amount           NUMERIC(14,2)
  description      VARCHAR(1000)
  previous_hash    CHAR(64)
  entry_hash       CHAR(64)       UNIQUE
  recorded_at      TIMESTAMP(6)

schema_migrations   (version, applied_at) -- tracks which migration files have run
```

Full DDL with every constraint's rationale as an inline comment:
`app/src/main/resources/db/migration/V1__create_accounts_table.sql`,
`V2__create_ledger_entries_table.sql`, `V3__create_ledger_entries_indexes.sql`.

## Relationships

`ledger_entries.payer_id` and `ledger_entries.payee_id` are both foreign keys to
`accounts.account_id`. This means **you cannot record a transaction against an
account that doesn't exist** — enforced by the database, not by application
discipline. An account must be created (`JdbcLedgerStore.createAccountIfAbsent`)
before it can appear in any transaction; `appendingForAnAccountThatDoesNotExistRollsBackCleanly_foreignKeyViolation`
proves the whole multi-statement append rolls back cleanly when this is violated.

There is deliberately **no** foreign key from `ledger_entries.previous_hash` to
another row's `entry_hash`, even though that link is exactly what makes this an
append-only *chain*. See "Tradeoffs" below for why that specific, tempting-looking
FK would be actively misleading rather than merely unnecessary.

## Constraints, and what each one is actually for

| Constraint | Table.column(s) | Defends against |
|---|---|---|
| `PRIMARY KEY` | `accounts.account_id` | Two rows claiming to be the same account |
| `PRIMARY KEY` | `ledger_entries.id` | A technical row-identity requirement; not business-meaningful (see "Tradeoffs") |
| `UNIQUE` | `ledger_entries.idempotency_key` | **The actual duplicate/idempotency-detection mechanism.** A second INSERT reusing a key fails this constraint; `JdbcLedgerStore.append` catches that failure and resolves it as a replay or a conflict — see `docs/INTEGRITY_AND_IDEMPOTENCY.md` |
| `UNIQUE` | `ledger_entries.sequence_number` | Two entries can never occupy the same position in the domain-assigned chain |
| `UNIQUE` | `ledger_entries.transaction_id` | A generated transaction id colliding with an existing one — almost certainly a bug, not a business event, if it ever happens (see the dedicated test for this exact case) |
| `UNIQUE` | `ledger_entries.entry_hash` | Two entries hashing identically — an HMAC-SHA256 collision is practically impossible, so this is really catching "the same content hashed twice by mistake" |
| `NOT NULL` | every column except `description` (defaults to `''`) | Missing required data — the baseline every other constraint here builds on |
| `CHECK (is_system OR balance >= 0)` | `accounts` | A non-system account's balance going negative — the database's own backstop for the same rule `JdbcLedgerStore.debit`'s guarded UPDATE already enforces (see "Consistency assumptions") |
| `CHECK (payer_id <> payee_id)` | `ledger_entries` | Self-transactions — mirrors `TransactionRequest`'s constructor rule, independently, at the database layer |
| `CHECK (amount > 0 AND amount <= 1000000.00)` | `ledger_entries` | Mirrors `TransactionRequest.MIN_AMOUNT`/`MAX_AMOUNT`, independently, at the database layer |
| `FOREIGN KEY` (×2) | `ledger_entries.payer_id`, `payee_id` | Recording a transaction against a nonexistent account |

## Indexes, and why each one exists (not "index everything")

- `idx_ledger_entries_payer_sequence (payer_id, sequence_number)` and
  `idx_ledger_entries_payee_sequence (payee_id, sequence_number)` — support the one
  real query this store needs to serve efficiently: *"give me this account's
  transaction history, in order."* The leading column is the equality filter; the
  trailing column matches the natural `ORDER BY` so no separate sort step is needed.
  This is the SQL-layer equivalent of `TransactionLedger.reconcileBalances()`'s
  per-account replay.
- **No index on `entry_hash`/`previous_hash` beyond what their `UNIQUE` constraints
  already create.** There is no query pattern that looks up an entry *by* hash — the
  chain is always walked in `sequence_number` order (see `findAll()`), never
  hash-first. An index nothing queries against is pure write-overhead with no
  read-side benefit.
- **No index on `description`.** Never filtered or sorted on. Indexing a column
  nothing queries is exactly the "fake complexity" this pass was asked to avoid.
- The four `UNIQUE` constraints on `ledger_entries` already create their own
  supporting indexes (a standard consequence of declaring `UNIQUE` in every
  mainstream RDBMS, including both H2 and PostgreSQL) — `idempotency_key` lookups
  (`findByIdempotencyKey`) and duplicate detection are already served by that index
  with no separate `CREATE INDEX` needed.

## Transaction boundaries

Every `JdbcLedgerStore.append(entry)` call is exactly one JDBC transaction:

```
BEGIN
  INSERT INTO ledger_entries (...)
  UPDATE accounts SET balance = balance - ? WHERE account_id = ? AND (is_system OR balance >= ?)   -- payer debit, guarded
  UPDATE accounts SET balance = balance + ? WHERE account_id = ?                                     -- payee credit
COMMIT   -- or ROLLBACK if any of the above failed
```

All three statements commit together or none of them do — there is no point between
the `INSERT` and the `COMMIT` where a caller could observe a half-applied
transaction (a new entry with no matching balance change, or vice versa). This is
tested directly by `insufficientFundsRollsBackTheWholeTransaction_noPartialState`,
which asserts not just that the failing debit didn't happen, but that the entry
*row itself* was never persisted either — proving the rollback covers the whole
transaction, not just the statement that happened to fail.

`createAccountIfAbsent` and read operations (`findByIdempotencyKey`, `findAll`,
`getBalance`) are each their own single-statement, effectively-auto-committed unit
of work — there's no multi-statement invariant they need to protect, so they don't
need an explicit transaction boundary beyond what JDBC gives every statement by
default.

## Consistency assumptions — how application logic and database constraints relate

This is the part most likely to come up in review, so it's stated explicitly rather
than left to be inferred: **the application-level checks and the database
constraints are not duplicating each other's job by accident — each layer protects
against a different class of failure, and removing either one would leave a real
gap.**

- **`TransactionRequest`'s constructor** (Java) validates a request *before* it's
  ever sent anywhere — catching a caller's mistake as early and cheaply as possible,
  with a precise, typed exception (`InvalidTransactionException`).
- **The database's `CHECK`/`NOT NULL`/FK constraints** validate the same rules again,
  independently, at the moment of persistence — catching anything that reached this
  far *without* going through the validated constructor (a bug elsewhere in the
  process, a direct SQL script, a future caller who forgot). `rawInsertWithANullPayerIsRejectedByTheNotNullConstraint`
  and its neighboring tests prove these constraints do real work by bypassing the
  Java API entirely and hitting the database with raw SQL.
- **`JdbcLedgerStore.debit`'s guarded `UPDATE ... WHERE balance >= ?`** is the
  *primary* mechanism preventing a negative balance — it's atomic (check-and-update
  in one statement, so two concurrent debits against the same account can't both
  pass a stale check) and gives a precise, informative failure
  (`InsufficientFundsException` with the actual amounts).
- **The `accounts.balance` `CHECK` constraint** is the backstop *behind* that guard,
  not a second copy of the same logic path: it exists specifically for the
  hypothetical future caller who writes a naive, unguarded `UPDATE accounts SET
  balance = balance - ?` without the `WHERE balance >= ?` clause.
  `rawUnguardedUpdateThatWouldMakeABalanceNegativeIsRejectedByTheAccountsCheckConstraint`
  proves the database catches that mistake even when the guarded application code
  path is bypassed entirely.
- **The `idempotency_key` `UNIQUE` constraint** and the in-memory
  `TransactionLedger`'s `ConcurrentHashMap`-based idempotency check
  (`docs/INTEGRITY_AND_IDEMPOTENCY.md` §3.2) are not redundant either: the in-memory
  check only coordinates callers *within one JVM instance*. The database constraint
  is what would still hold if two separate processes (or two separate
  `TransactionLedger` instances) both tried to persist the same logical transaction
  against a shared database — closing exactly the "multi-process idempotency" gap
  `docs/INTEGRITY_AND_IDEMPOTENCY.md` §7 named as a limitation of the in-memory
  engine alone.

**What this schema assumes and does not itself guarantee:** it assumes whatever
process is writing to it already produced a correctly hash-chained `LedgerEntry`
(sequence number, `previous_hash`, `entry_hash` all internally consistent) before
calling `append` — the database's constraints check *relational* integrity
(uniqueness, referential existence, value ranges), not *cryptographic* integrity.
Verifying the hash chain itself is `LedgerIntegrityVerifier`'s job (pure Java,
reused unchanged from `docs/INTEGRITY_AND_IDEMPOTENCY.md`), run against
`JdbcLedgerStore.findAll()`'s result — deliberately not reimplemented in SQL. See
"Tradeoffs" for why.

## Sample SQL queries

```sql
-- An account's transaction history, in order (served by idx_ledger_entries_payer_sequence
-- / idx_ledger_entries_payee_sequence):
SELECT sequence_number, transaction_id, payer_id, payee_id, amount, description, recorded_at
FROM ledger_entries
WHERE payer_id = 'alice' OR payee_id = 'alice'
ORDER BY sequence_number;

-- Recompute an account's balance purely from the log (the SQL-layer analogue of
-- TransactionLedger.reconcileBalances() -- a real audit query, not a cache lookup):
SELECT
    COALESCE(SUM(CASE WHEN payee_id = 'alice' THEN amount ELSE 0 END), 0)
  - COALESCE(SUM(CASE WHEN payer_id = 'alice' THEN amount ELSE 0 END), 0) AS computed_balance
FROM ledger_entries
WHERE payer_id = 'alice' OR payee_id = 'alice';

-- Has this idempotency key already been used? (served directly by its UNIQUE index)
SELECT * FROM ledger_entries WHERE idempotency_key = ?;

-- Every account whose cached balance disagrees with a from-scratch replay of the log
-- -- the kind of query that would catch a bug in the balance-update logic itself:
SELECT a.account_id, a.balance AS cached_balance, replay.computed_balance
FROM accounts a
JOIN (
    SELECT account_id,
           COALESCE(SUM(credit), 0) - COALESCE(SUM(debit), 0) AS computed_balance
    FROM (
        SELECT payee_id AS account_id, amount AS credit, 0 AS debit FROM ledger_entries
        UNION ALL
        SELECT payer_id AS account_id, 0 AS credit, amount AS debit FROM ledger_entries
        WHERE payer_id <> 'SYSTEM'
    ) movements
    GROUP BY account_id
) replay ON replay.account_id = a.account_id
WHERE a.balance <> replay.computed_balance;
```

## Tradeoffs

| Decision | What it costs | What it buys |
|---|---|---|
| Two separate keys on `ledger_entries` (`id` surrogate PK vs. `sequence_number` domain key) instead of one | An extra column, and a moment's explanation of why | Solves a real chicken-and-egg problem: an entry's HMAC has to cover its own position, but a database `IDENTITY` value isn't known until *after* the row is inserted. The domain layer (`TransactionLedger`/`LedgerEntryFactory`) already assigns `sequence_number` correctly *before* the entry is built; this schema persists that decision instead of trying to re-derive chain position from a value that's only available too late to hash. This is the schema working *with* the existing, already-tested Java design rather than against it. |
| No self-referencing FK from `previous_hash` to `entry_hash` | Chain-breakage (reordering/deletion) isn't caught by the database itself, only by `LedgerIntegrityVerifier` in application code | Avoids a constraint that would be actively misleading: (1) the genesis entry's `previous_hash` is a sentinel that matches no real row, which a naive FK would reject outright; (2) even a *satisfied* FK here would only prove "this hash exists somewhere as some entry's `entry_hash`" — not that it's cryptographically correct, which is the property that actually matters and which no portable SQL `CHECK`/FK can express (HMAC-SHA256 isn't a function any mainstream SQL engine can compute natively). A FK that looks like an integrity guarantee but isn't one is worse than no FK at all. |
| Hash-chain verification stays in Java (`LedgerIntegrityVerifier`), not reimplemented in SQL | An auditor has to run application code, not just a SQL query, to fully verify the chain | Correctness by reuse: the exact same, already-tested verifier from `docs/INTEGRITY_AND_IDEMPOTENCY.md` works against both the in-memory ledger and this SQL store's `findAll()` result, with zero duplicated crypto logic to keep in sync |
| A hand-written `SchemaMigrator` instead of Flyway/Liquibase | No dependency-provided rollback/checksum/repair tooling | Three DDL files don't need a framework; the whole mechanism (apply-in-order, record in `schema_migrations`, one transaction per file) is readable end-to-end in under a minute — pulling in a real migration framework for this would be exactly the "fake complexity" this pass was asked to avoid |
| H2 (embedded, in-JVM) instead of Testcontainers+real PostgreSQL for automated tests | Not literally running against PostgreSQL; H2's `MODE=PostgreSQL` narrows but doesn't eliminate the gap | Tests run with no external process, no Docker daemon required (this sandbox has none running — see `TestDatabases`' Javadoc) and no network dependency, while the schema itself is written in portable, standard SQL specifically so it would run against real PostgreSQL largely unchanged |
| The atomic guarded `UPDATE ... WHERE balance >= ?` pattern instead of `SELECT ... FOR UPDATE` then `UPDATE` | Slightly less obvious to a reader unfamiliar with the pattern (worth this comment existing at all) | One round trip instead of two; no explicit row lock to remember to release; the WHERE clause itself is the concurrency control, so there's no way to forget to check sufficiency before mutating — the check and the mutation are literally the same statement |
| `NUMERIC(14,2)` amounts, matched to `TransactionRequest`'s own scale-2 normalization | A hard, arbitrary policy ceiling (`999,999,999,999.99`) | Exact decimal arithmetic (no `float`/`double` anywhere in this module, on either side of the JDBC boundary) and a schema that documents its own precision contract instead of leaving it to an implicit default |
| `TIMESTAMP(6)` (explicit microsecond precision), with `Instant` rounded to microseconds before hashing | This was a real bug caught during development, not just a hypothetical: an untruncated `java.time.Instant` (nanosecond precision) hashed one way and read back rounded to microseconds by H2's storage engine hashed differently, breaking the chain on every entry the moment it round-tripped through the database. Naively *truncating* (rather than rounding) to microseconds was tried first and was still wrong, because H2 (and standard SQL fixed-precision timestamps generally) *round to nearest*, not truncate, on storage. | A single, explicit rounding function (`LedgerEntry`'s private `round(Instant)`) applied once, before hashing, so every consumer — in-memory or SQL-backed — hashes the identical value regardless of how many times it round-trips through a database with microsecond-or-coarser precision. See `docs/INTEGRITY_AND_IDEMPOTENCY.md`'s changelog for this fix and the exact commit that caught it. |
