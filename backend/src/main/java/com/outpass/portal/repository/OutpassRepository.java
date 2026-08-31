package com.outpass.portal.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.outpass.portal.model.entity.Outpass;
import com.outpass.portal.model.enums.OutpassStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface OutpassRepository extends JpaRepository<Outpass, Long> {
    List<Outpass> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<Outpass> findByStatusOrderByCreatedAtDesc(OutpassStatus status);
    List<Outpass> findByHostelAndStatusOrderByCreatedAtDesc(String hostel, OutpassStatus status);
    List<Outpass> findByHostelOrderByCreatedAtDesc(String hostel);
    List<Outpass> findByHostelInAndStatusOrderByCreatedAtDesc(List<String> hostels, OutpassStatus status);
    List<Outpass> findByHostelInOrderByCreatedAtDesc(List<String> hostels);
    List<Outpass> findByDateBetween(LocalDateTime start, LocalDateTime end);
    List<Outpass> findByHostelAndDateBetween(String hostel, LocalDateTime start, LocalDateTime end);
    long countByStudentIdAndStatus(Long studentId, OutpassStatus status);

    // Locks the outpass row for the duration of the enclosing transaction, so a status
    // check (e.g. "is it still PENDING/APPROVED/DEPARTED") followed by a status-transition
    // write can't race with another concurrent action on the same outpass (e.g. a warden
    // approving and declining at the same time, or a guard double-processing a scan).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Outpass o where o.id = :id")
    Optional<Outpass> findByIdForUpdate(Long id);
}

