package com.ahni.backend.service;

import com.ahni.backend.domain.EnrollmentStatus;
import com.ahni.backend.domain.InquiryStatus;
import com.ahni.backend.dto.AdminInquiryResponse;
import com.ahni.backend.dto.InquiryAnswerRequest;
import com.ahni.backend.dto.InquiryAssigneeRequest;
import com.ahni.backend.entity.Admin;
import com.ahni.backend.entity.Inquiry;
import com.ahni.backend.entity.Student;
import com.ahni.backend.exception.AdminAccessDeniedException;
import com.ahni.backend.exception.InquiryNotFoundException;
import com.ahni.backend.exception.InvalidInquiryException;
import com.ahni.backend.repository.InquiryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminInquiryServiceTest {
    @Mock
    private AdminAccessService access;

    @Mock
    private InquiryRepository inquiries;

    private AdminInquiryService service;
    private UUID authUserId;
    private Admin admin;
    private Student student;

    @BeforeEach
    void setUp() {
        service = new AdminInquiryService(access, inquiries);
        authUserId = UUID.randomUUID();
        admin = mock(Admin.class);
        student = new Student(
            UUID.randomUUID(),
            "student@inha.edu",
            2024,
            EnrollmentStatus.ENROLLED,
            "인하"
        );
    }

    @Test
    void 관리자가_삭제된_문의까지_최신순_목록을_조회한다() {
        Inquiry inquiry = new Inquiry(student, "문의", "내용");
        inquiry.delete();
        when(access.getAdmin(authUserId)).thenReturn(admin);
        when(inquiries.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(inquiry));

        List<AdminInquiryResponse> result = service.findAll(authUserId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().student().email()).isEqualTo("student@inha.edu");
        assertThat(result.getFirst().deletedAt()).isNotNull();
        verify(inquiries).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void 관리자가_접수된_문의를_열면_확인_중으로_표시한다() {
        Inquiry inquiry = new Inquiry(student, "문의", "내용");
        when(access.getAdmin(authUserId)).thenReturn(admin);
        when(inquiries.findByEntityId(inquiry.getEntityId()))
            .thenReturn(Optional.of(inquiry));

        AdminInquiryResponse result = service.get(authUserId, inquiry.getEntityId());

        assertThat(result.status()).isEqualTo(InquiryStatus.IN_REVIEW);
    }

    @Test
    void 관리자가_문의에_답변한다() {
        Inquiry inquiry = new Inquiry(student, "문의", "내용");
        when(admin.getName()).thenReturn("관리자");
        when(access.getAdmin(authUserId)).thenReturn(admin);
        when(inquiries.findByEntityId(inquiry.getEntityId()))
            .thenReturn(Optional.of(inquiry));

        AdminInquiryResponse result = service.answer(
            authUserId,
            inquiry.getEntityId(),
            new InquiryAnswerRequest(" 확인했습니다. ")
        );

        assertThat(result.status()).isEqualTo(InquiryStatus.ANSWERED);
        assertThat(result.answer()).isEqualTo("확인했습니다.");
        assertThat(result.answeredByAdminName()).isEqualTo("관리자");
        assertThat(result.answeredAt()).isNotNull();
    }

    @Test
    void 삭제된_문의에는_답변할_수_없다() {
        Inquiry inquiry = new Inquiry(student, "문의", "내용");
        inquiry.delete();
        when(access.getAdmin(authUserId)).thenReturn(admin);
        when(inquiries.findByEntityId(inquiry.getEntityId()))
            .thenReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> service.answer(
            authUserId,
            inquiry.getEntityId(),
            new InquiryAnswerRequest("확인했습니다.")
        ))
            .isInstanceOf(InvalidInquiryException.class)
            .hasMessage("삭제된 문의에는 답변할 수 없습니다.");
    }

    @Test
    void 관리자가_아니면_문의_저장소를_호출하지_않는다() {
        when(access.getAdmin(authUserId)).thenThrow(new AdminAccessDeniedException());

        assertThatThrownBy(() -> service.findAll(authUserId))
            .isInstanceOf(AdminAccessDeniedException.class);
        verifyNoInteractions(inquiries);
    }

    @Test
    void 없는_문의는_404로_처리한다() {
        UUID inquiryEntityId = UUID.randomUUID();
        when(access.getAdmin(authUserId)).thenReturn(admin);
        when(inquiries.findByEntityId(inquiryEntityId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(authUserId, inquiryEntityId))
            .isInstanceOf(InquiryNotFoundException.class);
    }

    @Test
    void 관리자가_문의_담당자를_지정하고_해제한다() {
        Inquiry inquiry = new Inquiry(student, "문의", "내용");
        UUID assigneeEntityId = UUID.randomUUID();
        Admin assignee = mock(Admin.class);
        when(assignee.getEntityId()).thenReturn(assigneeEntityId);
        when(assignee.getName()).thenReturn("담당자");
        when(access.getAdmin(authUserId)).thenReturn(admin);
        when(access.findActiveAdmin(assigneeEntityId)).thenReturn(Optional.of(assignee));
        when(inquiries.findByEntityId(inquiry.getEntityId()))
            .thenReturn(Optional.of(inquiry));

        AdminInquiryResponse assigned = service.assign(
            authUserId,
            inquiry.getEntityId(),
            new InquiryAssigneeRequest(assigneeEntityId)
        );

        assertThat(assigned.assignedAdmin().entityId()).isEqualTo(assigneeEntityId);
        assertThat(assigned.assignedAdmin().name()).isEqualTo("담당자");
        assertThat(assigned.status()).isEqualTo(InquiryStatus.SUBMITTED);

        AdminInquiryResponse unassigned = service.assign(
            authUserId,
            inquiry.getEntityId(),
            new InquiryAssigneeRequest(null)
        );

        assertThat(unassigned.assignedAdmin()).isNull();
    }

    @Test
    void 활성_관리자가_아니면_담당자로_지정할_수_없다() {
        Inquiry inquiry = new Inquiry(student, "문의", "내용");
        UUID assigneeEntityId = UUID.randomUUID();
        when(access.getAdmin(authUserId)).thenReturn(admin);
        when(access.findActiveAdmin(assigneeEntityId)).thenReturn(Optional.empty());
        when(inquiries.findByEntityId(inquiry.getEntityId()))
            .thenReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> service.assign(
            authUserId,
            inquiry.getEntityId(),
            new InquiryAssigneeRequest(assigneeEntityId)
        ))
            .isInstanceOf(InvalidInquiryException.class)
            .hasMessage("담당 관리자를 찾을 수 없습니다.");
        assertThat(inquiry.getAssignedAdmin()).isNull();
    }

    @Test
    void 삭제된_문의에는_담당자를_지정할_수_없다() {
        Inquiry inquiry = new Inquiry(student, "문의", "내용");
        inquiry.delete();
        when(access.getAdmin(authUserId)).thenReturn(admin);
        when(inquiries.findByEntityId(inquiry.getEntityId()))
            .thenReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> service.assign(
            authUserId,
            inquiry.getEntityId(),
            new InquiryAssigneeRequest(null)
        ))
            .isInstanceOf(InvalidInquiryException.class)
            .hasMessage("삭제된 문의에는 담당자를 지정할 수 없습니다.");
    }
}
