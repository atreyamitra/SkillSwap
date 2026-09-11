package com.skillswap.app.ledger;

import com.skillswap.app.ledger.exception.IdempotencyKeyConflictException;
import org.junit.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Concurrency tests for {@link TransactionLedger}. Every assertion here checks a
 * final-state invariant that must hold under EVERY possible thread interleaving
 * (final counts, set membership, chain validity, balance reconciliation) rather than
 * a specific interleaving or timing — that is what keeps these tests deterministic
 * (never flaky) despite exercising genuine concurrency. See
 * {@code docs/INTEGRITY_AND_IDEMPOTENCY.md} §5 for why this is the correct way to
 * test concurrent code, and what kind of assertion to avoid.
 *
 * <p>All start gates use a {@link CountDownLatch} released after every worker thread
 * is already submitted and blocked on it, to maximize actual overlap; no test relies
 * on {@code Thread.sleep} for coordination. Every {@code get()} carries a generous but
 * bounded timeout so a real deadlock fails the test loudly instead of hanging CI.
 */
public class TransactionLedgerConcurrencyTest {

    private static final byte[] KEY = "concurrency-test-key".getBytes(StandardCharsets.UTF_8);

    // ---- 3. concurrent duplicate requests ----

    @Test(timeout = 15_000)
    public void manyConcurrentCallsWithTheSameIdempotencyKeyProduceExactlyOneLedgerEntry() throws Exception {
        TransactionLedger ledger = new TransactionLedger(KEY);
        ledger.deposit("seed", "alice", new BigDecimal("1000.00"));

        TransactionRequest request = new TransactionRequest("dup-key", "alice", "bob", new BigDecimal("10.00"), "lunch");
        int threadCount = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<TransactionResult>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                startGate.await();
                return ledger.submit(request);
            }));
        }
        startGate.countDown();

        Set<String> distinctTransactionIds = new HashSet<>();
        Set<Long> distinctSequenceNumbers = new HashSet<>();
        int replayedCount = 0;
        for (Future<TransactionResult> future : futures) {
            TransactionResult result = future.get(10, TimeUnit.SECONDS);
            distinctTransactionIds.add(result.getTransactionId());
            distinctSequenceNumbers.add(result.getSequenceNumber());
            if (result.isReplayed()) replayedCount++;
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals("all concurrent callers must observe the SAME transactionId", 1, distinctTransactionIds.size());
        assertEquals("all concurrent callers must observe the SAME sequence number", 1, distinctSequenceNumbers.size());
        assertEquals("exactly one caller performs the real commit; the rest replay", threadCount - 1, replayedCount);
        // The seed deposit is entry 0; the one distinct duplicated transaction is entry 1.
        assertEquals("the ledger must contain exactly one entry for the transfer despite 50 concurrent submits",
                2, ledger.size());
        assertTrue(ledger.verifyIntegrity().isValid());
    }

    @Test(timeout = 15_000)
    public void concurrentConflictingPayloadsUnderTheSameKeyYieldExactlyOneSuccessAndOneConflict() throws Exception {
        TransactionLedger ledger = new TransactionLedger(KEY);
        ledger.deposit("seed", "alice", new BigDecimal("1000.00"));

        TransactionRequest requestA = new TransactionRequest("conflict-key", "alice", "bob", new BigDecimal("10.00"), "A");
        TransactionRequest requestB = new TransactionRequest("conflict-key", "alice", "bob", new BigDecimal("20.00"), "B");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        Future<Object> futureA = pool.submit(callOrCapture(startGate, ledger, requestA));
        Future<Object> futureB = pool.submit(callOrCapture(startGate, ledger, requestB));
        startGate.countDown();

        Object outcomeA = futureA.get(10, TimeUnit.SECONDS);
        Object outcomeB = futureB.get(10, TimeUnit.SECONDS);
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        int successes = (outcomeA instanceof TransactionResult ? 1 : 0) + (outcomeB instanceof TransactionResult ? 1 : 0);
        int conflicts = (outcomeA instanceof IdempotencyKeyConflictException ? 1 : 0)
                + (outcomeB instanceof IdempotencyKeyConflictException ? 1 : 0);

        assertEquals("regardless of which request wins the race, exactly one must succeed", 1, successes);
        assertEquals("and the other must be rejected as a conflicting payload under the same key", 1, conflicts);
        assertEquals(2, ledger.size()); // the seed deposit is entry 0, the one winning transfer is entry 1
    }

    private static Callable<Object> callOrCapture(CountDownLatch startGate, TransactionLedger ledger, TransactionRequest request) {
        return () -> {
            startGate.await();
            try {
                return ledger.submit(request);
            } catch (RuntimeException e) {
                return e;
            }
        };
    }

    // ---- 4. many concurrent independent transactions: the deterministic stress test ----

    /**
     * The one deterministic concurrency stress test called for in
     * docs/INTEGRITY_AND_IDEMPOTENCY.md: many threads, many independent transactions,
     * asserted only against invariants that hold under every interleaving:
     * <ul>
     *   <li>every submitted transaction is recorded exactly once (no lost updates,
     *       no duplicate commits),</li>
     *   <li>sequence numbers are a contiguous 0..N-1 set with no gaps or duplicates
     *       (proves the single append lock actually serializes commits correctly),</li>
     *   <li>the hash chain verifies end-to-end (proves concurrent HMAC computation
     *       and chaining didn't corrupt anything), and</li>
     *   <li>live balances agree exactly with balances replayed from the log alone
     *       (proves "the log is the sole source of truth" under real contention, not
     *       just in the single-threaded tests).</li>
     * </ul>
     * Deliberately sized so that even in the worst-case scheduling, no account can run
     * out of funds mid-test (each of 20 accounts is seeded with 10,000.00; the whole
     * test moves at most ~40.00 total through any single account) — an
     * InsufficientFundsException here would depend on thread scheduling order, which
     * is exactly the kind of nondeterminism that makes concurrency tests flaky, so the
     * amounts are chosen with a large safety margin specifically to avoid it.
     */
    @Test(timeout = 30_000)
    public void manyConcurrentIndependentTransactionsPreserveAllLedgerInvariants() throws Exception {
        TransactionLedger ledger = new TransactionLedger(KEY);
        int accountCount = 20;
        String[] accounts = new String[accountCount];
        for (int i = 0; i < accountCount; i++) {
            accounts[i] = "account-" + i;
            ledger.deposit("seed-" + i, accounts[i], new BigDecimal("10000.00"));
        }

        int threadCount = 16;
        int transactionsPerThread = 50;
        int totalIndependentTransactions = threadCount * transactionsPerThread;

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger counter = new AtomicInteger();
        List<Future<Void>> futures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            int threadIndex = t;
            futures.add(pool.submit(() -> {
                startGate.await();
                for (int i = 0; i < transactionsPerThread; i++) {
                    int n = counter.getAndIncrement();
                    String payer = accounts[n % accountCount];
                    String payee = accounts[(n + 1) % accountCount];
                    String key = "stress-" + threadIndex + "-" + i;
                    ledger.submit(new TransactionRequest(key, payer, payee, new BigDecimal("1.00"), "stress"));
                }
                return null;
            }));
        }
        startGate.countDown();
        for (Future<Void> future : futures) {
            future.get(20, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        List<LedgerEntry> snapshot = ledger.snapshot();
        int expectedTotalEntries = totalIndependentTransactions + accountCount; // + the seed deposits
        assertEquals("every independent transaction must be recorded exactly once",
                expectedTotalEntries, snapshot.size());

        Set<Long> sequenceNumbers = new HashSet<>();
        for (LedgerEntry entry : snapshot) {
            sequenceNumbers.add(entry.getSequenceNumber());
        }
        assertEquals("sequence numbers must be unique - no lost updates", snapshot.size(), sequenceNumbers.size());
        for (long i = 0; i < snapshot.size(); i++) {
            assertTrue("sequence numbers must be contiguous with no gaps (missing " + i + ")",
                    sequenceNumbers.contains(i));
        }

        assertTrue("hash chain must verify end-to-end with no corruption under contention",
                ledger.verifyIntegrity().isValid());

        Map<String, BigDecimal> reconciled = ledger.reconcileBalances();
        for (String account : accounts) {
            assertEquals("live balance must equal the balance recomputed purely by replaying the log for " + account,
                    ledger.getBalance(account), reconciled.getOrDefault(account, BigDecimal.ZERO));
        }
    }
}
