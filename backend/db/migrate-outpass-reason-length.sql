-- =====================================================
-- Migration: Widen outpasses.reason from VARCHAR(50) to VARCHAR(500)
-- =====================================================
-- The `reason` column was never declared in schema.sql/schema-cloud.sql/schema-managed.sql --
-- it was added ad hoc by `ddl-auto=update` on an already-existing `outpasses` table, at
-- whatever length Hibernate picked up at the time (VARCHAR(50) in practice). OutpassRequest
-- validates `reason` up to 500 characters, so a DTO-valid request over 50 characters passed
-- validation but crashed at insert time with a raw MysqlDataTruncation error (found live during
-- black-box security verification -- see vulnerabilities.md, FINDING-3).
--
-- Safe to run multiple times: MODIFY COLUMN to an identical definition is a no-op. Does not
-- drop, truncate, or otherwise touch existing row data -- only widens the column's max length.
--
-- Run this against production once; it is also captured (for fresh deployments) in
-- backend/db/schema-managed.sql, backend/src/main/resources/schema.sql, and
-- backend/src/main/resources/schema-cloud.sql.

ALTER TABLE outpasses MODIFY COLUMN reason VARCHAR(500) NULL;

SELECT COLUMN_NAME, CHARACTER_MAXIMUM_LENGTH
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'outpasses' AND COLUMN_NAME = 'reason';
