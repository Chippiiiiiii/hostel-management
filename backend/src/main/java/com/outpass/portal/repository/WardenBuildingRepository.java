package com.outpass.portal.repository;

import com.outpass.portal.model.entity.WardenBuilding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WardenBuildingRepository extends JpaRepository<WardenBuilding, Long> {
    List<WardenBuilding> findByWardenIdOrderByCreatedAtAsc(Long wardenId);
    boolean existsByWardenIdAndBuildingId(Long wardenId, Long buildingId);
    void deleteByWardenIdAndBuildingId(Long wardenId, Long buildingId);
}
