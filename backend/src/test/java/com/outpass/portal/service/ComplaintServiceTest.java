package com.outpass.portal.service;

import com.outpass.portal.model.entity.Student;
import com.outpass.portal.repository.ComplaintRepository;
import com.outpass.portal.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Covers the photo-upload size cap on Complaint.photo -- the only base64-photo field in the
 * app with no DTO to attach a @Size constraint to (createComplaint takes a raw Map value),
 * so the limit has to be enforced explicitly in the service.
 */
@ExtendWith(MockitoExtension.class)
class ComplaintServiceTest {

    @Mock private ComplaintRepository complaintRepository;
    @Mock private StudentRepository studentRepository;

    private ComplaintService complaintService;

    private Student student;

    @BeforeEach
    void setUp() {
        complaintService = new ComplaintService(complaintRepository, studentRepository);
        student = Student.builder().id(1L).name("S").rollNo("R1").hostel("Hostel A").roomNumber("101").build();
    }

    @Test
    void photoWithinLimit_isAccepted() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        complaintService.createComplaint(1L, "PLUMBING", "Broken fan", "a".repeat(2_800_000));

        verify(complaintRepository).save(any());
    }

    @Test
    void photoOverLimit_isRejectedBeforeTouchingRepository() {
        assertThatThrownBy(() -> complaintService.createComplaint(
                1L, "PLUMBING", "Broken fan", "a".repeat(2_800_001)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("too large");

        verifyNoInteractions(studentRepository, complaintRepository);
    }

    @Test
    void noPhoto_isAccepted() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        complaintService.createComplaint(1L, "PLUMBING", "Broken fan", null);

        verify(complaintRepository).save(any());
    }
}
