# SkillSwap

[![Android CI](https://github.com/atreyamitra/SkillSwap/actions/workflows/android-ci.yml/badge.svg)](https://github.com/atreyamitra/SkillSwap/actions/workflows/android-ci.yml)

The badge reflects the default branch's latest run of
[`android-ci.yml`](.github/workflows/android-ci.yml) (checkout → JDK 17 →
`testDebugUnitTest` → `lintDebug` → `assembleDebug`, all fail-on-error — see
[`docs/SDLC.md`](docs/SDLC.md) §6). It won't show green until that workflow has
actually run on `main`; this repo does not claim CI status it hasn't earned.

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

## Flagship engineering feature: an idempotent, tamper-evident transaction ledger

`app/src/main/java/com/skillswap/app/ledger/` is a standalone, Android/Firebase-free
Java module — a thread-safe, idempotent transaction ledger with HMAC-based integrity
verification, available both in-memory and backed by a real, migrated SQL schema. It
exists to demonstrate, with 140 passing tests (including two deterministic
concurrency stress tests — one in-memory, one against a real database — each
verified over 30 consecutive runs with zero flakiness), a precise answer to: **what
happens if two requests hit this service at exactly the same time?**

- Duplicate/concurrent-duplicate request detection via idempotency keys — enforced
  in Java (`ConcurrentHashMap#computeIfAbsent`'s at-most-once-per-key guarantee) AND,
  independently, by a database `UNIQUE` constraint (`ledger/sql/`), closing the
  multi-process gap the in-memory version alone can't
- Atomic updates and lost-update prevention (a single locked critical section
  in-memory; an atomic guarded `UPDATE ... WHERE balance >= ?` plus a JDBC
  transaction at the database layer)
- A tamper-evident HMAC-SHA256 hash chain, with constant-time comparison and
  length-prefixed canonical encoding
- A normalized SQL schema (PostgreSQL-compatible DDL, tested against H2) with
  primary/foreign keys, `UNIQUE`/`NOT NULL`/`CHECK` constraints, and indexes matched
  to real access patterns — every constraint proven by a test that deliberately
  tries to violate it, including by bypassing the Java layer entirely with raw SQL
- An honest, scoped threat model — explicitly **not** claiming PCI DSS compliance,
  "banking-grade" security, or any regulatory certification

Full design writeup: [`docs/INTEGRITY_AND_IDEMPOTENCY.md`](docs/INTEGRITY_AND_IDEMPOTENCY.md).
Database design: [`docs/DATABASE_DESIGN.md`](docs/DATABASE_DESIGN.md).
Threat model: [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md).

## Prerequisites

- JDK 17 (matches `app/build.gradle`'s `sourceCompatibility`/`targetCompatibility`
  and the CI workflow's `setup-java` step)
- Android Studio (Koala/2024.1+) or a standalone Android SDK with platform 34 +
  build-tools, reachable over the network the first time you sync (see
  `BUILD_NOTES.md` §1 if that sync fails in a sandboxed environment)
- A Firebase project, for anything beyond compiling/testing (see "Setup" below)

## Setup

```bash
git clone https://github.com/atreyamitra/SkillSwap.git
cd SkillSwap
cp app/google-services.json.example app/google-services.json  # placeholder; see below
```

To actually run the app (sign up, store data), replace that placeholder with a
real `app/google-services.json` from a Firebase project where you've enabled
Email/Password auth and created a Realtime Database with the rules in
`firebase/database.rules.json` — full step-by-step in
[`BUILD_NOTES.md`](BUILD_NOTES.md) §3. Compiling and running the test suite
(next section) does **not** need a real Firebase project — the placeholder is
enough.

**Run it:**
```bash
./gradlew installDebug   # installs onto a connected device/emulator
# then launch "SkillSwap" from the device's app drawer, or:
adb shell am start -n com.skillswap.app/.activities.SplashActivity
```

**Test it:**
```bash
./gradlew testDebugUnitTest   # exact command CI runs; see docs/SDLC.md §6
```

## Tech stack

| Layer | Choice |
|---|---|
| Language | Java 17 |
| UI | Android Views + ViewBinding + Material Components (no Compose) |
| Auth | Firebase Authentication (email/password) |
| Data | Firebase Realtime Database (JSON tree, path-based security rules) |
| Background work | WorkManager (session reminders) |
| Location | FusedLocationProviderClient + Haversine distance |
| Testing | JUnit 4 (unit + H2-backed integration tests, no Robolectric) |
| Static analysis | Android Lint (`./gradlew lintDebug`, in CI) |
| CI | GitHub Actions (`.github/workflows/android-ci.yml` — test, lint, assemble) |
| Dependency updates | Dependabot (`.github/dependabot.yml` — Gradle + Actions, weekly) |

minSdk 24, target/compileSdk 34.

## Architecture

```
com.skillswap.app/
  activities/   11 screens (Splash, Login, Register, Main, UserDetail, Chat,
                EditProfile, ManageSkills, SessionSchedule, Review, Favorites)
  fragments/    4 bottom-nav destinations hosted inside MainActivity
  adapters/     RecyclerView.Adapter subclasses (ViewHolder pattern)
  models/       POJOs mirroring the database (User, SwapRequest, Message,
                Session, Review), each with a validating constructor,
                identity-based equals/hashCode, and — for SwapRequest and
                Session — a status enum (RequestStatus/SessionStatus) that
                encodes a real finite state machine instead of a bare
                String constant
  exception/    A small hierarchy (SkillSwapException and two subclasses)
                for actual business-rule violations, as distinct from
                generic bad-argument errors (which use plain
                IllegalArgumentException/NullPointerException)
  firebase/     DatabasePaths (single source of truth for path strings),
                AuthManager, and one thin repository per data type —
                Activities/Fragments never call FirebaseDatabase directly
  utils/        MatchUtils, DistanceUtils, ValidationUtils, PrefsManager,
                NotificationUtils, LocationUtils
```

Activities/Fragments are the view + controller layer; the `firebase/` package is
a thin repository layer so database access and path strings live in one place
instead of being copy-pasted across every screen; `models/` and `utils/` hold
side-effect-free domain logic (matching, distance, validation, status
transitions) kept deliberately free of Android framework dependencies so it
can be unit tested without an emulator. The tradeoffs behind the domain model
— why status fields are `enum`-typed in the API but `String`-typed on the
wire, why models aren't *fully* immutable — are written up in
[`docs/ENGINEERING_DECISIONS.md`](docs/ENGINEERING_DECISIONS.md).

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

All automated tests live under `app/src/test/java` and run as plain JUnit 4 —
**140 tests total**, no Android/Robolectric dependency, all runnable with the
one command in "Setup" above:

- **Unit tests**: `MatchUtilsTest`, `DistanceUtilsTest`, `DatabasePathsTest`,
  `RequestStatusTest`/`SessionStatusTest`, `SwapRequestTest`/`SessionTest`/
  `ReviewTest`/`MessageTest`/`UserTest` (domain model validation and
  `equals`/`hashCode`), and the `ledger/` module's own unit tests (HMAC,
  constant-time comparison, idempotency, and two deterministic concurrency
  stress tests — see [`docs/INTEGRITY_AND_IDEMPOTENCY.md`](docs/INTEGRITY_AND_IDEMPOTENCY.md)).
- **Integration tests**: `ledger/sql/*Test` — run against a real, migrated H2
  database (schema, constraints, transactions, and rollback all exercised for
  real, not mocked) — see [`docs/DATABASE_DESIGN.md`](docs/DATABASE_DESIGN.md).

(All of this was also verified directly in this repository's development
sandbox — which has no Android SDK — by compiling and running the
Android-framework-free subset with plain `javac`/`java`. See `AUDIT.md` for the
exact commands and `docs/SDLC.md` §4 for the full testing picture.)

There are currently no instrumented (on-device) or UI tests; see `TODO.md`.

## Project docs

- [`docs/SDLC.md`](docs/SDLC.md) — this project's lifecycle end to end
  (requirements → design → implementation → testing → review → CI → release
  concept → maintenance) and its Definition of Done.

- [`BUILD_NOTES.md`](BUILD_NOTES.md) — architecture, Firebase schema & security
  rules, demo script, viva-style Q&A.
- [`docs/ENGINEERING_DECISIONS.md`](docs/ENGINEERING_DECISIONS.md) — the
  domain model's design decisions and their tradeoffs.
- [`docs/INTEGRITY_AND_IDEMPOTENCY.md`](docs/INTEGRITY_AND_IDEMPOTENCY.md) /
  [`docs/DATABASE_DESIGN.md`](docs/DATABASE_DESIGN.md) /
  [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md) — the ledger module's design,
  its SQL schema, and its threat model, in full.
- [`AUDIT.md`](AUDIT.md) — an honest engineering self-review: scores, evidence,
  and what was fixed.
- [`STATUS.md`](STATUS.md) — current state at a glance.
- [`TODO.md`](TODO.md) — known gaps and next steps, prioritized.
- [`HANDOFF.md`](HANDOFF.md) — what a new contributor needs to know before
  touching this code.
