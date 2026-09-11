# Engineering Decisions

The most important design decisions in the domain layer (`models/`,
`exception/`), what they cost, and what they buy. Written so each one survives
the question "why does this exist?" in an interview — if a change couldn't
survive that question, it isn't in this codebase.

## 1. Status fields became enums, not Strings — but only at the boundary

**Before:** `SwapRequest.status` and `Session.status` were plain `String`
fields, each with four/three `public static final String` "constants"
(`STATUS_PENDING`, `STATUS_ACCEPTED`, ...) that were really just documentation
— nothing stopped `request.setStatus("Pending")` (wrong case) or
`request.setStatus("APROVED")` (typo) from compiling and silently corrupting
data.

**After:** `RequestStatus` and `SessionStatus` are enums. The public API
(`getStatus()`/`setStatus(...)`) only ever exchanges the enum type, so an
invalid status string is no longer representable through normal code —
the compiler rejects it.

**The tradeoff that made this safe to do at all:** Firebase's Android SDK
deserializes POJOs by reflection directly into whatever type a field is
declared as, using the no-arg constructor — it never calls the other
constructors or any setter you write. If the model's `status` field were
literally typed `RequestStatus`, correctness would depend on Firebase's
enum-mapping behavior, which this sandbox has no way to exercise against a
real database. Instead, the field stays a plain `String status;` internally
(exactly the format already being written to the database), and the
`RequestStatus`/`SessionStatus` conversion happens only inside the getter and
setter:

```java
public RequestStatus getStatus() { return status == null ? null : RequestStatus.valueOf(status); }
public void setStatus(RequestStatus status) { this.status = Objects.requireNonNull(status).name(); }
```

This is a small anti-corruption layer at the model's boundary: the wire
format never changes (so nothing about the Firebase schema or existing data
is at risk), but every line of application code now works with a type-safe,
exhaustively-switchable enum. It's the same reason a REST client typically
parses `"ACTIVE"` into an `enum Status` right at the deserialization
boundary instead of passing raw strings deeper into the app.

**What this did NOT fix:** an object loaded straight from
`DataSnapshot.getValue(SwapRequest.class)` never runs the validating
constructor — Firebase sets `status` via reflection after calling the no-arg
constructor. So a corrupt value written directly against the REST API (or by
a bug elsewhere) would still deserialize; `getStatus()` would then throw
`IllegalArgumentException` from `RequestStatus.valueOf(...)` when read. That
failure mode is a **known, accepted gap** — closing it fully would mean a
custom `ValueEventListener` that parses each field defensively instead of
letting Firebase's generic mapper do it, which is a real, larger change this
pass didn't make.

## 2. A validated finite state machine, not just an enum

**Decision:** `RequestStatus`/`SessionStatus` aren't just a closed set of
values — they carry the actual transition graph:

```
PENDING --accept--> ACCEPTED --complete--> COMPLETED
   \--reject--> REJECTED
```

`canTransitionTo(target)` encodes exactly the edges above; REJECTED and
COMPLETED are terminal. `RequestsFragment`'s accept/reject/complete handlers
now check `canTransitionTo` before writing, instead of trusting that the UI
only ever shows the button for a request that's actually still PENDING.

**Why this matters beyond "looks nice":** the UI already hides the Accept
button once a request isn't PENDING — so in the common case this guard never
fires. It exists for the case the UI can't prevent: two devices (or two tabs)
acting on the same request. If a request was accepted from device A a moment
ago, device B's still-stale list might still show the Accept button; without
the guard, tapping it would silently overwrite `ACCEPTED` back onto an
already-accepted (or worse, already-completed) request. The guard turns that
race into "nothing happens, user sees a message" instead of "database
now holds a state nothing can act on." This is a genuine, if modest,
concurrency-correctness improvement, not decoration.

