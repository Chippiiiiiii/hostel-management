package com.outpass.portal.service;

import com.outpass.portal.model.entity.Building;
import com.outpass.portal.model.entity.YearHostelEligibility;
import com.outpass.portal.repository.BuildingRepository;
import com.outpass.portal.repository.YearHostelEligibilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

// Admin-configured year -> allowed-hostels mapping (requirement: "year-based hostel
// eligibility"). Deliberately a plain (year INT, building) join table rather than a new
// AcademicYear entity -- year is just an integer 1-4, and Building/hostel already exists.
@Service
@RequiredArgsConstructor
@Slf4j
public class HostelEligibilityService {

    public static final int MIN_YEAR = 1;
    public static final int MAX_YEAR = 4;

    private final YearHostelEligibilityRepository eligibilityRepository;
    private final BuildingRepository buildingRepository;

    @Transactional(readOnly = true)
    public Map<Integer, List<Map<String, Object>>> getConfiguration() {
        Map<Integer, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (int year = MIN_YEAR; year <= MAX_YEAR; year++) {
            result.put(year, new ArrayList<>());
        }
        for (YearHostelEligibility e : eligibilityRepository.findAll()) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("buildingId", e.getBuilding().getId());
            b.put("buildingName", e.getBuilding().getName());
            result.computeIfAbsent(e.getYear(), y -> new ArrayList<>()).add(b);
        }
        return result;
    }

    @Transactional
    public void addMapping(Integer year, Long buildingId) {
        validateYear(year);
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new RuntimeException("Building not found"));
        if (eligibilityRepository.existsByYearAndBuildingId(year, buildingId)) {
            throw new RuntimeException("This hostel is already allowed for year " + year);
        }
        eligibilityRepository.save(YearHostelEligibility.builder().year(year).building(building).build());
        log.info("Year-hostel mapping added: year={} building={}", year, buildingId);
    }

    @Transactional
    public void removeMapping(Integer year, Long buildingId) {
        eligibilityRepository.deleteByYearAndBuildingId(year, buildingId);
        log.info("Year-hostel mapping removed: year={} building={}", year, buildingId);
    }

    @Transactional(readOnly = true)
    public Set<Long> getAllowedBuildingIds(Integer year) {
        if (year == null) {
            return Set.of();
        }
        return eligibilityRepository.findByYear(year).stream()
                .map(e -> e.getBuilding().getId())
                .collect(Collectors.toSet());
    }

    // Backend-authoritative check: the frontend only ever shows allowed hostels, but this
    // is what actually enforces it -- a direct API call with a disallowed (year, hostel)
    // combination must not be able to bypass the restriction.
    @Transactional(readOnly = true)
    public void validateEligibility(Integer year, Long buildingId) {
        validateYear(year);
        if (buildingId == null || !eligibilityRepository.existsByYearAndBuildingId(year, buildingId)) {
            throw new RuntimeException("Selected hostel is not available for your academic year.");
        }
    }

    private void validateYear(Integer year) {
        if (year == null || year < MIN_YEAR || year > MAX_YEAR) {
            throw new RuntimeException("Invalid academic year");
        }
    }
}
