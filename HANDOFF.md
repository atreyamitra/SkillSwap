# Handoff

What to read before touching this code, in order:

1. `README.md` — what the project is and how it's laid out.
2. `BUILD_NOTES.md` — architecture, Firebase schema, security rules.
3. `docs/SDLC.md` — how this project is built/tested/reviewed, and its
   Definition of Done.
4. `TODO.md` — what's next, in priority order.

## Build environment note

The Android app (`activities/`, `fragments/`, layouts, Firebase integration)
needs Android Studio or a standalone Android SDK (platform 34 + build-tools)
with normal internet access — Gradle needs to fetch the Android Gradle Plugin
and every AndroidX/Firebase dependency from Google's Maven repository the
first time you sync. See `BUILD_NOTES.md` §2 for the exact setup.

The `ledger`/`ledger/sql` module has **no** Android dependency at all — it's
plain Java 17 + JUnit + JDBC, and can be built and tested with just a JDK, no
Android SDK required.

## What's safe to change without a full Android build

- `app/src/test/java/` and anything it tests that has no Android import
  (`models/`, `utils/MatchUtils`, `utils/DistanceUtils`, `firebase/DatabasePaths`,
  and the whole `ledger/` module).
- Markdown docs.
- `.github/workflows/*.yml` — review action versions and job dependencies
  carefully since you can't dry-run it locally, but a broken workflow file
  fails loudly in the Actions tab rather than corrupting anything.

## What needs a real Android build to verify

- Any `Activity`/`Fragment`/`Adapter`/layout XML — a typo'd `binding.foo` or
  resource id only fails at build time.
- `ValidationUtils`, `PrefsManager`, `NotificationUtils`, `LocationUtils` —
  all depend on Android framework classes directly.
- `firebase/database.rules.json` — test against a real Firebase project or
  the Rules Simulator before trusting a change here; it's security-critical.

## Where design decisions are written down

`docs/ENGINEERING_DECISIONS.md` (domain model), `docs/INTEGRITY_AND_IDEMPOTENCY.md`
and `docs/DATABASE_DESIGN.md` (the ledger module), and `BUILD_NOTES.md`'s Q&A
section (everything else). If something looks unintentional, check there
first — it's probably a deliberate tradeoff with a paragraph explaining why.
