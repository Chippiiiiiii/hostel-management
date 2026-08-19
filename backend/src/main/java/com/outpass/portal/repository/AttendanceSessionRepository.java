package com.outpass.portal.repository;

import com.outpass.portal.model.entity.AttendanceSession;
import com.outpass.portal.model.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {
    Optional<AttendanceSession> findByStatus(SessionStatus status);
}
