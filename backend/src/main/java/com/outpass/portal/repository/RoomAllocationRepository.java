package com.outpass.portal.repository;

import com.outpass.portal.model.entity.RoomAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomAllocationRepository extends JpaRepository<RoomAllocation, Long> {
    List<RoomAllocation> findByRoomId(Long roomId);
    Optional<RoomAllocation> findByStudentEmail(String studentEmail);
    long countByRoomId(Long roomId);
    void deleteByStudentEmail(String studentEmail);
    List<RoomAllocation> findByStudentNameContainingIgnoreCaseOrStudentRollNoContainingIgnoreCaseOrStudentDepartmentContainingIgnoreCase(
        String name, String rollNo, String department
    );
}
