# Software Development Lifecycle — SkillSwap

How this repository's engineering process actually works, stage by stage. Every
claim below points at a real file or command in this repo — nothing here is
aspirational. This document is deliberately sized for a small, single-developer
project: it borrows the *shape* of a professional SDLC without the machinery that
shape usually implies (no ticketing system, no staging environment, no release
train) — see each section's "Scaled-down because" note.

## 1. Requirements

Captured as plain-language feature/behavior descriptions in `BUILD_NOTES.md`
("What the matching algorithm does", the Firebase schema section) and, for
gaps/next work, `TODO.md` (each item states the problem, not just the fix).

**Scaled-down because:** one developer, no stakeholders to negotiate scope with.
A real team-scale project would track this in issues (see
`.github/ISSUE_TEMPLATE/feature_request.md`, added specifically so this repo
*could* scale to that without changing tooling).

## 2. Design

Every non-trivial decision is written down **before or alongside** the code that
implements it, with its tradeoffs — not after the fact:

- `docs/ENGINEERING_DECISIONS.md` — domain model (enums, immutability, exceptions).
- `docs/INTEGRITY_AND_IDEMPOTENCY.md` — the ledger's concurrency/integrity design.
- `docs/DATABASE_DESIGN.md` — the SQL schema, constraints, and indexes.
- `docs/THREAT_MODEL.md` — security assumptions and explicit non-claims.

**Scaled-down because:** no design-review meeting, no RFC process — one person
writes the doc and the code in the same pass. The discipline that survives at
this scale is *writing the "why" down at all*, not the ceremony around it.

## 3. Implementation

Java 17, Android Views (no Kotlin/Compose — see `BUILD_NOTES.md` for why),
layered as `activities/` → `firebase/` (repository layer) → `models/`/`utils/`
(framework-free domain logic) → `ledger/` (a standalone module with no Android
dependency at all). See the README's Architecture section for the package map.

**Scaled-down because:** no style-guide-enforcing formatter is wired into CI
(see "Formatting / static analysis" below for what *is* wired in, and why more
than that isn't, yet).

## 4. Testing

- **Automated unit tests**: `app/src/test/java/...` — 140 JUnit 4 tests covering
  `models/`, `utils/`, and the `ledger/` module's domain logic (enums, validation,
  equals/hashCode, HMAC integrity, idempotency).
- **Automated integration tests**: `app/src/test/java/com/skillswap/app/ledger/sql/*`
  — run against a real, migrated H2 database (not mocks), proving actual SQL
  constraint enforcement, transaction rollback, and cross-connection concurrency.
  These run in the *same* Gradle task as the unit tests
  (`./gradlew testDebugUnitTest`) because they're both plain JUnit tests in the
  same source set — there's no separate integration-test task to remember to run.
- **Deterministic concurrency tests**: `TransactionLedgerConcurrencyTest` and
  `JdbcLedgerStoreConcurrencyTest` assert only final-state invariants that hold
  under every thread interleaving (see their Javadoc) — verified flake-free over
  30 consecutive fresh-JVM runs during development (see `STATUS.md`).
- **No instrumented/UI tests yet** — `androidTestImplementation` dependencies are
  declared in `app/build.gradle` but no test uses them; tracked honestly in
  `TODO.md`, not hidden.

**Scaled-down because:** no code-coverage gate in CI (no coverage tool is wired
up) — coverage isn't tracked as a number, only as "does the behavior that
matters have a test," judged case by case in review.

## 5. Code review

This repository's actual review history is a single developer's own
self-review, not a second reviewer's approval — the honest state for a solo
project. The infrastructure for real review exists for when that changes:
`.github/PULL_REQUEST_TEMPLATE.md` asks every PR to state what changed, how it
was tested, and the rollback plan; `.github/ISSUE_TEMPLATE/` gives bug reports
and feature requests a consistent shape.

**Scaled-down because:** no branch-protection rule requiring an approving review
is configured (GitHub repo settings, not a file in this repo) — appropriate for
a single contributor, and the first thing to turn on if a second one joins.

## 6. Continuous Integration

`.github/workflows/android-ci.yml` runs on every push to `main`/`master` and every
pull request. It does, in order:

