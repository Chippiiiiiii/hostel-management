package com.outpass.portal.service;

import com.outpass.portal.dto.request.OutpassRequest;
import com.outpass.portal.dto.response.OutpassResponse;
import com.outpass.portal.model.entity.Outpass;
import com.outpass.portal.model.entity.Student;
import com.outpass.portal.model.enums.OutpassStatus;
import com.outpass.portal.repository.OutpassRepository;
import com.outpass.portal.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Student-submitted date/returnDate values represent IST wall-clock time (from a
 * browser datetime-local input with no timezone offset attached). The service must
 * compare them against a "now" computed in IST specifically, not the JVM/container's
 * default zone (which, absent an explicit TZ override, is typically UTC) — otherwise
 * every late-return and date-range comparison silently skews by the UTC-IST offset.
 */
@ExtendWith(MockitoExtension.class)
class OutpassServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Mock private OutpassRepository outpassRepository;
    @Mock private StudentRepository studentRepository;

    private OutpassService service;

    @BeforeEach
    void setUp() {
        service = new OutpassService(outpassRepository, studentRepository);
    }

    private Outpass outpassWithReturnDate(LocalDateTime returnDate, OutpassStatus status) {
        return Outpass.builder()
                .id(1L)
                .student(Student.builder().id(1L).build())
                .name("S").rollNo("R1").department("D").hostel("H1").roomNumber("101")
                .date(returnDate.minusHours(1))
                .returnDate(returnDate)
                .noOfDays(1)
                .placeOfVisit("Home")
                .contactNumber("9999999999")
                .parentNumber("9999999999")
                .status(status)
                .build();
    }

    @Test
    void markReturnFlagsLateWhenPastIstReturnDateEvenIfJvmDefaultZoneWouldSayOtherwise() {
        // A returnDate that is 3 hours in the past by the real IST clock. Under the old
        // bug (bare LocalDateTime.now(), effectively UTC in production), "now" would be
        // ~5.5 hours behind IST — i.e. still ~2.5 hours BEFORE this returnDate — so the
        // student would be wrongly recorded as on-time.
        LocalDateTime returnDate = LocalDateTime.now(IST).minusHours(3);
        Outpass outpass = outpassWithReturnDate(returnDate, OutpassStatus.DEPARTED);

        when(outpassRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(outpass));
        when(outpassRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OutpassResponse response = service.markReturn(1L, 99L, "H1");

        assertThat(response.getIsLateReturn()).isTrue();
        assertThat(response.getStatus()).isEqualTo(OutpassStatus.OVERDUE);
    }

    @Test
    void markReturnDoesNotFlagLateWhenReturnDateIsStillInTheIstFuture() {
        LocalDateTime returnDate = LocalDateTime.now(IST).plusHours(3);
        Outpass outpass = outpassWithReturnDate(returnDate, OutpassStatus.DEPARTED);

        when(outpassRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(outpass));
        when(outpassRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OutpassResponse response = service.markReturn(1L, 99L, "H1");

        assertThat(response.getIsLateReturn()).isFalse();
        assertThat(response.getStatus()).isEqualTo(OutpassStatus.COMPLETED);
    }

    @Test
    void getActiveOutpassesUsesIstNowToDetermineWhetherOutpassIsCurrentlyActive() {
        // Departed 3 hours ago (IST), due back in 2 hours (IST) -> currently active.
        Outpass active = Outpass.builder()
                .id(2L).student(Student.builder().id(2L).build())
                .name("S").rollNo("R2").department("D").hostel("H1").roomNumber("101")
                .date(LocalDateTime.now(IST).minusHours(3))
                .returnDate(LocalDateTime.now(IST).plusHours(2))
                .noOfDays(1).placeOfVisit("Home")
                .contactNumber("9999999999").parentNumber("9999999999")
                .status(OutpassStatus.APPROVED)
                .build();

        when(outpassRepository.findByStatusOrderByCreatedAtDesc(OutpassStatus.APPROVED))
                .thenReturn(List.of(active));

        List<OutpassResponse> result = service.getActiveOutpasses();

        assertThat(result).hasSize(1);
    }

    // Regression coverage for the OutpassService concurrency fix: every status-transition
    // method must lock the row (via findByIdForUpdate) instead of reading it unlocked
    // (via findById), otherwise two concurrent requests against the same outpass (e.g. a
    // warden approving while another approves/declines, or a guard double-scanning) could
    // both pass their status check before either write lands, corrupting the state machine.
    @Test
    void approveOutpassLocksTheOutpassRowInsteadOfPlainRead() {
        Outpass outpass = outpassWithReturnDate(LocalDateTime.now(IST).plusHours(3), OutpassStatus.PENDING);
        outpass.setHostel("H1");

        when(outpassRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(outpass));
        when(outpassRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OutpassResponse response = service.approveOutpass(1L, List.of("H1"), 99L, null);

        assertThat(response.getStatus()).isEqualTo(OutpassStatus.APPROVED);
        verify(outpassRepository, never()).findById(any());
    }

    @Test
    void markDepartureLocksTheOutpassRowInsteadOfPlainRead() {
        Outpass outpass = outpassWithReturnDate(LocalDateTime.now(IST).plusHours(3), OutpassStatus.APPROVED);
        outpass.setHostel("H1");

        when(outpassRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(outpass));
        when(outpassRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OutpassResponse response = service.markDeparture(1L, 99L, "H1");

        assertThat(response.getStatus()).isEqualTo(OutpassStatus.DEPARTED);
        verify(outpassRepository, never()).findById(any());
    }

    // ==================== createOutpass: single-active-outpass business rule ====================

    private OutpassRequest validRequest() {
        OutpassRequest request = new OutpassRequest();
        request.setReason("Family function");
        request.setPlaceOfVisit("Home");
        request.setDate(LocalDateTime.now(IST).plusHours(1));
        request.setReturnDate(LocalDateTime.now(IST).plusDays(1));
        request.setNoOfDays(1);
        request.setContactNumber("9000000000");
        request.setParentNumber("9000000001");
        return request;
    }

    @Test
    void createOutpassLocksTheStudentRowBeforeCheckingForAnActiveOutpass() {
        Student student = Student.builder().id(1L).name("S").rollNo("R1").department("D")
                .hostel("H1").roomNumber("101").build();
        when(studentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(student));
        when(outpassRepository.existsByStudentIdAndStatusIn(eq(1L), anyList())).thenReturn(false);
        when(outpassRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createOutpass(1L, validRequest());

        verify(studentRepository).findByIdForUpdate(1L);
        verify(studentRepository, never()).findById(any());
    }

    @ParameterizedTest
    @EnumSource(value = OutpassStatus.class, names = {"PENDING", "APPROVED", "DEPARTED"})
    void createOutpassRejectedWhileAnActiveOutpassExists(OutpassStatus activeStatus) {
        Student student = Student.builder().id(1L).name("S").rollNo("R1").department("D")
                .hostel("H1").roomNumber("101").build();
        when(studentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(student));
        when(outpassRepository.existsByStudentIdAndStatusIn(eq(1L),
                eq(List.of(OutpassStatus.PENDING, OutpassStatus.APPROVED, OutpassStatus.DEPARTED))))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createOutpass(1L, validRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already have an active outpass");

        verify(outpassRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = OutpassStatus.class, names = {"DECLINED", "COMPLETED", "OVERDUE"})
    void createOutpassAllowedWhenOnlyResolvedOutpassesExist(OutpassStatus resolvedStatus) {
        Student student = Student.builder().id(1L).name("S").rollNo("R1").department("D")
                .hostel("H1").roomNumber("101").build();
        when(studentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(student));
        // No PENDING/APPROVED/DEPARTED outpass exists -- only a resolved one (of whichever
        // status this run is parameterized with), which must never block a new request.
        when(outpassRepository.existsByStudentIdAndStatusIn(eq(1L), anyList())).thenReturn(false);
        when(outpassRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OutpassResponse response = service.createOutpass(1L, validRequest());

        assertThat(response.getStatus()).isEqualTo(OutpassStatus.PENDING);
        verify(outpassRepository).save(any());
    }

    // ==================== studentIdCardPhoto: Warden/Security-only inclusion ====================

    private Student studentWithIdCard() {
        return Student.builder().id(1L).idCardPhoto("data:image/png;base64,idcard").build();
    }

    private Outpass outpassForStudent(Student student, OutpassStatus status) {
        return Outpass.builder()
                .id(1L)
                .student(student)
                .name("S").rollNo("R1").department("D").hostel("H1").roomNumber("101")
                .date(LocalDateTime.now(IST).minusHours(1))
                .returnDate(LocalDateTime.now(IST).plusHours(1))
                .noOfDays(1)
                .placeOfVisit("Home")
                .contactNumber("9999999999")
                .parentNumber("9999999999")
                .status(status)
                .build();
    }

    @Test
    void wardenPendingOutpassResponseIncludesStudentIdCardPhoto() {
        Outpass outpass = outpassForStudent(studentWithIdCard(), OutpassStatus.PENDING);
        when(outpassRepository.findByHostelInAndStatusOrderByCreatedAtDesc(List.of("H1"), OutpassStatus.PENDING))
                .thenReturn(List.of(outpass));

        List<OutpassResponse> result = service.getPendingOutpassesByHostels(List.of("H1"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStudentIdCardPhoto()).isEqualTo("data:image/png;base64,idcard");
    }

    @Test
    void wardenHistoryResponseIncludesStudentIdCardPhoto() {
        Outpass outpass = outpassForStudent(studentWithIdCard(), OutpassStatus.COMPLETED);
        when(outpassRepository.findByHostelInOrderByCreatedAtDesc(List.of("H1")))
                .thenReturn(List.of(outpass));

        List<OutpassResponse> result = service.getAllOutpassesByHostels(List.of("H1"));

        assertThat(result.get(0).getStudentIdCardPhoto()).isEqualTo("data:image/png;base64,idcard");
    }

    @Test
    void approveOutpassResponseIncludesStudentIdCardPhoto() {
        Outpass outpass = outpassForStudent(studentWithIdCard(), OutpassStatus.PENDING);
        outpass.setHostel("H1");
        when(outpassRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(outpass));
        when(outpassRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OutpassResponse response = service.approveOutpass(1L, List.of("H1"), 99L, null);

        assertThat(response.getStudentIdCardPhoto()).isEqualTo("data:image/png;base64,idcard");
    }

    @Test
    void declineOutpassResponseIncludesStudentIdCardPhoto() {
        Outpass outpass = outpassForStudent(studentWithIdCard(), OutpassStatus.PENDING);
        outpass.setHostel("H1");
        when(outpassRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(outpass));
        when(outpassRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        com.outpass.portal.dto.request.DeclineOutpassRequest request = new com.outpass.portal.dto.request.DeclineOutpassRequest();
        request.setDeclineReason("No reason");
        OutpassResponse response = service.declineOutpass(1L, List.of("H1"), 99L, request);

        assertThat(response.getStudentIdCardPhoto()).isEqualTo("data:image/png;base64,idcard");
    }

    @Test
    void securityActiveOutpassResponseIncludesStudentIdCardPhoto() {
        Outpass outpass = outpassForStudent(studentWithIdCard(), OutpassStatus.APPROVED);
        outpass.setHostel("H1");
        when(outpassRepository.findByHostelAndStatusOrderByCreatedAtDesc("H1", OutpassStatus.APPROVED))
                .thenReturn(List.of(outpass));

        List<OutpassResponse> result = service.getActiveOutpassesByHostel("H1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStudentIdCardPhoto()).isEqualTo("data:image/png;base64,idcard");
    }

    @Test
    void securityTodayOutpassResponseIncludesStudentIdCardPhoto() {
        Outpass outpass = outpassForStudent(studentWithIdCard(), OutpassStatus.APPROVED);
        outpass.setHostel("H1");
        outpass.setDate(LocalDateTime.now(IST));
        when(outpassRepository.findByHostelAndDateBetween(eq("H1"), any(), any()))
                .thenReturn(List.of(outpass));

        List<OutpassResponse> result = service.getTodayOutpassesByHostel("H1");

        assertThat(result.get(0).getStudentIdCardPhoto()).isEqualTo("data:image/png;base64,idcard");
    }

    @Test
    void securityOutpassByIdResponseIncludesStudentIdCardPhoto() {
        Outpass outpass = outpassForStudent(studentWithIdCard(), OutpassStatus.APPROVED);
        outpass.setHostel("H1");
        when(outpassRepository.findById(1L)).thenReturn(Optional.of(outpass));

        OutpassResponse result = service.getOutpassByIdAndHostel(1L, "H1");

        assertThat(result.getStudentIdCardPhoto()).isEqualTo("data:image/png;base64,idcard");
    }

    @Test
    void markDepartureResponseIncludesStudentIdCardPhoto() {
        Outpass outpass = outpassForStudent(studentWithIdCard(), OutpassStatus.APPROVED);
        outpass.setHostel("H1");
        when(outpassRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(outpass));
        when(outpassRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OutpassResponse response = service.markDeparture(1L, 99L, "H1");

        assertThat(response.getStudentIdCardPhoto()).isEqualTo("data:image/png;base64,idcard");
    }

    @Test
    void markReturnResponseIncludesStudentIdCardPhoto() {
        Outpass outpass = outpassForStudent(studentWithIdCard(), OutpassStatus.DEPARTED);
        outpass.setHostel("H1");
        outpass.setReturnDate(LocalDateTime.now(IST).plusHours(1));
        when(outpassRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(outpass));
        when(outpassRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OutpassResponse response = service.markReturn(1L, 99L, "H1");

        assertThat(response.getStudentIdCardPhoto()).isEqualTo("data:image/png;base64,idcard");
    }

    @Test
    void securityDepartedOutpassResponseIncludesStudentIdCardPhoto() {
        Outpass outpass = outpassForStudent(studentWithIdCard(), OutpassStatus.DEPARTED);
        outpass.setHostel("H1");
        when(outpassRepository.findByHostelAndStatusOrderByCreatedAtDesc("H1", OutpassStatus.DEPARTED))
                .thenReturn(List.of(outpass));

        List<OutpassResponse> result = service.getDepartedOutpassesByHostel("H1");

        assertThat(result.get(0).getStudentIdCardPhoto()).isEqualTo("data:image/png;base64,idcard");
    }

    // ==================== studentIdCardPhoto: never leaked to Student-facing responses ====================

    @Test
    void createOutpassResponseDoesNotIncludeStudentIdCardPhoto() {
        Student student = Student.builder().id(1L).name("S").rollNo("R1").department("D")
                .hostel("H1").roomNumber("101").idCardPhoto("data:image/png;base64,idcard").build();
        when(studentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(student));
        when(outpassRepository.existsByStudentIdAndStatusIn(eq(1L), anyList())).thenReturn(false);
        when(outpassRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OutpassResponse response = service.createOutpass(1L, validRequest());

        assertThat(response.getStudentIdCardPhoto()).isNull();
    }

    @Test
    void studentOwnOutpassLookupDoesNotIncludeStudentIdCardPhoto() {
        Outpass outpass = outpassForStudent(studentWithIdCard(), OutpassStatus.PENDING);
        when(outpassRepository.findById(1L)).thenReturn(Optional.of(outpass));

        OutpassResponse response = service.getOutpassById(1L, 1L);

        assertThat(response.getStudentIdCardPhoto()).isNull();
    }

    @Test
    void studentOutpassHistoryDoesNotIncludeStudentIdCardPhoto() {
        Outpass outpass = outpassForStudent(studentWithIdCard(), OutpassStatus.COMPLETED);
        when(outpassRepository.findByStudentIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(outpass));

        List<OutpassResponse> result = service.getStudentOutpasses(1L);

        assertThat(result.get(0).getStudentIdCardPhoto()).isNull();
    }

    // A student with no ID card uploaded (idCardPhoto == null on the Student record) must
    // not break the Warden/Security response -- it simply comes through as null, letting
    // the frontend render "ID card not uploaded" rather than erroring.
    @Test
    void missingIdCardPhotoIsHandledGracefullyInWardenResponse() {
        Student studentWithoutIdCard = Student.builder().id(1L).build();
        Outpass outpass = outpassForStudent(studentWithoutIdCard, OutpassStatus.PENDING);
        when(outpassRepository.findByHostelInAndStatusOrderByCreatedAtDesc(List.of("H1"), OutpassStatus.PENDING))
                .thenReturn(List.of(outpass));

        List<OutpassResponse> result = service.getPendingOutpassesByHostels(List.of("H1"));

        assertThat(result.get(0).getStudentIdCardPhoto()).isNull();
    }
}
