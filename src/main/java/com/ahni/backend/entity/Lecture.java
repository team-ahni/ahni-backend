package com.ahni.backend.entity;

import com.ahni.backend.domain.AcademicTerm;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
public class Lecture {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, updatable = false)
    private UUID entityId = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;
    @Column(nullable = false, length = 500)
    private String professorName;
    @Column(nullable = false)
    private int academicYear;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private AcademicTerm term;
    @Column(nullable = false, length = 30)
    private String section;
    @OneToMany(mappedBy = "lecture", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayOfWeek ASC, startTime ASC")
    private List<LectureSchedule> schedules = new ArrayList<>();
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected Lecture() { }

    public Lecture(Course course, String professorName, int year, AcademicTerm term, String section) {
        if (course == null || professorName == null || professorName.isBlank() || professorName.trim().length() > 500
            || term == null || year < 2000 || year > 9999
            || section == null || section.isBlank() || section.trim().length() > 30)
            throw new IllegalArgumentException("개설 강의 정보를 확인해 주세요.");
        this.course = course;
        this.professorName = professorName.trim();
        this.academicYear = year;
        this.term = term;
        this.section = section.trim();
    }

    public void addSchedule(int day, java.time.LocalTime start, java.time.LocalTime end, String classroom) {
        LectureSchedule next = new LectureSchedule(this, day, start, end, classroom);
        if (schedules.stream().anyMatch(existing -> existing.overlaps(next)))
            throw new IllegalArgumentException("한 강의의 수업 시간이 서로 겹칩니다.");
        schedules.add(next);
    }

    @PrePersist
    void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}
