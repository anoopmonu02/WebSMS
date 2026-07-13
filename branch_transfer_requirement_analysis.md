# Mid-Session Student Branch Transfer — Requirement Analysis

Prepared for: United Avadh - Shiksha School Portal
Scope: A student enrolled at one branch of a multi-branch school relocates mid-session to another branch of the *same* school, after fees/attendance/exam history already exist at the old branch.
Constraint honored throughout: no implementation code. Every recommendation is explained; every open question is left open rather than assumed.

This document is grounded in the actual codebase (verified by reading the real entities, repositories, and controllers this session) rather than a generic ERP template. Where the requirement's example workflow references a module this system doesn't have, that is called out explicitly instead of invented.

---

## How this system is actually built (the facts everything below rests on)

Three structural facts drive almost every recommendation in this document:

**`Student` vs `AcademicStudent` are already separate.** `Student` (table `students`) is the person — name, parents, DOB, address, family account, login. `AcademicStudent` (table `academic_students`) is one enrollment record: a specific student, at a specific `school`, in a specific `academicYear`, in a specific grade/section/medium, with its own `classSrNo`/`boardSrNo`/`rollNo`/`status`. A student's transactional records (fees, attendance) hang off `academicStudent`, not off `student` directly.

**Transactional records already carry their own direct `school` + `academicYear` foreign keys, independent of the student's current record.** `FeeSubmission`, `Attendance`, `Grievance`, `ExamResultSummary` each store their own `school_id`/`academic_year_id` at the moment the row is created (confirmed by reading each entity). This means a fee receipt issued at Branch A stays permanently attached to Branch A even if the student's current enrollment record later points elsewhere — the history doesn't move just because the student does.

**`AcademicStudent` is already an append-and-supersede shape, not a pure single-row-per-student shape.** Nothing in the schema enforces "one `AcademicStudent` per student" — a student could in principle have multiple `AcademicStudent` rows (e.g., one per academic year, or currently: one Active + old ones left as Inactive). `status` already defaults to `"Active"` with `STATUS_INACTIVE` defined as a constant, and `FeeSubmission`/`Attendance` point at a specific `academic_student_id`, not directly at `student_id`. This is exactly the shape a "close old enrollment, open new enrollment" transfer needs, and it already exists for a different reason (year promotion), which is a good sign it will hold up.

One naming trap worth flagging up front: `AcademicStudent.migrationDate` is **not** what its name suggests — it's a `@CreationTimestamp`, i.e. "when this record was created," not "the date this student migrated between branches/years." Any new transfer feature should not reuse or rely on that field; it needs its own dating.

A second thing worth flagging: `Student` itself *also* stores `grade`/`section`/`medium` fields, duplicating what `AcademicStudent` stores. Today these can drift out of sync since nothing in the codebase snaps them together automatically outside specific flows. A transfer feature has to decide whether it updates `Student.grade/section/medium` too, or leaves them alone — see Phase 1, Q14.

---

## Phase 1 — Requirement Discovery

These are business questions that need the school's/customer's answer. I have not assumed answers to any of these; the rest of the document flags where the recommendation depends on the answer.

**Trigger & initiation**
1. Who can *initiate* a branch transfer request — parent (self-service), source-branch admin/staff, or only a central/super admin? Can more than one of these initiate, with different approval paths?
2. Is a transfer always parent-requested (relocation), or can the school initiate one unilaterally (e.g., rebalancing enrollment across branches, disciplinary reassignment)? Does the *reason* need to be captured and does it change the workflow?

