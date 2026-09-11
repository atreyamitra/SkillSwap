# Integrity, Idempotency & Concurrency — `com.skillswap.app.ledger`

This document describes the project's flagship engineering subsystem: an in-memory,
thread-safe, idempotent, tamper-evident transaction ledger. It is a standalone,
Android/Firebase-free Java module (`app/src/main/java/com/skillswap/app/ledger/`) —
SkillSwap itself has no monetary feature today, so this is not wired into any
specific screen. It exists to answer, precisely and with code and tests behind every
claim, the question this document is written to satisfy:

> **"What happens if two requests hit this service at exactly the same time?"**

See `docs/THREAT_MODEL.md` for what this module defends against and, just as
importantly, what it does not. See `docs/ENGINEERING_DECISIONS.md` for how this
fits into the rest of the repository.

---

## 1. The problem

Any system that records the effect of a request exactly once, even though requests
can be retried, duplicated, or raced, has to answer four questions concretely:

1. If the same logical request arrives twice, does the effect happen twice?
2. If a caller doesn't know whether their first attempt succeeded (timeout, dropped
   response, crash before reading the reply), can they safely find out, or safely
   retry, without risking a double-effect?
3. If two different requests arrive at literally the same instant, is the order they
   get applied in well-defined, and does one ever silently clobber the other?
4. Once data has been recorded, can anyone tell — reliably, not just "probably" —
   whether it has since been altered?

This module answers all four with a working implementation and 65 passing tests, not
just a design sketch.

## 2. Failure modes this is built to prevent

| # | Failure mode | Concrete scenario |
|---|---|---|
| 1 | Duplicate transaction / request detection | A client's HTTP request succeeds server-side, but the response is lost. The client retries the *same* logical request. Naively, this creates two transactions instead of one. |
| 2 | Idempotency-key conflict | A client reuses an idempotency key for a *different* payload — a bug, a key collision, or a replay attack. Naively accepting it applies the wrong transaction under a key the caller thinks means something else. |
| 3 | Concurrent duplicate requests | Two threads (or two instances of a retrying client) submit the identical request at the same instant, not sequentially. Naive duplicate-detection (`if (!seen.contains(key))`) is itself racy: both threads can pass the check before either records the key. |
| 4 | Lost update / check-then-act race | Two concurrent transactions debit the same account. Each reads the balance, both see it's sufficient, both proceed — the account goes negative even though each check individually said "no. |
| 5 | Non-atomic partial writes | A transaction is half-applied — the sender's balance is debited but the receiver's is never credited, because a crash or exception happened between the two writes. |
| 6 | Silent tampering | Ledger data is edited after the fact (by a bug, a compromised process, or direct storage access) and nothing notices. |
| 7 | Chain splicing | Individual records are each internally valid, but have been reordered, deleted, or had a record spliced in from elsewhere — undetectable by checking records one at a time. |

## 3. Implementation

### 3.1 Duplicate detection & idempotency keys (§1, §2 above)

Every `TransactionRequest` carries a caller-supplied `idempotencyKey`.
`TransactionLedger.submit(request)`:

- **First time a key is seen:** the transaction is validated and committed; the
  result is stored, keyed by the idempotency key, and returned with
  `replayed = false`.
- **Key seen again, identical payload** (same payer, payee, amount, description):
  no new effect happens. The original result is returned with `replayed = true`.
  A retrying client can call this as many times as it likes.
- **Key seen again, different payload:** `IdempotencyKeyConflictException` — the
  new request is rejected outright, never silently applied under the old key.

"Identical payload" is decided by `TransactionRequest.equals()`, which — because
`amount` is normalized to a fixed 2-decimal scale in the constructor — compares
every field by value, with no scale-sensitivity trap (see §3.5).

### 3.2 Concurrent duplicate requests (§3 above)

The naive "check a Set, then act" pattern is racy under concurrency: two threads can
both observe "key not present" before either inserts it. `submit()` avoids this with
`ConcurrentHashMap#computeIfAbsent`, which the JDK guarantees invokes its mapping
function **at most once per key**, atomically, even under contention:

```java
PendingSubmission mine = new PendingSubmission(request);
PendingSubmission owner = byIdempotencyKey.computeIfAbsent(request.getIdempotencyKey(), key -> mine);

if (owner != mine) {
    // Someone else already owns this key — check for a conflict, then wait for them.
    if (!owner.originalRequest.equals(request)) throw new IdempotencyKeyConflictException(...);
    return awaitReplay(owner.future);
}
// Reference equality (owner == mine) is only true for the single thread that
// actually won the race to create this key's entry — everyone else observes the
// winner's object back from computeIfAbsent instead.
```

