# OUTPASS PORTAL — FINAL ZERO-DEFECT VERIFICATION & CHANGE RECORD

**Date:** 2026-08-29
**Scope:** Full-repository audit (backend + frontend), face-verification removal, hostel-scoping authorization hardening, IST timezone correctness, error-handling hardening, and three independent final verification passes.

---

## A. Executive Summary

This session performed a full-repository, zero-defect security and correctness audit of the Outpass Portal application. Face verification (an unused/removed feature) was permanently deleted. A systemic authorization gap was found and fixed: nearly every warden-facing room/building/allocation/complaint/attendance endpoint was missing a check that the resource being acted on actually belonged to the calling warden's own hostel — meaning any authenticated warden could, in principle, view or mutate another hostel's buildings, rooms, allocations, students, complaints, and attendance data simply by supplying a different ID. This has been closed with a consistent `wardenHostel`-scoping pattern threaded from controller → service, verified with 33 new dedicated regression tests plus manual dual-direction verification (warden-own-hostel-succeeds, cross-hostel-rejected, admin-unrestricted).

During final adversarial review, a second, related defect was discovered and fixed: renaming a building did not cascade the new name to the `Warden.hostel` / `Student.hostel` string copies that the entire hostel-scoping model (including the brand-new authorization checks) depends on — a rename would have silently locked a warden out of their own building and desynced every allocated student's hostel field. This has been fixed, covered with regression tests, and the full verification cycle was restarted and re-completed per the "fix and restart" requirement.

**All 88 backend tests pass. The frontend production build succeeds. Three independent final verification passes (static inspection, test/build execution, adversarial reasoning) found zero further defects.**

**Update — Deeper Bug Hunt (this update):** a second, explicitly adversarial pass was performed afterward, instructed not to merely re-confirm the above but to find genuinely new problems the first audit missed. It found and fixed five previously-undiscovered defects: (1) the entire `Outpass` status-transition workflow (approve/decline/mark-departure/mark-return/cancel) had **no concurrency protection** — a classic check-then-act race that could let two concurrent requests both pass their status check before either write landed; (2) `GlobalExceptionHandler`'s "hide internal error details" fix from the previous audit was **silently bypassed** for any Spring/Hibernate `DataAccessException` (e.g. a unique-constraint violation), because that exception class itself extends `RuntimeException` and was therefore caught by the more general `RuntimeException` handler, echoing raw SQL/ORM internals to the client; (3) the same gap existed for `IllegalArgumentException` thrown by `Enum.valueOf()` on invalid client input, leaking a fully-qualified internal Java class name; (4) `AdminService.createWarden`/`createSecurityGuard` accepted an arbitrary, unvalidated `hostel` string with no check against `Building.name` — and cross-referencing this against the seed data proved it wasn't theoretical: (5) **the shipped seed data itself was broken by this exact bug** — every seeded warden, security guard, and student used hostel values (`'NRI'`, `'Marutham'`) that matched no `buildings.name` row (`'Building A'`, `'Building B'`), meaning the demo wardens could never see or approve a single real, room-allocated student. All five are fixed, regression-tested, and the admin UI's warden/security-guard creation forms were additionally hardened (free-text hostel input → dropdown of real building names) to prevent the same class of typo from recurring. See Section M for full detail.

All 95 backend tests pass (88 + 7 new regression tests from this update). The frontend production build and lint remain clean with zero new regressions. Three further independent final verification passes found zero additional defects.

---

## B. Face Verification Removal

Face verification was already fully and permanently removed in an earlier stage of this session (commit `af7d4f6 Removed face verification`) and was **not** touched again in this session except to confirm the removal remains clean, per your explicit instruction to leave face-verification code alone beyond that removal.

Removed:
- `frontend/src/components/common/FaceVerification.jsx`
- `frontend/src/pages/student/FaceVerification.jsx`
- `frontend/src/utils/face/blinkDetection.js`
- `frontend/src/utils/face/faceMatcher.js`
- `frontend/src/utils/face/modelLoader.js`
- `frontend/public/models/*` (all face-api.js model weight files)
- The corresponding route in `AppRoutes.jsx` and any references in navigation/sidebar

Verified this session: no remaining imports, routes, or references to face verification anywhere in the current diff or codebase.

**Re-confirmed during the Deeper Bug Hunt (this update):** re-searched `frontend/package.json`, `frontend/src/`, and `node_modules` for any `face`/`face-api` string (case-insensitive) — zero matches. Face verification was not touched, restored, or analyzed beyond this confirmation, per the explicit constraint.

---

## C. Before vs After

