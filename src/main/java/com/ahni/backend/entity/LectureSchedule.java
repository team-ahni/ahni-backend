package com.ahni.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Getter
public class LectureSchedule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, updatable = false)
    private UUID entityId = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_id", nullable = false)
    private Lecture lecture;
    @Column(nullable = false)
    private int dayOfWeek;
    @Column(nullable = false)
    private LocalTime startTime;
    @Column(nullable = false)
    private LocalTime endTime;
    @Column(length = 200)
    private String classroom;

    protected LectureSchedule() { }

    public LectureSchedule(Lecture lecture, int day, LocalTime start, LocalTime end, String classroom) {
        if (lecture == null || day < 1 || day > 7 || start == null || end == null || !start.isBefore(end)
            || start.getSecond() != 0 || end.getSecond() != 0 || start.getNano() != 0 || end.getNano() != 0
            || (classroom != null && classroom.length() > 200))
            throw new IllegalArgumentException("수업 요일과 시작·종료 시간을 확인해 주세요.");
        this.lecture = lecture;
        this.dayOfWeek = day;
        this.startTime = start;
        this.endTime = end;
        this.classroom = classroom;
    }

    public boolean overlaps(LectureSchedule other) {
        return dayOfWeek == other.dayOfWeek
            && startTime.isBefore(other.endTime) && other.startTime.isBefore(endTime);
    }
}
