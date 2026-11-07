package com.ahni.backend.repository;

import com.ahni.backend.domain.StudentRelinkStatus;
import com.ahni.backend.entity.Student;
import com.ahni.backend.entity.StudentRelinkRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRelinkRequestRepository extends JpaRepository<StudentRelinkRequest, Long> {
    Optional<StudentRelinkRequest> findByNewAuthUserIdAndStatus(UUID newAuthUserId, StudentRelinkStatus status);

    @EntityGraph(attributePaths = {"student", "resolvedByAdmin"})
    List<StudentRelinkRequest> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StudentRelinkRequest> findByEntityId(UUID entityId);

    List<StudentRelinkRequest> findAllByStudentAndStatus(Student student, StudentRelinkStatus status);
}
