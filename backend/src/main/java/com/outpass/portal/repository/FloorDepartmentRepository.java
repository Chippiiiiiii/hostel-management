package com.outpass.portal.repository;

import com.outpass.portal.model.entity.FloorDepartment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FloorDepartmentRepository extends JpaRepository<FloorDepartment, Long> {
    Optional<FloorDepartment> findByBuildingIdAndFloorNumber(Long buildingId, Integer floorNumber);
    List<FloorDepartment> findByBuildingId(Long buildingId);
}