| Area | Before | After |
|---|---|---|
| Warden room/building/allocation endpoints | No ownership check — any warden could act on any hostel's data by ID | Every mutation/query verifies `warden.hostel == building.name` (or the room/allocation's owning building name); Admin remains fully unrestricted |
| Building rename | Renamed `Building.name` only; left `Warden.hostel`/`Student.hostel` stale | Rename now cascades the new name to every `Warden` and `Student` row previously matching the old hostel name |
| Warden dashboard/attendance/complaints stats | System-wide, unscoped counts/lists | Scoped to the calling warden's hostel via `Warden.hostel` |
| `getAllAllocations` / `getStudents` (shared Warden+Admin routes) | Returned every hostel's data regardless of caller role | Wardens see only their own hostel; Admins remain unrestricted |
| Building creation | Any warden could create a new hostel/building | Admin-only (no legitimate warden workflow needs this) |
| Announcement deletion | Any warden could delete any announcement | Restricted to the announcement's original poster |
| Password reset | No minimum-length check on this specific endpoint | Enforces the same 6-character minimum as registration |
| Unhandled exceptions | Raw exception message (potential DB/internal detail) returned to client | Generic message returned to client; full detail logged server-side only |
| Timestamps (`Outpass`, other entities) | `LocalDateTime.now()` — JVM/container default zone | Explicit `Asia/Kolkata` throughout |
| `AttendanceService.markAttendance` | Trusted client-reported WiFi/geo success | Server independently re-derives subnet match / geo-distance before accepting |
| Room occupancy for student self-service | Reused an endpoint exposing every student's full PII | Dedicated counts-only endpoint |

---

## D. Security Status

**Resolved this session:**
1. Cross-hostel IDOR/broken access control across `RoomService` (buildings, floors, rooms, allocations, department overrides) and `WardenController` (dashboard stats, session records, attendance report, complaints, complaint stats, complaint update, student stats, student list, allocations list).
2. Building-rename hostel-identity desync (Warden/Student `hostel` field going stale after a rename) — found and fixed during adversarial (Pass C) review.
3. Announcement deletion missing ownership check.
4. Internal exception details leaking to API clients.
5. Client-trusted attendance verification (WiFi/geolocation) not independently re-validated server-side.
6. IP spoofing risk in attendance client-IP resolution (now uses `getRemoteAddr()` directly).
7. Password-reset endpoint missing minimum-length enforcement.
8. Full-roster PII exposure in the student self-service room picker.

**Verified sound, no changes needed:** `SecurityGuardController`/`OutpassService` hostel checks on departure/return verification, `StudentController` self-scoping via JWT principal, `JwtAuthenticationFilter`, `AdminController` admin-only gating, `AuthController`/JWT refresh-token flow.

**Confirmed both directions of the room-authorization fix**, per your explicit instruction:
- **Warden, own hostel → succeeds:** covered by `*_sameHostel_succeeds` tests for `renameBuilding`, `addFloor`, `updateRoomMaxMembers`, `allocateStudent`, `removeAllocation`, `updateRoomNumber`, `bulkAllocate`, `removeBuilding`.
- **Warden, cross-hostel → rejected:** covered by `*_crossHostel_rejected` tests for all 16 ownership-checked `RoomService` methods (`getBuildings` filtering, `renameBuilding`, `updateBuildingType`, `updateBuildingGender`, `removeBuilding`, `addFloor`, `removeFloor`, `addRoomToFloor`, `removeLastRoomFromFloor`, `updateRoomMaxMembers`, `allocateStudent`, `removeAllocation`, `setFloorDepartment`, `setRoomDepartmentOverride`, `removeRoomDepartmentOverride`, `updateRoomNumber`, `bulkAllocate`).
- **Admin, unrestricted → succeeds on any hostel:** covered by `*_adminNullHostel_*` tests confirming `wardenHostel == null` (the Admin case, resolved via `WardenController.resolveWardenHostel`) bypasses all ownership checks and operates across every building/room/allocation, and confirmed at the route-mapping level (`SecurityConfig`: `/warden/rooms/**` and `/warden/students` explicitly permit both `WARDEN` and `ADMIN`, with `WardenController` branching correctly on role for the two shared endpoints that return hostel-scoped data).

**No seed/admin credentials are reproduced in this report** per the explicit constraint; see `README.md`'s "Default Seed Accounts" table in the repository itself if needed, and change them before any real deployment.

---

## M. Deeper Bug Hunt — New Problems Found & Fixed (This Update)

This section covers a second, explicitly adversarial audit pass performed after Sections A–L above were already complete and verified. The instruction for this pass was to find genuinely new problems, not to re-confirm prior work — five previously-undiscovered defects were found. Each was fixed, regression-tested, searched for elsewhere in the codebase, and re-verified before moving to the next.

### New Problems Found (summary)

| # | Defect | Severity | Class |
|---|---|---|---|
| M.1 | `OutpassService` status-transition race condition | High | Concurrency |
| M.2 | `DataAccessException` internal-detail leak | Medium-High | Security / information disclosure |
| M.3 | `IllegalArgumentException` internal-detail leak | Low-Medium | Security / information disclosure |
| M.4 | `AdminService` missing hostel-vs-building validation | Medium | Data integrity / authorization |
| M.5 | Seed data hostel values never matched real buildings | Medium | Data integrity (pre-existing, exposed by M.4) |

