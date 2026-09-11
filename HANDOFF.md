# Handoff

What a new contributor (or reviewer) needs to know before touching this code.

## Read these first, in order

1. `README.md` — what the project is and how it's laid out.
2. `BUILD_NOTES.md` — the detailed architecture doc, Firebase schema, security
   rules, and a demo script. This is the source of truth for "why" decisions.
3. `AUDIT.md` — an honest score card of what's solid and what's not, and
   exactly what was verified vs. assumed in the most recent review pass.
4. `TODO.md` — what to do next, in priority order.

## Environment constraints that shaped this pass

This repository was reviewed and extended inside a sandboxed container with:
- **No Android SDK** — no `platforms;android-34`, no build-tools.
- **No network access to `dl.google.com`** — the Android Gradle Plugin and
  every AndroidX/Firebase/Material dependency live there and could not be
  fetched.

Both are documented in detail (with the exact error reproduced) in
`BUILD_NOTES.md` §1. Practical consequence for anyone continuing this work
from a similar sandbox: you **cannot** run `./gradlew assembleDebug` or
`./gradlew testDebugUnitTest` here, because both require compiling against
AndroidX/Firebase, which requires the blocked network path. What you *can*
do, and what this pass did:
- Compile and run any test class whose production code has **zero**
  Android/Firebase imports directly with `javac`/`java` against the
  JUnit/Hamcrest jars bundled with the local Gradle install
  (`/opt/gradle-*/lib/junit-*.jar`, `hamcrest-core-*.jar`) — see the exact
  commands and output in `AUDIT.md` §5.
- Review (but not execute) the new GitHub Actions workflow
  (`.github/workflows/android-ci.yml`) — it will run for real on GitHub's
  own runners, which have full internet access.

If you're picking this up on a normal development machine with internet
access, none of this applies to you — open the project in Android Studio,
let Gradle sync, and everything above "just works." The constraints only
bind inside this specific sandbox.

## What is safe to change without an Android build

- Anything under `app/src/test/java/` and any pure-Java class it tests
  (currently `MatchUtils`, `DistanceUtils`, `DatabasePaths`) — verify with
  the manual `javac`/`java` steps above, no Android SDK required.
- Markdown docs.
- `.github/workflows/*.yml` — review carefully (action versions, job
  dependencies) since it can't be dry-run locally, but it's low-risk: a
  broken CI file fails loudly in the Actions tab rather than corrupting
  anything.

## What is NOT safe to change without a real Android build available

- Any `Activity`/`Fragment`/`Adapter`/layout XML — a typo'd `binding.foo`
  or resource id fails at build time, not at review time, and this sandbox
  cannot catch that.
- `ValidationUtils`, `PrefsManager`, `NotificationUtils`, `LocationUtils` —
  all depend on Android framework classes (`TextUtils`, `SharedPreferences`,
  `NotificationManager`, `FusedLocationProviderClient`) and cannot be
  compiled or tested here. `TODO.md` #2 proposes a safe path to make
  `ValidationUtils` testable without changing its behavior.
- `firebase/database.rules.json` — changes here are security-critical and
  should be tested against a real Firebase project (or the Firebase Rules
  Simulator) before being trusted, not just read for plausibility.

## Who to ask / where decisions live

This is a single-developer educational project — there's no team to ask.
Every non-obvious design decision that would normally live in tribal
knowledge is instead written down in `BUILD_NOTES.md`'s viva-prep Q&A
section; check there before assuming something is unintentional.
