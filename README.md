# SkillSwap

A native Android app (Java, no Kotlin/Compose) where users list skills they can
**teach** and skills they want to **learn**, get matched against other users by a
transparent scoring algorithm, send/accept skill-swap requests, chat, schedule
sessions, and leave ratings.

This is an independent educational/portfolio project built to demonstrate Java
fundamentals, layered application design, and basic backend integration
(Firebase Authentication + Realtime Database). It is not affiliated with, built
for, or endorsed by any employer, and none of its data or "test" accounts are real.

## Why this project

Most tutorial-driven Android apps skip the parts that actually distinguish
engineering work from "connect a screen to a database": preventing duplicate
writes, keeping denormalized data consistent, writing server-side authorization
rules instead of trusting the client, and having a testable core instead of
logic wired directly into `Activity` classes. This project's design choices
(see `firebase/DatabasePaths.java`, `firebase/database.rules.json`, and
`utils/MatchUtils.java`) were made with those concerns in mind.

## Tech stack

| Layer | Choice |
|---|---|
| Language | Java 17 |
| UI | Android Views + ViewBinding + Material Components (no Compose) |
| Auth | Firebase Authentication (email/password) |
| Data | Firebase Realtime Database (JSON tree, path-based security rules) |
| Background work | WorkManager (session reminders) |
| Location | FusedLocationProviderClient + Haversine distance |
| Testing | JUnit 4 (local, pure-Java unit tests) |
| CI | GitHub Actions (`./gradlew testDebugUnitTest`, `assembleDebug`) |

minSdk 24, target/compileSdk 34.

## Architecture

```
com.skillswap.app/
  activities/   11 screens (Splash, Login, Register, Main, UserDetail, Chat,
                EditProfile, ManageSkills, SessionSchedule, Review, Favorites)
  fragments/    4 bottom-nav destinations hosted inside MainActivity
  adapters/     RecyclerView.Adapter subclasses (ViewHolder pattern)
  models/       Plain POJOs mirroring the database (User, SwapRequest,
                Message, Session, Review)
  firebase/     DatabasePaths (single source of truth for path strings),
                AuthManager, and one thin repository per data type —
                Activities/Fragments never call FirebaseDatabase directly
  utils/        MatchUtils, DistanceUtils, ValidationUtils, PrefsManager,
                NotificationUtils, LocationUtils
```

Activities/Fragments are the view + controller layer; the `firebase/` package is
a thin repository layer so database access and path strings live in one place
instead of being copy-pasted across every screen; `utils/` holds
side-effect-free logic (matching, distance, validation) kept deliberately free
of Android framework dependencies so it can be unit tested without an emulator.

Full architecture notes, the Firebase schema, security rules explanation, and a
20-question viva-prep Q&A are in [`BUILD_NOTES.md`](BUILD_NOTES.md).

## The matching algorithm

`utils/MatchUtils.computeMatch()` scores a candidate match 0-100 as the sum of
two independently capped 50-point halves:

- how much of what **they teach** overlaps with what **you want to learn**
  (scaled by how much of your want-list that covers), and
- how much of what **you teach** overlaps with what **they want to learn**.

This rewards a genuinely mutual trade over a one-directional overlap — the
whole point of a "swap" — using nothing more exotic than case-insensitive set
intersection. See `MatchUtilsTest.java` for the worked examples.

## Testing

Local, dependency-free unit tests live under `app/src/test/java`:

- `MatchUtilsTest` — 8 cases covering full/partial/zero overlap, case
  insensitivity, duplicate skills, empty and null lists.
- `DistanceUtilsTest` — Haversine distance against a known city-pair distance,
  symmetry, antipodal points, and the "unset coordinates" sentinel.
- `DatabasePathsTest` — conversation-id ordering, determinism, and null safety.

Run them with:

```bash
./gradlew testDebugUnitTest
```

(These were also verified in this repository's development sandbox — which
has no Android SDK or network access to Google's Maven repo — by compiling
and running them directly against plain JUnit 4 with `javac`/`java`, since
none of the three classes under test have any Android framework dependency.
See `AUDIT.md` for details.)

There are currently no instrumented (on-device) or UI tests; see `TODO.md`.

## Building

You need Android Studio (or the Android SDK + a network connection to
`dl.google.com`) and a Firebase project. Full step-by-step setup — creating the
Firebase project, enabling email/password auth, applying the Realtime
Database rules, and a suggested two-account demo flow — is in
[`BUILD_NOTES.md`](BUILD_NOTES.md).

```bash
git clone <this repo>
cd SkillSwap
# place your own app/google-services.json (see app/google-services.json.example)
./gradlew assembleDebug
```

## Project docs

- [`BUILD_NOTES.md`](BUILD_NOTES.md) — architecture, Firebase schema & security
  rules, demo script, viva-style Q&A.
- [`AUDIT.md`](AUDIT.md) — an honest engineering self-review: scores, evidence,
  and what was fixed.
- [`STATUS.md`](STATUS.md) — current state at a glance.
- [`TODO.md`](TODO.md) — known gaps and next steps, prioritized.
- [`HANDOFF.md`](HANDOFF.md) — what a new contributor needs to know before
  touching this code.