**Approval**
3. Does the transfer need explicit approval, and from whom — source branch head, destination branch head, a central admin, or all three? Is approval mandatory for every transfer or only above some threshold (e.g., only if there's an outstanding balance)?
4. Can the destination branch *reject* an allocation (e.g., "section full"), and if so what happens next — does the request go back to the requester, or does the school just pick another section?

**Financial**
5. If the student has an outstanding balance at the old branch, what happens to it — must it be cleared before transfer, carried forward as an opening balance at the new branch, or written off? Is this a per-transfer decision or a fixed policy?
6. If the student has a *credit* (overpaid) at the old branch, does it transfer as a credit at the new branch, or get refunded?
7. Do the two branches use the same fee structure (same fee heads, same amounts, same due dates) or can they differ? If they differ, does the student get re-slotted into the destination branch's fee plan starting from the transfer date, and what happens to already-paid months that don't map cleanly?
8. Do old-branch receipts/receipt numbering stay exactly as issued (untouched), with the new branch simply starting its own receipt sequence going forward? (I'd assume yes — but confirming, since receipt numbers are often audited by number sequence.)

**Academic continuity**
9. Are grade/section/medium free to differ at the new branch (e.g., different section names, no equivalent medium offered), or must the school guarantee an equivalent slot exists before allowing the request to even be submitted?
10. Does the student need to pass any admission-style check at the new branch (interview, seat test, capacity check), or is internal transfer automatically accepted since they're already an enrolled student of the same school?
11. Is there a concept of "seats available" per grade/section at each branch today? (Verified: no capacity/seat-limit field exists anywhere in the current schema. If capacity checking is required, that's new.)

**Identity & records**
12. Should the student keep the same `registrationNo` (school-wide) after transfer, or does the destination branch issue a new one? What about `classSrNo`/`boardSrNo`/`rollNo` — these are per-enrollment today, so presumably always regenerate at the new branch, but confirming.
13. Does the *login* (the student's user account, tied 1:1 to `Student`) stay exactly the same across the transfer? (I'd expect yes, since `Student` is the person-level entity and doesn't change — but this needs confirming since some schools issue branch-specific credentials.)
14. Should `Student.grade`/`section`/`medium` (the duplicate fields living on the person-level record) be updated to mirror the new `AcademicStudent`, or left as-is? Given they can already drift from `AcademicStudent` today, this needs an explicit decision rather than inheriting whatever the current behavior happens to be.

**Timing**
15. Can a transfer be back-dated, or only same-day/future-dated? Back-dating interacts badly with already-recorded attendance/fees at the old branch for that period — does the school want that blocked entirely?
16. Is there a blackout window — e.g., no transfers during exam weeks, or in the last N weeks of the academic year — or is timing entirely at the requester's discretion?

**Sibling / family accounts**
17. `FamilyAccount` is already global (not branch-scoped) and keyed by mobile number — confirmed no code change needed there. But if siblings are enrolled together and only one transfers, does the school want any prompt/warning surfaced ("this student has 2 siblings still at the old branch"), or is that purely the parent's call with no system involvement?

**Reversal**
18. Can a transfer be cancelled/reversed after execution (student decides not to move, or it was requested in error)? If yes, within what window, and does reversal need to be a formal "reverse transfer" (its own audited event) rather than manual data editing?

**Reporting & historical correctness**
19. When someone re-runs a report for a past date (e.g., "March attendance for Branch A"), must the student still appear under Branch A for that date, even though they're now at Branch B? (I'd assume yes — this is the core "reports generated before the transfer must remain historically correct" requirement — confirming it applies uniformly to attendance, fees, and exam reports.)
20. Should the *current* branch roster (e.g., "list of Branch A students today") exclude the student going forward? (I'd assume yes, automatically, once the old enrollment is closed — confirming.)

**Retention / audit**
21. Is there a required retention period or audit-trail format mandated by any education-board or regulatory requirement in this school's jurisdiction, or is "keep everything, never delete" a sufficient policy?

**Session boundary**
22. What happens if a transfer request is raised close to year-end, overlapping with the normal year-promotion process? Should the system block transfer requests during the promotion window, or handle them as a special case?

---

## Phase 2 — Process Analysis

*(See the workflow diagram rendered above.)* The diagram marks steps 1 (request), 2 (source branch review), 5 (destination allocation), 7 (effective date), 8 (preview/confirm) and 9 (atomic execution) as **mandatory** — these can't be skipped without breaking data integrity or leaving the transfer ambiguous. Steps 3 (balance handling), 4 (head-office sign-off), 6 (destination acceptance) and 10 (notifications) are **optional/configurable** — whether they exist at all depends on the Phase 1 answers above (particularly Q3–Q6).

Two steps deserve explanation beyond the diagram labels:

*Step 8, preview before commit*, mirrors the pattern already used elsewhere in this codebase: `MigrationController` (the existing SuperAdmin family-account-linking utility) uses a scan → preview → execute flow rather than committing changes immediately. That's the strongest existing precedent in this app for "show the operator exactly what will change before it happens," and the branch-transfer wizard should follow the same shape (see Phase 9).

*Step 9, atomic execution*, means closing the old `AcademicStudent` row and creating the new one (plus writing the transfer log entry) must happen as a single database transaction. A half-completed transfer — old enrollment closed but new one not yet created, or vice versa — would leave the student invisible to both branches, which is worse than not transferring at all.

---

## Phase 3 — Module Impact Analysis

I've grouped this by what actually exists in the codebase, verified this session, rather than assuming a generic ERP's module list. Several modules named in typical transfer checklists (Library, Hostel, Transport, Timetable, Homework, formal Report Cards, Certificates, a separate Discipline module, Inventory, Payroll, dedicated Teacher/Parent portals beyond the current login, and any external API integrations) **do not exist in this system today** — there's nothing to analyze impact on, and I'm not going to invent behavior for modules that aren't there. If any of these get built later, they'll need this same analysis repeated against their real schema.

**Modules that exist and are affected:**

*Enrollment (`Student` / `AcademicStudent`).* This is the module the transfer directly acts on. Changes: a new `AcademicStudent` row at the destination branch; the old row's `status` moves to something like `"Transferred"` (a new status value, not currently defined — today only `Active`/`Inactive` exist). Stays unchanged: the `Student` row itself — same person, same login, same family account. What stays at the old branch: the closed `AcademicStudent` row exactly as it was, permanently. What starts fresh: a new `AcademicStudent` row with new `classSrNo`/`boardSrNo`/`rollNo` at the destination.

*Fees (`FeeSubmission`, `FeeSubmissionBalance`, `FeeSubmissionSub`, `FeeSubmissionMonths`).* Every existing `FeeSubmission` row points at a specific `academic_student_id` (confirmed `NotNull`, not nullable) — so old-branch receipts automatically stay attached to the old (now-closed) `AcademicStudent` row and remain queryable exactly as they were, with zero code change needed for that part. What changes: whether/how an opening balance is set on the *new* `AcademicStudent` row (the entity already has an `openingBalance` + `openingBalanceRemark` pair, originally built for year-promotion carry-forward — it's a strong candidate for carrying forward a transfer balance too, pending Phase 1 Q5/Q6). Depends entirely on the Phase 1 financial policy answers.

*Attendance.* Same shape as Fees — every `Attendance` row points at `academic_student_id` with its own `school`/`academicYear`. Old attendance stays put automatically. New attendance at the destination branch accrues against the new `AcademicStudent` row from the transfer date forward. No code change needed for historical correctness here either, *provided* the new enrollment's start date is set correctly (Phase 1 Q15).

*Examination (`ExamResultSummary`, `ExamDetails`).* Confirmed to exist with its own direct `school`/`academicYear` FKs, same pattern as Fees/Attendance. Historical results stay attached to the old branch/enrollment automatically. Open question: if an exam is scheduled to happen *during* the transfer window, which branch's exam does the student sit (Phase 7 edge case).

*Grievance.* Also carries its own `academicStudentId`/`school`/`academicYear` (built this session, same pattern deliberately followed). Open grievances at the old branch: does the transfer require them to be closed first (a "no open grievances" gate, similar to the "no outstanding balance" gate), or do they simply stay open and visible under the old branch's grievance list, orphaned from the student's new current context? This wasn't asked in Phase 1 above — worth adding: **does an unresolved grievance block a transfer, the way an outstanding balance might?**

*Messaging (`SmsMessage`).* No structural impact — messages are already a log, not a live state. Historical messages stay associated with whatever `academicStudent`/school they were sent against.

*Family accounts (`FamilyAccount`, `SiblingGroup`).* No structural change needed — already global/cross-branch, confirmed no `School` field. Transfer doesn't touch these tables at all.

*User accounts / login (`UserEntity`).* No change — `Student.userEntity` is a stable 1:1 link untouched by which `AcademicStudent` is currently active.

*Permissions (`AppScreen` / `RoleInitializer`).* New module — needs a new screen key for the transfer feature itself (Phase 8).

---

## Phase 4 — Data Integrity Analysis

The short version: because Fees, Attendance, Exams, and Grievances already point at `academic_student_id` (not `student_id`) with their own direct `school`/`academicYear`, **closing the old `AcademicStudent` row and creating a new one for the destination branch preserves all four histories automatically, with no historical row ever being edited.** This is the single most important integrity property of the recommended design, and it falls directly out of how the schema was already built — not out of anything new being added.

What still needs explicit handling:

*Receipts/serial numbers* — old-branch receipt numbers are never touched, never reused, never renumbered. The new branch's receipt sequence is independent (confirming this is already how multi-branch receipt numbering works today, per Phase 1 Q8).

*Opening balance carry-forward* — this is the one place data *does* need to be written at transfer time (if the policy says carry it forward): a value gets set on the new `AcademicStudent.openingBalance`. That's an intentional new value, not an edit to history, so it doesn't compromise the "never rewrite history" principle — it's the same mechanism already used for year-to-year promotion.

*Audit trail* — every entity already has `createdBy`/`updatedBy`/`creationDate`/`lastUpdated` (standard Hibernate audit columns). That covers "who touched this row and when" for the two `AcademicStudent` rows themselves, but it does **not** capture the transfer as an *event* — there's no single row today that says "student X moved from Branch A to Branch B on date Y for reason Z, approved by W." That's a gap, addressed in Phase 5.

*Referential safety* — no existing FK needs to change type or become nullable to support this. The design adds a new `AcademicStudent` row and a new transfer-log row; it does not modify the shape of `FeeSubmission`, `Attendance`, `ExamResultSummary`, or `Grievance` at all.

---

## Phase 5 — Database Analysis

**Is the current schema sufficient?** Mostly yes, for the historical-integrity part — that's the encouraging finding. It's insufficient in two specific ways:

1. `AcademicStudent.status` only has `Active`/`Inactive` defined as constants. A transfer needs to distinguish "closed because the student moved to another branch of the same school" from "closed/inactive for some other reason" (withdrawal, expulsion, etc.), because reporting and any future "reactivate" logic need to tell those apart. **Recommendation: add a new status value**, e.g. `"Transferred"`, used only when an `AcademicStudent` row is closed specifically due to an internal branch transfer.

2. There is no table today that records a transfer as an *event* — linking the old `AcademicStudent` row, the new one, the date, the reason, who approved it, and what happened to the balance. Without this, "why did this student have two enrollment rows" is only inferable, not stated.

**Recommendation: one new table**, something like `student_transfer` (naming aside), holding: a reference to the `Student`, a reference to the old `AcademicStudent` row, a reference to the new `AcademicStudent` row, the requested date, the effective/transfer date, a reason/remark, whatever approval fields Phase 1 Q3 requires (approved-by, approval timestamp, approval remark), the balance-handling decision actually applied (carried forward / written off / retained — a record of the *decision*, not just its effect), status of the transfer request itself (requested / approved / executed / cancelled/reversed), and the standard `createdBy`/`updatedBy`/timestamps. This single table doubles as the audit log for the feature — a separate audit table isn't needed on top of it, since every state change (request → approval → execution → possible reversal) can be represented as updates to this one row plus the existing audit columns, or as a small number of append-only status-history rows if the school wants a full state-by-state trail rather than a single mutable row (see Phase 6 for that tradeoff).

**Indexes/constraints:** FK constraints from `student_transfer` to `students`, and to `academic_students` (twice — old and new), plus an index on `(student_id, status)` for "does this student have a pending transfer" lookups, mirroring the composite-index pattern already used for the Grievance dashboard query.

**What this deliberately does *not* recommend:** no changes to `FeeSubmission`, `Attendance`, `ExamResultSummary`, or `Grievance` — their existing direct-FK-per-record design already does the job. Adding a "current branch" cache column to any of them, or retrofitting them to reference `Student` instead of `AcademicStudent`, would be unnecessary schema churn that this design specifically avoids, per the requirement's own instruction not to break existing functionality.

---

## Phase 6 — Architecture Review

Six models were asked to be weighed:

*Update-in-place* (edit the student's existing `school` field directly) — rejected. This is what the codebase already avoids for `AcademicStudent`/`Student`'s transactional children precisely because it would silently rewrite which branch old fee/attendance/exam rows "belong to" if any of them were ever joined through the student's *current* branch instead of their own stored `school_id`. It's also the one option that actively conflicts with the "reports generated before the transfer must remain historically correct" requirement.

*Pure history table* (one big append-only log of every field change) — more general than needed here. This system doesn't do full-row versioning anywhere else, and introducing it just for this one workflow would be a new architectural pattern the rest of the codebase doesn't share.

*Event sourcing* (rebuild current state by replaying events) — significant overkill for a single, occasional workflow in an otherwise straightforward CRUD system. Would require the whole application to change how it reads "current" state, which contradicts "avoid recommending changes that unnecessarily break existing functionality."

*Effective-dated rows* (every enrollment row has a validity window, current state = row where today falls in range) — closest relative of the recommendation below, but heavier than necessary since this app doesn't currently do date-range queries for "what was true on date X" anywhere else.

*Full versioning* (every entity gets a version number) — same overkill problem as event sourcing, and unrelated to what's actually needed (only `AcademicStudent` needs multiple time-bounded records; `Student`, `FeeSubmission`, etc. don't).

*Hybrid: close-old/open-new plus a dedicated transfer log* — **recommended.** This is not a new pattern for this app — it's the natural extension of the "append a new `AcademicStudent` row, mark the old one inactive" shape that (per the codebase) already exists for year-to-year promotion. It reuses `status` the same way promotion does, adds one new status value and one new table, and requires zero changes to any of the four history-bearing entities (Fees/Attendance/Exams/Grievance). It gives an explicit, queryable transfer record (unlike update-in-place), without requiring the whole app to adopt effective-dating or event-sourcing for entities that don't need it.

---

## Phase 7 — Edge Cases

From the requirement's own list, applied to this system, plus additions specific to what this codebase actually has:

- Transfer requested mid-way through a fee month that's only partially paid — does the destination branch's fee plan pick up from the next full period, or is there a pro-rated month?
- Transfer requested during an active exam cycle (`ExamDetails`/`ExamResultSummary` exist) — does the student finish the exam at the old branch, or restart at the new one? Who owns the result if the exam spans the transfer date?
- Destination branch has a different fee structure — already flagged in Phase 1 Q7, but the edge case specifically: what if a fee head at the old branch has no equivalent at the new one (or vice versa)?
- Destination grade/section doesn't exist (e.g., new branch doesn't offer that medium) — since Grade/Section/Medium are global master data (confirmed no `School` scoping), a grade *name* existing globally doesn't guarantee that branch actually runs a section for it. Needs a validation step before the request can even be submitted.
- No capacity/seat-limit concept exists today (confirmed) — so "section full at destination" can't currently be detected automatically; it would rely entirely on manual judgment at approval time, unless a capacity field gets added as part of this feature (a real scope question for Phase 1 Q11).
- Two students who are siblings both attempt separate transfer requests to different destination branches — no system conflict (FamilyAccount is global) but worth surfacing to the approver as a heads-up.
- Transfer requested and approved, but reversed before the effective date arrives (future-dated transfer, cancelled before it executes) — should be a clean cancel of the still-pending `student_transfer` row, no `AcademicStudent` changes needed yet since nothing executed.
- Transfer already *executed*, then needs to be reversed (student changes their mind) — this is not a simple cancel; it likely needs to be its own new transfer (new branch back to old), preserving both transfer events in the log, rather than deleting/undoing the first one — otherwise the audit trail lies about what happened.
- Duplicate/accidental transfer request for the same student while one is already pending — needs a "one pending transfer per student" guard.
- Transfer requested for a student who has an unresolved `Grievance` open at the old branch — flagged above in Phase 3; not in the requirement's original list but real given this codebase has a Grievance module.
- Transfer effective date falls exactly on year-end / overlaps the year-promotion process (Phase 1 Q22) — needs explicit sequencing rules so a student isn't simultaneously being promoted and transferred.
- Branch closure (a branch shutting down entirely, forcing bulk transfers) — a bulk-transfer variant of the same wizard, out of scope for a first version but worth naming since "what if an entire branch's students need to move at once" is a very different UX (batch, not per-student) from the single-student wizard this document assumes.
- Wrong destination branch selected and only discovered after execution — same answer as "reversal after execution" above: a corrective second transfer, not a data edit.

---

## Phase 8 — Security & Permissions

This app enforces access through named `AppScreen` entries (`module`, `screenName`, `screenKey`, `description`), seeded via `RoleInitializer`, checked server-side with `@CheckAccess(screen=..., type=AccessType.X)` and in templates with `sms:access="KEY:ACTION"`. The transfer feature needs its own screen key(s) rather than reusing an unrelated one (the way Grievance currently reuses `MESSAGE_SEND`/`MESSAGE_VIEW` as a known, called-out shortcut) — a dedicated `STUDENT_TRANSFER` screen keeps the audit and permission story honest for a feature this sensitive.

Who can do what needs to map onto the Phase 1 answers (Q1, Q3), but structurally the feature likely needs separate permission checks for: *initiate* a request, *approve* a request (possibly two separate approvals if both source and destination branch sign off), *execute/finalize* (may or may not be the same actor as approve), and *cancel/reverse* a request. Whichever roles get these, every action should write to the `student_transfer` row's audit fields (`createdBy`/`updatedBy`, already a standard pattern in this codebase) so "who approved this transfer" is always answerable.

---

## Phase 9 — UI/UX Flow

Recommended shape, following the `MigrationController` scan → preview → execute precedent already in this codebase:

1. **Initiate** — pick the student (likely from wherever the requester already views student records), pick a destination branch/grade/section/medium, enter a reason. Client-side validation blocks obviously invalid picks (e.g., same branch as current).
2. **Server-side checks** — outstanding balance check, open-grievance check (if Phase 1 decides these should gate), destination grade/section existence check. Surfaced as warnings or hard blocks depending on policy.
3. **Approval step(s)** — if required by policy, the request sits in a pending state until approved; approvers see the same preview described next.
4. **Preview** — before anything is written, show exactly what will happen: old enrollment (SR no / roll no / status) → closed; new enrollment → created with new SR no; balance handling → carried forward / written off / retained (whichever was decided); effective date. This is the direct analogue of `MigrationController`'s preview step, and it's the point where a mistaken request gets caught before it's irreversible.
5. **Confirm & execute** — single atomic action; on success, show a summary (old enrollment closed, new enrollment created, balance carried, transfer logged).
6. **Audit/history view** — a per-student transfer history (trivial once `student_transfer` exists — just list rows for that student), and ideally a per-branch view ("students transferred out/in this term").
7. **Rollback path** — per Phase 7, "rollback" after execution should route back through the same wizard as a new reverse transfer rather than a silent undo, so the log stays truthful.
8. **Error handling** — since execution is a single transaction (Phase 2, step 9), a failure mid-execution should leave *nothing* committed rather than a half-applied state; the user sees a clear failure message and can retry, not a partially transferred student.

---

## Phase 10 — Recommended Solution

**Architecture:** hybrid close-old/open-new enrollment model (new `AcademicStudent` row for the destination branch, old row's status set to a new `"Transferred"` value), plus one new `student_transfer` table acting as both the transfer record and its own audit log. No existing entity (`FeeSubmission`, `Attendance`, `ExamResultSummary`, `Grievance`, `Student`) needs structural changes — their existing direct-FK design already isolates history from the transfer.

**Database changes required:** one new table (`student_transfer`, fields per Phase 5), one new `AcademicStudent.status` constant (`"Transferred"`), and FK/index additions scoped entirely to the new table. Everything else is additive, not corrective — nothing about the current schema needs to be undone or reworked.

**Business process:** the 10-step flow in Phase 2, with the exact set of mandatory vs. optional steps depending on the customer's answers to Phase 1 (approval requirements, balance policy, destination-acceptance requirement, notification requirements).

**Required vs. optional for a first version:** required — request capture, source-branch balance/grievance check, destination allocation, preview, atomic execution, transfer log, per-student history view. Optional/deferrable — multi-level approval chains, destination-branch explicit acceptance step, automated notifications, capacity/seat-limit enforcement (since no capacity concept exists today, this would be new scope beyond the transfer feature itself), and bulk/branch-closure transfers.

**Scalability considerations:** the design doesn't introduce any new query pattern that doesn't already exist elsewhere in the app (indexed lookups by student/school/status), so it should scale the same way the rest of the schema does. The one new table stays small relative to `academic_students`/`fee_submission` (one row per transfer event, not per transaction), so it's not a growth concern.

**Risks if done wrong:** the single biggest risk is skipping the "own direct `school`/`academicYear` FK per transactional record" discipline this system already follows — e.g., taking a shortcut where the new `AcademicStudent` row's history is queried by joining through the student's *current* enrollment rather than the FK stored on each historical row. That would silently corrupt every "as of a past date" report the moment a second transfer happens to the same student. The second risk is treating execution as multiple separate writes instead of one transaction — a partial transfer (old closed, new not yet created, or vice versa) would make the student vanish from both branches' active rosters simultaneously.

---

## Open items before this can move to a design/spec stage

The Phase 1 questions above are the actual blocker — in particular Q3 (approval chain), Q5/Q6 (balance policy), Q11 (whether capacity checking is in scope), Q12/Q13/Q14 (what stays vs. regenerates on the student's identity fields), and Q18 (reversal policy). Everything in Phases 2–10 is written to hold regardless of how those get answered, but the exact screen flow and table fields can't be finalized until they are.
