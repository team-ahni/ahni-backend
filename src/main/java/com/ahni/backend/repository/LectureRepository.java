package com.ahni.backend.repository;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.entity.Lecture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface LectureRepository extends JpaRepository<Lecture, Long> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Transactional(readOnly = true)
    Optional<Lecture> findByEntityId(UUID entityId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lecture l where l.entityId = :id")
    Optional<Lecture> findForCatalogUpdate(@Param("id") UUID entityId);
    @EntityGraph(attributePaths = {"course", "schedules"})
    List<Lecture> findAllByAcademicYearAndTermOrderByCourseCodeAscSectionAsc(int year, AcademicTerm term);
    boolean existsByCourseIdAndAcademicYearAndTermAndSection(Long courseId, int year, AcademicTerm term, String section);
    boolean existsByCourseIdAndAcademicYearAndTermAndSectionAndEntityIdNot(
        Long courseId, int year, AcademicTerm term, String section, UUID entityId);
}
