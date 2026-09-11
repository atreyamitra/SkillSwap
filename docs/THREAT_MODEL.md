# Threat Model — `com.skillswap.app.ledger`

This is a realistic threat model for the ledger module described in
`docs/INTEGRITY_AND_IDEMPOTENCY.md`. It is written to be checked against the actual
code and tests, not aspirational.

## 0. What this document is not claiming

Stated up front, explicitly, because it's easy for a security-adjacent document to
be read as claiming more than it does:

- **This is not PCI DSS compliant**, and no claim of PCI DSS compliance is made or
  implied. PCI DSS is a certification regime covering an entire cardholder-data
  environment (network segmentation, key-management processes, audit logging,
  physical security, third-party assessment, and much more) — a single in-memory
  Java class cannot be "PCI DSS compliant" in isolation, and this one has not been
  assessed against it.
- **This is not "banking-grade" or "production-grade" security.** Those are not
  precise, checkable claims; this document deliberately replaces them with specific,
  falsifiable statements about what is and isn't defended against, backed by tests.
- **This has no relationship to Wells Fargo** or any other named financial
  institution's systems, standards, or infrastructure. Nothing here should be read
  as implying compatibility, endorsement, or affiliation.
- **This has no regulatory certification of any kind** (SOC 2, ISO 27001, FFIEC
  guidance, or otherwise). No such certification has been sought or obtained,
  because a student/portfolio project's in-memory library is not the kind of thing
  that is certified.

What follows is instead a concrete, scoped analysis of one module's actual
guarantees, in the same spirit as a real design-review threat model.

## 1. Scope and assets

**In scope:** `com.skillswap.app.ledger` — the `TransactionLedger`, its supporting
value objects, the HMAC/hash-chain integrity mechanism, and the idempotency-key
handling.

**Out of scope:** everything else in the SkillSwap repository (Android UI, Firebase
rules, authentication) — this module has no dependency on and no integration with
any of it today.

**Assets being protected:**

1. **Ledger entries** — the record of what transactions were committed, in what
   order, for what amounts, between which accounts.
2. **Balance correctness** — that an account's recorded balance reflects reality
   (no double-spend, no lost debit/credit).
3. **The HMAC key** — the secret that makes tamper detection possible at all.
4. **Idempotency-key semantics** — the guarantee that reusing a key is either a safe
   no-op (same payload) or a loud failure (different payload), never a silent wrong
   action.

## 2. Adversaries and trust boundaries

| Adversary | Capability assumed | In scope? |
|---|---|---|
| **A retrying client** | Can resubmit the same or a modified request any number of times, including concurrently with itself | **Yes** — this is the primary scenario the module defends against |
| **A racing concurrent caller** | Multiple threads/callers submitting to the same `TransactionLedger` instance at the same time, with no coordination between them | **Yes** — the module's core concurrency guarantees |
| **A process with in-JVM access but not the HMAC key** | Can read or edit the entry list directly (e.g. via reflection, a bug elsewhere in the same process, or access to a serialized dump of the ledger) but does not know the secret key | **Partially** — tamper *detection* is in scope; tamper *prevention* (stopping the edit from happening) is not, since this is a plain in-memory Java object with no OS-level or JVM-level sandboxing around it |
| **A process or party that possesses the HMAC key** | Can compute valid HMACs for arbitrary data | **Out of scope for detection** — see §3. This adversary can forge an internally-consistent chain from scratch; nothing in this module can tell that chain apart from a genuine one. Key confidentiality is a precondition this module assumes, not something it enforces. |
| **An attacker with a timing oracle over a network-facing HMAC check** | Can send many requests and measure response timing to infer byte-by-byte MAC correctness | **Defended in the primitive** (`ConstantTimeCompare`), **but the realistic exposure here is low** — see §4. |
| **An attacker able to exhaust memory/threads (DoS)** | Can submit unbounded numbers of distinct idempotency keys or hold locks indefinitely | **Explicitly out of scope** — see §6. No rate limiting, no bounded map eviction, no defense against resource exhaustion is implemented. |
| **A caller who reuses idempotency keys across logically unrelated transactions** | Generates the same key for two operations that should be independent | **Detected as a conflict, not prevented** — the module rejects the second request loudly; it cannot stop a caller from choosing keys badly, only refuse to silently misapply a mismatched one. |

## 3. What HMAC does and does not prove here

