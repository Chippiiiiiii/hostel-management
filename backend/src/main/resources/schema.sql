-- =====================================================
-- Hostel Management Database Schema
-- =====================================================
-- SAFE TO RE-RUN: Uses IF NOT EXISTS everywhere.
-- Will NOT destroy existing data.
-- =====================================================

CREATE DATABASE IF NOT EXISTS outpass_portal
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE outpass_portal;

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
    -- Academic year of study (1-4). Nullable: existing students predate this field and are
    -- not auto-backfilled -- see backend/db/backfill-student-year.sql.
    year INT NULL,
    hostel VARCHAR(100) NOT NULL,
    room_number VARCHAR(20) NOT NULL,
    contact_number VARCHAR(15) NOT NULL,
    parent_number VARCHAR(15) NOT NULL,
    profile_picture LONGTEXT NULL,
    -- Separate from profile_picture: uploaded once via PUT /student/profile/id-card-photo
    -- and referenced from every outpass response shown to Warden/Security, rather than
    -- duplicated onto each outpass row.
    id_card_photo LONGTEXT NULL,
    gender VARCHAR(10) NOT NULL DEFAULT 'BOY',
    -- NULL = pre-existing/seed account (treated as verified for login purposes).
    -- FALSE = newly self-registered, unverified. TRUE = verified via emailed link.
    email_verified BOOLEAN NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_student_email UNIQUE (email),
    CONSTRAINT uk_student_roll_no UNIQUE (roll_no),
    CONSTRAINT chk_student_email CHECK (email REGEXP '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_student_contact CHECK (contact_number REGEXP '^[0-9]{10}$'),
    CONSTRAINT chk_student_parent CHECK (parent_number REGEXP '^[0-9]{10}$'),
    CONSTRAINT chk_student_year CHECK (year IS NULL OR year BETWEEN 1 AND 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Backfills the columns for an already-existing `students` table (fresh installs
-- already get them from the CREATE TABLE above). Requires MySQL 8.0.29+.
ALTER TABLE students ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS year INT NULL;
ALTER TABLE students ADD COLUMN IF NOT EXISTS id_card_photo LONGTEXT NULL;

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

-- Backfills the column for an already-existing `wardens` table. Requires MySQL 8.0.29+.
ALTER TABLE wardens ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;

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

-- Backfills the column for an already-existing `security_guards` table. Requires MySQL 8.0.29+.
ALTER TABLE security_guards ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;

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

-- The `reason` column was never declared here (added ad hoc by ddl-auto=update on an
-- already-existing outpasses table, at whatever length Hibernate picked up at the time --
-- VARCHAR(50) in practice). OutpassRequest validates reason up to 500 characters; widen the
-- column to match so a DTO-valid request can never fail at insert time with a raw
-- data-truncation error. Re-runnable; a MODIFY to the same definition is a no-op.
ALTER TABLE outpasses MODIFY COLUMN reason VARCHAR(500) NULL;

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

-- Widens the enum for an already-existing `refresh_tokens` table (fresh installs already
-- get 'ADMIN' from the CREATE TABLE above). Re-runnable; a MODIFY to the same definition
-- is a no-op.
ALTER TABLE refresh_tokens MODIFY COLUMN user_type ENUM('STUDENT', 'WARDEN', 'SECURITY_GUARD', 'ADMIN') NOT NULL;

-- =====================================================
-- TABLE: tokens (revoked JWT blacklist)
-- =====================================================
CREATE TABLE IF NOT EXISTS tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    jid VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,

    CONSTRAINT uk_token_jid UNIQUE (jid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

    CONSTRAINT uk_password_reset_token UNIQUE (token),
    INDEX idx_password_reset_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- TABLE: email_verification_tokens
-- =====================================================
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_email_verification_token UNIQUE (token),
    INDEX idx_email_verification_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

-- Widens the enum for an already-existing `access_logs` table. Re-runnable.
ALTER TABLE access_logs MODIFY COLUMN role ENUM('STUDENT', 'WARDEN', 'SECURITY_GUARD', 'ADMIN') NOT NULL;

-- =====================================================
-- TABLE: buildings
-- =====================================================
CREATE TABLE IF NOT EXISTS buildings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    gender VARCHAR(10) NOT NULL DEFAULT 'BOY',
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
    department_override VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_room_building FOREIGN KEY (building_id)
        REFERENCES buildings(id) ON DELETE CASCADE,
    CONSTRAINT uk_room_building_number UNIQUE (building_id, room_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Backfills the column for an already-existing `rooms` table. Requires MySQL 8.0.29+.
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS department_override VARCHAR(100) NULL;

-- =====================================================
-- TABLE: year_hostel_eligibility
-- Admin-configured: which buildings/hostels a student in a given academic year (1-4) may
-- select at registration. Absence of a (year, building) row means "not allowed" -- there
-- is no unconfigured-year fallback.
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

-- =====================================================
-- TABLE: floor_departments
-- Default department per (building, floor). A room's own department_override (above)
-- takes precedence over this when both are set; NULL override means "inherit this".
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

-- =====================================================
-- TABLE: room_config
-- =====================================================
CREATE TABLE IF NOT EXISTS room_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(50) NOT NULL,
    config_value VARCHAR(500) NOT NULL,

    CONSTRAINT uk_config_key UNIQUE (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- TABLE: complaints
-- =====================================================
CREATE TABLE IF NOT EXISTS complaints (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    student_name VARCHAR(100) NOT NULL,
    student_roll_no VARCHAR(20) NOT NULL,
    hostel VARCHAR(100) NOT NULL,
    room_number VARCHAR(20) NOT NULL,
    category ENUM('PLUMBING', 'ELECTRICAL', 'CLEANLINESS', 'FURNITURE', 'INTERNET', 'NOISE', 'OTHER') NOT NULL,
    description VARCHAR(1000) NOT NULL,
    photo LONGTEXT NULL,
    status ENUM('PENDING', 'IN_PROGRESS', 'RESOLVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    warden_response VARCHAR(1000) NULL,
    responded_by BIGINT NULL,
    responded_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_complaint_student FOREIGN KEY (student_id)
        REFERENCES students(id) ON DELETE CASCADE,

    INDEX idx_complaint_student (student_id),
    INDEX idx_complaint_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- CLEANUP EVENT
-- =====================================================
DELIMITER //

CREATE EVENT IF NOT EXISTS cleanup_expired_tokens
ON SCHEDULE EVERY 1 DAY
STARTS CURRENT_TIMESTAMP
DO
BEGIN
    DELETE FROM refresh_tokens WHERE expiry_date < NOW();
    DELETE FROM tokens WHERE expires_at < NOW();
END//

DELIMITER ;

SET GLOBAL event_scheduler = ON;

SELECT 'Schema ready (no data was deleted).' AS Status;
SHOW TABLES;
