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

    @Test void invalidRevisionPreservesExistingDetailsAndSchedules() {
        var lecture = new Lecture(course, "기존 교수", 2026, AcademicTerm.SECOND, "001");
        lecture.addSchedule(1, java.time.LocalTime.of(9, 0), java.time.LocalTime.of(10, 0), "101호");
        var overlapping = java.util.List.of(
            new LectureSchedule(lecture, 2, java.time.LocalTime.of(9, 0), java.time.LocalTime.of(11, 0), "102호"),
            new LectureSchedule(lecture, 2, java.time.LocalTime.of(10, 0), java.time.LocalTime.of(12, 0), "103호"));
        assertThatThrownBy(() -> lecture.revise("수정 교수", "002", overlapping))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(lecture.getProfessorName()).isEqualTo("기존 교수");
        assertThat(lecture.getSection()).isEqualTo("001");
        assertThat(lecture.getSchedules()).hasSize(1);
        assertThat(lecture.getSchedules().getFirst().getClassroom()).isEqualTo("101호");
    }
}
