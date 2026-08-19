package com.outpass.portal.repository;

import com.outpass.portal.model.entity.AttendanceRecord;
import com.outpass.portal.model.enums.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    List<AttendanceRecord> findByStudentIdOrderByDateDesc(Long studentId);
    Optional<AttendanceRecord> findByStudentIdAndDate(Long studentId, LocalDate date);
    List<AttendanceRecord> findBySessionIdOrderByMarkedAtDesc(Long sessionId);
    long countByStudentId(Long studentId);
    long countByStudentIdAndStatus(Long studentId, AttendanceStatus status);
}
