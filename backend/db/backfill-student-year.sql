-- =====================================================
-- Optional: backfill `students.year` for existing students
-- =====================================================
-- Existing students registered before this feature shipped have `year = NULL` (the new
-- column is additive and nullable -- see schema.sql/schema-cloud.sql/schema-managed.sql).
-- This is NOT auto-populated. There is no reliable, general way to derive "current
-- academic year of study" for an existing student without guessing, so this script is
-- provided as an OPTIONAL aid, not a ready-to-run fix -- read the caveats below before
-- using it.
--
-- WHAT THIS SCRIPT ASSUMES (verify BOTH against your own data before running anything):
--   1. `roll_no` encodes an admission year as its leading 4 digits (e.g. '2024503541' ->
--      admitted 2024). This matches the two seed students in seed-data.sql, but has NOT
--      been verified against real production roll-number formats, which may differ (e.g.
--      lateral-entry students, transfers, a different numbering scheme entirely, or roll
--      numbers that don't start with a year at all).
--   2. A simple "current calendar year minus admission year" gives the year of study,
--      with academic-year rollover happening at a specific month you must set below
--      (@cutoff_month) -- e.g. if the academic year restarts in July, a student admitted
--      in 2024 is still "Year 1" until July 2025, then becomes "Year 2".
--   3. Any student whose computed value would fall outside 1-4 (already graduated, roll
--      number doesn't parse as a 4-digit year prefix, etc.) is intentionally left NULL --
--      never guess-clamped into range.
--
-- Set these two values for your institution before running anything below:
SET @cutoff_month = 7;              -- month (1-12) the academic year advances, e.g. July
SET @current_academic_year = 2026;  -- the academic year currently in progress, e.g. 2026 for 2026-27

-- ---------------------------------------------------------------------------------------
-- STEP 1 (recommended): PREVIEW ONLY. Run this first and review the results -- do not run
-- the UPDATE in step 2 until you've checked this looks right for a sample of real students.
-- ---------------------------------------------------------------------------------------
-- SELECT
--     s.id, s.roll_no, s.name,
--     CAST(LEFT(s.roll_no, 4) AS UNSIGNED) AS parsed_admission_year,
--     (@current_academic_year
--         - CAST(LEFT(s.roll_no, 4) AS UNSIGNED)
--         + IF(MONTH(CURDATE()) >= @cutoff_month, 1, 0)) AS computed_year
-- FROM students s
-- WHERE s.year IS NULL
--   AND s.roll_no REGEXP '^[0-9]{4}'
-- ORDER BY s.roll_no;

-- ---------------------------------------------------------------------------------------
-- STEP 2: Only backfills students whose computed year lands in the valid 1-4 range.
-- Never touches a student who already has `year` set (idempotent / safe to re-run).
-- ---------------------------------------------------------------------------------------
-- UPDATE students s
-- SET s.year = (@current_academic_year
--         - CAST(LEFT(s.roll_no, 4) AS UNSIGNED)
--         + IF(MONTH(CURDATE()) >= @cutoff_month, 1, 0))
-- WHERE s.year IS NULL
--   AND s.roll_no REGEXP '^[0-9]{4}'
--   AND (@current_academic_year
--         - CAST(LEFT(s.roll_no, 4) AS UNSIGNED)
--         + IF(MONTH(CURDATE()) >= @cutoff_month, 1, 0)) BETWEEN 1 AND 4;

-- ---------------------------------------------------------------------------------------
-- STEP 3: Students whose year could NOT be determined -- report these for manual review
-- (roll number doesn't start with 4 digits, or the computed value falls outside 1-4,
-- e.g. already graduated or a data-entry anomaly). Run this after step 2, or on its own
-- to see the full list before deciding whether to run step 2 at all.
-- ---------------------------------------------------------------------------------------
-- SELECT s.id, s.name, s.email, s.roll_no, s.hostel, s.room_number
-- FROM students s
-- WHERE s.year IS NULL
-- ORDER BY s.roll_no;

SELECT 'This script is a manual aid only -- nothing above runs automatically. Read the caveats, set @cutoff_month and @current_academic_year, then uncomment step 1 to preview before ever running step 2.' AS Status;
