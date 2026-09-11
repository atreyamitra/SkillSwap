package com.skillswap.app.ledger.sql;

import com.skillswap.app.ledger.LedgerEntry;
import com.skillswap.app.ledger.TransactionLedger;
import com.skillswap.app.ledger.exception.IdempotencyKeyConflictException;
import com.skillswap.app.ledger.exception.InsufficientFundsException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Durable, SQL-backed persistence for {@link LedgerEntry} rows, enforcing the same
 * integrity and idempotency guarantees {@code TransactionLedger} provides in memory
 * — but as real database constraints, which is what makes them hold even across
 * process restarts or multiple concurrently-connected writers, not just within one
 * JVM's lock. See {@code docs/DATABASE_DESIGN.md} for the full schema rationale.
 *
 * <p><b>Scope, stated plainly:</b> this class persists an already-fully-formed
 * {@code LedgerEntry} (its {@code sequenceNumber}, {@code previousHash}, and
 * {@code entryHash} are computed by the existing, already-tested Java domain layer
 * before ever reaching this class — see {@code docs/DATABASE_DESIGN.md}
 * "Tradeoffs" for why the database's own {@code id} column does not double as that
 * chain position). This class's job is narrower and more mechanical: get the entry
 * onto durable storage exactly once, atomically, with its account-balance side
 * effects, and refuse anything that violates a relational constraint.
 */
public final class JdbcLedgerStore {

    private final DataSource dataSource;

