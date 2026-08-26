package com.outpass.portal.repository;

import com.outpass.portal.model.entity.YearHostelEligibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface YearHostelEligibilityRepository extends JpaRepository<YearHostelEligibility, Long> {
    List<YearHostelEligibility> findByYear(Integer year);
    boolean existsByYearAndBuildingId(Integer year, Long buildingId);
    void deleteByYearAndBuildingId(Integer year, Long buildingId);
}