The winning thread commits the transaction and completes a shared
`CompletableFuture<TransactionResult>`; every other thread (whether it arrived a
nanosecond later or was already waiting) blocks on that same future and receives the
**identical** result once it completes — or the identical exception, if the winner's
commit failed (see §3.6). This is proven, not asserted, by
`manyConcurrentCallsWithTheSameIdempotencyKeyProduceExactlyOneLedgerEntry` (50
threads, one shared request, released simultaneously via a `CountDownLatch`) and
`concurrentConflictingPayloadsUnderTheSameKeyYieldExactlyOneSuccessAndOneConflict`.

### 3.3 Atomic updates & lost-update prevention (§4 above)

Per-key coordination (§3.2) only prevents *the same key* from being processed twice.
It does nothing to prevent two *different* keys — e.g. two independent debits
against the same account — from racing each other. That requires a second,
coarser-grained mechanism: `TransactionLedger.commit()` runs its entire
check-then-act sequence (read the payer's balance, decide if funds are sufficient,
assign the next sequence number, compute the hash chain, append the entry, update
both balances) inside one `ReentrantLock`:

```java
appendLock.lock();
try {
    // 1. Check the invariant against CURRENT state (balance sufficiency).
    // 2. Only if the check passes, mutate state (append entry, update balances).
    //    Steps 1 and 2 happen without releasing the lock in between.
} finally {
    appendLock.unlock();
}
```

Validation happens *before* any mutation, and both happen under the *same* lock
acquisition — never "check, release, then act." This is what makes the
insufficient-funds check race-free: two concurrent debits against the same account
are strictly serialized by the lock, so the second one always sees the *result* of
the first, never a stale balance. `insufficientFundsIsRejectedAndLeavesTheLedgerAndBalancesUnchanged`
proves a rejected transaction leaves the ledger byte-for-byte unchanged (no partial
effect); the stress test (§3.4) proves this holds under real contention, not just in
a single-threaded test.

### 3.4 The deterministic concurrency stress test

`manyConcurrentIndependentTransactionsPreserveAllLedgerInvariants` runs 16 threads ×
50 independent transactions (800 total) against 20 pre-funded accounts, released
simultaneously via a `CountDownLatch`, then asserts:

- every transaction was recorded **exactly once** (no lost updates, no duplicate
  commits),
- sequence numbers are the exact contiguous set `{0 .. N-1}` (proves the lock
  actually serializes commits — a bug here would produce duplicate or skipped
  sequence numbers),
