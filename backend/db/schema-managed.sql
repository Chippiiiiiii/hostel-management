-- =====================================================
-- Outpass Portal - Managed MySQL Schema (Render / Aiven / Railway / Clever Cloud)
-- =====================================================
-- Run this ONCE against your managed MySQL database, e.g.:
--   mysql -h <host> -P <port> -u <user> -p<pass> --ssl-mode=REQUIRED <db_name> < schema-managed.sql
--
-- Differences from src/main/resources/schema.sql (the local/self-hosted version):
--   * No DROP/CREATE DATABASE or USE  -> a managed provider gives you ONE database and
--     no privilege to create/drop databases. Create the DB in the provider console first.
--   * No `SET GLOBAL event_scheduler` / EVENT -> requires SUPER privilege, denied on managed DBs.
--     Expired-token cleanup is handled in application code, so the event is not needed.
--   * No stored procedures / views -> the app uses Spring Data JPA and never calls them.
-- The app runs with spring.jpa.hibernate.ddl-auto=validate, so these tables must exist
-- and match the JPA entities before the backend will start.
-- =====================================================

-- =====================================================
-- TABLE: students
-- =====================================================
CREATE TABLE IF NOT EXISTS students (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    roll_no VARCHAR(20) NOT NULL,
    department VARCHAR(100) NOT NULL,
    year INT NULL,
    hostel VARCHAR(100) NOT NULL,
    room_number VARCHAR(20) NOT NULL,
    contact_number VARCHAR(15) NOT NULL,
    parent_number VARCHAR(15) NOT NULL,
    profile_picture LONGTEXT NULL,
    email_verified BOOLEAN NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_student_email UNIQUE (email),
    CONSTRAINT uk_student_roll_no UNIQUE (roll_no),
    CONSTRAINT chk_student_email CHECK (email REGEXP '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_student_contact CHECK (contact_number REGEXP '^[0-9]{10}$'),
    CONSTRAINT chk_student_parent CHECK (parent_number REGEXP '^[0-9]{10}$'),
    CONSTRAINT chk_student_year CHECK (year IS NULL OR year BETWEEN 1 AND 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_student_email ON students(email);
CREATE INDEX idx_student_roll_no ON students(roll_no);
CREATE INDEX idx_student_hostel ON students(hostel);

-- =====================================================
-- TABLE: wardens
-- =====================================================
CREATE TABLE IF NOT EXISTS wardens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    hostel VARCHAR(100),
    phone VARCHAR(15),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_warden_email UNIQUE (email),
    CONSTRAINT chk_warden_email CHECK (email REGEXP '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_warden_phone CHECK (phone IS NULL OR phone REGEXP '^[0-9]{10}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_warden_email ON wardens(email);
CREATE INDEX idx_warden_hostel ON wardens(hostel);

-- =====================================================
-- TABLE: security_guards
-- =====================================================
CREATE TABLE IF NOT EXISTS security_guards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    hostel VARCHAR(100),
    phone VARCHAR(15),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_security_email UNIQUE (email),
    CONSTRAINT chk_security_email CHECK (email REGEXP '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_security_phone CHECK (phone IS NULL OR phone REGEXP '^[0-9]{10}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_security_email ON security_guards(email);
CREATE INDEX idx_security_hostel ON security_guards(hostel);

-- =====================================================
-- TABLE: admins
-- =====================================================
CREATE TABLE IF NOT EXISTS admins (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(15),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_admin_email UNIQUE (email),
    CONSTRAINT chk_admin_email CHECK (email REGEXP '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_admin_phone CHECK (phone IS NULL OR phone REGEXP '^[0-9]{10}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_admin_email ON admins(email);

-- =====================================================
-- TABLE: outpasses
-- =====================================================
CREATE TABLE IF NOT EXISTS outpasses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    roll_no VARCHAR(20) NOT NULL,
    department VARCHAR(100) NOT NULL,
    hostel VARCHAR(100) NOT NULL,
    room_number VARCHAR(20) NOT NULL,
    date DATETIME NOT NULL,
    return_date DATETIME NOT NULL,
    num_of_days INT NOT NULL,
    visit_place VARCHAR(255) NOT NULL,
    contact_number VARCHAR(15) NOT NULL,
    parent_number VARCHAR(15) NOT NULL,
    status ENUM('PENDING', 'APPROVED', 'DECLINED', 'DEPARTED', 'COMPLETED', 'OVERDUE') NOT NULL DEFAULT 'PENDING',
    actual_departure_time DATETIME NULL,
    actual_return_time DATETIME NULL,
    departure_verified_by BIGINT NULL,
    return_verified_by BIGINT NULL,
    is_late_return BOOLEAN DEFAULT FALSE,
    decline_reason VARCHAR(500) NULL,
    warden_comments VARCHAR(500) NULL,
    processed_by BIGINT NULL,
    processed_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_outpass_student FOREIGN KEY (student_id)
        REFERENCES students(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    CONSTRAINT chk_outpass_dates CHECK (return_date > date),
    CONSTRAINT chk_outpass_days CHECK (num_of_days > 0 AND num_of_days <= 30),
    CONSTRAINT chk_outpass_contact CHECK (contact_number REGEXP '^[0-9]{10}$'),
    CONSTRAINT chk_outpass_parent CHECK (parent_number REGEXP '^[0-9]{10}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_outpass_student ON outpasses(student_id);
CREATE INDEX idx_outpass_status ON outpasses(status);
CREATE INDEX idx_outpass_date ON outpasses(date);
CREATE INDEX idx_outpass_created ON outpasses(created_at);
CREATE INDEX idx_outpass_hostel ON outpasses(hostel);

-- =====================================================
-- TABLE: refresh_tokens
-- =====================================================
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(512) NOT NULL,
    user_id BIGINT NOT NULL,
    user_type ENUM('STUDENT', 'WARDEN', 'SECURITY_GUARD', 'ADMIN') NOT NULL,
    expiry_date TIMESTAMP NOT NULL,

    CONSTRAINT uk_refresh_token UNIQUE (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_refresh_user ON refresh_tokens(user_id, user_type);
CREATE INDEX idx_refresh_expiry ON refresh_tokens(expiry_date);

-- =====================================================
-- TABLE: tokens (revoked JWT blacklist)
-- =====================================================
CREATE TABLE IF NOT EXISTS tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    jid VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,

    CONSTRAINT uk_token_jid UNIQUE (jid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_revoked_jid ON tokens(jid);
CREATE INDEX idx_revoked_expires ON tokens(expires_at);

-- =====================================================
-- TABLE: password_reset_tokens
-- Pre-existing entity that was missing from this file (schema drift relied on
-- ddl-auto=update). Added here for consistency; no application logic changed.
-- =====================================================
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    user_type VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_password_reset_token UNIQUE (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_password_reset_email ON password_reset_tokens(email);

-- =====================================================
-- TABLE: email_verification_tokens
-- =====================================================
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_email_verification_token UNIQUE (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_email_verification_email ON email_verification_tokens(email);

-- =====================================================
-- TABLE: access_logs
-- =====================================================
CREATE TABLE IF NOT EXISTS access_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    role ENUM('STUDENT', 'WARDEN', 'SECURITY_GUARD', 'ADMIN') NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    method VARCHAR(10) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_access_username (username),
    INDEX idx_access_timestamp (timestamp),
    INDEX idx_access_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- TABLE: buildings
-- =====================================================
CREATE TABLE IF NOT EXISTS buildings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- TABLE: warden_buildings
-- Admin-configured: which buildings a warden is assigned to manage. A warden may be
-- assigned to several buildings; wardens.hostel remains the single "primary" hostel every
-- existing warden-scoped query reads (dashboard, outpass, attendance, complaints, room
-- management). This table is the source of truth for the Admin "manage buildings" UI.
-- =====================================================
CREATE TABLE IF NOT EXISTS warden_buildings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    warden_id BIGINT NOT NULL,
    building_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_warden_building_warden FOREIGN KEY (warden_id)
        REFERENCES wardens(id) ON DELETE CASCADE,
    CONSTRAINT fk_warden_building_building FOREIGN KEY (building_id)
        REFERENCES buildings(id) ON DELETE CASCADE,
    CONSTRAINT uk_warden_building UNIQUE (warden_id, building_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_warden_building_warden ON warden_buildings(warden_id);

-- =====================================================
-- TABLE: rooms
-- =====================================================
CREATE TABLE IF NOT EXISTS rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    building_id BIGINT NOT NULL,
    floor_number INT NOT NULL,
    room_number VARCHAR(20) NOT NULL,
    max_members INT NOT NULL DEFAULT 6,
    department_override VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_room_building FOREIGN KEY (building_id)
        REFERENCES buildings(id) ON DELETE CASCADE,
    CONSTRAINT uk_room_building_number UNIQUE (building_id, room_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_room_building ON rooms(building_id);
CREATE INDEX idx_room_floor ON rooms(building_id, floor_number);

-- =====================================================
-- TABLE: floor_departments
-- Default department per (building, floor). A room's own department_override (above)
-- takes precedence over this when both are set.
-- =====================================================
CREATE TABLE IF NOT EXISTS floor_departments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    building_id BIGINT NOT NULL,
    floor_number INT NOT NULL,
    department VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_floor_department_building FOREIGN KEY (building_id)
        REFERENCES buildings(id) ON DELETE CASCADE,
    CONSTRAINT uk_floor_department_building_floor UNIQUE (building_id, floor_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_floor_department_building ON floor_departments(building_id);

-- =====================================================
-- TABLE: year_hostel_eligibility
-- Admin-configured: which buildings/hostels a student in a given academic year (1-4) may
-- select at registration. Absence of a (year, building) row means "not allowed".
-- =====================================================
CREATE TABLE IF NOT EXISTS year_hostel_eligibility (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    year INT NOT NULL,
    building_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_year_hostel_building FOREIGN KEY (building_id)
        REFERENCES buildings(id) ON DELETE CASCADE,
    CONSTRAINT uk_year_hostel UNIQUE (year, building_id),
    CONSTRAINT chk_year_hostel_year CHECK (year BETWEEN 1 AND 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_year_hostel_year ON year_hostel_eligibility(year);

-- =====================================================
-- TABLE: room_allocations
-- =====================================================
CREATE TABLE IF NOT EXISTS room_allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    student_id BIGINT NULL,
    student_name VARCHAR(100) NOT NULL,
    student_roll_no VARCHAR(20) NOT NULL,
    student_department VARCHAR(100) NOT NULL,
    student_email VARCHAR(255) NOT NULL,
    allocated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_allocation_room FOREIGN KEY (room_id)
        REFERENCES rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_allocation_student FOREIGN KEY (student_id)
        REFERENCES students(id) ON DELETE SET NULL,
    CONSTRAINT uk_allocation_email UNIQUE (student_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_allocation_room ON room_allocations(room_id);
CREATE INDEX idx_allocation_student ON room_allocations(student_id);

-- =====================================================
-- TABLE: attendance_sessions
-- =====================================================
-- building_id is nullable at the schema level to accommodate historical rows created
-- before this column existed (see db/backfill-attendance-room-building.sql). Every new
-- session created by AttendanceService.startSession is required, at the Java/service
-- layer, to carry a real building -- see AttendanceSession.java. Tightening this column
-- to NOT NULL is a deliberately separate follow-up migration, run only after the
-- backfill's "unresolved sessions" report (in the backfill script) is empty or reviewed
-- and accepted; this file intentionally does not include that follow-up migration.
CREATE TABLE IF NOT EXISTS attendance_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    building_id BIGINT NULL,
    started_by BIGINT NOT NULL,
    status ENUM('ACTIVE', 'CLOSED') NOT NULL DEFAULT 'ACTIVE',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    stopped_at TIMESTAMP NULL,

    CONSTRAINT fk_session_warden FOREIGN KEY (started_by)
        REFERENCES wardens(id) ON DELETE CASCADE,
    -- RESTRICT (not CASCADE): removing a building must never silently delete the
    -- historical attendance record of who was present while it existed.
    CONSTRAINT fk_session_building FOREIGN KEY (building_id)
        REFERENCES buildings(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_session_building_status ON attendance_sessions(building_id, status);
CREATE INDEX idx_session_warden ON attendance_sessions(started_by);

-- =====================================================
-- TABLE: attendance_records
-- =====================================================
CREATE TABLE IF NOT EXISTS attendance_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    date DATE NOT NULL,
    time VARCHAR(20) NOT NULL,
    method ENUM('WIFI', 'GEO_BIOMETRIC') NOT NULL,
    status ENUM('PRESENT', 'ABSENT') NOT NULL DEFAULT 'PRESENT',
    latitude DOUBLE NULL,
    longitude DOUBLE NULL,
    distance INT NULL,
    marked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_record_session FOREIGN KEY (session_id)
        REFERENCES attendance_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_record_student FOREIGN KEY (student_id)
        REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT uk_record_student_date UNIQUE (student_id, date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_record_session ON attendance_records(session_id);
CREATE INDEX idx_record_student ON attendance_records(student_id);
CREATE INDEX idx_record_date ON attendance_records(date);

-- =====================================================
-- TABLE: room_config
-- =====================================================
-- building_id NULL is the ADMIN-set campus-wide default template that
-- BuildingConfigService/RoomService.addBuilding seed new buildings from -- it is read as
-- a fallback but never written by a warden-facing endpoint (see backend/AGENTS.md). MySQL
-- does not treat NULLs as equal within a composite unique index, so uk_config_key_building
-- cannot by itself prevent a second NULL-building default row for the same key from being
-- inserted (INSERT IGNORE does NOT dedupe on the nullable column here -- verified: 3x
-- INSERT IGNORE against a NULL-building row inserts 3 rows, not 1). The seed below instead
-- guards each default row with an explicit NOT EXISTS check on (config_key, building_id IS
-- NULL) so re-running this file never creates a duplicate default row.
CREATE TABLE IF NOT EXISTS room_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(50) NOT NULL,
    config_value VARCHAR(500) NOT NULL,
    building_id BIGINT NULL,

    CONSTRAINT uk_config_key_building UNIQUE (config_key, building_id),
    CONSTRAINT fk_roomconfig_building FOREIGN KEY (building_id)
        REFERENCES buildings(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The campus-wide admin default template (building_id NULL). Guarded with an explicit
-- NOT EXISTS (config_key, building_id IS NULL) check per row -- INSERT IGNORE alone does
-- not dedupe NULL-building rows against the composite unique index (see comment above).
INSERT INTO room_config (config_key, config_value, building_id)
SELECT * FROM (
    SELECT 'max_rooms_per_floor' AS config_key, '10' AS config_value, NULL AS building_id
    UNION ALL SELECT 'max_members_per_room', '6', NULL
    UNION ALL SELECT 'wifi_allowed_subnets', '192.168.0.0/16,10.0.0.0/8,172.16.0.0/12', NULL
    UNION ALL SELECT 'hostel_latitude', '12.8231', NULL
    UNION ALL SELECT 'hostel_longitude', '80.0444', NULL
    UNION ALL SELECT 'hostel_radius', '50', NULL
) AS defaults
WHERE NOT EXISTS (
    SELECT 1 FROM room_config rc
    WHERE rc.config_key = defaults.config_key AND rc.building_id IS NULL
);

INSERT IGNORE INTO buildings (id, name, type) VALUES (1, 'Building A', 'NRI'), (2, 'Building B', 'NORMAL');

-- Fresh-deploy seed buildings get their own per-building config rows too, matching what
-- db/backfill-attendance-room-building.sql would produce for a pre-existing database --
-- a brand-new local/dev setup should not need to run the backfill just to have working
-- attendance/room config for the two demo buildings seeded above.
INSERT IGNORE INTO room_config (config_key, config_value, building_id)
SELECT rc.config_key, rc.config_value, b.id
FROM room_config rc
CROSS JOIN buildings b
WHERE rc.building_id IS NULL AND b.id IN (1, 2);

-- Building A (id=1): 3 floors, 10 rooms each
INSERT IGNORE INTO rooms (building_id, floor_number, room_number, max_members) VALUES
(1, 1, '101', 6), (1, 1, '102', 6), (1, 1, '103', 6), (1, 1, '104', 6), (1, 1, '105', 6),
(1, 1, '106', 6), (1, 1, '107', 6), (1, 1, '108', 6), (1, 1, '109', 6), (1, 1, '110', 6),
(1, 2, '201', 6), (1, 2, '202', 6), (1, 2, '203', 6), (1, 2, '204', 6), (1, 2, '205', 6),
(1, 2, '206', 6), (1, 2, '207', 6), (1, 2, '208', 6), (1, 2, '209', 6), (1, 2, '210', 6),
(1, 3, '301', 6), (1, 3, '302', 6), (1, 3, '303', 6), (1, 3, '304', 6), (1, 3, '305', 6),
(1, 3, '306', 6), (1, 3, '307', 6), (1, 3, '308', 6), (1, 3, '309', 6), (1, 3, '310', 6);

-- Building B (id=2): 3 floors, 10 rooms each
INSERT IGNORE INTO rooms (building_id, floor_number, room_number, max_members) VALUES
(2, 1, '101', 6), (2, 1, '102', 6), (2, 1, '103', 6), (2, 1, '104', 6), (2, 1, '105', 6),
(2, 1, '106', 6), (2, 1, '107', 6), (2, 1, '108', 6), (2, 1, '109', 6), (2, 1, '110', 6),
(2, 2, '201', 6), (2, 2, '202', 6), (2, 2, '203', 6), (2, 2, '204', 6), (2, 2, '205', 6),
(2, 2, '206', 6), (2, 2, '207', 6), (2, 2, '208', 6), (2, 2, '209', 6), (2, 2, '210', 6),
(2, 3, '301', 6), (2, 3, '302', 6), (2, 3, '303', 6), (2, 3, '304', 6), (2, 3, '305', 6),
(2, 3, '306', 6), (2, 3, '307', 6), (2, 3, '308', 6), (2, 3, '309', 6), (2, 3, '310', 6);

-- =====================================================
-- SAMPLE DATA (optional - remove for a clean production DB)
-- Passwords are bcrypt hashes of the original seed credentials.
-- =====================================================
INSERT IGNORE INTO wardens (name, email, password_hash, hostel, phone) VALUES
('Ram', 'ram@mit.edu', '$2a$10$9Vw3Fdy7DyCXmAW7XG4dAO2lVc4Vayng/Xow2nAlMP4HTpzn/x9bW', 'Building A', '9876543210'),
('Rajesh', 'rajesh@mit.edu', '$2a$10$u039FG6i7pE.nEjJtiI3O.dGZy2KhT18q0VOfdfnb7Oon.Ob9OMtu', 'Building B', '9876543211');

INSERT IGNORE INTO security_guards (name, email, password_hash, hostel, phone) VALUES
('muthu', 'muthu@mit.edu', '$2a$10$xhYbBcJFal8ildwL4OvvYOynu9RW/G7PunB9JiRmYagIM5s.x50G6', 'Building A', '9876543212'),
('somu', 'somu@mit.edu', '$2a$10$Rq8cGIg4XvhQBVw/3Ui2z.afZJvr6LchIzACM2sOwCHHMDIkuKQP6', 'Building B', '9876543213');

INSERT IGNORE INTO students (name, email, password_hash, roll_no, department, hostel, room_number, contact_number, parent_number) VALUES
('Yuvi', 'yuvi@mit.edu', '$2a$10$H3Ag5JR2uJDmBLCLY1NOSeLsHKcNvMmBNQlYPMsZbSqBMGojXWhnK', '2024503541', 'CT', 'Building A', '101', '9876543214', '9876543215'),
('Aravinth', 'arvi@mit.edu', '$2a$10$oE5clQ8xgcRrCzvcUabJoeupjQiNHr7MBR.n2ZQD0F0uJABDoJAa.', '2024503001', 'CT', 'Building B', '201', '9876543216', '9876543217');

-- BOOTSTRAP ADMIN -- there is no self-registration endpoint for ADMIN accounts by design.
-- Sample seed credentials (change the password after first login in any real deployment):
-- admin1@mit.edu / admin123
INSERT IGNORE INTO admins (name, email, password_hash, phone) VALUES
('Admin', 'admin1@mit.edu', '$2a$10$xzBXs6azDR.5O4ETxPT5he5JLTdTqwcsJgmlDi31b24y06WLbmUgy', NULL);
