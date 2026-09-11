# Status

Current state of the repository at a glance. Last updated 2026-09-11
(SQL/database persistence pass).

## What works

- Full app source compiles by inspection (no Android SDK available in the
  development sandbox to run a real `./gradlew assembleDebug` — see
  `BUILD_NOTES.md` §1 and `AUDIT.md` §9 for exactly what that means and how
  correctness was checked instead).
- The domain model (`models/`, `exception/`), the pure-utility classes
  (`utils/MatchUtils`, `utils/DistanceUtils`, `firebase/DatabasePaths`), and
  the ledger module (`ledger/`, `ledger/crypto/`, `ledger/exception/`,
  `ledger/sql/`) are unit/integration tested and passing —
  **140/140 tests**, compiled clean with `javac -Xlint:all -Werror` (zero
  warnings). The 4 concurrency-specific test classes (2 in-memory, 2
  SQL-backed) were additionally run 30 times each in fresh JVMs with zero
  failures to rule out flakiness. See `AUDIT.md` §5-7 and
  `docs/ENGINEERING_DECISIONS.md`/`docs/INTEGRITY_AND_IDEMPOTENCY.md`/
  `docs/DATABASE_DESIGN.md` for the exact verification methods.
- **Flagship feature:** `ledger/TransactionLedger` (in-memory) and
  `ledger/sql/JdbcLedgerStore` (durable, real-schema-backed) — an idempotent,
  HMAC-tamper-evident transaction ledger with a normalized SQL persistence
  layer, full design writeups (`docs/INTEGRITY_AND_IDEMPOTENCY.md`,
  `docs/DATABASE_DESIGN.md`) and a threat model (`docs/THREAT_MODEL.md`).
  Standalone and Android/Firebase-free by design — not currently wired into
  any SkillSwap screen (the app has no monetary feature), so the whole
  module, migrations included, is fully compiled and tested in this sandbox
  against a real (H2) database with no Android SDK gap to flag. Maven
  Central was reachable from this sandbox (unlike Google's Maven repo, which
  Android's own build needs — see `BUILD_NOTES.md` §1), which is what made
  a real H2 dependency and real migration-runner testing possible here.
- Firebase security rules (`firebase/database.rules.json`) are written and
  documented but have not been deployed against a live Firebase project from
  this sandbox — deploying and exercising them requires a real Firebase
  console, which is out of this repo's reach here.

## What's new in this pass (domain-hardening)

- **`RequestStatus`/`SessionStatus` enums** replace the four/three
  `public static final String` status "constants" that used to live on
  `SwapRequest`/`Session`. Both enums encode a validated finite state
  machine (`canTransitionTo`, `requireTransitionTo`, `isTerminal`) instead
  of being a bare closed set of values.
- **A small exception hierarchy** (`exception/SkillSwapException` and two
  subclasses) for the two real business-rule violations in this domain
  (invalid status transition, out-of-range rating) — everything else still
  uses the JDK's own `IllegalArgumentException`/`NullPointerException`.
- **Defensive, validating constructors** on `SwapRequest`, `Session`,
  `Review`, and `Message` — null checks plus real domain rules ("can't swap
  with yourself," "rating must be 1-5," "can't review yourself," "message
  text can't be blank").
- **`equals`/`hashCode`** added to every model, keyed on the database
  identity (`requestId`, `sessionId`, `reviewId`, `uid`, `messageId`), with
  `Message` specially handling the not-yet-sent (`null` id) case.
- **`User.getSkillsTeach()`/`getSkillsWant()`** now return an unmodifiable
  view, and their setters copy rather than alias the input list — closes a
  real (if latent) encapsulation gap, confirmed safe by checking every call
  site was already read-only.
- A concurrency-correctness guard in `RequestsFragment`: accept/reject/
  complete now check `RequestStatus.canTransitionTo(...)` before writing,
  so a stale UI (e.g. two devices racing on the same request) fails safely
  instead of silently overwriting a terminal status.
- `docs/ENGINEERING_DECISIONS.md` — the "why," including the two biggest
  tradeoffs (why status fields stay `String` on the wire but `enum` at the
  API boundary; why models are *not* fully immutable).
- 6 Android-dependent call sites updated mechanically to the new enum types
  (`SwapRequestRepository`, `SessionRepository`, `RequestAdapter`,
  `ChatListFragment`, `RequestsFragment`, `MainActivity`) — every changed
  line is traced in `docs/ENGINEERING_DECISIONS.md` §7.

## What has NOT changed

- No monetary values exist anywhere in this app (it's a skill-barter app,
  not a marketplace) — "use BigDecimal for money" had nothing to attach to;
  noted rather than invented.
- Models are still not *fully* immutable — setters remain, deliberately (see
  `docs/ENGINEERING_DECISIONS.md` §4) — because two of them
  (`User.setUid`, `Message.setMessageId`) are load-bearing for a Firebase
  quirk (the database key isn't embedded in a node's own JSON payload) in
  files this sandbox cannot compile-check.
- `ValidationUtils`, `PrefsManager`, `NotificationUtils`, `LocationUtils`,
  and every layout XML are untouched — all either depend on the Android
  framework directly or are UI wiring this sandbox cannot compile.
- `BUILD_NOTES.md` was left as-is; it was already accurate.

## Known unknowns

- The 6 mechanically-edited Android files (listed above) were traced
  line-by-line and brace-balance-checked, but **not compiled** — this
  sandbox has no Android SDK. Verify with `./gradlew assembleDebug` on a
  machine with SDK access before merging; see `HANDOFF.md`.
- The GitHub Actions CI workflow has been reviewed for correctness but not
  observed to run, since GitHub-hosted runners (with real internet access)
  are required to fetch the Android SDK/AGP that this sandbox cannot reach.
  Watch the first run after this branch is pushed — it will catch anything
  the point above missed.
