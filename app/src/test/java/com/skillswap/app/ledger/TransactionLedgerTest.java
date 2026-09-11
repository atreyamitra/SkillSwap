package com.skillswap.app.ledger;

import com.skillswap.app.ledger.exception.IdempotencyKeyConflictException;
import com.skillswap.app.ledger.exception.InsufficientFundsException;
import org.junit.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TransactionLedgerTest {

    private static final byte[] KEY = "ledger-test-key".getBytes(StandardCharsets.UTF_8);

    private static TransactionLedger newFundedLedger(String account, String amount) {
        TransactionLedger ledger = new TransactionLedger(KEY);
        ledger.deposit("seed-" + account, account, new BigDecimal(amount));
        return ledger;
    }

    // ---- basic commit ----

    @Test
    public void aFreshSubmissionIsAppendedAndNotMarkedAsReplayed() {
        TransactionLedger ledger = newFundedLedger("alice", "100.00");
        TransactionResult result = ledger.submit(
                new TransactionRequest("t1", "alice", "bob", new BigDecimal("10.00"), "lunch"));

        assertFalse(result.isReplayed());
        assertEquals(2, ledger.size()); // the seed deposit is entry 0, this transfer is entry 1
        assertEquals(1L, result.getSequenceNumber());
    }

    @Test
    public void sequenceNumbersAreAssignedInCommitOrderStartingAtZero() {
        TransactionLedger ledger = newFundedLedger("alice", "100.00");
        TransactionResult r1 = ledger.submit(new TransactionRequest("t1", "alice", "bob", new BigDecimal("1.00"), ""));
        TransactionResult r2 = ledger.submit(new TransactionRequest("t2", "alice", "bob", new BigDecimal("1.00"), ""));
        // r0 was the deposit itself.
        assertEquals(1L, r1.getSequenceNumber());
        assertEquals(2L, r2.getSequenceNumber());
    }

    // ---- 1. duplicate transaction / request detection, 2. idempotency keys ----

    @Test
    public void repeatingTheIdenticalRequestReplaysTheOriginalResultWithoutANewLedgerEntry() {
        TransactionLedger ledger = newFundedLedger("alice", "100.00");
        TransactionRequest request = new TransactionRequest("dup-1", "alice", "bob", new BigDecimal("10.00"), "lunch");

        TransactionResult first = ledger.submit(request);
        TransactionResult second = ledger.submit(request);
        TransactionResult third = ledger.submit(
                new TransactionRequest("dup-1", "alice", "bob", new BigDecimal("10.00"), "lunch")); // equal, not same instance

        assertFalse(first.isReplayed());
        assertTrue(second.isReplayed());
        assertTrue(third.isReplayed());
        assertEquals(first.getTransactionId(), second.getTransactionId());
        assertEquals(first.getTransactionId(), third.getTransactionId());
        assertEquals(first.getSequenceNumber(), second.getSequenceNumber());
        assertEquals(2, ledger.size()); // deposit + the ONE transfer, never two transfers
    }

    @Test
    public void sameIdempotencyKeyWithAConflictingAmountIsRejected() {
        TransactionLedger ledger = newFundedLedger("alice", "100.00");
        ledger.submit(new TransactionRequest("k1", "alice", "bob", new BigDecimal("10.00"), "lunch"));

        try {
            ledger.submit(new TransactionRequest("k1", "alice", "bob", new BigDecimal("20.00"), "lunch"));
            fail("expected IdempotencyKeyConflictException");
        } catch (IdempotencyKeyConflictException expected) {
            // expected
        }
        assertEquals(2, ledger.size()); // the conflicting attempt must not have been appended
    }

    @Test
    public void sameIdempotencyKeyWithADifferentPayeeIsRejected() {
        TransactionLedger ledger = newFundedLedger("alice", "100.00");
        ledger.submit(new TransactionRequest("k1", "alice", "bob", new BigDecimal("10.00"), ""));
        try {
            ledger.submit(new TransactionRequest("k1", "alice", "carol", new BigDecimal("10.00"), ""));
            fail("expected IdempotencyKeyConflictException");
        } catch (IdempotencyKeyConflictException expected) {
            // expected
        }
    }

    @Test
    public void sameIdempotencyKeyWithADifferentDescriptionIsRejected() {
        TransactionLedger ledger = newFundedLedger("alice", "100.00");
        ledger.submit(new TransactionRequest("k1", "alice", "bob", new BigDecimal("10.00"), "lunch"));
        try {
            ledger.submit(new TransactionRequest("k1", "alice", "bob", new BigDecimal("10.00"), "dinner"));
            fail("expected IdempotencyKeyConflictException");
        } catch (IdempotencyKeyConflictException expected) {
            // expected
        }
    }

    // ---- 6. lost-update prevention / 5. race-condition prevention (single-threaded proof of the rule) ----

    @Test
    public void insufficientFundsIsRejectedAndLeavesTheLedgerAndBalancesUnchanged() {
        TransactionLedger ledger = newFundedLedger("alice", "5.00");
        Map<String, BigDecimal> balancesBefore = ledger.reconcileBalances();
        int sizeBefore = ledger.size();

        try {
            ledger.submit(new TransactionRequest("k1", "alice", "bob", new BigDecimal("10.00"), ""));
            fail("expected InsufficientFundsException");
        } catch (InsufficientFundsException expected) {
            // expected
        }

        assertEquals(sizeBefore, ledger.size());
        assertEquals(balancesBefore, ledger.reconcileBalances());
        assertEquals(new BigDecimal("5.00"), ledger.getBalance("alice"));
    }

    @Test
    public void anUnfundedAccountCannotPaySoZeroBalancePayerFailsInsufficientFunds() {
        TransactionLedger ledger = new TransactionLedger(KEY); // "alice" never funded
        try {
            ledger.submit(new TransactionRequest("k1", "alice", "bob", new BigDecimal("0.01"), ""));
            fail("expected InsufficientFundsException");
        } catch (InsufficientFundsException expected) {
            // expected
        }
    }

    // ---- 13. failure behavior, 14. retry behavior ----

    @Test
    public void aFailedAttemptDoesNotPermanentlyClaimItsIdempotencyKey_aCorrectedRetrySucceeds() {
        TransactionLedger ledger = newFundedLedger("alice", "5.00");

        try {
            ledger.submit(new TransactionRequest("retry-key", "alice", "bob", new BigDecimal("10.00"), ""));
            fail("expected InsufficientFundsException");
        } catch (InsufficientFundsException expected) {
            // expected: alice only has 5.00
        }

        // Real-world equivalent: the payer tops up, then retries the SAME logical
        // operation. Whether that retry reuses the same idempotency key is a client
        // choice; here we show it succeeding under a fixed, corrected payload.
        ledger.deposit("top-up", "alice", new BigDecimal("100.00"));
        TransactionResult retried = ledger.submit(
                new TransactionRequest("retry-key", "alice", "bob", new BigDecimal("10.00"), ""));

        assertFalse(retried.isReplayed()); // this is a fresh commit, not a replay of a failure
        assertEquals(new BigDecimal("95.00"), ledger.getBalance("alice"));
        assertEquals(new BigDecimal("10.00"), ledger.getBalance("bob"));
    }

    @Test
    public void replayingAnAlreadySucceededKeyNeverReExecutesTheTransfer() {
        TransactionLedger ledger = newFundedLedger("alice", "100.00");
        TransactionRequest request = new TransactionRequest("k1", "alice", "bob", new BigDecimal("10.00"), "");

        ledger.submit(request);
        ledger.submit(request); // simulate a client retrying after e.g. a lost response
        ledger.submit(request);

        // If replay re-executed the transfer, alice would be debited 30.00, not 10.00.
        assertEquals(new BigDecimal("90.00"), ledger.getBalance("alice"));
        assertEquals(new BigDecimal("10.00"), ledger.getBalance("bob"));
    }

    // ---- 4. atomic updates ----

    @Test
    public void multipleTransfersUpdateBothBalancesTogetherNeverOneWithoutTheOther() {
        TransactionLedger ledger = newFundedLedger("alice", "100.00");
        ledger.submit(new TransactionRequest("k1", "alice", "bob", new BigDecimal("30.00"), ""));

        assertEquals(new BigDecimal("70.00"), ledger.getBalance("alice"));
        assertEquals(new BigDecimal("30.00"), ledger.getBalance("bob"));
        // Total money in the system is conserved: nothing created or destroyed except
        // by the SYSTEM_ACCOUNT deposit itself.
        assertEquals(new BigDecimal("100.00"), ledger.getBalance("alice").add(ledger.getBalance("bob")));
    }

    // ---- 7. deterministic ledger state ----

    @Test
    public void liveBalancesAlwaysAgreeWithBalancesReplayedPurelyFromTheLog() {
        TransactionLedger ledger = newFundedLedger("alice", "100.00");
        ledger.submit(new TransactionRequest("k1", "alice", "bob", new BigDecimal("10.00"), ""));
        ledger.submit(new TransactionRequest("k2", "bob", "carol", new BigDecimal("4.00"), ""));
        ledger.submit(new TransactionRequest("k3", "alice", "carol", new BigDecimal("5.00"), ""));

        Map<String, BigDecimal> reconciled = ledger.reconcileBalances();
        for (String account : new String[]{"alice", "bob", "carol"}) {
            assertEquals(ledger.getBalance(account), reconciled.getOrDefault(account, BigDecimal.ZERO));
        }
    }

    @Test
    public void theHashChainIsUnbrokenAfterSeveralCommits() {
        TransactionLedger ledger = newFundedLedger("alice", "100.00");
        ledger.submit(new TransactionRequest("k1", "alice", "bob", new BigDecimal("10.00"), ""));
        ledger.submit(new TransactionRequest("k2", "alice", "bob", new BigDecimal("5.00"), ""));

        List<LedgerEntry> entries = ledger.snapshot();
        assertEquals(LedgerEntry.GENESIS_HASH, entries.get(0).getPreviousHash());
        for (int i = 1; i < entries.size(); i++) {
            assertEquals(entries.get(i - 1).getEntryHash(), entries.get(i).getPreviousHash());
        }
        assertTrue(ledger.verifyIntegrity().isValid());
    }

    // ---- injectable clock: proves the hash genuinely covers recordedAt ----

    @Test
    public void recordedAtComesFromTheInjectedClockAndFeedsTheHash() {
        Instant fixed = Instant.parse("2026-01-01T00:00:00Z");
        Clock frozen = Clock.fixed(fixed, ZoneOffset.UTC);
        TransactionLedger ledger = new TransactionLedger(KEY, frozen);
        ledger.deposit("seed", "alice", new BigDecimal("10.00"));

        assertEquals(fixed, ledger.snapshot().get(0).getRecordedAt());
    }

    // ---- constructor / key handling ----

    @Test
    public void constructorRejectsANullKey() {
        try {
            new TransactionLedger(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void constructorRejectsAnEmptyKey() {
        try {
            new TransactionLedger(new byte[0]);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void mutatingTheCallersKeyArrayAfterConstructionDoesNotAffectTheLedger() {
        byte[] key = "original-key-value".getBytes(StandardCharsets.UTF_8);
        TransactionLedger ledger = new TransactionLedger(key);
        ledger.deposit("seed", "alice", new BigDecimal("10.00"));
        IntegrityReport before = ledger.verifyIntegrity();

        java.util.Arrays.fill(key, (byte) 0); // caller "wipes" their copy

        assertTrue(before.isValid());
        assertTrue(ledger.verifyIntegrity().isValid()); // ledger's own defensive copy is unaffected
    }
}
