package com.ahni.backend.service;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.domain.CourseCategory;
import com.ahni.backend.entity.Course;
import com.ahni.backend.entity.Lecture;
import com.ahni.backend.exception.InvalidGradeException;
import com.ahni.backend.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OfferedCourseTest {
    @Test
    void lectureLookupUsesYearAndTermAndRegistrationRejectsOtherTerms() {
        var courses = mock(CourseRepository.class);
        var lectures = mock(LectureRepository.class);
        var service = new CurriculumService(mock(AdminAccessService.class), mock(CurriculumRepository.class),
            mock(CurriculumCourseRepository.class), courses, mock(DepartmentRepository.class), lectures);
        var course = new Course(null, "CSE101", "자료구조", new BigDecimal("3.0"), CourseCategory.ELECTIVE);
        when(lectures.findOfferedCourses(2026, AcademicTerm.SECOND)).thenReturn(List.of(new Lecture(course, "김교수", 2026, AcademicTerm.SECOND, "001"),
            new Lecture(course, "이교수", 2026, AcademicTerm.SECOND, "002"),
            new Lecture(course, "김교수", 2026, AcademicTerm.SECOND, "003")));
        var result = service.getOfferedCourses(2026, AcademicTerm.SECOND);
        assertThat(result).extracting("code").containsExactly("CSE101");
        assertThat(result.getFirst().professorNames()).containsExactly("김교수", "이교수");
        when(courses.findByEntityId(course.getEntityId())).thenReturn(Optional.of(course));
        when(lectures.existsByCourseEntityIdAndAcademicYearAndTerm(course.getEntityId(), 2026, AcademicTerm.SECOND)).thenReturn(true);
        assertThat(service.requireOfferedCourse(2026, AcademicTerm.SECOND, course.getEntityId())).isSameAs(course);
        assertThatThrownBy(() -> service.requireOfferedCourse(2026, AcademicTerm.FIRST, course.getEntityId()))
            .isInstanceOf(InvalidGradeException.class);
    }
}