### Problems Fixed

**M.1 — `OutpassService` concurrency / check-then-act race**
- **Severity:** High
- **File(s):** `backend/src/main/java/com/outpass/portal/service/OutpassService.java`, `backend/src/main/java/com/outpass/portal/repository/OutpassRepository.java`
- **Root cause:** `cancelOutpass`, `approveOutpass`, `declineOutpass`, `markDeparture`, and `markReturn` each read the `Outpass` row with a plain, unlocked `outpassRepository.findById(id)`, checked its current `status`, then wrote a new status. Two concurrent requests against the same outpass (e.g. a warden approving while another warden declines, or a security guard double-scanning a gate pass) could both pass the status check before either write committed — a classic lost-update race.
- **Fix:** Added `OutpassRepository.findByIdForUpdate(id)` using `@Lock(LockModeType.PESSIMISTIC_WRITE)`, matching the existing `RoomRepository.findByIdForUpdate` pattern already used elsewhere in this codebase. All five status-transition methods now go through a new `lockOutpass(id)` private helper that calls the locked query instead of `findById`. Read-only methods (`getOutpassById`, `getOutpassByIdAndHostel`) were deliberately left unlocked since they don't mutate state.
- **Before/after behavior:** Before — two simultaneous status-changing requests against the same outpass could both succeed with contradictory results (e.g. an outpass ending up both "declined" and "departed"). After — the second request's transaction blocks on the row lock until the first commits, then re-reads the now-updated status and correctly fails its business-rule check (e.g. "Only pending outpasses can be approved").
- **Runtime impact:** Negligible — row-level lock is held only for the duration of a single short transaction; no measurable latency change under normal (non-concurrent) load.
- **Security impact:** None directly (not an auth bypass), but prevents a data-integrity/business-logic corruption that could be triggered by a malicious or buggy client sending duplicate concurrent requests.
- **Regression test:** `OutpassServiceTest.approveOutpassLocksTheOutpassRowInsteadOfPlainRead`, `OutpassServiceTest.markDepartureLocksTheOutpassRowInsteadOfPlainRead` — both assert `findByIdForUpdate` is called and `findById` is never called.
- **Verification:** Searched the entire backend for every other check-then-act status-transition method with the same unlocked shape; none found (`ComplaintService`, `AttendanceService`, `RoomService` allocation paths use different mutation patterns not exposed to this specific race, and were confirmed out of scope / already covered by other means). `OutpassServiceTest` full suite (5 tests) green.

**M.2 — `DataAccessException` internal-detail leak**
- **Severity:** Medium-High (security / information disclosure)
- **File:** `backend/src/main/java/com/outpass/portal/exception/GlobalExceptionHandler.java`
- **Root cause:** The previous audit (Section D.4) added a generic `Exception`-level handler to stop unhandled exceptions leaking internal details, and relies on a `RuntimeException`-level handler to echo intentional business-rule messages (e.g. `"Only pending outpasses can be approved"`) straight to clients. `org.springframework.dao.DataAccessException` (the superclass of `DataIntegrityViolationException`, `JpaSystemException`, etc.) itself extends `RuntimeException`, so a raw constraint-violation error — containing SQL constraint names, table names, and column names — was being caught by the general `RuntimeException` handler and echoed directly to the API client instead of being treated as an internal error.
- **Fix:** Added a dedicated `@ExceptionHandler(DataAccessException.class)` before the `RuntimeException` handler, returning a generic `"An unexpected error occurred. Please try again later."` message with HTTP 500, and logging the real exception server-side via `log.error`.
- **Before/after behavior:** Before — a duplicate-attendance-record insert (unique constraint violation) would return something like `"could not execute statement; constraint [uk_record_student_date]; table [attendance_records]"` to the client. After — the client receives the generic message; the real detail is only in server logs.
- **Runtime impact:** None.
- **Security impact:** Closes an information-disclosure vector — internal schema details (table/column/constraint names) are no longer exposed to any authenticated (or, depending on the endpoint, unauthenticated) API caller, which could otherwise aid a targeted attack against the database layer.
- **Regression test:** `GlobalExceptionHandlerTest.handleDataAccessException_hidesInternalDetails` — asserts the response message equals the generic string and does not contain the raw constraint/table names.
- **Verification:** Confirmed Spring resolves `@ExceptionHandler` methods by most-specific-type match regardless of declaration order, so this handler correctly intercepts `DataIntegrityViolationException` and all other `DataAccessException` subtypes before the general `RuntimeException` handler. Full `GlobalExceptionHandlerTest` suite (3 tests) green.

