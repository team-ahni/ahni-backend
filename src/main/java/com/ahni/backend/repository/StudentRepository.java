package com.ahni.backend.repository;

import com.ahni.backend.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Student s where s.authUserId = :authUserId and s.deletedAt is null")
    Optional<Student> findForTimetableUpdate(@org.springframework.data.repository.query.Param("authUserId") UUID authUserId);

    Optional<Student> findByAuthUserId(UUID authUserId);

    boolean existsByEmailIgnoreCase(String email);
}
