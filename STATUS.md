# Status

Current state of the repository at a glance. Last updated 2026-09-11.

## What works

- Full app source compiles by inspection (no Android SDK available in the
  development sandbox to run a real `./gradlew assembleDebug` — see
  `BUILD_NOTES.md` §1 and `AUDIT.md` §9 for exactly what that means and how
  correctness was checked instead).
- Core matching, distance, and chat-id logic is unit tested and passing
  (18/18 tests — see `AUDIT.md` §5 for the exact verification method and
  `TODO.md` for what still has zero coverage).
- Firebase security rules (`firebase/database.rules.json`) are written and
  documented but have not been deployed against a live Firebase project from
  this sandbox — deploying and exercising them requires a real Firebase
  console, which is out of this repo's reach here.

## What's new in this pass

- `app/src/test/java/` — first automated tests in the repository
  (`MatchUtilsTest`, `DistanceUtilsTest`, `DatabasePathsTest`).
- `.github/workflows/android-ci.yml` — first CI workflow in the repository.
- `README.md`, `AUDIT.md`, `STATUS.md` (this file), `TODO.md`, `HANDOFF.md`.

## What has NOT changed

- No production Java/XML source files were modified — this pass added tests
  and documentation only, to avoid making unverifiable behavior changes in a
  sandbox with no Android build capability. See `AUDIT.md` for every place a
  code change was considered and deliberately deferred, and why.
- `BUILD_NOTES.md` was left as-is; it was already accurate.

## Known unknowns

- The new GitHub Actions workflow has been reviewed for correctness but not
  observed to run, since GitHub-hosted runners (with real internet access)
  are required to fetch the Android SDK/AGP that this sandbox cannot reach.
  Watch the first run after this branch is pushed.
