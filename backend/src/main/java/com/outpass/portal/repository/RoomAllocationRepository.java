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

    // room_allocations.student_id is nullable (ON DELETE SET NULL if the Student row is
    // ever removed), so counting via that association would silently undercount through an
    // implicit inner join. room_id is NOT NULL and Room.building is non-optional, so counting
    // by the room's building name (== hostel, see RoomService#allocateStudent) is safe.
    long countByRoom_Building_Name(String hostel);
    long countByRoom_Building_NameIn(List<String> hostels);
    void deleteByStudentEmail(String studentEmail);
    List<RoomAllocation> findByStudentNameContainingIgnoreCaseOrStudentRollNoContainingIgnoreCaseOrStudentDepartmentContainingIgnoreCase(
        String name, String rollNo, String department
    );
}
