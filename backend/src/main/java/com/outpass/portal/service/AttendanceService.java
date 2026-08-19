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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRecordRepository recordRepository;
    private final StudentRepository studentRepository;
    private final RoomConfigRepository configRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> verifyWifi(String clientIp) {
        String subnets = configRepository.findByConfigKey("wifi_allowed_subnets")
                .map(c -> c.getConfigValue())
                .orElse("");

        boolean isLocal = "127.0.0.1".equals(clientIp) || "0:0:0:0:0:0:0:1".equals(clientIp);
        boolean connected = isLocal || SubnetUtils.isInAnySubnet(clientIp, subnets);

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
            session.setStoppedAt(java.time.LocalDateTime.now());
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
        session.setStoppedAt(java.time.LocalDateTime.now());
        sessionRepository.save(session);
    }

    @Transactional
    public Map<String, Object> markAttendance(Long studentId, String method,
                                               Double latitude, Double longitude, Integer distance) {
        AttendanceSession session = sessionRepository.findByStatus(SessionStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("No active attendance session"));

        LocalDate today = LocalDate.now();
        recordRepository.findByStudentIdAndDate(studentId, today).ifPresent(r -> {
            throw new RuntimeException("Attendance already marked for today");
        });

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        AttendanceRecord record = AttendanceRecord.builder()
                .session(session)
                .student(student)
                .date(today)
                .time(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .method(AttendanceMethod.valueOf(method))
                .status(AttendanceStatus.PRESENT)
                .latitude(latitude)
                .longitude(longitude)
                .distance(distance)
                .build();

        recordRepository.save(record);
        return mapRecord(record);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTodayStatus(Long studentId) {
        return recordRepository.findByStudentIdAndDate(studentId, LocalDate.now())
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
    public List<Map<String, Object>> getSessionRecords(Long sessionId) {
        return recordRepository.findBySessionIdOrderByMarkedAtDesc(sessionId)
                .stream().map(r -> {
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
