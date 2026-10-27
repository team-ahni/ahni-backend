package com.ahni.backend.service;

import com.ahni.backend.domain.*;
import com.ahni.backend.dto.*;
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
class LectureServiceTest {
    @Mock LectureRepository lectures;
    @Mock CourseRepository courses;
    @Mock AdminAccessService admins;
    @Mock TimetableRepository timetables;
    LectureService service;
    UUID user = UUID.randomUUID();

    @BeforeEach void setup() {
        service = new LectureService(lectures, courses, admins, timetables);
    }

    @Test void studentCannotAccessCatalogWrites() {
        when(admins.getAdmin(user)).thenThrow(new AdminAccessDeniedException());
        assertThatThrownBy(() -> service.importLectures(user, new LectureImportRequest(List.of())))
            .isInstanceOf(AdminAccessDeniedException.class);
        verifyNoInteractions(lectures, courses);
    }

    @Test void createsOfferingWithMultipleWeeklySchedules() {
        var course = new Course(null, "TT101", "자료구조", new BigDecimal("3.0"), CourseCategory.MAJOR);
        var professor = "남춘성,이우형";
        when(courses.findByEntityIdAndActiveTrue(course.getEntityId())).thenReturn(Optional.of(course));
        when(lectures.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.create(user, new LectureRequest(course.getEntityId(), professor,
            2026, AcademicTerm.SECOND, "001", List.of(
                new LectureScheduleRequest(1, LocalTime.of(9, 30), LocalTime.of(11, 0), "101호"),
                new LectureScheduleRequest(3, LocalTime.of(13, 0), LocalTime.of(14, 30), "102호"))));
        assertThat(result.schedules()).hasSize(2);
        assertThat(result.courseName()).isEqualTo("자료구조");
        assertThat(result.professorName()).isEqualTo("남춘성,이우형");
        verify(admins).getAdmin(user);
    }

    @Test void updatesExistingOfferingWithoutRequiringAnActiveCourseAndKeepsIdentity() {
        var course = new Course(null, "TT101", "자료구조", new BigDecimal("3.0"), CourseCategory.MAJOR);
        course.deactivate();
        var lecture = new Lecture(course, "교수", 2026, AcademicTerm.SECOND, "001");
        lecture.addSchedule(1, LocalTime.of(9, 0), LocalTime.of(10, 0), "101호");
        when(lectures.findForCatalogUpdate(lecture.getEntityId())).thenReturn(Optional.of(lecture));
        when(lectures.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.update(user, lecture.getEntityId(), new LectureUpdateRequest(course.getEntityId(),
            "남춘성,이우형", 2026, AcademicTerm.SECOND, "002", List.of()));
        assertThat(result.entityId()).isEqualTo(lecture.getEntityId());
        assertThat(result.professorName()).isEqualTo("남춘성,이우형");
        assertThat(result.section()).isEqualTo("002");
        assertThat(result.schedules()).isEmpty();
        verifyNoInteractions(courses, timetables);
    }

    @Test void updateRejectsIdentityChangesAndDuplicateSectionsWithoutMutating() {
        var course = new Course(null, "TT101", "자료구조", new BigDecimal("3.0"), CourseCategory.MAJOR);
        var lecture = new Lecture(course, "기존 교수", 2026, AcademicTerm.SECOND, "001");
        when(lectures.findForCatalogUpdate(lecture.getEntityId())).thenReturn(Optional.of(lecture));
        assertThatThrownBy(() -> service.update(user, lecture.getEntityId(), new LectureUpdateRequest(
            UUID.randomUUID(), "수정 교수", 2026, AcademicTerm.SECOND, "002", List.of())))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(user, lecture.getEntityId(), new LectureUpdateRequest(
            course.getEntityId(), "수정 교수", 2027, AcademicTerm.FIRST, "002", List.of())))
            .isInstanceOf(IllegalArgumentException.class);
        when(lectures.existsByCourseIdAndAcademicYearAndTermAndSectionAndEntityIdNot(
            null, 2026, AcademicTerm.SECOND, "002", lecture.getEntityId())).thenReturn(true);
        assertThatThrownBy(() -> service.update(user, lecture.getEntityId(), new LectureUpdateRequest(
            course.getEntityId(), "수정 교수", 2026, AcademicTerm.SECOND, "002", List.of())))
            .isInstanceOf(TimetableConflictException.class);
        assertThat(lecture.getProfessorName()).isEqualTo("기존 교수");
        verify(lectures, never()).saveAndFlush(any());
    }

    @Test void deleteClearsOnlyTheTargetLectureLinksBeforeDeletingTheLecture() {
        var course = new Course(null, "TT101", "자료구조", new BigDecimal("3.0"), CourseCategory.MAJOR);
        var lecture = new Lecture(course, "교수", 2026, AcademicTerm.SECOND, "001");
        when(lectures.findForCatalogUpdate(lecture.getEntityId())).thenReturn(Optional.of(lecture));
        service.delete(user, lecture.getEntityId());
        var order = inOrder(admins, lectures, timetables);
        order.verify(admins).getAdmin(user);
        order.verify(lectures).findForCatalogUpdate(lecture.getEntityId());
        order.verify(timetables).deleteAllForLecture(lecture);
        order.verify(lectures).delete(lecture);
        verifyNoInteractions(courses);
    }

    @Test void updateAndDeleteRequireAnAdminAndAnExistingLecture() {
        var id = UUID.randomUUID();
        var request = new LectureUpdateRequest(UUID.randomUUID(), "교수", 2026, AcademicTerm.SECOND, "001", List.of());
        assertThatThrownBy(() -> service.update(user, id, request)).isInstanceOf(LectureNotFoundException.class);
        assertThatThrownBy(() -> service.delete(user, id)).isInstanceOf(LectureNotFoundException.class);
        reset(lectures);
        when(admins.getAdmin(user)).thenThrow(new AdminAccessDeniedException());
        assertThatThrownBy(() -> service.update(user, id, request)).isInstanceOf(AdminAccessDeniedException.class);
        assertThatThrownBy(() -> service.delete(user, id)).isInstanceOf(AdminAccessDeniedException.class);
        verifyNoInteractions(lectures, timetables, courses);
    }
}
