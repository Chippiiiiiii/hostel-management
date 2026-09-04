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
    @Mock private StudentRepository studentRepository;
    @Mock private FloorDepartmentRepository floorDepartmentRepository;
    @Mock private HostelEligibilityService hostelEligibilityService;
    @Mock private WardenRepository wardenRepository;
    @Mock private BuildingConfigService buildingConfigService;

    private RoomService roomService;

    private Building building;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(buildingRepository, roomRepository, allocationRepository,
                studentRepository, floorDepartmentRepository, hostelEligibilityService,
                wardenRepository, buildingConfigService);
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

    // ---- Gender eligibility: server-side, cannot be bypassed by a client-supplied roomId ----
    // Every allocation entry point funnels through performAllocation's checkGenderEligibility
    // call, which compares the room's building gender against the authoritative Student.gender
    // fetched server-side via findByEmailForUpdate -- never a client-supplied value. Each test
    // below stubs findByEmailForUpdate to return a real Student (unlike most tests above, which
    // rely on the default empty stub and so never exercise this check), and pairs a matching
    // department + available capacity so gender is provably the reason for the rejection.

    @Test
    void allocateStudentAllowsSameGenderRoom() {
        Room r = room(70L, 1, "101", 4, null); // building is BOY (see setUp)
        when(roomRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(r));
        when(floorDepartmentRepository.findByBuildingIdAndFloorNumber(1L, 1)).thenReturn(Optional.empty());
        when(allocationRepository.countByRoomId(70L)).thenReturn(0L);
        when(allocationRepository.findByStudentEmail("boy@x.com")).thenReturn(Optional.empty());
        Student boyStudent = student(300L, "Boy Student", "R300", "CT", "boy@x.com"); // gender BOY
        when(studentRepository.findByEmailForUpdate("boy@x.com")).thenReturn(Optional.of(boyStudent));

        roomService.allocateStudent(70L, "Boy Student", "R300", "CT", "boy@x.com", null);

        verify(allocationRepository).save(any(RoomAllocation.class));
    }

    @Test
    void allocateStudentRejectsOppositeGenderRoomEvenWithCapacityAndMatchingDepartment() {
        Room r = room(71L, 1, "102", 4, null); // building is BOY (see setUp)
        when(roomRepository.findByIdForUpdate(71L)).thenReturn(Optional.of(r));
        when(floorDepartmentRepository.findByBuildingIdAndFloorNumber(1L, 1)).thenReturn(Optional.empty());
        // Deliberately not stubbing countByRoomId/findByStudentEmail: the gender check must
        // reject before either is ever consulted, capacity/department notwithstanding.
        Student girlStudent = Student.builder().id(301L).name("Girl Student").rollNo("R301")
                .department("CT").email("girl@x.com").gender("GIRL")
                .hostel("Building A").roomNumber("101")
                .contactNumber("9000000000").parentNumber("9000000001").build();
        when(studentRepository.findByEmailForUpdate("girl@x.com")).thenReturn(Optional.of(girlStudent));

        assertThatThrownBy(() -> roomService.allocateStudent(71L, "Girl Student", "R301", "CT", "girl@x.com", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("BOY");

        verify(allocationRepository, never()).save(any());
    }

    @Test
    void allocateStudentSelfServiceRejectsOppositeGenderRoom() {
        Room r = room(72L, 1, "103", 4, null); // building is BOY (see setUp)
        when(roomRepository.findByIdForUpdate(72L)).thenReturn(Optional.of(r));
        when(floorDepartmentRepository.findByBuildingIdAndFloorNumber(1L, 1)).thenReturn(Optional.empty());
        Student girlStudent = Student.builder().id(302L).name("Girl Self").rollNo("R302")
                .department("CT").email("girlself@x.com").gender("GIRL")
                .hostel("Building A").roomNumber("101")
                .contactNumber("9000000000").parentNumber("9000000001").build();
        when(studentRepository.findByEmailForUpdate("girlself@x.com")).thenReturn(Optional.of(girlStudent));

        assertThatThrownBy(() -> roomService.allocateStudentSelfService(
                72L, "Girl Self", "R302", "CT", "girlself@x.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("BOY");

        verify(allocationRepository, never()).save(any());
    }

    @Test
    void allocateForRegistrationRejectsOppositeGenderRoom() {
        Room r = room(73L, 1, "104", 4, null); // building is BOY (see setUp)
        Student girlStudent = Student.builder().id(303L).name("Girl Reg").rollNo("R303")
                .department("CT").year(1).email("girlreg@x.com").gender("GIRL")
                .hostel("Building A").roomNumber("104")
                .contactNumber("9000000000").parentNumber("9000000001").build();

        when(buildingRepository.findByName("Building A")).thenReturn(Optional.of(building));
        when(roomRepository.findByBuildingIdAndRoomNumber(1L, "104")).thenReturn(Optional.of(r));
        when(roomRepository.findByIdForUpdate(73L)).thenReturn(Optional.of(r));
        when(floorDepartmentRepository.findByBuildingIdAndFloorNumber(1L, 1)).thenReturn(Optional.empty());
        // findByEmailForUpdate is keyed by email, and performAllocation looks the just-
        // "registered" student back up by the same email allocateForRegistration was called
        // with -- simulating that the student row is already visible within the transaction.
        when(studentRepository.findByEmailForUpdate("girlreg@x.com")).thenReturn(Optional.of(girlStudent));

        assertThatThrownBy(() -> roomService.allocateForRegistration(girlStudent))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("BOY");

        verify(allocationRepository, never()).save(any());
    }

    @Test
    void allocateForRegistrationAllowsMatchingGenderRoom() {
        Room r = room(74L, 1, "105", 4, null); // building is BOY (see setUp)
        Student boyStudent = Student.builder().id(304L).name("Boy Reg").rollNo("R304")
                .department("CT").year(1).email("boyreg@x.com").gender("BOY")
                .hostel("Building A").roomNumber("105")
                .contactNumber("9000000000").parentNumber("9000000001").build();

        when(buildingRepository.findByName("Building A")).thenReturn(Optional.of(building));
        when(roomRepository.findByBuildingIdAndRoomNumber(1L, "105")).thenReturn(Optional.of(r));
        when(roomRepository.findByIdForUpdate(74L)).thenReturn(Optional.of(r));
        when(floorDepartmentRepository.findByBuildingIdAndFloorNumber(1L, 1)).thenReturn(Optional.empty());
        when(allocationRepository.countByRoomId(74L)).thenReturn(0L);
        when(allocationRepository.findByStudentEmail("boyreg@x.com")).thenReturn(Optional.empty());
        when(studentRepository.findByEmailForUpdate("boyreg@x.com")).thenReturn(Optional.of(boyStudent));

        roomService.allocateForRegistration(boyStudent);

        verify(allocationRepository).save(any(RoomAllocation.class));
    }

    @Test
    void genderCheckIsSkippedWhenNoAuthoritativeStudentRecordExists() {
        // Mirrors the convention checkDepartmentEligibility already follows: with nothing to
        // validate against (findByEmailForUpdate returns empty, as in most tests above), the
        // allocation proceeds rather than failing closed on missing data unrelated to gender.
        Room r = room(75L, 1, "106", 4, null);
        when(roomRepository.findByIdForUpdate(75L)).thenReturn(Optional.of(r));
        when(floorDepartmentRepository.findByBuildingIdAndFloorNumber(1L, 1)).thenReturn(Optional.empty());
        when(allocationRepository.countByRoomId(75L)).thenReturn(0L);
        when(allocationRepository.findByStudentEmail("nostudentrow@x.com")).thenReturn(Optional.empty());
        when(studentRepository.findByEmailForUpdate("nostudentrow@x.com")).thenReturn(Optional.empty());

        roomService.allocateStudent(75L, "No Row", "R305", "CT", "nostudentrow@x.com", null);

        verify(allocationRepository).save(any(RoomAllocation.class));
    }

    @Test
    void genderCheckIsSkippedWhenBuildingGenderIsBlankRatherThanNull() {
        // Discovered during live-environment verification: the buildings table's gender
        // column is NOT NULL, so an unset value is persisted as "" rather than SQL NULL.
        // A blank string must be treated the same as a genuinely absent constraint --
        // otherwise every allocation into a building with no gender configured yet would
        // be wrongly rejected (blank never equals a real gender value).
        Building blankGenderBuilding = Building.builder().id(9L).name("Building Blank")
                .type("NORMAL").gender("").build();
        Room r = room(76L, 1, "107", 4, null);
        r.setBuilding(blankGenderBuilding);
        when(roomRepository.findByIdForUpdate(76L)).thenReturn(Optional.of(r));
        when(floorDepartmentRepository.findByBuildingIdAndFloorNumber(9L, 1)).thenReturn(Optional.empty());
        when(allocationRepository.countByRoomId(76L)).thenReturn(0L);
        when(allocationRepository.findByStudentEmail("anygender@x.com")).thenReturn(Optional.empty());
        Student anyGenderStudent = student(306L, "Any Gender", "R306", "CT", "anygender@x.com");
        when(studentRepository.findByEmailForUpdate("anygender@x.com")).thenReturn(Optional.of(anyGenderStudent));

        roomService.allocateStudent(76L, "Any Gender", "R306", "CT", "anygender@x.com", null);

        verify(allocationRepository).save(any(RoomAllocation.class));
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
