# 🎓 Outpass Portal

A comprehensive digital outpass management system for educational institutions, enabling students to request outpasses and administrators to manage them efficiently.

## 📋 Overview

The Outpass Portal is a full-stack web application that streamlines the process of requesting, approving, and tracking student outpasses. It provides role-based access control for Students, Wardens, and Security Guards with real-time notifications and approval workflows.

## ✨ Features

### 👨‍🎓 Student Features
- **Create Outpass Requests** - Submit outpass requests with reason, destination, and time details
- **Track Status** - Real-time tracking of outpass approval status
- **View History** - Complete history of all outpass requests with filtering options
- **Profile Management** - Update personal information and contact details
- **Dashboard Statistics** - View pending, approved, and rejected outpass counts
- **Late Return Tracking** - Automatic flagging of late returns

### 👔 Warden Features
- **Approve/Reject Outpasses** - Review and process student outpass requests
- **Add Comments** - Provide feedback or reasons for approval/rejection
- **Dashboard Analytics** - View statistics of pending and processed outpasses
- **Student Management** - View student profiles and outpass history
- **Bulk Actions** - Efficiently manage multiple outpass requests

### 🔒 Security Guard Features
- **Verify Outpasses** - Validate approved outpasses at exit/entry points
- **Mark Exit/Entry** - Record actual exit and return times
- **View Active Outpasses** - List of currently active approved outpasses
- **Dashboard Overview** - Real-time status of students outside campus

## 🛠️ Tech Stack

### Frontend
- **React 19.2.0** - Modern UI library
- **React Router DOM 7.13.1** - Client-side routing
- **Vite** - Fast build tool and dev server
- **Bootstrap 5.3.8** - Responsive design framework
- **Font Awesome 7.2.0** - Professional icon library
- **Axios 1.13.6** - HTTP client
- **React Hot Toast 2.6.0** - Toast notifications
- **date-fns 4.1.0** - Date formatting and manipulation

### Backend
- **Spring Boot 4.0.3** - Java backend framework
- **Spring Security** - Authentication and authorization
- **Spring Data JPA** - Database ORM
- **MySQL** - Relational database
- **JWT (jjwt 0.12.6)** - Token-based authentication
- **Lombok** - Reduce boilerplate code
- **Java 21** - Latest LTS Java version

## 🏗️ Architecture

```
outpass-portal/
├── frontend/                 # React frontend application
│   ├── src/
│   │   ├── components/      # Reusable UI components
│   │   │   └── common/      # Shared components (Navbar, Modals, etc.)
│   │   ├── context/         # React Context (AuthContext)
│   │   ├── pages/           # Route-based page components
│   │   │   ├── auth/        # Login, Register
│   │   │   ├── student/     # Student dashboard, create outpass, history
│   │   │   ├── warden/      # Warden dashboard, pending approvals
│   │   │   └── security/    # Security guard dashboard
│   │   ├── routes/          # Route definitions and PrivateRoute
│   │   ├── services/        # API service layer (axios)
│   │   └── utils/           # Constants and utilities
│   └── vite.config.js       # Vite configuration
│
└── backend/                 # Spring Boot backend
    ├── src/main/java/com/outpass/portal/
    │   ├── config/          # Security, CORS configuration
    │   ├── controller/      # REST API endpoints
    │   ├── dto/             # Data Transfer Objects
    │   ├── model/           # JPA entities
    │   ├── repository/      # Database repositories
    │   ├── security/        # JWT filters, authentication
    │   ├── service/         # Business logic layer
    │   └── util/            # Utility classes
    └── src/main/resources/
        ├── application.properties
        └── schema.sql       # Database schema
```

## 🚀 Getting Started

### Prerequisites
- Java 21 or higher
- Node.js 18+ and npm
- MySQL 8.0+
- Maven 3.8+

### Backend Setup

1. **Clone the repository**
```bash
git clone <https://github.com/yuvii-b/outpass-portal>
cd outpass-portal/backend
```

2. **Configure MySQL Database**
```bash
mysql -u root -p
CREATE DATABASE outpass_db;
```

3. **Update application.properties**
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/outpass_db
spring.datasource.username=your_username
spring.datasource.password=your_password
```

4. **Run the backend**
```bash
./mvnw spring-boot:run
```
Backend will start on `http://localhost:8080`

### Frontend Setup

1. **Navigate to frontend directory**
```bash
cd outpass-portal/frontend
```

2. **Install dependencies**
```bash
npm install
```

3. **Configure environment variables**
Create a `.env` file:
```env
VITE_API_BASE_URL=http://localhost:8080/api
```

4. **Run development server**
```bash
npm run dev
```
Frontend will start on `http://localhost:5173`

## 🔐 Authentication

The application uses JWT-based authentication with the following roles:

