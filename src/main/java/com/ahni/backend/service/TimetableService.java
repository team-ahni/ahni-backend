package com.ahni.backend.service;

import com.ahni.backend.domain.AcademicTerm;
import com.ahni.backend.dto.*;
import com.ahni.backend.entity.*;
import com.ahni.backend.exception.*;
import com.ahni.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class TimetableService {
    private final StudentRepository students;
    private final LectureRepository lectures;
    private final TimetableRepository timetables;

    public TimetableService(StudentRepository students, LectureRepository lectures, TimetableRepository timetables) {
        this.students = students; this.lectures = lectures; this.timetables = timetables;
    }

    public List<TimetableResponse> list(UUID user, int year, AcademicTerm term) {
        if (year < 2000 || year > 9999 || term == null) throw new IllegalArgumentException();
        Student student = students.findByAuthUserId(user).filter(s -> s.getDeletedAt() == null)
            .orElseThrow(StudentNotFoundException::new);
        return timetables.findAllByStudentAndLectureAcademicYearAndLectureTermOrderByCreatedAtAsc(student, year, term)
            .stream().map(TimetableService::response).toList();
    }

    @Transactional
    public TimetableResponse create(UUID user, TimetableRequest request) {
        Student student = lockStudent(user);
        Lecture lecture = findLecture(request.lectureEntityId());
        checkConflicts(student, lecture, null);
        return response(timetables.saveAndFlush(new Timetable(student, lecture)));
    }

    @Transactional
    public TimetableResponse update(UUID user, UUID id, TimetableRequest request) {
        Student student = lockStudent(user);
        Timetable entry = timetables.findByEntityIdAndStudent(id, student).orElseThrow(TimetableNotFoundException::new);
        Lecture lecture = findLecture(request.lectureEntityId());
        if (entry.getLecture().getAcademicYear() != lecture.getAcademicYear()
            || entry.getLecture().getTerm() != lecture.getTerm())
            throw new IllegalArgumentException("같은 연도·학기의 강의만 변경할 수 있습니다.");
        checkConflicts(student, lecture, id);
        entry.changeLecture(lecture);
        return response(timetables.saveAndFlush(entry));
    }

    @Transactional
    public void delete(UUID user, UUID id) {
        Student student = lockStudent(user);
        timetables.delete(timetables.findByEntityIdAndStudent(id, student).orElseThrow(TimetableNotFoundException::new));
    }

    private Student lockStudent(UUID user) {
        // Serialize one student's writes so simultaneous additions cannot bypass conflict checks.
        return students.findForTimetableUpdate(user).orElseThrow(StudentNotFoundException::new);
    }

    private Lecture findLecture(UUID id) {
        return lectures.findByEntityId(id).orElseThrow(LectureNotFoundException::new);
    }

    private void checkConflicts(Student student, Lecture lecture, UUID excluded) {
        var entries = timetables.findAllByStudentAndLectureAcademicYearAndLectureTermOrderByCreatedAtAsc(
            student, lecture.getAcademicYear(), lecture.getTerm());
        for (Timetable entry : entries) {
            if (entry.getEntityId().equals(excluded)) continue;
            Lecture existing = entry.getLecture();
            if (existing.getEntityId().equals(lecture.getEntityId()))
                throw new TimetableConflictException("이미 시간표에 등록한 강의입니다.");
            for (LectureSchedule next : lecture.getSchedules()) {
                for (LectureSchedule current : existing.getSchedules()) {
                    if (next.overlaps(current))
                        throw new TimetableConflictException(existing.getCourse().getName() + " 강의와 "
                            + java.time.DayOfWeek.of(current.getDayOfWeek()).getDisplayName(
                                java.time.format.TextStyle.FULL, java.util.Locale.KOREAN)
                            + " " + current.getStartTime() + "~" + current.getEndTime()
                            + " 시간이 겹칩니다.");
                }
            }
        }
    }

    private static TimetableResponse response(Timetable entry) {
        return new TimetableResponse(entry.getEntityId(), LectureService.response(entry.getLecture()),
            entry.getCreatedAt(), entry.getUpdatedAt());
    }
}