This is the single most important nuance in this threat model, stated explicitly
because it's the one most often glossed over:

**HMAC-SHA256 with a shared secret proves:** the data has not been altered since it
was signed by *someone who holds the key*, provided the verifier also holds (only)
that same key.

**HMAC-SHA256 does NOT prove:** *which* holder of the key produced a given entry
(non-repudiation), or provide any guarantee at all to a party that does not hold the
key. If a system needed "prove to a third party, who doesn't have the secret, that
this specific signer produced this data," that requires an asymmetric digital
signature (e.g. Ed25519/ECDSA), not a symmetric MAC. This module intentionally uses
HMAC because its actual scenario — one process writing and verifying its own ledger
— has exactly one signer and one verifier, who are the same party. If a future use
case introduced multiple independent verifying parties who must trust each other
without sharing a secret, HMAC would be the wrong primitive, and this document says
so rather than letting that gap go unstated.

**Consequence for tamper detection specifically:** `LedgerIntegrityVerifier` can tell
you *that* the data no longer matches what was originally signed. It cannot tell you
*who* changed it, *when*, or distinguish "an external attacker without the key
edited the JSON on disk" from "an internal bug corrupted a field in memory" — both
produce the same `HASH_MISMATCH` result. Root-causing a detected tamper event is
outside this module's job; it hands you the fact of corruption and the index where
it starts, nothing more.

## 4. Timing side-channels: realistic exposure

`ConstantTimeCompare` exists and is used (see
`docs/INTEGRITY_AND_IDEMPOTENCY.md` §3.7) — but an honest threat model has to ask
*whether the threat it defends against is actually reachable here*, not just note
that the mitigation exists.

The classic timing attack against a MAC comparison requires an attacker who can (a)
submit many candidate MAC values, and (b) measure the comparison's response time
precisely enough to infer, byte by byte, which candidate is closer to correct — the
setup that matters for, say, a network API that accepts a client-supplied signature
and returns a fast/slow response depending on where the mismatch occurs.

**This module's actual verification path does not fit that setup.** `verify()`
recomputes the expected HMAC from data already inside the same process and compares
it to a stored value already inside the same process — there is no external party
submitting candidate hashes to be timed, and no network boundary an attacker could
measure across. The realistic threat this specific comparison defends against, in
this specific module, is closer to theoretical than exploitable.

**Why it's still implemented anyway:** two reasons, stated as tradeoffs rather than
oversold as critical defenses — (1) it is free — exactly as much code as the unsafe
version — so there's no cost to doing it correctly; (2) the comparison primitive is
reused wherever an HMAC needs checking, and if this module or a future one ever
exposes HMAC verification across a real trust boundary (e.g. verifying an inbound
webhook signature from an external caller), the safe primitive is already in place
rather than needing to be retrofitted under pressure later. This is presented as
good hygiene with a small, real payoff for future reuse — not as evidence that this
module currently faces a practical timing attack, because it does not.

## 5. What is defended, with the specific test that proves it

| Threat | Defense | Proof |
|---|---|---|
| Duplicate request (sequential retry) | Idempotency key + replay | `repeatingTheIdenticalRequestReplaysTheOriginalResultWithoutANewLedgerEntry` |
| Idempotency key reused with a different payload | `IdempotencyKeyConflictException` | `sameIdempotencyKeyWithAConflictingAmountIsRejected` and 2 related tests |
| Concurrent duplicate requests (race, not sequence) | `ConcurrentHashMap#computeIfAbsent` at-most-once-per-key guarantee | `manyConcurrentCallsWithTheSameIdempotencyKeyProduceExactlyOneLedgerEntry` (50 threads) |
| Concurrent conflicting payloads under one key | Same mechanism + payload comparison | `concurrentConflictingPayloadsUnderTheSameKeyYieldExactlyOneSuccessAndOneConflict` |
| Lost update (two concurrent debits, same account) | Check-then-act inside one lock, not split across two | `manyConcurrentIndependentTransactionsPreserveAllLedgerInvariants` (16×50 concurrent transactions, balance reconciliation) |
| Partial/non-atomic write | Validate-then-mutate inside one critical section; nothing observable in between | `insufficientFundsIsRejectedAndLeavesTheLedgerAndBalancesUnchanged` |
| Silent data tampering (single field altered) | HMAC per entry, recomputed and compared on verify | `alteringATransactionValueIsDetected` |
| Corrupted/replaced hash | Same mechanism | `corruptingTheStoredHashDirectlyIsDetected` |
| Malformed stored hash (not valid hex) | Explicit format check before comparison | `invalidHmacFormatIsRejected` |
| Reordered, deleted, or spliced entries | Hash-chain `previousHash` linkage, checked independently of each entry's own hash | `breakingTheChainLinkIsDetectedEvenIfEachEntrysOwnHashIsValid`, `reorderingEntriesBreaksTheChain`, `deletingAMiddleEntryBreaksTheChain` |
| Verification attempted with the wrong key | HMAC is keyed; wrong key recomputes different values everywhere | `verifyingWithTheWrongKeyFailsEvenOnAGenuineUntamperedChain` |
| Field-boundary confusion in the signed payload | Length-prefixed canonical encoding, not delimiter-joined | `lengthPrefixingPreventsFieldBoundaryConfusion` |
| Boundary/malformed monetary values (over/under limit, wrong scale, negative, zero, self-transaction, null fields) | Validating constructor, checked before any idempotency-key registration | `TransactionRequestTest` (14 cases) |

