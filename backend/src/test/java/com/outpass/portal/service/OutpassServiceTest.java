package com.outpass.portal.service;

import com.outpass.portal.dto.response.OutpassResponse;
import com.outpass.portal.model.entity.Outpass;
import com.outpass.portal.model.entity.Student;
import com.outpass.portal.model.enums.OutpassStatus;
import com.outpass.portal.repository.OutpassRepository;
import com.outpass.portal.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

        OutpassResponse response = service.approveOutpass(1L, "H1", 99L, null);

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
}
