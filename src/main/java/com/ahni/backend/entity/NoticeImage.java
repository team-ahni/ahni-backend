package com.ahni.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Getter
public class NoticeImage implements Persistable<UUID> {
    @Transient
    private boolean newImage = true;
    @Id
    private UUID entityId;
    @Column(nullable = false, updatable = false)
    private UUID noticeId;
    @Column(nullable = false, unique = true, length = 200, updatable = false)
    private String storageKey;
    @Column(nullable = false, length = 32, updatable = false)
    private String contentType;
    @Column(nullable = false, updatable = false)
    private long sizeBytes;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    private Instant deletedAt;

    protected NoticeImage() {}

    public NoticeImage(UUID id, UUID noticeId, String contentType, long sizeBytes) {
        this.entityId = id;
        this.noticeId = noticeId;
        this.storageKey = "notices/" + noticeId + "/" + id + "/" + UUID.randomUUID();
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = Instant.now();
    }

    @Override
    public UUID getId() {
        return entityId;
    }

    @Override
    public boolean isNew() {
        return newImage;
    }

    @PostLoad
    @PostPersist
    private void markPersisted() {
        newImage = false;
    }

    public void softDelete() {
        deletedAt = Instant.now();
    }
}