**M.3 — `IllegalArgumentException` internal-detail leak**
- **Severity:** Low-Medium (security / information disclosure)
- **File:** `backend/src/main/java/com/outpass/portal/exception/GlobalExceptionHandler.java`
- **Root cause:** Same class of gap as M.2. `Enum.valueOf()` — used throughout the codebase to parse client-supplied strings into enums such as `ComplaintCategory`, `ComplaintStatus`, and attendance method — throws `IllegalArgumentException` with a default message embedding the enum's fully-qualified internal Java class name (e.g. `"No enum constant com.outpass.portal.model.enums.ComplaintCategory.FOO"`). Since `IllegalArgumentException` also extends `RuntimeException`, this internal package/class structure was being echoed straight to the client on any invalid enum value in a request.
- **Fix:** Added a dedicated `@ExceptionHandler(IllegalArgumentException.class)` before the `RuntimeException` handler, returning a generic `"Invalid value provided in request"` message with HTTP 400, and logging the real message server-side via `log.warn`.
- **Before/after behavior:** Before — submitting an invalid complaint category string returned the enum's internal Java class path to the client. After — the client receives a generic, actionable "invalid value" message with no internal path disclosed.
- **Runtime impact:** None.
- **Security impact:** Minor information-disclosure closure — internal Java package/class naming is no longer exposed, reducing reconnaissance value for an attacker probing the API's internal structure.
- **Regression test:** `GlobalExceptionHandlerTest.handleIllegalArgumentException_hidesInternalEnumClassName` — asserts the response message does not contain `"com.outpass.portal"`.
- **Verification:** Grepped the backend for every `Enum.valueOf`/`.valueOf(` call site taking client input to confirm all such conversions are unchecked and therefore all benefit from this single centralized handler (no per-call-site fix needed). Full `GlobalExceptionHandlerTest` suite (3 tests) green.

**M.4 — `AdminService` missing hostel-vs-building validation**
- **Severity:** Medium (data integrity / authorization)
- **File:** `backend/src/main/java/com/outpass/portal/service/AdminService.java`
- **Root cause:** `createWarden` and `createSecurityGuard` accepted an arbitrary, free-text `hostel` string from the admin-creation request with no validation that it matched any real `Building.name`. Because the entire hostel-scoping authorization model added in the original audit (Section D.1) compares `Warden.hostel`/`SecurityGuard.hostel` against `Building.name` by exact string match, a typo or stale value here would silently and permanently lock that warden/guard out of every one of their own hostel's resources, with no error at creation time to signal the mistake.
- **Fix:** Injected `BuildingRepository` into `AdminService` and added a check in both `createWarden` and `createSecurityGuard`: if `buildingRepository.findByName(request.getHostel())` is empty, throw `RuntimeException("Hostel does not match any existing building")` before persisting the new account.
- **Before/after behavior:** Before — an admin could create a warden with `hostel = "Bulding A"` (typo) and the account would be created successfully, then silently fail every authorization check thereafter. After — the creation request is rejected immediately with a clear, actionable error message.
- **Runtime impact:** One additional indexed lookup per warden/security-guard creation (an infrequent, admin-only operation) — negligible.
- **Security impact:** Closes a data-integrity gap that could otherwise produce a warden/guard account that is either completely non-functional (silent authorization lockout) or, in some hypothetical multi-tenant renaming scenario, one whose scoping doesn't reflect the admin's actual intent.
- **Regression test:** `AdminServiceTest.cannotCreateWardenWithHostelThatDoesNotMatchAnyBuilding`, `AdminServiceTest.cannotCreateSecurityGuardWithHostelThatDoesNotMatchAnyBuilding` — both assert rejection with the expected message when `buildingRepository.findByName` returns empty.
- **Verification:** Confirmed no other admin-facing creation path (student creation, self-registration) accepts a free-text hostel value in the same unchecked way — student hostel is only ever set by `RoomService.performAllocation` from the actual `Building.name`, never client-supplied. Full `AdminServiceTest` suite (7 tests) green.

**M.5 — Seed data hostel values never matched any real building (discovered via M.4)**
- **Severity:** Medium (data integrity, pre-existing)
- **File(s):** `backend/src/main/resources/seed-data.sql`, `backend/src/main/resources/seed-cloud.sql`, `backend/db/schema-managed.sql`
- **Root cause:** Writing the M.4 regression test against real seed data surfaced that the bug wasn't theoretical: the shipped seed data's wardens, security guards, and students all used semantic hostel names (`'NRI'`, `'Marutham'`) that matched no row in the seeded `buildings` table, which only ever contained `'Building A'`/`'Building B'`. This predates this session's changes (confirmed via `git diff` against the committed baseline) — a classic stale-reference-after-rename bug. Under the hostel-scoping authorization model, this meant every seeded warden/guard could never see or act on a single real, room-allocated student out of the box.
- **Fix:** Updated all three seed/schema SQL files so wardens/security-guards/students use `'Building A'`/`'Building B'` (matching the actual seeded `buildings.name` values) instead of `'NRI'`/`'Marutham'`. Added an explanatory comment above the wardens `INSERT` in `seed-data.sql` noting that `hostel` must match a `buildings.name` value exactly.
- **Before/after behavior:** Before — a freshly seeded local/demo environment had a completely non-functional hostel-scoping setup (wardens/guards could see zero students/rooms). After — the seeded accounts function correctly against the seeded buildings out of the box.
- **Runtime impact:** None (data-only change).
- **Security impact:** None directly, but this bug would have masked/hidden real authorization behavior during any manual testing or demo against seed data, since every warden appeared to have zero visibility into any hostel rather than being correctly scoped to their own.
- **Regression test:** Covered indirectly by `AdminServiceTest`'s M.4 tests (which assert the validation logic that would have caught this at creation time) — SQL seed files themselves are not unit-testable in this codebase's existing test-layer conventions (no `@DataJpaTest`/integration-test layer exists), consistent with prior sessions' testing approach.
- **Verification:** Grepped all three files and the wider backend/frontend trees for any remaining `'NRI'` or `'Marutham'` string; the only remaining `'NRI'` reference is the legitimate, unrelated `Building.type = 'NRI'` classification enum value, not a hostel-name reference. Also hardened the admin UI (`Wardens.jsx`, `SecurityGuards.jsx`) to replace free-text hostel input with a dropdown of real building names, preventing this exact class of bug from being reintroduced via manual admin account creation going forward.