## 6. Explicitly out of scope / residual risk

These are not defended against, on purpose, and are named here rather than left
implicit:

- **Denial of service.** No rate limiting, no cap on the number of distinct
  idempotency keys tracked (the `ConcurrentHashMap` grows unboundedly), no timeout
  on how long a caller can hold up others waiting on the same key's future. A
  malicious or buggy caller that never lets its "winning" commit finish (e.g. blocks
  forever before `commit()` returns) would starve every other caller using the same
  idempotency key indefinitely. A real production deployment would need eviction
  policy for completed/old keys and a bound on in-flight work.
- **Key compromise.** If the HMAC key leaks, an attacker can forge an
  indistinguishable chain from scratch (§3). This module has no key-rotation,
  detection-of-compromise, or revocation story at all.
- **Persistence and durability.** Everything is in memory. A crash loses the ledger
  entirely; there is no write-ahead log, no replication, no backup.
- **Multi-process / distributed idempotency.** The guarantees are per-JVM-instance.
  Two separate processes (e.g. two servers behind a load balancer) each running
  their own `TransactionLedger` would not coordinate with each other at all — this
  module solves the *local* concurrency problem, not the distributed one.
- **Authentication and authorization.** Nothing in this module checks *who* is
  calling `submit()` or whether they're allowed to move money from a given
  `payerId`. That's assumed to be enforced by a caller/layer above this one, which
  does not exist in this repository.
- **Side-channels beyond the one MAC comparison discussed in §4.** No claim is made
  about, for example, memory-access-pattern side channels, garbage-collection
  timing, or any other channel beyond the specific comparison this module performs.
- **Malicious input beyond what `TransactionRequest`'s constructor validates.**
  Anything not explicitly checked (see `TransactionRequestTest`) is not guaranteed
  to be handled safely — this is a "validate what's specified, don't try to
  anticipate everything" scope, not an exhaustive input-fuzzing guarantee.

## 7. Summary: precise answer to "what happens if two requests hit this at exactly the same time?"

- **Same idempotency key, same payload:** exactly one is committed; the other
  blocks briefly and then receives the identical result, marked as a replay. No
  double effect, ever, proven by a 50-thread concurrent test.
- **Same idempotency key, different payload:** exactly one succeeds (whichever wins
  the internal race — not deterministic which one, and the API does not promise
  "first submitted wins"); the other is rejected with `IdempotencyKeyConflictException`.
  Never both applied, never the second silently overwriting the first.
- **Different idempotency keys, independent transactions, possibly touching the same
  account:** both are committed, strictly ordered (one is assigned a sequence number
  and fully applied — including its balance check — before the other's check even
  runs), so neither can observe a stale balance from the other. No lost updates, no
  negative balances from a race, proven under 800 concurrent transactions across 20
  accounts.
- **Any transaction, at any concurrency level:** either fully committed (one ledger
  entry, both balances updated, hash chain extended) or not committed at all —
  never partially.

What is *not* guaranteed: which of two racing, independent requests gets processed
first (only that the outcome is total and consistent, not that arrival order is
honored); any of it surviving a process restart; and, per §3, any protection at all
against a party that already holds the HMAC key.
