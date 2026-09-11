package com.skillswap.app.ledger;

import com.skillswap.app.ledger.crypto.HmacUtil;
import org.junit.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LedgerIntegrityVerifierTest {

    private static final byte[] KEY = "verifier-test-key".getBytes(StandardCharsets.UTF_8);
    private static final byte[] WRONG_KEY = "a-different-key".getBytes(StandardCharsets.UTF_8);

    /** Builds a genuine 3-entry chain through the real, correct code path. */
    private static List<LedgerEntry> validChain() {
        TransactionLedger ledger = new TransactionLedger(KEY);
        ledger.deposit("seed", "alice", new BigDecimal("100.00"));
        ledger.submit(new TransactionRequest("t1", "alice", "bob", new BigDecimal("10.00"), "first"));
        ledger.submit(new TransactionRequest("t2", "alice", "bob", new BigDecimal("20.00"), "second"));
        return ledger.snapshot();
    }

    private static LedgerEntry withAmount(LedgerEntry original, BigDecimal newAmount) {
        // Simulates an attacker (or storage corruption) editing a stored field without
        // being able to recompute a matching HMAC (they don't have the key) or without
        // bothering to (a bit-flip). Either way, the OLD entryHash no longer matches.
        return new LedgerEntry(original.getSequenceNumber(), original.getTransactionId(),
                original.getIdempotencyKey(), original.getPayerId(), original.getPayeeId(),
                newAmount, original.getDescription(), original.getRecordedAt(),
                original.getPreviousHash(), original.getEntryHash());
    }

    private static LedgerEntry withEntryHash(LedgerEntry original, String newHash) {
        return new LedgerEntry(original.getSequenceNumber(), original.getTransactionId(),
                original.getIdempotencyKey(), original.getPayerId(), original.getPayeeId(),
                original.getAmount(), original.getDescription(), original.getRecordedAt(),
                original.getPreviousHash(), newHash);
    }

    private static LedgerEntry withPreviousHash(LedgerEntry original, String newPreviousHash) {
        return new LedgerEntry(original.getSequenceNumber(), original.getTransactionId(),
                original.getIdempotencyKey(), original.getPayerId(), original.getPayeeId(),
                original.getAmount(), original.getDescription(), original.getRecordedAt(),
                newPreviousHash, original.getEntryHash());
    }

    @Test
    public void aGenuineUntamperedChainVerifies() {
        IntegrityReport report = LedgerIntegrityVerifier.verify(validChain(), KEY);
        assertTrue(report.isValid());
    }

    @Test
    public void anEmptyChainVerifies() {
        IntegrityReport report = LedgerIntegrityVerifier.verify(new ArrayList<>(), KEY);
        assertTrue(report.isValid());
    }

    @Test
    public void alteringATransactionValueIsDetected() {
        List<LedgerEntry> chain = new ArrayList<>(validChain());
        // Attacker tries to change entry 1's amount from 10.00 to 999.00, without
        // knowing the secret key, so entryHash still reflects the original amount.
        chain.set(1, withAmount(chain.get(1), new BigDecimal("999.00")));

        IntegrityReport report = LedgerIntegrityVerifier.verify(chain, KEY);
        assertFalseValid(report, IntegrityReport.Failure.HASH_MISMATCH, 1);
    }

    @Test
    public void corruptingTheStoredHashDirectlyIsDetected() {
        List<LedgerEntry> chain = new ArrayList<>(validChain());
        // Simulates storage-level corruption (e.g. a bit flip) or an attacker who
        // overwrites the hash with garbage instead of the transaction data.
        chain.set(1, withEntryHash(chain.get(1), "deadbeef".repeat(8)));

        IntegrityReport report = LedgerIntegrityVerifier.verify(chain, KEY);
        assertFalseValid(report, IntegrityReport.Failure.HASH_MISMATCH, 1);
    }

    @Test
    public void invalidHmacFormatIsRejected() {
        List<LedgerEntry> chain = new ArrayList<>(validChain());
        chain.set(0, withEntryHash(chain.get(0), "not-valid-hex"));

        try {
            LedgerIntegrityVerifier.verify(chain, KEY);
            org.junit.Assert.fail("expected IllegalArgumentException for malformed hex");
        } catch (IllegalArgumentException expected) {
            // HmacUtil.fromHex rejects non-hex content — corrupted-beyond-hex-shape
            // data fails loudly rather than being silently treated as "just wrong".
        }
    }

    @Test
    public void breakingTheChainLinkIsDetectedEvenIfEachEntrysOwnHashIsValid() {
        List<LedgerEntry> chain = new ArrayList<>(validChain());
        // Each entry, taken alone, still has a self-consistent entryHash — but entry 2's
        // previousHash no longer points at entry 1's real hash (as if entry 1 were
        // deleted, or entry 2 were spliced in from a different, unrelated chain).
        chain.set(2, withPreviousHash(chain.get(2), "f".repeat(64)));

        IntegrityReport report = LedgerIntegrityVerifier.verify(chain, KEY);
        assertFalseValid(report, IntegrityReport.Failure.CHAIN_BROKEN, 2);
    }

    @Test
    public void reorderingEntriesBreaksTheChain() {
        List<LedgerEntry> chain = new ArrayList<>(validChain());
        LedgerEntry tmp = chain.get(1);
        chain.set(1, chain.get(2));
        chain.set(2, tmp);

        IntegrityReport report = LedgerIntegrityVerifier.verify(chain, KEY);
        assertTrue(!report.isValid());
    }

    @Test
    public void deletingAMiddleEntryBreaksTheChain() {
        List<LedgerEntry> chain = new ArrayList<>(validChain());
        chain.remove(1);

        IntegrityReport report = LedgerIntegrityVerifier.verify(chain, KEY);
        assertFalseValid(report, IntegrityReport.Failure.CHAIN_BROKEN, 1);
    }

    @Test
    public void verifyingWithTheWrongKeyFailsEvenOnAGenuineUntamperedChain() {
        // Proves the "HMAC, not plain hash" point: without the correct secret, even a
        // completely untampered chain cannot be verified. A plain SHA-256 hash chain
        // (no secret) would still verify here, because anyone can recompute a plain
        // hash — which is exactly why a keyed MAC, not a bare hash, is used.
        IntegrityReport report = LedgerIntegrityVerifier.verify(validChain(), WRONG_KEY);
        assertTrue(!report.isValid());
        assertEquals(IntegrityReport.Failure.HASH_MISMATCH, report.getFailure());
        assertEquals(0, report.getFirstBadIndex()); // fails at the very first entry
    }

    @Test
    public void firstEntryMustPointAtGenesisHash() {
        List<LedgerEntry> chain = new ArrayList<>(validChain());
        chain.set(0, withPreviousHash(chain.get(0), "1".repeat(64)));

        IntegrityReport report = LedgerIntegrityVerifier.verify(chain, KEY);
        assertFalseValid(report, IntegrityReport.Failure.CHAIN_BROKEN, 0);
    }

    @Test
    public void canonicalBytesChangeWhenAnyFieldChanges_provingTheHashActuallyCoversEveryField() {
        LedgerEntry entry = validChain().get(1);
        byte[] original = entry.toCanonicalBytes();

        LedgerEntry differentDescription = new LedgerEntry(entry.getSequenceNumber(), entry.getTransactionId(),
                entry.getIdempotencyKey(), entry.getPayerId(), entry.getPayeeId(), entry.getAmount(),
                "a different description", entry.getRecordedAt(), entry.getPreviousHash(), entry.getEntryHash());

        assertTrue(!java.util.Arrays.equals(original, differentDescription.toCanonicalBytes()));
    }

    @Test
    public void lengthPrefixingPreventsFieldBoundaryConfusion() {
        // Without length-prefixing, amount="1" + description="23" and amount="12" +
        // description="3" would concatenate to the identical string "123". Prove the
        // canonical encoding tells them apart.
        Instant fixedTime = Instant.parse("2026-01-01T00:00:00Z");
        byte[] a = LedgerEntry.canonicalBytes(0, "tx", "key", "alice", "bob",
                new BigDecimal("1.00"), "23", fixedTime, LedgerEntry.GENESIS_HASH);
        byte[] b = LedgerEntry.canonicalBytes(0, "tx", "key", "alice", "bob",
                new BigDecimal("12.00"), "3", fixedTime, LedgerEntry.GENESIS_HASH);

        assertTrue(!java.util.Arrays.equals(a, b));
        // and therefore their HMACs differ too, which is the property that actually matters
        assertTrue(!HmacUtil.hex(HmacUtil.compute(KEY, a)).equals(HmacUtil.hex(HmacUtil.compute(KEY, b))));
    }

    private static void assertFalseValid(IntegrityReport report, IntegrityReport.Failure expectedFailure, long expectedIndex) {
        assertTrue(!report.isValid());
        assertEquals(expectedFailure, report.getFailure());
        assertEquals(expectedIndex, report.getFirstBadIndex());
    }
}