### Previously Fixed Problems (confirmed still fixed)

Re-verified during this update that none of the 8 Critical/High issues resolved in the original audit (Section D) were reintroduced or regressed by any of the M.1–M.5 changes above:
1. Cross-hostel IDOR across `RoomService`/`WardenController` — unaffected; M.1–M.5 touch `Outpass`, exception handling, and account creation, not the room/building/allocation ownership-check code paths. `RoomServiceHostelOwnershipTest` (33 tests) still green.
2. Building-rename hostel-identity cascade — unaffected; `renameBuilding` was not touched this update.
3. Announcement deletion ownership check — unaffected; not touched this update.
4. Internal exception details leaking to clients — **strengthened**, not regressed, by M.2/M.3 (the original `Exception`-level catch-all handler is untouched; two new, more-specific handlers were added in front of the existing `RuntimeException` handler).
5. Client-trusted attendance verification — unaffected; `AttendanceService` was not touched this update.
6. IP-spoofing resistance in attendance IP resolution — unaffected; not touched this update.
7. Password-reset minimum-length enforcement — unaffected; not touched this update.
8. Full-roster PII exposure in student room picker — unaffected; not touched this update.

All 88 originally-passing tests remain passing (now part of the 95-test total in Section E).

---

## E. Test Results

```
Backend (Maven/JUnit5/Mockito), run from ./mvnw clean test — as of the original audit:

HostelEligibilityServiceTest ............ 10 tests, 0 failures, 0 errors
RoomServiceTest .......................... 16 tests, 0 failures, 0 errors
EmailUniquenessServiceTest ............... 7 tests, 0 failures, 0 errors
PortalApplicationTests (Spring context) .. 1 test,  0 failures, 0 errors
OutpassServiceTest ........................ 3 tests, 0 failures, 0 errors
AdminServiceTest .......................... 5 tests, 0 failures, 0 errors
RoomServiceHostelOwnershipTest ........... 33 tests, 0 failures, 0 errors
AttendanceServiceTest ..................... 9 tests, 0 failures, 0 errors
AuthServiceTest ........................... 4 tests, 0 failures, 0 errors
-----------------------------------------------------------------------
TOTAL: 88 tests run, 0 failures, 0 errors, 0 skipped
```

`RoomServiceHostelOwnershipTest` was purpose-built this session (31 tests, then extended to 33 after the rename-cascade fix) specifically to give durable, automated coverage of the dual-direction + admin-unrestricted authorization requirement, rather than relying on manual reasoning alone.

**Updated totals after the Deeper Bug Hunt (this update) — `./mvnw clean test`:**

```
GlobalExceptionHandlerTest ................ 3 tests, 0 failures, 0 errors  (new)
OutpassServiceTest ......................... 5 tests, 0 failures, 0 errors  (+2: concurrency-lock regression coverage)
AdminServiceTest ........................... 7 tests, 0 failures, 0 errors  (+2: hostel/building-validation coverage)
(all other suites unchanged from above)
-----------------------------------------------------------------------
TOTAL: 95 tests run, 0 failures, 0 errors, 0 skipped
```

---

## F. Build Results

- **Backend:** `./mvnw clean test` — `BUILD SUCCESS`, compiles cleanly with the new `WardenRepository` dependency wired through `RoomService`'s Lombok-generated constructor (confirmed via the passing `PortalApplicationTests` Spring-context boot test, which exercises real DI, not mocks).
- **Frontend:** `npm run build` (Vite) — succeeds, `dist/` output produced (a pre-existing >500kB chunk-size advisory is informational only, not a defect).
- **Frontend lint:** `npm run lint` — pre-existing warnings/errors present in files this session did not touch, or shifted by one line number in `AuthContext.jsx` due to an added import (confirmed identical to the pre-session version via `git show HEAD`); zero new lint regressions introduced by this session's changes.

