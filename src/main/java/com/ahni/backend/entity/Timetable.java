package com.ahni.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
public class Timetable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, updatable = false)
    private UUID entityId = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_id", nullable = false)
    private Lecture lecture;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected Timetable() { }
    public Timetable(Student student, Lecture lecture) {
        if (student == null || lecture == null) throw new IllegalArgumentException("학생과 강의는 필수입니다.");
        this.student = student;
        this.lecture = lecture;
    }
    public void changeLecture(Lecture lecture) {
        if (lecture == null) throw new IllegalArgumentException("강의는 필수입니다.");
        this.lecture = lecture;
    }
    @PrePersist
    void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}
