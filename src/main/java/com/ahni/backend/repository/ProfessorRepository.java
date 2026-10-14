package com.ahni.backend.repository;

import com.ahni.backend.entity.Professor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ProfessorRepository extends JpaRepository<Professor, Long> {
    Optional<Professor> findByEntityIdAndDeletedAtIsNull(UUID entityId);
    List<Professor> findAllByDeletedAtIsNullOrderByNameAsc();
}
