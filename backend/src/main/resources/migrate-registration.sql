-- =====================================================
-- Migration: Add profile_picture to students, type to buildings
-- SAFE TO RE-RUN (uses IF NOT EXISTS / column checks)
-- =====================================================

USE outpass_portal;

-- Add profile_picture column to students (if not exists)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'outpass_portal' AND TABLE_NAME = 'students' AND COLUMN_NAME = 'profile_picture');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE students ADD COLUMN profile_picture LONGTEXT NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add type column to buildings (if not exists)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'outpass_portal' AND TABLE_NAME = 'buildings' AND COLUMN_NAME = 'type');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE buildings ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT \'NORMAL\'', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Set Building A as NRI type
UPDATE buildings SET type = 'NRI' WHERE name = 'Building A' AND type = 'NORMAL';

SELECT 'Migration complete.' AS Status;
