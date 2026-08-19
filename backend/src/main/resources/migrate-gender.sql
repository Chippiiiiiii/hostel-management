-- Add gender column to buildings and students
-- Safe to re-run

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'buildings' AND COLUMN_NAME = 'gender' AND TABLE_SCHEMA = DATABASE());
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE buildings ADD COLUMN gender VARCHAR(10) NOT NULL DEFAULT ''BOY''',
    'SELECT ''buildings.gender already exists'' AS Status');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'students' AND COLUMN_NAME = 'gender' AND TABLE_SCHEMA = DATABASE());
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE students ADD COLUMN gender VARCHAR(10) NOT NULL DEFAULT ''BOY''',
    'SELECT ''students.gender already exists'' AS Status');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT 'Gender migration complete.' AS Status;
