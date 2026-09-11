# SkillSwap — Build Notes

SkillSwap is a Java + XML Android app (no Kotlin, no Compose) for a college mini-project:
users list skills they can teach and skills they want to learn, get matched against
other users, send/accept swap requests, chat, schedule sessions, and leave ratings.

Package: `com.skillswap.app` · minSdk 24 · target/compileSdk 34 · ViewBinding · Material
Components · Firebase Auth + Realtime Database.

---

## 1. Build status

The `ledger`/`ledger/sql` module (see `README.md`) has no Android dependency
and is compiled and tested directly with `javac`/JUnit — no Android SDK
needed for that part.

The Android app itself has **not** been compiled with a full Android Studio
setup while writing this. Every file was checked by hand instead: every
`R.id`/`R.layout`/`R.string`/`R.color`/`R.drawable` reference against its
declared resource, every `ViewBinding` field access against the layout it's
generated from, every `Activity` against its `AndroidManifest.xml` entry, and
every Firebase path string against `DatabasePaths` (no root path is
hardcoded anywhere else). No mismatches found, but this is not a substitute
for a real compile — see `STATUS.md` for exactly what's verified and what
isn't, and run `./gradlew assembleDebug` on a machine with the SDK installed
before trusting the app builds.

## 2. Opening and building the project in Android Studio

1. Install **Android Studio** (Koala/2024.1+ recommended) with the Android SDK for API
   34 (SDK Manager will offer this automatically).
2. `git clone` this repo and choose **Open** in Android Studio, pointing at
   the repo root (the folder containing `settings.gradle`).
3. Let Gradle sync finish — first sync downloads AGP 8.2.2, Gradle 8.14.3 (wrapper is
   already checked in under `gradle/wrapper/`), and every AndroidX/Material/Firebase
   dependency. This requires normal internet access, which your machine has.
