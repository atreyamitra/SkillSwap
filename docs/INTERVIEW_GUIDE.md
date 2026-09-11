# Interview Guide

Prep material for talking about this repository. Every answer here should be
something you could actually say out loud and then defend if asked "show me
the code."

## 1. 30-second explanation

"SkillSwap is an Android app where people trade skills instead of money — I
teach you guitar, you teach me Java. Inside it, the part I'm most proud of is
a standalone Java module called the ledger: it's a transaction engine that
handles the same problems a payments system has to — duplicate requests,
concurrent writes, and detecting if data's been tampered with — backed by
both an in-memory version and a real SQL schema, both fully unit tested."

## 2. 90-second explanation

"SkillSwap started as an Android app for skill-bartering — Firebase Auth,
Realtime Database, a matching algorithm that scores mutual skill overlap. It's
a normal layered app: Activities/Fragments for UI, a thin repository layer
over Firebase, and framework-free model classes underneath.

The part I built to go deeper is `ledger/` — it's not wired into the app's UI,
it's a standalone module because I wanted to explore transaction-processing
problems properly instead of bolting them onto an app that doesn't have money
in it. It has an in-memory engine that guarantees exactly-once processing for
a given idempotency key even under concurrent access — using a
`ConcurrentHashMap` for coordination and a lock for the parts that need total
ordering, like assigning sequence numbers. Every entry is HMAC-signed and
chained to the one before it, so tampering is detectable. Then I added a real
SQL-backed version of the same thing — a normalized schema with foreign keys,
check constraints, and the idempotency guarantee enforced again at the
database level with a unique constraint, so it survives even across separate
processes.

It's got 140 tests, including two concurrency stress tests that I ran
repeatedly to make sure they weren't flaky, and a handful of tests that
deliberately bypass my own Java code and hit the database with raw SQL just
to prove the constraints do something on their own."

## 3. Architecture walkthrough

Start at the top and go down:

1. **Android app** (`activities/`, `fragments/`, `adapters/`) — the UI layer.
   Never touches Firebase directly.
2. **`firebase/`** — one repository class per data type (`UserRepository`,
   `SwapRequestRepository`, etc.), plus `DatabasePaths` as the single source
   of truth for path strings. Firebase Realtime Database chosen over
   Firestore for a simpler mental model at this scale.
3. **`models/`** — POJOs with validating constructors and `equals`/`hashCode`
   by identity. `RequestStatus`/`SessionStatus` are enums with a real
   transition graph (`canTransitionTo`), not bare strings.
4. **`ledger/`** (the standalone part) —
   - `TransactionRequest`/`LedgerEntry` — validated input, immutable output.
   - `TransactionLedger` — in-memory engine: `ConcurrentHashMap` for
     per-key idempotency, one `ReentrantLock` for the parts that need total
     ordering (sequence numbers, hash chaining, balances).
   - `ledger/crypto/` — HMAC-SHA256 signing, constant-time comparison.
   - `ledger/sql/` — `JdbcLedgerStore` (same guarantees, enforced by real SQL
     constraints) and `SchemaMigrator` (applies the migrations in
     `app/src/main/resources/db/migration/`).

If asked to draw it: request → validate → (in-memory engine OR SQL store) →
hash-chained entry → verifiable later by recomputing the HMAC chain.

## 4. 15 likely technical questions

1. What does idempotency mean here, concretely?
2. What happens if two threads submit the same request at the same time?
3. Why a `ConcurrentHashMap` and a lock, instead of just one big lock?
4. Why HMAC instead of a plain hash?
5. Why is the comparison constant-time, and does that actually matter here?
6. Walk me through what happens if a debit would overdraw an account.
7. Why is `BigDecimal` used instead of `double`?
8. What's the difference between the surrogate `id` and `sequence_number` in
   the SQL schema?
9. Why no foreign key from `previous_hash` to `entry_hash`?
10. How do you know your concurrency tests aren't flaky?
11. What's the difference between the in-memory engine and the SQL store —
    why have both?
12. What would you have to change to run this against a second server
    instance?
13. What's NOT tested or NOT guaranteed here?
14. Why does SkillSwap use Firebase but the ledger uses SQL?
15. What's the riskiest assumption in this design?

## 5. Concise answers

1. **Idempotency** — submitting the same request twice (same key, same
   payload) has the same effect as submitting it once. The second call
   returns the original result instead of doing the work again.
