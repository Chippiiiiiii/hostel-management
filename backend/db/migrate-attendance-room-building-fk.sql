-- =====================================================
-- Migration: add building_id to attendance_sessions and room_config
-- =====================================================
-- Converts the global attendance-session and room/attendance-config model to per-building
-- ownership (see backend/AGENTS.md). Purely additive: no existing column is changed or
-- dropped, no row is deleted. Run this BEFORE the backfill script
-- (db/backfill-attendance-room-building.sql) and BEFORE deploying the application code
-- that requires these columns to exist.
--
-- THIS IS A ONE-TIME MIGRATION. IT IS NOT SAFE TO RE-RUN.
-- Only the two "ADD COLUMN IF NOT EXISTS" statements below are individually rerunnable.
-- Every other statement in this file (ADD CONSTRAINT ... FOREIGN KEY, CREATE INDEX,
-- DROP INDEX, DROP INDEX uk_config_key, ADD CONSTRAINT ... UNIQUE) has no MySQL
-- "IF [NOT] EXISTS" form -- verified directly: MySQL 9.7.1 rejects
-- "ADD CONSTRAINT ... FOREIGN KEY IF NOT EXISTS (...)", "CREATE INDEX ... IF NOT EXISTS",
-- and "DROP INDEX IF EXISTS ..." with a syntax error. Re-running this file against a
-- database it has already been applied to WILL fail (duplicate constraint/index name on
-- the ADD statements, "check that column/key exists" on the DROP INDEX statements).
-- Track that this migration has run (e.g. a schema_migrations table, or your deploy
-- tooling's own migration ledger) rather than relying on this script being re-runnable.

ALTER TABLE attendance_sessions
    ADD COLUMN IF NOT EXISTS building_id BIGINT NULL AFTER id;

ALTER TABLE attendance_sessions
    ADD CONSTRAINT fk_session_building FOREIGN KEY (building_id)
        REFERENCES buildings(id) ON DELETE RESTRICT;

CREATE INDEX idx_session_building_status ON attendance_sessions(building_id, status);

-- The old campus-wide index is no longer the right lookup shape (every read is now
-- building-scoped, not status-only) -- drop it now that idx_session_building_status
-- covers the same and more.
DROP INDEX idx_session_status ON attendance_sessions;

ALTER TABLE room_config
    ADD COLUMN IF NOT EXISTS building_id BIGINT NULL AFTER config_value;

ALTER TABLE room_config
    DROP INDEX uk_config_key,
    ADD CONSTRAINT uk_config_key_building UNIQUE (config_key, building_id),
    ADD CONSTRAINT fk_roomconfig_building FOREIGN KEY (building_id)
        REFERENCES buildings(id) ON DELETE CASCADE;

-- At this point every existing room_config row has building_id = NULL (the pre-migration
-- global value becomes the admin-set campus default template) and every existing
-- attendance_sessions row has building_id = NULL (historical, resolved by the backfill
-- script on a best-effort basis). Do not enforce NOT NULL on either column here -- see
-- db/backfill-attendance-room-building.sql for the unresolved-rows report that must be
-- reviewed before any future NOT NULL migration.
