package com.skillswap.app.ledger.sql;

import com.skillswap.app.ledger.LedgerEntry;
import com.skillswap.app.ledger.LedgerEntryFactory;
import com.skillswap.app.ledger.TransactionLedger;
import com.skillswap.app.ledger.TransactionRequest;
import com.skillswap.app.ledger.exception.IdempotencyKeyConflictException;
import org.junit.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Concurrency tests for {@link JdbcLedgerStore}, run against a real (H2) database
 * with multiple threads, each using its own JDBC {@link java.sql.Connection}.
 *
 * <p><b>What's different from {@code TransactionLedgerConcurrencyTest} (the in-memory
 * engine's equivalent tests):</b> {@code JdbcLedgerStore} has no {@code ReentrantLock},
 * no {@code ConcurrentHashMap}, no Java-level synchronization of any kind. Its
 * "exactly one wins" guarantee under concurrent identical requests comes entirely
 * from the database's own unique-constraint enforcement, which is atomic across
 * connections and processes by construction — that is the whole reason a database
 * constraint is a meaningfully different, additional layer of protection over the
 * in-memory engine's JVM-local lock (see {@code docs/DATABASE_DESIGN.md}
 * "Consistency assumptions" and {@code docs/INTEGRITY_AND_IDEMPOTENCY.md}'s
 * limitations on multi-process idempotency, which this specifically addresses).
 *
 * <p>As with the in-memory concurrency tests, every assertion here is a final-state
 * invariant that holds under any interleaving — no timing assumptions, no
 * {@code Thread.sleep}. See that class's Javadoc for the general rationale.
 */
public class JdbcLedgerStoreConcurrencyTest {

    private static final byte[] KEY = "jdbc-concurrency-test-key".getBytes(StandardCharsets.UTF_8);
    private static final Clock CLOCK = Clock.systemUTC();

    @Test(timeout = 20_000)
    public void manyConcurrentAppendsOfTheIdenticalEntryProduceExactlyOnePersistedRow() throws Exception {
        DataSource dataSource = TestDatabases.freshMigratedDatabase();
        JdbcLedgerStore store = new JdbcLedgerStore(dataSource);
        store.createAccountIfAbsent(TransactionLedger.SYSTEM_ACCOUNT, true, BigDecimal.ZERO);
        store.createAccountIfAbsent("alice", false, BigDecimal.ZERO);
        store.createAccountIfAbsent("bob", false, BigDecimal.ZERO);

        LedgerEntry deposit = LedgerEntryFactory.next(null,
                new TransactionRequest("seed-1", TransactionLedger.SYSTEM_ACCOUNT, "alice", new BigDecimal("1000.00"), ""),
                KEY, CLOCK);
        store.append(deposit);

        LedgerEntry sharedEntry = LedgerEntryFactory.next(deposit,
                new TransactionRequest("dup-key", "alice", "bob", new BigDecimal("10.00"), "lunch"), KEY, CLOCK);

        int threadCount = 30;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<AppendOutcome>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                startGate.await();
                return store.append(sharedEntry);
            }));
        }
        startGate.countDown();

        Set<String> distinctTransactionIds = new HashSet<>();
        int alreadyExistedCount = 0;
        for (Future<AppendOutcome> future : futures) {
            AppendOutcome outcome = future.get(15, TimeUnit.SECONDS);
            distinctTransactionIds.add(outcome.getEntry().getTransactionId());
            if (outcome.isAlreadyExisted()) {
                alreadyExistedCount++;
            }
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals("every concurrent caller must observe the SAME transactionId", 1, distinctTransactionIds.size());
        assertEquals("exactly one caller's INSERT wins; every other observes it already existed",
                threadCount - 1, alreadyExistedCount);
        assertEquals("exactly one row for the transfer must be persisted despite 30 concurrent connections",
                2, store.findAll().size()); // seed deposit + the one transfer
        assertEquals(new BigDecimal("990.00"), store.getBalance("alice")); // debited exactly once
        assertEquals(new BigDecimal("10.00"), store.getBalance("bob"));    // credited exactly once
    }

    @Test(timeout = 20_000)
    public void concurrentConflictingPayloadsUnderTheSameKeyYieldExactlyOneSuccessAndOneConflict() throws Exception {
        DataSource dataSource = TestDatabases.freshMigratedDatabase();
        JdbcLedgerStore store = new JdbcLedgerStore(dataSource);
        store.createAccountIfAbsent(TransactionLedger.SYSTEM_ACCOUNT, true, BigDecimal.ZERO);
        store.createAccountIfAbsent("alice", false, BigDecimal.ZERO);
        store.createAccountIfAbsent("bob", false, BigDecimal.ZERO);

        LedgerEntry deposit = LedgerEntryFactory.next(null,
                new TransactionRequest("seed-1", TransactionLedger.SYSTEM_ACCOUNT, "alice", new BigDecimal("1000.00"), ""),
                KEY, CLOCK);
        store.append(deposit);

        LedgerEntry entryA = LedgerEntryFactory.next(deposit,
                new TransactionRequest("conflict-key", "alice", "bob", new BigDecimal("10.00"), "A"), KEY, CLOCK);
        LedgerEntry entryB = LedgerEntryFactory.next(deposit,
                new TransactionRequest("conflict-key", "alice", "bob", new BigDecimal("20.00"), "B"), KEY, CLOCK);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        Future<Object> futureA = pool.submit(callOrCapture(startGate, store, entryA));
        Future<Object> futureB = pool.submit(callOrCapture(startGate, store, entryB));
        startGate.countDown();

        Object outcomeA = futureA.get(15, TimeUnit.SECONDS);
        Object outcomeB = futureB.get(15, TimeUnit.SECONDS);
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        int successes = (outcomeA instanceof AppendOutcome ? 1 : 0) + (outcomeB instanceof AppendOutcome ? 1 : 0);
        int conflicts = (outcomeA instanceof IdempotencyKeyConflictException ? 1 : 0)
                + (outcomeB instanceof IdempotencyKeyConflictException ? 1 : 0);

        assertEquals("regardless of which entry's INSERT wins the race, exactly one must succeed", 1, successes);
        assertEquals("and the other must be rejected as a conflicting payload under the same key", 1, conflicts);
        assertEquals(2, store.findAll().size()); // seed deposit + the one winning transfer
    }

    private static Callable<Object> callOrCapture(CountDownLatch startGate, JdbcLedgerStore store, LedgerEntry entry) {
        return () -> {
            startGate.await();
            try {
                return store.append(entry);
            } catch (RuntimeException e) {
                return e;
            }
        };
    }
}
