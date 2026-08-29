package com.outpass.portal.service;

import com.outpass.portal.model.entity.*;
import com.outpass.portal.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomService {

    private final BuildingRepository buildingRepository;
    private final RoomRepository roomRepository;
    private final RoomAllocationRepository allocationRepository;
    private final RoomConfigRepository configRepository;
    private final StudentRepository studentRepository;
    private final FloorDepartmentRepository floorDepartmentRepository;
    private final HostelEligibilityService hostelEligibilityService;
    private final WardenRepository wardenRepository;

    // wardenHostel == null means the caller is Admin (or another unrestricted staff role)
    // and sees every hostel; a non-null value restricts results/mutations to the building
    // whose name matches that hostel, since Building.name IS the hostel (see
    // student.setHostel(room.getBuilding().getName()) in performAllocation below).
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getBuildings(String wardenHostel) {
        List<Building> buildings = buildingRepository.findAll().stream()
                .filter(b -> wardenHostel == null || wardenHostel.equals(b.getName()))
                .collect(Collectors.toList());
        return buildings.stream().map(this::mapBuilding).collect(Collectors.toList());
    }

    private void verifyBuildingOwnership(Building building, String wardenHostel) {
        if (wardenHostel != null && !wardenHostel.equals(building.getName())) {
            throw new RuntimeException("You can only manage your own hostel");
        }
    }

    private void verifyRoomOwnership(Room room, String wardenHostel) {
        if (wardenHostel != null && !wardenHostel.equals(room.getBuilding().getName())) {
            throw new RuntimeException("You can only manage rooms in your own hostel");
        }
    }

    @Transactional
    public Map<String, Object> renameBuilding(Long buildingId, String newName, String wardenHostel) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new RuntimeException("Building not found"));
        verifyBuildingOwnership(building, wardenHostel);
        String oldName = building.getName();
        building.setName(newName);
        buildingRepository.save(building);

        // Warden.hostel and Student.hostel are free-text copies of the building name (see
        // AdminService#createWarden and performAllocation below) -- every hostel-scoped
        // query/authorization check in the app, including verifyBuildingOwnership/
        // verifyRoomOwnership above, relies on that string staying in sync. Without this
        // cascade, renaming a building would silently lock its own warden out and desync
        // every already-allocated student's hostel field.
        if (!oldName.equals(newName)) {
            List<Warden> wardens = wardenRepository.findByHostel(oldName);
            wardens.forEach(w -> w.setHostel(newName));
            wardenRepository.saveAll(wardens);

            List<Student> students = studentRepository.findByHostel(oldName);
            students.forEach(s -> s.setHostel(newName));
            studentRepository.saveAll(students);
        }
        return mapBuilding(building);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        configRepository.findByConfigKey("max_rooms_per_floor")
                .ifPresent(c -> config.put("maxRoomsPerFloor", Integer.parseInt(c.getConfigValue())));
        configRepository.findByConfigKey("max_members_per_room")
                .ifPresent(c -> config.put("maxMembersPerRoom", Integer.parseInt(c.getConfigValue())));
        configRepository.findByConfigKey("wifi_allowed_subnets")
                .ifPresent(c -> config.put("wifiAllowedSubnets", c.getConfigValue()));
        config.putIfAbsent("maxRoomsPerFloor", 10);
        config.putIfAbsent("maxMembersPerRoom", 6);
        config.putIfAbsent("wifiAllowedSubnets", "");
        return config;
    }

    @Transactional
    public void updateConfig(int maxRoomsPerFloor, int maxMembersPerRoom) {
        updateConfigValue("max_rooms_per_floor", String.valueOf(maxRoomsPerFloor));
        updateConfigValue("max_members_per_room", String.valueOf(maxMembersPerRoom));
        roomRepository.updateAllMaxMembers(maxMembersPerRoom);
    }

    @Transactional
    public void updateWifiSubnets(String subnets) {
        updateConfigValue("wifi_allowed_subnets", subnets);
    }

    private void updateConfigValue(String key, String value) {
        RoomConfig config = configRepository.findByConfigKey(key)
                .orElse(RoomConfig.builder().configKey(key).build());
        config.setConfigValue(value);
        configRepository.save(config);
    }

    @Transactional
    public void updateRoomMaxMembers(Long roomId, int maxMembers, String wardenHostel) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        verifyRoomOwnership(room, wardenHostel);
        room.setMaxMembers(maxMembers);
        roomRepository.save(room);
    }

    @Transactional
    public Map<String, Object> addFloor(Long buildingId, String wardenHostel) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new RuntimeException("Building not found"));
        verifyBuildingOwnership(building, wardenHostel);

        List<Room> existingRooms = roomRepository.findByBuildingIdOrderByFloorNumberAscRoomNumberAsc(buildingId);
        int newFloorNumber = existingRooms.stream()
                .mapToInt(Room::getFloorNumber)
                .max().orElse(0) + 1;

        Map<String, Object> config = getConfig();
        int roomCount = (int) config.get("maxRoomsPerFloor");
        int maxMembers = (int) config.get("maxMembersPerRoom");

        List<Room> newRooms = new ArrayList<>();
        for (int i = 1; i <= roomCount; i++) {
            newRooms.add(Room.builder()
                    .building(building)
                    .floorNumber(newFloorNumber)
                    .roomNumber(newFloorNumber + String.format("%02d", i))
                    .maxMembers(maxMembers)
                    .build());
        }
        roomRepository.saveAll(newRooms);
        return Map.of("floorNumber", newFloorNumber, "rooms", newRooms.size());
    }

    @Transactional
    public void removeFloor(Long buildingId, int floorNumber, String wardenHostel) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new RuntimeException("Building not found"));
        verifyBuildingOwnership(building, wardenHostel);
        List<Room> rooms = roomRepository.findByBuildingIdAndFloorNumberOrderByRoomNumberAsc(buildingId, floorNumber);
        for (Room room : rooms) {
            long occupants = allocationRepository.countByRoomId(room.getId());
            if (occupants > 0) {
                throw new RuntimeException("Cannot remove floor with allocated students");
            }
        }
        roomRepository.deleteAll(rooms);
        floorDepartmentRepository.findByBuildingIdAndFloorNumber(buildingId, floorNumber)
                .ifPresent(floorDepartmentRepository::delete);
    }

    @Transactional
    public void addRoomToFloor(Long buildingId, int floorNumber, String wardenHostel) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new RuntimeException("Building not found"));
        verifyBuildingOwnership(building, wardenHostel);

        long currentCount = roomRepository.countByBuildingIdAndFloorNumber(buildingId, floorNumber);
        int newRoomIndex = (int) currentCount + 1;

        Map<String, Object> config = getConfig();
        int maxMembers = (int) config.get("maxMembersPerRoom");

        Room room = Room.builder()
                .building(building)
                .floorNumber(floorNumber)
                .roomNumber(floorNumber + String.format("%02d", newRoomIndex))
                .maxMembers(maxMembers)
                .build();
        roomRepository.save(room);
    }

    @Transactional
    public void removeLastRoomFromFloor(Long buildingId, int floorNumber, String wardenHostel) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new RuntimeException("Building not found"));
        verifyBuildingOwnership(building, wardenHostel);
        List<Room> rooms = roomRepository.findByBuildingIdAndFloorNumberOrderByRoomNumberAsc(buildingId, floorNumber);
        if (rooms.isEmpty()) return;
        Room last = rooms.get(rooms.size() - 1);
        long occupants = allocationRepository.countByRoomId(last.getId());
        if (occupants > 0) {
            throw new RuntimeException("Cannot remove room with allocated students");
        }
        roomRepository.delete(last);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllAllocations() {
        List<RoomAllocation> allocations = allocationRepository.findAll();
        return allocations.stream().map(this::mapAllocation).collect(Collectors.toList());
    }

    // Warden-facing equivalent of getAllAllocations(): a warden must only see the
    // name/roll no/department/email of students allocated within their own hostel,
    // not every hostel's roster. "Hostel" has no first-class column on Room/RoomAllocation
    // — it is the owning Building's name (see student.setHostel(room.getBuilding().getName())
    // above), so filter on that instead.
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllocationsByHostel(String hostel) {
        return allocationRepository.findAll().stream()
                .filter(a -> hostel.equals(a.getRoom().getBuilding().getName()))
                .map(this::mapAllocation)
                .collect(Collectors.toList());
    }

    // Student-facing occupancy view: the self-service room picker only needs a per-room
    // headcount to show which rooms have space, so this returns counts only rather than
    // reusing getAllAllocations(), which exposes every student's name/roll no/department/
    // email — full PII with no legitimate need in the student self-service flow.
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRoomOccupancyForStudent(String excludeStudentEmail) {
        Map<Long, Long> countsByRoomId = allocationRepository.findAll().stream()
                .filter(a -> !a.getStudentEmail().equals(excludeStudentEmail))
                .collect(Collectors.groupingBy(a -> a.getRoom().getId(), Collectors.counting()));

        return countsByRoomId.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("roomId", entry.getKey());
                    map.put("occupantCount", entry.getValue());
                    return map;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStudentAllocation(String studentEmail) {
        return allocationRepository.findByStudentEmail(studentEmail)
                .map(this::mapAllocation)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRoommates(String studentEmail) {
        return allocationRepository.findByStudentEmail(studentEmail)
                .map(allocation -> allocationRepository.findByRoomId(allocation.getRoom().getId())
                        .stream()
                        .filter(a -> !a.getStudentEmail().equals(studentEmail))
                        .map(this::mapAllocation)
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    // ==================== Allocation (single) ====================

    // Warden/admin (and other staff) initiated change: always allowed to move a student
    // regardless of whether they already have a room — staff authority is never weakened
    // by the student-side lock enforced in allocateStudentSelfService below.
    @Transactional
    public Map<String, Object> allocateStudent(Long roomId, String name, String rollNo,
                                                String department, String email, String wardenHostel) {
        Room room = lockRoom(roomId);
        verifyRoomOwnership(room, wardenHostel);
        checkDepartmentEligibility(room, department);
        RoomAllocation allocation = performAllocation(room, name, rollNo, department, email, false);
        log.info("Room allocation changed: student={} -> building={} floor={} room={}",
                email, room.getBuilding().getName(), room.getFloorNumber(), room.getRoomNumber());
        return mapAllocation(allocation);
    }

    // Student self-service allocation (POST /student/rooms/allocate). Atomic: the "does
    // this student already have a room" check and the allocation write happen inside one
    // transaction, serialized against every other allocation path (warden/admin single or
    // bulk allocate, registration) via the student-row lock inside performAllocation. If a
    // staff allocation is (or becomes, mid-race) already in place, this always loses and
    // rejects — it never overwrites a staff-made assignment.
    @Transactional
    public Map<String, Object> allocateStudentSelfService(Long roomId, String name, String rollNo,
                                                            String department, String email) {
        Room room = lockRoom(roomId);
        checkDepartmentEligibility(room, department);
        RoomAllocation allocation = performAllocation(room, name, rollNo, department, email, true);
        log.info("Self-service room allocation: student={} -> building={} floor={} room={}",
                email, room.getBuilding().getName(), room.getFloorNumber(), room.getRoomNumber());
        return mapAllocation(allocation);
    }

    // Resolves the student's chosen building/room by name (as submitted at registration),
    // validates capacity and effective department against the real Room entity, and
    // creates the locking RoomAllocation. Intended to run inside the same transaction
    // as the Student insert (see AuthService.registerStudent) so a failure here rolls
    // back the whole registration.
    @Transactional
    public RoomAllocation allocateForRegistration(Student student) {
        Building building = buildingRepository.findByName(student.getHostel())
                .orElseThrow(() -> new RuntimeException(
                        "Selected hostel could not be found. Please choose your room again."));

        // Backend-authoritative: re-validate that this hostel is actually configured as
        // eligible for the student's academic year, regardless of what the frontend showed.
        hostelEligibilityService.validateEligibility(student.getYear(), building.getId());

        Room room = roomRepository.findByBuildingIdAndRoomNumber(building.getId(), student.getRoomNumber())
                .orElseThrow(() -> new RuntimeException(
                        "Selected room could not be found. Please choose your room again."));

        room = lockRoom(room.getId());
        checkDepartmentEligibility(room, student.getDepartment());
        RoomAllocation allocation = performAllocation(
                room, student.getName(), student.getRollNo(), student.getDepartment(), student.getEmail(), false);

        log.info("Registration room allocation: student={} -> building={} floor={} room={}",
                student.getEmail(), room.getBuilding().getName(), room.getFloorNumber(), room.getRoomNumber());
        return allocation;
    }

    private Room lockRoom(Long roomId) {
        return roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
    }

    private void checkDepartmentEligibility(Room room, String studentDepartment) {
        String effective = effectiveDepartment(room);
        if (effective == null) {
            return; // no department restriction configured for this room/floor
        }
        if (studentDepartment == null || !effective.trim().equalsIgnoreCase(studentDepartment.trim())) {
            throw new RuntimeException("This room is reserved for the " + effective +
                    " department; your department (" + studentDepartment + ") is not eligible.");
        }
    }

    // Assumes the room is already locked (see lockRoom) and department eligibility already
    // checked. Shared by single-allocate, registration, self-service, and bulk-allocate so
    // capacity handling and the RoomAllocation/Student sync logic exist in exactly one
    // place.
    //
    // Locks the student's own row first (when one exists) — this is what makes the "does
    // this student already have an allocation" check atomic with the create/update decision
    // across every call path, not just within a single method call: two concurrent
    // allocation attempts for the SAME student (self-service vs. warden/admin, or two
    // overlapping staff actions) always serialize on this lock rather than racing on the
    // room_allocations row, since that row may not exist yet on the very first allocation.
    //
    // rejectIfAlreadyAllocated=true is used only by the student self-service path: if an
    // allocation already exists once the lock is held (whether it existed before this call
    // started, or was created by a concurrent staff action that won the race), this throws
    // instead of overwriting it — staff assignments always win over a student's own request.
    // Warden/admin/bulk/registration pass false, preserving their existing "create or move"
    // behavior.
    private RoomAllocation performAllocation(Room room, String name, String rollNo, String department,
                                              String email, boolean rejectIfAlreadyAllocated) {
        Student student = studentRepository.findByEmailForUpdate(email).orElse(null);

        RoomAllocation allocation = allocationRepository.findByStudentEmail(email).orElse(null);
        if (allocation != null && rejectIfAlreadyAllocated) {
            throw new RuntimeException("Room is locked. Contact your warden or admin to change your room.");
        }

        long currentOccupants = allocationRepository.countByRoomId(room.getId());
        if (currentOccupants >= room.getMaxMembers()) {
            throw new RuntimeException("Room is full. Please select a different room.");
        }

        if (allocation != null) {
            allocation.setRoom(room);
            allocation.setStudent(student);
            allocation.setStudentName(name);
            allocation.setStudentRollNo(rollNo);
            allocation.setStudentDepartment(department);
        } else {
            allocation = RoomAllocation.builder()
                    .room(room)
                    .student(student)
                    .studentName(name)
                    .studentRollNo(rollNo)
                    .studentDepartment(department)
                    .studentEmail(email)
                    .build();
        }

        allocationRepository.save(allocation);

        // Also update the student's hostel and roomNumber fields if they exist in the system
        if (student != null) {
            student.setHostel(room.getBuilding().getName());
            student.setRoomNumber(room.getRoomNumber());
            studentRepository.save(student);
        }

        return allocation;
    }

    @Transactional
    public void removeAllocation(String studentEmail, String wardenHostel) {
        if (wardenHostel != null) {
            allocationRepository.findByStudentEmail(studentEmail).ifPresent(allocation -> {
                if (!wardenHostel.equals(allocation.getRoom().getBuilding().getName())) {
                    throw new RuntimeException("You can only manage allocations in your own hostel");
                }
            });
        }
        allocationRepository.deleteByStudentEmail(studentEmail);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchAllocations(String query) {
        return allocationRepository
                .findByStudentNameContainingIgnoreCaseOrStudentRollNoContainingIgnoreCaseOrStudentDepartmentContainingIgnoreCase(
                        query, query, query)
                .stream().map(this::mapAllocation).collect(Collectors.toList());
    }

    // ==================== Floor / room department ====================

    private String effectiveDepartment(Room room) {
        if (room.getDepartmentOverride() != null && !room.getDepartmentOverride().isBlank()) {
            return room.getDepartmentOverride();
        }
        return floorDepartmentRepository
                .findByBuildingIdAndFloorNumber(room.getBuilding().getId(), room.getFloorNumber())
                .map(FloorDepartment::getDepartment)
                .orElse(null);
    }

    @Transactional
    public Map<String, Object> setFloorDepartment(Long buildingId, int floorNumber, String department, String wardenHostel) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new RuntimeException("Building not found"));
        verifyBuildingOwnership(building, wardenHostel);
        if (department == null || department.isBlank()) {
            throw new RuntimeException("Department is required");
        }
        FloorDepartment floorDepartment = floorDepartmentRepository
                .findByBuildingIdAndFloorNumber(buildingId, floorNumber)
                .orElse(FloorDepartment.builder().building(building).floorNumber(floorNumber).build());
        floorDepartment.setDepartment(department.trim());
        floorDepartmentRepository.save(floorDepartment);

        log.info("Floor department set: building={} floor={} department={}", buildingId, floorNumber, floorDepartment.getDepartment());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("buildingId", buildingId);
        result.put("floorNumber", floorNumber);
        result.put("department", floorDepartment.getDepartment());
        return result;
    }

    @Transactional
    public void setRoomDepartmentOverride(Long roomId, String department, String wardenHostel) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new RuntimeException("Room not found"));
        verifyRoomOwnership(room, wardenHostel);
        if (department == null || department.isBlank()) {
            throw new RuntimeException("Department is required");
        }
        room.setDepartmentOverride(department.trim());
        roomRepository.save(room);
        log.info("Room department override set: room={} department={}", roomId, room.getDepartmentOverride());
    }

    @Transactional
    public void removeRoomDepartmentOverride(Long roomId, String wardenHostel) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new RuntimeException("Room not found"));
        verifyRoomOwnership(room, wardenHostel);
        room.setDepartmentOverride(null);
        roomRepository.save(room);
        log.info("Room department override removed (reverts to floor default): room={}", roomId);
    }

    // ==================== Room number editing ====================

    // Changes only the room's number; floorNumber/building are never read or written here,
    // so a number edit can never move a room to a different floor.
    @Transactional
    public void updateRoomNumber(Long roomId, String newRoomNumber, String wardenHostel) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new RuntimeException("Room not found"));
        verifyRoomOwnership(room, wardenHostel);
        String trimmed = newRoomNumber == null ? "" : newRoomNumber.trim();
        if (trimmed.isEmpty()) {
            throw new RuntimeException("Room number is required");
        }
        if (!trimmed.equals(room.getRoomNumber())) {
            roomRepository.findByBuildingIdAndRoomNumber(room.getBuilding().getId(), trimmed)
                    .ifPresent(existing -> {
                        throw new RuntimeException("Room number " + trimmed + " already exists in this building");
                    });
        }
        String oldNumber = room.getRoomNumber();
        room.setRoomNumber(trimmed);
        roomRepository.save(room);
        log.info("Room number changed: room={} building={} floor={} {} -> {}",
                roomId, room.getBuilding().getName(), room.getFloorNumber(), oldNumber, trimmed);
    }

    // ==================== Bulk allocation ====================

    // Only ever considers students with zero existing RoomAllocation rows; never moves an
    // already-housed student. Scoped to one building (required), optionally one floor.
    // Runs in a single transaction; every candidate room is locked up front (ascending by
    // id) so this can't race with a concurrent single-allocate or another bulk-allocate call.
    @Transactional
    public Map<String, Object> bulkAllocate(Long buildingId, Integer floorNumber,
                                             String initiatorEmail, String initiatorRole, String wardenHostel) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new RuntimeException("Building not found"));
        verifyBuildingOwnership(building, wardenHostel);

        List<Room> candidateRooms = floorNumber != null
                ? roomRepository.findByBuildingIdAndFloorNumberOrderByRoomNumberAsc(buildingId, floorNumber)
                : roomRepository.findByBuildingIdOrderByFloorNumberAscRoomNumberAsc(buildingId);

        List<Room> lockedRooms = candidateRooms.stream()
                .map(Room::getId)
                .sorted()
                .map(this::lockRoom)
                .collect(Collectors.toList());

        Map<Long, Integer> remainingByRoomId = new LinkedHashMap<>();
        Map<Long, String> departmentByRoomId = new LinkedHashMap<>();
        for (Room r : lockedRooms) {
            String dept = effectiveDepartment(r);
            if (dept == null) continue;
            long occupied = allocationRepository.countByRoomId(r.getId());
            int remaining = (int) (r.getMaxMembers() - occupied);
            if (remaining > 0) {
                remainingByRoomId.put(r.getId(), remaining);
                departmentByRoomId.put(r.getId(), normalizeDepartment(dept));
            }
        }

        Map<String, List<Room>> roomsByDepartment = lockedRooms.stream()
                .filter(r -> remainingByRoomId.containsKey(r.getId()))
                .sorted(Comparator.comparing(Room::getFloorNumber).thenComparing(Room::getRoomNumber))
                .collect(Collectors.groupingBy(r -> departmentByRoomId.get(r.getId()),
                        LinkedHashMap::new, Collectors.toList()));

        List<Student> eligible = new ArrayList<>(studentRepository.findUnassignedByGender(building.getGender()));
        eligible.sort(Comparator.comparing(s -> Optional.ofNullable(s.getRollNo()).orElse("")));

        Map<String, List<Student>> studentsByDepartment = eligible.stream()
                .collect(Collectors.groupingBy(s -> normalizeDepartment(s.getDepartment()),
                        LinkedHashMap::new, Collectors.toList()));

        int assigned = 0;
        Set<Long> roomsUsed = new LinkedHashSet<>();
        List<Map<String, Object>> unassigned = new ArrayList<>();
        List<Map<String, Object>> byDepartment = new ArrayList<>();

        for (Map.Entry<String, List<Student>> entry : studentsByDepartment.entrySet()) {
            String dept = entry.getKey();
            List<Student> students = entry.getValue();
            int assignedForDept = 0;

            if (dept.isEmpty()) {
                for (Student s : students) {
                    unassigned.add(unassignedEntry(s, "Student has no department on file"));
                }
            } else {
                List<Room> deptRooms = roomsByDepartment.getOrDefault(dept, List.of());
                for (Student s : students) {
                    Room target = deptRooms.stream()
                            .filter(r -> remainingByRoomId.get(r.getId()) > 0)
                            .findFirst()
                            .orElse(null);
                    if (target == null) {
                        unassigned.add(unassignedEntry(s,
                                "No available room found for department " + s.getDepartment()));
                        continue;
                    }
                    performAllocation(target, s.getName(), s.getRollNo(), s.getDepartment(), s.getEmail(), false);
                    remainingByRoomId.merge(target.getId(), -1, Integer::sum);
                    roomsUsed.add(target.getId());
                    assigned++;
                    assignedForDept++;
                }
            }

            Map<String, Object> deptStat = new LinkedHashMap<>();
            deptStat.put("department", dept.isEmpty() ? null : dept);
            deptStat.put("studentsNeedingRooms", students.size());
            deptStat.put("assigned", assignedForDept);
            byDepartment.add(deptStat);
        }

        log.info("Bulk allocation run by {} ({}): building={} floor={} processed={} assigned={} remaining={} roomsUsed={}",
                initiatorEmail, initiatorRole, buildingId, floorNumber,
                eligible.size(), assigned, unassigned.size(), roomsUsed.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("buildingId", buildingId);
        result.put("floorNumber", floorNumber);
        result.put("studentsProcessed", eligible.size());
        result.put("assigned", assigned);
        result.put("remaining", unassigned.size());
        result.put("roomsUsed", roomsUsed.size());
        result.put("byDepartment", byDepartment);
        result.put("unassigned", unassigned);
        return result;
    }

    private String normalizeDepartment(String department) {
        return department == null ? "" : department.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> unassignedEntry(Student s, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("studentId", s.getId());
        m.put("name", s.getName());
        m.put("rollNo", s.getRollNo());
        m.put("department", s.getDepartment());
        m.put("reason", reason);
        return m;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getBuildingsPublic() {
        List<Building> buildings = buildingRepository.findAll();
        List<RoomAllocation> allAllocations = allocationRepository.findAll();
        Map<Long, Long> roomOccupancy = allAllocations.stream()
                .collect(Collectors.groupingBy(a -> a.getRoom().getId(), Collectors.counting()));

        return buildings.stream().map(building -> {
            List<Room> rooms = roomRepository.findByBuildingIdOrderByFloorNumberAscRoomNumberAsc(building.getId());
            Map<Integer, List<Room>> floorMap = rooms.stream()
                    .collect(Collectors.groupingBy(Room::getFloorNumber, TreeMap::new, Collectors.toList()));

            List<Map<String, Object>> floors = new ArrayList<>();
            for (Map.Entry<Integer, List<Room>> entry : floorMap.entrySet()) {
                List<Map<String, Object>> roomList = entry.getValue().stream().map(r -> {
                    long occupied = roomOccupancy.getOrDefault(r.getId(), 0L);
                    Map<String, Object> roomMap = new LinkedHashMap<>();
                    roomMap.put("id", r.getId());
                    roomMap.put("roomNumber", r.getRoomNumber());
                    roomMap.put("maxMembers", r.getMaxMembers());
                    roomMap.put("occupied", occupied);
                    roomMap.put("available", r.getMaxMembers() - occupied);
                    return roomMap;
                }).collect(Collectors.toList());

                floors.add(Map.of(
                        "floorNumber", entry.getKey(),
                        "rooms", roomList
                ));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", building.getId());
            result.put("name", building.getName());
            result.put("type", building.getType());
            result.put("gender", building.getGender());
            result.put("floors", floors);
            return result;
        }).collect(Collectors.toList());
    }

    // Same shape as getBuildingsPublic(), filtered to only the hostels an Admin has
    // configured as eligible for the given academic year (see HostelEligibilityService).
    // Used by the registration flow so the frontend never even receives a disallowed
    // hostel to render, on top of the hard backend-side check in allocateForRegistration.
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getBuildingsPublicForYear(Integer year) {
        Set<Long> allowedBuildingIds = hostelEligibilityService.getAllowedBuildingIds(year);
        return getBuildingsPublic().stream()
                .filter(b -> allowedBuildingIds.contains((Long) b.get("id")))
                .collect(Collectors.toList());
    }

    private Map<String, Object> mapBuilding(Building building) {
        List<Room> rooms = roomRepository.findByBuildingIdOrderByFloorNumberAscRoomNumberAsc(building.getId());
        Map<Integer, String> floorDepartments = floorDepartmentRepository.findByBuildingId(building.getId())
                .stream()
                .collect(Collectors.toMap(FloorDepartment::getFloorNumber, FloorDepartment::getDepartment));

        Map<Integer, List<Room>> floorMap = rooms.stream()
                .collect(Collectors.groupingBy(Room::getFloorNumber, TreeMap::new, Collectors.toList()));

        List<Map<String, Object>> floors = new ArrayList<>();
        for (Map.Entry<Integer, List<Room>> entry : floorMap.entrySet()) {
            String floorDefault = floorDepartments.get(entry.getKey());
            List<Map<String, Object>> roomList = entry.getValue().stream().map(r -> {
                Map<String, Object> roomMap = new LinkedHashMap<>();
                roomMap.put("id", r.getId());
                roomMap.put("roomNumber", r.getRoomNumber());
                roomMap.put("maxMembers", r.getMaxMembers());
                String override = r.getDepartmentOverride();
                String effective = override != null ? override : floorDefault;
                roomMap.put("departmentOverride", override);
                roomMap.put("effectiveDepartment", effective);
                roomMap.put("departmentSource", override != null ? "ROOM_OVERRIDE"
                        : (floorDefault != null ? "FLOOR_DEFAULT" : null));
                return roomMap;
            }).collect(Collectors.toList());

            Map<String, Object> floorEntry = new LinkedHashMap<>();
            floorEntry.put("floorNumber", entry.getKey());
            floorEntry.put("department", floorDefault);
            floorEntry.put("rooms", roomList);
            floors.add(floorEntry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", building.getId());
        result.put("name", building.getName());
        result.put("type", building.getType());
        result.put("gender", building.getGender());
        result.put("floors", floors);
        return result;
    }

    @Transactional
    public Map<String, Object> addBuilding(String name, String type, String gender) {
        Building building = Building.builder()
                .name(name)
                .type(type != null ? type : "NORMAL")
                .gender(gender != null ? gender : "BOY")
                .build();
        buildingRepository.save(building);
        return mapBuilding(building);
    }

    @Transactional
    public void removeBuilding(Long buildingId, String wardenHostel) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new RuntimeException("Building not found"));
        verifyBuildingOwnership(building, wardenHostel);
        long allocations = allocationRepository.findAll().stream()
                .filter(a -> a.getRoom().getBuilding().getId().equals(buildingId))
                .count();
        if (allocations > 0) {
            throw new RuntimeException("Cannot remove building with allocated students. Remove all students first.");
        }
        buildingRepository.deleteById(buildingId);
    }

    @Transactional
    public Map<String, Object> updateBuildingGender(Long buildingId, String gender, String wardenHostel) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new RuntimeException("Building not found"));
        verifyBuildingOwnership(building, wardenHostel);
        building.setGender(gender);
        buildingRepository.save(building);
        return mapBuilding(building);
    }

    @Transactional
    public Map<String, Object> updateBuildingType(Long buildingId, String type, String wardenHostel) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new RuntimeException("Building not found"));
        verifyBuildingOwnership(building, wardenHostel);
        building.setType(type);
        buildingRepository.save(building);
        return mapBuilding(building);
    }

    private Map<String, Object> mapAllocation(RoomAllocation a) {
        Room room = a.getRoom();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", a.getId());
        map.put("buildingId", room.getBuilding().getId());
        map.put("buildingName", room.getBuilding().getName());
        map.put("floor", room.getFloorNumber());
        map.put("roomNumber", room.getRoomNumber());
        map.put("roomId", room.getId());
        map.put("name", a.getStudentName());
        map.put("rollNo", a.getStudentRollNo());
        map.put("department", a.getStudentDepartment());
        map.put("studentEmail", a.getStudentEmail());
        map.put("allocatedAt", a.getAllocatedAt());
        return map;
    }
}
