# TODO

Prioritized, in the order a next contributor should tackle them. Each item
names why it's next and roughly how big it is.

## Ledger module (`ledger/`) — see docs/INTEGRITY_AND_IDEMPOTENCY.md §7 for the full list

- **Wire it into a real feature, or don't.** The ledger is currently standalone
  (no caller in the app). The natural fit would be a future "session credits"
  feature; until/unless that's built, this module is a portfolio-grade
  subsystem in its own right, not a half-integrated feature. Don't force an
  integration just to have one.
- **Bounded idempotency-key retention.** `byIdempotencyKey` grows without limit;
  a real deployment needs a TTL/eviction policy (e.g. evict keys older than
  24h) so long-running processes don't leak memory.
- **Persistence.** Everything is in-memory; a process restart loses the ledger.
  Not attempted here because it would require picking a real storage layer,
  which is a bigger, separate decision than this pass's scope.
- **Multi-process idempotency.** Today's guarantees are per-JVM-instance only;
  a distributed version needs a shared store (e.g. a database with a unique
  constraint on idempotency key) instead of a local `ConcurrentHashMap`.

## Highest priority

0. **Compile-verify the domain-hardening pass on a real Android SDK.** This
   sandbox has no Android SDK, so the enum-status refactor (`RequestStatus`/
   `SessionStatus` replacing String constants) was traced line-by-line
   across its 6 call sites but never run through `javac`+AGP for real. Run
   `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` on a machine
   with SDK access before merging; see `docs/ENGINEERING_DECISIONS.md` §7
   for the exact list of touched files and `STATUS.md` for what's verified
   vs. not.

1. **Add a small, real SQL-backed feature.** The repository currently has
   zero SQL (Firebase Realtime Database only — see `AUDIT.md` §8). The most
   defensible, non-invented way to close this gap: use Room (SQLite) to
   cache the signed-in user's own swap-request history locally for offline
   viewing on the Requests tab. This is a genuinely useful feature (offline
   support), not padding, and it would give real, discussable SQL/ORM
   experience (schema design, a DAO with a couple of real queries, a
   migration). Estimated size: one `@Entity`, one `@Dao`, one `RoomDatabase`
   subclass, and a small sync step in `SwapRequestRepository`. **This is the
   single highest-value next improvement** — see the note at the end of
   `AUDIT.md`.

2. **Unit test `ValidationUtils` without an Android dependency.** Extract the
   email-regex/null-check logic into a pure-Java class so it can be tested
   the same way `MatchUtils`/`DistanceUtils` are now, without pulling in
   Robolectric. Must preserve exact validation behavior (same accepted/
   rejected emails) — needs a careful diff review, not a rewrite.

3. **Run the new CI workflow for real** and fix whatever a real Android SDK
   turns up that this sandbox couldn't check (resource typos this repo's own
   manual audit might have missed, deprecation warnings, etc.).

## Medium priority

4a. **Enforce status transitions server-side.** `RequestStatus`/
    `SessionStatus.canTransitionTo` now guards writes client-side
    (`RequestsFragment`), which closes the common case but not a true race
    between two clients — that needs a Firebase `runTransaction` or a
    security rule that validates the *previous* value, not just the new
    one. Needs a live Firebase project to test; see
    `docs/ENGINEERING_DECISIONS.md` §2.

4. Add a shared generic base for the six Firebase repository classes to
   remove the repeated `ValueEventListener`/callback boilerplate
   (`AUDIT.md` §2) — real duplication, but risky to do blind without a
   compiler; do it once CI is green so the refactor can be verified.

5. Guard against double-submission races on "write once" actions (send swap
   request, submit review) with a simple in-flight flag in the relevant
   `Activity`, on top of the existing pre-write existence checks
   (`AUDIT.md` §6).

6. Add a lint step (`./gradlew lint`) to the CI workflow once it's confirmed
   green.

## Lower priority / nice-to-have

7. Instrumented (on-device) or Espresso UI tests for at least the
   login → discover → send-request happy path.

8. `FirebaseDatabase.setPersistenceEnabled(true)` for basic offline
   resilience, plus retry/backoff on writes (`AUDIT.md` §4).

9. Explore Firebase App Check / rate limiting for the live project once one
   exists (`AUDIT.md` §7).