1. **Checkout** (`actions/checkout@v4`)
2. **Configure the correct JDK** (`actions/setup-java@v4`, Temurin 17 — matching
   `app/build.gradle`'s `sourceCompatibility`/`targetCompatibility`)
3. **Build**: implicitly, as part of running tests and lint (Gradle compiles
   before it can test)
4. **Run tests**: `./gradlew testDebugUnitTest` — all 140 tests, unit and
   integration alike
5. **Fail on test failure**: Gradle's own non-zero exit code fails the step,
   which fails the job, which fails the workflow — no `continue-on-error`
   anywhere in the file
6. **Static analysis**: `./gradlew lintDebug` (Android Lint — built into AGP,
   zero extra dependency)
7. **Test/lint report upload**: both reports are uploaded as build artifacts
   (`actions/upload-artifact@v4`) so a failure's detail is inspectable without
   re-running locally
8. A second job, `assemble`, gated on the first passing, builds a debug APK with
   a placeholder Firebase config (`app/google-services.json.example`) to catch
   compile/resource errors independent of any real Firebase project

**Scaled-down because:** one workflow file, two jobs, no matrix build (multiple
API levels/JDKs), no deployment step, no required-check branch-protection rule
wired up from the GitHub UI side. This is deliberately the minimum that
satisfies "checkout, JDK, build, test, fail on failure" — see the task that
produced this document for exactly that bar — not a mature multi-environment
pipeline, which would be disproportionate machinery for this project's size.

## 7. Release / deployment concept

There is no real release pipeline, and this section says so rather than
inventing one. If this project needed to ship a real release, the shape it
would take:

1. Tag a commit on `main` (`vX.Y.Z`), following the `versionName`/`versionCode`
   already declared in `app/build.gradle`.
2. `./gradlew bundleRelease` (App Bundle) or `assembleRelease` (APK), signed
   with a real keystore — `app/proguard-rules.pro` and the `release` build type
   already exist in `app/build.gradle` for exactly this, just never exercised
   with a real signing config in this repo.
3. Upload to the Play Console's internal testing track before any wider
   rollout.

None of this is automated today (no CD step in the workflow, no Play Console
credentials configured) — deliberately, since actually publishing this app is
out of scope for what this repository is (an educational/portfolio project, not
a shipping product — see `README.md`'s opening paragraph and
`docs/THREAT_MODEL.md` §0 for the same honesty applied to security claims).

## 8. Maintenance

- `TODO.md` — prioritized, dated, with the reasoning for each item's priority.
- `STATUS.md` — a point-in-time snapshot of what's verified vs. not, updated at
  the end of each round of work.
- `HANDOFF.md` — what a new contributor (or a future version of the same one)
  needs to know before touching the code, including the current build
  limitations (Android app not yet compiled locally, no Docker daemon
  available for Testcontainers — see `BUILD_NOTES.md` and `docs/DATABASE_DESIGN.md`).
- Dependency updates: `.github/dependabot.yml` opens weekly PRs for both Gradle
  dependencies and the GitHub Actions used in CI, so version drift is
  surfaced automatically instead of discovered during an unrelated change.

---

## Definition of Done

A change to this repository is done when:

1. **It compiles.** For the Android app: `./gradlew assembleDebug`. For the
   `ledger`/`ledger/sql` modules (no Android dependency): `javac -Xlint:all
   -Werror` against the actual sources, not just "looks right."
2. **It has a test**, or an honest note in `TODO.md`/`STATUS.md` explaining
   why not yet. A change to behavior with no test anywhere is not done.
3. **`./gradlew testDebugUnitTest` passes**, including anything newly added —
   checked locally before pushing, not left for CI to discover first.
4. **Concurrency-sensitive changes have a deterministic test** — assertions on
   final-state invariants, not timing, per
   `docs/INTEGRITY_AND_IDEMPOTENCY.md` §5's rationale — and were run more than
   once to rule out obvious flakiness before being called done.
5. **A design decision worth defending in review has a paragraph in the
   relevant `docs/*.md`** — not just a comment, if it's the kind of choice a
   future reader would reasonably ask "why?" about.
6. **No security/compliance claim beyond what's actually true** — see
   `docs/THREAT_MODEL.md` §0's explicit non-claims list; the same standard
   applies to any future doc.
7. **`README.md` still matches reality** after the change — a stale run
   command or file list is itself a bug.
