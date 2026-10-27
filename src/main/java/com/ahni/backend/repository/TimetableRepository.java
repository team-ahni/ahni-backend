package com.ahni.backend.repository;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface TimetableRepository extends JpaRepository<Timetable, Long> {
    @Modifying
    @Query("delete from Timetable t where t.lecture = :lecture")
    int deleteAllForLecture(@Param("lecture") Lecture lecture);
    Optional<Timetable> findByEntityIdAndStudent(UUID entityId, Student student);
    List<Timetable> findAllByStudentAndLectureAcademicYearAndLectureTermOrderByCreatedAtAsc(
        Student student, int year, AcademicTerm term);
}
