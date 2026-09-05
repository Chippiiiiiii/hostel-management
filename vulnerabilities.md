# Vulnerabilities & Remediation Record

Last audit: 2026-09-05. This document reflects the **current** security state of the Outpass
Portal only. It is rewritten (not appended to) at each audit — history of past findings and how
they were fixed lives in git history and prior commit messages, not here.

Scope covered: backend (Spring Boot) — authentication, authorization/IDOR, room-allocation
gender segregation, refresh-token rotation, rate limiting, input validation, error-handling
disclosure, CORS/CSP/security headers, secrets handling; frontend (React) — XSS surface. Verified
by reading the current code on `main` (commit `9110d1a`, working tree clean) and cross-checking
against the previous verification passes' claims rather than assuming they still hold.

---

## Current status: no open Critical/High/Medium vulnerabilities

All previously-identified vulnerabilities (gender-segregation bypass in room allocation,
refresh-token rotation race, missing CSP header, 400-vs-403/401 authorization semantics, unbounded
photo upload size, and others) were confirmed fixed in the current code:

- `RoomService.checkGenderEligibility` (`RoomService.java`) is enforced inside `performAllocation`, the single choke point for every allocation path (self-service, staff, registration, bulk).
- `RefreshTokenRepository.deleteByTokenAtomic` performs single-use rotation as one atomic `DELETE`, checked by affected-row-count before any new token pair is minted.
- `SecurityConfig` sets `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'`, plus a custom `AuthenticationEntryPoint` (401 for unauthenticated) and `AccessDeniedHandler` (403 for authenticated-but-unauthorized) — no request that was previously blocked is now allowed through; only status-code semantics changed.
- `ForbiddenOperationException` (403) is used consistently for every ownership/hostel-scope denial across `RoomService`, `OutpassService`, `AttendanceService`, `ComplaintService`, `WardenController`.
- `MaxRequestBodySizeFilter` caps `/auth/student/register`, `/student/profile`, `/student/complaints` at 4MB before Jackson ever sees the body (handles both honest `Content-Length` and chunked/understated bodies); `@Size` constraints on `StudentProfileUpdateRequest`/`StudentRegistrationRequest.profilePicture` add a second, DTO-level bound.
- Login/auth endpoints are rate-limited both per-account (`LoginAttemptService`, exponential backoff, keyed by normalized email regardless of which role endpoint is hit) and per-IP (`RateLimiterService`); `/warden/rooms/**` is no longer exempt from the general rate limiter.
- CORS is configured with an explicit origin allowlist (`cors.allowed-origins`, env-overridable) and `allowCredentials(true)` — never a wildcard origin with credentials.
- All secrets (`JWT_SECRET`, `DB_PASSWORD`, mail credentials) are environment-variable-backed (`application.properties`); no hardcoded credentials found in source.
- No raw SQL string-concatenation anywhere in the codebase (`grep` for `createNativeQuery`/string-built `createQuery` found no matches) — all data access goes through Spring Data JPA repositories/parameterized queries.
- No `dangerouslySetInnerHTML` (or equivalent) in `frontend/src/` — React's default escaping covers all rendered user input (outpass `reason`, complaint `description`, student `name`, etc.).
- The multi-hostel warden architecture (`WardenBuildingService`, `warden_buildings` table) was reviewed for this audit: building assignment/unassignment (`AdminController` → `WardenBuildingService.assignBuilding`/`unassignBuilding`) is reachable only via `/admin/**`, which `SecurityConfig` restricts to `hasRole("ADMIN")`; every warden-facing read of their own assigned hostels (`WardenController.assignedHostelsOf`/`assignedBuildingIdsOf`) is keyed off `userPrincipal.getId()` from the authenticated JWT, never a client-supplied ID — no privilege-escalation or cross-warden IDOR path found in this architecture.

---

## Known, deliberately-accepted risks (not vulnerabilities, documented tradeoffs)

### 1. In-memory rate-limit/backoff state assumes single-instance deployment
`RateLimiterService`/`LoginAttemptService` hold state in a process-local `ConcurrentHashMap`. This
is correct only as long as the backend runs as a single instance (currently true — Render free
plan, no autoscaling configured in `render.yaml`). If the service is ever horizontally scaled,
each instance would track attempts independently, diluting the effective rate limit. **Action
needed only if/when multi-instance deployment is planned** — replace with a shared store (Redis).

### 2. Access tokens remain valid until natural expiry after logout/account changes
Stateless JWT design: `JwtAuthenticationFilter` re-checks the live `enabled` flag on every request
for Warden/SecurityGuard (so disabling one of those accounts takes effect immediately regardless
of token expiry), but Student/Admin have no `enabled` column and no "disable account" capability
exists for those roles today, so the gap is currently inapplicable to them. Access-token TTL is
30 minutes (`jwt.access-token-expiration`), which bounds the exposure window. Refresh tokens
*are* fully server-side revocable (logout, password reset). **Action needed only if a
disable-student/disable-admin feature is ever added.**

### 3. Coarse-grained generic rate-limit buckets
`RateLimitInterceptor` groups all POST actions for a user into one shared "CREATE" counter (and
similarly for UPDATE/READ) rather than per-endpoint buckets. This is a UX/business coarseness
issue, not a security issue — no cross-user or cross-privilege leakage results. Left as-is.

---

## Outstanding operational action (not a code vulnerability)

- **Production database migration not yet applied.** `backend/db/migrate-outpass-reason-length.sql` (idempotent `ALTER TABLE outpasses MODIFY COLUMN reason VARCHAR(500) NULL;`) widens the `reason` column to match the DTO's `@Size(max = 500)` validation. It is applied and verified locally, and the fresh-deploy schema files (`schema.sql`, `schema-cloud.sql`, `db/schema-managed.sql`) already carry the correct width. It has **not** been run against the production (Aiven) database — this requires an operator with production DB credentials to run it manually; it is safe, idempotent, and requires no downtime. Not something further code changes can close.

---

## Verification basis

- Read current `SecurityConfig.java`, `MaxRequestBodySizeFilter.java`, `WardenBuildingService.java`, `AdminController.java`, `WardenController.java` in full.
- Grepped the full backend source for: native/string-concatenated SQL queries, hardcoded secrets/passwords/API keys, `printStackTrace`/unsanitized exception messages reaching clients, `dangerouslySetInnerHTML` in the frontend.
- Confirmed `git log`/`git diff` show no source changes since the last verification pass (`9110d1a`) — working tree clean, so the previously-tested state is the current deployed state.
- Backend test suite: 201/201 passing as of the last run against this exact commit (rate limiting, JWT, gender eligibility, refresh-token concurrency, hostel-ownership scoping, exception sanitization, request-size limits all covered by dedicated test classes — see `backend/src/test/java/com/outpass/portal/`).

**Result: no new vulnerabilities found in this pass.**
