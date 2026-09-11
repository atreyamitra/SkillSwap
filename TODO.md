# TODO

Prioritized, in the order a next contributor should tackle them. Each item
names why it's next and roughly how big it is.

## Highest priority

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
