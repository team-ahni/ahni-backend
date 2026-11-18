package com.ahni.backend.service;

import com.ahni.backend.entity.Notice;
import com.ahni.backend.entity.Admin;
import com.ahni.backend.entity.Student;
import com.ahni.backend.exception.AdminAccessDeniedException;
import com.ahni.backend.exception.NoticeException;
import com.ahni.backend.repository.NoticeRepository;
import com.ahni.backend.repository.StudentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {
    private final UUID userId = UUID.randomUUID();

    @Mock private NoticeRepository notices;
    @Mock private AdminAccessService admins;
    @Mock private StudentRepository students;
    @InjectMocks private NoticeService service;

    @Test
    void newNoticeIsPrivate() {
        var admin = mock(Admin.class);
        when(admin.getName()).thenReturn("등록 관리자");
        when(admins.getAdmin(userId)).thenReturn(admin);
        when(notices.saveAndFlush(any(Notice.class))).thenAnswer(call -> call.getArgument(0));

        var created = service.create(userId, " 수강신청 ", " 일정 안내 ");

        assertThat(created.title()).isEqualTo("수강신청");
        assertThat(created.body()).isEqualTo("일정 안내");
        assertThat(created.publishedAt()).isNull();
        assertThat(created.createdAt()).isEqualTo(created.updatedAt());
        assertThat(created.createdByName()).isEqualTo("등록 관리자");
    }

    @Test
    void studentsReadOnlyPublishedNotices() {
        var notice = new Notice("수강신청", "일정 안내", "관리자");
        notice.update("수강신청", "일정 안내", true);
        when(students.findByAuthUserId(userId)).thenReturn(Optional.of(mock(Student.class)));
        when(notices.findAllByDeletedAtIsNullAndPublishedAtIsNotNullOrderByPublishedAtDescEntityIdAsc()).thenReturn(List.of(notice));

        assertThat(service.listPublished(userId)).extracting("entityId").containsExactly(notice.getEntityId());
    }

    @Test
    void nonAdminsCannotChangeNotices() {
        when(admins.getAdmin(userId)).thenThrow(new AdminAccessDeniedException());

        assertThatThrownBy(() -> service.create(userId, "제목", "내용"))
            .isInstanceOf(AdminAccessDeniedException.class);
        verifyNoInteractions(notices);
    }

    @Test
    void staleUpdateLeavesNoticeUnchanged() {
        var notice = new Notice("기존 제목", "기존 내용", "관리자");
        when(notices.findByEntityIdAndDeletedAtIsNull(notice.getEntityId())).thenReturn(Optional.of(notice));

        assertThatThrownBy(() -> service.update(userId, notice.getEntityId(), "새 제목", "새 내용", true, 1))
            .isInstanceOfSatisfying(NoticeException.class,
                error -> assertThat(error.code()).isEqualTo("NOTICE_VERSION_CONFLICT"));
        assertThat(notice.getTitle()).isEqualTo("기존 제목");
        assertThat(notice.getPublishedAt()).isNull();
    }
}
