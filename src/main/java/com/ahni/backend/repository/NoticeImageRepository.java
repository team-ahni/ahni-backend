package com.ahni.backend.repository;

import com.ahni.backend.entity.NoticeImage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeImageRepository extends JpaRepository<NoticeImage, UUID> {
    List<NoticeImage> findAllByNoticeIdAndDeletedAtIsNull(UUID noticeId);
    Optional<NoticeImage> findByEntityIdAndNoticeIdAndDeletedAtIsNull(UUID entityId, UUID noticeId);
}
