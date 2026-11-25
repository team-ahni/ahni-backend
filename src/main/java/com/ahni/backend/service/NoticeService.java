package com.ahni.backend.service;

import com.ahni.backend.dto.NoticeResponse;
import com.ahni.backend.dto.InquiryAttachmentFile;
import com.ahni.backend.domain.NoticeDocument;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ahni.backend.entity.Notice;
import com.ahni.backend.exception.NoticeException;
import com.ahni.backend.exception.StudentNotFoundException;
import com.ahni.backend.repository.NoticeRepository;
import com.ahni.backend.repository.StudentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NoticeService {
    private final NoticeRepository notices;
    private final AdminAccessService admins;
    private final StudentRepository students;
    private final NoticeImageService images;

    public NoticeService(NoticeRepository notices,
        AdminAccessService admins, StudentRepository students, NoticeImageService images) {
        this.notices = notices;
        this.admins = admins;
        this.students = students;
        this.images = images;
    }

    public List<NoticeResponse> listAdmin(UUID userId) {
        admins.getAdmin(userId);
        return notices.findAllByDeletedAtIsNullOrderByUpdatedAtDescEntityIdAsc().stream().map(NoticeService::response).toList();
    }

    public List<NoticeResponse> listPublished(UUID userId) {
        requireStudent(userId);
        return notices.findAllByDeletedAtIsNullAndPublishedAtIsNotNullOrderByPublishedAtDescEntityIdAsc()
            .stream().map(NoticeService::response).toList();
    }

    @Transactional
    public NoticeResponse create(UUID userId, String title, String body) {
        return create(userId, title, body, null, List.of());
    }

    @Transactional
    public NoticeResponse create(UUID userId, String title, String body, JsonNode bodyJson, List<NoticeImageService.Upload> uploads) {
        var admin = admins.getAdmin(userId);
        NoticeDocument document = document(body, bodyJson);
        if (document == null && !uploads.isEmpty()) throw NoticeException.invalidContent();
        Notice notice = new Notice(title, document == null ? body : document.text(), admin.getName());
        if (document != null) notice.setBodyJson(document.json());
        notices.saveAndFlush(notice);
        if (document != null) images.save(notice, document, uploads);
        return response(notice);
    }

    @Transactional
    public NoticeResponse update(UUID userId, UUID noticeId, String title, String body, boolean published, long version) {
        return update(userId, noticeId, title, body, published, version, null, List.of());
    }

    @Transactional
    public NoticeResponse update(UUID userId, UUID noticeId, String title, String body, boolean published,
        long version, JsonNode bodyJson, List<NoticeImageService.Upload> uploads) {
        admins.getAdmin(userId);
        Notice notice = findNotice(noticeId);
        if (notice.getVersion() != version) throw NoticeException.conflict();
        NoticeDocument document = document(body, bodyJson);
        if (document == null && !uploads.isEmpty()) throw NoticeException.invalidContent();
        // Legacy visibility-only writes preserve rich text and image references.
        boolean preserveRichBody = document == null && notice.getBody().equals(body.strip());
        boolean clearImages = document == null && !preserveRichBody && notice.getBodyJson() != null;
        notice.update(title, document == null ? body : document.text(), published);
        if (document != null) notice.setBodyJson(document.json());
        if (clearImages) notice.setBodyJson(null);
        flushNoticeChange();
        if (document != null) images.save(notice, document, uploads);
        if (clearImages) images.removeAll(noticeId);
        return response(notice);
    }

    public InquiryAttachmentFile downloadAdminImage(UUID userId, UUID noticeId, UUID imageId) {
        admins.getAdmin(userId);
        findNotice(noticeId);
        return images.download(noticeId, imageId);
    }

    public InquiryAttachmentFile downloadPublishedImage(UUID userId, UUID noticeId, UUID imageId) {
        requireStudent(userId);
        if (findNotice(noticeId).getPublishedAt() == null) throw NoticeException.notFound();
        return images.download(noticeId, imageId);
    }

    private static NoticeDocument document(String body, JsonNode bodyJson) {
        if (bodyJson != null && !bodyJson.isNull()) return NoticeDocument.parse(bodyJson);
        if (body == null || body.isBlank()) throw NoticeException.invalidContent();
        return null;
    }

    @Transactional
    public void delete(UUID userId, UUID noticeId, long version) {
        admins.getAdmin(userId);
        Notice notice = findNotice(noticeId);
        if (notice.getVersion() != version) {
            throw NoticeException.conflict();
        }
        notice.softDelete();
        flushNoticeChange();
    }

    private void flushNoticeChange() {
        try {
            notices.flush();
        } catch (OptimisticLockingFailureException exception) {
            throw NoticeException.conflict();
        }
    }

    private void requireStudent(UUID userId) {
        students.findByAuthUserId(userId).filter(student -> student.getDeletedAt() == null)
            .orElseThrow(StudentNotFoundException::new);
    }

    private Notice findNotice(UUID noticeId) {
        return notices.findByEntityIdAndDeletedAtIsNull(noticeId).orElseThrow(NoticeException::notFound);
    }

    private static NoticeResponse response(Notice notice) {
        return new NoticeResponse(notice.getEntityId(), notice.getTitle(), notice.getBody(),
            notice.getUpdatedAt(), notice.getPublishedAt(), notice.getVersion(),
            notice.getCreatedAt(), notice.getCreatedByName(),
            notice.getBodyJson() == null ? null : new ObjectMapper().readTree(notice.getBodyJson()));
    }
}
