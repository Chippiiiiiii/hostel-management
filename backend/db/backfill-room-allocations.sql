-- =====================================================
-- Backfill: create RoomAllocation rows for existing students
-- =====================================================
-- Registration did not create a `room_allocations` row until this change shipped, so
-- every student who registered before today has `students.hostel` / `students.room_number`
-- set as free strings with NOTHING in `room_allocations` linking them to a real room.
--
-- Room "locking" (a student cannot change their room after registration) is implemented
-- as: a student is locked iff they have a room_allocations row. Without running this
-- backfill, every existing student would look "unlocked" the moment the new code ships,
-- even though they already have a room per their hostel/room_number strings.
--
-- SAFE, MANUAL, ONE-TIME, IDEMPOTENT:
--   * Not run automatically by the application (application code never executes this file).
--   * Re-running it is harmless: the INSERT ... SELECT below only considers students who
--     do not already have a room_allocations row.
--   * It NEVER writes to students.hostel or students.room_number. It only reads those two
--     columns to find an EXACT, unambiguous match against a real buildings.name +
--     rooms.room_number pair. A student whose hostel/room_number don't match any real
--     room is left completely untouched -- no partial match, no fuzzy match, no edit --
--     and shows up in the "unmatched" query below for manual reconciliation by a
--     warden/admin (who can allocate them a real room via the warden UI, which creates
--     the room_allocations row properly, with capacity/department enforced).
--   * Does NOT re-check room capacity or department eligibility: these students are
--     already physically in that room per the existing data, so retroactively enforcing
--     capacity/department here could incorrectly leave a legitimately-housed student
--     without an allocation (i.e. incorrectly "unlocked") just because their room is
--     already at or over nominal capacity, or because department rules were configured
--     after they moved in. Going forward, all NEW allocations do enforce both.
--
-- Run this ONCE, after deploying the schema changes, against your database:
--   mysql -h <host> -P <port> -u <user> -p<pass> <db_name> < backfill-room-allocations.sql
-- =====================================================

-- Preview first (recommended): how many students will be backfilled?
-- SELECT COUNT(*) AS students_to_backfill
-- FROM students s
-- JOIN buildings b ON b.name = s.hostel
-- JOIN rooms r ON r.building_id = b.id AND r.room_number = s.room_number
-- WHERE s.email NOT IN (SELECT student_email FROM room_allocations);

INSERT INTO room_allocations (room_id, student_id, student_name, student_roll_no, student_department, student_email, allocated_at)
SELECT r.id, s.id, s.name, s.roll_no, s.department, s.email, s.created_at
FROM students s
JOIN buildings b ON b.name = s.hostel
JOIN rooms r ON r.building_id = b.id AND r.room_number = s.room_number
WHERE s.email NOT IN (SELECT student_email FROM room_allocations);

-- After running the INSERT above, use this to list students who could NOT be backfilled
-- (their hostel/room_number did not match any real building/room) -- these students
-- remain unlocked until a warden/admin assigns them a real room:
--
-- SELECT s.id, s.name, s.email, s.roll_no, s.department, s.hostel, s.room_number
-- FROM students s
-- WHERE s.email NOT IN (SELECT student_email FROM room_allocations)
-- ORDER BY s.name;

SELECT 'Backfill complete. Run the unmatched-students query above to see who needs manual reconciliation.' AS Status;
