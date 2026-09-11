# TODO

Prioritized, in the order a next contributor should tackle them. Each item
names why it's next and roughly how big it is.

## SDLC infrastructure (see docs/SDLC.md)

- **Finish converting silent `onError` bodies to `AppLogger` calls.**
  `MainActivity`'s two are done (see `STATUS.md`); the same empty
  `public void onError(String message) { }` pattern still exists in
  `FavoritesActivity`, `ManageSkillsActivity`, `EditProfileActivity`,
  `UserDetailActivity`, `HomeFragment`, `RequestsFragment`, and
  `ChatListFragment` (grep for the exact string to find every remaining
  spot). Mechanical, low-risk, one line each — deliberately not done in one
  sweep here since none of it can be compiled in this sandbox and a batch
  edit across 7 files raises the odds of an unnoticed typo.
- **Wire branch-protection + required status check** (GitHub repo settings,
  not a file) so `android-ci.yml` passing is actually required before merge
  — currently the workflow runs but nothing enforces it.
- **Code coverage tooling** (e.g. JaCoCo) if this project ever wants a
  number instead of case-by-case "does this have a test" review.

## Ledger module (`ledger/`, `ledger/sql/`) — see docs/INTEGRITY_AND_IDEMPOTENCY.md §7 and docs/DATABASE_DESIGN.md "Tradeoffs" for the full list

- **Wire the in-memory engine and the SQL store together.** They currently exist
  side by side, sharing domain types (`LedgerEntry`, `LedgerEntryFactory`,
  `LedgerIntegrityVerifier`) but with no code path where `TransactionLedger`
  actually writes through to `JdbcLedgerStore` for durability. The natural next
  step is a `TransactionLedger` variant (or a decorator) that commits in-memory
  *and* persists via the store inside the same logical operation — deliberately
  not done in this pass, since getting the failure semantics right (what
  happens if the SQL write fails after the in-memory commit succeeds?) is a
  real design question, not a mechanical wiring exercise.
- **Wire either into a real SkillSwap feature, or don't.** Both are currently
  standalone (no caller in the Android app). The natural fit would be a future
  "session credits" feature; until/unless that's built, this module is a
  portfolio-grade subsystem in its own right, not a half-integrated feature.
- **Bounded idempotency-key retention** (in-memory engine only — the SQL store
  has no equivalent unbounded map). `byIdempotencyKey` grows without limit; a
  real deployment needs a TTL/eviction policy (e.g. evict keys older than 24h)
  so long-running processes don't leak memory.
- **A real PostgreSQL run.** The schema is written to be PostgreSQL-compatible
  and tested against H2 (`MODE=PostgreSQL`) since this sandbox has no running
  Docker daemon for Testcontainers — see `docs/DATABASE_DESIGN.md`. Running the
  same migrations and test suite against a real PostgreSQL instance (locally,
  or via Testcontainers on a machine with Docker available) would close that
  gap and is the single most valuable thing to verify next for this layer.
- **Migration rollback tooling.** `SchemaMigrator` applies forward only, by
  design (see its Javadoc) — no `down` migrations. Fine for this project's
  three additive, reviewed-by-hand migrations; a real multi-developer project
  would want either a real migration framework or a hand-written rollback
  story before this scales past a handful of files.

## Highest priority

0. **Compile-verify the domain-hardening pass on a real Android SDK.** This
   sandbox has no Android SDK, so the enum-status refactor (`RequestStatus`/
   `SessionStatus` replacing String constants) was traced line-by-line
   across its 6 call sites but never run through `javac`+AGP for real. Run
   `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` on a machine
   with SDK access before merging; see `docs/ENGINEERING_DECISIONS.md` §7
   for the exact list of touched files and `STATUS.md` for what's verified
   vs. not.

1. ~~Add a small, real SQL-backed feature.~~ **Done** — see `ledger/sql/`
   (`JdbcLedgerStore`, a migrated PostgreSQL-compatible schema, and 20
   integration tests against a real H2 database) and `docs/DATABASE_DESIGN.md`.
   This superseded the original plan here (a Room/SQLite cache of swap-request
   history in the Android app itself) in favor of a standalone module that's
   fully testable in this sandbox without an Android SDK — see
   `docs/DATABASE_DESIGN.md`'s opening section for why. The Android-side
   Room/SQLite idea is still a reasonable, separate future feature if the app
   ever needs offline swap-request viewing, but is no longer needed to
   demonstrate real SQL competence in this repository.

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
