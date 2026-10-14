package com.ahni.backend.service;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.dto.*;
import com.ahni.backend.entity.*;
import com.ahni.backend.exception.*;
import com.ahni.backend.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class LectureService {
    private final LectureRepository lectures;
    private final ProfessorRepository professors;
    private final CourseRepository courses;
    private final DepartmentRepository departments;
    private final AdminAccessService admins;

    public LectureService(LectureRepository lectures, ProfessorRepository professors,
        CourseRepository courses, DepartmentRepository departments, AdminAccessService admins) {
        this.lectures = lectures; this.professors = professors; this.courses = courses;
        this.departments = departments; this.admins = admins;
    }

    public List<LectureResponse> list(int year, AcademicTerm term) {
        if (year < 2000 || year > 9999 || term == null) throw new IllegalArgumentException();
        return lectures.findAllByAcademicYearAndTermOrderByCourseCodeAscSectionAsc(year, term).stream()
            .map(LectureService::response).toList();
    }

    public List<ProfessorResponse> professors(UUID user) {
        admins.getAdmin(user);
        return professors.findAllByDeletedAtIsNullOrderByNameAsc().stream().map(LectureService::response).toList();
    }

    @Transactional
    public ProfessorResponse createProfessor(UUID user, ProfessorRequest request) {
        admins.getAdmin(user);
        Department department = request.departmentEntityId() == null ? null
            : departments.findByEntityIdAndDeletedAtIsNull(request.departmentEntityId())
                .orElseThrow(DepartmentNotFoundException::new);
        return response(professors.saveAndFlush(new Professor(department, request.name(), request.office())));
    }

    @Transactional
    public LectureResponse create(UUID user, LectureRequest request) {
        admins.getAdmin(user);
        return createChecked(request);
    }

    @Transactional
    public List<LectureResponse> importLectures(UUID user, LectureImportRequest request) {
        admins.getAdmin(user);
        if (request.lectures() == null || request.lectures().isEmpty() || request.lectures().size() > 200)
            throw new IllegalArgumentException("한 번에 1~200강의만 등록할 수 있습니다.");
        int year = request.lectures().getFirst().academicYear();
        AcademicTerm term = request.lectures().getFirst().term();
        Set<String> keys = new HashSet<>();
        for (LectureRequest lecture : request.lectures()) {
            if (lecture.academicYear() != year || lecture.term() != term)
                throw new IllegalArgumentException("같은 연도·학기의 강의만 함께 등록할 수 있습니다.");
            if (!keys.add(lecture.courseEntityId() + ":" + lecture.section().trim()))
                throw new TimetableConflictException("파일에 같은 과목·분반이 중복되어 있습니다.");
        }
        // Keep the whole import in one transaction; a later failure rolls back earlier rows.
        return request.lectures().stream().map(this::createChecked).toList();
    }

    private LectureResponse createChecked(LectureRequest request) {
        Course course = courses.findByEntityIdAndActiveTrue(request.courseEntityId()).orElseThrow(CourseNotFoundException::new);
        Professor professor = professors.findByEntityIdAndDeletedAtIsNull(request.professorEntityId())
            .orElseThrow(LectureNotFoundException::new);
        if (lectures.existsByCourseIdAndAcademicYearAndTermAndSection(course.getId(),
            request.academicYear(), request.term(), request.section().trim()))
            throw new TimetableConflictException("같은 연도·학기의 분반이 이미 등록되어 있습니다.");
        Lecture lecture = new Lecture(course, professor, request.academicYear(), request.term(), request.section());
        request.schedules().forEach(s -> lecture.addSchedule(s.dayOfWeek(), s.startTime(), s.endTime(), s.classroom()));
        try { return response(lectures.saveAndFlush(lecture)); }
        catch (DataIntegrityViolationException exception) {
            throw new TimetableConflictException("같은 연도·학기의 분반이 이미 등록되어 있습니다.");
        }
    }

    static ProfessorResponse response(Professor professor) {
        return new ProfessorResponse(professor.getEntityId(), professor.getName(), professor.getOffice());
    }

    static LectureResponse response(Lecture lecture) {
        return new LectureResponse(lecture.getEntityId(), lecture.getCourse().getEntityId(),
            lecture.getCourse().getCode(), lecture.getCourse().getName(), lecture.getCourse().getCredit(),
            response(lecture.getProfessor()), lecture.getAcademicYear(), lecture.getTerm(), lecture.getSection(),
            lecture.getSchedules().stream().map(s -> new LectureScheduleResponse(s.getEntityId(),
                s.getDayOfWeek(), s.getStartTime(), s.getEndTime(), s.getClassroom())).toList());
    }
}