- the hash chain verifies end-to-end (proves concurrent HMAC computation and
  chaining didn't corrupt anything under contention), and
- live balances agree **exactly** with balances recomputed purely by replaying the
  log (proves "the log is the sole source of truth" under real contention, not just
  in the single-threaded tests).

**Why this test is not flaky.** Every assertion is a final-state invariant that must
hold under *every possible* thread interleaving — not a specific interleaving, not a
timing window. There is no `Thread.sleep`-based coordination (a `CountDownLatch`
start gate is used instead, so all worker threads are already blocked and waiting
before being released together, maximizing actual overlap) and no assertion depends
on which thread happened to run first, run fastest, or win any particular race. The
account balances (10,000.00 seeded, ~40.00 moved through any single account in the
worst case) are sized with a large safety margin specifically so that no legitimate
scheduling order can trigger an `InsufficientFundsException` — that would make the
test's *outcome* depend on scheduling, which is exactly the property a non-flaky
concurrency test must not have. Verified with 30 consecutive fresh-JVM runs during
development with zero failures.

### 3.5 Deterministic ledger state

"Deterministic" here means something specific, stated precisely because it's easy to
overclaim: **the ledger's integrity invariants — contiguous sequence numbers, an
unbroken hash chain, balances that exactly equal a replay of the log — hold
deterministically, regardless of thread interleaving.** It does *not* mean "the same
wall-clock processing order every run": which of several concurrently-submitted,
independent transactions gets sequence number 5 versus 6 depends on which thread
happens to acquire the lock first, and that is genuinely nondeterministic between
runs. What's guaranteed is that *whichever* order is chosen is total, gap-free, and
tamper-evident — an auditor doesn't need to know the wall-clock arrival order to
verify the log is internally consistent.

The design choice that makes this provable is `reconcileBalances()`: it recomputes
every account's balance from nothing but the entry log, replaying it from genesis.
The live `balances` map is a cache; the log is the sole source of truth. If they ever
disagreed, that would be a real bug — the map, not the log, would be wrong. Every
test that touches balances checks this agreement, including under the stress test.

`BigDecimal.equals()` is scale-sensitive (`new BigDecimal("5.0").equals(new
BigDecimal("5.00"))` is `false`, even though they're the same amount —
`compareTo` treats them as equal). Rather than remembering to use `compareTo`
everywhere downstream, `TransactionRequest`'s constructor normalizes every amount to
scale 2 once, at the boundary — see `requestsWithDifferentlyScaledButNumericallyEqualAmountsAreConsideredTheSameRequest`.
After that point, plain `.equals()` is safe and correct everywhere else in the
module.

### 3.6 Failure & retry behavior

- **Validation failure** (`InvalidTransactionException` — null/blank fields,
  self-transaction, bad amount): thrown from `TransactionRequest`'s constructor,
  *before* `submit()` is ever called, so an invalid request never consumes an
  idempotency key at all.
- **Business-rule failure at commit time** (`InsufficientFundsException` — can only
  be known from ledger state, not the request alone, so it's checked inside the lock
  in `commit()`, not in the constructor): the failing thread's shared
  `CompletableFuture` completes **exceptionally**, so every other thread waiting on
  the same key sees the identical exception, not a false success. The key is then
  removed from the map (`byIdempotencyKey.remove(key, owner)`, a compare-and-remove
  so a since-replaced entry is never accidentally deleted) — **a failed attempt does
  not permanently claim its key.** A caller can fix the underlying condition (e.g.
  top up the payer's balance) and retry with the same key; see
  `aFailedAttemptDoesNotPermanentlyClaimItsIdempotencyKey_aCorrectedRetrySucceeds`.
- **Retry of an already-succeeded key:** always a pure replay — the transaction is
  never re-executed, no matter how many times the same key is retried (see
  `replayingAnAlreadySucceededKeyNeverReExecutesTheTransfer`, which asserts the
  balance after three retries of the same request equals the balance after one).

### 3.7 Integrity verification & tamper detection

Every `LedgerEntry` stores an HMAC-SHA256 (`entryHash`) over its own fields —
sequence number, transaction id, idempotency key, payer, payee, amount, description,
timestamp — **and** the previous entry's hash (`previousHash`), forming a hash
chain rooted at a fixed `GENESIS_HASH`. `LedgerIntegrityVerifier.verify(entries, key)`
walks the chain and, for each entry, checks two independent things:

1. **`previousHash` matches the prior entry's `entryHash`** (or `GENESIS_HASH` for
   the first entry) — catches reordering, insertion, or deletion anywhere in the
   chain, even if every individual entry's own hash is internally valid
   (`breakingTheChainLinkIsDetectedEvenIfEachEntrysOwnHashIsValid`,
   `reorderingEntriesBreaksTheChain`, `deletingAMiddleEntryBreaksTheChain`).
2. **`entryHash` matches the HMAC recomputed from the entry's own fields** — catches
   any field being altered after the fact (`alteringATransactionValueIsDetected`) or
   the stored hash itself being corrupted or replaced
   (`corruptingTheStoredHashDirectlyIsDetected`).

**Why HMAC and not a plain hash.** A plain SHA-256 hash chain (no secret key) only
proves *internal self-consistency* — but anyone, including an attacker who can edit
the stored data, can also recompute a plain hash and produce a new, internally
"valid" but entirely forged chain. Using a *keyed* MAC means recomputing a valid
`entryHash` requires the secret key. `verifyingWithTheWrongKeyFailsEvenOnAGenuineUntamperedChain`
proves the corollary directly: even a completely untampered chain fails verification
without the correct key — which is the entire point (see `docs/THREAT_MODEL.md` for
exactly what this does and doesn't prove about *who* could have tampered with it).

**Constant-time comparison.** The recomputed HMAC is compared to the stored one via
`ConstantTimeCompare.equals`, which delegates to `MessageDigest.isEqual` — the JDK's
documented constant-time comparison for equal-length byte arrays (always true here:
HMAC-SHA256 output is always 32 bytes). `String.equals`/`Arrays.equals` exit early on
the first mismatched byte, leaking timing information about *where* two values
differ — the textbook building block of a timing side-channel attack. See
`ConstantTimeCompare`'s Javadoc, and `docs/THREAT_MODEL.md` §4, for an honest
assessment of how much this matters for *this specific* use (verifying locally-held
data, not a network-facing check) versus why it's still the correct default.

**Canonical byte encoding.** The bytes actually HMAC'd are built with 4-byte
length-prefixed fields (`LedgerEntry.canonicalBytes`), not delimiter-joined strings.
A delimiter is ambiguous if any field could contain it: `amount="1"` +
`description="23"` and `amount="12"` + `description="3"` would concatenate to the
identical string `"123"` under naive joining. Length-prefixing removes that ambiguity
regardless of field content — proved directly by
`lengthPrefixingPreventsFieldBoundaryConfusion`.

### 3.8 Secret/key handling

`TransactionLedger`'s constructor takes the HMAC key as a `byte[]` and immediately
`.clone()`s it — a defensive copy, so a caller that later zeroes or otherwise mutates
their own copy of the array (a common "wipe the secret when done" pattern) cannot
affect the ledger's already-stored copy, and cannot be affected by it either
(`mutatingTheCallersKeyArrayAfterConstructionDoesNotAffectTheLedger`). The key is
never logged, never included in any `toString()`, and never exposed through any
getter. `HmacUtil.compute` creates a fresh `javax.crypto.Mac` instance per call
rather than sharing one across threads — `Mac` is documented as **not** thread-safe
for concurrent use (it accumulates state between `update`/`doFinal` calls), so
sharing one instance under contention is a real, easy-to-introduce bug; creating a
fresh instance sidesteps it entirely rather than managing a lock or a `ThreadLocal`
pool for a negligible performance gain.

**What this module does NOT do**, stated plainly: key generation, key rotation, key
storage/retrieval from a KMS or keystore, or key distribution to multiple verifying
parties. The API is designed to *accept* an externally-supplied key rather than
generating or hardcoding one, which is the right shape for those concerns to be
layered on later — but none of them are implemented here. See
`docs/THREAT_MODEL.md` §3.

## 4. Invariants

Stated precisely, because "the ledger is correct" is not a testable claim on its own
— these eight are, and every one has a test that would fail if it were violated:

1. **Idempotency:** for a fixed idempotency key, repeated calls to `submit` with an
   equal payload produce exactly one committed effect, however many times or however
   concurrently they are called.
2. **Conflict rejection:** for a fixed idempotency key, a call with a *different*
   payload always throws `IdempotencyKeyConflictException` and never mutates the
   ledger.
3. **Sequence contiguity:** the set of sequence numbers across all committed entries
   is exactly `{0, 1, ..., N-1}` for a ledger with N entries — no gaps, no
   duplicates, regardless of concurrent submission order.
4. **Chain integrity:** for every entry after the first, `previousHash` equals the
   immediately preceding entry's `entryHash`; the first entry's `previousHash`
   equals `GENESIS_HASH`.
5. **Hash correctness:** for every entry, `entryHash` equals
   `HMAC-SHA256(key, canonicalBytes(entry's own fields))`.
6. **Atomicity:** a transaction either fully commits (one new entry, both balances
   updated) or has no effect at all — there is no observable intermediate state.
7. **Balance conservation:** for every transaction other than a deposit
   (`payerId == SYSTEM_ACCOUNT`), the payer's balance decreases by exactly `amount`
   and the payee's increases by exactly `amount` — nothing is created or destroyed.
8. **Log-as-truth:** `reconcileBalances()` (recomputed purely from the entry log)
   always exactly equals the live `balances` map, for every account, at every point
   after any sequence of commits.

## 5. Concurrency model

Two mechanisms, at two different granularities, for two different jobs:

| Mechanism | Granularity | Job | Why this mechanism |
|---|---|---|---|
| `ConcurrentHashMap#computeIfAbsent` | Per idempotency key | Ensure exactly one "worker" processes a given key, with zero blocking between unrelated keys | Lock-free for distinct keys; the JDK's own atomicity guarantee for `computeIfAbsent` does exactly the "claim this key exactly once" job with no extra machinery |
| One `ReentrantLock` around `commit()` | Global (whole ledger) | Serialize sequence-number assignment, hash chaining, and balance updates | These three invariants are inherently global — a sequence number or a hash-chain link cannot be "sharded" by account without breaking the single linear, verifiable history this module exists to provide. A single short critical section is the correct mechanism, not a compromise: anything fancier (e.g. per-account locks) would only help throughput on a workload this module was never asked to demonstrate, at real risk of a subtle correctness bug (getting the lock ordering wrong across accounts) for no proven benefit. |

This is a deliberate choice, not an oversight: **the ledger's write path has exactly
one door.** `deposit()` is not a separate, unaudited mutation path — it constructs a
`TransactionRequest` from `SYSTEM_ACCOUNT` and calls the same `submit()` everything
else uses. There is no method anywhere in this class that mutates `entries` or
`balances` outside `commit()`'s lock. This single-writer-path design is *why* the
eight invariants above are provable at all — a second mutation path would need its
own, independently-argued correctness proof, and a good rule for an append-only
audit log is not to have one.

**What this does and doesn't say about throughput.** All commits are strictly
serialized — this ledger has no concurrent-write throughput beyond one commit at a
time, by design. That's the correct tradeoff for a single-process, in-memory,
integrity-first component being evaluated on correctness, not for a system that
would actually need to process a high volume of independent transactions in
parallel. See §7 (Limitations).

## 6. Security assumptions

- The HMAC key is confidential and known only to whatever process(es) legitimately
  need to write or verify the ledger. Anyone with the key can forge an
  indistinguishable, internally-"valid" chain from scratch — HMAC proves *the data
  hasn't changed since it was signed by someone with the key*, not *who* that
  someone was (see `docs/THREAT_MODEL.md` §2 for the non-repudiation gap this
  implies).
- The JVM's `SecureRandom`-backed `UUID.randomUUID()` and the JDK's `HmacSHA256`
  implementation are trusted to behave as documented.
- The process is trusted not to be compromised at the point of committing a
  transaction — this module detects tampering with data *after* it's written, not a
  compromised writer forging a plausible transaction *at* write time (it would sign
  correctly, because it has the key).
- Callers pass a genuinely unique idempotency key per logical operation. Key
  generation/uniqueness is the caller's responsibility (e.g. a UUID per user action),
  not something this module can enforce — it can only detect *reuse* of a key it has
  already seen.

## 7. Limitations

Stated as plainly as the guarantees, because a security-relevant module that hides
its limitations is worse than one that has none documented:

- **In-memory, single-process only.** There is no persistence, no replication, no
  distributed consensus. A process restart loses the entire ledger. This is not a
  distributed-systems solution to idempotency (e.g. it does not solve "two different
  servers behind a load balancer, both handling a retried request") — it solves the
  concurrency problem *within one JVM*, which is what its test suite proves and all
  it claims.
- **No non-repudiation.** As above: a symmetric MAC cannot prove *which* holder of
  the key produced a given entry, only that *some* holder did. A real audit system
  needing "prove which party signed this" needs asymmetric signatures, not HMAC.
- **No key rotation, storage, or distribution.** The module accepts an
  externally-supplied key and nothing more; all key-lifecycle concerns are out of
  scope (see §3.8).
- **Serialized writes only.** One global lock means no concurrent-write scaling.
  Correct and simple; not a throughput solution.
- **Validation constructors don't protect data from other paths into the JVM.**
  Everything reachable through `submit()`/`deposit()` is validated; a `LedgerEntry`
  constructed directly (bypassing the ledger) is not — this is intentional (see
  `LedgerIntegrityVerifierTest`, which does exactly this to simulate tamper
  scenarios) but worth stating: the validating constructors are a property of the
  *ledger's* API, not of `LedgerEntry` as a bare class.
- **`InsufficientFundsException` is the only ledger-state-dependent business rule
  implemented.** This module is not a general ledger/accounting engine — no
  interest, fees, multi-currency, reversals, or double-entry bookkeeping beyond the
  simple debit/credit pair described in §3.3.

## 8. Tradeoffs, summarized

| Decision | What it costs | What it buys |
|---|---|---|
| One global lock for `commit()` | No concurrent-write throughput | Simple, provably-correct global invariants (sequence contiguity, chain integrity) with no per-shard correctness burden |
| Fresh `Mac` instance per HMAC call | A small, unmeasured per-call cost | Zero risk of the classic shared-mutable-`Mac` concurrency bug, with no extra machinery |
| Failed commits release their idempotency key | A caller could theoretically hammer a doomed key indefinitely if the underlying condition never resolves | Correct retry semantics: a transient/state-dependent failure (insufficient funds) doesn't permanently brick a key |
| HMAC (symmetric) instead of a digital signature (asymmetric) | No non-repudiation; anyone with the key can forge | Simpler key management for a single-verifier, single-signer scenario — the actual scenario this module has |
| `equals`/normalization at construction instead of `compareTo` everywhere | A slightly more complex constructor | Every later comparison in the module can use plain `.equals()` safely, once, instead of every call site having to remember the `BigDecimal` scale trap |
| In-memory only | No durability, no multi-process guarantee | The entire concurrency and integrity story is testable, deterministically, in a plain JVM unit test — no database, no network, no flakiness from external infrastructure |
