# 🏨 Hostel Management System

A comprehensive hostel management platform for educational institutions — handling student outpasses, attendance tracking, department- and year-eligibility-aware room allocation, complaint management, and announcements with role-based access for Admins, Students, Wardens, and Security Guards.

**🌐 Live:** [hostel-management-mit.vercel.app](https://hostel-management-mit.vercel.app)

## 📋 Overview

The Hostel Management System digitizes day-to-day hostel operations. Students select their academic year and an eligible hostel, register into a room, request outpasses, mark attendance via WiFi or geolocation + biometric verification, file complaints, and view announcements — once registered, a student's room is locked and can only be changed by a Warden or Admin. Wardens manage outpass approvals, monitor attendance, configure floor/room department eligibility, run  bulk room allocation, and respond to complaints. Admins create and manage Warden/Security Guard accounts (with enable/disable), have full room-management control, and configure which hostels are available to students in each academic year. Security guards verify departures and returns at hostel gates. The system provides real-time notifications, risk assessment for frequent outpass users, and exportable attendance reports.

## ✨ Features

### 👨‍🎓 Student Features

- **Academic Year & Eligible Hostels** — Registration requires an explicit academic year (1st–4th, no default); only hostels an Admin has configured as eligible for that year are offered, and the backend independently rejects any other combination
- **Room Locking** — Once a room is assigned at registration, a student cannot change it themselves (`PUT /student/profile` and `POST /student/rooms/allocate` both reject the attempt) — only a Warden or Admin can move them afterward
- **Outpass Requests** — Submit outpass requests with reason, destination, dates, and contact details
- **Outpass Tracking** — Real-time status tracking (Pending → Approved/Declined → Departed → Completed)
- **Cancel Pending Outpasses** — Cancel requests that haven't been reviewed yet
- **Attendance Marking** — Mark attendance via hostel WiFi detection or geolocation + WebAuthn biometric verification
- **Attendance History** — View attendance records, present/absent counts, and percentage
- **Complaints** — File categorized complaints (Plumbing, Electrical, Cleanliness, Furniture, Internet, Noise, Other) with photo attachments
- **Announcements** — View notices posted by wardens
- **Roommate Info** — See who else is allocated to the same room
- **Profile Management** — Update contact details and profile picture
- **Dashboard** — Overview of outpass stats, attendance percentage, quick navigation
- **Notifications** — Browser notifications for outpass status changes and attendance sessions

### 👔 Warden Features
- **Outpass Management** — Approve or decline student outpass requests with comments/reasons
- **Bulk Actions** — Approve or decline multiple outpasses at once
- **Student Risk Assessment** — Automatic risk level (Low/Medium/High) based on outpass frequency and on-time return rate
- **Attendance System** — Start/stop attendance sessions, view real-time records, configure WiFi SSID/subnet and GPS coordinates
- **Attendance Reports** — Date-range reports with CSV export
- **Room Management** — Add/remove buildings, floors, rooms; configure capacity; toggle building type (Regular/NRI) and gender (Boys/Girls); edit room numbers (stored values, never regenerated from the floor)
- **Department Eligibility** — Set a default department per floor, override it per room (room override always wins), and view each room's effective department and whether it's inherited or overridden
- **Manual & Bulk Room Allocation** — Move any student (including one already housed) into a specific room, or run "Auto Allocate Rooms" per building (optionally scoped to one floor) to assign every currently-unassigned matching-gender student into a department-eligible room with capacity — never moves an already-housed student; transactional and safe under concurrent allocation
- **Student Directory** — View all registered students with search
- **Complaint Management** — View all complaints, update status (Pending → In Progress → Resolved/Rejected), respond to students
- **Announcements** — Post and manage notices for students
- **Dashboard Analytics** — Total students, room occupancy, today's attendance, pending complaints, outpass stats

### 🔒 Security Guard Features
- **Departure Verification** — Verify and mark student departure for approved outpasses
- **Return Verification** — Verify and mark student return, with automatic late-return detection
- **Active Outpasses** — View all currently active outpasses for the hostel
- **Today's Schedule** — List of all outpasses scheduled for today
- **Dashboard** — Overview of approved (ready to exit), departed, and today's outpass counts

### 👑 Admin Features
- **Warden & Security Guard Accounts** — Create accounts, view all accounts, enable/disable them at any time
- **Immediate Lockout on Disable** — A disabled account is rejected on its very next request, not just at its next login — an already-issued access token is invalidated mid-session, not merely blocked from renewal
- **Full Room Control** — Everything a Warden can do for rooms/floors/departments/bulk allocation (Admin reuses the same endpoints; there is no separate, duplicated room-management API)
- **Year → Hostel Eligibility Configuration** — Add/remove which hostels are selectable by students in each academic year (1st–4th); duplicate mappings are rejected
- **No self-registration** — There is deliberately no public endpoint to create an Admin account; the first one is bootstrapped via a manual SQL insert (see Getting Started)

## 🛠️ Tech Stack

### Frontend
| Package | Version | Purpose |
|---------|---------|---------|
| React | 19.2.0 | UI framework |
| React Router DOM | 7.13.1 | Client-side routing |
| Vite | 7.3.1 | Build tool and dev server |
| Bootstrap | 5.3.8 | Responsive CSS framework |
| React Bootstrap | 2.10.10 | Bootstrap React components |
| Font Awesome | 7.2.0 | Icon library |
| Axios | 1.13.6 | HTTP client |
| React Hot Toast | 2.6.0 | Toast notifications |
| date-fns | 4.1.0 | Date formatting |
| React Hook Form | 7.71.2 | Form handling |
| Lucide React | 0.575.0 | Additional icons |

### Backend
| Package | Version | Purpose |
|---------|---------|---------|
| Spring Boot | 4.0.3 | Java backend framework |
| Spring Security | — | Authentication and authorization |
| Spring Data JPA | — | Database ORM with Hibernate |
| MySQL Connector/J | — | MySQL JDBC driver |
| JJWT | 0.12.6 | JWT token generation and validation |
| Lombok | — | Reduce boilerplate code |
| dotenv-java | 3.0.0 | Load environment variables from .env |
| Java | 21 | Runtime (LTS) |

## 🏗️ Architecture

```
hostel-management/
├── frontend/                          # React frontend (Vite)
│   ├── src/
│   │   ├── components/common/         # Shared components
│   │   │   ├── Navbar.jsx             #   Navigation bar with dark mode toggle
│   │   │   ├── LoadingSpinner.jsx     #   Loading state component
│   │   │   ├── StudentStatsCard.jsx   #   Student stats + risk assessment card
│   │   │   ├── ApproveCommentsModal.jsx
│   │   │   └── DeclineReasonModal.jsx
│   │   ├── components/room/
│   │   │   └── RoomManagementPanel.jsx # Shared building/floor/room/department/bulk-allocate
│   │   │                               #   UI, reused by both the Warden and Admin room pages
│   │   ├── context/
│   │   │   └── AuthContext.jsx        # Authentication context provider
│   │   ├── hooks/
│   │   │   ├── useAttendanceAlert.js  # Attendance session polling + notifications
│   │   │   └── useOutpassNotifications.js # Outpass status change alerts
│   │   ├── pages/
│   │   │   ├── auth/                  # Login, Register, ForgotPassword, VerifyEmail
│   │   │   ├── student/               # Student dashboard, outpass, attendance, complaints
│   │   │   ├── warden/                # Warden dashboard, management pages
│   │   │   ├── admin/                 # Admin dashboard, Wardens, SecurityGuards, RoomManagement,
│   │   │   │                          #   YearHostels
│   │   │   └── security/              # Security guard dashboard
│   │   ├── routes/
│   │   │   ├── AppRoutes.jsx          # Route definitions
│   │   │   └── PrivateRoute.jsx       # Role-based route protection
│   │   ├── services/                  # API service layer (Axios)
│   │   │   ├── api.js                 #   Axios instance + interceptors
│   │   │   ├── authService.js         #   Authentication API calls
│   │   │   ├── outpassService.js      #   Outpass + announcements API
│   │   │   ├── attendanceService.js   #   Attendance + WiFi/geo/biometric
│   │   │   ├── complaintService.js    #   Complaints API
│   │   │   ├── roomService.js         #   Room/building/department/bulk-allocate API
│   │   │   └── adminService.js        #   Warden/guard account + year-hostel config API
│   │   ├── utils/constants.js         # API URL, roles, status enums
│   │   ├── App.css                    # Global component styles
│   │   └── index.css                  # Theme variables, dark mode
│   ├── vercel.json                    # Vercel SPA rewrite config
│   └── vite.config.js                 # Vite config with API proxy
│
├── backend/                           # Spring Boot backend
│   ├── src/main/java/com/outpass/portal/
│   │   ├── config/                    # Security, CORS, JWT, rate limit config
│   │   │   ├── SecurityConfig.java
│   │   │   ├── JwtConfig.java
│   │   │   ├── WebConfig.java
│   │   │   └── EnvConfig.java
│   │   ├── controller/                # REST API endpoints
│   │   │   ├── AuthController.java
│   │   │   ├── StudentController.java
│   │   │   ├── WardenController.java
│   │   │   ├── AdminController.java
│   │   │   ├── SecurityGuardController.java
│   │   │   └── HealthController.java
│   │   ├── dto/                       # Request/Response DTOs
│   │   │   ├── request/
│   │   │   └── response/
│   │   ├── model/
│   │   │   ├── entity/                # JPA entities (20 tables)
│   │   │   └── enums/                 # Role (incl. ADMIN), OutpassStatus, ComplaintCategory, etc.
│   │   ├── repository/                # Spring Data JPA repositories
│   │   ├── security/                  # JWT provider, auth filter, UserPrincipal,
│   │   │                              #   MaxRequestBodySizeFilter (photo-upload size cap)
│   │   ├── service/                   # Business logic layer (RoomService, AdminService,
│   │   │                              #   EmailUniquenessService, HostelEligibilityService, etc.)
│   │   ├── interceptor/               # Rate limit interceptor
│   │   ├── exception/                 # GlobalExceptionHandler, ForbiddenOperationException
│   │   │                              #   (403 for ownership/authorization denials)
│   │   └── util/                      # Rate limiter, subnet utils, EmailUtils (email normalization)
│   ├── src/main/resources/
│   │   ├── application.properties     # App config with env-var overrides
│   │   ├── schema.sql                 # Full local schema
│   │   ├── schema-cloud.sql           # Cloud-safe schema (no events)
│   │   ├── seed-data.sql              # Sample data (local)
│   │   └── seed-cloud.sql             # Sample data (cloud)
│   ├── db/
│   │   ├── schema-managed.sql         # Managed MySQL schema with indexes
│   │   ├── backfill-room-allocations.sql # Manual, one-time: link pre-existing students'
│   │   │                                 #   hostel/room strings to real RoomAllocation rows
│   │   ├── backfill-student-year.sql  # Optional manual aid: derive students.year from
│   │   │                               #   roll_no where possible (never guesses)
│   │   └── migrate-outpass-reason-length.sql # Pending: widen outpasses.reason to VARCHAR(500)
│   │                                          #   in production (see SQL Files Reference below)
│   └── Dockerfile                     # Multi-stage Docker build
│
├── render.yaml                        # Render deployment blueprint
└── README.md
```

## 📊 Database Schema

### Entity Relationship

| Entity | Table | Description |
|--------|-------|-------------|
| **Student** | `students` | Student accounts with profile, academic year (1-4, nullable for pre-existing students), hostel, room details |
| **Warden** | `wardens` | Warden accounts assigned to specific hostels; `enabled` flag (Admin-managed) |
| **SecurityGuard** | `security_guards` | Security guard accounts assigned to hostels; `enabled` flag (Admin-managed) |
| **Admin** | `admins` | Admin accounts — no self-registration; bootstrapped manually (see Getting Started) |
| **Outpass** | `outpasses` | Outpass requests with full lifecycle tracking |
| **Building** | `buildings` | Hostel buildings (type: Regular/NRI, gender: Boy/Girl) |
| **Room** | `rooms` | Individual rooms within buildings; editable `room_number` (stored, never regenerated); optional `department_override` |
| **FloorDepartment** | `floor_departments` | Default department per (building, floor); overridden by a room's own `department_override` when set |
| **YearHostelEligibility** | `year_hostel_eligibility` | Admin-configured: which hostels a student in a given academic year (1-4) may select at registration |
| **RoomAllocation** | `room_allocations` | Student-to-room assignments — presence of a row is what "locks" a student's room |
| **RoomConfig** | `room_config` | Key-value settings for room management |
| **AttendanceSession** | `attendance_sessions` | Warden-initiated attendance windows |
| **AttendanceRecord** | `attendance_records` | Individual attendance marks |
| **Complaint** | `complaints` | Student complaints with category and status |
| **Announcement** | `announcements` | Warden-posted notices |
| **RefreshToken** | `refresh_tokens` | JWT refresh token storage |
| **Token** | `tokens` | Revoked JWT blacklist (schema/repository exist but are not currently wired into request auth — see Authentication section) |
| **PasswordResetToken** | `password_reset_tokens` | Password reset flow tokens (15-minute expiry, single-use) |
| **EmailVerificationToken** | `email_verification_tokens` | Registration email-verification tokens (24-hour expiry, single-use) |
| **AccessLog** | `access_logs` | API access audit trail |

### Key Enums

| Enum | Values |
|------|--------|
| Role | `STUDENT`, `WARDEN`, `SECURITY_GUARD`, `ADMIN` |
| OutpassStatus | `PENDING`, `APPROVED`, `DECLINED`, `DEPARTED`, `COMPLETED`, `OVERDUE` |
| ComplaintCategory | `PLUMBING`, `ELECTRICAL`, `CLEANLINESS`, `FURNITURE`, `INTERNET`, `NOISE`, `OTHER` |
| ComplaintStatus | `PENDING`, `IN_PROGRESS`, `RESOLVED`, `REJECTED` |
| AttendanceMethod | `WIFI`, `GEO_BIOMETRIC` |
| AttendanceStatus | `PRESENT`, `ABSENT` |
| SessionStatus | `ACTIVE`, `CLOSED` |

## 📡 API Endpoints

All endpoints are prefixed with `/api`.

### Authentication (`/auth`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/auth/student/register` | Register a student account — requires `year` (1-4); the chosen hostel/room is validated against year eligibility, department, and capacity, and email is checked for uniqueness across **all** account types (Student/Warden/SecurityGuard/Admin), not just students |
| POST | `/auth/student/login` | Student login |
| POST | `/auth/warden/login` | Warden login (rejected if the account is disabled) |
| POST | `/auth/security/login` | Security guard login (rejected if the account is disabled) |
| POST | `/auth/admin/login` | Admin login |
| POST | `/auth/refresh` | Refresh JWT access token (also rejected if the account has since been disabled) |
| POST | `/auth/logout` | Invalidate refresh tokens |
| GET | `/auth/verify-email` | Verify a student's email via the token from the registration email |
| POST | `/auth/resend-verification` | Resend the verification email (generic response either way — does not reveal if the email exists) |
| POST | `/auth/forgot-password` | Request password reset (generic response either way — does not reveal if the email exists); supports all four account types via an explicit `role` field |
| POST | `/auth/reset-password` | Reset password with token — also revokes all of that account's refresh tokens |
| GET | `/auth/buildings` | Public building list (unfiltered — legacy/general use) |
| GET | `/auth/hostels-by-year?year=` | Public building list filtered to hostels an Admin has configured as eligible for that academic year — what the registration page actually uses |

Unauthenticated endpoints above (`register`, `forgot-password`, `resend-verification`, `verify-email`) are rate-limited by client IP (and IP+email where applicable) — see the Rate Limiting section below. Email lookups across all four account types are case-insensitive (`Test@gmail.com` and `test@gmail.com` are the same account).

### Student (`/student`) — requires `ROLE_STUDENT`

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/student/profile` | Get student profile |
| PUT | `/student/profile` | Update contact details/photo — hostel/room fields are not editable here; any attempt to change them is rejected (use the room-allocate endpoint instead, subject to the locking rule above) |
| POST | `/student/outpass` | Create outpass request |
| GET | `/student/outpass/history` | Get outpass history |
| GET | `/student/outpass/{id}` | Get specific outpass |
| DELETE | `/student/outpass/{id}` | Cancel pending outpass |
| GET | `/student/attendance/session` | Get active attendance session |
| POST | `/student/attendance/mark` | Mark attendance |
| GET | `/student/attendance/today` | Today's attendance status |
| GET | `/student/attendance/history` | Attendance history |
| GET | `/student/attendance/stats` | Attendance statistics |
| GET | `/student/attendance/verify-wifi` | Verify hostel WiFi connection |
| GET | `/student/attendance/location` | Get hostel GPS coordinates |
| GET | `/student/rooms/buildings` | Get buildings for room selection |
| GET | `/student/rooms/allocation` | Get own room allocation |
| POST | `/student/rooms/allocate` | Self-allocate to a room — **locked**: rejected (atomically, race-safe) once the student already has an allocation; only usable for a student who has none yet |
| GET | `/student/rooms/roommates` | Get roommates |
| GET | `/student/rooms/allocations` | Get all room allocations |
| POST | `/student/complaints` | Submit a complaint |
| GET | `/student/complaints` | Get own complaints |
| GET | `/student/announcements` | Get announcements |

### Warden (`/warden`) — requires `ROLE_WARDEN`

All `/warden/rooms/**` endpoints below (and `GET /warden/students`) also accept `ROLE_ADMIN` — Admin reuses this exact API for its "full room control" rather than a duplicate endpoint set.

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/warden/dashboard/stats` | Dashboard statistics |
| GET | `/warden/outpass/pending` | Pending outpasses (hostel-scoped) |
| PUT | `/warden/outpass/{id}/approve` | Approve outpass |
| PUT | `/warden/outpass/{id}/decline` | Decline outpass |
| PUT | `/warden/outpass/bulk-approve` | Bulk approve outpasses |
| PUT | `/warden/outpass/bulk-decline` | Bulk decline outpasses |
| GET | `/warden/outpass/history` | All outpasses for hostel |
| GET | `/warden/student/{id}/stats` | Student stats + risk assessment |
| POST | `/warden/attendance/start` | Start attendance session |
| POST | `/warden/attendance/stop` | Stop attendance session |
| GET | `/warden/attendance/session` | Get active session |
| GET | `/warden/attendance/session/{id}/records` | Session attendance records |
| GET | `/warden/attendance/config` | Get attendance config |
| PUT | `/warden/attendance/config` | Update WiFi/GPS config |
| GET | `/warden/attendance/report` | Attendance report (date range) |
| GET | `/warden/rooms/buildings` | Get buildings |
| POST | `/warden/rooms/buildings` | Add building |
| DELETE | `/warden/rooms/buildings/{id}` | Remove building |
| PUT | `/warden/rooms/buildings/{id}/rename` | Rename building |
| PUT | `/warden/rooms/buildings/{id}/type` | Set building type (Regular/NRI) |
| PUT | `/warden/rooms/buildings/{id}/gender` | Set building gender (Boy/Girl) |
| GET | `/warden/rooms/config` | Get room-management config (max rooms/floor, max members/room, WiFi subnets) |
| PUT | `/warden/rooms/config` | Update room-management config |
| PUT | `/warden/rooms/{roomId}/max-members` | Update room capacity |
| POST | `/warden/rooms/buildings/{id}/floors` | Add floor |
| DELETE | `/warden/rooms/buildings/{id}/floors/{floor}` | Remove floor |
| POST | `/warden/rooms/buildings/{id}/floors/{floor}/rooms` | Add room |
| DELETE | `/warden/rooms/buildings/{id}/floors/{floor}/rooms/last` | Remove last room |
| GET | `/warden/rooms/allocations` | All room allocations |
| POST | `/warden/rooms/{roomId}/allocate` | Allocate/move a student into a room — works even if they already have one (this is the authorized override of the student-side room lock); rejects a department mismatch against the room's effective department |
| DELETE | `/warden/rooms/allocations/{email}` | Remove allocation |
| PUT | `/warden/rooms/{roomId}/number` | Edit a room's number — only the number field changes; floor/building are never touched |
| PUT | `/warden/rooms/{roomId}/department` | Set a room's department override |
| DELETE | `/warden/rooms/{roomId}/department` | Remove a room's department override (reverts to the floor default) |
| PUT | `/warden/rooms/buildings/{id}/floors/{floor}/department` | Set a floor's default department |
| POST | `/warden/rooms/buildings/{id}/auto-allocate` | Bulk-allocate every currently-unassigned matching-gender student in the building (optional `floorNumber` query param) into a department-eligible room with capacity — never moves an already-housed student; returns processed/assigned/remaining/rooms-used plus per-student reasons for anyone left unassigned |
| GET | `/warden/students` | List all students |
| GET | `/warden/complaints` | Get complaints (optional status filter) |
| GET | `/warden/complaints/stats` | Complaint statistics |
| PUT | `/warden/complaints/{id}` | Update complaint status/response |
| GET | `/warden/announcements` | Get announcements |
| POST | `/warden/announcements` | Create announcement |
| DELETE | `/warden/announcements/{id}` | Delete announcement |

### Admin (`/admin`) — requires `ROLE_ADMIN`

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/admin/wardens` | Create a warden account |
| GET | `/admin/wardens` | List all wardens (incl. enabled/disabled status) |
| PUT | `/admin/wardens/{id}/status` | Enable/disable a warden — takes effect on that account's very next request, not just its next login |
| POST | `/admin/security-guards` | Create a security guard account |
| GET | `/admin/security-guards` | List all security guards |
| PUT | `/admin/security-guards/{id}/status` | Enable/disable a security guard |
| GET | `/admin/year-hostels` | Full year → allowed-hostels configuration (always includes all 4 years, even if unconfigured) |
| POST | `/admin/year-hostels` | Add a hostel to a year's allowed list — rejects a duplicate mapping |
| DELETE | `/admin/year-hostels/{year}/{buildingId}` | Remove a hostel from a year's allowed list |

Room management for Admin is **not** duplicated here — see the note at the top of the Warden section above; Admin calls the same `/warden/rooms/**` endpoints.

### Security Guard (`/security`) — requires `ROLE_SECURITY_GUARD`

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/security/outpass/active` | Active outpasses for hostel |
| GET | `/security/outpass/{id}` | Get specific outpass |
| GET | `/security/outpass/today` | Today's outpasses |
| GET | `/security/outpass/departed` | Departed outpasses |
| PUT | `/security/outpass/{id}/mark-departure` | Verify student departure |
| PUT | `/security/outpass/{id}/mark-return` | Verify student return |

## 🚀 Getting Started

### Prerequisites

- Java 21+
- Node.js 18+ and npm
- MySQL 8.0+
- Maven 3.8+

### Backend Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/Chippiiiiiii/hostel-management.git
   cd hostel-management/backend
   ```

2. **Create the database**
   ```bash
   mysql -u root -p
   ```
   ```sql
   CREATE DATABASE outpass_portal;
   ```

3. **Apply the schema**
   ```bash
   mysql -u root -p outpass_portal < src/main/resources/schema.sql
   ```

4. **Seed sample data** (optional)
   ```bash
   mysql -u root -p outpass_portal < src/main/resources/seed-data.sql
   ```

5. **Admin account** — there is no self-registration for Admins. If you ran the seed data in step 4, a sample account (`admin1@mit.edu` / `admin123`) was already created — see Default Seed Accounts below, and change its password after first login. For a production deployment (or a different email), generate your own BCrypt hash at strength 10 (matching `spring.security.password.strength`) and edit the `INSERT INTO admins (...)` row in `src/main/resources/seed-data.sql` before re-running it:
   ```bash
   node -e "console.log(require('bcryptjs').hashSync('yourPassword', 10))"
   ```

6. **Create a `.env` file** in `backend/`
   ```env
   DB_HOST=localhost
   DB_PORT=3306
   DB_NAME=outpass_portal
   DB_USERNAME=root
   DB_PASSWORD=your_password
   JWT_SECRET=your-secret-key-at-least-32-characters-long
   CORS_ALLOWED_ORIGINS=http://localhost:5173

   # Used to build links in verification/password-reset emails.
   FRONTEND_URL=http://localhost:5173

   # Optional — email features degrade gracefully if left unset (see EmailService).
   # Leave blank locally to skip sending real emails during development.
   MAIL_HOST=
   MAIL_PORT=587
   MAIL_USERNAME=
   MAIL_PASSWORD=
   ```

7. **Run the backend**
   ```bash
   ./mvnw spring-boot:run
   ```
   Backend starts on `http://localhost:8080`

### Frontend Setup

1. **Navigate to frontend directory**
   ```bash
   cd hostel-management/frontend
   ```

2. **Install dependencies**
   ```bash
   npm install
   ```

3. **Start the dev server**
   ```bash
   npm run dev
   ```
   Frontend starts on `http://localhost:5173` — the Vite config proxies `/api` requests to `localhost:8080`.

### Default Seed Accounts

If you ran the seed data:

| Role | Email | Password |
|------|-------|----------|
| Admin | admin1@mit.edu | admin123 |
| Warden | warden1@mit.edu | warden123 |
| Security Guard | security1@mit.edu | security123 |
| Student | student1@mit.edu | student123 |

⚠️ Change the Admin password after first login in any real deployment — this is a seed credential for local/dev use. Before students can meaningfully register, log in as Admin and configure at least one hostel per academic year under **Admin → Year → Hostel Eligibility** (`/admin/year-hostels`) — otherwise the registration page has nothing eligible to offer.

## 🔐 Authentication

The app uses JWT-based authentication with access + refresh token flow:

- **Access Token** — 24-hour expiry, sent as `Authorization: Bearer <token>` header. Role/enabled-status is re-checked against the database on **every request** (not trusted from the token's embedded claim), so a role change or account disable takes effect immediately — see the note below on the one case that's still bounded by token expiry
- **Refresh Token** — 7-day expiry, stored in localStorage, auto-refreshed on 401 responses
- **Role-based Access** — Routes and API endpoints are protected by role (`STUDENT`, `WARDEN`, `SECURITY_GUARD`, `ADMIN`), enforced entirely via URL-prefix matchers in `SecurityConfig` (no separate/duplicated authorization system)
- **Global Email Uniqueness** — Student, Warden, SecurityGuard, and Admin are separate tables, but an email can only belong to one of them at a time (case-insensitively) — enforced by `EmailUniquenessService` on every account-creation path, so a colliding email can never silently shadow another account
- **Warden/Security Guard Enable-Disable** — Admin-managed; a disabled account is rejected at login, at refresh-token renewal, **and** on every subsequent authenticated request via `JwtAuthenticationFilter` — an already-issued access token stops working immediately, it isn't just blocked from future logins
- **Email Verification** — New student registrations must verify via an emailed link (24h expiry, single-use) before they can log in; pre-existing/seeded accounts are treated as already verified
- **Password Reset** — Token-based password reset flow via email (15-minute expiry, single-use); a successful reset revokes all of that account's refresh tokens immediately, so any other logged-in session is forced to re-authenticate once its current access token expires
- **Session revocation limits** — Because access tokens are stateless JWTs, an access token issued *before* a password reset remains valid for up to its remaining ≤24h lifetime even after the reset (this is distinct from the disable-lockout above, which *is* checked per-request). Only the ability to mint *new* access tokens (via refresh) is cut off immediately by a password reset.
- **401 vs. 403 semantics** — A request with no/invalid token gets `401 Unauthorized`; a request from an authenticated user who lacks the required role or ownership (e.g. a Warden acting on another Warden's hostel) gets `403 Forbidden`. Enforced by custom `AccessDeniedHandler`/`AuthenticationEntryPoint` beans in `SecurityConfig` (Spring Security's default `AnonymousAuthenticationFilter` behavior would otherwise return 403 for both cases) and, at the service layer, by throwing `ForbiddenOperationException` (→403) instead of a plain `RuntimeException` (→400) for ownership/authorization denials.
- **Content-Security-Policy** — Every response carries `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'`, set in `SecurityConfig`, as defense-in-depth against XSS/clickjacking on top of the API's existing input validation.
- **Upload size limits** — Base64-encoded photo fields (`profilePicture` on registration/profile-update, complaint photos) are capped at ~2MB decoded (`@Size`-validated at the DTO/service layer, →400 with a field error if exceeded). Independently, `MaxRequestBodySizeFilter` hard-caps the entire request body at 4MB on `/auth/student/register`, `/student/profile`, and `/student/complaints` (→413), so a client can't force unbounded server-side buffering by omitting or understating `Content-Length`.

## 📲 Attendance System

The attendance system supports two verification methods:

1. **WiFi Detection** — Checks if the student is connected to the configured hostel WiFi network (SSID + subnet match)
2. **Geolocation + Biometric** — Falls back to GPS location verification (must be within 50m of hostel) followed by WebAuthn/FIDO2 biometric authentication

Wardens start and stop attendance sessions. Students are notified in real-time via BroadcastChannel API, localStorage events, and browser Notification API.

## 🏢 Room & Department Management

- **Effective Department** — A room's department is `room.departmentOverride` if set, otherwise the default configured for its floor (`FloorDepartment`); if neither is set, the room has no department restriction. This single precedence rule is enforced everywhere a department needs to be checked (registration, manual allocation, bulk allocation).
- **Room Locking** — A student's room is locked the moment a `RoomAllocation` row exists for them (created at registration, or by a Warden/Admin). The student-side allocate endpoint is atomic and race-safe: the "already allocated" check and the write happen under a pessimistic lock on the student's own row, so a concurrent Warden/Admin assignment can never be silently overwritten by the student's own request — whichever wins the race, a staff-made assignment always wins.
- **Bulk Allocation** — Scoped to one building (required) with an optional single-floor filter. Only ever considers students with **zero** existing `RoomAllocation` rows; matches each student's department against the target room's effective department; respects remaining capacity; runs inside one transaction with every candidate room locked up front (ascending by ID, to avoid deadlocking against concurrent single-allocate calls). There is no "Reallocate All" (moving already-housed students) — that's an intentionally unimplemented future feature.
- **Room Numbers** — Stored, editable values, never recalculated from the floor. New rooms default to `001, 002, …` on floor 0, `101, 102, …` on floor 1, etc., but a Warden/Admin can rename any room afterward; editing a number never changes its floor.

## 🎓 Year-Based Hostel Eligibility

Registration requires an explicit academic year (1st–4th, no default). Which hostels are then offered is entirely Admin-configured (`/admin/year-hostels`) — a `(year, building)` pair must exist for a hostel to be selectable, there is no "unconfigured year = anything allowed" fallback. The frontend fetches only the eligible list (`GET /auth/hostels-by-year`), but the backend independently re-validates the submitted `(year, hostel)` combination during registration regardless of what the frontend showed — a direct API call with a disallowed combination is rejected.

## ⚡ Rate Limiting

### Authenticated endpoints (per logged-in user)

`/student/**`, `/warden/**`, `/security/**`, `/admin/**` (excluding `/warden/rooms/**`, which Admin also calls) are rate-limited per authenticated user by `RateLimitInterceptor`. These limits are hardcoded constants in `RateLimiterService`, not environment-configurable:

| Tier | Limit | Applies to |
|------|-------|------------|
| CREATE | 10/hour | POST requests (outpass creation, complaints, etc.) |
| UPDATE | 20/hour | PUT requests (approvals, profile updates, etc.) |
| READ | 200/minute | GET requests |

Rate limit headers (`X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Type`) are included in responses.

### Unauthenticated auth/email endpoints (per client IP, or IP + normalized email)

`/auth/**` is excluded from the interceptor above (there's no logged-in user yet) and instead rate-limited directly in `AuthController`/`AuthService`. These limits *are* configurable via `application.properties` / env vars:

| Endpoint | Limit | Keyed by | Property |
|----------|-------|----------|----------|
| `POST /auth/student/register` | 5/hour | IP | `rate.limit.auth.register` (`RATE_LIMIT_REGISTER`) |
| `POST /auth/forgot-password` | 3/hour | IP + email | `rate.limit.auth.forgot-password` (`RATE_LIMIT_FORGOT_PASSWORD`) |
| `POST /auth/resend-verification` | 3/hour | IP + email | `rate.limit.auth.resend-verification` (`RATE_LIMIT_RESEND_VERIFICATION`) |
| `GET /auth/verify-email` | 20/hour | IP | `rate.limit.auth.verify-email` (`RATE_LIMIT_VERIFY_EMAIL`) |

Window length for all four is `rate.limit.auth.window-seconds` (`RATE_LIMIT_AUTH_WINDOW_SECONDS`, default 3600). Exceeding a limit returns HTTP 429. Client IP is resolved via `server.forward-headers-strategy=native` (trusts Render's edge as the sole proxy in front of the service).

## 🎨 UI/UX

- **Monotone Theme** — Professional dark slate color scheme with accent colors
- **Dark Mode** — Mocha palette (espresso brown + gold-tan accents) with toggle in navbar, persisted to localStorage
- **Responsive Design** — Mobile-first with Bootstrap 5
- **Toast Notifications** — Real-time feedback via React Hot Toast
- **Card-based Layout** — Clean information hierarchy
- **Loading States** — Spinners during async operations
- **Role-based Navigation** — Navbar links adapt to user role

## 🌐 Deployment

The app deploys as three independent services:

| Service | Host | Method |
|---------|------|--------|
| Frontend (React) | **Vercel** | Static Vite build, SPA rewrite via `vercel.json` |
| Backend (Spring Boot) | **Render** | Docker web service via `render.yaml` |
| Database (MySQL) | **Aiven** | Managed MySQL with SSL |

### Frontend (Vercel)

Vercel runs `npm run build` and serves `frontend/dist/`. Set the environment variable:

```env
VITE_API_BASE_URL=https://<your-render-service>.onrender.com/api
```

### Database (Managed MySQL)

Create a managed MySQL instance (Aiven, Railway, PlanetScale, etc.) and apply the schema:

```bash
mysql -h <host> -P <port> -u <user> -p<password> --ssl-mode=REQUIRED <db_name> \
  < backend/db/schema-managed.sql
```

The app uses `spring.jpa.hibernate.ddl-auto=update`, but tables should exist before first boot.

**Upgrading an existing deployment** to a version with the Admin role/room-department/year-eligibility features: `ddl-auto=update` will add the new tables/columns automatically on next boot, **except** the `ENUM` columns on `refresh_tokens.user_type` and `access_logs.role`, which need a one-time manual migration first (Hibernate won't rewrite an existing column's type):
```sql
ALTER TABLE refresh_tokens MODIFY COLUMN user_type ENUM('STUDENT','WARDEN','SECURITY_GUARD','ADMIN') NOT NULL;
ALTER TABLE access_logs MODIFY COLUMN role ENUM('STUDENT','WARDEN','SECURITY_GUARD','ADMIN') NOT NULL;
```
Then bootstrap the first Admin (see Getting Started) and optionally run `backend/db/backfill-room-allocations.sql` / `backend/db/backfill-student-year.sql` — both are manual, reviewable, and safe to skip.

### Backend (Render)

Deploy via the `render.yaml` blueprint or create a Docker web service. Set these environment variables:

| Variable | Description |
|----------|-------------|
| `DB_HOST` | MySQL host |
| `DB_PORT` | MySQL port |
| `DB_NAME` | Database name |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |
| `DB_SSL_MODE` | `REQUIRED` for managed MySQL |
| `JWT_SECRET` | JWT signing secret |
| `CORS_ALLOWED_ORIGINS` | Comma-separated frontend URLs |
| `FRONTEND_URL` | Deployed frontend origin (e.g. `https://hostel-management-mit.vercel.app`) — used to build verification/reset email links. **Must be set in production**; it defaults to `http://localhost:5173` otherwise, which breaks emailed links. |
| `MAIL_HOST` | SMTP host (optional — omit to disable email sending) |
| `MAIL_PORT` | SMTP port, e.g. `587` |
| `MAIL_USERNAME` | SMTP username / from-address |
| `MAIL_PASSWORD` | SMTP password / app password |

`PORT` is injected by Render automatically. Health check path is `/api/health`. If `MAIL_HOST`/`MAIL_USERNAME`/`MAIL_PASSWORD` are left unset, registration/password-reset still work but no emails are sent (a warning is logged on startup); if mail *is* configured but `FRONTEND_URL` is left at its default, a startup error is logged since emailed links would point to `localhost`.

> Render's free tier sleeps after inactivity — first request after idle cold-starts in ~30–50 seconds.

### Local Build (Self-hosted)

```bash
cd backend
./mvnw clean package
java -jar target/portal-0.0.1-SNAPSHOT.jar
```

## ⚙️ Configuration

### Backend (`application.properties`)

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8080` | Server port (overridden by `PORT` env var) |
| `server.forward-headers-strategy` | `native` | Resolves real client IP from `X-Forwarded-For` via the container's trusted-proxy handling (for auth-endpoint rate limiting) |
| `spring.jpa.hibernate.ddl-auto` | `update` | Schema update strategy |
| `jwt.access-token-expiration` | `86400000` (24h) | Access token TTL |
| `jwt.refresh-token-expiration` | `604800000` (7d) | Refresh token TTL |
| `app.frontend.url` | `http://localhost:5173` | Base URL used to build links in verification/reset emails (`FRONTEND_URL`) — **must** be overridden in production |
| `rate.limit.auth.forgot-password` | `3` | Forgot-password requests per window, per IP+email (`RATE_LIMIT_FORGOT_PASSWORD`) |
| `rate.limit.auth.resend-verification` | `3` | Resend-verification requests per window, per IP+email (`RATE_LIMIT_RESEND_VERIFICATION`) |
| `rate.limit.auth.register` | `5` | Registrations per window, per IP (`RATE_LIMIT_REGISTER`) |
| `rate.limit.auth.verify-email` | `20` | Verify-email requests per window, per IP (`RATE_LIMIT_VERIFY_EMAIL`) |
| `rate.limit.auth.window-seconds` | `3600` | Window length for the four properties above (`RATE_LIMIT_AUTH_WINDOW_SECONDS`) |
| `token.cleanup.cron` | `0 0 3 * * *` | Daily cron for purging expired email-verification/password-reset/refresh tokens (`TOKEN_CLEANUP_CRON`) |
| Timezone | `Asia/Kolkata` | Server timezone |

> Note: the authenticated-user CREATE/UPDATE/READ limits in the Rate Limiting section above are hardcoded constants in `RateLimiterService`, **not** properties — there is no `rate.limit.create`/`update`/`read` property to override.

### Frontend (`vite.config.js`)

- Dev server on port 5173
- API proxy: `/api` → `http://localhost:8080`

## 📄 SQL Files Reference

| File | Purpose |
|------|---------|
| `backend/src/main/resources/schema.sql` | Full local schema (20 tables + a daily `cleanup_expired_tokens` MySQL EVENT for `refresh_tokens`/`tokens`; the newer `password_reset_tokens`/`email_verification_tokens` are cleaned up in application code instead — see `TokenCleanupScheduler`) |
| `backend/src/main/resources/schema-cloud.sql` | Cloud-safe schema (no events/procedures) |
| `backend/db/schema-managed.sql` | Managed MySQL with indexes + seed data |
| `backend/src/main/resources/seed-data.sql` | Sample data for local development, including the commented Admin bootstrap template |
| `backend/src/main/resources/seed-cloud.sql` | Sample data for cloud, including the commented Admin bootstrap template |
| `backend/db/backfill-room-allocations.sql` | Manual, one-time, idempotent: creates `RoomAllocation` rows for existing students whose `hostel`/`room_number` exactly match a real building/room, so they become "locked" like new registrations. Never writes `students.hostel`/`room_number`; unmatched students are reported for manual reconciliation, not guessed. Not auto-run. |
| `backend/db/backfill-student-year.sql` | Optional manual aid: attempts to derive `students.year` from a 4-digit admission-year prefix in `roll_no`. Explicitly caveated as unverified against real roll-number formats — preview before running; never guess-clamps an out-of-range result. Not auto-run. |
| `backend/db/migrate-outpass-reason-length.sql` | **Pending production migration.** Widens `outpasses.reason` from `VARCHAR(50)` (an artifact of an old `ddl-auto=update` run) to `VARCHAR(500)` to match `OutpassRequest`'s validated length — without it, a DTO-valid reason over 50 characters fails at insert time with a raw truncation error. Idempotent (`MODIFY COLUMN` to an identical definition is a no-op) and non-destructive (no data is dropped or truncated). Already captured in `schema.sql`/`schema-cloud.sql`/`schema-managed.sql` for fresh deployments — this script is only needed to bring an existing production database in line. Not auto-run; requires explicit execution against production. |
| `backend/src/main/resources/quick-start.sql` | Drop and recreate database (destructive) |
| `backend/src/main/resources/reset.sql` | Drop database entirely (destructive) |

## 📝 License

This project is created for educational purposes.

## 👥 Author

**Yuvaraj B** 

**Tharun P**

**Fathima Fahmiya S**

**Bharath**

**Nivriti Muthuvairan**

**Kaushal N**



