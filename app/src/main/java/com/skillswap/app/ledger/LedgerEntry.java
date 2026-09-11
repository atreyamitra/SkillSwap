package com.skillswap.app.ledger;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * One committed, immutable entry in the ledger's append-only log. Every entry carries
 * the HMAC of the entry <em>before</em> it ({@code previousHash}) as well as its own
 * ({@code entryHash}), forming a hash chain: recomputing {@code entryHash} from an
 * entry's own fields and comparing it to the stored value detects tampering with that
 * entry; comparing {@code previousHash} to the prior entry's {@code entryHash} detects
 * reordering, insertion, or deletion anywhere in the chain. See
 * {@code LedgerIntegrityVerifier} and {@code docs/INTEGRITY_AND_IDEMPOTENCY.md}.
 */
public final class LedgerEntry {

    /** The {@code previousHash} of the first entry in a ledger — 32 zero bytes, hex
     *  encoded, matching the byte length of a real HMAC-SHA256 output so "genesis"
     *  is visually distinguishable from a real hash but structurally the same shape. */
    public static final String GENESIS_HASH = "0".repeat(64);

    private final long sequenceNumber;
    private final String transactionId;
    private final String idempotencyKey;
    private final String payerId;
    private final String payeeId;
    private final BigDecimal amount;
    private final String description;
    private final Instant recordedAt;
    private final String previousHash;
    private final String entryHash;

    public LedgerEntry(long sequenceNumber, String transactionId, String idempotencyKey,
                        String payerId, String payeeId, BigDecimal amount, String description,
                        Instant recordedAt, String previousHash, String entryHash) {
        this.sequenceNumber = sequenceNumber;
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.payerId = Objects.requireNonNull(payerId, "payerId");
        this.payeeId = Objects.requireNonNull(payeeId, "payeeId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.description = description == null ? "" : description;
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        this.previousHash = Objects.requireNonNull(previousHash, "previousHash");
        this.entryHash = Objects.requireNonNull(entryHash, "entryHash");
    }

    /**
     * The exact byte sequence that gets HMAC'd to produce (or verify) an entry's
     * {@code entryHash}. Each field is written as a 4-byte big-endian length prefix
     * followed by its UTF-8 bytes, rather than joined with a delimiter. A delimiter
     * (even something like {@code "|"}) is ambiguous if any field could ever contain
     * it — e.g. {@code amount="1"} + {@code description="23"} and {@code amount="12"}
     * + {@code description="3"} would hash identically under naive concatenation.
     * Length-prefixing removes that ambiguity entirely regardless of field content.
     *
     * <p>This is a {@code static} method taking plain values, not an instance method,
     * because the ledger needs to compute the hash-to-be <em>before</em> the
     * {@code LedgerEntry} it will belong to exists (the entry's own constructor
     * requires the hash as an argument). {@link #toCanonicalBytes()} is the instance
     * form used when re-verifying an existing entry, and simply forwards to this one.
     * {@code public} so {@link LedgerEntryFactory} (the normal way to build a
     * correctly-chained entry) and tests that deliberately need to construct an
     * entry with a specific, hand-controlled hash (see e.g.
     * {@code JdbcLedgerStoreTest}'s uniqueness tests) can both call it directly.
     */
    public static byte[] canonicalBytes(long sequenceNumber, String transactionId, String idempotencyKey,
                                  String payerId, String payeeId, BigDecimal amount, String description,
                                  Instant recordedAt, String previousHash) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeField(out, Long.toString(sequenceNumber));
        writeField(out, transactionId);
        writeField(out, idempotencyKey);
        writeField(out, payerId);
        writeField(out, payeeId);
        writeField(out, amount.toPlainString());
        writeField(out, description);
        // Rounded to microseconds before hashing: java.time.Instant carries
        // nanosecond precision, but neither H2 nor real PostgreSQL preserve that —
        // PostgreSQL's timestamp type has a hard microsecond ceiling regardless of
        // declared precision, and (this is the part that actually bit this project's
        // own SQL integration smoke test) both round to the nearest microsecond on
        // storage rather than truncating: nanos=...146977 round-trips as ...147000,
        // not ...146000. Hashing the raw, untruncated Instant — or naively truncating
        // it — would make the hash unrecomputable after a round trip through either
        // database. round(recordedAt) applies the identical rounding rule once here,
        // so every consumer (in-memory or SQL-backed) hashes the same value. See
        // docs/DATABASE_DESIGN.md "Consistency assumptions".
        writeField(out, round(recordedAt).toString());
        writeField(out, previousHash);
        return out.toByteArray();
    }

    /**
     * Rounds {@code instant} to the nearest microsecond, matching the rounding
     * (not truncating) behavior observed from H2's and standard SQL's fixed-precision
     * TIMESTAMP storage. Nanos exactly at the halfway point (500) round up, per
     * {@link Math#round(double)}, which is the same tie-breaking rule Java's own
     * rounding conversions use elsewhere.
     */
    private static Instant round(Instant instant) {
        long nanos = instant.getNano();
        long roundedMicros = Math.round(nanos / 1000.0);
        Instant startOfSecond = instant.truncatedTo(ChronoUnit.SECONDS);
        if (roundedMicros == 1_000_000L) {
            return startOfSecond.plusSeconds(1);
        }
        return startOfSecond.plusNanos(roundedMicros * 1000);
    }

    private static void writeField(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int length = bytes.length;
        out.write((length >>> 24) & 0xFF);
        out.write((length >>> 16) & 0xFF);
        out.write((length >>> 8) & 0xFF);
        out.write(length & 0xFF);
        out.write(bytes, 0, bytes.length);
    }

    /** The canonical bytes for THIS entry's own stored fields — what
     *  {@code LedgerIntegrityVerifier} recomputes the expected HMAC over. */
    public byte[] toCanonicalBytes() {
        return canonicalBytes(sequenceNumber, transactionId, idempotencyKey, payerId, payeeId,
                amount, description, recordedAt, previousHash);
    }

    public long getSequenceNumber() { return sequenceNumber; }
    public String getTransactionId() { return transactionId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPayerId() { return payerId; }
    public String getPayeeId() { return payeeId; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public Instant getRecordedAt() { return recordedAt; }
    public String getPreviousHash() { return previousHash; }
    public String getEntryHash() { return entryHash; }

    /** Identity is the ledger position: two entries are "the same entry" exactly when
     *  they occupy the same sequence number with the same transaction id. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LedgerEntry)) return false;
        LedgerEntry other = (LedgerEntry) o;
        return sequenceNumber == other.sequenceNumber && transactionId.equals(other.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequenceNumber, transactionId);
    }

    @Override
    public String toString() {
        return "LedgerEntry{seq=" + sequenceNumber + ", txId='" + transactionId + "', payer='" + payerId
                + "', payee='" + payeeId + "', amount=" + amount + ", entryHash='" + entryHash + "'}";
    }
}
