package com.skillswap.app.ledger.sql;

import com.skillswap.app.ledger.LedgerEntry;
import com.skillswap.app.ledger.LedgerEntryFactory;
import com.skillswap.app.ledger.TransactionLedger;
import com.skillswap.app.ledger.TransactionRequest;
import com.skillswap.app.ledger.exception.IdempotencyKeyConflictException;
import com.skillswap.app.ledger.exception.InsufficientFundsException;
import com.skillswap.app.ledger.exception.LedgerException;
import org.junit.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Integration tests for {@link JdbcLedgerStore}, run against a real H2 database
 * (schema-migrated, not mocked) — see {@code TestDatabases} and
 * {@code docs/DATABASE_DESIGN.md} for why H2 rather than Testcontainers/PostgreSQL
 * in this environment.
 */
public class JdbcLedgerStoreTest {

    private static final byte[] KEY = "jdbc-store-test-key".getBytes(StandardCharsets.UTF_8);
    private static final Clock CLOCK = Clock.systemUTC();

    private static JdbcLedgerStore newStoreWithFundedAccounts(DataSource dataSource) {
        JdbcLedgerStore store = new JdbcLedgerStore(dataSource);
        store.createAccountIfAbsent(TransactionLedger.SYSTEM_ACCOUNT, true, BigDecimal.ZERO);
        store.createAccountIfAbsent("alice", false, BigDecimal.ZERO);
        store.createAccountIfAbsent("bob", false, BigDecimal.ZERO);
        return store;
    }

    private static LedgerEntry deposit(JdbcLedgerStore store, String idempotencyKey, String payee, String amount) {
        LedgerEntry entry = LedgerEntryFactory.next(lastEntryOrNull(store),
                new TransactionRequest(idempotencyKey, TransactionLedger.SYSTEM_ACCOUNT, payee, new BigDecimal(amount), "seed"),
                KEY, CLOCK);
        store.append(entry);
        return entry;
    }