2. **Two threads, same request** — `ConcurrentHashMap.computeIfAbsent`
   guarantees exactly one thread "wins" and does the real work; the other
   blocks on a shared `CompletableFuture` and gets the identical result back.
3. **Two mechanisms, not one** — the map handles "don't double-process this
   key" without blocking unrelated keys at all. The lock handles the parts
   that are inherently global — you can't have two entries claim the same
   sequence number, no matter which keys they belong to. One lock for
   everything would work but serializes unrelated work unnecessarily; the
   map avoids that for the common case.
4. **HMAC vs. plain hash** — a plain hash only proves internal consistency;
   anyone can recompute one and forge a new "valid" chain. HMAC requires a
   secret key to produce a valid signature, so tampering by someone without
   the key is detectable.
5. **Constant-time comparison** — `MessageDigest.isEqual` instead of
   `String.equals`, which exits early on a mismatch and can leak timing
   information. Honestly, for this specific case (comparing locally-stored
   hashes, not a network-facing check) the realistic risk is low — I did it
   anyway because it's free and correct, and said so directly in
   `docs/THREAT_MODEL.md` rather than overselling it.
6. **Overdraft** — the debit is one atomic SQL statement:
   `UPDATE accounts SET balance = balance - ? WHERE balance >= ?`. If the
   row doesn't match, zero rows update, and I throw
   `InsufficientFundsException` and roll back everything in that transaction
   — the entry insert included. Nothing partial is ever left behind.
7. **BigDecimal, not double** — floating point can't represent decimal
   fractions like 0.10 exactly, so repeated arithmetic on money drifts.
   `BigDecimal` with a fixed scale doesn't.
8. **`id` vs `sequence_number`** — `id` is just a database row identity
   (an `IDENTITY` column), assigned after insert. `sequence_number` is the
   ledger's own logical position, assigned by the Java code *before* insert,
   because the entry's hash has to cover its own position and you can't hash
   a value the database hasn't generated yet.
9. **No FK on the hash chain** — a foreign key would only prove "this hash
   value exists somewhere," not that it's cryptographically correct — and
   the genesis entry's `previous_hash` is a sentinel that matches no real
   row, which a naive FK would just reject. That check has to stay in Java
   (`LedgerIntegrityVerifier`), where it can actually verify the HMAC.
10. **Non-flaky concurrency tests** — every assertion is a final-state
    invariant true under *any* interleaving (exact entry count, no gaps in
    sequence numbers, balances match a replay of the log) — never a timing
    assumption. I ran both stress tests 30 times each in fresh JVMs during
    development and got zero failures.
11. **Two engines** — the in-memory one is simple and fast for a single
    process; the SQL one is durable and, critically, its idempotency
    guarantee (a `UNIQUE` constraint) holds even if two separate processes
    are both writing to the same database, which a JVM-local `ConcurrentHashMap`
    can never do.
12. **Second server instance** — today, nothing coordinates them; that's a
    named gap, not something I've solved. The SQL store's idempotency-key
    constraint would still work across processes, but sequence-number
    assignment isn't currently safe for multiple independent writers.
13. **Not tested/guaranteed** — no persistence across a process restart for
    the in-memory engine, no key rotation, no non-repudiation (HMAC proves
    data wasn't altered by someone without the key, not *who* altered it),
    no rate limiting.
14. **Firebase vs. SQL** — the app's actual data (users, chats, swap
    requests) is naturally a JSON tree with simple access patterns, which is
    what Firebase is good at. The ledger's data is relational by nature
    (accounts referencing each other, uniqueness constraints, transactions)
    — genuinely a different problem needing a different tool, not a
    stylistic choice.
15. **Riskiest assumption** — that the process holding the HMAC key is
    trustworthy. If that key leaks, someone can forge an internally
    "valid" chain from scratch and I have no way to tell it apart from a
    real one. That's stated plainly in `docs/THREAT_MODEL.md`.

## 6. Five difficult follow-ups

1. **"Your lock serializes every single commit — how would you make this
   scale to many accounts under contention?"** Honest answer: I'd need
   per-account locking or sharding the sequence space, and that's genuinely
   harder to get right (lock ordering across accounts) — I chose the simple,
   provably-correct version because this project's goal was correctness
   under concurrency, not throughput, and I say so in the docs rather than
   pretending it scales.
