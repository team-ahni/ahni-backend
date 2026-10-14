package com.ahni.backend.repository;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.entity.Lecture;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface LectureRepository extends JpaRepository<Lecture, Long> {
    Optional<Lecture> findByEntityId(UUID entityId);
    List<Lecture> findAllByAcademicYearAndTermOrderByCourseCodeAscSectionAsc(int year, AcademicTerm term);
    boolean existsByCourseIdAndAcademicYearAndTermAndSection(Long courseId, int year, AcademicTerm term, String section);
}
