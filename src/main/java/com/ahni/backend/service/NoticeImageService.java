package com.ahni.backend.service;

import com.ahni.backend.client.NoticeImageStorageClient;
import com.ahni.backend.domain.NoticeDocument;
import com.ahni.backend.dto.InquiryAttachmentFile;
import com.ahni.backend.entity.Notice;
import com.ahni.backend.entity.NoticeImage;
import com.ahni.backend.exception.NoticeException;
import com.ahni.backend.repository.NoticeImageRepository;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NoticeImageService {
    public record Upload(UUID imageId, String filename, String contentType, byte[] content) {}
    private static final Logger log = LoggerFactory.getLogger(NoticeImageService.class);
    private final NoticeImageRepository images;
    private final NoticeImageStorageClient storage;

    public NoticeImageService(NoticeImageRepository images, NoticeImageStorageClient storage) {
        this.images = images;
        this.storage = storage;
    }

    public void save(Notice notice, NoticeDocument document, List<Upload> uploads) {
        var existing = images.findAllByNoticeIdAndDeletedAtIsNull(notice.getEntityId());
        var availableIds = new HashSet<UUID>();
        existing.forEach(image -> availableIds.add(image.getEntityId()));
        if (uploads.size() > 10 || uploads.stream().mapToLong(upload -> upload.content().length).sum() > 5L * 1024 * 1024) {
            throw NoticeException.invalidContent();
        }
        var additions = uploads.stream().map(upload -> {
            if (upload.imageId() == null || !availableIds.add(upload.imageId())
                || images.existsById(upload.imageId()) || !document.imageIds().contains(upload.imageId())) {
                throw NoticeException.invalidContent();
            }
            try {
                String name = InquiryAttachmentService.sanitizeFilename(upload.filename());
                String type = InquiryAttachmentService.validateContent(name, upload.contentType(), upload.content());
                if (!type.equals("image/jpeg") && !type.equals("image/png")) throw NoticeException.invalidContent();
                return new NoticeImage(upload.imageId(), notice.getEntityId(), type, upload.content().length);
            } catch (com.ahni.backend.exception.InvalidInquiryAttachmentException
                     | com.ahni.backend.exception.UnsupportedInquiryAttachmentTypeException
                     | com.ahni.backend.exception.InquiryAttachmentTooLargeException exception) {
                throw NoticeException.invalidContent();
            }
        }).toList();
        if (!availableIds.containsAll(document.imageIds())) throw NoticeException.invalidContent();
        // Register compensation before storage calls. A database commit failure also removes new objects.
        for (int index = 0; index < additions.size(); index++) {
            var image = additions.get(index);
            var upload = uploads.get(index);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) return;
                    try {
                        storage.delete(image.getStorageKey());
                    } catch (NoticeException exception) {
                        log.warn("공지 사진 롤백 정리 실패 (imageId={})", image.getEntityId());
                    }
                }
            });
            storage.upload(image.getStorageKey(), image.getContentType(), upload.content());
        }
        existing.stream().filter(image -> !document.imageIds().contains(image.getEntityId()))
            .forEach(NoticeImage::softDelete);
        images.saveAll(additions);
    }

    public void removeAll(UUID noticeId) {
        images.findAllByNoticeIdAndDeletedAtIsNull(noticeId).forEach(NoticeImage::softDelete);
    }

    public InquiryAttachmentFile download(UUID noticeId, UUID imageId) {
        var image = images.findByEntityIdAndNoticeIdAndDeletedAtIsNull(imageId, noticeId)
            .orElseThrow(NoticeException::imageNotFound);
        return new InquiryAttachmentFile(imageId.toString(), image.getContentType(), storage.download(image.getStorageKey()));
    }
}
