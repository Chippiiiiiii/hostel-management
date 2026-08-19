package com.outpass.portal.repository;

import com.outpass.portal.model.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByBuildingIdOrderByFloorNumberAscRoomNumberAsc(Long buildingId);
    List<Room> findByBuildingIdAndFloorNumberOrderByRoomNumberAsc(Long buildingId, Integer floorNumber);
    Optional<Room> findByBuildingIdAndRoomNumber(Long buildingId, String roomNumber);
    long countByBuildingIdAndFloorNumber(Long buildingId, Integer floorNumber);
}
