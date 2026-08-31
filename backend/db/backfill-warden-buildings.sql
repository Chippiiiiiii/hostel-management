-- =====================================================
-- Backfill: warden_buildings for wardens created before this feature shipped
-- =====================================================
-- Every existing warden already has a single-hostel assignment recorded as a free-text
-- `wardens.hostel` string that must equal a `buildings.name` exactly (see
-- backend/AGENTS.md's hostel-scoping contract). This script materializes that existing
-- assignment as a row in `warden_buildings` so the Admin "manage buildings" UI shows the
-- warden's current building immediately, without changing `wardens.hostel` or any
-- runtime warden-scoped query.
--
-- Safe to run multiple times: only inserts a (warden_id, building_id) pair that isn't
-- already present, and only for wardens whose `hostel` matches a real building (a warden
-- whose `hostel` doesn't match any building has a pre-existing data problem -- see the
-- "NRI" incident in backend/AGENTS.md -- and is intentionally left for manual review
-- rather than silently guessed at here).

INSERT INTO warden_buildings (warden_id, building_id)
SELECT w.id, b.id
FROM wardens w
JOIN buildings b ON b.name = w.hostel
WHERE w.hostel IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM warden_buildings wb
      WHERE wb.warden_id = w.id AND wb.building_id = b.id
  );

-- Review: wardens whose `hostel` does not match any building -- these were not backfilled
-- above and need the assignment fixed manually (via the Admin "manage buildings" UI, which
-- resolves buildings by ID rather than free-text name, or by correcting `wardens.hostel`).
SELECT w.id, w.name, w.email, w.hostel
FROM wardens w
WHERE w.hostel IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM buildings b WHERE b.name = w.hostel);
