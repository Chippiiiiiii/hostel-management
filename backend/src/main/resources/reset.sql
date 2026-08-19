-- =====================================================
-- DANGER: Full Database Reset
-- =====================================================
-- This DELETES everything and starts fresh.
-- Only use this if you want to wipe all data.
-- =====================================================

DROP DATABASE IF EXISTS outpass_portal;

-- After running this, run schema.sql then seed-data.sql:
--   mysql -u root < schema.sql
--   mysql -u root < seed-data.sql
