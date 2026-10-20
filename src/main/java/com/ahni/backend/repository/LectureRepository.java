package com.ahni.backend.repository;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.entity.Lecture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.*;

public interface LectureRepository extends JpaRepository<Lecture, Long> {
    Optional<Lecture> findByEntityId(UUID entityId);
    @EntityGraph(attributePaths = {"course", "schedules"})
    List<Lecture> findAllByAcademicYearAndTermOrderByCourseCodeAscSectionAsc(int year, AcademicTerm term);
    boolean existsByCourseIdAndAcademicYearAndTermAndSection(Long courseId, int year, AcademicTerm term, String section);
}
