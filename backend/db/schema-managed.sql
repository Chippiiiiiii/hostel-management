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
    CONSTRAINT chk_student_parent CHECK (parent_number REGEXP '^[0-9]{10}$')
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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_security_email UNIQUE (email),
    CONSTRAINT chk_security_email CHECK (email REGEXP '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_security_phone CHECK (phone IS NULL OR phone REGEXP '^[0-9]{10}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_security_email ON security_guards(email);
CREATE INDEX idx_security_hostel ON security_guards(hostel);

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
    user_type ENUM('STUDENT', 'WARDEN', 'SECURITY_GUARD') NOT NULL,
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
    role ENUM('STUDENT', 'WARDEN', 'SECURITY_GUARD') NOT NULL,
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
-- TABLE: rooms
-- =====================================================
CREATE TABLE IF NOT EXISTS rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    building_id BIGINT NOT NULL,
    floor_number INT NOT NULL,
    room_number VARCHAR(20) NOT NULL,
    max_members INT NOT NULL DEFAULT 6,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_room_building FOREIGN KEY (building_id)
        REFERENCES buildings(id) ON DELETE CASCADE,
    CONSTRAINT uk_room_building_number UNIQUE (building_id, room_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_room_building ON rooms(building_id);
CREATE INDEX idx_room_floor ON rooms(building_id, floor_number);

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
CREATE TABLE IF NOT EXISTS attendance_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    started_by BIGINT NOT NULL,
    status ENUM('ACTIVE', 'CLOSED') NOT NULL DEFAULT 'ACTIVE',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    stopped_at TIMESTAMP NULL,

    CONSTRAINT fk_session_warden FOREIGN KEY (started_by)
        REFERENCES wardens(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_session_status ON attendance_sessions(status);
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
CREATE TABLE IF NOT EXISTS room_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(50) NOT NULL,
    config_value VARCHAR(500) NOT NULL,

    CONSTRAINT uk_config_key UNIQUE (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO room_config (config_key, config_value) VALUES
('max_rooms_per_floor', '10'),
('max_members_per_room', '6'),
('wifi_allowed_subnets', '192.168.0.0/16,10.0.0.0/8,172.16.0.0/12'),
('hostel_latitude', '12.8231'),
('hostel_longitude', '80.0444'),
('hostel_radius', '50');

INSERT IGNORE INTO buildings (id, name, type) VALUES (1, 'Building A', 'NRI'), (2, 'Building B', 'NORMAL');

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
('Ram', 'ram@mit.edu', '$2a$10$9Vw3Fdy7DyCXmAW7XG4dAO2lVc4Vayng/Xow2nAlMP4HTpzn/x9bW', 'NRI', '9876543210'),
('Rajesh', 'rajesh@mit.edu', '$2a$10$u039FG6i7pE.nEjJtiI3O.dGZy2KhT18q0VOfdfnb7Oon.Ob9OMtu', 'Marutham', '9876543211');

INSERT IGNORE INTO security_guards (name, email, password_hash, hostel, phone) VALUES
('muthu', 'muthu@mit.edu', '$2a$10$xhYbBcJFal8ildwL4OvvYOynu9RW/G7PunB9JiRmYagIM5s.x50G6', 'NRI', '9876543212'),
('somu', 'somu@mit.edu', '$2a$10$Rq8cGIg4XvhQBVw/3Ui2z.afZJvr6LchIzACM2sOwCHHMDIkuKQP6', 'Marutham', '9876543213');

INSERT IGNORE INTO students (name, email, password_hash, roll_no, department, hostel, room_number, contact_number, parent_number) VALUES
('Yuvi', 'yuvi@mit.edu', '$2a$10$H3Ag5JR2uJDmBLCLY1NOSeLsHKcNvMmBNQlYPMsZbSqBMGojXWhnK', '2024503541', 'CT', 'NRI', '101', '9876543214', '9876543215'),
('Aravinth', 'arvi@mit.edu', '$2a$10$oE5clQ8xgcRrCzvcUabJoeupjQiNHr7MBR.n2ZQD0F0uJABDoJAa.', '2024503001', 'CT', 'Marutham', '201', '9876543216', '9876543217');
