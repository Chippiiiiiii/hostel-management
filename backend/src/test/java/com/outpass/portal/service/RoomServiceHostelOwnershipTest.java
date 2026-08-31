package com.outpass.portal.service;

import com.outpass.portal.model.entity.*;
import com.outpass.portal.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Covers the /warden/rooms/** cross-hostel authorization gap fix: a warden must only be
 * able to view/mutate buildings, floors, rooms and allocations that belong to their own
 * hostel (Building.name == Warden.hostel), while a null wardenHostel (Admin) remains
 * fully unrestricted across every hostel.
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceHostelOwnershipTest {

    @Mock private BuildingRepository buildingRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private RoomAllocationRepository allocationRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private FloorDepartmentRepository floorDepartmentRepository;
    @Mock private HostelEligibilityService hostelEligibilityService;
    @Mock private WardenRepository wardenRepository;
    @Mock private BuildingConfigService buildingConfigService;

    private RoomService roomService;

    private Building hostelA;
    private Building hostelB;

    private static final String HOSTEL_A = "Hostel A";
    private static final String HOSTEL_B = "Hostel B";

    @BeforeEach
    void setUp() {
        roomService = new RoomService(buildingRepository, roomRepository, allocationRepository,
                studentRepository, floorDepartmentRepository, hostelEligibilityService,
                wardenRepository, buildingConfigService);
        hostelA = Building.builder().id(1L).name(HOSTEL_A).type("NORMAL").gender("BOY").build();
        hostelB = Building.builder().id(2L).name(HOSTEL_B).type("NORMAL").gender("BOY").build();
    }

    private Room roomIn(Building building, Long id, int floor, String number) {
        return Room.builder().id(id).building(building).floorNumber(floor).roomNumber(number)
                .maxMembers(4).build();
    }

    // ==================== getBuildings: list filtering ====================

    @Test
    void getBuildings_wardenHostel_returnsOnlyOwnBuilding() {
        when(buildingRepository.findAll()).thenReturn(List.of(hostelA, hostelB));
        when(roomRepository.findByBuildingIdOrderByFloorNumberAscRoomNumberAsc(1L)).thenReturn(List.of());
        when(floorDepartmentRepository.findByBuildingId(1L)).thenReturn(List.of());

        List<Map<String, Object>> result = roomService.getBuildings(List.of(HOSTEL_A));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("name")).isEqualTo(HOSTEL_A);
        verify(roomRepository, never()).findByBuildingIdOrderByFloorNumberAscRoomNumberAsc(2L);
    }

    @Test
    void getBuildings_adminNullHostel_returnsEveryBuilding() {
        when(buildingRepository.findAll()).thenReturn(List.of(hostelA, hostelB));
        when(roomRepository.findByBuildingIdOrderByFloorNumberAscRoomNumberAsc(anyLong())).thenReturn(List.of());
        when(floorDepartmentRepository.findByBuildingId(anyLong())).thenReturn(List.of());

        List<Map<String, Object>> result = roomService.getBuildings(null);

        assertThat(result).hasSize(2);
    }

    // ==================== renameBuilding ====================

    @Test
    void renameBuilding_sameHostel_succeeds() {
        when(buildingRepository.findById(1L)).thenReturn(Optional.of(hostelA));
        when(roomRepository.findByBuildingIdOrderByFloorNumberAscRoomNumberAsc(1L)).thenReturn(List.of());
        when(floorDepartmentRepository.findByBuildingId(1L)).thenReturn(List.of());
        when(wardenRepository.findByHostel(HOSTEL_A)).thenReturn(List.of());
        when(studentRepository.findByHostel(HOSTEL_A)).thenReturn(List.of());

        roomService.renameBuilding(1L, "Hostel A Renamed", List.of(HOSTEL_A));

        assertThat(hostelA.getName()).isEqualTo("Hostel A Renamed");
        verify(buildingRepository).save(hostelA);
    }

    // A rename must cascade to every Warden.hostel and Student.hostel copy of the old
    // building name -- otherwise the warden loses ownership of their own (renamed)
    // building on their very next request, and every already-allocated student's hostel
    // field goes stale, silently breaking every other hostel-scoped query in the app.
    @Test
    void renameBuilding_cascadesNewNameToWardensAndStudentsOfThatHostel() {
        Warden warden = Warden.builder().id(9L).name("W").email("w@x.com").hostel(HOSTEL_A).build();
        Student student = Student.builder().id(99L).name("S").hostel(HOSTEL_A).build();

        when(buildingRepository.findById(1L)).thenReturn(Optional.of(hostelA));
        when(roomRepository.findByBuildingIdOrderByFloorNumberAscRoomNumberAsc(1L)).thenReturn(List.of());
        when(floorDepartmentRepository.findByBuildingId(1L)).thenReturn(List.of());
        when(wardenRepository.findByHostel(HOSTEL_A)).thenReturn(List.of(warden));
        when(studentRepository.findByHostel(HOSTEL_A)).thenReturn(List.of(student));

        roomService.renameBuilding(1L, "Hostel A Renamed", List.of(HOSTEL_A));

        assertThat(warden.getHostel()).isEqualTo("Hostel A Renamed");
        assertThat(student.getHostel()).isEqualTo("Hostel A Renamed");
        verify(wardenRepository).saveAll(List.of(warden));
        verify(studentRepository).saveAll(List.of(student));
    }

    @Test
    void renameBuilding_toSameName_skipsCascadeLookup() {
        when(buildingRepository.findById(1L)).thenReturn(Optional.of(hostelA));
        when(roomRepository.findByBuildingIdOrderByFloorNumberAscRoomNumberAsc(1L)).thenReturn(List.of());
        when(floorDepartmentRepository.findByBuildingId(1L)).thenReturn(List.of());

        roomService.renameBuilding(1L, HOSTEL_A, List.of(HOSTEL_A));

        verify(wardenRepository, never()).findByHostel(anyString());
        verify(studentRepository, never()).findByHostel(anyString());
    }

    @Test
    void renameBuilding_crossHostel_rejected() {
        when(buildingRepository.findById(2L)).thenReturn(Optional.of(hostelB));

        assertThatThrownBy(() -> roomService.renameBuilding(2L, "Hijacked", List.of(HOSTEL_A)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("own hostel");

        verify(buildingRepository, never()).save(any());
    }

    @Test
    void renameBuilding_adminNullHostel_succeedsOnAnyBuilding() {
        when(buildingRepository.findById(2L)).thenReturn(Optional.of(hostelB));
        when(roomRepository.findByBuildingIdOrderByFloorNumberAscRoomNumberAsc(2L)).thenReturn(List.of());
        when(floorDepartmentRepository.findByBuildingId(2L)).thenReturn(List.of());

        roomService.renameBuilding(2L, "Renamed By Admin", null);

        assertThat(hostelB.getName()).isEqualTo("Renamed By Admin");
    }

    // ==================== updateBuildingType / updateBuildingGender ====================

    @Test
    void updateBuildingType_crossHostel_rejected() {
        when(buildingRepository.findById(2L)).thenReturn(Optional.of(hostelB));

        assertThatThrownBy(() -> roomService.updateBuildingType(2L, "SPECIAL", List.of(HOSTEL_A)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("own hostel");

        verify(buildingRepository, never()).save(any());
    }

    @Test
    void updateBuildingGender_crossHostel_rejected() {
        when(buildingRepository.findById(2L)).thenReturn(Optional.of(hostelB));

        assertThatThrownBy(() -> roomService.updateBuildingGender(2L, "GIRL", List.of(HOSTEL_A)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("own hostel");

        verify(buildingRepository, never()).save(any());
    }

    // ==================== removeBuilding ====================

    @Test
    void removeBuilding_crossHostel_rejectedBeforeAllocationCheck() {
        when(buildingRepository.findById(2L)).thenReturn(Optional.of(hostelB));

        assertThatThrownBy(() -> roomService.removeBuilding(2L, List.of(HOSTEL_A)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("own hostel");

        verify(allocationRepository, never()).findAll();
        verify(buildingRepository, never()).deleteById(any());
    }

    @Test
    void removeBuilding_sameHostel_succeedsWhenNoAllocations() {
        when(buildingRepository.findById(1L)).thenReturn(Optional.of(hostelA));
        when(allocationRepository.findAll()).thenReturn(List.of());

        roomService.removeBuilding(1L, List.of(HOSTEL_A));

        verify(buildingRepository).deleteById(1L);
    }

    // ==================== addFloor / removeFloor / addRoomToFloor / removeLastRoomFromFloor ====================

    @Test
    void addFloor_crossHostel_rejected() {
        when(buildingRepository.findById(2L)).thenReturn(Optional.of(hostelB));

        assertThatThrownBy(() -> roomService.addFloor(2L, List.of(HOSTEL_A)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("own hostel");

        verify(roomRepository, never()).saveAll(any());
    }

    @Test
    void addFloor_sameHostel_succeeds() {
        when(buildingRepository.findById(1L)).thenReturn(Optional.of(hostelA));
        when(roomRepository.findByBuildingIdOrderByFloorNumberAscRoomNumberAsc(1L)).thenReturn(List.of());
        when(buildingConfigService.getConfigString(anyString(), anyLong(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(2));

        roomService.addFloor(1L, List.of(HOSTEL_A));

        verify(roomRepository).saveAll(any());
    }

    @Test
    void removeFloor_crossHostel_rejected() {
        when(buildingRepository.findById(2L)).thenReturn(Optional.of(hostelB));

        assertThatThrownBy(() -> roomService.removeFloor(2L, 1, List.of(HOSTEL_A)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("own hostel");

        verify(roomRepository, never()).deleteAll(any());
    }

    @Test
    void addRoomToFloor_crossHostel_rejected() {
        when(buildingRepository.findById(2L)).thenReturn(Optional.of(hostelB));

        assertThatThrownBy(() -> roomService.addRoomToFloor(2L, 1, List.of(HOSTEL_A)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("own hostel");

        verify(roomRepository, never()).save(any());
    }

    @Test
    void removeLastRoomFromFloor_crossHostel_rejected() {
        when(buildingRepository.findById(2L)).thenReturn(Optional.of(hostelB));

        assertThatThrownBy(() -> roomService.removeLastRoomFromFloor(2L, 1, List.of(HOSTEL_A)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("own hostel");

        verify(roomRepository, never()).delete(any());
    }

    // ==================== updateRoomMaxMembers ====================

    @Test
    void updateRoomMaxMembers_sameHostel_succeeds() {
        Room room = roomIn(hostelA, 10L, 1, "101");
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));

        roomService.updateRoomMaxMembers(10L, 8, List.of(HOSTEL_A));

        assertThat(room.getMaxMembers()).isEqualTo(8);
        verify(roomRepository).save(room);
    }

    @Test
    void updateRoomMaxMembers_crossHostel_rejected() {
        Room room = roomIn(hostelB, 20L, 1, "101");
        when(roomRepository.findById(20L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.updateRoomMaxMembers(20L, 8, List.of(HOSTEL_A)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("own hostel");

        verify(roomRepository, never()).save(any());
    }

    @Test
    void updateRoomMaxMembers_adminNullHostel_succeedsOnAnyRoom() {
        Room room = roomIn(hostelB, 20L, 1, "101");
        when(roomRepository.findById(20L)).thenReturn(Optional.of(room));

        roomService.updateRoomMaxMembers(20L, 8, null);

        verify(roomRepository).save(room);
    }

    // ==================== allocateStudent ====================

    @Test
    void allocateStudent_sameHostel_succeeds() {
        Room room = roomIn(hostelA, 30L, 1, "101");
        when(roomRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(room));
        when(floorDepartmentRepository.findByBuildingIdAndFloorNumber(1L, 1)).thenReturn(Optional.empty());
        when(allocationRepository.countByRoomId(30L)).thenReturn(0L);
        when(allocationRepository.findByStudentEmail("ok@x.com")).thenReturn(Optional.empty());
        when(studentRepository.findByEmailForUpdate("ok@x.com")).thenReturn(Optional.empty());

        roomService.allocateStudent(30L, "S", "R1", "CS", "ok@x.com", List.of(HOSTEL_A));

        verify(allocationRepository).save(any(RoomAllocation.class));
    }

    @Test
    void allocateStudent_crossHostel_rejected() {
        Room room = roomIn(hostelB, 31L, 1, "101");
        when(roomRepository.findByIdForUpdate(31L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.allocateStudent(31L, "S", "R2", "CS", "bad@x.com", List.of(HOSTEL_A)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("own hostel");

        verify(allocationRepository, never()).save(any());
    }

    @Test
    void allocateStudent_adminNullHostel_succeedsAcrossAnyHostel() {
        Room room = roomIn(hostelB, 31L, 1, "101");
        when(roomRepository.findByIdForUpdate(31L)).thenReturn(Optional.of(room));
        when(floorDepartmentRepository.findByBuildingIdAndFloorNumber(2L, 1)).thenReturn(Optional.empty());
        when(allocationRepository.countByRoomId(31L)).thenReturn(0L);
        when(allocationRepository.findByStudentEmail("admin-placed@x.com")).thenReturn(Optional.empty());
        when(studentRepository.findByEmailForUpdate("admin-placed@x.com")).thenReturn(Optional.empty());

        roomService.allocateStudent(31L, "S", "R2", "CS", "admin-placed@x.com", null);

        verify(allocationRepository).save(any(RoomAllocation.class));
    }

    // ==================== removeAllocation ====================

    @Test
    void removeAllocation_sameHostel_succeeds() {
        Room room = roomIn(hostelA, 40L, 1, "101");
        RoomAllocation allocation = RoomAllocation.builder().id(1L).room(room)
                .studentName("S").studentEmail("s@x.com").build();
        when(allocationRepository.findByStudentEmail("s@x.com")).thenReturn(Optional.of(allocation));

        roomService.removeAllocation("s@x.com", List.of(HOSTEL_A));

        verify(allocationRepository).deleteByStudentEmail("s@x.com");
    }

    @Test
    void removeAllocation_crossHostel_rejected() {
        Room room = roomIn(hostelB, 41L, 1, "101");
        RoomAllocation allocation = RoomAllocation.builder().id(2L).room(room)
                .studentName("S2").studentEmail("s2@x.com").build();
        when(allocationRepository.findByStudentEmail("s2@x.com")).thenReturn(Optional.of(allocation));

        assertThatThrownBy(() -> roomService.removeAllocation("s2@x.com", List.of(HOSTEL_A)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("own hostel");

        verify(allocationRepository, never()).deleteByStudentEmail(anyString());
    }

    @Test
    void removeAllocation_noExistingAllocation_wardenScoped_isANoOpNotAnError() {
        when(allocationRepository.findByStudentEmail("ghost@x.com")).thenReturn(Optional.empty());

        roomService.removeAllocation("ghost@x.com", List.of(HOSTEL_A));

        verify(allocationRepository).deleteByStudentEmail("ghost@x.com");
    }

    @Test
    void removeAllocation_adminNullHostel_succeedsAcrossAnyHostel() {
        // wardenHostel == null (Admin) skips the ownership lookup entirely -- deletion is
        // unconditional, matching the pre-fix behavior for the unrestricted caller.
        roomService.removeAllocation("s2@x.com", null);

        verify(allocationRepository, never()).findByStudentEmail(anyString());
        verify(allocationRepository).deleteByStudentEmail("s2@x.com");
    }

    // ==================== setFloorDepartment / room department override ====================

    @Test
    void setFloorDepartment_crossHostel_rejected() {
        when(buildingRepository.findById(2L)).thenReturn(Optional.of(hostelB));

        assertThatThrownBy(() -> roomService.setFloorDepartment(2L, 1, "CS", List.of(HOSTEL_A)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("own hostel");

        verify(floorDepartmentRepository, never()).save(any());
    }

    @Test
    void setRoomDepartmentOverride_crossHostel_rejected() {
        Room room = roomIn(hostelB, 50L, 1, "101");
        when(roomRepository.findById(50L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.setRoomDepartmentOverride(50L, "CS", List.of(HOSTEL_A)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("own hostel");

        verify(roomRepository, never()).save(any());
    }

    @Test
    void removeRoomDepartmentOverride_crossHostel_rejected() {
        Room room = roomIn(hostelB, 51L, 1, "101");
        when(roomRepository.findById(51L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.removeRoomDepartmentOverride(51L, List.of(HOSTEL_A)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("own hostel");

        verify(roomRepository, never()).save(any());
    }

    // ==================== updateRoomNumber ====================

    @Test
    void updateRoomNumber_crossHostel_rejected() {
        Room room = roomIn(hostelB, 60L, 1, "101");
        when(roomRepository.findById(60L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.updateRoomNumber(60L, "999", List.of(HOSTEL_A)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("own hostel");

        assertThat(room.getRoomNumber()).isEqualTo("101");
        verify(roomRepository, never()).save(any());
    }

    @Test
    void updateRoomNumber_sameHostel_succeeds() {
        Room room = roomIn(hostelA, 61L, 1, "101");
        when(roomRepository.findById(61L)).thenReturn(Optional.of(room));
        when(roomRepository.findByBuildingIdAndRoomNumber(1L, "999")).thenReturn(Optional.empty());

        roomService.updateRoomNumber(61L, "999", List.of(HOSTEL_A));

        assertThat(room.getRoomNumber()).isEqualTo("999");
    }

    // ==================== bulkAllocate ====================

    @Test
    void bulkAllocate_crossHostel_rejected() {
        when(buildingRepository.findById(2L)).thenReturn(Optional.of(hostelB));

        assertThatThrownBy(() -> roomService.bulkAllocate(2L, null, "warden@x.com", "WARDEN", List.of(HOSTEL_A)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("own hostel");

        verify(roomRepository, never()).findByBuildingIdOrderByFloorNumberAscRoomNumberAsc(anyLong());
    }

    @Test
    void bulkAllocate_sameHostel_proceedsPastOwnershipCheck() {
        when(buildingRepository.findById(1L)).thenReturn(Optional.of(hostelA));
        when(roomRepository.findByBuildingIdOrderByFloorNumberAscRoomNumberAsc(1L)).thenReturn(List.of());
        when(studentRepository.findUnassignedByGender("BOY")).thenReturn(List.of());

        Map<String, Object> result = roomService.bulkAllocate(1L, null, "warden@x.com", "WARDEN", List.of(HOSTEL_A));

        assertThat(result.get("studentsProcessed")).isEqualTo(0);
    }
}