    private static LedgerEntry lastEntryOrNull(JdbcLedgerStore store) {
        List<LedgerEntry> all = store.findAll();
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    // ---- insert ----

    @Test
    public void appendingATransactionPersistsTheEntryAndUpdatesBothBalances() {
        DataSource dataSource = TestDatabases.freshMigratedDatabase();
        JdbcLedgerStore store = newStoreWithFundedAccounts(dataSource);
        deposit(store, "seed-1", "alice", "100.00");

        LedgerEntry transfer = LedgerEntryFactory.next(lastEntryOrNull(store),
                new TransactionRequest("t1", "alice", "bob", new BigDecimal("30.00"), "lunch"), KEY, CLOCK);
        AppendOutcome outcome = store.append(transfer);

        assertFalse(outcome.isAlreadyExisted());
        assertEquals(new BigDecimal("70.00"), store.getBalance("alice"));
        assertEquals(new BigDecimal("30.00"), store.getBalance("bob"));
        assertEquals(2, store.findAll().size()); // the seed deposit + this transfer
    }

    // ---- lookup ----

    @Test
    public void findByIdempotencyKeyReturnsThePersistedEntry() {
        DataSource dataSource = TestDatabases.freshMigratedDatabase();
        JdbcLedgerStore store = newStoreWithFundedAccounts(dataSource);
        LedgerEntry seeded = deposit(store, "seed-1", "alice", "100.00");

        Optional<LedgerEntry> found = store.findByIdempotencyKey("seed-1");
        assertTrue(found.isPresent());
        assertEquals(seeded.getTransactionId(), found.get().getTransactionId());
    }

    @Test
    public void findByIdempotencyKeyReturnsEmptyForAnUnknownKey() {
        DataSource dataSource = TestDatabases.freshMigratedDatabase();
        JdbcLedgerStore store = newStoreWithFundedAccounts(dataSource);
        assertTrue(store.findByIdempotencyKey("never-used").isEmpty());
    }

    @Test
    public void findAllReturnsEntriesInSequenceOrder() {
        DataSource dataSource = TestDatabases.freshMigratedDatabase();
        JdbcLedgerStore store = newStoreWithFundedAccounts(dataSource);
        deposit(store, "seed-1", "alice", "100.00");
        LedgerEntry t1 = LedgerEntryFactory.next(lastEntryOrNull(store),
                new TransactionRequest("t1", "alice", "bob", new BigDecimal("10.00"), ""), KEY, CLOCK);
        store.append(t1);
        LedgerEntry t2 = LedgerEntryFactory.next(lastEntryOrNull(store),
                new TransactionRequest("t2", "alice", "bob", new BigDecimal("5.00"), ""), KEY, CLOCK);
        store.append(t2);

        List<LedgerEntry> all = store.findAll();
        assertEquals(3, all.size());
        assertEquals(0L, all.get(0).getSequenceNumber());
        assertEquals(1L, all.get(1).getSequenceNumber());
        assertEquals(2L, all.get(2).getSequenceNumber());
    }

    @Test
    public void createAccountIfAbsentIsIdempotent() {
        DataSource dataSource = TestDatabases.freshMigratedDatabase();
        JdbcLedgerStore store = new JdbcLedgerStore(dataSource);
        store.createAccountIfAbsent("alice", false, new BigDecimal("5.00"));
        store.createAccountIfAbsent("alice", false, new BigDecimal("999.00")); // ignored, not an error
        assertEquals(new BigDecimal("5.00"), store.getBalance("alice"));
    }

    // ---- uniqueness & idempotency ----

    @Test
    public void repeatingAnIdenticalAppendReplaysWithoutASecondInsertOrBalanceChange() {
        DataSource dataSource = TestDatabases.freshMigratedDatabase();
        JdbcLedgerStore store = newStoreWithFundedAccounts(dataSource);
        deposit(store, "seed-1", "alice", "100.00");

        LedgerEntry transfer = LedgerEntryFactory.next(lastEntryOrNull(store),
                new TransactionRequest("t1", "alice", "bob", new BigDecimal("10.00"), "lunch"), KEY, CLOCK);
        AppendOutcome first = store.append(transfer);
        AppendOutcome second = store.append(transfer); // identical entry, repeated

        assertFalse(first.isAlreadyExisted());
        assertTrue(second.isAlreadyExisted());
        assertEquals(first.getEntry().getTransactionId(), second.getEntry().getTransactionId());
        assertEquals(2, store.findAll().size()); // seed + the ONE transfer, not two
        assertEquals(new BigDecimal("90.00"), store.getBalance("alice")); // debited once, not twice
    }

    @Test
    public void sameIdempotencyKeyWithADifferentAmountThrowsConflictAndInsertsNothing() {
        DataSource dataSource = TestDatabases.freshMigratedDatabase();
        JdbcLedgerStore store = newStoreWithFundedAccounts(dataSource);
        deposit(store, "seed-1", "alice", "100.00");

        LedgerEntry first = LedgerEntryFactory.next(lastEntryOrNull(store),
                new TransactionRequest("t1", "alice", "bob", new BigDecimal("10.00"), "lunch"), KEY, CLOCK);
        store.append(first);

        LedgerEntry conflicting = LedgerEntryFactory.next(lastEntryOrNull(store),
                new TransactionRequest("t1", "alice", "bob", new BigDecimal("20.00"), "lunch"), KEY, CLOCK);
        try {
            store.append(conflicting);
            fail("expected IdempotencyKeyConflictException");
        } catch (IdempotencyKeyConflictException expected) {
            // expected
        }
        assertEquals(2, store.findAll().size()); // seed + the ORIGINAL transfer only
        assertEquals(new BigDecimal("90.00"), store.getBalance("alice")); // unaffected by the rejected attempt
    }

    @Test
    public void duplicateTransactionIdWithADifferentIdempotencyKeyIsRejectedByItsOwnUniqueConstraint() {
        // A genuinely different failure mode from the idempotency-key story: two
        // otherwise-unrelated entries should never share a generated transaction id.
        DataSource dataSource = TestDatabases.freshMigratedDatabase();
        JdbcLedgerStore store = newStoreWithFundedAccounts(dataSource);
        deposit(store, "seed-1", "alice", "100.00");

        LedgerEntry first = LedgerEntryFactory.next(lastEntryOrNull(store),
                new TransactionRequest("t1", "alice", "bob", new BigDecimal("10.00"), ""), KEY, CLOCK);
        store.append(first);

        // Same transactionId as `first`, but a different idempotency key and a
        // correct, freshly-computed hash chain -- the ONLY thing wrong with this
        // entry is the reused transaction_id.
        LedgerEntry reusedTxId = new LedgerEntry(first.getSequenceNumber() + 1, first.getTransactionId(),
                "t2", "alice", "bob", new BigDecimal("5.00"), "", CLOCK.instant(), first.getEntryHash(),
                recomputeHash(2, first.getTransactionId(), "t2", "alice", "bob", new BigDecimal("5.00"), first.getEntryHash()));

        try {
            store.append(reusedTxId);
            fail("expected a persistence failure from the unique constraint on transaction_id");
        } catch (LedgerException expected) {
            // Not IdempotencyKeyConflictException: this constraint violation is a
            // different one (uq_ledger_entries_transaction_id), surfaced as the
            // generic LedgerPersistenceException, not the idempotency-specific type.
            assertTrue(expected instanceof LedgerPersistenceException);
        }
        assertEquals(2, store.findAll().size()); // seed + `first` only; the reuse attempt never landed
    }

    private static String recomputeHash(long seq, String txId, String idemKey, String payer, String payee,
                                         BigDecimal amount, String previousHash) {
        // Deliberately not reusing LedgerEntryFactory here: this helper needs to
        // produce a hash that is internally self-consistent (so the test isolates
        // JUST the transaction_id uniqueness constraint, not an unrelated HMAC
        // mismatch) without going through the normal chain-building path.
        byte[] canonical = LedgerEntry.canonicalBytes(seq, txId, idemKey, payer, payee, amount, "",
                java.time.Instant.now(), previousHash);
        return com.skillswap.app.ledger.crypto.HmacUtil.hex(com.skillswap.app.ledger.crypto.HmacUtil.compute(KEY, canonical));
    }

    // ---- rollback on failure ----

    @Test
    public void insufficientFundsRollsBackTheWholeTransaction_noPartialState() {
        DataSource dataSource = TestDatabases.freshMigratedDatabase();
        JdbcLedgerStore store = newStoreWithFundedAccounts(dataSource);
        deposit(store, "seed-1", "alice", "5.00");
        int entriesBefore = store.findAll().size();

        LedgerEntry tooMuch = LedgerEntryFactory.next(lastEntryOrNull(store),
                new TransactionRequest("t1", "alice", "bob", new BigDecimal("10.00"), ""), KEY, CLOCK);
        try {
            store.append(tooMuch);
            fail("expected InsufficientFundsException");
        } catch (InsufficientFundsException expected) {
            // expected
        }

        // Nothing partially applied: no new row, and NEITHER balance moved (proving
        // the entry INSERT itself was rolled back along with the failed debit, not
        // just the debit alone).
        assertEquals(entriesBefore, store.findAll().size());
        assertEquals(new BigDecimal("5.00"), store.getBalance("alice"));
        // new BigDecimal("0.00"), not BigDecimal.ZERO: the NUMERIC(14,2) column always
        // returns a scale-2 value, and BigDecimal.equals() is scale-sensitive
        // (BigDecimal.ZERO has scale 0) -- the exact trap documented in
        // TransactionRequest's Javadoc, caught here by this test itself.
        assertEquals(new BigDecimal("0.00"), store.getBalance("bob"));
        assertTrue(store.findByIdempotencyKey("t1").isEmpty());
    }

    @Test
    public void appendingForAnAccountThatDoesNotExistRollsBackCleanly_foreignKeyViolation() {
        DataSource dataSource = TestDatabases.freshMigratedDatabase();
        JdbcLedgerStore store = new JdbcLedgerStore(dataSource);
        store.createAccountIfAbsent(TransactionLedger.SYSTEM_ACCOUNT, true, BigDecimal.ZERO);
        store.createAccountIfAbsent("alice", false, BigDecimal.ZERO);
        // Note: "bob" was never created via createAccountIfAbsent.

        LedgerEntry deposit = LedgerEntryFactory.next(null,
                new TransactionRequest("seed-1", TransactionLedger.SYSTEM_ACCOUNT, "alice", new BigDecimal("100.00"), ""),
                KEY, CLOCK);
        store.append(deposit);

        LedgerEntry toGhostAccount = LedgerEntryFactory.next(deposit,
                new TransactionRequest("t1", "alice", "bob", new BigDecimal("10.00"), ""), KEY, CLOCK);
        try {
            store.append(toGhostAccount);
            fail("expected a foreign key violation, wrapped as LedgerPersistenceException");
        } catch (LedgerPersistenceException expected) {
            // expected
        }

        assertEquals(1, store.findAll().size()); // only the seed deposit; nothing else landed
        assertEquals(new BigDecimal("100.00"), store.getBalance("alice")); // untouched by the failed attempt
    }

    // ---- database constraints as an independent safety net (bypassing the Java API on purpose) ----

    @Test
    public void rawInsertWithANullPayerIsRejectedByTheNotNullConstraint() throws SQLException {
        DataSource dataSource = TestDatabases.freshMigratedDatabase();
        assertRawInsertFails(dataSource,
                "INSERT INTO ledger_entries (sequence_number, transaction_id, idempotency_key, payer_id, " +
                        "payee_id, amount, previous_hash, entry_hash, recorded_at) VALUES " +
                        "(0, 'tx-null-payer', 'k-null-payer', NULL, 'alice', 1.00, '" + LedgerEntry.GENESIS_HASH
                        + "', '" + "a".repeat(64) + "', CURRENT_TIMESTAMP)");
    }

    @Test
    public void rawInsertWithAZeroAmountIsRejectedByTheCheckConstraint() throws SQLException {
        DataSource dataSource = TestDatabases.freshMigratedDatabase();
        seedTwoAccounts(dataSource);
        assertRawInsertFails(dataSource,
                "INSERT INTO ledger_entries (sequence_number, transaction_id, idempotency_key, payer_id, " +
                        "payee_id, amount, previous_hash, entry_hash, recorded_at) VALUES " +
                        "(0, 'tx-zero', 'k-zero', 'alice', 'bob', 0.00, '" + LedgerEntry.GENESIS_HASH
                        + "', '" + "b".repeat(64) + "', CURRENT_TIMESTAMP)");
    }

    @Test
    public void rawInsertWithAnAmountOverTheCapIsRejectedByTheCheckConstraint() throws SQLException {
        DataSource dataSource = TestDatabases.freshMigratedDatabase();
        seedTwoAccounts(dataSource);
        assertRawInsertFails(dataSource,
                "INSERT INTO ledger_entries (sequence_number, transaction_id, idempotency_key, payer_id, " +
                        "payee_id, amount, previous_hash, entry_hash, recorded_at) VALUES " +
                        "(0, 'tx-over', 'k-over', 'alice', 'bob', 1000000.01, '" + LedgerEntry.GENESIS_HASH
                        + "', '" + "c".repeat(64) + "', CURRENT_TIMESTAMP)");
    }

    @Test
    public void rawInsertWithPayerEqualToPayeeIsRejectedByTheCheckConstraint() throws SQLException {
        DataSource dataSource = TestDatabases.freshMigratedDatabase();
        seedTwoAccounts(dataSource);
        assertRawInsertFails(dataSource,
                "INSERT INTO ledger_entries (sequence_number, transaction_id, idempotency_key, payer_id, " +
                        "payee_id, amount, previous_hash, entry_hash, recorded_at) VALUES " +
                        "(0, 'tx-self', 'k-self', 'alice', 'alice', 1.00, '" + LedgerEntry.GENESIS_HASH
                        + "', '" + "d".repeat(64) + "', CURRENT_TIMESTAMP)");
    }

    @Test
    public void rawUnguardedUpdateThatWouldMakeABalanceNegativeIsRejectedByTheAccountsCheckConstraint()
            throws SQLException {
        // JdbcLedgerStore.debit() never issues this statement -- it always uses the
        // atomic, guarded UPDATE (see its Javadoc). This test proves that even a
        // hypothetical future caller who bypassed that guard and wrote a naive,
        // unguarded UPDATE directly would still be stopped by the database itself.
        DataSource dataSource = TestDatabases.freshMigratedDatabase();
        seedTwoAccounts(dataSource);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("UPDATE accounts SET balance = balance - 1.00 WHERE account_id = 'alice'");
            fail("expected the CHECK constraint to reject driving alice's balance negative");
        } catch (SQLException expected) {
            assertEquals("23513", expected.getSQLState()); // ANSI check-constraint-violation class
        }
    }

    private static void seedTwoAccounts(DataSource dataSource) {
        JdbcLedgerStore store = new JdbcLedgerStore(dataSource);
        store.createAccountIfAbsent("alice", false, new BigDecimal("0.00"));
        store.createAccountIfAbsent("bob", false, new BigDecimal("0.00"));
    }

    private static void assertRawInsertFails(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            fail("expected a constraint violation for: " + sql);
        } catch (SQLException expected) {
            assertTrue("expected an integrity constraint violation (SQLSTATE class 23), got "
                            + expected.getSQLState(),
                    expected.getSQLState() != null && expected.getSQLState().startsWith("23"));
        }
    }
}
