package com.ahni.backend.service;

import com.ahni.backend.domain.*;
import com.ahni.backend.dto.TimetableRequest;
import com.ahni.backend.entity.*;
import com.ahni.backend.exception.*;
import com.ahni.backend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class TimetableServiceTest {
    @Mock StudentRepository students;
    @Mock LectureRepository lectures;
    @Mock TimetableRepository timetables;
    TimetableService service;
    final UUID user = UUID.randomUUID();
    Student student;
    @BeforeEach void setup() {
        service = new TimetableService(students, lectures, timetables);
        student = new Student(user, "test@inha.edu", 2024, EnrollmentStatus.ENROLLED, "학생");
    }
    Lecture lecture(String code, int day, int start, int end) {
        var lecture = new Lecture(new Course(null, code, code, new BigDecimal("3.0"), CourseCategory.MAJOR),
            "교수", 2026, AcademicTerm.SECOND, "001");
        lecture.addSchedule(day, LocalTime.of(start, 0), LocalTime.of(end, 0), "101호");
        return lecture;
    }
    void lock() { when(students.findForTimetableUpdate(user)).thenReturn(Optional.of(student)); }
    void existing(Timetable entry) {
        when(timetables.findAllByStudentAndLectureAcademicYearAndLectureTermOrderByCreatedAtAsc(
            student, 2026, AcademicTerm.SECOND)).thenReturn(List.of(entry));
    }
    @Test void createsAdjacentLectureAndLocksStudent() {
        lock();
        var next = lecture("NEXT", 1, 10, 11);
        existing(new Timetable(student, lecture("OLD", 1, 9, 10)));
        when(lectures.findByEntityId(next.getEntityId())).thenReturn(Optional.of(next));
        when(timetables.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.create(user, new TimetableRequest(next.getEntityId())).lecture().courseCode()).isEqualTo("NEXT");
        verify(students).findForTimetableUpdate(user);
    }
    @Test void rejectsOverlapWithoutSaving() {
        lock();
        var next = lecture("NEXT", 1, 9, 11);
        existing(new Timetable(student, lecture("OLD", 1, 10, 12)));
        when(lectures.findByEntityId(next.getEntityId())).thenReturn(Optional.of(next));
        assertThatThrownBy(() -> service.create(user, new TimetableRequest(next.getEntityId())))
            .isInstanceOf(TimetableConflictException.class).hasMessageContaining("OLD");
        verify(timetables, never()).saveAndFlush(any());
    }
    @Test void rejectsDuplicate() {
        lock();
        var lecture = lecture("NEXT", 1, 9, 11);
        existing(new Timetable(student, lecture));
        when(lectures.findByEntityId(lecture.getEntityId())).thenReturn(Optional.of(lecture));
        assertThatThrownBy(() -> service.create(user, new TimetableRequest(lecture.getEntityId())))
            .isInstanceOf(TimetableConflictException.class).hasMessageContaining("이미");
    }
    @Test void updateExcludesOwnEntry() {
        lock();
        var entry = new Timetable(student, lecture("OLD", 1, 9, 11));
        var next = lecture("NEXT", 1, 9, 11);
        existing(entry);
        when(timetables.findByEntityIdAndStudent(entry.getEntityId(), student)).thenReturn(Optional.of(entry));
        when(lectures.findByEntityId(next.getEntityId())).thenReturn(Optional.of(next));
        when(timetables.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.update(user, entry.getEntityId(), new TimetableRequest(next.getEntityId()))
            .lecture().entityId()).isEqualTo(next.getEntityId());
    }
    @Test void refusesAnotherStudentsEntry() {
        lock();
        var id = UUID.randomUUID();
        when(timetables.findByEntityIdAndStudent(id, student)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(user, id)).isInstanceOf(TimetableNotFoundException.class);
        verify(timetables, never()).delete(any(Timetable.class));
    }
    @Test void missingProfileIsRejected() {
        when(students.findForTimetableUpdate(user)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(user, new TimetableRequest(UUID.randomUUID())))
            .isInstanceOf(StudentNotFoundException.class);
        verifyNoInteractions(lectures, timetables);
    }
    @Test void listsOnlySelectedSemester() {
        when(students.findByAuthUserId(user)).thenReturn(Optional.of(student));
        when(timetables.findAllByStudentAndLectureAcademicYearAndLectureTermOrderByCreatedAtAsc(
            student, 2026, AcademicTerm.FIRST)).thenReturn(List.of());
        assertThat(service.list(user, 2026, AcademicTerm.FIRST)).isEmpty();
    }
}
