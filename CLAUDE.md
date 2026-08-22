# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development rules

- Before making changes, inspect the relevant existing files and understand the current implementation.
- Keep changes focused on the user's requested feature.
- Do not rewrite or refactor unrelated code.
- Reuse existing services, components, utilities, DTOs, and patterns whenever possible.
- Do not introduce a new library when the existing project already provides the required functionality.
- When changing authentication, inspect both frontend and backend auth flows before modifying code.
- Do not modify database schema or run database migration/destructive SQL commands without explicit user confirmation.
- Never expose, print, commit, or modify real secrets from `.env` files.
- Preserve existing API response formats unless a change is required.
- After backend changes, run the relevant Maven tests/build.
- After frontend changes, run lint and build when appropriate.
- When finished, summarize the files changed and explain any configuration or environment variables that need to be added.

## Project overview

Hostel Management System — students request outpasses, mark attendance (WiFi or geolocation + WebAuthn biometric), file complaints, and view announcements. Wardens approve/decline outpasses, run attendance sessions, manage rooms/buildings, and respond to complaints. Security guards verify departures/returns at the gate. Three roles: `STUDENT`, `WARDEN`, `SECURITY_GUARD`.

Two independent apps in one repo:
- `frontend/` — React 19 + Vite, deployed to Vercel
- `backend/` — Spring Boot 4 (Java 21), deployed to Render (Docker), MySQL on Aiven

## Commands

### Frontend (`frontend/`)
```bash
npm install       # install deps
npm run dev       # dev server on :5173, proxies /api -> http://localhost:8080
npm run build     # production build (vite build)
npm run lint      # eslint .
npm run preview   # preview production build
```
No frontend test suite/framework is configured.

### Backend (`backend/`)
```bash
./mvnw spring-boot:run          # run on :8080 (loads backend/.env via dotenv-java)
./mvnw clean package            # build jar
java -jar target/portal-0.0.1-SNAPSHOT.jar
./mvnw test                     # run tests (only a context-load smoke test currently exists)
./mvnw test -Dtest=ClassName    # run a single test class
```
Requires a `backend/.env` with `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS` (see README for the full local setup, including `mysql ... < src/main/resources/schema.sql`).

## Architecture

### Backend layering
Standard Spring layering under `backend/src/main/java/com/outpass/portal/`:
- `controller/` — one controller per role (`AuthController`, `StudentController`, `WardenController`, `SecurityGuardController`), plus `HealthController`. `SecurityConfig` gates each `/student/**`, `/warden/**`, `/security/**` prefix by role, so a new endpoint's URL prefix *is* its authorization — keep new controllers under the right prefix rather than adding method-level checks.
- `dto/request` and `dto/response` — controllers do not expose entities directly.
- `service/` — business logic (risk assessment, attendance verification, rate limiting logic, etc.).
- `model/entity/` — 15+ JPA entities (see README's Entity Relationship table); `model/enums/` — `Role`, `OutpassStatus`, `ComplaintCategory`, `ComplaintStatus`, `AttendanceMethod`, etc.
- `repository/` — Spring Data JPA.
- `security/` — `JwtAuthenticationFilter`, `UserPrincipal`, JWT provider. Auth is stateless (`SessionCreationPolicy.STATELESS`); the filter runs before `UsernamePasswordAuthenticationFilter`.
- `interceptor/` — per-user rate limiting (CREATE 10/hr on POST, UPDATE 20/hr on PUT, READ 200/min on GET), adds `X-RateLimit-*` response headers.
- `exception/` — `GlobalExceptionHandler` + `RateLimitExceededException`.
- `config/` — `SecurityConfig` (CORS + role-based route matchers + BCrypt), `JwtConfig`, `WebConfig`, `EnvConfig`.

Schema is applied manually (`spring.jpa.hibernate.ddl-auto=update` only updates, doesn't create from scratch) — see the SQL files table in README for which script to use where: `schema.sql` (full local, includes MySQL events), `schema-cloud.sql` (no events/procedures, for cloud), `db/schema-managed.sql` (managed MySQL with indexes), plus `seed-data.sql`/`seed-cloud.sql`. `quick-start.sql` and `reset.sql` are destructive — never run them without explicit user confirmation.

### Frontend structure
Under `frontend/src/`:
- `pages/{auth,student,warden,security}/` — route-level pages, mirroring backend roles.
- `services/` — Axios API layer, one file per domain (`authService`, `outpassService`, `attendanceService`, `complaintService`, `roomService`); `api.js` holds the shared Axios instance + interceptors (this is where the access/refresh token refresh-on-401 flow lives).
- `context/AuthContext.jsx` — auth state provider.
- `routes/AppRoutes.jsx` + `routes/PrivateRoute.jsx` — role-based route protection, mirrors the backend's role-gated URL prefixes.
- `hooks/useAttendanceAlert.js`, `hooks/useOutpassNotifications.js` — cross-tab/session notifications via BroadcastChannel API, localStorage events, and the browser Notification API (not just polling — check these before adding new real-time UI behavior).
- `utils/constants.js` — API base URL, role names, status enums; keep these in sync with the backend enums in `model/enums/`.

### Auth flow
JWT access (24h) + refresh (7d, localStorage) tokens. Refresh happens automatically on a 401 via the Axios interceptor in `services/api.js`. Password reset is token-based (`PasswordResetToken` entity, `/auth/forgot-password` + `/auth/reset-password`). When touching auth, check both `AuthController`/`AuthService` (backend) and `authService.js`/`AuthContext.jsx` (frontend) — the flow spans both.

### Attendance system
Two verification methods selected per session by the warden: WiFi (SSID + subnet match, checked via `util` subnet helpers) or geolocation (must be within 50m of configured hostel coordinates) + WebAuthn/FIDO2 biometric. Wardens start/stop `AttendanceSession`s; each mark becomes an `AttendanceRecord`.

### Deployment
Three independently deployed pieces — see `render.yaml` (backend, Docker on Render, health check `/api/health`) and README's Deployment section for the full matrix (Vercel for frontend, Aiven MySQL). `CORS_ALLOWED_ORIGINS` and `VITE_API_BASE_URL` must be kept in sync across environments when adding a new deployed frontend origin.
