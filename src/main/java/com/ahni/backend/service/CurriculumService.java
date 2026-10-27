package com.ahni.backend.service;

import com.ahni.backend.domain.CourseCategory;
import com.ahni.backend.domain.CurriculumDivision;
import com.ahni.backend.dto.*;
import com.ahni.backend.entity.*;
import com.ahni.backend.exception.*;
import com.ahni.backend.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class CurriculumService {
    private final AdminAccessService access;
    private final CurriculumRepository curricula;
    private final CurriculumCourseRepository links;
    private final CourseRepository courses;
    private final DepartmentRepository departments;
    private final LectureRepository lectures;

    public CurriculumService(AdminAccessService access, CurriculumRepository curricula,
        CurriculumCourseRepository links, CourseRepository courses, DepartmentRepository departments, LectureRepository lectures) {
        this.access = access;
        this.curricula = curricula;
        this.links = links;
        this.courses = courses;
        this.departments = departments;
        this.lectures = lectures;
    }

    public List<CurriculumResponse> list(UUID authUserId) {
        access.getCurrentAdmin(authUserId);
        List<Curriculum> found = curricula.findAllByOrderByDepartmentNameAscCurriculumYearDesc();
        if (found.isEmpty()) return List.of();
        var grouped = links.findAllByCurriculumInOrderByCourseCodeAsc(found).stream()
            .collect(java.util.stream.Collectors.groupingBy(link -> link.getCurriculum().getId()));
        return found.stream().map(curriculum -> response(curriculum, grouped.getOrDefault(curriculum.getId(), List.of()))).toList();
    }

    public CurriculumResponse get(UUID authUserId, UUID entityId) {
        access.getCurrentAdmin(authUserId);
        return response(curricula.findByEntityId(entityId).orElseThrow(CurriculumNotAvailableException::new));
    }

    public List<CourseResponse> getCourses(int academicYear, UUID departmentEntityId) {
        if (academicYear < 2000 || academicYear > 9999) throw new IllegalArgumentException("교과과정 연도가 올바르지 않습니다.");
        if (departmentEntityId != null) departments.findByEntityIdAndDeletedAtIsNull(departmentEntityId).orElseThrow(DepartmentNotFoundException::new);
        List<Course> published = links.findPublishedCourses(academicYear, departmentEntityId);
        if (published.isEmpty()) throw new CurriculumNotAvailableException();
        return published.stream().map(CurriculumService::courseResponse).toList();
    }

    public List<OfferedCourseResponse> getOfferedCourses(int year, com.ahni.backend.domain.AcademicTerm term) {
        if (year < 2000 || year > 9999 || term == null) throw new IllegalArgumentException("수강 연도와 학기를 확인해 주세요.");
        var grouped = lectures.findOfferedCourses(year, term).stream()
            .collect(java.util.stream.Collectors.groupingBy(lecture -> lecture.getCourse().getEntityId(),
                LinkedHashMap::new, java.util.stream.Collectors.toList()));
        return grouped.values().stream().map(group -> {
            var base = courseResponse(group.getFirst().getCourse());
            var professors = group.stream().map(Lecture::getProfessorName).distinct().toList();
            return new OfferedCourseResponse(base.entityId(), base.code(), base.name(), base.credit(),
                base.category(), base.department(), professors);
        }).toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Course requireOfferedCourse(int year, com.ahni.backend.domain.AcademicTerm term, UUID courseEntityId) {
        Course course = courses.findByEntityId(courseEntityId).orElseThrow(CourseNotFoundException::new);
        if (!lectures.existsByCourseEntityIdAndAcademicYearAndTerm(courseEntityId, year, term))
            throw new InvalidGradeException("선택한 연도와 학기에 개설되지 않은 과목입니다.");
        return course;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Course requirePublishedCourse(int academicYear, UUID courseEntityId) {
        List<Curriculum> published = curricula.findYearForValidation(academicYear).stream()
            .filter(Curriculum::isPublished).toList();
        if (published.isEmpty()) throw new CurriculumNotAvailableException();
        Course course = courses.findByEntityId(courseEntityId).orElseThrow(CourseNotFoundException::new);
        boolean connected = links.findAllByCurriculumInOrderByCourseCodeAsc(published).stream()
            .anyMatch(link -> link.getCourse().getEntityId().equals(courseEntityId));
        if (!connected) throw new CourseNotInCurriculumException();
        return course;
    }

    @Transactional
    public CurriculumResponse create(UUID authUserId, CurriculumRequest input) {
        access.getCurrentAdmin(authUserId);
        if (input.version() != null) throw new IllegalArgumentException("생성 시 버전을 입력하지 않습니다.");
        Department department = departments.findByEntityIdAndDeletedAtIsNull(input.departmentEntityId()).orElseThrow(DepartmentNotFoundException::new);
        if (curricula.existsByDepartmentAndCurriculumYear(department, input.curriculumYear())) throw new CurriculumAlreadyExistsException();
        var curriculum = new Curriculum(department, input.curriculumYear(), input.sourceTitle(), input.sourceUrl());
        List<CurriculumCourse> next = buildLinks(curriculum, input.courses());
        try {
            curricula.saveAndFlush(curriculum);
        } catch (DataIntegrityViolationException exception) {
            String cause = exception.getMostSpecificCause().getMessage();
            if (cause != null && cause.contains("uq_curriculum_department_year")) throw new CurriculumAlreadyExistsException();
            throw exception;
        }
        links.saveAllAndFlush(next);
        return response(curriculum);
    }

    @Transactional
    public CurriculumResponse update(UUID authUserId, UUID entityId, CurriculumRequest input) {
        access.getCurrentAdmin(authUserId);
        Curriculum curriculum = locked(entityId, input.version());
        if (!curriculum.getDepartment().getEntityId().equals(input.departmentEntityId())
            || curriculum.getCurriculumYear() != input.curriculumYear()) throw new IllegalArgumentException("학과와 연도는 변경할 수 없습니다.");
        List<CurriculumCourse> next = buildLinks(curriculum, input.courses());
        curriculum.updateSource(input.sourceTitle(), input.sourceUrl());
        links.deleteAll(links.findAllByCurriculumOrderByCourseCodeAsc(curriculum));
        links.flush();
        links.saveAllAndFlush(next);
        curricula.flush();
        return response(curriculum);
    }

    @Transactional
    public CurriculumResponse publish(UUID authUserId, UUID entityId, CurriculumPublicationRequest input) {
        access.getCurrentAdmin(authUserId);
        Curriculum curriculum = locked(entityId, input.version());
        List<CurriculumCourse> assignments = links.findAllByCurriculumOrderByCourseCodeAsc(curriculum);
        if (input.published() && assignments.isEmpty()) throw new IllegalArgumentException("빈 교과과정은 공개할 수 없습니다.");
        for (CurriculumCourse assignment : assignments) validateCourse(assignment.getCourse(), assignment.getDivision());
        curriculum.setPublished(input.published());
        curricula.flush();
        return response(curriculum);
    }

    private Curriculum locked(UUID entityId, Long version) {
        if (version == null) throw new IllegalArgumentException("편집 버전은 필수입니다.");
        Curriculum curriculum = curricula.findForUpdate(entityId).orElseThrow(CurriculumNotAvailableException::new);
        if (curriculum.getVersion() != version) throw new CurriculumEditConflictException();
        return curriculum;
    }

    private List<CurriculumCourse> buildLinks(Curriculum curriculum, List<CurriculumCourseRequest> input) {
        var ids = new HashSet<UUID>();
        for (var row : input) if (!ids.add(row.courseEntityId())) throw new IllegalArgumentException("같은 과목을 중복 연결할 수 없습니다.");
        var catalog = ids.isEmpty() ? Map.<UUID, Course>of() : courses.findCurriculumCandidates(new ArrayList<>(ids)).stream()
            .collect(java.util.stream.Collectors.toMap(Course::getEntityId, course -> course));
        var next = new ArrayList<CurriculumCourse>();
        for (var row : input) {
            Course course = Optional.ofNullable(catalog.get(row.courseEntityId())).orElseThrow(CourseNotFoundException::new);
            validateCourse(course, row.division());
            next.add(new CurriculumCourse(curriculum, course, row.division(), row.recommendedYear(),
                row.recommendedTerm(), row.areaCode(), row.areaName(), row.majorArea(), row.note()));
        }
        return next;
    }

    private static void validateCourse(Course course, CurriculumDivision division) {
        CourseCategory category = switch (division) {
            case MAJOR_REQUIRED, MAJOR_ELECTIVE, MAJOR_FOUNDATION -> CourseCategory.MAJOR;
            case GENERAL_REQUIRED, GENERAL_ELECTIVE -> CourseCategory.GENERAL_EDUCATION;
            case ELECTIVE -> CourseCategory.ELECTIVE;
        };
        if (course.getCategory() != category) throw new IllegalArgumentException("과목 영역과 교과과정 분류가 일치하지 않습니다.");
    }

    private CurriculumResponse response(Curriculum curriculum) {
        return response(curriculum, links.findAllByCurriculumOrderByCourseCodeAsc(curriculum));
    }

    private CurriculumResponse response(Curriculum curriculum, List<CurriculumCourse> rows) {
        Department department = curriculum.getDepartment();
        List<CurriculumCourseResponse> assignments = rows.stream()
            .map(link -> new CurriculumCourseResponse(link.getEntityId(), courseResponse(link.getCourse()),
                link.getDivision(), link.getRecommendedYear(), link.getRecommendedTerm(), link.getAreaCode(),
                link.getAreaName(), link.getMajorArea(), link.getNote())).toList();
        return new CurriculumResponse(curriculum.getEntityId(), curriculum.getVersion(),
            new DepartmentResponse(department.getEntityId(), department.getName()), curriculum.getCurriculumYear(),
            curriculum.getSourceTitle(), curriculum.getSourceUrl(), curriculum.isPublished(), assignments);
    }

    private static CourseResponse courseResponse(Course course) {
        var department = course.getDepartment();
        return new CourseResponse(course.getEntityId(), course.getCode(), course.getName(), course.getCredit(),
            course.getCategory(), department == null ? null : new DepartmentResponse(department.getEntityId(), department.getName()));
    }
}
