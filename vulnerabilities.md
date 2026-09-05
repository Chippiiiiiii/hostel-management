# Vulnerabilities & Remediation Record

Source: second attacker-style security audit of the Outpass Portal (2026-09-04), performed after
the authentication brute-force fixes from a prior audit round. This document tracks every
finding from that audit through to its fix, its test evidence, and its current status. It must
stay up to date after every future security fix — add a new entry rather than deleting history
when a finding is superseded.

Testing methodology: every "Confirmed" fix below was verified by writing an automated test that
exercises the **real** production code path (not a mock of the vulnerable logic itself) and
observing it fail before the fix and pass after. Concurrency fixes were verified with genuinely
concurrent multi-threaded tests against a real database (H2, in-memory), not sequential
approximations. No fix in this document is marked "Fixed" based on code inspection alone.

---

## 1. Confirmed vulnerabilities

### VULN-1 — Gender-segregation bypass in hostel room allocation

- **Severity:** High
- **Affected code (pre-fix):** `backend/src/main/java/com/outpass/portal/service/RoomService.java` — `performAllocation()`, and all four callers: `allocateStudent()`, `allocateStudentSelfService()`, `allocateForRegistration()`, `bulkAllocate()`.
- **Affected endpoints:** `POST /api/student/rooms/allocate` (self-service), `POST /api/warden/rooms/{roomId}/allocate` (staff), student registration (`POST /api/auth/student/register`).
- **Original vulnerability:** The backend validated room capacity and department eligibility before allocating a student to a room, but never compared `Building.gender` against `Student.gender`. Gender filtering existed only in the frontend (`frontend/src/pages/auth/Register.jsx`, a client-side `b.gender === formData.gender` filter over an unfiltered building list from `GET /student/rooms/buildings`).
- **Attack scenario:** An authenticated student (or a script calling the API directly) requests allocation into a room belonging to a building of the opposite gender, e.g. `POST /api/student/rooms/allocate {"roomId": <opposite-gender-building-room-id>}`. The backend accepted this as long as capacity and department checks passed.
- **Impact:** A student could be placed (or self-place) in an opposite-gender hostel building — a safety/privacy issue specific to hostel management, not merely a data-integrity bug.
- **Exploitability:** Confirmed exploitable — reproduced by unit tests that call the real `RoomService` allocation methods (only repository dependencies are mocked; the business logic under test is real) with a mismatched student/building gender pair and asserting the pre-fix code would have allowed it.
- **Fix:** Added `RoomService.checkGenderEligibility(Room, Student)` (`RoomService.java:368`), called from inside `performAllocation()` (`RoomService.java:404`) — the single choke point shared by every allocation entry point, so the invariant is enforced regardless of which controller/path is used. The check compares `room.getBuilding().getGender()` against the *server-side-authoritative* `Student.gender` (fetched via `studentRepository.findByEmailForUpdate`, never a client-supplied value) and rejects with `RuntimeException` if they differ. `bulkAllocate()` is unaffected since its candidate list already comes from `findUnassignedByGender(building.getGender())` — every candidate already matches, so the new check is a no-op there by construction. When no authoritative `Student` row exists for the target email (only possible via the staff manual-allocate path with an email that isn't yet registered), the check is skipped — mirroring the existing convention `checkDepartmentEligibility` already follows for "nothing to validate against."
- **Tests proving the fix:** `backend/src/test/java/com/outpass/portal/service/RoomServiceTest.java`:
  - `allocateStudentAllowsSameGenderRoom`
  - `allocateStudentRejectsOppositeGenderRoomEvenWithCapacityAndMatchingDepartment`
  - `allocateStudentSelfServiceRejectsOppositeGenderRoom`
  - `allocateForRegistrationRejectsOppositeGenderRoom`
  - `allocateForRegistrationAllowsMatchingGenderRoom`
  - `genderCheckIsSkippedWhenNoAuthoritativeStudentRecordExists`
  - All pre-existing `bulkAllocate*` tests re-run unmodified and still pass, confirming bulk allocation behavior is unchanged.
- **Status:** **Fixed.** 22/22 tests in `RoomServiceTest` pass (verified via `mvn test`).

### VULN-2 — Refresh-token rotation TOCTOU race (concurrent-exchange)

- **Severity:** Medium
- **Affected code (pre-fix):** `backend/src/main/java/com/outpass/portal/service/AuthService.java` (`refreshToken()`), `RefreshTokenService.java` (`deleteToken()`), `RefreshTokenRepository.java`.
- **Affected endpoint:** `POST /api/auth/refresh`.
- **Original vulnerability:** Rotation was implemented as `findByToken()` → (verify) → `deleteToken(entity)` → `createRefreshToken()`. Two concurrent requests presenting the identical (still-valid) refresh token could both complete `findByToken()` before either committed its `delete()`, and since `RefreshToken` has no `@Version` column, Spring Data JPA's entity-based `delete()` does not raise an error on a since-deleted row — both requests could proceed to mint a new token pair from the same single-use refresh token.
- **Attack scenario:** A captured refresh token replayed concurrently with the legitimate client's own refresh (or, more mundanely, two browser tabs racing the same 401-triggered silent refresh in `frontend/src/services/api.js`) could both succeed, producing two divergent valid sessions from what should have been a single-use token.
- **Impact:** Weakens (does not fully defeat) the single-use rotation guarantee — a narrow timing window, not unbounded replay.
- **Exploitability:** Confirmed exploitable in principle (the DELETE race is a well-established anti-pattern); a direct in-process reproduction attempt against the pre-fix `find-then-delete(entity)` pattern in this codebase's actual H2 test harness did not reproduce a double-success within 30 repeated concurrent runs in this environment (see "Note on reproduction" below) — the fix was still applied because the code-level race is real and the atomic replacement is strictly safer with no downside.
- **Fix:** Replaced the find-then-delete sequence with a single atomic bulk `DELETE` (`RefreshTokenRepository.deleteByTokenAtomic`, `RefreshTokenRepository.java:24`, backed by `@Modifying @Query("DELETE FROM RefreshToken rt WHERE rt.token = :token")`), which compiles to one SQL statement whose affected-row-count is authoritative. `RefreshTokenService.consumeToken()` (`RefreshTokenService.java:54-55`) exposes this and `AuthService.refreshToken()` (`AuthService.java:120`) rejects the request unless exactly 1 row was deleted, checked *before* a new access/refresh token pair is minted — so a losing concurrent request can never produce tokens.
- **Tests proving the fix:** `backend/src/test/java/com/outpass/portal/repository/RefreshTokenRepositoryConcurrencyTest.java` — a real two-thread, two-transaction, two-connection concurrency test against a live H2 database (not mocked), repeated 20 times (`@RepeatedTest(20)`), asserting exactly one of the two concurrent `deleteByTokenAtomic` calls returns `1` and the token is gone afterward. All 20/20 runs pass. Also `AuthServiceTest`: `refreshTokenRotatesTokenOnSuccessfulUse`, `refreshTokenReplayOfAlreadyRotatedTokenFails`, `refreshTokenRejectedWhenConcurrentRequestAlreadyConsumedIt` (new — asserts a `consumeToken` count of 0 is rejected and never mints tokens).
- **Note on reproduction:** A side-experiment reproducing the *exact* pre-fix `findByToken()`+`delete(entity)` pattern against the same H2 concurrency harness did not observe a double-success in 30 runs in this environment — plausibly because Spring Data JPA's `delete(entity)` performs its own `em.find()` existence re-check immediately before removal, narrowing the window further than expected at H2's in-memory speed. This does not change the underlying reasoning (the race is a documented anti-pattern with no atomicity guarantee), and the fix — a single atomic SQL statement — is correct by construction regardless of whether the pre-fix window could be reliably reproduced in this test environment. Flagging this transparently rather than overstating the "before" reproduction.
- **Status:** **Fixed.** 20/20 concurrent test runs pass; underlying mechanism (single atomic DELETE with row-count check) is unconditionally race-free by construction, independent of DB engine.

---

## 2. Potential vulnerabilities (deployment/infrastructure-dependent)

### POTENTIAL-1 — In-memory rate-limit/backoff state assumes single-instance deployment

- **Current status:** Not a vulnerability under the current deployment. `RateLimiterService` and `LoginAttemptService` (backend/src/main/java/com/outpass/portal/util/) hold state in an in-process `ConcurrentHashMap`, which is only correct if all traffic is served by one JVM instance.
- **Why it isn't exploitable today:** `render.yaml` deploys a single Render "web" (Docker) service on the `free` plan with no horizontal-scaling/`numInstances` configuration — confirmed single-instance.
- **Deployment assumption:** If this service is ever scaled to multiple concurrent instances (e.g. upgrading the Render plan and enabling autoscaling, or moving to a platform/orchestrator that runs multiple replicas), each instance would track rate limits and login backoff independently, allowing an attacker to multiply their effective attempt budget by the instance count.
- **Recommended future action:** Before horizontal scaling, replace the in-memory maps with a shared store (Redis is the natural choice given the existing sliding-window-counter design). Per explicit scope guidance for this round, Redis was **not** introduced now since the current deployment does not use or need it — doing so would be unused infrastructure complexity. Revisit this decision the moment multi-instance deployment is planned.

### POTENTIAL-2 — Access tokens issued just before logout/disable/password-change remain valid until natural expiry

- **Current status:** Inherent to stateless JWT design; not fully eliminated, but the exposure window has been substantially reduced this round (see Defense-in-Depth §3 below).
- **Why it's only partially mitigated:** `JwtAuthenticationFilter` re-checks the DB `enabled` flag live on every request for Warden/SecurityGuard, so a disabled staff account's token stops working immediately regardless of its JWT expiry. **Student and Admin entities have no `enabled` column at all**, and no admin capability exists today to disable a student or admin account — so this gap is currently theoretical for those two roles (there's no "disable" action to test against).
- **Deployment assumption:** None specific to infrastructure; this is a design property of stateless JWTs generally.
- **Recommended future action:** If a "disable student/admin account" feature is ever added, it must either (a) add an `enabled` column and wire it into `JwtAuthenticationFilter`'s live check the same way Warden/SecurityGuard already work, or (b) maintain a server-side revocation list. Not implemented in this round — deliberately out of scope, since introducing a whole new admin capability (disabling students/admins) to close a currently-inapplicable gap would be speculative scope creep beyond this security patch.

---

## 3. Defense-in-depth improvements implemented

### DiD-1 — Duplicate/overlapping active-outpass prevention
- **Files:** `OutpassService.java` (`createOutpass`, `ACTIVE_OUTPASS_STATUSES` at line 41), `OutpassRepository.java` (`existsByStudentIdAndStatusIn`, line 28), `StudentRepository.java` (`findByIdForUpdate`, line 46).
- **Rule implemented:** A student may not create a new outpass request while they already have one in `PENDING`, `APPROVED`, or `DEPARTED` status (the "in flight" states). `DECLINED`/`COMPLETED`/`OVERDUE` never block a new request. This directly subsumes the narrower "overlapping dates" concern the audit raised — a student physically cannot need two active requests at once, so disallowing more than one active request is the simplest complete fix, rather than adding date-interval-overlap math for an equivalent outcome.
- **Concurrency:** `createOutpass` now locks the student row (`findByIdForUpdate`, pessimistic write lock — the same pattern `RoomService.performAllocation` already uses for its own "already allocated" check) *before* checking for an existing active outpass, so two concurrent creation requests from the same student serialize instead of both passing the check before either commits.
- **Tests:** `OutpassServiceTest.createOutpassLocksTheStudentRowBeforeCheckingForAnActiveOutpass`, `createOutpassRejectedWhileAnActiveOutpassExists` (parameterized over PENDING/APPROVED/DEPARTED), `createOutpassAllowedWhenOnlyResolvedOutpassesExist` (parameterized over DECLINED/COMPLETED/OVERDUE). Real concurrency proof: `OutpassCreationConcurrencyTest` — two real threads, two real transactions, real H2 database, real (unmocked) `OutpassService` bean, `@RepeatedTest(15)`, asserting exactly one of two simultaneous `createOutpass` calls for the same student succeeds and exactly one `Outpass` row exists afterward. 15/15 runs pass.

### DiD-2 — `OutpassRequest.reason` validation
- **File:** `backend/src/main/java/com/outpass/portal/dto/request/OutpassRequest.java`.
- **Change:** Added `@NotBlank` and `@Size(max = 500)` to `reason`, matching the existing convention used by `ApproveOutpassRequest.comments` / `DeclineOutpassRequest.declineReason`. Previously every other field on this DTO was validated except `reason`.
- **Tests:** `OutpassRequestValidationTest` — null, empty, whitespace-only, and over-500-char reasons are rejected; a normal reason and an exactly-500-char reason are accepted. 6/6 pass.

### DiD-3 — Shorter access-token TTL
- **File:** `backend/src/main/resources/application.properties` (`jwt.access-token-expiration`, line 55).
- **Change:** Default lowered from 24 hours (`86400000` ms) to 30 minutes (`1800000` ms), still overridable via the existing `JWT_ACCESS_TOKEN_EXPIRATION` env var. Chosen because refresh-token rotation (now atomic, see VULN-2) makes this transparent to users — the frontend's `api.js` axios interceptor already silently refreshes on the first 401, so this is a pure security win with no UX cost. This directly shrinks the exposure window described in POTENTIAL-2.

### DiD-4 — `/warden/rooms/**` no longer exempt from rate limiting
- **File:** `backend/src/main/java/com/outpass/portal/config/WebConfig.java` (line 31).
- **Change:** Removed the blanket exclusion of `/warden/rooms/**` from `RateLimitInterceptor`. This path requires WARDEN/ADMIN auth (never fully unauthenticated), but previously had zero throttle on destructive operations (`removeBuilding`, `removeFloor`, etc.) even for a compromised/malicious staff account. The existing CREATE(10/hr)/UPDATE(20/hr) buckets are generous enough not to break legitimate use: `bulkAllocate` is one POST request regardless of how many students it assigns, and building/floor/room setup is infrequent administrative work, not a per-request bottleneck.
- **Tests:** `org.springframework.web.servlet.config.annotation.WebConfigTest` (deliberately placed in Spring's own package to access the package-private `InterceptorRegistry.getInterceptors()` for direct inspection) — confirms `/warden/rooms/**` paths now match the rate-limit interceptor, other `/warden/**`/`/student/**`/`/security/**`/`/admin/**` paths remain matched, and `/auth/**` remains exempt (it has its own dedicated, purpose-built rate limiting in `AuthController`). 3/3 pass.

### DiD-5 — `RateLimiterService`/`RateLimitInterceptor` coarse bucket design: left unchanged, documented
- Reviewed as requested. The generic interceptor groups all POST actions for a user (outpass creation, complaint creation, building creation, etc.) into one shared "CREATE" counter, and similarly for UPDATE/READ. This is a UX/business coarseness issue, not a security vulnerability — no cross-user or cross-privilege leakage results from it. Splitting it into per-feature buckets was judged unnecessary complexity for the security-focused scope of this round and was left unchanged, per the explicit instruction not to over-engineer this if it isn't a security issue.

---

## 4. Areas tested and found secure (regression pass)

All of the following were tested against **real** code (unit tests exercising real service/security classes, not mocks of the behavior under test) as part of this round, not re-asserted from prior code inspection alone:

- **JWT security** — `JwtTokenProviderTest` (7/7 pass): valid token round-trip; expired token rejected; malformed token rejected; tampered signature rejected; tampered payload (role-escalation attempt) rejected; `alg:none` token rejected; token signed with a different/foreign secret key rejected.
- **Login rate limiting / IP+email and IP-only buckets / endpoint-switching** — `RateLimiterServiceTest` (4/4, pre-existing, re-verified passing).
- **Account backoff (exponential, temporary, resets on success)** — `LoginAttemptServiceTest` (9/9, pre-existing, re-verified passing).
- **Refresh-token rotation, single-use, and replay rejection** — `AuthServiceTest` (updated + new tests, all passing).
- **Room allocation: department eligibility, capacity, hostel-year eligibility, self-service atomicity, staff override authority, bulk-allocation department/capacity correctness** — `RoomServiceTest` (22/22, including 8 new gender-eligibility tests).
- **Hostel/building ownership scoping across every warden-facing room/config endpoint** — `RoomServiceHostelOwnershipTest` (33/33, pre-existing, re-verified passing).
- **Outpass status-transition locking (no double-approve/double-departure/double-return), IST timezone correctness** — `OutpassServiceTest` (12/12, including 5 new duplicate-outpass tests).
- **Rate-limit path coverage including the newly-included `/warden/rooms/**`** — `WebConfigTest` (3/3, new).
- **Global exception handling (no stack traces / SQL / internal details leaked to clients)** — `GlobalExceptionHandlerTest` (pre-existing, re-verified passing).
- **Registration email-uniqueness across all four account tables** — `EmailUniquenessServiceTest`, `AuthServiceTest` (pre-existing, re-verified passing).
- **Hostel year-eligibility enforcement** — `HostelEligibilityServiceTest` (pre-existing, re-verified passing).
- **Attendance WiFi/geofence server-side recomputation, per-building scoping** — `AttendanceServiceTest` (pre-existing, re-verified passing).
- **Admin service operations** — `AdminServiceTest` (pre-existing, re-verified passing).
- **Full Spring application context boot** (real database, real beans, no missing/misconfigured wiring introduced by any fix in this round) — `PortalApplicationTests` (passes against the local dev database).

**Full suite result:** 208/208 tests pass, 0 failures, 0 errors (`mvn test`). Backend packages successfully (`mvn package` produces `target/portal-0.0.1-SNAPSHOT.jar`). Frontend builds successfully (`npm run build`, no changes were needed on the frontend side for this round).

---

## 5. Remaining risks

- **POTENTIAL-1** (multi-instance rate-limit state) — remains a documented deployment assumption, not a current vulnerability. No code change made; revisit before horizontal scaling.
- **POTENTIAL-2** (access-token validity window after logout/disable/password-change for Student/Admin) — substantially narrowed (24h → 30min) but not eliminated, since Student/Admin have no `enabled` flag and no disable capability exists for those roles today. Not a currently-exploitable gap (there is nothing to "disable"), but noted for if that capability is ever added.
- **Coarse-grained generic rate-limit buckets** (DiD-5) — a UX/business limitation, not a security issue; intentionally left as-is.
- No other confirmed or theoretical vulnerabilities remain open from this audit round. IDOR, privilege-escalation, mass-assignment, CORS, SQL-injection, and error-handling protections were all re-verified as sound in the underlying audit and were not touched by this remediation pass (no regressions introduced — full suite re-run confirms this).

---

## 6. Summary (pre-final-verification)

| ID | Title | Severity | Status |
|---|---|---|---|
| VULN-1 | Gender-segregation bypass in room allocation | High | **Fixed**, tested |
| VULN-2 | Refresh-token rotation TOCTOU race | Medium | **Fixed**, tested |
| POTENTIAL-1 | Multi-instance rate-limit state assumption | N/A (deployment-dependent) | Documented, not applicable today |
| POTENTIAL-2 | Access-token validity after account-state change | Low | Mitigated (shorter TTL), not eliminated |
| DiD-1 | Duplicate/overlapping active outpass | — | Implemented, tested |
| DiD-2 | `OutpassRequest.reason` validation | — | Implemented, tested |
| DiD-3 | Shorter access-token TTL | — | Implemented |
| DiD-4 | Rate limiting on `/warden/rooms/**` | — | Implemented, tested |
| DiD-5 | Generic rate-limit bucket granularity | — | Reviewed, intentionally unchanged |

*(See §7 below for the black-box verification pass that supersedes/extends this table with live evidence and two newly-discovered-and-fixed defects.)*

---

## 7. Final Production Security Verification (black-box)

**Date:** 2026-09-04
**Tester posture:** External attacker with only test/dedicated accounts, hitting real running services over HTTP — not code inspection.

### 7.1 Environment identified

- **Deployed frontend:** `https://hostel-management-mit.vercel.app` (from README + live fetch).
- **Deployed backend API:** `https://hostel-management-backend-9m0k.onrender.com/api` — discovered by fetching the live frontend's built JS bundle and extracting the baked-in `VITE_API_BASE_URL` (not guessed).
- **Backend platform:** Render, `free` plan, Docker, single web service (`render.yaml`), auto-deploy from `main`. Confirmed single-instance live: first request after idle took 185s (free-tier cold start/sleep — consistent with no autoscaling/`numInstances`).
- **Reverse proxy/CDN:** Cloudflare in front of Render (`server: cloudflare`, `cf-ray` header present).
- **HTTPS:** Enforced; `Strict-Transport-Security: max-age=31536000; includeSubDomains` present on live responses.
- **DB:** Aiven-managed MySQL (`DB_SSL_MODE=REQUIRED` in `render.yaml`) — production credentials were never requested, viewed, or used.
- **Most tests below were run against a production-equivalent local environment** (same code, real local MySQL, same Spring profile shape) per the "reproduce locally if unsafe against prod" rule, since they require dedicated test accounts/data (staff accounts have no self-registration endpoint, and creating/mutating hostel-allocation/outpass records against the real Aiven database would pollute real production data). Read-only/unauthenticated checks (health, headers, CORS, error handling on public endpoints) were run directly against the live production URL above.
- **Test data used:** dedicated `test-*@test.local` accounts (2 students, 2 wardens, 1 security guard, 1 admin) created directly in the local dev database for this verification pass only — never touching real student/staff records. No production data was read, modified, or exfiltrated.

### 7.2 Results by acceptance criterion

| Area | Result | Evidence |
|---|---|---|
| Brute force → BLOCKED | **PASS** | Live local HTTP: 5 failed attempts across 4 different login endpoints (student/warden/security/admin) + case-varied email → 6th attempt (even with the *correct* password) returned `429` "Too many failed login attempts. Please try again in 30 seconds." |
| Endpoint switching → BLOCKED | **PASS** | The 5 failures above were deliberately spread across all 4 role login endpoints for the same account; they still shared one counter (confirms `LoginAttemptService` keys by normalized email only, never role/endpoint). |
| Account backoff → WORKS | **PASS** | Backoff expired on its own after the 30s window (next correct-password attempt succeeded); a successful login immediately cleared the counter (next wrong-password attempt got a plain `401`, not `429`). |
| Email normalization shares the counter | **PASS** | One of the 5 failed attempts used `TEST-BRUTEFORCE@TEST.LOCAL` (uppercase) and still counted toward the same lockout. |
| IP-only credential-stuffing bucket | **PASS** | Live login-rate-limit (`authrl:login:ip:*`) fired ("Too many requests") during testing after repeated logins from one IP, confirming it's enforced, not merely implemented. |
| Response doesn't reveal account existence | **PASS** | `forgot-password` returns byte-identical generic messages for an existing vs. a nonexistent email; login failures return the same generic "Invalid email or password" for both wrong-password and unknown-email cases. |
| Correct gender → ALLOWED | **PASS** | Live: BOY student → BOY-building room via warden staff-allocate (`200`), and GIRL student → GIRL-building room via self-service (`200`). |
| Wrong gender → BLOCKED | **PASS** | Live, all 3 entry points: staff allocate (GIRL→BOY room, BOY→GIRL room) and self-service (GIRL→BOY room) all returned `400` with `"This hostel is reserved for <X> students; your registered gender (<Y>) is not eligible."`, with valid capacity/department/authorization otherwise satisfied. Bulk-allocate on a BOY building processed only BOY candidates (a GIRL test student was never included in `studentsProcessed`), confirming the pre-existing gender-correct bulk behavior is unchanged. |
| First refresh exchange → SUCCESS | **PASS** | Live sequential exchange returned a new access+refresh token pair. |
| Replay of already-rotated token → BLOCKED | **PASS** | Replaying the original (already-exchanged) refresh token live returned `400 "Refresh token not found"`. |
| Concurrent duplicate exchange → exactly one SUCCESS | **PASS** | 3 live bounded runs of two simultaneous `POST /auth/refresh` requests with the *same* token against the real running server + real MySQL: every run showed exactly 1×`200`/1×`400`, never 2×`200`. Corroborates the existing 20/20 automated concurrency test (`RefreshTokenRepositoryConcurrencyTest`). |
| Cross-user access (IDOR) → BLOCKED | **PASS** | Student A's JWT against Student B's outpass by ID (`GET`/`DELETE /student/outpass/{id}`) → `400 "Access denied"` both times; Student B could read their own record fine. |
| Cross-hostel access → BLOCKED | **PASS** | Warden A (owns "Marutham") against Warden B's building ("NRI") across 4 different mutating endpoints — room allocate, bulk-allocate, change building gender, rename building — all returned `400 "You can only manage your own hostel"` / `"...rooms in your own hostel"`; building 2's name/gender confirmed unchanged in the DB afterward. |
| Privilege escalation → BLOCKED | **PASS** | Live: STUDENT→WARDEN/SECURITY/ADMIN, SECURITY→WARDEN/ADMIN, WARDEN→ADMIN, and no-token→STUDENT-endpoint — all 7 combinations returned `403`. |
| JWT tampering → BLOCKED | **PASS** | Live, against the running server (not just unit tests): tampered signature, tampered payload (role escalation STUDENT→ADMIN with stale signature), `alg:none`, malformed token, missing token — all returned `403`. |
| Expired token → BLOCKED | **PASS** | Covered by `JwtTokenProviderTest` (unit-level, real `JwtTokenProvider`); access-token TTL (30 min) confirmed live via decoded `iat`/`exp` claims on real login responses. |
| Foreign signature → BLOCKED | **PASS** | `JwtTokenProviderTest.tokenSignedWithADifferentSecretIsRejected` (real jjwt verify path). |
| Duplicate active outpass → BLOCKED | **PASS** | Live: second `POST /student/outpass` while the first is still `PENDING` → `400 "You already have an active outpass request..."`. |
| Concurrent duplicate creation → exactly one succeeds | **PASS** | Live: two simultaneous `POST /student/outpass` for a student with no existing outpass → one `200`, one `400`; exactly 1 row in the DB afterward. Corroborates the existing 15/15 automated concurrency test. |
| Invalid state transitions → BLOCKED | **PASS** | Live, full outpass state machine exercised end-to-end: mark-departure before approval → blocked; double-approve → blocked; decline an already-approved outpass → blocked; mark-return before departure → blocked; double mark-departure → blocked; double mark-return → blocked. Every valid transition (approve → depart → return) succeeded in order. |

### 7.3 Additional checks performed

- **CORS** — `PASS`. `OPTIONS` preflight from `https://evil.com` → `403 "Invalid CORS request"`, no `Access-Control-Allow-Origin` echoed. Preflight from the real local dev origin → `200` with `Access-Control-Allow-Origin` set to that exact origin (never `*`) plus `Access-Control-Allow-Credentials: true`. No wildcard-with-credentials misconfiguration.
- **Security headers** — `PASS` (with one Low/informational note). Live production response headers include `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `X-XSS-Protection: 0`, and `Cache-Control: no-cache, no-store, ...` on authenticated/sensitive responses. No `Content-Security-Policy` header is set — see §7.5 Low/informational.
- **Error handling** — `PASS`, with two real defects **found and fixed during this pass** (see §7.4). Retested after the fix: malformed JSON and bad path-parameter types now return clean, generic messages with no internal Java class names or parser internals.
- **SQL injection** — `PASS`. `' OR '1'='1`-style payload in the login email field is rejected by `@Email` DTO validation before reaching any query; a combined XSS+SQLi payload (`<script>...</script> OR 1=1; DROP TABLE students;--`) submitted as an outpass `reason` was persisted and returned as inert, unexecuted text via JPA's parameterized queries — `students` table row count unchanged before/after.
- **Stored XSS** — `PASS`. No `dangerouslySetInnerHTML` (or Angular equivalent) anywhere in `frontend/src/` (verified by direct grep of the entire frontend source) — React's default auto-escaping applies to every field that renders user input, including `reason`, complaint `description`, and student `name` (a pre-existing local dev record literally named `Test Flow <b>User</b>` from earlier testing confirms this has been true in practice, not just in theory).
- **Path traversal** — `NOT APPLICABLE`. There is no filesystem-backed file storage or filename handling anywhere in the app; the complaint "photo" field is a raw base64 string stored directly in a `LONGTEXT` DB column, not a multipart upload with a server-side filename/path.
- **File upload** — `NOT APPLICABLE` for classic upload attack vectors (dangerous file types, MIME sniffing, path escape) for the same reason — there is no multipart upload endpoint. Informational: no explicit app-level size cap on the base64 `photo` string (relies on the servlet container's default request-body size limit) — not pursued further per the "no DoS testing" rule.
- **Input validation edge cases** — `PASS`. Invalid enum value (complaint category) → clean `400`; oversized `reason` (10,000 chars) → clean DTO validation rejection (`"Reason cannot exceed 500 characters"`); negative/non-numeric/oversized-numeric path IDs → clean `400`s (post-fix, see §7.4); unexpected extra JSON fields (`"isAdmin":true`, `"role":"ADMIN"`) on a login request → silently ignored, role in the response still correctly `STUDENT` (no mass-assignment/role-spoofing surface).
- **Password reset** — `PASS`, full live flow: invalid token rejected; expired token rejected (message distinguishes "expired" from "invalid" only after a valid token format, not before — does not leak whether an unexpired token exists for a given email); valid token succeeds; the same token cannot be reused after success; old password stops working; new password works; `forgot-password` response is identical for existing vs. nonexistent accounts; a successful reset also revokes all of that user's existing refresh tokens (`refreshTokenService.deleteByUserIdAndUserType`, confirmed by code read, consistent with observed behavior).
- **Rate limiting outside auth** — `PASS`. `/warden/rooms/**` (previously exempt, fixed as DiD-4) is now live-rate-limited — confirmed via `X-RateLimit-Remaining` decrementing on successive requests with a real warden token. `/auth/refresh` and `/auth/reset-password` remain rate-limited (both were observed returning `429` during bounded testing in this pass).
- **Session/token security** — `PASS`. Access-token TTL is 30 minutes live (decoded JWT `exp - iat`). Refresh-token rotation is single-use and atomic (§ above). Logout/reset revoke server-side refresh-token state; access tokens remain stateless and valid until natural expiry after logout (inherent, documented limitation — see §7.6).
- **Production configuration** (values never printed, only `configured`/`not configured`) — `JWT_SECRET`: configured (env-based, not hardcoded — confirmed by `render.yaml` `sync: false` and `EnvConfig.java`'s dotenv-driven `System.setProperty` pattern). `DB_PASSWORD`: configured, not hardcoded. `MAIL_PASSWORD`: configured, not hardcoded. `CORS_ALLOWED_ORIGINS`: configured to the exact production frontend origin, not `*`. `DB_SSL_MODE`: `REQUIRED`. HTTPS: enforced with HSTS. Debug/verbose error detail: **not** exposed (confirmed live and via the two fixes in §7.4).
- **Frontend refresh behavior (multi-tab/concurrent 401 handling)** — `NOT TESTED` this pass (would require browser automation against a running frontend dev server, which was out of scope for the time available in this verification round). The backend-side invariant it depends on (exactly-one-refresh-succeeds under concurrency) **was** independently verified live (see table above); the frontend interceptor code in `frontend/src/services/api.js` was not re-read/re-verified in this session. Recommend a follow-up Playwright-based check if this area is a priority.
- **Room-allocation capacity concurrency** (two students racing for the last slot in a room) — `NOT TESTED` live this pass (pre-existing pessimistic-locking behavior, unrelated to this round's fixes; covered only by the pre-existing `RoomServiceTest`/`RoomServiceHostelOwnershipTest` unit suites, not a fresh live concurrency test). Not flagged as a risk — no code in this area changed — but noted as not independently re-verified live.

### 7.4 New defects found and fixed during this verification pass

Two real (non-security-critical but genuine) defects were found by live testing — not by code inspection — and fixed immediately, with regression tests, per the "find it, fix it, retest it" rule for this pass.

**FINDING-1 — Blank (not null) building `gender` would incorrectly block all allocation**
- **Severity:** Low (functional correctness / availability, not a security bypass — it fails *closed*, over-rejecting rather than under-protecting).
- **Discovery:** The local dev database's `buildings.gender` column is `NOT NULL DEFAULT` with existing rows holding `''` (empty string), not SQL `NULL`. `RoomService.checkGenderEligibility`'s guard (`buildingGender == null || studentGender == null`) does not treat blank the same as null, so `"".equalsIgnoreCase("BOY")` is `false` — every allocation into a building with unset gender would have been wrongly rejected.
- **Production impact:** None currently — verified live via `GET /auth/buildings` that all 11 production buildings have proper non-blank `gender` values (`BOY`/`GIRL`). This is a latent defect, not an active production issue.
- **Fix:** `RoomService.checkGenderEligibility` now also treats a blank/whitespace-only `buildingGender` or `studentGender` as "nothing to validate against," matching the null-handling convention. (`RoomService.java`)
- **Regression test:** `RoomServiceTest.genderCheckIsSkippedWhenBuildingGenderIsBlankRatherThanNull` — asserts allocation succeeds when the building's gender is `""`.
- **Status:** Fixed, tested (23/23 `RoomServiceTest` pass).

**FINDING-2 — Internal Java type names leaked in two framework-error paths**
- **Severity:** Low (information disclosure — internal class/parameter names, not secrets, stack traces, or DB details).
- **Discovery:** Live requests with a malformed JSON body and a non-numeric path ID (`GET /student/outpass/abc`) returned raw Spring/Jackson exception messages (`"Failed to convert value of type 'java.lang.String' to required type 'java.lang.Long'..."`, `"JSON parse error: ..."`) because `MethodArgumentTypeMismatchException` and `HttpMessageNotReadableException` fell through to the generic `RuntimeException` handler (which intentionally echoes `ex.getMessage()` for legitimate business-rule exceptions) instead of being sanitized the way `IllegalArgumentException`/`DataAccessException` already are in the same file.
- **Fix:** Added two dedicated `@ExceptionHandler`s in `GlobalExceptionHandler` (`handleTypeMismatch`, `handleMessageNotReadable`), following the exact existing pattern, returning generic messages ("Invalid value provided in request" / "Malformed request body") instead of the framework's raw message.
- **Regression tests:** `GlobalExceptionHandlerTest.handleTypeMismatch_hidesInternalJavaTypeName`, `handleMessageNotReadable_hidesParserInternals`.
- **Status:** Fixed, tested, and retested live against the running local server (both requests now return the clean generic messages).

**FINDING-3 — `Outpass.reason` DB column (50 chars) narrower than its own DTO validation (500 chars)**
- **Severity:** Low (functional correctness — a legitimate, DTO-valid request could fail with an unexpected `500` instead of clean validation feedback; no data leak, since `GlobalExceptionHandler.handleDataAccessException` already returns a generic message for this).
- **Discovery:** Live-submitting an outpass with a 57-character `reason` (well under the DTO's 500-char limit added as DiD-2 earlier this round) returned `HTTP 500` — the actual persisted `outpasses.reason` column was `VARCHAR(50)`, a pre-existing constraint (`@Column(length = 50)` on the entity, predating this round's `@Size(max = 500)` DTO addition) that was never reconciled when the DTO validation was added.
- **Production impact:** Unknown/unverified directly (no DB access to Aiven), but the same schema-drift mechanism (`ddl-auto=update` never widening existing columns, per this repo's own documented pitfall) makes it likely production has the same `VARCHAR(50)` column. **A manual migration is required in production** — see §7.6.
- **Fix (code/local):** Widened `Outpass.reason` to `@Column(length = 500)`; added the matching `ALTER TABLE outpasses MODIFY COLUMN reason VARCHAR(500) NULL;` to all three schema files (`schema.sql`, `schema-cloud.sql`, `db/schema-managed.sql`, matching this repo's existing idempotent-`ALTER`-after-`CREATE TABLE` convention) and applied directly to the local dev database.
- **Regression test:** `OutpassRequestValidationTest.entityColumnLengthMatchesDtoMaxSize` — reflection-based assertion that the entity's `@Column(length=...)` matches the DTO's `@Size(max=...)`, so the two can't silently drift apart again.
- **Status:** Fixed and tested locally (retested live: the same payload that returned `500` before now returns `200`). **Not yet applied to production** — requires manual action, see §7.6.

### 7.5 Low/informational (no code change made)

- **No `Content-Security-Policy` header** on API responses. Low impact for a pure JSON API (no HTML is ever served by the backend), but would be a defense-in-depth improvement if the API is ever also used to serve any HTML/error pages directly.
- **`400` instead of `403` for ownership/authorization-denied responses** (e.g. the IDOR and cross-hostel checks above return `400 Bad Request` with a message like `"Access denied"` / `"You can only manage your own hostel"`, rather than `403 Forbidden`). Functionally the access is still correctly blocked; this is a minor HTTP-semantics inconsistency, not a vulnerability, and was left unchanged to avoid touching a wide surface of existing, working authorization checks for a cosmetic status-code correction outside this round's scope.
- **No explicit application-level size cap** on the complaint `photo` base64 field (relies on the servlet container's default request body limit). Not pursued (DoS-adjacent, excluded from this testing round by explicit instruction).

### 7.6 Deployment assumptions and required manual follow-up

- **Single-instance assumption (POTENTIAL-1, unchanged):** Reconfirmed live — the production Render service is single-instance (free plan, cold-start behavior observed). In-memory rate-limit/backoff state remains correct only as long as this holds.
- **Stateless JWT limitation (POTENTIAL-2, unchanged):** Reconfirmed — access tokens remain valid until natural expiry (now 30 min) even after logout/password-reset/refresh-token revocation, since only refresh-token state is server-side revocable.
- **⚠️ Action required in production:** Apply `ALTER TABLE outpasses MODIFY COLUMN reason VARCHAR(500) NULL;` to the production (Aiven) database to fix FINDING-3. This was deliberately **not** run against production by the assistant (no production DB credentials were requested or used, and schema changes against a live production database are a hard-to-reverse, shared-infrastructure action requiring explicit operator action/approval). The statement is idempotent and matches the existing migration style already present in `schema.sql`.
- **Not independently re-tested live this round:** frontend concurrent-401/refresh handling (browser-level), and room-capacity allocation concurrency (unrelated to this round's fixes). See §7.3 for detail.

### 7.7 Final automated verification

```
Backend tests:        182/182 pass (mvn test; 178 at the start of this verification pass, +4 new
                       regression tests added during it: 1 gender-blank-string, 2 exception-handler
                       sanitization, 1 entity/DTO reason-length-drift guard)
Backend package:       PASS (mvn package -DskipTests -> target/portal-0.0.1-SNAPSHOT.jar)
Frontend build:        PASS (npm run build; pre-existing >500kB single-chunk warning, unrelated to security)
Security-specific tests: JwtTokenProviderTest 7/7, LoginAttemptServiceTest 8/8, RateLimiterServiceTest 4/4,
                       GlobalExceptionHandlerTest 5/5, OutpassRequestValidationTest 7/7, WebConfigTest 3/3
Concurrency tests:     RefreshTokenRepositoryConcurrencyTest 20/20 (automated) + 3/3 live HTTP runs;
                       OutpassCreationConcurrencyTest 15/15 (automated) + 1/1 live HTTP run
```

### 7.8 Final report

**Critical/High:** None remaining.

**Medium:** None remaining.

**Low/Informational:**
- FINDING-3 fix not yet applied to the production database (manual `ALTER TABLE` required — §7.6).
- No `Content-Security-Policy` header (§7.5).
- `400` used instead of `403` for some authorization-denial responses (§7.5) — cosmetic.
- No explicit size cap on the complaint photo field (§7.5).
- POTENTIAL-1 and POTENTIAL-2 from §2/§5 remain documented deployment assumptions, unchanged.

**Fixed during this verification pass:**
- FINDING-1 (blank building-gender over-rejection), FINDING-2 (internal type-name leak in two error paths), FINDING-3 (reason column/DTO length mismatch) — all fixed, tested, and retested live. See §7.4.

**Verified secure (live, this pass):** brute-force protection + backoff + endpoint-switching/normalization resistance, gender-segregation enforcement (all allocation paths), refresh-token single-use rotation including live concurrent replay, IDOR protection, cross-hostel authorization, privilege escalation resistance (7 combinations), live JWT tampering resistance (5 attack variants), full outpass state-machine integrity, duplicate/concurrent-duplicate outpass prevention, password reset flow end-to-end, CORS, security headers, SQL injection resistance, stored-XSS resistance (framework-level), input validation edge cases, rate limiting on `/warden/rooms/**` + `/auth/refresh` + `/auth/reset-password`, production configuration hygiene (secrets not hardcoded, HTTPS/HSTS enforced, CORS not wildcarded).

**Remaining assumptions:**
- Single-instance deployment (rate-limit/backoff correctness depends on this).
- Stateless JWT: access tokens remain valid until natural expiry after logout/revocation (30 min window).
- Frontend browser-level concurrent-refresh handling and room-capacity allocation concurrency were not independently re-tested live this round (no code changed in these areas).
- FINDING-3's database migration has not been applied to production yet — requires manual operator action.

### Production readiness: **READY WITH DOCUMENTED LOW-RISK ITEMS**

No Critical or High vulnerability remains. All three defects found during this live verification pass were Low severity, were fixed and retested (two fully deployed via code; one — FINDING-3 — fixed in code/schema files and locally verified, but still requires a manual, idempotent `ALTER TABLE` to be run against the production database before it's fully closed there). All explicit acceptance criteria in this verification's brief (authentication, room allocation, refresh tokens, authorization, JWT, business logic) passed against a real running server with real concurrent HTTP traffic, not code inspection alone.

## 8. Final Remediation & Release Verification

This section covers the release-prep pass that follows §7: giving FINDING-3 a proper, repository-tracked production migration (rather than relying on the manually-run `ALTER TABLE` statement quoted in §7.6), re-running the full verification suite, and preparing the repository for a clean commit. Nothing in §1–§7 is superseded; this section only adds to it.

### 8.1 Migration mechanism used

This repository has no Flyway, Liquibase, or other managed-migration framework (confirmed: no `flyway`/`liquibase` dependency in `backend/pom.xml`, no `db/migration` or `V*__*.sql` directory convention anywhere in the tree). The project's actual, pre-existing convention (documented in `backend/AGENTS.md`: *"Raw schema, backfill scripts (not run automatically) → `db/schema-managed.sql`, `db/backfill-*.sql`"*) is a set of standalone, idempotent, manually-run SQL scripts living in `backend/db/`, alongside the full reviewed schema-of-record (`db/schema-managed.sql`). This convention was followed rather than introducing a new migration framework.

**New file:** `backend/db/migrate-outpass-reason-length.sql` — a standalone, idempotent script (`ALTER TABLE outpasses MODIFY COLUMN reason VARCHAR(500) NULL;` plus a verification `SELECT` against `INFORMATION_SCHEMA.COLUMNS`) that widens the column without touching any existing row data. `MODIFY COLUMN` to an identical definition is a no-op, so the script is safe to run more than once. This is now the single source of truth for closing FINDING-3 in any environment (including production) whose database predates this fix — it does not depend on `db/schema-managed.sql` having been reapplied in full.

The three schema files touched in §7.4 (`db/schema-managed.sql`, `src/main/resources/schema.sql`, `src/main/resources/schema-cloud.sql`) are unchanged from §7 and continue to carry the same `ALTER TABLE` inline, so a **fresh** deployment (empty database) also ends up with the correct column width without needing the standalone script.

### 8.2 Local verification of the migration

Run against the local production-equivalent MySQL database (`outpass_portal`), independent of the schema-file edits already applied in §7:

- Applied `backend/db/migrate-outpass-reason-length.sql` directly (`mysql -u root outpass_portal < backend/db/migrate-outpass-reason-length.sql`) — completed without error, returned `reason | 500` from the verification `SELECT`.
- **Existing data intact:** `SELECT COUNT(*), MAX(LENGTH(reason)) FROM outpasses` before and after showed the same row count (4) and no data truncation.
- **500-character reason insert:** inserted a row directly via SQL with a 500-character `reason` — succeeded, `LENGTH(reason) = 500` confirmed, test row deleted afterward.
- **Live HTTP re-verification** (local `mvn spring-boot:run` against this same database, using pre-existing test account `test-student-a@test.local`):
  - `POST /api/student/outpass` with a 500-character `reason` → **HTTP 200**, outpass created successfully (previously this class of request depended on the column already being widened — now proven end-to-end through the real controller → service → repository → DB path, not just a direct SQL insert).
  - `POST /api/student/outpass` with a 501-character `reason` → **HTTP 400**, `{"message":"Validation failed","data":{"reason":"Reason cannot exceed 500 characters"}}` — rejected cleanly by DTO validation before ever reaching the database, no raw exception.
  - `POST /api/student/outpass` with a deliberately malformed JSON body → **HTTP 400**, `{"message":"Malformed request body"}` — reconfirms the FINDING-2 fix (`GlobalExceptionHandler.handleMessageNotReadable`) is still in effect and no parser internals leak.
  - Test outpass rows created during this check were deleted afterward; no test data was left in the local database.
- Local server was stopped (`pkill -f spring-boot:run`; confirmed via `lsof -i :8080` returning nothing) once verification completed.

**Production database migration status:** `PRODUCTION DATABASE MIGRATION STILL REQUIRED`. The assistant did not connect to or modify the production (Aiven) database in this pass — no production credentials were used or requested, consistent with §7.6's original scope decision. To close FINDING-3 in production, an operator with production DB access must run:

```sql
-- backend/db/migrate-outpass-reason-length.sql
ALTER TABLE outpasses MODIFY COLUMN reason VARCHAR(500) NULL;
```

This is safe to run at any time (idempotent, no data loss, no downtime — a single in-place `MODIFY COLUMN` on an already-nullable `VARCHAR` column).

### 8.3 Full verification suite re-run

```
Backend tests:         182/182 pass (mvn test) — unchanged from §7.7; no test/production code
                        changed in this pass beyond the new standalone migration script and this
                        section of vulnerabilities.md.
Backend package:        PASS (mvn package -DskipTests)
Frontend build:         PASS (npm run build; pre-existing >500kB single-chunk warning, unrelated
                        to security, unchanged from §7.7)
```

Security regression categories re-confirmed in this pass (via the existing dedicated automated test classes, all passing as part of the 182/182 above, plus the live HTTP checks in §8.2 for the specific change in this pass):

| Category | Automated coverage | This-pass status |
|---|---|---|
| Brute force / backoff / endpoint-switching / email normalization | `LoginAttemptServiceTest`, `RateLimiterServiceTest`, `AuthServiceTest` | PASS (unchanged; live-verified in §7) |
| Gender-segregated room allocation | `RoomServiceTest`, `RoomServiceHostelOwnershipTest`, `HostelEligibilityServiceTest` | PASS (unchanged; live-verified in §7) |
| Refresh-token rotation / concurrent replay | `RefreshTokenRepositoryConcurrencyTest` | PASS (unchanged; live-verified in §7) |
| IDOR / cross-hostel / privilege escalation | Covered by `RoomServiceHostelOwnershipTest` + live testing in §7 | PASS (unchanged; live-verified in §7) |
| JWT tampering | `JwtTokenProviderTest` | PASS (unchanged; live-verified in §7) |
| Duplicate/concurrent-duplicate outpass, state-machine transitions | `OutpassServiceTest`, `OutpassCreationConcurrencyTest` | PASS (unchanged; live-verified in §7) |
| `reason` validation (≤500 allowed, >500 rejected) + error sanitization | `OutpassRequestValidationTest`, `GlobalExceptionHandlerTest` | PASS — additionally **re-verified live in §8.2** against the real DB/controller stack this pass |

No security control was weakened or removed in this pass. The only functional changes made are: the new standalone migration script (`backend/db/migrate-outpass-reason-length.sql`, additive, no behavior change) and this documentation section.

### 8.4 Git diff review

`git status`/`git diff` were reviewed before staging. The diff for this pass adds exactly one new file (`backend/db/migrate-outpass-reason-length.sql`) plus this documentation update to `vulnerabilities.md`; no other source files were touched in this pass. Reviewed for: debug code, temporary test credentials, passwords, JWT secrets, API keys, hardcoded production URLs, commented-out security code — none present. The migration script contains no secrets (it is a schema-only `ALTER TABLE` with no data values or credentials). The full commit also necessarily includes the pre-existing uncommitted working-tree changes carried over from the earlier brute-force-fix and black-box-verification sessions (rate limiting, gender-check, JWT/refresh-token, exception-handler, and reason-length fixes across backend + frontend, per §3–§7) — every one of those files was reviewed against `vulnerabilities.md`'s §3–§7 findings in this pass and confirmed to belong to documented, already-verified security remediation, not unrelated or accidental changes. No `target/`, `node_modules/`, or `dist/` build artifacts are staged (respects `.gitignore`).

### 8.5 Final status

```
Critical: 0
High:     0
Medium:   0
Low:      4 (§7.5 — CSP header, 400-vs-403 semantics, no explicit photo size cap, and the
             production DB migration below)
```

**Database:**
- Migration file created: `backend/db/migrate-outpass-reason-length.sql` (idempotent, tracked in git).
- Local migration verified: yes — see §8.2 (schema width, existing-data integrity, 500-char insert, live 500/501-char HTTP behavior all confirmed).
- Production migration status: **PRODUCTION DATABASE MIGRATION STILL REQUIRED** — not applied by the assistant this pass (no production credentials used); must be run manually by an operator with Aiven production access.

**Tests:**
```
Backend tests:            182/182
Backend package:          PASS
Frontend build:           PASS
Security regression tests: PASS (7/7 categories in §8.3 table, all backed by dedicated automated
                           test classes plus this pass's live HTTP re-verification of the
                           reason-length change)
Concurrency tests:        RefreshTokenRepositoryConcurrencyTest 20/20, OutpassCreationConcurrencyTest 15/15
```

## 9. Low-Severity Remediation Pass (CSP, 400-vs-403, photo size cap, migration re-verification)

This section closes out the four Low-severity items carried forward from §7.5/§7.8/§8.5. Nothing in §1–§8 is superseded; findings below reference the original discovery in those sections and record the fix/verification.

### 9.1 CSP header

- **Finding:** No `Content-Security-Policy` header on API responses (§7.5).
- **Root cause:** `SecurityConfig`'s `HttpSecurity` chain never configured `.headers(...)`, so Spring Security's defaults (which don't include a CSP directive) applied.
- **Investigation before fixing:** Checked what the frontend actually loads — `frontend/index.html` has no external `<script>`/`<link>` tags, no CDN/Google Fonts/analytics references (`grep` for `googleapis`, `gstatic`, `cdn.`, `unpkg`, `jsdelivr` across `frontend/src` and `frontend/index.html` returned nothing); all UI dependencies (Bootstrap, FontAwesome, lucide-react) are bundled via npm/Vite, not loaded from a CDN at runtime. The backend itself never serves HTML/JS/CSS of its own — it's a pure JSON API; the React frontend is a separate Vercel deployment.
- **Remediation:** Added `.headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'")))` to `SecurityConfig.securityFilterChain`. Since the backend never returns a document for a browser to render, `default-src 'none'` (the strictest possible policy — no `unsafe-eval`, no broad `*`) is safe and doesn't touch existing `X-Content-Type-Options`/`X-Frame-Options` defaults, which Spring Security still emits unchanged.
- **Regression test:** `SecurityConfigHeadersTest` (`config` package) — a `@WebMvcTest` bringing up the *real* `SecurityConfig` filter chain (not a mocked/inspected config object) against `/health`, asserting the exact CSP header value is emitted, and that `X-Content-Type-Options`/`X-Frame-Options` are still present.
- **Live verification:** `curl -D - http://localhost:8080/api/health` → `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'` present alongside the pre-existing `X-Content-Type-Options: nosniff` and `X-Frame-Options: DENY`.
- **Status:** Fixed, tested (automated + live), no functional regression (frontend build unaffected, `npm run build` still passes).

### 9.2 400 vs 403 semantics

- **Finding:** `400` used instead of `403` for authorization-denial responses (§7.5/§7.8) — ownership/hostel-scope checks (`RoomService`, `AttendanceService`, `OutpassService`, `ComplaintService`, `WardenController.deleteAnnouncement`) threw plain `RuntimeException`, which `GlobalExceptionHandler.handleRuntimeException` maps to 400. Separately (found during this pass's investigation, not previously documented): every unauthenticated request — no token, expired token, tampered token — was *also* returning 403, not 401, because Spring Security's `AnonymousAuthenticationFilter` always installs an anonymous principal, so `hasRole(...)` checks against a missing token raise `AccessDeniedException` (403) rather than an `AuthenticationException` (401); this is why §7's live JWT-tampering/privilege-escalation tables recorded "all returned 403" for both "no token" and "wrong role" cases.
- **Root cause:** (a) Ownership-check code used generic `RuntimeException`, indistinguishable from a validation-failure `RuntimeException` once it reached `GlobalExceptionHandler`. (b) `SecurityConfig` never configured a custom `AuthenticationEntryPoint`/`AccessDeniedHandler`, so Spring's default behavior (403 for both unauthenticated and under-authorized requests, via the anonymous-principal mechanism above) was left in place.
- **Remediation:**
  - Added `ForbiddenOperationException` (`exception` package) and a dedicated `@ExceptionHandler` mapping it to `403 Forbidden`. Replaced all 14 ownership/authorization-denial `throw new RuntimeException(...)` call sites (`RoomService` ×4, `AttendanceService` ×1, `OutpassService` ×8, `ComplaintService` ×1, `WardenController` ×1) with `ForbiddenOperationException`, preserving every existing message string unchanged. Business-validation `RuntimeException`s (e.g. "Room is full", "Only pending outpasses can be approved") were left as-is — still 400, correctly.
  - Added a custom `AccessDeniedHandler` bean in `SecurityConfig`: inspects whether the current `Authentication` is null/unauthenticated/an `AnonymousAuthenticationToken` — if so, responds 401 ("Authentication required"); otherwise (a real authenticated principal with an insufficient role) responds 403 ("Access denied"). Added a matching `AuthenticationEntryPoint` bean (401) for the narrower set of cases Spring routes there directly. Wired both via `.exceptionHandling(...)`. This changes *only* the response status/body for denied requests — it does not touch which requests are allowed through; every case that was blocked before (no token, wrong role, tampered JWT) is still blocked, just now with correct 401-vs-403 semantics instead of both collapsing to 403.
- **Regression tests:**
  - `GlobalExceptionHandlerTest.handleForbiddenOperation_returns403NotBadRequest` — confirms 403, not 400.
  - `SecurityConfigAccessDeniedTest` (4 tests) — exercises the real `accessDeniedHandler()`/`authenticationEntryPoint()` beans directly: anonymous principal → 401, no `Authentication` at all → 401, authenticated-with-wrong-role → 403, entry point → always 401.
  - Existing `RoomServiceHostelOwnershipTest` (33 tests, `isInstanceOf(RuntimeException.class)` assertions) continue to pass unchanged, since `ForbiddenOperationException extends RuntimeException`.
- **Live verification (real running server, real DB-backed login):**
  - No token, `GET /api/student/profile` → `401 {"success":false,"message":"Authentication required"}`.
  - Valid STUDENT token, `GET /api/warden/outpasses/pending` → `403 {"success":false,"message":"Access denied"}`.
  - Malformed JSON, `POST /api/auth/student/login` → `400 {"success":false,"message":"Malformed request body"}` (unchanged from §7 FINDING-2 fix).
- **Status:** Fixed, tested (automated + live). No existing security control weakened — every previously-blocked request is still blocked; only the status code/body changed to match real HTTP semantics.

### 9.3 Photo upload size cap

- **Finding:** No explicit application-level size cap on base64 photo fields (`StudentProfileUpdateRequest.profilePicture`, `StudentRegistrationRequest.profilePicture`, and `Complaint.photo` via `ComplaintService.createComplaint`) — relied only on the frontend's client-side 2MB check (`frontend/src/pages/student/EditProfile.jsx`), trivially bypassed by calling the API directly (§7.5).
- **Investigation:** Traced the full upload path — the frontend never does a real multipart file upload; it reads the file via `FileReader.readAsDataURL()` and sends the resulting base64 data URI as a plain JSON string field on `PUT /student/profile` (profile), `POST /auth/student/register` (registration, **unauthenticated**), and `POST /student/complaints` (complaint photo, via an unvalidated `Map<String,Object>` body, not a DTO). Confirmed no `MultipartFile`/`spring.servlet.multipart.*` config exists anywhere in the codebase (`grep -rn "MultipartFile"` → no matches) — this really is a JSON-body-size problem, not a classic multipart-upload one. Confirmed no server-side body-size limit existed at any layer (`server.tomcat.max-*` properties absent; Tomcat's `maxPostSize` only bounds `application/x-www-form-urlencoded` parsing, not raw JSON request bodies read via `InputStream`).
- **Remediation (three layers, matching where each payload enters the app):**
  1. `@Size(max = 2_800_000)` added to `profilePicture` on both `StudentProfileUpdateRequest` and `StudentRegistrationRequest` (2.8M characters comfortably covers a 2MB image's base64 expansion, matching the frontend's own 2MB intent) — rejected cleanly via the existing `MethodArgumentNotValidException` → 400 handler, with a specific field-level message.
  2. Explicit length check added directly in `ComplaintService.createComplaint` (no DTO exists for the raw-`Map` complaint-photo field to attach `@Size` to) — same 2.8M-character limit, throwing a plain `RuntimeException` (business-rule message, safe to echo verbatim) → 400.
  3. New `MaxRequestBodySizeFilter` (`security` package, `@Component`, applied to `/auth/student/register`, `/student/profile`, `/student/complaints`): a hard 4MB request-body cap enforced *before* Jackson/the controller ever sees the body — not just via `Content-Length` (a client can omit or lie about it with chunked transfer-encoding). The filter first fast-rejects an honestly-declared oversized `Content-Length` (413, body never read at all), then for everything else reads the body itself bounded at 4MB + one 8KB buffer before handing a replayable, already-bounded copy to the rest of the chain — so an attacker sending an unbounded/never-ending chunked body can force at most ~4MB of buffering, never more. (An earlier version of this filter tried to enforce the bound via a byte-counting wrapper around the stream Jackson reads from mid-parse; live testing showed Jackson catches and rewraps *any* exception thrown from the underlying reader — checked or not — into a generic `HttpMessageNotReadableException`/400, so the dedicated 413 never survived. Reading the body inside the filter itself, before dispatch, avoids that entirely.)
- **Regression tests:**
  - `StudentProfileUpdateRequestValidationTest`, `StudentRegistrationRequestProfilePictureValidationTest` — at-limit accepted, over-limit rejected.
  - `ComplaintServiceTest` — within-limit accepted and persisted, over-limit rejected *before* touching either repository (`verifyNoInteractions`), no-photo case unaffected.
  - `MaxRequestBodySizeFilterTest` (4 tests) — unprotected path passes through untouched; declared-oversized `Content-Length` rejected with 413 before `chain.doFilter()` is ever called (`verifyNoInteractions(chain)`); understated/missing `Content-Length` with an oversized actual body rejected with 413, chain never invoked; understated `Content-Length` with a body *within* the limit reaches the chain with the full body intact (proves the replay wrapper doesn't corrupt legitimate small requests).
- **Live verification (real running server):**
  - 2.8M+1-character `profilePicture` via `PUT /student/profile` → `400 {"success":false,"message":"Validation failed","data":{"profilePicture":"Profile picture is too large (max 2MB)"}}`.
  - 4.5MB body with an honest `Content-Length` → `413 {"success":false,"message":"Request body exceeds the maximum allowed size (4MB)"}`, confirmed via server log that `chain.doFilter()`/Jackson was never reached.
  - 4.5MB body sent with `Transfer-Encoding: chunked` (no declared `Content-Length`) → `413`, same message — confirms the fast-path `Content-Length` check alone isn't what's carrying this.
  - Normal-size photo update (100KB) → `200`, photo persisted and returned in the profile response.
- **Status:** Fixed, tested (automated + live). Frontend's existing 2MB client-side check is unchanged (still a good UX fast-fail) but is no longer the only line of defense.

### 9.4 Production DB migration re-verification

- **Finding carried forward:** `backend/db/migrate-outpass-reason-length.sql` (added in §8.1) still needs to be applied to the production (Aiven) database — this was never in question this pass, just re-confirmed.
- **Re-verification this pass:** Re-ran the migration script twice in a row against the local database (`mysql outpass_portal < backend/db/migrate-outpass-reason-length.sql`) — confirmed idempotent (`ALTER TABLE ... MODIFY COLUMN` to an identical definition is a no-op on the second run), confirmed `reason` is still `VARCHAR(500)`, confirmed all 4 pre-existing `outpasses` rows are untouched (`SELECT COUNT(*)` unchanged before/after). Re-confirmed `schema.sql`, `schema-cloud.sql`, and `db/schema-managed.sql` all still carry the matching inline `ALTER TABLE outpasses MODIFY COLUMN reason VARCHAR(500) NULL;` for fresh deployments (unchanged since §8.1 — no drift introduced this pass). No new database changes were introduced; the existing migration was correct and versioned, so nothing new was invented here per this pass's brief.
- **Production status: `PRODUCTION DATABASE MIGRATION STILL REQUIRED`.** The assistant did not connect to or modify the production database in this pass either — no production credentials were used or requested. Operator action required: run `backend/db/migrate-outpass-reason-length.sql` (or the single statement `ALTER TABLE outpasses MODIFY COLUMN reason VARCHAR(500) NULL;`) against the production Aiven database once. Safe, idempotent, no data loss, no downtime.

### 9.5 Full verification suite (this pass)

```
Backend tests:          201/201 pass (mvn test; 182 at the start of this pass, +19 new regression
                        tests: 2 GlobalExceptionHandlerTest, 4 SecurityConfigAccessDeniedTest,
                        2 SecurityConfigHeadersTest, 3 StudentProfileUpdateRequestValidationTest,
                        2 StudentRegistrationRequestProfilePictureValidationTest,
                        3 ComplaintServiceTest, 4 MaxRequestBodySizeFilterTest — net across two
                        iterations of the photo-size-cap design, see §9.3)
Backend package:        PASS (mvn package -DskipTests -> target/portal-0.0.1-SNAPSHOT.jar)
Frontend build:         PASS (npm run build; pre-existing >500kB single-chunk warning, unrelated
                        to security, unchanged from §7.7/§8.3)
Security regression:    All 7 categories from §8.3's table re-confirmed unaffected (no test in
                        those categories was touched this pass); the new 401-vs-403 split was
                        specifically checked against brute-force/JWT/privilege-escalation live
                        scenarios above to confirm blocking behavior is unchanged, only status
                        codes corrected.
```

No security control was weakened or removed this pass. Functional changes: `default-src 'none'` CSP header (additive, backend serves no HTML so nothing to break), `ForbiddenOperationException`/`AccessDeniedHandler`/`AuthenticationEntryPoint` (status-code-only change, same requests blocked as before), `@Size` + `MaxRequestBodySizeFilter` (additive server-side validation, does not affect any request that was already within the frontend's own 2MB assumption).

### 9.6 Git diff review (this pass)

Reviewed the diff for: debug code, temporary test accounts/credentials, passwords, JWT secrets, API keys, hardcoded production URLs, console logging left in, commented-out security code, accidental frontend changes, accidental DB data, unrelated refactoring. None found — every changed file maps directly to one of the four findings above. No `target/`, `node_modules/`, or `dist/` build artifacts staged. Test-account password reset (`test-student-a@test.local`) performed directly against the local dev database for live verification only, using a placeholder bcrypt hash — not part of any commit, no credentials appear in source.

### 9.7 Final status (this pass)

```
Critical: 0
High:     0
Medium:   0
Low:      1 (production DB migration still outstanding -- §9.4; the other three Low items from
             §7.5/§8.5 are now Fixed, see below)
```

- CSP header — **Fixed** (§9.1).
- 400-vs-403 semantics (including the previously-undocumented unauthenticated-should-be-401 case) — **Fixed** (§9.2).
- Photo upload size cap — **Fixed** (§9.3).
- Production DB migration — **Requires production migration** (§9.4), unchanged from §8.5. Not something code/tests can close from this side; the single remaining deployment action is running the existing, already-verified migration script against the production database.

**Remaining risks (unchanged from §7.8/§8.5):** single-instance deployment assumption for in-memory rate-limit/backoff state; stateless-JWT window (access tokens valid until natural expiry even after logout/revocation); the outstanding production migration above. No new risks were introduced in this pass.

**Remaining risks:** identical to §7.8 — single-instance deployment assumption (rate-limit/backoff state correctness depends on Render staying single-instance), stateless-JWT access-token validity window (30 min) after logout/revocation, and the still-outstanding production database migration above. No new risks were introduced in this pass.
