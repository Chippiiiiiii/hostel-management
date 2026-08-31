package com.outpass.portal.repository;

import com.outpass.portal.model.entity.AttendanceSession;
import com.outpass.portal.model.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {
    // No campus-wide findByStatus(status) here by design -- every session lookup must be
    // scoped to a specific building (or an explicit set of buildings a warden/student is
    // actually associated with). See backend/AGENTS.md: a global lookup here is exactly
    // the shape of the cross-hostel attendance-stop finding this table was migrated to fix.
    Optional<AttendanceSession> findByBuilding_IdAndStatus(Long buildingId, SessionStatus status);
    List<AttendanceSession> findByBuilding_IdInAndStatus(List<Long> buildingIds, SessionStatus status);
}
