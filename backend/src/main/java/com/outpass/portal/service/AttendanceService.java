package com.outpass.portal.service;

import com.outpass.portal.model.entity.AttendanceRecord;
import com.outpass.portal.model.entity.AttendanceSession;
import com.outpass.portal.model.entity.Student;
import com.outpass.portal.model.enums.AttendanceMethod;
import com.outpass.portal.model.enums.AttendanceStatus;
import com.outpass.portal.model.enums.SessionStatus;
import com.outpass.portal.repository.AttendanceRecordRepository;
import com.outpass.portal.repository.AttendanceSessionRepository;
import com.outpass.portal.repository.RoomConfigRepository;
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

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRecordRepository recordRepository;
    private final StudentRepository studentRepository;
    private final RoomConfigRepository configRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> verifyWifi(String clientIp) {
        String subnets = configRepository.findByConfigKey("wifi_allowed_subnets")
                .map(c -> c.getConfigValue())
                .orElse("");

        boolean connected = SubnetUtils.isInAnySubnet(clientIp, subnets);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connected", connected);
        result.put("ip", clientIp);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAttendanceConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("wifiAllowedSubnets", getConfigString("wifi_allowed_subnets", ""));
        config.put("hostelLatitude", getConfigString("hostel_latitude", "0"));
        config.put("hostelLongitude", getConfigString("hostel_longitude", "0"));
        config.put("hostelRadius", getConfigString("hostel_radius", "50"));
        return config;
    }

    @Transactional
    public void updateAttendanceConfig(String wifiSubnets, String latitude, String longitude, String radius) {
        saveConfigValue("wifi_allowed_subnets", wifiSubnets);
        saveConfigValue("hostel_latitude", latitude);
        saveConfigValue("hostel_longitude", longitude);
        saveConfigValue("hostel_radius", radius);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getHostelLocation() {
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("latitude", Double.parseDouble(getConfigString("hostel_latitude", "0")));
        location.put("longitude", Double.parseDouble(getConfigString("hostel_longitude", "0")));
        location.put("radius", Integer.parseInt(getConfigString("hostel_radius", "50")));
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

    private String getConfigString(String key, String defaultValue) {
        return configRepository.findByConfigKey(key)
                .map(c -> c.getConfigValue())
                .orElse(defaultValue);
    }

    private void saveConfigValue(String key, String value) {
        String safeValue = (value == null || value.isEmpty()) ? "0" : value;
        com.outpass.portal.model.entity.RoomConfig config = configRepository.findByConfigKey(key)
                .orElse(com.outpass.portal.model.entity.RoomConfig.builder().configKey(key).build());
        config.setConfigValue(safeValue);
        configRepository.save(config);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getActiveSession() {
        return sessionRepository.findByStatus(SessionStatus.ACTIVE)
                .map(this::mapSession)
                .orElse(null);
    }

    @Transactional
    public Map<String, Object> startSession(Long wardenId) {
        // Close any existing active session
        sessionRepository.findByStatus(SessionStatus.ACTIVE).ifPresent(session -> {
            session.setStatus(SessionStatus.CLOSED);
            session.setStoppedAt(java.time.LocalDateTime.now(IST));
            sessionRepository.save(session);
        });

        AttendanceSession session = AttendanceSession.builder()
                .startedBy(wardenId)
                .status(SessionStatus.ACTIVE)
                .build();
        sessionRepository.save(session);
        return mapSession(session);
    }

    @Transactional
    public void stopSession() {
        AttendanceSession session = sessionRepository.findByStatus(SessionStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("No active session"));
        session.setStatus(SessionStatus.CLOSED);
        session.setStoppedAt(java.time.LocalDateTime.now(IST));
        sessionRepository.save(session);
    }

    @Transactional
    public Map<String, Object> markAttendance(Long studentId, String method,
                                               Double latitude, Double longitude, Integer distance,
                                               String clientIp) {
        AttendanceSession session = sessionRepository.findByStatus(SessionStatus.ACTIVE)
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
        // via curl) to claim WIFI/GEO_BIOMETRIC success from anywhere.
        double serverDistance = 0;
        if (attendanceMethod == AttendanceMethod.WIFI) {
            String subnets = getConfigString("wifi_allowed_subnets", "");
            if (!SubnetUtils.isInAnySubnet(clientIp, subnets)) {
                throw new RuntimeException("Not connected to hostel WiFi");
            }
        } else {
            if (latitude == null || longitude == null) {
                throw new RuntimeException("Location is required for this verification method");
            }
            double hostelLat = Double.parseDouble(getConfigString("hostel_latitude", "0"));
            double hostelLon = Double.parseDouble(getConfigString("hostel_longitude", "0"));
            int radius = Integer.parseInt(getConfigString("hostel_radius", "50"));
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
    public List<Map<String, Object>> getSessionRecords(Long sessionId, String wardenHostel) {
        return recordRepository.findBySessionIdOrderByMarkedAtDesc(sessionId)
                .stream()
                .filter(r -> wardenHostel.equals(r.getStudent().getHostel()))
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
