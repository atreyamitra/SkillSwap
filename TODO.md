# TODO

Known gaps, prioritized. Each one names the problem, not just the fix.

## Highest priority

1. **Build and test the Android app on a real machine with the SDK
   installed.** Everything under `activities/`, `fragments/`, and
   `adapters/` has been read carefully but never compiled here — see
   `STATUS.md`. Run `./gradlew assembleDebug` and `./gradlew
   testDebugUnitTest` before trusting it.
2. **Wire the in-memory `TransactionLedger` and `JdbcLedgerStore` together.**
   They share domain types (`LedgerEntry`, `LedgerEntryFactory`) but nothing
   currently makes the in-memory engine's commits durable. The natural next
   step is a variant that writes through to the SQL store inside the same
   logical operation — not done yet because getting the failure semantics
   right (what happens if the SQL write fails after the in-memory commit
   succeeds?) needs its own design pass, not a quick patch.
3. **Finish converting silent `onError` bodies to logging.** The pattern
   `public void onError(String message) { }` still appears in
   `FavoritesActivity`, `ManageSkillsActivity`, `EditProfileActivity`,
   `UserDetailActivity`, `HomeFragment`, `RequestsFragment`, and
   `ChatListFragment`. `MainActivity`'s two were converted to
   `AppLogger.w(...)` as a worked example.

## Medium priority

4. Run the SQL schema (`app/src/main/resources/db/migration/`) against real
   PostgreSQL, not just H2 — the schema is written to be portable, but this
   hasn't been confirmed against a real Postgres instance.
5. Bound the in-memory ledger's idempotency-key map (`TransactionLedger`) —
   it grows without limit; a long-running process needs a TTL/eviction
   policy.
6. Enforce swap-request status transitions server-side (a Firebase
   security rule or transaction), not just client-side in
   `RequestsFragment` — the client-side guard closes the common case but
   not a true race between two devices.
7. Extract a shared base for the six Firebase repository classes to remove
   their repeated `ValueEventListener` boilerplate.
8. Add a lint step's results as a required check, and turn on branch
   protection so CI passing is actually enforced before merge (currently
   the workflow runs but nothing requires it to pass).

## Lower priority

9. Instrumented/Espresso UI tests for the login → discover → send-request flow.
10. `FirebaseDatabase.setPersistenceEnabled(true)` for basic offline support.
11. Extract `ValidationUtils`'s email/password logic into a pure-Java class
    so it can be unit tested without Robolectric.
12. Code coverage tooling (e.g. JaCoCo), if a number becomes more useful
    than case-by-case review.
