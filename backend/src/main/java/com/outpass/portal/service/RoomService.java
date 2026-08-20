package com.outpass.portal.service;

import com.outpass.portal.model.entity.*;
import com.outpass.portal.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final BuildingRepository buildingRepository;
    private final RoomRepository roomRepository;
    private final RoomAllocationRepository allocationRepository;
    private final RoomConfigRepository configRepository;
    private final StudentRepository studentRepository;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getBuildings() {
        List<Building> buildings = buildingRepository.findAll();
        return buildings.stream().map(this::mapBuilding).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> renameBuilding(Long buildingId, String newName) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new RuntimeException("Building not found"));
        building.setName(newName);
        buildingRepository.save(building);
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
    public void updateRoomMaxMembers(Long roomId, int maxMembers) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        room.setMaxMembers(maxMembers);
        roomRepository.save(room);
    }

    @Transactional
    public Map<String, Object> addFloor(Long buildingId) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new RuntimeException("Building not found"));

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
    public void removeFloor(Long buildingId, int floorNumber) {
        List<Room> rooms = roomRepository.findByBuildingIdAndFloorNumberOrderByRoomNumberAsc(buildingId, floorNumber);
        for (Room room : rooms) {
            long occupants = allocationRepository.countByRoomId(room.getId());
            if (occupants > 0) {
                throw new RuntimeException("Cannot remove floor with allocated students");
            }
        }
        roomRepository.deleteAll(rooms);
    }

    @Transactional
    public void addRoomToFloor(Long buildingId, int floorNumber) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new RuntimeException("Building not found"));

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
    public void removeLastRoomFromFloor(Long buildingId, int floorNumber) {
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

    @Transactional
    public Map<String, Object> allocateStudent(Long roomId, String name, String rollNo,
                                                String department, String email) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        long currentOccupants = allocationRepository.countByRoomId(roomId);
        if (currentOccupants >= room.getMaxMembers()) {
            throw new RuntimeException("Room is full. Please select a different room.");
        }

        Student student = studentRepository.findByEmail(email).orElse(null);

        // Update existing allocation or create new one
        RoomAllocation allocation = allocationRepository.findByStudentEmail(email).orElse(null);
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

        return mapAllocation(allocation);
    }

    @Transactional
    public void removeAllocation(String studentEmail) {
        allocationRepository.deleteByStudentEmail(studentEmail);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchAllocations(String query) {
        return allocationRepository
                .findByStudentNameContainingIgnoreCaseOrStudentRollNoContainingIgnoreCaseOrStudentDepartmentContainingIgnoreCase(
                        query, query, query)
                .stream().map(this::mapAllocation).collect(Collectors.toList());
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

    private Map<String, Object> mapBuilding(Building building) {
        List<Room> rooms = roomRepository.findByBuildingIdOrderByFloorNumberAscRoomNumberAsc(building.getId());

        Map<Integer, List<Room>> floorMap = rooms.stream()
                .collect(Collectors.groupingBy(Room::getFloorNumber, TreeMap::new, Collectors.toList()));

        List<Map<String, Object>> floors = new ArrayList<>();
        for (Map.Entry<Integer, List<Room>> entry : floorMap.entrySet()) {
            List<Map<String, Object>> roomList = entry.getValue().stream().map(r -> {
                Map<String, Object> roomMap = new LinkedHashMap<>();
                roomMap.put("id", r.getId());
                roomMap.put("roomNumber", r.getRoomNumber());
                roomMap.put("maxMembers", r.getMaxMembers());
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
    public void removeBuilding(Long buildingId) {
        long allocations = allocationRepository.findAll().stream()
                .filter(a -> a.getRoom().getBuilding().getId().equals(buildingId))
                .count();
        if (allocations > 0) {
            throw new RuntimeException("Cannot remove building with allocated students. Remove all students first.");
        }
        buildingRepository.deleteById(buildingId);
    }

    @Transactional
    public Map<String, Object> updateBuildingGender(Long buildingId, String gender) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new RuntimeException("Building not found"));
        building.setGender(gender);
        buildingRepository.save(building);
        return mapBuilding(building);
    }

    @Transactional
    public Map<String, Object> updateBuildingType(Long buildingId, String type) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new RuntimeException("Building not found"));
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
