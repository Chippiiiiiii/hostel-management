package com.outpass.portal.service;

import com.outpass.portal.model.entity.AttendanceRecord;
import com.outpass.portal.model.entity.AttendanceSession;
import com.outpass.portal.model.entity.Building;
import com.outpass.portal.model.entity.Student;
import com.outpass.portal.model.enums.AttendanceMethod;
import com.outpass.portal.model.enums.AttendanceStatus;
import com.outpass.portal.model.enums.SessionStatus;
import com.outpass.portal.repository.AttendanceRecordRepository;
import com.outpass.portal.repository.AttendanceSessionRepository;
import com.outpass.portal.repository.BuildingRepository;
import com.outpass.portal.repository.StudentRepository;
import com.outpass.portal.util.SubnetUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

// Every session/config operation here is scoped to a specific Building.id -- see
// backend/AGENTS.md and BuildingConfigService. A warden's assigned-buildings list
// (wardenBuildingIds, resolved by WardenController from WardenBuildingService) governs
// which building a mutation is allowed to target; a student's own hostel (resolved via
// Student.hostel -> Building.name, the same lookup allocateForRegistration in
// RoomService already uses) governs which building's session/config applies to them.
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRecordRepository recordRepository;
    private final StudentRepository studentRepository;
    private final BuildingRepository buildingRepository;
    private final BuildingConfigService buildingConfigService;

    // wardenBuildingIds == null means the caller is Admin and may target any building; a
    // non-null (possibly empty) list restricts the target to buildings actually assigned
    // to the calling warden. Mirrors RoomService.verifyBuildingOwnership exactly.
    private void verifyBuildingOwnership(Long buildingId, List<Long> wardenBuildingIds) {
        if (wardenBuildingIds != null && !wardenBuildingIds.contains(buildingId)) {
            throw new RuntimeException("You can only manage your own hostel");
        }
    }

    // A student has no warden_buildings row -- their building is derived from their own
    // Student.hostel, the same free-text-name-matches-Building.name convention
    // RoomService.allocateForRegistration already relies on. A student whose hostel
    // doesn't resolve to a real building (data problem, not a normal state) gets a clear
    // error rather than silently falling through to any other hostel's session/config.
    private Long resolveStudentBuildingId(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        if (student.getHostel() == null || student.getHostel().isBlank()) {
            throw new RuntimeException("No hostel assigned to this account yet");
        }
        Building building = buildingRepository.findByName(student.getHostel())
                .orElseThrow(() -> new RuntimeException("Your hostel could not be matched to a building. Contact your warden."));
        return building.getId();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> verifyWifi(Long studentId, String clientIp) {
        Long buildingId = resolveStudentBuildingId(studentId);
        String subnets = buildingConfigService.getConfigString("wifi_allowed_subnets", buildingId, "");

        boolean connected = SubnetUtils.isInAnySubnet(clientIp, subnets);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connected", connected);
        result.put("ip", clientIp);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAttendanceConfig(Long buildingId, List<Long> wardenBuildingIds) {
        verifyBuildingOwnership(buildingId, wardenBuildingIds);
        return buildConfigMap(buildingId);
    }

    private Map<String, Object> buildConfigMap(Long buildingId) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("wifiAllowedSubnets", buildingConfigService.getConfigString("wifi_allowed_subnets", buildingId, ""));
        config.put("hostelLatitude", buildingConfigService.getConfigString("hostel_latitude", buildingId, "0"));
        config.put("hostelLongitude", buildingConfigService.getConfigString("hostel_longitude", buildingId, "0"));
        config.put("hostelRadius", buildingConfigService.getConfigString("hostel_radius", buildingId, "50"));
        return config;
    }

    @Transactional
    public void updateAttendanceConfig(Long buildingId, List<Long> wardenBuildingIds,
                                        String wifiSubnets, String latitude, String longitude, String radius) {
        verifyBuildingOwnership(buildingId, wardenBuildingIds);
        buildingConfigService.saveConfigValue("wifi_allowed_subnets", buildingId,
                wifiSubnets == null ? "" : wifiSubnets);
        buildingConfigService.saveConfigValue("hostel_latitude", buildingId, orZero(latitude));
        buildingConfigService.saveConfigValue("hostel_longitude", buildingId, orZero(longitude));
        buildingConfigService.saveConfigValue("hostel_radius", buildingId, orZero(radius));
    }

    private String orZero(String value) {
        return (value == null || value.isEmpty()) ? "0" : value;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getHostelLocation(Long studentId) {
        Long buildingId = resolveStudentBuildingId(studentId);
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("latitude", Double.parseDouble(buildingConfigService.getConfigString("hostel_latitude", buildingId, "0")));
        location.put("longitude", Double.parseDouble(buildingConfigService.getConfigString("hostel_longitude", buildingId, "0")));
        location.put("radius", Integer.parseInt(buildingConfigService.getConfigString("hostel_radius", buildingId, "50")));
        return location;
    }

    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getActiveSession(Long buildingId, List<Long> wardenBuildingIds) {
        verifyBuildingOwnership(buildingId, wardenBuildingIds);
        return sessionRepository.findByBuilding_IdAndStatus(buildingId, SessionStatus.ACTIVE)
                .map(this::mapSession)
                .orElse(null);
    }

    // Student-facing equivalent of getActiveSession: resolves the student's own building
    // rather than trusting a client-supplied buildingId, since a student has no
    // legitimate reason to ask about any building other than their own.
    @Transactional(readOnly = true)
    public Map<String, Object> getActiveSessionForStudent(Long studentId) {
        Long buildingId = resolveStudentBuildingId(studentId);
        return sessionRepository.findByBuilding_IdAndStatus(buildingId, SessionStatus.ACTIVE)
                .map(this::mapSession)
                .orElse(null);
    }

    @Transactional
    public Map<String, Object> startSession(Long wardenId, Long buildingId, List<Long> wardenBuildingIds) {
        verifyBuildingOwnership(buildingId, wardenBuildingIds);
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new RuntimeException("Building not found"));

        // Close any existing active session for THIS building only -- a session already
        // running for a different building must be left untouched.
        sessionRepository.findByBuilding_IdAndStatus(buildingId, SessionStatus.ACTIVE).ifPresent(session -> {
            session.setStatus(SessionStatus.CLOSED);
            session.setStoppedAt(java.time.LocalDateTime.now(IST));
            sessionRepository.save(session);
        });

        AttendanceSession session = AttendanceSession.builder()
                .building(building)
                .startedBy(wardenId)
                .status(SessionStatus.ACTIVE)
                .build();
        sessionRepository.save(session);
        return mapSession(session);
    }

    @Transactional
    public void stopSession(Long buildingId, List<Long> wardenBuildingIds) {
        verifyBuildingOwnership(buildingId, wardenBuildingIds);
        AttendanceSession session = sessionRepository.findByBuilding_IdAndStatus(buildingId, SessionStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("No active session"));
        session.setStatus(SessionStatus.CLOSED);
        session.setStoppedAt(java.time.LocalDateTime.now(IST));
        sessionRepository.save(session);
    }

    @Transactional
    public Map<String, Object> markAttendance(Long studentId, String method,
                                               Double latitude, Double longitude, Integer distance,
                                               String clientIp) {
        Long buildingId = resolveStudentBuildingId(studentId);
        AttendanceSession session = sessionRepository.findByBuilding_IdAndStatus(buildingId, SessionStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("No active attendance session"));

        LocalDate today = LocalDate.now(IST);
        recordRepository.findByStudentIdAndDate(studentId, today).ifPresent(r -> {
            throw new RuntimeException("Attendance already marked for today");
        });

        AttendanceMethod attendanceMethod;
        try {
            attendanceMethod = AttendanceMethod.valueOf(method);
        } catch (Exception e) {
            throw new RuntimeException("Invalid attendance method");
        }

        // The client only ever *proposes* how it wants to verify presence; the server
        // independently re-derives whether that proposal actually holds before trusting
        // it, rather than accepting the client's self-reported connected/withinRange
        // result. This closes the gap where a request could otherwise be crafted (e.g.
        // via curl) to claim WIFI/GEO_BIOMETRIC success from anywhere. Verification is
        // against the student's OWN building's config -- never any other hostel's.
        double serverDistance = 0;
        if (attendanceMethod == AttendanceMethod.WIFI) {
            String subnets = buildingConfigService.getConfigString("wifi_allowed_subnets", buildingId, "");
            if (!SubnetUtils.isInAnySubnet(clientIp, subnets)) {
                throw new RuntimeException("Not connected to hostel WiFi");
            }
        } else {
            if (latitude == null || longitude == null) {
                throw new RuntimeException("Location is required for this verification method");
            }
            double hostelLat = Double.parseDouble(buildingConfigService.getConfigString("hostel_latitude", buildingId, "0"));
            double hostelLon = Double.parseDouble(buildingConfigService.getConfigString("hostel_longitude", buildingId, "0"));
            int radius = Integer.parseInt(buildingConfigService.getConfigString("hostel_radius", buildingId, "50"));
            serverDistance = distanceMeters(latitude, longitude, hostelLat, hostelLon);
            if (serverDistance > radius) {
                throw new RuntimeException("You are too far from the hostel to mark attendance");
            }
        }

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        // Kept as a separate Integer variable (not inlined into the ternary below): a
        // `cond ? Integer : int` ternary unboxes the Integer operand unconditionally at
        // compile time, so inlining this would NPE on the WIFI path whenever the client
        // omits distance (which it legitimately does, since distance only applies to
        // GEO_BIOMETRIC).
        Integer recordDistance = attendanceMethod == AttendanceMethod.WIFI
                ? distance
                : Integer.valueOf((int) Math.round(serverDistance));

        AttendanceRecord record = AttendanceRecord.builder()
                .session(session)
                .student(student)
                .date(today)
                .time(LocalTime.now(IST).format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .method(attendanceMethod)
                .status(AttendanceStatus.PRESENT)
                .latitude(latitude)
                .longitude(longitude)
                .distance(recordDistance)
                .build();

        recordRepository.save(record);
        return mapRecord(record);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTodayStatus(Long studentId) {
        return recordRepository.findByStudentIdAndDate(studentId, LocalDate.now(IST))
                .map(this::mapRecord)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHistory(Long studentId) {
        return recordRepository.findByStudentIdOrderByDateDesc(studentId)
                .stream().map(this::mapRecord).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats(Long studentId) {
        long total = recordRepository.countByStudentId(studentId);
        long present = recordRepository.countByStudentIdAndStatus(studentId, AttendanceStatus.PRESENT);
        long absent = total - present;
        int percentage = total > 0 ? (int) Math.round((double) present / total * 100) : 0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("present", present);
        stats.put("absent", absent);
        stats.put("percentage", percentage);
        return stats;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSessionRecords(Long sessionId, List<String> wardenHostels) {
        return recordRepository.findBySessionIdOrderByMarkedAtDesc(sessionId)
                .stream()
                .filter(r -> wardenHostels.contains(r.getStudent().getHostel()))
                .map(r -> {
                    Map<String, Object> map = mapRecord(r);
                    Student s = r.getStudent();
                    map.put("name", s.getName());
                    map.put("rollNo", s.getRollNo());
                    map.put("room", s.getRoomNumber());
                    return map;
                }).collect(Collectors.toList());
    }

    private Map<String, Object> mapSession(AttendanceSession s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", s.getId());
        map.put("buildingId", s.getBuilding() != null ? s.getBuilding().getId() : null);
        map.put("buildingName", s.getBuilding() != null ? s.getBuilding().getName() : null);
        map.put("status", s.getStatus().name());
        map.put("startedAt", s.getStartedAt() != null
                ? s.getStartedAt().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : null);
        map.put("startedDate", s.getStartedAt() != null
                ? s.getStartedAt().toLocalDate().toString() : null);
        map.put("stoppedAt", s.getStoppedAt() != null
                ? s.getStoppedAt().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : null);
        return map;
    }

    private Map<String, Object> mapRecord(AttendanceRecord r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", r.getId());
        map.put("date", r.getDate().toString());
        map.put("time", r.getTime());
        map.put("method", r.getMethod().name());
        map.put("status", r.getStatus().name());
        map.put("latitude", r.getLatitude());
        map.put("longitude", r.getLongitude());
        map.put("distance", r.getDistance());
        return map;
    }
}
