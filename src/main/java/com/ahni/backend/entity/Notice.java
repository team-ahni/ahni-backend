package com.ahni.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "notice")
@Getter
public class Notice {
    @Id
    private UUID entityId;
    @Column(nullable = false, length = 150)
    private String title;
    @Column(nullable = false, length = 10000)
    private String body;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant publishedAt;
    private Instant deletedAt;
    @Column(updatable = false)
    private Instant createdAt;
    @Column(updatable = false, length = 100)
    private String createdByName;
    @Version
    private long version;

    protected Notice() {}

    public Notice(String title, String body, String createdByName) {
        this.entityId = UUID.randomUUID();
        update(title, body, false);
        this.createdAt = updatedAt;
        this.createdByName = createdByName;
    }

    public void softDelete() {
        deletedAt = Instant.now();
    }

    public void update(String title, String body, boolean published) {
        this.title = title.strip();
        this.body = body.strip();
        this.updatedAt = Instant.now();
        this.publishedAt = published ? (publishedAt == null ? updatedAt : publishedAt) : null;
    }
}
