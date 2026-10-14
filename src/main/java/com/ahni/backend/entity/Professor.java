package com.ahni.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
public class Professor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, updatable = false)
    private UUID entityId = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(length = 200)
    private String office;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant deletedAt;

    protected Professor() { }

    public Professor(Department department, String name, String office) {
        if (name == null || name.isBlank() || name.trim().length() > 100)
            throw new IllegalArgumentException("교수명을 확인해 주세요.");
        if (office != null && office.length() > 200)
            throw new IllegalArgumentException("연구실 위치가 너무 깁니다.");
        this.department = department;
        this.name = name.trim();
        this.office = office;
    }

    @PrePersist
    void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}
