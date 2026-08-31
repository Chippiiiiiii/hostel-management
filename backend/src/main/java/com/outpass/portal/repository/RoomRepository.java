package com.outpass.portal.repository;

import com.outpass.portal.model.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByBuildingIdOrderByFloorNumberAscRoomNumberAsc(Long buildingId);
    List<Room> findByBuildingIdAndFloorNumberOrderByRoomNumberAsc(Long buildingId, Integer floorNumber);
    Optional<Room> findByBuildingIdAndRoomNumber(Long buildingId, String roomNumber);
    long countByBuildingIdAndFloorNumber(Long buildingId, Integer floorNumber);

    // Deliberately scoped by building -- there is no campus-wide equivalent. A capacity
    // change made for one hostel must never rewrite another hostel's already-allocated
    // rooms (this is what RoomService.updateConfig used to do via an unscoped
    // updateAllMaxMembers(), the second bug found alongside the cross-hostel WiFi/session
    // authorization gap).
    @Modifying
    @Query("UPDATE Room r SET r.maxMembers = :maxMembers WHERE r.building.id = :buildingId")
    void updateMaxMembersByBuildingId(@Param("maxMembers") int maxMembers, @Param("buildingId") Long buildingId);

    // Locks the room row for the duration of the enclosing transaction, so a capacity
    // check followed by an allocation write can't race with another concurrent
    // allocation (single or bulk) on the same room.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(Long id);
}
