package com.ahni.backend.entity;

import com.ahni.backend.domain.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalTime;
import static org.assertj.core.api.Assertions.*;

class LectureScheduleTest {
    private Lecture lecture() {
        return new Lecture(new Course(null, "TEST101", "자료구조", new BigDecimal("3.0"), CourseCategory.MAJOR),
            new Professor(null, "교수", null), 2026, AcademicTerm.SECOND, "001");
    }
    @Test
    void overlappingAndAdjacentTimes() {
        var lecture = lecture();
        var a = new LectureSchedule(lecture, 1, LocalTime.of(9, 0), LocalTime.of(10, 0), null);
        assertThat(a.overlaps(new LectureSchedule(lecture, 1, LocalTime.of(9, 30), LocalTime.of(10, 30), null))).isTrue();
        assertThat(a.overlaps(new LectureSchedule(lecture, 1, LocalTime.of(10, 0), LocalTime.of(11, 0), null))).isFalse();
        assertThat(a.overlaps(new LectureSchedule(lecture, 2, LocalTime.of(9, 0), LocalTime.of(10, 0), null))).isFalse();
    }
    @Test
    void invalidTimesAndDuplicateMeetingsAreRejected() {
        var lecture = lecture();
        assertThatThrownBy(() -> lecture.addSchedule(8, LocalTime.of(9, 0), LocalTime.of(10, 0), null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lecture.addSchedule(1, LocalTime.of(10, 0), LocalTime.of(9, 0), null))
            .isInstanceOf(IllegalArgumentException.class);
        lecture.addSchedule(1, LocalTime.of(9, 0), LocalTime.of(10, 0), null);
        assertThatThrownBy(() -> lecture.addSchedule(1, LocalTime.of(9, 30), LocalTime.of(11, 0), null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lecture.addSchedule(1, LocalTime.of(11, 0, 1), LocalTime.of(12, 0), null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
