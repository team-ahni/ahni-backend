package com.ahni.backend.service;

import com.ahni.backend.dto.NoticeResponse;
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

    public NoticeService(NoticeRepository notices,
        AdminAccessService admins, StudentRepository students) {
        this.notices = notices;
        this.admins = admins;
        this.students = students;
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
        var admin = admins.getAdmin(userId);
        return response(notices.saveAndFlush(new Notice(title, body, admin.getName())));
    }

    @Transactional
    public NoticeResponse update(UUID userId, UUID noticeId, String title, String body, boolean published, long version) {
        admins.getAdmin(userId);
        Notice notice = findNotice(noticeId);
        if (notice.getVersion() != version) {
            throw NoticeException.conflict();
        }
        notice.update(title, body, published);
        flushNoticeChange();
        return response(notice);
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
            notice.getCreatedAt(), notice.getCreatedByName());
    }
}
