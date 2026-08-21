# 🏨 Hostel Management System

A comprehensive hostel management platform for educational institutions — handling student outpasses, attendance tracking, room allocation, complaint management, and announcements with role-based access for Students, Wardens, and Security Guards.

**🌐 Live:** [hostel-management-mit.vercel.app](https://hostel-management-mit.vercel.app)

## 📋 Overview

The Hostel Management System digitizes day-to-day hostel operations. Students request outpasses, mark attendance via WiFi or geolocation + biometric verification, file complaints, and view announcements. Wardens manage approvals, monitor attendance, allocate rooms, and respond to complaints. Security guards verify departures and returns at hostel gates. The system provides real-time notifications, risk assessment for frequent outpass users, and exportable attendance reports.

## ✨ Features

### 👨‍🎓 Student Features
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
- **Room Management** — Add/remove buildings, floors, rooms; allocate students; configure capacity; toggle building type (Regular/NRI) and gender (Boys/Girls)
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
│   │   ├── context/
│   │   │   └── AuthContext.jsx        # Authentication context provider
│   │   ├── hooks/
│   │   │   ├── useAttendanceAlert.js  # Attendance session polling + notifications
│   │   │   └── useOutpassNotifications.js # Outpass status change alerts
│   │   ├── pages/
│   │   │   ├── auth/                  # Login, Register, ForgotPassword
│   │   │   ├── student/               # Student dashboard, outpass, attendance, complaints
│   │   │   ├── warden/                # Warden dashboard, management pages
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
│   │   │   └── roomService.js         #   Room/building management API
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
│   │   │   ├── SecurityGuardController.java
│   │   │   └── HealthController.java
│   │   ├── dto/                       # Request/Response DTOs
│   │   │   ├── request/
│   │   │   └── response/
│   │   ├── model/
│   │   │   ├── entity/                # JPA entities (15 tables)
│   │   │   └── enums/                 # Role, OutpassStatus, ComplaintCategory, etc.
│   │   ├── repository/                # Spring Data JPA repositories
│   │   ├── security/                  # JWT provider, auth filter, UserPrincipal
│   │   ├── service/                   # Business logic layer
│   │   ├── interceptor/               # Rate limit interceptor
│   │   ├── exception/                 # Global exception handler
│   │   └── util/                      # Rate limiter, subnet utils
│   ├── src/main/resources/
│   │   ├── application.properties     # App config with env-var overrides
│   │   ├── schema.sql                 # Full local schema
│   │   ├── schema-cloud.sql           # Cloud-safe schema (no events)
│   │   ├── seed-data.sql              # Sample data (local)
│   │   └── seed-cloud.sql             # Sample data (cloud)
│   ├── db/
│   │   └── schema-managed.sql         # Managed MySQL schema with indexes
│   └── Dockerfile                     # Multi-stage Docker build
│
├── render.yaml                        # Render deployment blueprint
└── README.md
```

## 📊 Database Schema

### Entity Relationship

| Entity | Table | Description |
|--------|-------|-------------|
| **Student** | `students` | Student accounts with profile, hostel, room details |
| **Warden** | `wardens` | Warden accounts assigned to specific hostels |
| **SecurityGuard** | `security_guards` | Security guard accounts assigned to hostels |
| **Outpass** | `outpasses` | Outpass requests with full lifecycle tracking |
| **Building** | `buildings` | Hostel buildings (type: Regular/NRI, gender: Boy/Girl) |
| **Room** | `rooms` | Individual rooms within buildings |
| **RoomAllocation** | `room_allocations` | Student-to-room assignments |
| **RoomConfig** | `room_config` | Key-value settings for room management |
| **AttendanceSession** | `attendance_sessions` | Warden-initiated attendance windows |
| **AttendanceRecord** | `attendance_records` | Individual attendance marks |
| **Complaint** | `complaints` | Student complaints with category and status |
| **Announcement** | `announcements` | Warden-posted notices |
| **RefreshToken** | `refresh_tokens` | JWT refresh token storage |
| **Token** | `tokens` | Revoked JWT blacklist |
| **PasswordResetToken** | `password_reset_tokens` | Password reset flow tokens |
| **AccessLog** | `access_logs` | API access audit trail |

### Key Enums

| Enum | Values |
|------|--------|
| Role | `STUDENT`, `WARDEN`, `SECURITY_GUARD` |
| OutpassStatus | `PENDING`, `APPROVED`, `DECLINED`, `DEPARTED`, `COMPLETED`, `OVERDUE` |
| ComplaintCategory | `PLUMBING`, `ELECTRICAL`, `CLEANLINESS`, `FURNITURE`, `INTERNET`, `NOISE`, `OTHER` |
| ComplaintStatus | `PENDING`, `IN_PROGRESS`, `RESOLVED`, `REJECTED` |
| AttendanceMethod | `WIFI`, `GEO_BIOMETRIC` |

## 📡 API Endpoints

All endpoints are prefixed with `/api`.

### Authentication (`/auth`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/auth/student/register` | Register a student account |
| POST | `/auth/student/login` | Student login |
| POST | `/auth/warden/login` | Warden login |
| POST | `/auth/security/login` | Security guard login |
| POST | `/auth/refresh` | Refresh JWT access token |
| POST | `/auth/logout` | Invalidate refresh tokens |
| POST | `/auth/forgot-password` | Request password reset |
| POST | `/auth/reset-password` | Reset password with token |
| GET | `/auth/buildings` | Public building list (for registration) |

### Student (`/student`) — requires `ROLE_STUDENT`

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/student/profile` | Get student profile |
| PUT | `/student/profile` | Update profile |
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
| POST | `/student/rooms/allocate` | Self-allocate to a room |
| GET | `/student/rooms/roommates` | Get roommates |
| POST | `/student/complaints` | Submit a complaint |
| GET | `/student/complaints` | Get own complaints |
| GET | `/student/announcements` | Get announcements |

### Warden (`/warden`) — requires `ROLE_WARDEN`

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
| PUT | `/warden/rooms/{roomId}/max-members` | Update room capacity |
| POST | `/warden/rooms/buildings/{id}/floors` | Add floor |
| DELETE | `/warden/rooms/buildings/{id}/floors/{floor}` | Remove floor |
| POST | `/warden/rooms/buildings/{id}/floors/{floor}/rooms` | Add room |
| DELETE | `/warden/rooms/buildings/{id}/floors/{floor}/rooms/last` | Remove last room |
| GET | `/warden/rooms/allocations` | All room allocations |
| POST | `/warden/rooms/{roomId}/allocate` | Allocate student to room |
| DELETE | `/warden/rooms/allocations/{email}` | Remove allocation |
| GET | `/warden/students` | List all students |
| GET | `/warden/complaints` | Get complaints (optional status filter) |
| GET | `/warden/complaints/stats` | Complaint statistics |
| PUT | `/warden/complaints/{id}` | Update complaint status/response |
| GET | `/warden/announcements` | Get announcements |
| POST | `/warden/announcements` | Create announcement |
| DELETE | `/warden/announcements/{id}` | Delete announcement |

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

5. **Create a `.env` file** in `backend/`
   ```env
   DB_HOST=localhost
   DB_PORT=3306
   DB_NAME=outpass_portal
   DB_USERNAME=root
   DB_PASSWORD=your_password
   JWT_SECRET=your-secret-key-at-least-32-characters-long
   CORS_ALLOWED_ORIGINS=http://localhost:5173
   ```

6. **Run the backend**
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
| Warden | warden1@mit.edu | warden123 |
| Security Guard | security1@mit.edu | security123 |
| Student | student1@mit.edu | student123 |

## 🔐 Authentication

The app uses JWT-based authentication with access + refresh token flow:

- **Access Token** — 24-hour expiry, sent as `Authorization: Bearer <token>` header
- **Refresh Token** — 7-day expiry, stored in localStorage, auto-refreshed on 401 responses
- **Role-based Access** — Routes and API endpoints are protected by role (`STUDENT`, `WARDEN`, `SECURITY_GUARD`)
- **Password Reset** — Token-based password reset flow via email

## 📲 Attendance System

The attendance system supports two verification methods:

1. **WiFi Detection** — Checks if the student is connected to the configured hostel WiFi network (SSID + subnet match)
2. **Geolocation + Biometric** — Falls back to GPS location verification (must be within 50m of hostel) followed by WebAuthn/FIDO2 biometric authentication

Wardens start and stop attendance sessions. Students are notified in real-time via BroadcastChannel API, localStorage events, and browser Notification API.

## ⚡ Rate Limiting

The backend enforces per-user rate limits:

| Tier | Limit | Applies to |
|------|-------|------------|
| CREATE | 10/hour | POST requests (outpass creation, complaints, etc.) |
| UPDATE | 20/hour | PUT requests (approvals, profile updates, etc.) |
| READ | 200/minute | GET requests |

Rate limit headers (`X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Type`) are included in responses.

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

`PORT` is injected by Render automatically. Health check path is `/api/health`.

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
| `spring.jpa.hibernate.ddl-auto` | `update` | Schema update strategy |
| `jwt.accessTokenExpiration` | `86400000` (24h) | Access token TTL |
| `jwt.refreshTokenExpiration` | `604800000` (7d) | Refresh token TTL |
| `rate.limit.create` | `10` | POST requests per hour |
| `rate.limit.update` | `20` | PUT requests per hour |
| `rate.limit.read` | `200` | GET requests per minute |
| Timezone | `Asia/Kolkata` | Server timezone |

### Frontend (`vite.config.js`)

- Dev server on port 5173
- API proxy: `/api` → `http://localhost:8080`

## 📄 SQL Files Reference

| File | Purpose |
|------|---------|
| `backend/src/main/resources/schema.sql` | Full local schema (17 tables + events) |
| `backend/src/main/resources/schema-cloud.sql` | Cloud-safe schema (no events/procedures) |
| `backend/db/schema-managed.sql` | Managed MySQL with indexes + seed data |
| `backend/src/main/resources/seed-data.sql` | Sample data for local development |
| `backend/src/main/resources/seed-cloud.sql` | Sample data for cloud |
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



