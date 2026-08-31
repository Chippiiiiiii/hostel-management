package com.outpass.portal.repository;

import com.outpass.portal.model.entity.Complaint;
import com.outpass.portal.model.enums.ComplaintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    List<Complaint> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<Complaint> findByHostelOrderByCreatedAtDesc(String hostel);

    List<Complaint> findByHostelAndStatusOrderByCreatedAtDesc(String hostel, ComplaintStatus status);

    List<Complaint> findByHostelInOrderByCreatedAtDesc(List<String> hostels);

    List<Complaint> findByHostelInAndStatusOrderByCreatedAtDesc(List<String> hostels, ComplaintStatus status);

    long countByStatus(ComplaintStatus status);

    long countByStudentId(Long studentId);

    long countByHostel(String hostel);

    long countByHostelAndStatus(String hostel, ComplaintStatus status);

    long countByHostelIn(List<String> hostels);

    long countByHostelInAndStatus(List<String> hostels, ComplaintStatus status);
}