- **STUDENT** - Students can create and manage their outpass requests
- **WARDEN** - Wardens can approve/reject outpass requests
- **SECURITY_GUARD** - Security guards can verify and mark exit/entry

### Default Credentials for Testing
Create users via the registration page or use SQL:
```sql
-- Password should be bcrypt-encoded
INSERT INTO users (email, password, role, name, phone, department)
VALUES ('student@college.edu', '$2a$10$...', 'STUDENT', 'John Doe', '1234567890', 'CSE');
```

## 📡 API Endpoints

### Authentication
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login and get JWT token

### Student APIs
- `GET /api/student/profile` - Get student profile
- `PUT /api/student/profile` - Update profile
- `POST /api/student/outpass` - Create outpass request
- `GET /api/student/outpass/history` - Get outpass history
- `GET /api/student/outpass/stats` - Get statistics

### Warden APIs
- `GET /api/warden/outpasses/pending` - Get pending outpasses
- `PUT /api/warden/outpasses/{id}/approve` - Approve outpass
- `PUT /api/warden/outpasses/{id}/reject` - Reject outpass

### Security APIs
- `GET /api/security/outpasses/active` - Get active outpasses
- `PUT /api/security/outpasses/{id}/mark-exit` - Mark exit time
- `PUT /api/security/outpasses/{id}/mark-entry` - Mark entry time

## 🎨 UI/UX Features

- **Monotone Theme** - Professional dark slate color scheme (#1a202c, #2d3748)
- **Responsive Design** - Mobile-first approach with Bootstrap
- **Font Awesome Icons** - Consistent, scalable icon system
- **Enhanced Date Picker** - User-friendly date/time selection
- **Toast Notifications** - Real-time feedback for user actions
- **Loading States** - Clear feedback during async operations
- **Card-based Layout** - Organized information presentation

## 🌐 Deployment

The app is deployed as three independent pieces:

| Piece | Host | How |
|-------|------|-----|
| Frontend (React) | **Vercel** | Static Vite build, SPA rewrite ([frontend/vercel.json](frontend/vercel.json)) |
| Backend (Spring Boot) | **Render** | Docker web service ([backend/Dockerfile](backend/Dockerfile)) |
| Database (MySQL) | **External managed MySQL** | Aiven / Railway / Clever Cloud |

### Frontend (Vercel)
Vercel runs `npm run build` and serves `frontend/dist/`. Set the backend URL in the
Vercel project's environment variables and redeploy:
```env
VITE_API_BASE_URL=https://<your-render-service>.onrender.com/api
```

### Database (external managed MySQL)
Render has no managed MySQL, so the database lives on a managed provider.

1. Create a MySQL instance and an empty database in the provider console.
2. Apply the managed-friendly schema **once** (it omits `CREATE DATABASE`, `SET GLOBAL`,
   and the events/procedures/views a managed DB can't run):
   ```bash
   mysql -h <host> -P <port> -u <user> -p<pass> --ssl-mode=REQUIRED <db_name> \
     < backend/db/schema-managed.sql
   ```
   The app runs with `spring.jpa.hibernate.ddl-auto=validate`, so the tables must exist
   and match the entities before the backend will start.

### Backend (Render, Docker)
Deploy via the [render.yaml](render.yaml) blueprint (or create a Docker web service pointing
at `backend/Dockerfile`). Set these environment variables in the Render dashboard:

| Variable | Notes |
|----------|-------|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | from the managed MySQL console |
| `DB_SSL_MODE` | `REQUIRED` (managed MySQL enforces TLS) |
| `JWT_SECRET` | signing secret for JWTs |
| `CORS_ALLOWED_ORIGINS` | comma-separated, e.g. `https://outpass-portal.vercel.app` |

`PORT` is injected by Render automatically; the app binds to it. The health check path is
`/api/health`.

> **Note:** Render's free web service sleeps after inactivity, so the first request after an
> idle period cold-starts in ~30–50s.

### Local build (self-hosted alternative)
```bash
cd backend
./mvnw clean package
java -jar target/portal-0.0.1-SNAPSHOT.jar
```

## 📊 Database Schema

### Main Tables
- **users** - User accounts (students, wardens, security guards)
- **outpasses** - Outpass requests and their status
- **outpass_history** - Audit trail of status changes

For complete schema, see [schema.sql](backend/src/main/resources/schema.sql)

## 📝 Configuration Files

- `frontend/vite.config.js` - Vite build configuration with base path
- `frontend/.env` - Environment variables for API URL
- `backend/application.properties` - Spring Boot configuration
- `backend/pom.xml` - Maven dependencies and build settings

## 📄 License

This project is created for educational purposes.

## 👥 Authors

- **Yuvaraj B** - Initial work and full-stack development

**Built with ❤️ using React and Spring Boot**
