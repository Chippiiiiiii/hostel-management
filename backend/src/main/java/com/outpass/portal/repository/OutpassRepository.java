package com.outpass.portal.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.outpass.portal.model.entity.Outpass;
import com.outpass.portal.model.enums.OutpassStatus;

@Repository
public interface OutpassRepository extends JpaRepository<Outpass, Long> {
    List<Outpass> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<Outpass> findByStatusOrderByCreatedAtDesc(OutpassStatus status);
    List<Outpass> findByHostelAndStatusOrderByCreatedAtDesc(String hostel, OutpassStatus status);
    List<Outpass> findByHostelOrderByCreatedAtDesc(String hostel);
    List<Outpass> findByDateBetween(LocalDateTime start, LocalDateTime end);
    List<Outpass> findByHostelAndDateBetween(String hostel, LocalDateTime start, LocalDateTime end);
    long countByStudentIdAndStatus(Long studentId, OutpassStatus status);
}