    public JdbcLedgerStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /**
     * Creates an account if it doesn't already exist. Idempotent by design — a
     * second call for the same {@code accountId} is a silent no-op, not an error,
     * because "make sure this account exists" is naturally a repeatable setup
     * operation, unlike appending a transaction (where a repeat is meaningful and
     * must be either a safe replay or a loud conflict — see {@link #append}).
     */
    public void createAccountIfAbsent(String accountId, boolean isSystem, BigDecimal initialBalance) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(initialBalance, "initialBalance");
        String sql = "INSERT INTO accounts (account_id, is_system, balance) VALUES (?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.setBoolean(2, isSystem);
            statement.setBigDecimal(3, initialBalance);
            statement.executeUpdate();
        } catch (SQLException e) {
            if (isUniqueOrPrimaryKeyViolation(e)) {
                return; // already exists; treated as success, see Javadoc above
            }
            throw new LedgerPersistenceException("Failed to create account " + accountId, e);
        }
    }

    /**
     * Persists {@code entry} atomically along with its balance effects: the entry
     * row, the payer's debit, and the payee's credit either all commit together or
     * none of them do — there is no statement between the INSERT and the COMMIT
     * that isn't part of the same transaction.
     *
     * <p>Three distinct failure outcomes, each backed by a specific constraint:
     * <ul>
     *   <li>Reusing an idempotency key with an <b>identical</b> payload (same payer,
     *       payee, amount, description) — detected via
     *       {@code uq_ledger_entries_idempotency_key} — returns the original row
     *       with {@code alreadyExisted = true}. Nothing new is written.</li>
     *   <li>Reusing an idempotency key with a <b>different</b> payload — same
     *       constraint, different resolution — throws
     *       {@link IdempotencyKeyConflictException}, the identical exception type
     *       {@code TransactionLedger} throws for the same situation in memory.</li>
     *   <li>A debit that would take a non-system account negative — detected by the
     *       guarded {@code UPDATE ... WHERE balance >= ?} (backed by
     *       {@code chk_accounts_balance_non_negative} as a second line of defense) —
     *       throws {@link InsufficientFundsException}, again the same type the
     *       in-memory engine throws.</li>
     * </ul>
     */
    public AppendOutcome append(LedgerEntry entry) {
        Objects.requireNonNull(entry, "entry");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                insertEntry(connection, entry);
                if (!entry.getPayerId().equals(TransactionLedger.SYSTEM_ACCOUNT)) {
                    debit(connection, entry.getPayerId(), entry.getAmount());
                }
                credit(connection, entry.getPayeeId(), entry.getAmount());
                connection.commit();
                return new AppendOutcome(entry, false);
            } catch (SQLException e) {
                rollbackQuietly(connection);
                if (isUniqueViolation(e, "IDEMPOTENCY_KEY")) {
                    return resolveIdempotencyOutcome(connection, entry);
                }
                throw new LedgerPersistenceException("Failed to append ledger entry " + entry.getTransactionId(), e);
            } catch (RuntimeException e) {
                // InsufficientFundsException (or any other unchecked failure) surfaces
                // from debit()/credit() without being an SQLException — still must be
                // rolled back explicitly rather than relying on close()-time behavior.
                rollbackQuietly(connection);
                throw e;
            }
        } catch (SQLException e) {
            throw new LedgerPersistenceException("Failed to obtain a database connection", e);
        }
    }

    private void insertEntry(Connection connection, LedgerEntry entry) throws SQLException {
        String sql = "INSERT INTO ledger_entries " +
                "(sequence_number, transaction_id, idempotency_key, payer_id, payee_id, amount, " +
                " description, previous_hash, entry_hash, recorded_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, entry.getSequenceNumber());
            statement.setString(2, entry.getTransactionId());
            statement.setString(3, entry.getIdempotencyKey());
            statement.setString(4, entry.getPayerId());
            statement.setString(5, entry.getPayeeId());
            statement.setBigDecimal(6, entry.getAmount());
            statement.setString(7, entry.getDescription());
            statement.setString(8, entry.getPreviousHash());
            statement.setString(9, entry.getEntryHash());
            statement.setTimestamp(10, java.sql.Timestamp.from(entry.getRecordedAt()));
            statement.executeUpdate();
        }
    }

    /**
     * Debits {@code accountId} by {@code amount} in one atomic, guarded statement —
     * the WHERE clause checks sufficiency and performs the update together, so two
     * concurrent debits against the same account cannot both read "sufficient" and
     * both proceed (the classic check-then-act race). This is deliberately not a
     * separate SELECT-then-UPDATE: whichever of two concurrent transactions commits
     * first changes the row that the second one's WHERE clause re-evaluates against
     * (the database's own row-level locking during the UPDATE serializes this,
     * without this code needing an explicit {@code SELECT ... FOR UPDATE}).
     */
    private void debit(Connection connection, String accountId, BigDecimal amount) throws SQLException {
        String sql = "UPDATE accounts SET balance = balance - ? " +
                "WHERE account_id = ? AND (is_system OR balance >= ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, amount);
            statement.setString(2, accountId);
            statement.setBigDecimal(3, amount);
            int updated = statement.executeUpdate();
            if (updated == 0) {
                // The row is guaranteed to exist: insertEntry() above already succeeded,
                // and its foreign key to accounts(account_id) would have failed the
                // whole INSERT otherwise. So zero rows updated here can only mean the
                // WHERE clause's sufficiency check failed, not a missing account.
                BigDecimal available = getBalance(connection, accountId);
                throw new InsufficientFundsException(accountId, amount, available);
            }
        }
    }

    private void credit(Connection connection, String accountId, BigDecimal amount) throws SQLException {
        String sql = "UPDATE accounts SET balance = balance + ? WHERE account_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, amount);
            statement.setString(2, accountId);
            statement.executeUpdate();
        }
    }

    private AppendOutcome resolveIdempotencyOutcome(Connection connection, LedgerEntry attempted) throws SQLException {
        connection.setAutoCommit(true);
        Optional<LedgerEntry> existing = findByIdempotencyKey(connection, attempted.getIdempotencyKey());
        if (existing.isEmpty()) {
            throw new LedgerPersistenceException(
                    "Unique violation on idempotency_key but no row was found for key "
                            + attempted.getIdempotencyKey(), null);
        }
        if (isSamePayload(existing.get(), attempted)) {
            return new AppendOutcome(existing.get(), true);
        }
        throw new IdempotencyKeyConflictException(attempted.getIdempotencyKey());
    }

    private static boolean isSamePayload(LedgerEntry existing, LedgerEntry attempted) {
        return existing.getPayerId().equals(attempted.getPayerId())
                && existing.getPayeeId().equals(attempted.getPayeeId())
                && existing.getAmount().equals(attempted.getAmount()) // both scale 2, see LedgerEntry/TransactionRequest
                && existing.getDescription().equals(attempted.getDescription());
    }

    public Optional<LedgerEntry> findByIdempotencyKey(String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        try (Connection connection = dataSource.getConnection()) {
            return findByIdempotencyKey(connection, idempotencyKey);
        } catch (SQLException e) {
            throw new LedgerPersistenceException("Failed to look up idempotency key " + idempotencyKey, e);
        }
    }

    private Optional<LedgerEntry> findByIdempotencyKey(Connection connection, String idempotencyKey) throws SQLException {
        String sql = "SELECT * FROM ledger_entries WHERE idempotency_key = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, idempotencyKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        }
    }

    /** All entries, in ledger order — the SQL-layer equivalent of
     *  {@code TransactionLedger.snapshot()}. */
    public List<LedgerEntry> findAll() {
        List<LedgerEntry> results = new ArrayList<>();
        String sql = "SELECT * FROM ledger_entries ORDER BY sequence_number ASC";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                results.add(mapRow(resultSet));
            }
            return results;
        } catch (SQLException e) {
            throw new LedgerPersistenceException("Failed to load ledger entries", e);
        }
    }

    public BigDecimal getBalance(String accountId) {
        try (Connection connection = dataSource.getConnection()) {
            return getBalance(connection, accountId);
        } catch (SQLException e) {
            throw new LedgerPersistenceException("Failed to read balance for " + accountId, e);
        }
    }

    private BigDecimal getBalance(Connection connection, String accountId) throws SQLException {
        String sql = "SELECT balance FROM accounts WHERE account_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getBigDecimal("balance") : BigDecimal.ZERO;
            }
        }
    }

    private static LedgerEntry mapRow(ResultSet resultSet) throws SQLException {
        return new LedgerEntry(
                resultSet.getLong("sequence_number"),
                resultSet.getString("transaction_id"),
                resultSet.getString("idempotency_key"),
                resultSet.getString("payer_id"),
                resultSet.getString("payee_id"),
                resultSet.getBigDecimal("amount"),
                resultSet.getString("description"),
                resultSet.getTimestamp("recorded_at").toInstant(),
                resultSet.getString("previous_hash"),
                resultSet.getString("entry_hash")
        );
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Nothing meaningful to do if rollback itself fails while already handling
            // another failure; the connection is about to be closed by the caller's
            // try-with-resources either way.
        }
    }

    /** ANSI SQLSTATE class "23" is integrity constraint violation; "23505" is
     *  specifically a unique/primary-key violation. Matched by class prefix "23505"
     *  rather than an exact vendor error code so this works the same way against
     *  both H2 and a real PostgreSQL instance (see docs/DATABASE_DESIGN.md). */
    private static boolean isUniqueOrPrimaryKeyViolation(SQLException e) {
        return "23505".equals(e.getSQLState());
    }

    /** Same SQLSTATE class as above, additionally checking the constraint name
     *  (present in H2's and PostgreSQL's exception messages) to identify WHICH
     *  unique constraint fired — needed because {@code ledger_entries} has four
     *  independent UNIQUE constraints (see V2's migration) and only one of them
     *  (idempotency_key) has special replay-vs-conflict handling; the other three
     *  are real errors, not idempotency signals. */
    private static boolean isUniqueViolation(SQLException e, String constraintNameFragment) {
        if (!isUniqueOrPrimaryKeyViolation(e)) {
            return false;
        }
        String message = e.getMessage();
        return message != null && message.toUpperCase(java.util.Locale.ROOT).contains(constraintNameFragment);
    }
}
