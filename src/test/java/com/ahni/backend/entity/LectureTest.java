package com.ahni.backend.entity;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.domain.CourseCategory;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LectureTest {
    private final Course course = new Course(null, "TEST101", "자료구조", new BigDecimal("3.0"), CourseCategory.MAJOR);

    @Test void preservesJointAndExternalInstructorTextWithoutIdentity() {
        for (String name : new String[]{"남춘성,이우형", "영남대", "담당교수 미정"}) {
            var lecture = new Lecture(course, " " + name + " ", 2026, AcademicTerm.SECOND, "001");
            assertThat(lecture.getProfessorName()).isEqualTo(name);
        }
    }

    @Test void rejectsMissingOrOversizedInstructorNames() {
        for (String name : new String[]{null, "", "  ", "가".repeat(501)}) {
            assertThatThrownBy(() -> new Lecture(course, name, 2026, AcademicTerm.SECOND, "001"))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
