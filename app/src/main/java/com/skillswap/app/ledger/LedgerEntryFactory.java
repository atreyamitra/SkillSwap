package com.skillswap.app.ledger;

import com.skillswap.app.ledger.crypto.HmacUtil;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Builds the next {@link LedgerEntry} in a hash chain from a {@link TransactionRequest}
 * and the entry immediately before it (or {@code null} for the first entry in a
 * chain). This is the one place that assigns a sequence number, captures a timestamp,
 * and computes an entry's HMAC — extracted out of {@link TransactionLedger#commit}
 * so that logic exists exactly once, rather than being re-derived by hand anywhere
 * else that needs a genuinely chained entry (for instance,
 * {@code JdbcLedgerStoreTest}, which builds real chains against H2 without needing a
 * whole {@code TransactionLedger} instance and its lock).
 *
 * <p>This class does not touch balances, does not check sufficiency of funds, and
 * does not know about idempotency keys already in use — it only knows how to extend
 * a chain by one entry. Those other concerns stay owned by whoever calls it
 * ({@code TransactionLedger}, in production use).
 */
public final class LedgerEntryFactory {

    private LedgerEntryFactory() {
    }

    /**
     * @param previous the chain's current last entry, or {@code null} if this will be
     *                 the first entry (its {@code previousHash} will be
     *                 {@link LedgerEntry#GENESIS_HASH} and its sequence number 0)
     */
    public static LedgerEntry next(LedgerEntry previous, TransactionRequest request, byte[] hmacKey, Clock clock) {
        long sequenceNumber = previous == null ? 0 : previous.getSequenceNumber() + 1;
        String previousHash = previous == null ? LedgerEntry.GENESIS_HASH : previous.getEntryHash();
        String transactionId = UUID.randomUUID().toString();
        Instant recordedAt = clock.instant();

        byte[] canonical = LedgerEntry.canonicalBytes(sequenceNumber, transactionId,
                request.getIdempotencyKey(), request.getPayerId(), request.getPayeeId(),
                request.getAmount(), request.getDescription(), recordedAt, previousHash);
        String entryHash = HmacUtil.hex(HmacUtil.compute(hmacKey, canonical));

        return new LedgerEntry(sequenceNumber, transactionId, request.getIdempotencyKey(),
                request.getPayerId(), request.getPayeeId(), request.getAmount(), request.getDescription(),
                recordedAt, previousHash, entryHash);
    }
}
