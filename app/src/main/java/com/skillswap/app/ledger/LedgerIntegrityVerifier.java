package com.skillswap.app.ledger;

import com.skillswap.app.ledger.crypto.ConstantTimeCompare;
import com.skillswap.app.ledger.crypto.HmacUtil;

import java.util.List;
import java.util.Objects;

/**
 * A pure, stateless function over a list of entries and a key — deliberately kept
 * separate from {@code TransactionLedger} (which owns concurrency, locking, and
 * balances) so that "is this chain internally consistent" can be tested, and
 * reasoned about, with zero threading concerns: give it a list, get back an answer,
 * every time, for any list, real or deliberately corrupted for a test.
 */
public final class LedgerIntegrityVerifier {

    private LedgerIntegrityVerifier() {
    }

    public static IntegrityReport verify(List<LedgerEntry> entries, byte[] hmacKey) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(hmacKey, "hmacKey");

        String expectedPreviousHash = LedgerEntry.GENESIS_HASH;
        for (int i = 0; i < entries.size(); i++) {
            LedgerEntry entry = entries.get(i);

            if (!expectedPreviousHash.equals(entry.getPreviousHash())) {
                return IntegrityReport.failure(IntegrityReport.Failure.CHAIN_BROKEN, i);
            }

            byte[] expectedHmac = HmacUtil.compute(hmacKey, entry.toCanonicalBytes());
            byte[] actualHmac = HmacUtil.fromHex(entry.getEntryHash());
            if (!ConstantTimeCompare.equals(expectedHmac, actualHmac)) {
                return IntegrityReport.failure(IntegrityReport.Failure.HASH_MISMATCH, i);
            }

            expectedPreviousHash = entry.getEntryHash();
        }
        return IntegrityReport.ok();
    }
}
