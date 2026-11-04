package com.ahni.backend.repository;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.entity.Course;
import com.ahni.backend.entity.Student;
import com.ahni.backend.entity.StudentGrade;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentGradeRepository extends JpaRepository<StudentGrade, Long> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from StudentGrade grade where grade.student = :student")
    void deleteAllForStudent(Student student);

    boolean existsByStudentAndCourseAndAcademicYearAndTerm(
        Student student,
        Course course,
        int academicYear,
        AcademicTerm term
    );

    boolean existsByStudentAndCourseAndAcademicYearAndTermAndIdNot(
        Student student,
        Course course,
        int academicYear,
        AcademicTerm term,
        Long id
    );

    Optional<StudentGrade> findByEntityIdAndStudent(UUID entityId, Student student);

    boolean existsByReplacedGrade(StudentGrade replacedGrade);

    Optional<StudentGrade> findByReplacedGrade(StudentGrade replacedGrade);

    boolean existsByReplacedGradeAndEntityIdNot(
        StudentGrade replacedGrade,
        UUID entityId
    );

    @EntityGraph(attributePaths = {
        "course",
        "course.department",
        "replacedGrade"
    })
    List<StudentGrade> findAllByStudent(Student student);
}