4. **Firebase setup** (required before the app will actually authenticate/store data):
   1. Go to the [Firebase console](https://console.firebase.google.com/), create a new
      project (any name, e.g. "SkillSwap Demo").
   2. Add an **Android app** to that Firebase project with package name exactly
      `com.skillswap.app`.
   3. Download the generated `google-services.json` and place it at
      `app/google-services.json` (that exact path — sibling of `app/build.gradle`).
      A structurally-similar placeholder lives at `app/google-services.json.example` —
      do **not** commit your real file; it's already gitignored.
   4. In the Firebase console, enable **Authentication → Sign-in method → Email/Password**.
   5. In the Firebase console, create a **Realtime Database** (not Firestore) in test
      mode initially, then go to the **Rules** tab and paste the contents of
      `firebase/database.rules.json` from this repo (see section 3 below for what the
      rules do), then Publish.
5. Run the app on an emulator (API 24+) or physical device via the ▶ Run button, or
   build an APK via **Build → Build Bundle(s) / APK(s) → Build APK(s)** — the resulting
   `app-debug.apk` will be under `app/build/outputs/apk/debug/`.

## 3. Firebase Realtime Database structure & security rules

```
users/{uid}                              -> User profile (name, bio, skills, rating…)
swapRequests/{requestId}                 -> One skill-swap request
messages/{conversationId}/{messageId}    -> conversationId = sorted "uidA_uidB"
sessions/{sessionId}                     -> A scheduled meeting for an accepted swap
reviews/{reviewId}                       -> 1-5 star rating + comment for a completed swap
favorites/{uid}/{targetUid} : true       -> uid's favorited profiles
```

All root-level path segments are defined exactly once, in
`app/src/main/java/com/skillswap/app/firebase/DatabasePaths.java`, and every repository
class (`UserRepository`, `SwapRequestRepository`, `ChatRepository`, `SessionRepository`,
`ReviewRepository`, `FavoritesRepository`) reads from that class instead of hardcoding
strings.

`firebase/database.rules.json` (paste into the Firebase console's Rules tab) enforces,
in plain terms:
- **Everything requires being signed in** (`auth != null`) — the default is
  read/write `false` at the root, with each subtree opening up explicitly.
- **`users/{uid}`**: any signed-in user can *read* any profile (needed for Discover /
  match-scoring), but can only *write* their own (`auth.uid === $uid`).
- **`swapRequests/{requestId}`**: any signed-in user can read all requests (simplifies
  the Incoming/Sent/Accepted lists); a request can only be *created* by its sender
  (`newData.senderId === auth.uid`), and can only be *updated* (e.g. accept/reject/
  complete) by whichever of sender or receiver is currently signed in.
- **`messages/{conversationId}/…`**: read/write allowed only if the signed-in uid is
  one of the two ids encoded in the conversation id itself (`$conversationId` is
  `uidA_uidB`, checked with `.contains(auth.uid)`), and each message's `senderId` must
  match the writer's own uid.
- **`sessions/{sessionId}`**: readable by any signed-in user; writable only by one of
  the two participants (`userA`/`userB`).
- **`reviews/{reviewId}`**: readable by all signed-in users (needed to compute average
  ratings); a review can only be *created*, never edited afterward, and only by its own
  `reviewerId` — this backs up the app-side duplicate-review check.
- **`favorites/{uid}/…`**: fully private — only the owning uid can read or write their
  own favorites list.

## 4. Notifications — what's real here vs. what would need a backend

`NotificationUtils` creates two real Android notification channels (`channel_requests`,
`channel_sessions`) and posts genuine local notifications when:
- a new **incoming** swap request appears while `MainActivity` is running (detected via
  a Firebase `ValueEventListener` diffing snapshots),
- one of the current user's **sent** requests flips to ACCEPTED while the app is running,
- a scheduled **session reminder** fires, via a one-off `WorkManager` job
  (`SessionReminderWorker`) enqueued the moment a session is scheduled.

These are legitimate local notifications and will show up in the system tray, request
the `POST_NOTIFICATIONS` runtime permission on Android 13+, and work correctly.

**What this is not**: true push notifications that arrive even when the app process has
been fully killed (swiped away) require **Firebase Cloud Messaging (FCM) plus a small
server or Cloud Function** that listens for the same database writes server-side and
sends a push payload to the device's FCM token. That's real backend infrastructure and
is intentionally out of scope for a student mini-project demo — the above WorkManager +
foreground-listener approach is the standard "good enough" pattern taught for this kind
of assignment, and is honestly labelled here rather than faked.

## 5. Location — what's real here vs. limits

`LocationUtils` wraps `FusedLocationProviderClient` (play-services-location) and is used
from `EditProfileActivity` ("Use my current location" button, behind a runtime
permission request for `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION`) and from
`HomeFragment` (to compute "X km away" on Discover cards via the Haversine formula in
`DistanceUtils`). Every call is guarded: if permission was never granted, was denied, or
the location is simply unavailable (no last-known fix yet), the callback's
`onUnavailable()` path runs and nothing crashes — the UI just omits distance info.

## 6. What the matching algorithm does (for the viva)

See `app/src/main/java/com/skillswap/app/utils/MatchUtils.java` — fully commented. In
short: the 0-100% score is the sum of two independent halves, each worth up to 50
points —
- how much of what the **other person teaches** overlaps with what **you want to
  learn** (scaled by how much of your "want" list that covers), and
- how much of what **you teach** overlaps with what the **other person wants to
  learn** (scaled by how much of your "teach" list that covers).

This deliberately rewards a genuinely *mutual* trade over a one-directional overlap,
which is the whole point of a "swap". Skill-name comparison is case-insensitive and
whitespace-trimmed so "Guitar" and "guitar " are treated as the same skill.

---

# TEST DATA — for your live demo

No fake data is seeded automatically (Firebase Auth accounts must be created for real,
by you, through the Register screen). Suggested pair for the viva demo:

**User 1 — Arjun**
- Name: `Arjun Verma`, Email: `arjun.demo@example.com`, Password: `demo123`
- Teaches: `Java`, `DSA`
- Wants to learn: `Guitar`, `Photography`

**User 2 — Riya**
- Name: `Riya Sharma`, Email: `riya.demo@example.com`, Password: `demo123`
- Teaches: `Guitar`, `Photography`
- Wants to learn: `Java`

**Expected match score, Arjun looking at Riya's profile**: Riya teaches Guitar &
Photography (both of which are in Arjun's "want" list — 2 of 2 want-items covered →
full 50 points), and Arjun teaches Java (which is in Riya's "want" list — 1 of 2 of
Arjun's own teach-items happens to be wanted by Riya → 25 points). Total: **≈75%
match**. (Exact number depends on list order/size per the formula in `MatchUtils`.)

Register both accounts, add their skills via **Profile → Manage Skills**, then from
Arjun's account open Riya's card on the Discover tab to see the match score and
explanation, send a swap request (offer Java, request Guitar), switch to Riya's account
to accept it, chat, schedule a session, mark it complete, and leave a review — that's
the full demo flow end-to-end.

---

# Viva prep

## Architecture overview

```
com.skillswap.app/
  activities/   11 screens: Splash, Login, Register, Main (bottom-nav host),
                UserDetail, Chat, EditProfile, ManageSkills, SessionSchedule,
                Review, Favorites
  fragments/    4 bottom-nav destinations hosted inside MainActivity:
                Home (Discover), Requests (Incoming/Sent/Accepted tabs),
                ChatList, Profile
  adapters/     RecyclerView.Adapter subclasses: UserCardAdapter, RequestAdapter,
                ChatListAdapter, MessageAdapter (sent/received view types)
  models/       Plain POJOs mirroring the database: User, SwapRequest, Message,
                Session, Review (no-arg constructors required for Firebase's
                automatic deserialization)
  firebase/     DatabasePaths (single source of truth for path strings),
                AuthManager, and one thin repository per data type
                (UserRepository, SwapRequestRepository, ChatRepository,
                SessionRepository, ReviewRepository, FavoritesRepository) —
                Activities/Fragments never call FirebaseDatabase directly
  utils/        MatchUtils (matching algorithm), PrefsManager (SharedPreferences),
                ValidationUtils (form checks), NotificationUtils + 
                SessionReminderWorker (local notifications), LocationUtils,
                DistanceUtils (Haversine)
```

This is a fairly standard layered MVC-ish structure for a student Firebase app:
Activities/Fragments are the "controller + view" (they own ViewBinding and react to
UI events), models are dumb data holders, and the firebase/ package is a thin
repository layer so database access logic (and path strings) live in one place instead
of being copy-pasted across every screen.

## Likely viva questions & answers

1. **Why Realtime Database and not Firestore?** Simpler mental model for a small demo
   (a single JSON tree), cheaper to explain path-based security rules, and the standard
   choice taught alongside Firebase Auth in most college courses.
2. **Why ViewBinding and not findViewById?** Compile-time null-safety and type-safety —
   a typo'd id fails the build instead of crashing at runtime with a
   `NullPointerException`; also avoids unnecessary View casts.
3. **How does the match score work?** See section 7 above — two capped 50-point halves
   for mutual skill overlap, computed in `MatchUtils.computeMatch()`.
4. **How do you prevent duplicate swap requests?**
   `SwapRequestRepository.hasExistingActiveRequest()` scans for any PENDING or ACCEPTED
   request between the same two users before allowing a new one.
5. **How do you prevent duplicate reviews?**
   `ReviewRepository.hasReviewedSwap()` checks whether a review already exists for the
   same `(swapId, reviewerId)` pair before writing, both client-side (before showing the
   form as submittable) and again right before the actual write.
6. **How is the average rating kept in sync?** After a review write succeeds,
   `ReviewActivity` re-reads all reviews for that `reviewedUserId`
   (`getForUserOnce`), recomputes the mean client-side, and writes `avgRating` +
   `ratingCount` back onto that user's profile.
7. **How does chat know which "room" to use?** `DatabasePaths.conversationId(uidA,
   uidB)` sorts the two uids alphabetically and joins them with `_`, so both
   participants always compute the identical conversation key regardless of who sends
   first.
8. **Why can't you chat before a request is accepted?** The Chats tab is built by
   filtering the user's swap requests down to ACCEPTED/COMPLETED ones only — there's no
   conversationId shown for a PENDING request, so there's no route into ChatActivity
   for it. (The database rule doesn't structurally prevent messaging an arbitrary uid,
   but nothing in the UI exposes that path — a real production app would also want to
   validate this server-side against an accepted-request record if this needed to be
   airtight.)
9. **What happens if a user denies the location permission?** Every location call
   is wrapped so it degrades gracefully — `LocationUtils.hasLocationPermission()` is
   checked first, and both `getLastKnownLocation`'s success and failure paths are
   handled; the UI simply shows no distance / doesn't offer "use my location" results,
   never crashes.
10. **What's the security rule for messages?** Read/write is only allowed if the
    signed-in uid is one of the two ids encoded directly in the `conversationId` string,
    checked with `$conversationId.contains(auth.uid)`.
11. **Why store `senderName`/`receiverName` directly on a SwapRequest instead of
    joining against `users/{uid}` every time?** Denormalization for read simplicity in a
    Realtime Database (no server-side joins) — trades a small risk of a stale display
    name (if someone renames after sending a request) for much simpler list rendering.
12. **How would you add true push notifications?** Add the `firebase-messaging` SDK,
    store each user's FCM token under their profile, and add a small Cloud Function (or
    any server) that listens for the same database writes this app's client listens for
    now, and calls the FCM Admin SDK to push to the recipient's token — the client-side
    notification *display* code (`NotificationUtils`) would barely change.
13. **Why WorkManager for session reminders instead of AlarmManager?** WorkManager is
    the modern, batteries-optimizer-friendly API for deferred work; a one-off
    `OneTimeWorkRequest` with `setInitialDelay` is the standard way to schedule a
    "fire once, later" job without needing exact-alarm permissions.
14. **What stops someone from editing another user's profile directly in the
    database?** The security rule `".write": "auth.uid === $uid"` under `users/{uid}` —
    Firebase enforces this server-side regardless of what the client app tries to send.
15. **How is search implemented on the Discover screen?** Client-side filtering over
    the full user list already fetched (`getAllUsers`), matching the query against name
    and both skill lists case-insensitively — fine at demo scale; a production app with
    thousands of users would want a dedicated search index (e.g. Algolia) instead.
16. **What's the minSdk and why 24?** Android 7.0 (API 24) — chosen as a broad-compatible
    floor that still supports all AndroidX/Material APIs used here without extra
    compatibility shims, while covering the vast majority of real devices.
17. **How does `nonTransitiveRClass` affect resource references?** Each module gets its
    own `R` class containing only resources it actually declares (rather than a giant
    R aggregating every dependency's resources too), which is why this app's own
    resources are referenced as `com.skillswap.app.R.drawable.…` explicitly in a couple
    of adapter files instead of relying on a bare unqualified `R` — it's faster to build
    and avoids resource-id collisions across libraries.
18. **Why is there a separate `DatabasePaths` class instead of string literals
    everywhere?** Single source of truth — if a path segment ever needs to change, it
    changes in exactly one place, and it eliminates the class of bug where one file
    hardcodes `"swap_requests"` (snake_case typo) while another uses `"swapRequests"`.
19. **How would this scale past a classroom demo?** Realtime Database's `getAllUsers()`
    full-tree read for Discover doesn't scale — a production version would paginate,
    add server-side indexes, and likely move to Firestore or a real search service; the
    matching algorithm would also want to run more efficiently than client-side
    O(users × skills) once the user count grows.
20. **What testing exists?** This is a UI-heavy demo app without unit tests checked in;
    `MatchUtils` was specifically written as a small, pure, side-effect-free function
    (no Android framework dependency) specifically so it *could* be unit tested trivially
    with plain JUnit if required — that would be the natural first test to add.

## Feature → syllabus concept mapping

| Feature in the app | Concept it demonstrates |
|---|---|
| Register/Login screens | Firebase Authentication (email/password) |
| `users/{uid}` profile CRUD | Firebase Realtime Database reads/writes, JSON tree modeling |
| Discover RecyclerView + adapter | RecyclerView + Adapter/ViewHolder pattern |
| `MatchUtils` | Basic algorithm design (set intersection, weighted scoring), pure functions |
| Swap request accept/reject | State machine modeling (PENDING/ACCEPTED/REJECTED/COMPLETED) |
| Chat screen | Realtime listeners (`ChildEventListener`), event-driven UI updates |
| Session scheduling | `DatePickerDialog`/`TimePickerDialog`, standard Android dialogs |
| Review + average rating | Data aggregation, preventing duplicate writes (idempotency) |
| Favorites | Simple relational modeling in a NoSQL tree (`favorites/{uid}/{targetUid}`) |
| Notifications | `NotificationChannel`, `NotificationCompat`, runtime permissions (API 33+) |
| `WorkManager` session reminder | Deferred/background work scheduling |
| Location + Haversine distance | Runtime permissions, `FusedLocationProviderClient`, basic geometry |
| `database.rules.json` | Server-side authorization / access control |
| ViewBinding everywhere | Type-safe view access, modern Android UI binding |
| `PrefsManager` | `SharedPreferences`, simple local persistence |
| `ValidationUtils` | Input validation / form UX |