2. **"What stops someone from replaying a captured request against your SQL
   store from a different process?"** Nothing extra beyond the idempotency
   key itself — if they replay the exact same key+payload, it's treated as a
   safe retry, which is correct idempotency behavior, not a vulnerability,
   but only if idempotency keys are generated unpredictably by the client.
   I don't enforce that; it's a caller responsibility I should call out more
   clearly.
3. **"You said no non-repudiation — how would you actually get that?"**
   Asymmetric signatures (e.g., the writer signs with a private key, anyone
   can verify with the public key) instead of a shared HMAC secret. I didn't
   need it because this module has exactly one signer and one verifier, the
   same party.
4. **"Why didn't you just use an existing library or framework for this
   instead of writing it yourself?"** Because the point was to understand
   and demonstrate the mechanics — computeIfAbsent's atomicity guarantee,
   why HMAC needs a canonical byte encoding, why SQL rounds timestamps
   instead of truncating them (a bug I actually hit and fixed) — not to
   assemble a payments system as fast as possible.
5. **"How confident are you this is actually correct, versus just 'the
   tests pass'?"** Fairly confident in the specific properties the tests
   check (I can name them), much less confident about anything outside that
   — I'd say exactly that in an interview rather than claim more.

## 7. Tradeoffs I consciously made

- One global lock over per-account sharding — simplicity and provable
  correctness over throughput.
- HMAC over asymmetric signatures — matches the actual single-signer
  scenario; would be wrong for a multi-party trust setup.
- H2 instead of Testcontainers/PostgreSQL for automated tests — no Docker
  daemon was available while building this; the schema is written to be
  portable, but I haven't run it against real Postgres yet.
- Two separate ledger engines instead of one — kept the in-memory one simple
  and the SQL one focused on durability/constraints, rather than one system
  trying to do both jobs at once.
- Firebase for the app, SQL for the ledger — different data shapes, so
  different tools, rather than forcing one persistence technology everywhere.

## 8. Bugs/failure modes the tests actually catch

- Duplicate submission of the same request (sequentially or concurrently).
- Reusing an idempotency key with a different payload.
- A debit that would overdraw an account (in-memory and via SQL).
- A transaction against an account that doesn't exist (SQL foreign key).
- An altered field, a corrupted/wrong hash, or a reordered/deleted entry in
  the ledger.
- Malformed input: null/blank ids, self-transactions, wrong decimal scale,
  amounts outside the configured bounds.
- Bad data inserted via raw SQL that bypasses the Java validation entirely.
- A real bug I found while building this: `java.time.Instant`'s nanosecond
  precision doesn't survive a round trip through a SQL `TIMESTAMP` column,
  and the database *rounds* rather than truncates on storage — my first fix
  (truncating) was still wrong. Fixed by rounding to microseconds before
  hashing, in one place, so every consumer hashes the same value.

## 9. What I'd improve with another week

- Wire the in-memory engine and the SQL store together so the in-memory
  engine's commits are actually durable, instead of two systems that share
  types but not a write path.
- Run the schema against real PostgreSQL (via Docker/Testcontainers) instead
  of only H2.
- Bound the in-memory idempotency-key map so it can't grow unboundedly in a
  long-running process.
- Convert the rest of the app's silently-swallowed Firebase errors to actual
  logging (a few are done as a worked example; most aren't yet).
- Add instrumented/UI tests for the Android app — there are none today.

## 10. What I'd change for production

- Real key management (rotation, a KMS) instead of a key passed directly
  into the constructor.
- Multi-process/distributed sequence assignment, not a JVM-local lock.
- Rate limiting and bounded resource usage (the idempotency-key map has no
  eviction policy today).
- Asymmetric signatures if any third party ever needs to verify the ledger
  without holding the same secret used to write it.
- Actually running this against real PostgreSQL, with connection pooling,
  instead of H2.

## 11. Limitations I should openly admit

- The Android app itself hasn't been compiled with a real Android SDK while
  building this — the code has been checked by hand, not by a compiler.
- No production deployment, no real users, no performance numbers. This is
  a learning project, not a shipped product.
- Not affiliated with, modeled on, or endorsed by any bank or financial
  institution — the ledger module explores transaction-engineering concepts
  in the abstract.
- No claim of PCI DSS compliance, "banking-grade" security, or any
  certification. Concurrency correctness is proven for the specific
  invariants tested, not for every conceivable scenario.
