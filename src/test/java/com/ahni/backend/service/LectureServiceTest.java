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
    @Mock ProfessorRepository professors;
    @Mock CourseRepository courses;
    @Mock DepartmentRepository departments;
    @Mock AdminAccessService admins;
    LectureService service;
    UUID user = UUID.randomUUID();

    @BeforeEach void setup() {
        service = new LectureService(lectures, professors, courses, departments, admins);
    }

    @Test void studentCannotCreateProfessorOrAccessCatalogWrites() {
        when(admins.getAdmin(user)).thenThrow(new AdminAccessDeniedException());
        assertThatThrownBy(() -> service.createProfessor(user, new ProfessorRequest(null, "교수", null)))
            .isInstanceOf(AdminAccessDeniedException.class);
        assertThatThrownBy(() -> service.importLectures(user, new LectureImportRequest(List.of())))
            .isInstanceOf(AdminAccessDeniedException.class);
        verifyNoInteractions(lectures, professors, courses, departments);
    }

    @Test void createsOfferingWithMultipleWeeklySchedules() {
        var course = new Course(null, "TT101", "자료구조", new BigDecimal("3.0"), CourseCategory.MAJOR);
        var professor = new Professor(null, "교수", null);
        when(courses.findByEntityIdAndActiveTrue(course.getEntityId())).thenReturn(Optional.of(course));
        when(professors.findByEntityIdAndDeletedAtIsNull(professor.getEntityId())).thenReturn(Optional.of(professor));
        when(lectures.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.create(user, new LectureRequest(course.getEntityId(), professor.getEntityId(),
            2026, AcademicTerm.SECOND, "001", List.of(
                new LectureScheduleRequest(1, LocalTime.of(9, 30), LocalTime.of(11, 0), "101호"),
                new LectureScheduleRequest(3, LocalTime.of(13, 0), LocalTime.of(14, 30), "102호"))));
        assertThat(result.schedules()).hasSize(2);
        assertThat(result.courseName()).isEqualTo("자료구조");
        verify(admins).getAdmin(user);
    }
}
