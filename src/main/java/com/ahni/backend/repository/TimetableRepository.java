package com.ahni.backend.repository;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface TimetableRepository extends JpaRepository<Timetable, Long> {
    Optional<Timetable> findByEntityIdAndStudent(UUID entityId, Student student);
    List<Timetable> findAllByStudentAndLectureAcademicYearAndLectureTermOrderByCreatedAtAsc(
        Student student, int year, AcademicTerm term);
}
