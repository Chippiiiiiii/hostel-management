package com.outpass.portal.repository;

import com.outpass.portal.model.entity.Student;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByEmail(String email);
    Optional<Student> findByEmailIgnoreCase(String email);
    Optional<Student> findByRollNo(String rollNo);
    List<Student> findByHostel(String hostel);
    long countByHostel(String hostel);
    List<Student> findByHostelIn(List<String> hostels);
    long countByHostelIn(List<String> hostels);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByRollNo(String rollNo);

    // Students eligible for bulk auto-allocation: matching gender, no RoomAllocation yet.
    @Query("select s from Student s where s.gender = :gender " +
           "and not exists (select 1 from RoomAllocation a where a.studentEmail = s.email)")
    List<Student> findUnassignedByGender(@Param("gender") String gender);

    // Locks the student row for the duration of the enclosing transaction. Used before
    // deciding whether to create/update that student's RoomAllocation, so concurrent
    // allocation attempts for the same student (self-service vs. warden/admin, or two
    // concurrent self-service calls) serialize instead of racing.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.email = :email")
    Optional<Student> findByEmailForUpdate(@Param("email") String email);

    // Same locking contract as findByEmailForUpdate above, keyed by id. Used before deciding
    // whether to create a new Outpass for this student, so two concurrent outpass-creation
    // requests from the same student serialize instead of both passing the "no active
    // outpass yet" check before either has committed.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.id = :id")
    Optional<Student> findByIdForUpdate(@Param("id") Long id);
}

