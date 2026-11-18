package com.ahni.backend.repository;

import com.ahni.backend.entity.Notice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, UUID> {
    Optional<Notice> findByEntityIdAndDeletedAtIsNull(UUID entityId);
    List<Notice> findAllByDeletedAtIsNullOrderByUpdatedAtDescEntityIdAsc();
    List<Notice> findAllByDeletedAtIsNullAndPublishedAtIsNotNullOrderByPublishedAtDescEntityIdAsc();
}
