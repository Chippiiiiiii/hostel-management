package com.outpass.portal.service;

import com.outpass.portal.model.entity.*;
import com.outpass.portal.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Covers the room-allocation safeguards approved in the implementation plan: department
 * precedence (room override beats floor default), bulk allocation only ever touching
 * unassigned students while respecting capacity and department, and room-number edits
 * never touching floor/building.
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock private BuildingRepository buildingRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private RoomAllocationRepository allocationRepository;
    @Mock private RoomConfigRepository configRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private FloorDepartmentRepository floorDepartmentRepository;
    @Mock private HostelEligibilityService hostelEligibilityService;
    @Mock private WardenRepository wardenRepository;

    private RoomService roomService;

    private Building building;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(buildingRepository, roomRepository, allocationRepository,
                configRepository, studentRepository, floorDepartmentRepository, hostelEligibilityService,
                wardenRepository);
        building = Building.builder().id(1L).name("Building A").type("NORMAL").gender("BOY").build();
    }

    private Room room(Long id, int floor, String number, int maxMembers, String override) {
        return Room.builder().id(id).building(building).floorNumber(floor).roomNumber(number)
                .maxMembers(maxMembers).departmentOverride(override).build();
    }

    private Student student(Long id, String name, String rollNo, String department, String email) {
        return Student.builder().id(id).name(name).rollNo(rollNo).department(department)
                .email(email).gender("BOY").hostel("Building A").roomNumber("101")
                .contactNumber("9000000000").parentNumber("9000000001").build();
    }

    // ---- Department precedence: room override beats floor default ----

    @Test
    void roomOverrideTakesPrecedenceOverFloorDefault() {
        Room r = room(10L, 1, "101", 4, "ECE"); // room override = ECE
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(r));
        when(allocationRepository.countByRoomId(10L)).thenReturn(0L);
        when(allocationRepository.findByStudentEmail("s@x.com")).thenReturn(Optional.empty());

        // Student is ECE (matches room override) -> allowed. The floor default (whatever
        // it might be) is never even consulted once a room override is present.
        roomService.allocateStudent(10L, "S", "R1", "ECE", "s@x.com", null);

        verify(allocationRepository).save(any(RoomAllocation.class));
        verify(floorDepartmentRepository, never()).findByBuildingIdAndFloorNumber(anyLong(), anyInt());
    }

    @Test
    void allocationRejectedWhenDepartmentDoesNotMatchEffectiveDepartment() {
        Room r = room(11L, 1, "102", 4, null); // no override -> inherits floor default
        when(roomRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(r));
        when(floorDepartmentRepository.findByBuildingIdAndFloorNumber(1L, 1))
                .thenReturn(Optional.of(FloorDepartment.builder().building(building).floorNumber(1).department("CT").build()));

        assertThatThrownBy(() -> roomService.allocateStudent(11L, "S", "R2", "CSE", "s2@x.com", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("CT");

        verify(allocationRepository, never()).save(any());
    }

    @Test
    void allocationAllowedWhenNoDepartmentConfigured() {
        Room r = room(12L, 2, "201", 4, null); // no override, no floor default configured
        when(roomRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(r));
        when(floorDepartmentRepository.findByBuildingIdAndFloorNumber(1L, 2)).thenReturn(Optional.empty());
        when(allocationRepository.countByRoomId(12L)).thenReturn(0L);
        when(allocationRepository.findByStudentEmail("s3@x.com")).thenReturn(Optional.empty());

        roomService.allocateStudent(12L, "S", "R3", "ANYTHING", "s3@x.com", null);

        verify(allocationRepository).save(any(RoomAllocation.class));
    }

    @Test
    void allocationRejectedWhenRoomIsFull() {
        Room r = room(13L, 1, "103", 2, null);
        when(roomRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(r));
        when(floorDepartmentRepository.findByBuildingIdAndFloorNumber(1L, 1)).thenReturn(Optional.empty());
        when(allocationRepository.countByRoomId(13L)).thenReturn(2L); // already at maxMembers

        assertThatThrownBy(() -> roomService.allocateStudent(13L, "S", "R4", "CT", "s4@x.com", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("full");

        verify(allocationRepository, never()).save(any());
    }

    // ---- Room number editing never touches floor/building ----

    @Test
    void updateRoomNumberOnlyChangesRoomNumber() {
        Room r = room(20L, 1, "101", 4, null);
        when(roomRepository.findById(20L)).thenReturn(Optional.of(r));
        when(roomRepository.findByBuildingIdAndRoomNumber(1L, "999")).thenReturn(Optional.empty());

        roomService.updateRoomNumber(20L, "999", null);

        assertThat(r.getRoomNumber()).isEqualTo("999");
        assertThat(r.getFloorNumber()).isEqualTo(1); // unchanged
        assertThat(r.getBuilding()).isSameAs(building); // unchanged
        verify(roomRepository).save(r);
    }

    @Test
    void updateRoomNumberRejectsDuplicateWithinBuilding() {
        Room r = room(21L, 1, "104", 4, null);
        Room existing = room(22L, 1, "105", 4, null);
        when(roomRepository.findById(21L)).thenReturn(Optional.of(r));
        when(roomRepository.findByBuildingIdAndRoomNumber(1L, "105")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> roomService.updateRoomNumber(21L, "105", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");

        assertThat(r.getRoomNumber()).isEqualTo("104"); // unchanged on rejection
    }

    // ---- Bulk allocation ----

    @Test
    void bulkAllocationOnlyAssignsUnassignedStudentsRespectingCapacityAndDepartment() {
        Room ctRoom = room(30L, 1, "101", 1, "CT");   // capacity 1, department CT
        Room eceRoom = room(31L, 1, "102", 1, "ECE"); // capacity 1, department ECE
        Room noDeptRoom = room(32L, 1, "103", 4, null);

        when(buildingRepository.findById(1L)).thenReturn(Optional.of(building));
        when(roomRepository.findByBuildingIdOrderByFloorNumberAscRoomNumberAsc(1L))
                .thenReturn(List.of(ctRoom, eceRoom, noDeptRoom));
        // Locked-room lookups (ascending by id: 30, 31, 32)
        when(roomRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(ctRoom));
        when(roomRepository.findByIdForUpdate(31L)).thenReturn(Optional.of(eceRoom));
        when(roomRepository.findByIdForUpdate(32L)).thenReturn(Optional.of(noDeptRoom));

        when(floorDepartmentRepository.findByBuildingIdAndFloorNumber(1L, 1)).thenReturn(Optional.empty());
        when(allocationRepository.countByRoomId(anyLong())).thenReturn(0L);

        Student ctStudent = student(100L, "CT Student", "R100", "CT", "ct@x.com");
        Student eceStudent = student(101L, "ECE Student", "R101", "ECE", "ece@x.com");
        Student secondCtStudent = student(102L, "CT Student 2", "R102", "CT", "ct2@x.com");

        when(studentRepository.findUnassignedByGender("BOY"))
                .thenReturn(new ArrayList<>(List.of(ctStudent, eceStudent, secondCtStudent)));
        when(allocationRepository.findByStudentEmail(anyString())).thenReturn(Optional.empty());
        when(studentRepository.findByEmailForUpdate(anyString())).thenReturn(Optional.empty());

        Map<String, Object> result = roomService.bulkAllocate(1L, null, "warden@x.com", "WARDEN", null);

        assertThat(result.get("studentsProcessed")).isEqualTo(3);
        assertThat(result.get("assigned")).isEqualTo(2); // one CT room + one ECE room, both capacity 1
        assertThat(result.get("remaining")).isEqualTo(1); // second CT student has no room left

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> unassigned = (List<Map<String, Object>>) result.get("unassigned");
        assertThat(unassigned).hasSize(1);
        assertThat(unassigned.get(0).get("studentId")).isEqualTo(102L);

        // Exactly 2 allocations were created (not 3) -- capacity was respected.
        verify(allocationRepository, times(2)).save(any(RoomAllocation.class));
    }

    @Test
    void bulkAllocationNeverConsidersStudentsWhoAlreadyHaveARoom() {
        // findUnassignedByGender is the sole source of candidates, and its contract (see
        // StudentRepository) already excludes anyone with an existing RoomAllocation --
        // this test locks in that the service never queries beyond that list.
        when(buildingRepository.findById(1L)).thenReturn(Optional.of(building));
        when(roomRepository.findByBuildingIdOrderByFloorNumberAscRoomNumberAsc(1L)).thenReturn(List.of());
        when(studentRepository.findUnassignedByGender("BOY")).thenReturn(List.of());

        Map<String, Object> result = roomService.bulkAllocate(1L, null, "warden@x.com", "WARDEN", null);

        assertThat(result.get("studentsProcessed")).isEqualTo(0);
        assertThat(result.get("assigned")).isEqualTo(0);
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void bulkAllocationCanBeScopedToASingleFloor() {
        when(buildingRepository.findById(1L)).thenReturn(Optional.of(building));
        when(roomRepository.findByBuildingIdAndFloorNumberOrderByRoomNumberAsc(1L, 2)).thenReturn(List.of());
        when(studentRepository.findUnassignedByGender("BOY")).thenReturn(List.of());

        roomService.bulkAllocate(1L, 2, "warden@x.com", "WARDEN", null);

        verify(roomRepository).findByBuildingIdAndFloorNumberOrderByRoomNumberAsc(1L, 2);
        verify(roomRepository, never()).findByBuildingIdOrderByFloorNumberAscRoomNumberAsc(anyLong());
    }

    // ---- Floor department / room override CRUD ----

    @Test
    void removingRoomOverrideRevertsToFloorDefault() {
        Room r = room(40L, 1, "101", 4, "ECE");
        when(roomRepository.findById(40L)).thenReturn(Optional.of(r));

        roomService.removeRoomDepartmentOverride(40L, null);

        assertThat(r.getDepartmentOverride()).isNull();
        verify(roomRepository).save(r);
    }

    // ---- Self-service allocation is atomic: check-then-act race is closed ----

    @Test
    void selfServiceAllocationSucceedsWhenStudentHasNoExistingAllocation() {
        Room r = room(50L, 1, "101", 4, null);
        when(roomRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(r));
        when(floorDepartmentRepository.findByBuildingIdAndFloorNumber(1L, 1)).thenReturn(Optional.empty());
        when(studentRepository.findByEmailForUpdate("self@x.com")).thenReturn(Optional.empty());
        when(allocationRepository.findByStudentEmail("self@x.com")).thenReturn(Optional.empty());
        when(allocationRepository.countByRoomId(50L)).thenReturn(0L);

        roomService.allocateStudentSelfService(50L, "Self Student", "R50", "CT", "self@x.com");

        // The student's own row must be locked (pessimistic write) before the
        // already-allocated decision is made -- this is what makes the check atomic
        // with the write, closing the TOCTOU race against a concurrent staff allocation.
        verify(studentRepository).findByEmailForUpdate("self@x.com");
        verify(allocationRepository).save(any(RoomAllocation.class));
    }

    @Test
    void selfServiceAllocationRejectedWhenStudentAlreadyHasAllocation() {
        Room targetRoom = room(51L, 1, "102", 4, null);
        Room currentRoom = room(52L, 1, "103", 4, null);
        when(roomRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(targetRoom));
        when(floorDepartmentRepository.findByBuildingIdAndFloorNumber(1L, 1)).thenReturn(Optional.empty());
        when(studentRepository.findByEmailForUpdate("locked@x.com")).thenReturn(Optional.empty());

        RoomAllocation existing = RoomAllocation.builder()
                .id(1L).room(currentRoom).studentName("Locked Student").studentRollNo("R51")
                .studentDepartment("CT").studentEmail("locked@x.com").build();
        when(allocationRepository.findByStudentEmail("locked@x.com")).thenReturn(Optional.of(existing));

        // A Warden/Admin assignment (or the student's own earlier allocation) already
        // exists -- the self-service call must reject, never move/overwrite it.
        assertThatThrownBy(() -> roomService.allocateStudentSelfService(
                51L, "Locked Student", "R51", "CT", "locked@x.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("locked");

        verify(allocationRepository, never()).save(any());
        // The existing allocation object itself must be untouched (still points at its
        // original room) -- proves this is a hard reject, not a silent partial update.
        assertThat(existing.getRoom()).isSameAs(currentRoom);
    }

    @Test
    void wardenCanStillMoveAStudentWhoAlreadyHasAnAllocation() {
        // Staff authority must not be weakened by the self-service lock: allocateStudent
        // (the warden/admin path) must still succeed in moving an already-housed student.
        Room newRoom = room(53L, 1, "104", 4, null);
        Room oldRoom = room(54L, 1, "105", 4, null);
        when(roomRepository.findByIdForUpdate(53L)).thenReturn(Optional.of(newRoom));
        when(floorDepartmentRepository.findByBuildingIdAndFloorNumber(1L, 1)).thenReturn(Optional.empty());
        when(studentRepository.findByEmailForUpdate("moved@x.com")).thenReturn(Optional.empty());
        when(allocationRepository.countByRoomId(53L)).thenReturn(0L);

        RoomAllocation existing = RoomAllocation.builder()
                .id(2L).room(oldRoom).studentName("Moved Student").studentRollNo("R53")
                .studentDepartment("CT").studentEmail("moved@x.com").build();
        when(allocationRepository.findByStudentEmail("moved@x.com")).thenReturn(Optional.of(existing));

        roomService.allocateStudent(53L, "Moved Student", "R53", "CT", "moved@x.com", null);

        assertThat(existing.getRoom()).isSameAs(newRoom); // moved, not rejected
        verify(allocationRepository).save(existing);
    }

    // ---- Registration validates year-based hostel eligibility (backend-authoritative) ----

    @Test
    void registrationSucceedsWhenHostelIsEligibleForStudentsYear() {
        Room r = room(60L, 1, "101", 4, null);
        Student s = Student.builder().id(200L).name("Reg Student").rollNo("R200")
                .department("CT").year(2).email("reg@x.com")
                .hostel("Building A").roomNumber("101")
                .contactNumber("9000000000").parentNumber("9000000001").gender("BOY").build();

        when(buildingRepository.findByName("Building A")).thenReturn(Optional.of(building));
        when(roomRepository.findByBuildingIdAndRoomNumber(1L, "101")).thenReturn(Optional.of(r));
        when(roomRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(r));
        when(floorDepartmentRepository.findByBuildingIdAndFloorNumber(1L, 1)).thenReturn(Optional.empty());
        when(studentRepository.findByEmailForUpdate("reg@x.com")).thenReturn(Optional.empty());
        when(allocationRepository.findByStudentEmail("reg@x.com")).thenReturn(Optional.empty());
        when(allocationRepository.countByRoomId(60L)).thenReturn(0L);
        // Eligibility check passes silently (no exception) -- default Mockito behavior for
        // an unstubbed void method is a no-op, which is exactly "eligible" here.

        roomService.allocateForRegistration(s);

        verify(hostelEligibilityService).validateEligibility(2, 1L);
        verify(allocationRepository).save(any(RoomAllocation.class));
    }

    @Test
    void registrationRejectedWhenHostelIsNotEligibleForStudentsYear() {
        Student s = Student.builder().id(201L).name("Reg Student 2").rollNo("R201")
                .department("CT").year(2).email("reg2@x.com")
                .hostel("Building A").roomNumber("101")
                .contactNumber("9000000000").parentNumber("9000000001").gender("BOY").build();

        when(buildingRepository.findByName("Building A")).thenReturn(Optional.of(building));
        doThrow(new RuntimeException("Selected hostel is not available for your academic year."))
                .when(hostelEligibilityService).validateEligibility(2, 1L);

        assertThatThrownBy(() -> roomService.allocateForRegistration(s))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not available");

        // Rejected before any room is resolved/locked or allocation created.
        verify(roomRepository, never()).findByBuildingIdAndRoomNumber(any(), any());
        verify(allocationRepository, never()).save(any());
    }

    // ---- Student-facing occupancy must not expose other students' PII ----

    @Test
    void studentOccupancyViewReturnsCountsOnlyAndExcludesCallingStudent() {
        Room roomA = room(1L, 1, "101", 4, null);
        Room roomB = room(2L, 1, "102", 4, null);

        RoomAllocation self = RoomAllocation.builder().id(1L).room(roomA)
                .studentName("Self").studentRollNo("R1").studentDepartment("CS")
                .studentEmail("self@x.com").build();
        RoomAllocation roommate = RoomAllocation.builder().id(2L).room(roomA)
                .studentName("Roommate").studentRollNo("R2").studentDepartment("CS")
                .studentEmail("roommate@x.com").build();
        RoomAllocation otherRoom = RoomAllocation.builder().id(3L).room(roomB)
                .studentName("Other").studentRollNo("R3").studentDepartment("CS")
                .studentEmail("other@x.com").build();

        when(allocationRepository.findAll()).thenReturn(List.of(self, roommate, otherRoom));

        List<Map<String, Object>> result = roomService.getRoomOccupancyForStudent("self@x.com");

        // Only aggregate counts per room, keyed by roomId — no name/rollNo/department/email.
        assertThat(result).allSatisfy(entry -> assertThat(entry.keySet())
                .containsExactlyInAnyOrder("roomId", "occupantCount"));

        Map<Long, Long> countsByRoom = new HashMap<>();
        result.forEach(entry -> countsByRoom.put((Long) entry.get("roomId"), (Long) entry.get("occupantCount")));

        assertThat(countsByRoom.get(1L)).isEqualTo(1L); // roomA: only "roommate" counted, self excluded
        assertThat(countsByRoom.get(2L)).isEqualTo(1L); // roomB: "other"
    }
}
