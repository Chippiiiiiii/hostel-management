package com.outpass.portal.service;

import com.outpass.portal.dto.request.StudentProfileUpdateRequest;
import com.outpass.portal.dto.response.StudentProfileResponse;
import com.outpass.portal.model.entity.Student;
import com.outpass.portal.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Optional;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;

    private StudentService service;

    @BeforeEach
    void setUp() {
        service = new StudentService(studentRepository);
    }

    private String realPngDataUri() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private Student baseStudent() {
        return Student.builder()
                .id(1L).name("S").email("s@x.com").rollNo("R1").department("D")
                .hostel("H1").roomNumber("101").contactNumber("9000000000")
                .parentNumber("9000000001").profilePicture("data:image/png;base64,existingPfp")
                .build();
    }

    @Test
    void getProfileIncludesIdCardPhotoWhenPresent() {
        Student student = baseStudent();
        student.setIdCardPhoto("data:image/png;base64,existingIdCard");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        StudentProfileResponse profile = service.getProfile(1L);

        assertThat(profile.getIdCardPhoto()).isEqualTo("data:image/png;base64,existingIdCard");
    }

    @Test
    void getProfileHandlesMissingIdCardPhotoGracefully() {
        Student student = baseStudent();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        StudentProfileResponse profile = service.getProfile(1L);

        assertThat(profile.getIdCardPhoto()).isNull();
    }

    @Test
    void updateIdCardPhotoAcceptsAValidImageAndPersistsIt() throws Exception {
        Student student = baseStudent();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(studentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        String validImage = realPngDataUri();

        StudentProfileResponse response = service.updateIdCardPhoto(1L, validImage);

        assertThat(response.getIdCardPhoto()).isEqualTo(validImage);
        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(captor.capture());
        assertThat(captor.getValue().getIdCardPhoto()).isEqualTo(validImage);
    }

    // profilePicture must never be touched by the id-card-photo path -- they are
    // completely independent fields on the Student record.
    @Test
    void updateIdCardPhotoDoesNotAffectProfilePicture() throws Exception {
        Student student = baseStudent();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(studentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateIdCardPhoto(1L, realPngDataUri());

        assertThat(student.getProfilePicture()).isEqualTo("data:image/png;base64,existingPfp");
    }

    @Test
    void updateIdCardPhotoRejectsMalformedNonImageContent() {
        String fakeImage = "data:image/png;base64," + Base64.getEncoder().encodeToString("not a real image".getBytes());

        assertThatThrownBy(() -> service.updateIdCardPhoto(1L, fakeImage))
                .isInstanceOf(RuntimeException.class);

        verify(studentRepository, never()).save(any());
    }

    @Test
    void updateIdCardPhotoRejectsBlankInput() {
        assertThatThrownBy(() -> service.updateIdCardPhoto(1L, ""))
                .isInstanceOf(RuntimeException.class);

        verify(studentRepository, never()).save(any());
    }

    // There is no path in this method (or the controller that calls it) for a caller to
    // supply a student ID other than their own authenticated one -- studentId always comes
    // from userPrincipal.getId() in StudentController. This documents that contract: the
    // service updates exactly the row identified by the ID it was given, nothing else.
    @Test
    void updateIdCardPhotoOnlyEverTargetsTheGivenStudentId() throws Exception {
        Student student = baseStudent();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(studentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateIdCardPhoto(1L, realPngDataUri());

        verify(studentRepository, never()).findById(org.mockito.ArgumentMatchers.longThat(id -> id != 1L));
    }

    @Test
    void updateProfileDoesNotTouchIdCardPhoto() {
        Student student = baseStudent();
        student.setIdCardPhoto("data:image/png;base64,existingIdCard");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(studentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StudentProfileUpdateRequest request = new StudentProfileUpdateRequest();
        request.setContactNumber("9111111111");
        request.setParentNumber("9222222222");

        StudentProfileResponse response = service.updateProfile(1L, request);

        assertThat(response.getIdCardPhoto()).isEqualTo("data:image/png;base64,existingIdCard");
    }
}
