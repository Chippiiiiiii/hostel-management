-- =====================================================
-- Backfill: per-building room_config rows + best-effort attendance_sessions.building_id
-- =====================================================
-- Run AFTER db/migrate-attendance-room-building-fk.sql. Safe to run multiple times: every
-- insert/update below is guarded so a repeat run is a no-op where work is already done.
-- Never deletes or overwrites a pre-existing per-building row -- only fills gaps.

-- ---------------------------------------------------------------------------------------
-- 1) room_config: duplicate today's single global value (building_id IS NULL, the
--    pre-migration value, retained as the admin-set campus default template -- see
--    backend/AGENTS.md) into one row per existing Building, so no hostel silently loses
--    its WiFi/geofence/capacity settings during the cutover.
-- ---------------------------------------------------------------------------------------
INSERT INTO room_config (config_key, config_value, building_id)
SELECT rc.config_key, rc.config_value, b.id
FROM room_config rc
CROSS JOIN buildings b
WHERE rc.building_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM room_config rc2
      WHERE rc2.config_key = rc.config_key AND rc2.building_id = b.id
  );

-- ---------------------------------------------------------------------------------------
-- 2) attendance_sessions: best-effort backfill via the warden who started each session ->
--    their hostel at the time -> the matching building. Historical data is never deleted;
--    rows that can't be resolved this way are intentionally left with building_id NULL
--    (see the report query below) rather than guessed at.
-- ---------------------------------------------------------------------------------------
UPDATE attendance_sessions s
JOIN wardens w ON w.id = s.started_by
JOIN buildings b ON b.name = w.hostel
SET s.building_id = b.id
WHERE s.building_id IS NULL;

-- ---------------------------------------------------------------------------------------
-- 3) Report: sessions that remain unresolved after the best-effort backfill above (warden
--    deleted, warden's hostel renamed/cleared since, or the session predates any hostel
--    concept). Review this list manually before ever considering a NOT NULL migration on
--    attendance_sessions.building_id -- these rows are historical data, not defects to be
--    silently discarded. An empty result set here means every historical session was
--    successfully attributed to a building.
-- ---------------------------------------------------------------------------------------
SELECT s.id AS session_id, s.started_by AS warden_id, w.name AS warden_name,
       w.hostel AS warden_hostel_at_lookup_time, s.status, s.started_at, s.stopped_at
FROM attendance_sessions s
LEFT JOIN wardens w ON w.id = s.started_by
WHERE s.building_id IS NULL
ORDER BY s.started_at;

-- ---------------------------------------------------------------------------------------
-- 4) Verification: row-count sanity check -- room_config should now have exactly
--    (number of distinct config_key values) * (1 default row + count(buildings)) rows.
-- ---------------------------------------------------------------------------------------
SELECT
    (SELECT COUNT(DISTINCT config_key) FROM room_config) AS distinct_keys,
    (SELECT COUNT(*) FROM buildings) AS building_count,
    (SELECT COUNT(*) FROM room_config) AS actual_room_config_rows,
    (SELECT COUNT(DISTINCT config_key) FROM room_config) * (1 + (SELECT COUNT(*) FROM buildings)) AS expected_room_config_rows;
