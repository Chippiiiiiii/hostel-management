-- =====================================================
-- Migration: Add students.id_card_photo (student ID-card photo feature)
-- =====================================================
-- Separate from the existing profile_picture column -- the student uploads their ID-card
-- photo once via PUT /student/profile/id-card-photo, and it is referenced (never duplicated)
-- from every outpass response shown to Warden/Security. Does not touch or remove any
-- existing profile_picture data.
--
-- Safe to run multiple times: MySQL 8.0.29+ supports `ADD COLUMN IF NOT EXISTS`, so a
-- second run is a no-op rather than an error. Already captured (for fresh deployments) in
-- backend/db/schema-managed.sql, backend/src/main/resources/schema.sql, and
-- backend/src/main/resources/schema-cloud.sql.

ALTER TABLE students ADD COLUMN IF NOT EXISTS id_card_photo LONGTEXT NULL;

SELECT COLUMN_NAME, DATA_TYPE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'students' AND COLUMN_NAME = 'id_card_photo';
