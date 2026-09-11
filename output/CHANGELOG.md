# CHANGELOG — Wells Fargo Tech Intern (India) Resume Rewrite

## Source
- No resume file and no `facts.md` exist in this repo (SkillSwap is an unrelated Android app repo). Source of truth was the previously-uploaded PDF resume ("Atreya_Mitra_Wells_Fargo_2027_Full_Page_Resume.pdf"). No facts were invented beyond what appeared in that file.

## Eligibility / header
- Graduation month is not stated in the source ("Expected 2028" only). Kept 2028, added `[NEED METRIC: graduation month]` instead of guessing May/July.
- No home city is stated in the source. Used "Jaipur, India" (the university's city) as a placeholder since no other location exists in the source — this is an assumption, not a confirmed fact. Flag for candidate to correct if home city differs.
- Added an availability line ("Open to Bengaluru / Hyderabad | Summer 2027 Internship") — consistent with +91 phone / Manipal India context. Did not add any US work-authorization or sponsorship language, since the source never states US eligibility.
- Did not convert CGPA 8.16/10 to a 4.0 scale — no official conversion existed in the source.

## Credibility leaks removed
- Deleted "client purchase pending" from the freelance bullet — this phrase undercut the work as unfinished/unpaid.
- Kept the freelance engagement in EXPERIENCE (it's real client work), but flagged missing dates and payment status: `[NEED METRIC: start month, end month, paid Y/N]`.
- Dropped the "KLA Track challenge" fragment along with the RestorNet-S project (see below) — it was unexplained and read as an unverified award/competition claim.

## Projects
- Capped at 3 projects per instructions: kept **ScaleLink**, **AuthForge**, and **AccessLearn**.
- Dropped **RestorNet-S** (ML/PyTorch project) — not one of the two priority slots, and the fixed 6-section structure (no "Additional Technical Work" section) left no room for a 4th project.
- Dropped **Sentiment Analysis (IMDB)** and **Tumor Classification (Breast Cancer Wisconsin)** entirely — both are canonical tutorial/Kaggle datasets that read as coursework, not applied engineering, and the required section list has no "Additional Technical Work" home for them.
- Removed per-project "GitHub" label-only hyperlinks. The source never provided the actual per-repo URLs (only a generic "GitHub" link), so individual repo links were not invented. The single visible GitHub profile URL (github.com/atreyamitra) stays in the header. If the candidate wants individual repo links, they need to supply the actual URLs.

## Experience
- Trimmed freelance bullets to 2, per instructions. "9 routes" kept as scope description, not framed as an achievement.
- Razorpay HMAC signature verification bullet kept and tied to "payment integrity" — this word is justified by the existing bullet (signature verification, timing-safe comparison), not sprayed on unrelated work.

## Leadership
- Rewrote the Oneiros bullet using only existing words (logistics, scheduling, event days). Did not invent team size, attendance, or budget — added `[NEED METRIC: team size, number of events, attendance]` instead.

## Skills / keywords
- Skills list built only from tools already present in the source, grouped as Languages & Frameworks | Data & ML | Dev / SDLC.
- **Excel / PowerPoint are missing from the source resume — did not add them.** Add only after the candidate has real Excel/PowerPoint usage to point to.
- Did not add "risk," "stakeholders," or "financial analysis" — no existing bullet supports these terms.
- Added "payment integrity" (tied to the Razorpay HMAC verification bullet) and "accessibility compliance" (tied to the AccessLearn axe-core audit reaching 0 violations) — both are directly supported by existing work, not generic keyword stuffing.

## Structure
- Used exactly the 6 required sections: CONTACT, EDUCATION, EXPERIENCE, PROJECTS, LEADERSHIP, SKILLS. No separate PROFILE (the instructions only allow an optional 2-line profile; omitted since the header + section content already cover graduation year, stack, and proof points within the 1-page budget). No separate CERTIFICATIONS section — NPTEL/Oracle/Red Hat credentials were folded into a one-line "Relevant coursework/certifications" note under EDUCATION, since that was the only place in the fixed structure that could hold them without inventing a 7th section.

## Format
- No docx tooling exists in this repo (it's an Android/Gradle app, unrelated to document generation). Produced Markdown (`wf-tech-india.md`) and plain text (`wf-tech-india.txt`) instead, per the fallback instruction.
- Suggested human export filename: `Mitra_Atreya_WellsFargo_TechIntern_2027.pdf` (no "Full_Page" retained).

## Quality bar (0–10)
| Category | Score | Note |
|---|---|---|
| Parse safety | 10 | Single column, plain headings, visible full URLs, no tables/icons/columns |
| Eligibility clarity | 6 | Grad month still missing — left as `[NEED METRIC]` rather than guessed |
| JD relevance (WF India Tech intern) | 8 | Strong backend/auth/testing/payments signal; Excel/PPT gap is honest, not padded |
| Quantification | 8 | Real numbers kept throughout (10/190 requests, 200 concurrent, 26 tests, 0 violations, 20 concurrent rotations) |
| Leadership | 4 | Still the weakest section — no team size/scale available in source; flagged, not invented |
| Tool honesty | 10 | Every skill/tool listed already appeared in the source resume |
| Skim clarity | 9 | 6 clean sections, dense but scannable in ~8 seconds |

Categories below 8 (eligibility clarity, leadership) are below threshold specifically because of missing source facts — left as `[NEED METRIC]` placeholders rather than padded with invented numbers, per instructions.

## HireVue stories this resume now supports
1. **"Tell me about a time you found/fixed a subtle bug or edge case."** → AuthForge: refresh-token rotation race conditions, stale-token rejection after logout, verified across 20 concurrent requests.
2. **"Describe a time you ensured the security or integrity of a system."** → Freelance/Redline Garage: server-side Razorpay HMAC signature verification with timing-safe comparison (payment integrity).
3. **"Tell me about a project you owned end-to-end, including deployment."** → ScaleLink: distributed rate limiter built, tested (Jest/k6), and deployed via Terraform-provisioned Azure VMs behind a load balancer.
4. **"Describe a time you coordinated people or logistics under time pressure."** → Oneiros Operations Team: on-ground logistics/scheduling for the university fest across multiple event days.
5. **"Tell me about a time you improved accessibility or followed a compliance/quality standard."** → AccessLearn: axe-core audit, fixed 2 violations (1 critical), reached 0 violations across all routes.
