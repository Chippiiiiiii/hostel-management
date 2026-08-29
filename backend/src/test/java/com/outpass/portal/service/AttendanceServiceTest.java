package com.outpass.portal.service;

import com.outpass.portal.model.entity.AttendanceRecord;
import com.outpass.portal.model.entity.AttendanceSession;
import com.outpass.portal.model.entity.RoomConfig;
import com.outpass.portal.model.entity.Student;
import com.outpass.portal.model.enums.AttendanceMethod;
import com.outpass.portal.model.enums.AttendanceStatus;
import com.outpass.portal.model.enums.SessionStatus;
import com.outpass.portal.repository.AttendanceRecordRepository;
import com.outpass.portal.repository.AttendanceSessionRepository;
import com.outpass.portal.repository.RoomConfigRepository;
import com.outpass.portal.repository.StudentRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * markAttendance must independently re-derive whether a WIFI/GEO_BIOMETRIC claim actually
 * holds (real client IP against configured subnets; real distance against configured
 * radius) rather than trusting the client-submitted method/coordinates/distance as-is —
 * otherwise attendance could be marked from anywhere via a direct API call.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock private AttendanceSessionRepository sessionRepository;
    @Mock private AttendanceRecordRepository recordRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private RoomConfigRepository configRepository;

    private AttendanceService service;

    @BeforeEach
    void setUp() {
        service = new AttendanceService(sessionRepository, recordRepository, studentRepository, configRepository);

        AttendanceSession session = AttendanceSession.builder().id(1L).status(SessionStatus.ACTIVE).build();
        lenient().when(sessionRepository.findByStatus(SessionStatus.ACTIVE)).thenReturn(Optional.of(session));
        lenient().when(recordRepository.findByStudentIdAndDate(any(), any())).thenReturn(Optional.empty());
        lenient().when(studentRepository.findById(1L))
                .thenReturn(Optional.of(Student.builder().id(1L).name("S").rollNo("R1").build()));
        lenient().when(recordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        lenient().when(configRepository.findByConfigKey("wifi_allowed_subnets"))
                .thenReturn(Optional.of(RoomConfig.builder().configKey("wifi_allowed_subnets").configValue("10.0.0.0/24").build()));
        lenient().when(configRepository.findByConfigKey("hostel_latitude"))
                .thenReturn(Optional.of(RoomConfig.builder().configKey("hostel_latitude").configValue("13.0000").build()));
        lenient().when(configRepository.findByConfigKey("hostel_longitude"))
                .thenReturn(Optional.of(RoomConfig.builder().configKey("hostel_longitude").configValue("80.0000").build()));
        lenient().when(configRepository.findByConfigKey("hostel_radius"))
                .thenReturn(Optional.of(RoomConfig.builder().configKey("hostel_radius").configValue("50").build()));
    }

    @Test
    void wifiMethodRejectedWhenClientIpNotInAllowedSubnet() {
        assertThatThrownBy(() -> service.markAttendance(1L, "WIFI", null, null, null, "8.8.8.8"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Not connected to hostel WiFi");
    }

    @Test
    void wifiMethodAcceptedWhenClientIpInAllowedSubnet() {
        Map<String, Object> result = service.markAttendance(1L, "WIFI", null, null, null, "10.0.0.42");

        assertThat(result).isNotNull();
    }

    @Test
    void clientCannotForgeWifiSuccessByOnlyClaimingTheMethod() {
        // Regression check: a direct API call claiming WIFI from an arbitrary IP, with no
        // location data at all, must not be trusted just because the client says so.
        assertThatThrownBy(() -> service.markAttendance(1L, "WIFI", null, null, 0, "203.0.113.5"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void geoBiometricRejectedWhenOutsideConfiguredRadius() {
        assertThatThrownBy(() -> service.markAttendance(1L, "GEO_BIOMETRIC", 0.0, 0.0, 0, "8.8.8.8"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("too far");
    }

    @Test
    void geoBiometricRejectedWhenLocationMissing() {
        assertThatThrownBy(() -> service.markAttendance(1L, "GEO_BIOMETRIC", null, null, null, "8.8.8.8"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Location is required");
    }

    @Test
    void geoBiometricIgnoresClientReportedDistanceAndUsesServerComputedValue() {
        // Client claims it is right on top of the hostel (distance=0) while actually
        // submitting coordinates far away — the server must compute its own distance
        // rather than trust the client's number.
        assertThatThrownBy(() -> service.markAttendance(1L, "GEO_BIOMETRIC", 0.0, 0.0, 0, "8.8.8.8"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("too far");
    }

    @Test
    void geoBiometricAcceptedWhenWithinRadius() {
        Map<String, Object> result = service.markAttendance(1L, "GEO_BIOMETRIC", 13.0000, 80.0000, 999, "8.8.8.8");

        assertThat(result).isNotNull();
    }

    @Test
    void rejectsUnknownMethod() {
        assertThatThrownBy(() -> service.markAttendance(1L, "MAGIC", null, null, null, "8.8.8.8"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid attendance method");
    }

    @Test
    void getSessionRecordsOnlyReturnsStudentsFromTheRequestingWardensHostel() {
        // A session is a single global concept shared across all hostels, so the warden's
        // hostel must be enforced by filtering records after the fact — otherwise a warden
        // could read another hostel's students' names/roll numbers via this endpoint.
        Student ownHostelStudent = Student.builder().id(1L).name("A").rollNo("R1").hostel("Hostel A").build();
        Student otherHostelStudent = Student.builder().id(2L).name("B").rollNo("R2").hostel("Hostel B").build();

        AttendanceRecord ownRecord = AttendanceRecord.builder()
                .id(10L).student(ownHostelStudent).date(java.time.LocalDate.now())
                .time("10:00:00").method(AttendanceMethod.WIFI).status(AttendanceStatus.PRESENT).build();
        AttendanceRecord otherRecord = AttendanceRecord.builder()
                .id(11L).student(otherHostelStudent).date(java.time.LocalDate.now())
                .time("10:05:00").method(AttendanceMethod.WIFI).status(AttendanceStatus.PRESENT).build();

        when(recordRepository.findBySessionIdOrderByMarkedAtDesc(5L))
                .thenReturn(List.of(ownRecord, otherRecord));

        List<Map<String, Object>> result = service.getSessionRecords(5L, "Hostel A");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("rollNo")).isEqualTo("R1");
    }
}
