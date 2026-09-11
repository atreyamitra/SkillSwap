# Status

Where this repository actually stands, plainly.

## Works and is verified

- All 140 JUnit tests pass (`./gradlew testDebugUnitTest`), including 85 tests
  for the `ledger`/`ledger/sql` module and 55 for the rest of the domain model
  and utilities.
- The `ledger` module (in-memory engine + H2-backed SQL store) has zero
  Android/Firebase dependency, so it was compiled and run directly with
  `javac`/`java` during development, independent of the Android build.
- The two concurrency stress tests (in-memory and SQL-backed) were each run
  30 times back to back with zero failures.
- CI (`.github/workflows/android-ci.yml`) runs the full test suite plus
  Android Lint on every push/PR.

## Not yet verified

- The Android app itself (`activities/`, `fragments/`, layouts) has not been
  built with a real Android SDK in this environment — no local machine with
  Android Studio was available while writing this. The code has been read
  carefully and cross-checked against its imports, but `./gradlew
  assembleDebug` should be run on a real machine before trusting it fully.
  See `BUILD_NOTES.md` for the exact commands.
- Firebase security rules (`firebase/database.rules.json`) are written but
  have not been exercised against a live Firebase project.

## Known gaps (see TODO.md for the full list)

- No instrumented/UI tests.
- The in-memory ledger and the SQL store aren't wired together — they share
  types but nothing currently persists the in-memory engine's writes.
- Several Firebase `onError` callbacks still silently swallow failures;
  `MainActivity`'s two were converted to logging as a worked example, the
  rest are listed in `TODO.md`.
