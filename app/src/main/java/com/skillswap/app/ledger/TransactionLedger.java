package com.skillswap.app.ledger;

import com.skillswap.app.ledger.crypto.HmacUtil;
import com.skillswap.app.ledger.exception.IdempotencyKeyConflictException;
import com.skillswap.app.ledger.exception.InsufficientFundsException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An in-memory, thread-safe, idempotent, tamper-evident transaction ledger. This is
 * this project's flagship demonstration of duplicate-request detection, idempotency,
 * atomic updates, and integrity verification under concurrency — see
 * {@code docs/INTEGRITY_AND_IDEMPOTENCY.md} for the full design writeup and
 * {@code docs/THREAT_MODEL.md} for what this module does and does not defend against.
 *
 * <p>It is deliberately storage- and feature-agnostic: SkillSwap itself has no
 * monetary feature today, so this class is not wired into any specific screen. It
 * exists as a standalone, fully unit-testable subsystem (no Android/Firebase
 * dependency at all) that could back a future feature (e.g. session credits) without
 * any changes to its public API.
 *
 * <h2>Concurrency model, in one sentence</h2>
 * Idempotency-key resolution is fine-grained and lock-free per key (a
 * {@link ConcurrentHashMap#computeIfAbsent}); the actual ledger mutation — assigning
 * the next sequence number, computing the hash chain, updating balances — is a single
 * short critical section guarded by one {@link ReentrantLock}, because those
 * invariants (contiguous sequence numbers, an unbroken hash chain, correct balances)
 * are inherently global and cannot be sharded without breaking the "one linear,
 * verifiable history" guarantee this class exists to provide.
 */
public final class TransactionLedger {

    /** The payer identity used for {@link #deposit}. Exempt from the
     *  insufficient-funds check — it represents the ledger's own money supply, not a
     *  real constrained account. */
    public static final String SYSTEM_ACCOUNT = "SYSTEM";

    private final byte[] hmacKey;
    private final Clock clock;

    private final ReentrantLock appendLock = new ReentrantLock();
    private final List<LedgerEntry> entries = new ArrayList<>();
    private final Map<String, BigDecimal> balances = new HashMap<>();

    private final ConcurrentHashMap<String, PendingSubmission> byIdempotencyKey = new ConcurrentHashMap<>();

    public TransactionLedger(byte[] hmacKey) {
        this(hmacKey, Clock.systemUTC());
    }

    /** @param clock injectable so tests can assert on {@code recordedAt} deterministically. */
    public TransactionLedger(byte[] hmacKey, Clock clock) {
        if (hmacKey == null || hmacKey.length == 0) {
            throw new IllegalArgumentException("hmacKey must not be null or empty");
        }
        // Defensive copy: the caller cannot mutate our key after construction by
        // holding onto and later modifying the array they passed in.
        this.hmacKey = hmacKey.clone();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private static final class PendingSubmission {
        final TransactionRequest originalRequest;
        final CompletableFuture<TransactionResult> future = new CompletableFuture<>();

        PendingSubmission(TransactionRequest originalRequest) {
            this.originalRequest = originalRequest;
        }
    }

    /**
     * Submits a transaction. Exactly one of three things happens, regardless of how
     * many threads call this concurrently with the same idempotency key:
     * <ol>
     *   <li><b>First time this key is seen:</b> the transaction is validated, atomically
     *       appended to the ledger, and its result returned with {@code replayed=false}.</li>
     *   <li><b>Key already used, identical payload</b> (same payer/payee/amount/description):
     *       no new ledger entry is created; the original result is returned with
     *       {@code replayed=true}. Safe to call this way as many times as a retrying
     *       client likes.</li>
     *   <li><b>Key already used, different payload:</b> throws
     *       {@link IdempotencyKeyConflictException} — never silently applies the new
     *       payload under the old key.</li>
     * </ol>
     * If two callers race to be "first" with the same key, exactly one performs the
     * ledger append; the other blocks until that append completes (or fails) and then
     * returns/throws identically — see the class Javadoc's concurrency model.
     */
    public TransactionResult submit(TransactionRequest request) {
        Objects.requireNonNull(request, "request");

        PendingSubmission mine = new PendingSubmission(request);
        PendingSubmission owner = byIdempotencyKey.computeIfAbsent(request.getIdempotencyKey(), key -> mine);

        if (owner != mine) {
            // Someone else already owns this key — either finished, or in flight.
            if (!owner.originalRequest.equals(request)) {
                throw new IdempotencyKeyConflictException(request.getIdempotencyKey());
            }
            return awaitReplay(owner.future);
        }

        // computeIfAbsent guarantees at most one thread ever observes owner == mine
        // for a given key, so we are the sole worker responsible for this key.
        try {
            TransactionResult result = commit(request);
            owner.future.complete(result);
            return result;
        } catch (RuntimeException ex) {
            owner.future.completeExceptionally(ex);
            // Remove only if it's still our own (unprocessed) entry: a failed attempt
            // must not permanently claim the key, so a corrected retry can succeed.
            byIdempotencyKey.remove(request.getIdempotencyKey(), owner);
            throw ex;
        }
    }

    private TransactionResult awaitReplay(CompletableFuture<TransactionResult> future) {
        try {
            return future.join().asReplay();
        } catch (CompletionException wrapped) {
            Throwable cause = wrapped.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw wrapped;
        }
    }

    /**
     * The single, exclusive write path: validate against current ledger state, then
     * mutate. Validation happens BEFORE any mutation, inside the same lock — never
     * the other way around — so a rejected transaction (e.g. insufficient funds)
     * leaves the ledger byte-for-byte unchanged. There is no partially-applied state
     * for a caller to ever observe.
     */
    private TransactionResult commit(TransactionRequest request) {
        appendLock.lock();
        try {
            if (!request.getPayerId().equals(SYSTEM_ACCOUNT)) {
                BigDecimal payerBalance = balances.getOrDefault(request.getPayerId(), BigDecimal.ZERO);
                if (payerBalance.compareTo(request.getAmount()) < 0) {
                    throw new InsufficientFundsException(request.getPayerId(), request.getAmount(), payerBalance);
                }
            }

            long sequenceNumber = entries.size();
            String previousHash = entries.isEmpty()
                    ? LedgerEntry.GENESIS_HASH
                    : entries.get(entries.size() - 1).getEntryHash();
            String transactionId = UUID.randomUUID().toString();
            Instant recordedAt = clock.instant();

            byte[] canonical = LedgerEntry.canonicalBytes(sequenceNumber, transactionId,
                    request.getIdempotencyKey(), request.getPayerId(), request.getPayeeId(),
                    request.getAmount(), request.getDescription(), recordedAt, previousHash);
            String entryHash = HmacUtil.hex(HmacUtil.compute(hmacKey, canonical));

            LedgerEntry entry = new LedgerEntry(sequenceNumber, transactionId, request.getIdempotencyKey(),
                    request.getPayerId(), request.getPayeeId(), request.getAmount(), request.getDescription(),
                    recordedAt, previousHash, entryHash);
            entries.add(entry);

            if (!request.getPayerId().equals(SYSTEM_ACCOUNT)) {
                balances.merge(request.getPayerId(), request.getAmount().negate(), BigDecimal::add);
            }
            balances.merge(request.getPayeeId(), request.getAmount(), BigDecimal::add);

            return new TransactionResult(transactionId, sequenceNumber, entryHash, false);
        } finally {
            appendLock.unlock();
        }
    }

    /** Convenience for funding an account for tests/demos. Goes through the exact
     *  same {@link #submit} path as any other transaction — there is deliberately no
     *  second, unaudited way to change a balance — so deposits get the same
     *  idempotency and integrity guarantees as everything else. */
    public TransactionResult deposit(String idempotencyKey, String payeeId, BigDecimal amount) {
        return submit(new TransactionRequest(idempotencyKey, SYSTEM_ACCOUNT, payeeId, amount, "deposit"));
    }

    public BigDecimal getBalance(String accountId) {
        appendLock.lock();
        try {
            return balances.getOrDefault(accountId, BigDecimal.ZERO);
        } finally {
            appendLock.unlock();
        }
    }

    /** An immutable, point-in-time copy of the full entry log. */
    public List<LedgerEntry> snapshot() {
        appendLock.lock();
        try {
            return List.copyOf(entries);
        } finally {
            appendLock.unlock();
        }
    }

    public int size() {
        appendLock.lock();
        try {
            return entries.size();
        } finally {
            appendLock.unlock();
        }
    }

    public IntegrityReport verifyIntegrity() {
        return LedgerIntegrityVerifier.verify(snapshot(), hmacKey);
    }

    /**
     * Recomputes every account's balance by replaying the entry log from genesis,
     * entirely independent of the live {@code balances} map. This is the practical
     * demonstration of "deterministic ledger state": the log is the sole source of
     * truth, and {@code balances} is just a cache that must always agree with it —
     * if it doesn't, that's itself a bug to catch (see {@code TransactionLedgerTest}
     * and the stress test in {@code TransactionLedgerConcurrencyTest}).
     */
    public Map<String, BigDecimal> reconcileBalances() {
        Map<String, BigDecimal> recomputed = new HashMap<>();
        for (LedgerEntry entry : snapshot()) {
            if (!entry.getPayerId().equals(SYSTEM_ACCOUNT)) {
                recomputed.merge(entry.getPayerId(), entry.getAmount().negate(), BigDecimal::add);
            }
            recomputed.merge(entry.getPayeeId(), entry.getAmount(), BigDecimal::add);
        }
        return recomputed;
    }
}
