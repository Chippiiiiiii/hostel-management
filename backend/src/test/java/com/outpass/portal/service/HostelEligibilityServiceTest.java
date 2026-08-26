package com.outpass.portal.service;

import com.outpass.portal.model.entity.Building;
import com.outpass.portal.model.entity.YearHostelEligibility;
import com.outpass.portal.repository.BuildingRepository;
import com.outpass.portal.repository.YearHostelEligibilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Admin configures which hostels are selectable for each academic year (1-4). This is the
 * single place both the admin CRUD and the registration-time backend validation go through.
 */
@ExtendWith(MockitoExtension.class)
class HostelEligibilityServiceTest {

    @Mock private YearHostelEligibilityRepository eligibilityRepository;
    @Mock private BuildingRepository buildingRepository;

    private HostelEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new HostelEligibilityService(eligibilityRepository, buildingRepository);
    }

    private Building building(long id, String name) {
        return Building.builder().id(id).name(name).type("NORMAL").gender("BOY").build();
    }

    @Test
    void adminCanConfigureAllowedHostelForAYear() {
        Building b = building(1L, "Hostel C");
        when(buildingRepository.findById(1L)).thenReturn(Optional.of(b));
        when(eligibilityRepository.existsByYearAndBuildingId(2, 1L)).thenReturn(false);

        service.addMapping(2, 1L);

        verify(eligibilityRepository).save(any(YearHostelEligibility.class));
    }

    @Test
    void cannotCreateDuplicateMapping() {
        Building b = building(1L, "Hostel C");
        when(buildingRepository.findById(1L)).thenReturn(Optional.of(b));
        when(eligibilityRepository.existsByYearAndBuildingId(2, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.addMapping(2, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already allowed");

        verify(eligibilityRepository, never()).save(any());
    }

    @Test
    void rejectsMappingForOutOfRangeYear() {
        assertThatThrownBy(() -> service.addMapping(5, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid academic year");

        verifyNoInteractions(eligibilityRepository, buildingRepository);
    }

    @Test
    void canRemoveAMapping() {
        service.removeMapping(2, 1L);

        verify(eligibilityRepository).deleteByYearAndBuildingId(2, 1L);
    }

    @Test
    void configurationListsAllFourYearsEvenWhenUnconfigured() {
        when(eligibilityRepository.findAll()).thenReturn(List.of());

        Map<Integer, List<Map<String, Object>>> config = service.getConfiguration();

        assertThat(config.keySet()).containsExactlyInAnyOrder(1, 2, 3, 4);
        assertThat(config.get(1)).isEmpty();
    }

    @Test
    void configurationGroupsHostelsUnderTheirYear() {
        Building hostelA = building(1L, "Hostel A");
        Building hostelC = building(3L, "Hostel C");
        when(eligibilityRepository.findAll()).thenReturn(List.of(
                YearHostelEligibility.builder().id(10L).year(1).building(hostelA).build(),
                YearHostelEligibility.builder().id(11L).year(3).building(hostelA).build(),
                YearHostelEligibility.builder().id(12L).year(3).building(hostelC).build()
        ));

        Map<Integer, List<Map<String, Object>>> config = service.getConfiguration();

        assertThat(config.get(1)).hasSize(1);
        assertThat(config.get(2)).isEmpty();
        assertThat(config.get(3)).hasSize(2);
        assertThat(config.get(4)).isEmpty();
    }

    // ---- Registration-time validation (backend-authoritative) ----

    @Test
    void validEligibilityPassesSilently() {
        when(eligibilityRepository.existsByYearAndBuildingId(2, 3L)).thenReturn(true);

        service.validateEligibility(2, 3L); // no exception
    }

    @Test
    void invalidYearHostelCombinationIsRejected() {
        when(eligibilityRepository.existsByYearAndBuildingId(2, 99L)).thenReturn(false);

        assertThatThrownBy(() -> service.validateEligibility(2, 99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void allowedBuildingIdsReflectsConfiguredMappingsForThatYearOnly() {
        Building hostelA = building(1L, "Hostel A");
        Building hostelC = building(3L, "Hostel C");
        when(eligibilityRepository.findByYear(3)).thenReturn(List.of(
                YearHostelEligibility.builder().id(1L).year(3).building(hostelA).build(),
                YearHostelEligibility.builder().id(2L).year(3).building(hostelC).build()
        ));

        assertThat(service.getAllowedBuildingIds(3)).isEqualTo(Set.of(1L, 3L));
    }

    @Test
    void allowedBuildingIdsEmptyForNullYear() {
        assertThat(service.getAllowedBuildingIds(null)).isEmpty();
        verifyNoInteractions(eligibilityRepository);
    }
}
