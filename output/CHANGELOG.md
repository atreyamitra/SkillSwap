# CHANGELOG — Wells Fargo Tech Intern (India) Resume — Final Export

## Source
- No resume file or `facts.md` exists in this repo (SkillSwap is an unrelated Android app repo). Source of truth: prior drafts (`output/wf-tech-india.md`, `.txt`, prior `CHANGELOG.md`) plus the candidate's ANSWERS for this pass. No cut items were revived (RestorNet-S, IMDB sentiment, tumor classification, "client purchase pending," per-repo GitHub links, US work authorization, invented Excel/PowerPoint all stayed out).

## Substitutions from ANSWERS
- Preferred name, phone, email, city, LinkedIn, GitHub, university, degree, CGPA, target role, availability line — all confirmed as given, no change needed (already matched prior draft).
- No ANSWER supplied a real value for any previously-missing field (all five were UNKNOWN), so there were no `[NEED METRIC]` → real-value substitutions this pass.

## UNKNOWNs — placeholder brackets removed, lines rewritten honestly instead
- **Graduation month:** UNKNOWN. Line changed from `Expected [NEED METRIC: graduation month] 2028` to `Expected 2028` — no bracket left on the page, no month guessed.
- **Freelance start/end month:** UNKNOWN. Header changed from `2026 [NEED METRIC: start month, end month, paid Y/N]` to plain `2026`.
- **Freelance paid Y/N / live Y/N:** UNKNOWN. Bullets do not claim shipped-to-production, paid, or unpaid status — described only what was built (schema, checkout flow, HMAC verification), consistent with "never say client purchase pending" and never implying a status that isn't confirmed.
- **Oneiros team size / # events / # days / attendance:** UNKNOWN. Bullet rewritten verbatim to the candidate-approved fallback line: "coordinated on-ground logistics and day-of scheduling for the university annual fest across multiple event days." No headcount invented.
- **Excel/PowerPoint real-work usage:** UNKNOWN. Not added to Skills or anywhere else.

All five UNKNOWNs are tracked here only — the resume itself now reads as complete, with no visible `[NEED METRIC]` bracket anywhere.

## Structural / wording changes this pass
- Added the allowed 2-line profile under CONTACT (Manipal CSE, Expected 2028, backend/auth/payments/testing) — fits the "no fluff" cap.
- Education coursework/certification line tightened for page-fit (shorter labels, same four credentials, no facts dropped).
- ScaleLink cut to exactly 2 bullets per instruction: kept (a) Redis Lua rate limiter + 10/190 + 200 concurrent, and (b) Docker/Nginx/Azure/Terraform deploy + 13 Jest + CI + k6. Dropped the shortcode/SET NX bullet as directed.
- AuthForge kept at 3 bullets — draft fits comfortably on one page at this length, so the JWT/RBAC/2FA bullet, the 20-concurrent refresh-rotation bullet, and the 26-Jest/Actions bullet were all retained per "3 short bullets only if they fit."
- AccessLearn cut to 2 bullets, removed the format-listing detail ("PDFs, notes, audio, video and images...simplified text, TTS audio, diagram descriptions and live captions") down to "converts study material," per instruction to drop the feature list if needed for space — applied proactively since it reads tighter without weakening the axe-core claim.
- Skills group renamed "Data & ML" → "Data" per this pass's exact grouping instruction (Languages & Frameworks | Data | Dev / SDLC). No tools added or removed.
- Filename suggestion corrected to `Atreya_Mitra_WellsFargo_TechIntern_2027.pdf` (previous draft had it reversed as `Mitra_Atreya_...`).

## Page-fit notes
- Full draft (profile + all 6 sections) is comparable in length to the prior one-page version, with AccessLearn and Education actually shortened. No cuts beyond the mandated ScaleLink/AccessLearn trims were needed — did not have to touch AuthForge bullet 3, ScaleLink deploy adjectives, or drop payment verification / refresh-token concurrency (both protected in all cases per the cut-order rule).

## Quality bar (0–10)
| Category | Score | Note |
|---|---|---|
| Parse safety | 10 | Single column, plain text headings, visible full URLs (no "GitHub" as label text, no per-repo links), no tables/icons/columns |
| Eligibility | 6 | Graduation month still unknown; page now reads clean ("Expected 2028") rather than showing a bracket, but the underlying gap remains real |
| WF India tech relevance | 8 | Backend, auth, distributed systems, payment verification, accessibility compliance all present; Excel/PowerPoint still absent (honest gap, not faked) |
| Quantification | 8 | 10/190 requests, 200 concurrent, 13 Jest tests, 20 concurrent rotations, 26 Jest tests, 2→0 axe violations all retained |
| Leadership | 4 | Oneiros bullet is honest but still has no scale metric — weakest section, unchanged from prior pass because ANSWER was UNKNOWN |
| Honesty | 10 | Nothing invented this pass; every UNKNOWN was rewritten around rather than guessed, and no cut item was revived |
| Skim clarity | 9 | 6 required sections plus a 2-line profile, dense but scannable in ~8 seconds, no leftover bracket clutter |

## 5 HireVue stories tied to bullets
1. **Razorpay HMAC signature verification** → "Tell me about a time you protected a system from tampering or fraud." (EXPERIENCE, Freelance/Redline Garage bullet 2 — server-side HMAC verification, timing-safe comparison, payment integrity.)
2. **Redis 200-concurrent rate limiter** → "Tell me about a time you validated a system under load or concurrency." (PROJECTS, ScaleLink bullet 1 — 10/190 requests, correctness under 200 concurrent requests against real Redis.)
3. **Refresh-token rotation concurrency** → "Describe a time you found and closed a race condition or security gap." (PROJECTS, AuthForge bullet 2 — atomic rotation in MongoDB, verified across 20 concurrent requests, stale-token rejection after logout.)
4. **axe-core accessibility audit** → "Tell me about a time you followed a quality or compliance standard to completion." (PROJECTS, AccessLearn bullet 2 — 2 violations, 1 critical, fixed to 0 across all routes.)
5. **Oneiros on-ground logistics** → "Describe a time you coordinated people or schedules under time pressure without a fixed script." (LEADERSHIP — day-of scheduling for the university fest across multiple event days.)
