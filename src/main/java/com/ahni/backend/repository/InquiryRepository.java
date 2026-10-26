package com.ahni.backend.repository;

import com.ahni.backend.entity.Student;
import com.ahni.backend.entity.Inquiry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {
    @EntityGraph(attributePaths = {"answeredByAdmin"})
    List<Inquiry> findAllByStudentAndDeletedAtIsNullOrderByCreatedAtDesc(Student student);

    @EntityGraph(attributePaths = {"answeredByAdmin"})
    Optional<Inquiry> findByEntityIdAndStudentAndDeletedAtIsNull(UUID entityId, Student student);

    @EntityGraph(attributePaths = {"student", "answeredByAdmin", "assignedAdmin"})
    List<Inquiry> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"student", "answeredByAdmin", "assignedAdmin"})
    Optional<Inquiry> findByEntityId(UUID entityId);
}
