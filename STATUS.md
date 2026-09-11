# Status

Current state of the repository at a glance. Last updated 2026-09-11
(domain-hardening pass).

## What works

- Full app source compiles by inspection (no Android SDK available in the
  development sandbox to run a real `./gradlew assembleDebug` — see
  `BUILD_NOTES.md` §1 and `AUDIT.md` §9 for exactly what that means and how
  correctness was checked instead).
- The domain model (`models/`, `exception/`) and the pure-utility classes
  (`utils/MatchUtils`, `utils/DistanceUtils`, `firebase/DatabasePaths`) are
  unit tested and passing — **55/55 tests**, compiled clean with
  `javac -Xlint:all -Werror` (zero warnings). See `AUDIT.md` §5 and
  `docs/ENGINEERING_DECISIONS.md` for the exact verification method.
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