**What was deliberately not built:** true prevention of that race would
need a server-side transaction (Firebase's `runTransaction`) or security
rules that validate the *previous* value, not just the new one. That's a
bigger change to `database.rules.json` and untestable without a live
Firebase project from this environment — left as a `TODO.md` item rather
than shipped half-verified.

## 3. Two custom exceptions, not a framework-sized hierarchy

**Decision:** exactly two domain exception types
(`InvalidStatusTransitionException`, `InvalidRatingException`), both
extending a small abstract `SkillSwapException`. Everything else — null
arguments, "can't message yourself," "can't review yourself" — throws the
JDK's own `NullPointerException`/`IllegalArgumentException`.

**Why not more custom types, and why not fewer:** the two business rules that
got their own exception are the two that represent an *actual domain
constraint* someone would ask about in review ("what happens if you try to
accept an already-rejected request?", "what stops a 0-star rating?"). Every
other validation failure here is a generic "this argument was bad," which is
exactly what `IllegalArgumentException` is for — inventing
`SelfSwapException`, `SelfMessageException`, `SelfReviewException`, etc.
would be three barely-distinguishable one-off classes with no shared
handling anywhere, which is the "unnecessary abstraction" this pass was
explicitly asked to avoid. The dividing line — *domain rule vs. programmer
error* — is the same one most production Java codebases draw, and it's a
clean answer to "why is this a custom exception but that isn't?"

**Why unchecked, not checked:** every caller in this codebase is a UI click
handler or a Firebase callback that can only show the failure to the user —
there's no code path that recovers differently based on catching a checked
exception. Checked exceptions would only add `throws` clauses nothing acts
on.

## 4. Immutability was applied selectively, not everywhere

**Decision:** models did **not** become fully immutable (final fields, no
setters, one all-args constructor). `SwapRequest`, `Session`, `Review`,
`Message`, and `User` all keep their existing setters.

**Why, given the brief explicitly asked for immutability where
appropriate:** two setters are load-bearing in ways a fully immutable design
would have to work around blind, in files this sandbox cannot compile
(`UserRepository.getUser`/`getAllUsers` call `user.setUid(snapshot.getKey())`
because Firebase doesn't store a node's own key inside its JSON payload;
`ChatRepository.sendMessage` calls `message.setMessageId(key)` for the same
reason, after the push key is allocated). Making those fields final would
mean replacing this with a "wither" method (`withUid(String uid)` returning
a new instance) — a legitimate pattern, but one that changes the shape of
two repository methods this environment has no way to compile-check, for a
benefit (immutability of a `uid`/`messageId` field specifically) that's
marginal next to the risk of shipping an unverified change to files nobody
here can build.

**Where immutability *was* pushed hardest, and why that case was different:**
`User.getSkillsTeach()`/`getSkillsWant()` now return
`Collections.unmodifiableList(...)`, and `setSkillsTeach`/`setSkillsWant`
copy their input instead of aliasing it (see `UserTest`). This was safe to
do confidently because every call site was already grepped and confirmed
read-only (`UserCardAdapter`, `ProfileFragment`, `HomeFragment`,
`UserDetailActivity` all just read the list to render or score it;
`ManageSkillsActivity` already keeps its own separate mutable copy rather
than mutating the model's list in place). Encapsulation was worth adding
exactly where it was provably risk-free — not applied as a blanket rule.

**The honest framing for an interview:** immutability is a tool, not a
badge. Applying it to `skillsTeach`/`skillsWant` prevents a real bug (a
future caller mutating what they think is "just a getter" and being
surprised the model never persisted the change); applying it to `uid` would
have required rewriting working, unverifiable repository code for a much
smaller payoff. Knowing which is which is the actual skill being
demonstrated here.

## 5. Defensive validation lives in constructors, with named limits

**Decision:** the validating constructors (`SwapRequest`, `Session`,
`Review`, `Message`) reject nulls and enforce the domain rules that were
previously undocumented assumptions: a request needs two *different* users,
a review needs a rating in [1, 5] and can't be self-authored, a message
needs non-blank text and can't be self-sent, a session needs two different
participants.

**Why constructors and not, say, a separate `Validator` class:** every one
of these rules is a precondition on the object being constructible at all —
there's no valid `SwapRequest` with `senderId.equals(receiverId)`, ever. A
separate validator would be indirection with no caller that benefits from it
being decoupled from construction. This is "meaningful interfaces rather
than unnecessary abstraction," applied by *not* introducing an abstraction.

**The limit, stated plainly (see Decision 1):** these constructors protect
objects the app builds. They do not protect objects Firebase hands back via
reflection-based deserialization, because that path never calls them. Every
one of the four models says so in its class-level Javadoc rather than
implying a guarantee the code doesn't actually provide.

## 6. `equals`/`hashCode` are identity-based, matching what "the same
   record" means for a synced document

**Decision:** every model's `equals`/`hashCode` compares only the database
key (`requestId`, `sessionId`, `reviewId`, `uid`) — never the other fields.
`Message` is the one exception: it compares by `messageId`, but two messages
that both still have a `null` messageId (not yet sent) are equal only by
reference (`==`), never to each other.

**Why not compare every field:** these are live documents synced from a
database with realtime listeners. Two `User` objects for the same `uid`, one
fetched a second before the other after a profile edit, are *the same user*
even though `bio` differs — value-equality on every field would make a
`Set<User>` or a `RecyclerView` diff treat "the same person, slightly
stale" as two different people. Key-based equality is the correct semantic
here, the same reason JPA/Hibernate entities are conventionally compared by
ID rather than by value.

**Why `Message` needs the null-messageId special case:** without it, two
freshly-typed-but-not-yet-sent messages with the same text would `equals()`
each other (`null == null` are equal ids), which is wrong — they are two
distinct, not-yet-persisted things that happen to not have an identity yet.
`MessageTest.twoUnsentMessagesAreNotEqualEvenWithSameContent` pins this down.

## 7. What was in scope for this pass, and what wasn't

Everything above touches: `models/` (5 files), a new `exception/` package (3
files), and the narrowest possible set of call sites needed to keep the
enum change compiling in the files that reference status
(`SwapRequestRepository`, `SessionRepository`, `RequestAdapter`,
`ChatListFragment`, `RequestsFragment`, `MainActivity` — 6 files, all
mechanical `String` constant → enum swaps, traced line-by-line and listed in
`STATUS.md`). Deliberately **not** touched: `ValidationUtils` (Android
framework dependency, already flagged in `AUDIT.md` #1), `PrefsManager`,
`NotificationUtils`, `LocationUtils`, any layout XML, and the repository
classes' internal `ValueEventListener` boilerplate (real duplication, noted
in `AUDIT.md` #2 and `TODO.md` #4, left alone because refactoring six
call sites without a compiler to check them against is a bigger bet than
this pass was willing to make blind). "Smallest change with the largest
truthful improvement" cuts both ways: it's also a reason to stop.