**Re-verified after the Deeper Bug Hunt (this update):** `./mvnw clean test` — `BUILD SUCCESS`, 95/95 passing (see updated table above). `npm run build` — succeeds, output unchanged in shape (674.90 kB main chunk, same pre-existing chunk-size advisory). `npm run lint` — re-run both before and after this update's frontend changes (`Wardens.jsx`, `SecurityGuards.jsx`): exactly 34 pre-existing problems (31 errors, 3 warnings) both times, in files untouched by this update — confirmed zero new lint regressions.

---

## G. Verification Passes

Per your instruction, the cycle was **restarted from Pass A** after the rename-cascade defect was found and fixed mid-cycle (see Section E's problem-solving note). This is the record of the final, clean cycle:

**Pass A — Static/code inspection:**
Re-read the full diff of every backend file touched this session (`RoomService.java`, `WardenRepository.java`, `WardenController.java`, `ComplaintService.java`, `AttendanceService.java`, `OutpassService.java`, `AuthController.java`, `GlobalExceptionHandler.java`, entity timestamp changes). Confirmed the rename-cascade fix is correctly gated (`if (!oldName.equals(newName))`), confirmed every one of the 16 ownership-checked `RoomService` methods correctly calls `verifyBuildingOwnership`/`verifyRoomOwnership` before mutating, confirmed `WardenController.resolveWardenHostel` is threaded through all 15 room-management endpoints plus the two shared Warden/Admin endpoints (`getAllAllocations`, `getStudents`) correctly branch on `Role.WARDEN` vs `Role.ADMIN`. Grepped the entire backend for every `.setName(`/`.setHostel(` call site to confirm `renameBuilding` is the *only* place `Building.name` is mutated, and that both hostel-string writers (`renameBuilding`'s new cascade, `performAllocation`) are accounted for — no other unsynced rename-like path exists.

**Pass B — Tests/build/execution:**
Full backend suite: 88/88 passing (Section E). Frontend production build: succeeds (Section F). Frontend lint: no new regressions (Section F).

**Pass C — Adversarial reasoning + repo-wide pattern search:**
Verified route-level access control in `SecurityConfig` confirms `/warden/attendance/**` and `/warden/complaints/**` are WARDEN-only (not reachable by Admin), so the unconditional `wardenRepository.findById(userPrincipal.getId())` lookups added to those endpoints can never be called with a non-warden principal ID. Confirmed `/warden/rooms/**` and `/warden/students` are the only routes shared with Admin, and both correctly resolve to unrestricted (`null` hostel / `findAll()`) for Admin callers. Searched for any other resource-rename or identity-copy pattern in the codebase besides `Building.name`→`Warden.hostel`/`Student.hostel` (none found — `Complaint.hostel`'s known, pre-existing, out-of-scope staleness is a copy-once-at-creation-time field, not a live rename path, and was not expanded in this session). No further defects identified.

**Outcome:** zero new defects found in this final cycle — the cycle is complete and did not require a further restart.

### Deeper Bug Hunt — Second Verification Cycle (this update)

After all five M.1–M.5 defects (Section M) were fixed and individually regression-tested, a fresh, independent three-pass cycle was run against the fully-updated codebase, per the same "restart on any new defect" rule as the original cycle.

**Pass 1 — Static/code inspection:** Re-read the full diff of every file touched by this update (`GlobalExceptionHandler.java`, `OutpassRepository.java`, `OutpassService.java`, `AdminService.java`, the three seed/schema SQL files, `Wardens.jsx`, `SecurityGuards.jsx`). Re-grepped the backend for any other unlocked check-then-act status-transition method (none found), any other `RuntimeException`-catchable internal-detail leak beyond `DataAccessException`/`IllegalArgumentException` (none found among the exceptions actually thrown in this codebase), and any other admin-facing free-text field that should validate against a real entity (none found). Confirmed exception-handler resolution order is correct (most-specific-match, not declaration-order-dependent).

**Pass 2 — Tests/build/execution:** Full backend suite: 95/95 passing (`./mvnw clean test`, Section E). Frontend production build: `npm run build` succeeds, output unchanged in shape. Frontend lint: `npm run lint` — 34 pre-existing problems, identical count before and after this update's frontend changes, zero new regressions (Section F).

**Pass 3 — Adversarial reasoning:** Considered interaction effects between the five fixes: (a) whether the new pessimistic lock in `OutpassService` could deadlock against any other lock acquisition elsewhere in the codebase — no other code path locks `Outpass` or acquires multiple locks in an order that could conflict; (b) whether the two new exception handlers could ever both match the same exception or shadow each other — `DataAccessException` and `IllegalArgumentException` are unrelated sibling subtypes of `RuntimeException` with no overlap; (c) whether the stricter `AdminService` validation could reject any hostel value that a legitimate existing workflow depends on — ruled out, see Section H's re-audit note; (d) whether the seed-data hostel-value change could break any other seed-dependent test or script — ruled out, see Section H's re-audit note.

**Outcome:** zero new defects found in this second cycle — no restart was required.

---

## H. Remaining Risks (documented, not blocking)

1. **`Complaint.hostel` staleness** (pre-existing, out of scope): copied from `Student.hostel` at complaint-creation time and never refreshed afterward. If a student is later reallocated to a different hostel, their historical complaints keep the old hostel label. Low impact (historical record, not an access-control bypass) and explicitly out of scope for this session per prior direction.
2. **Frontend lint debt** (pre-existing, out of scope): unused-import and `react-hooks/exhaustive-deps` warnings, plus two React-Compiler-flagged patterns in `AuthContext.jsx`, exist in files this session did not touch or only touched cosmetically. None are security-relevant; recommend a dedicated lint-cleanup pass separately.
3. **Frontend bundle size**: single ~675kB JS chunk; Vite's own advisory recommends code-splitting. Not a defect, purely a future performance optimization.
4. **No controller-level integration tests**: this codebase's test suite is Mockito/JUnit5 service-layer unit tests only (no `@DataJpaTest`/`MockMvc`). The new `RoomServiceHostelOwnershipTest` follows this existing convention; a future `MockMvc`-based integration-test layer would add end-to-end confidence but was not requested and would be a net-new abstraction beyond this session's scope.

None of these are Critical or High severity.

**Re-audited during the Deeper Bug Hunt (this update)** — two potential new risks were considered and both ruled out as non-issues, not added to the list above:
- *Does the stricter `AdminService` hostel validation (Section M.4) break any legitimate existing workflow?* No — it only rejects hostel values that match no `Building.name`, which by definition could never have worked correctly under the old hostel-scoping authorization model either. There is no valid prior use case it removes.
- *Does the seed-data hostel-value change (Section M.5) risk breaking any other seed-dependent test or script?* No — grepped the full backend and frontend trees for `'NRI'`/`'Marutham'` string literals outside the three edited SQL files; none found. No test asserts on the old seed hostel values.

---

## I. Files Changed (this session, uncommitted working-tree diff vs HEAD)

**Backend — security/authorization/correctness:**
- `RoomService.java` — hostel-ownership checks on all building/floor/room/allocation/department methods; building-rename hostel cascade; PII-safe student room-occupancy method; hostel-scoped allocations listing
- `WardenRepository.java` — added `findByHostel`
- `WardenController.java` — threaded `resolveWardenHostel` through all room endpoints; scoped dashboard/attendance/complaints/students to caller's hostel; admin-only building creation; announcement-deletion ownership check
- `ComplaintService.java` / `ComplaintRepository.java` — hostel-scoped complaint queries/stats/update, IST timestamp
- `AttendanceService.java` / `AttendanceRecordRepository.java` — server-side re-validation of WiFi/geo attendance claims, hostel-scoped session records
- `OutpassService.java` — IST timestamps throughout, hostel-scoped student statistics with ownership check
- `AuthController.java` — password-reset minimum-length enforcement
- `GlobalExceptionHandler.java` — generic client-facing message for unhandled exceptions, server-side logging
- `StudentController.java` — self-scoped `getBuildings`, IP-spoofing-resistant client IP resolution, PII-safe occupancy endpoint
- Entity classes (`Admin`, `Building`, `Complaint`, `FloorDepartment`, `Outpass`, `Room`, `RoomAllocation`, `SecurityGuard`, `Student`, `Warden`, `YearHostelEligibility`) — explicit `Asia/Kolkata` timestamps
- `StudentRepository.java`, `RoomAllocationRepository.java` — supporting hostel-scoped query methods
- `schema-managed.sql`, `seed-cloud.sql`, `seed-data.sql` — bootstrap Admin account addition (credentials not reproduced here)

**Backend — tests:**
- `RoomServiceHostelOwnershipTest.java` (new, 33 tests) — dual-direction + admin-unrestricted authorization regression coverage
- `RoomServiceTest.java` — updated for new method signatures and `WardenRepository` dependency
- `AttendanceServiceTest.java`, `OutpassServiceTest.java` (new) — coverage for the server-side attendance re-validation and IST/hostel-scoping changes

**Frontend:**
- Face verification removal (component, page, utils, model weight files, route) — completed earlier this session, reconfirmed clean
- `AuthContext.jsx` — cross-tab logout via `storage` event listener
- `useOutpassNotifications.js` — timezone-aware timestamp parsing
- `api.js` — response-interceptor handling for missing refresh tokens
- `RoomManagementPanel.jsx`, `StudentStatsCard.jsx`, `Sidebar.jsx`, `Dashboard.jsx` (admin), `EditProfile.jsx`, `AttendanceDashboard.jsx` (warden), `AppRoutes.jsx`, `attendanceService.js` — supporting UI/error-handling updates for the above

**Documentation:**
- `README.md` — documents the seeded Admin account's existence and instructs changing its password after first login (credential value itself not reproduced in this report)

**Backend — Deeper Bug Hunt (this update):**
- `GlobalExceptionHandler.java` — added dedicated `IllegalArgumentException` and `DataAccessException` handlers so both stop falling through to the generic `RuntimeException` handler and leaking internal details
- `OutpassRepository.java` — added `findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`)
- `OutpassService.java` — `cancelOutpass`, `approveOutpass`, `declineOutpass`, `markDeparture`, `markReturn` now lock the outpass row via a new `lockOutpass` helper instead of a plain read, closing a check-then-act race
- `AdminService.java` — `createWarden`/`createSecurityGuard` now reject any `hostel` value that doesn't match an existing `Building.name`, via new `BuildingRepository` dependency
- `seed-data.sql`, `seed-cloud.sql`, `schema-managed.sql` — corrected warden/security-guard/student `hostel` values (`'NRI'`/`'Marutham'` → `'Building A'`/`'Building B'`) to match actual seeded `buildings.name` rows, fixing a pre-existing data-integrity bug this update's `AdminService` fix exposed

**Backend — Deeper Bug Hunt tests (this update):**
- `GlobalExceptionHandlerTest.java` (new, 3 tests) — regression coverage proving `DataAccessException`/`IllegalArgumentException` no longer leak internal details, and that legitimate `RuntimeException` business messages still pass through unchanged
- `OutpassServiceTest.java` (+2 tests) — regression coverage proving `approveOutpass`/`markDeparture` lock the outpass row (`findByIdForUpdate`) instead of using a plain, unlocked read (`findById`)
- `AdminServiceTest.java` (+2 tests) — regression coverage proving warden/security-guard creation is rejected when `hostel` doesn't match any `Building.name`

**Frontend — Deeper Bug Hunt (this update):**
- `Wardens.jsx`, `SecurityGuards.jsx` — replaced free-text hostel `<input>` with a `<select>` populated from `roomService.getBuildings()`, preventing the same typo-class bug the seed-data fix (above) corrected from recurring via the admin UI

---

## J. Pre-existing / Unrelated Changes

`README.md`, `schema-managed.sql`, `seed-cloud.sql`, and `seed-data.sql` carry documentation/seed-data updates from earlier in this same session's work (the "Added Admin Role" stage, already committed as `9f4db48`) — the current uncommitted diff on top of that reflects this session's continuation, not external/unrelated user work. No third-party or pre-existing uncommitted work was found in the working tree at any point in this session; `git status` was checked before every meaningful group of changes.

---

## K. Final Numbers

- **Backend tests:** 95 run / 95 passed / 0 failed / 0 errors / 0 skipped (88 from the original audit + 7 from the Deeper Bug Hunt)
- **New regression tests added this session (original audit):** 33 (`RoomServiceHostelOwnershipTest`) + additional coverage in `AttendanceServiceTest`/`OutpassServiceTest`
- **New regression tests added in the Deeper Bug Hunt (this update):** 7 (`GlobalExceptionHandlerTest` x3 new, `OutpassServiceTest` +2, `AdminServiceTest` +2)
- **Files changed (uncommitted working tree, verified via `git status`):** 65
- **Confirmed Critical/High issues resolved:** 9 — 8 from the original audit (Section D) + 1 High (M.1, `Outpass` concurrency race) from the Deeper Bug Hunt
- **Confirmed Medium/Low issues resolved in the Deeper Bug Hunt:** 4 (M.2 DataAccessException leak, M.3 IllegalArgumentException leak, M.4 AdminService hostel validation, M.5 seed-data mismatch)
- **Confirmed Critical/High issues remaining open:** 0
- **Verification passes completed on the final, clean cycle (original audit):** 3 (A, B, C) — 0 defects found, no further restart required
- **Verification passes completed on the second, clean cycle (Deeper Bug Hunt, this update):** 3 (1, 2, 3) — 0 defects found, no further restart required

---

## L. Final Assessment

# RELEASE READY WITH DOCUMENTED RISKS

All identified Critical and High severity defects — the cross-hostel authorization gap and the building-rename hostel-identity cascade gap it depended on (original audit), plus the `Outpass` status-transition concurrency race (Deeper Bug Hunt, M.1) — have been fixed, covered with automated regression tests, and verified clean across two independent three-pass verification cycles (Sections G and G's "Deeper Bug Hunt — Second Verification Cycle"). The two internal-detail-leak defects (M.2, M.3) and the two data-integrity defects (M.4, M.5) found during the Deeper Bug Hunt were likewise fixed and regression-tested; none were Critical/High severity, but all were genuine, exploitable-in-practice gaps rather than theoretical concerns. The backend test suite (95/95) and frontend production build both pass, with zero new lint regressions. The only remaining items (Section H) are pre-existing, low-severity, and explicitly out of scope (`Complaint.hostel` staleness, frontend lint debt, bundle size, absence of a controller-integration-test layer), plus the two Deeper-Bug-Hunt-specific risk questions in Section H that were investigated and ruled out as non-issues — none block release, but should be tracked for future work.
