package com.ahni.backend.service;

import com.ahni.backend.client.SupabaseStorageClient;
import com.ahni.backend.domain.EnrollmentStatus;
import com.ahni.backend.dto.InquiryAttachmentFile;
import com.ahni.backend.dto.InquiryAttachmentResponse;
import com.ahni.backend.entity.Admin;
import com.ahni.backend.entity.Inquiry;
import com.ahni.backend.entity.InquiryAttachment;
import com.ahni.backend.entity.Student;
import com.ahni.backend.exception.AdminAccessDeniedException;
import com.ahni.backend.exception.InquiryAttachmentLimitExceededException;
import com.ahni.backend.exception.InquiryAttachmentNotFoundException;
import com.ahni.backend.exception.InquiryAttachmentStorageUnavailableException;
import com.ahni.backend.exception.InquiryAttachmentTooLargeException;
import com.ahni.backend.exception.InquiryNotFoundException;
import com.ahni.backend.exception.InquiryUpdateConflictException;
import com.ahni.backend.exception.InvalidInquiryAttachmentException;
import com.ahni.backend.exception.UnsupportedInquiryAttachmentTypeException;
import com.ahni.backend.repository.InquiryAttachmentRepository;
import com.ahni.backend.repository.InquiryRepository;
import com.ahni.backend.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InquiryAttachmentServiceTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    private static final byte[] PDF = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);

    @Mock
    private StudentRepository students;

    @Mock
    private InquiryRepository inquiries;

    @Mock
    private InquiryAttachmentRepository attachments;

    @Mock
    private AdminAccessService access;

    @Mock
    private SupabaseStorageClient storage;

    private InquiryAttachmentService service;
    private UUID authUserId;
    private Student student;
    private Inquiry inquiry;

    @BeforeEach
    void setUp() {
        service = new InquiryAttachmentService(students, inquiries, attachments, access, storage);
        authUserId = UUID.randomUUID();
        student = new Student(authUserId, "student@inha.edu", 2024, EnrollmentStatus.ENROLLED, "인하");
        inquiry = new Inquiry(student, "성적 문의", "내용");
    }

    @Test
    void 본인_문의에_PNG를_업로드하면_원본_파일명이_없는_키로_저장한다() {
        givenOwnedInquiry();
        when(attachments.saveAndFlush(any(InquiryAttachment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        InquiryAttachmentResponse response = service.upload(
            authUserId, inquiry.getEntityId(), "../screens/grade.PNG", "image/png", PNG
        );

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(key.capture(), eq("image/png"), eq(PNG));
        assertThat(key.getValue())
            .isEqualTo("inquiries/" + inquiry.getEntityId() + "/" + response.entityId())
            .doesNotContain("grade");
        assertThat(response.filename()).isEqualTo("grade.PNG");
        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(response.sizeBytes()).isEqualTo(PNG.length);
    }

    @ParameterizedTest
    @CsvSource({
        "photo.jpeg, image/jpeg, JPEG",
        "photo.jpg, IMAGE/JPEG, JPEG",
        "report.pdf, application/pdf, PDF"
    })
    void 허용된_형식은_서버가_정한_형식으로_검증한다(String filename, String declared, String kind) {
        byte[] content = kind.equals("PDF") ? PDF : JPEG;

        assertThat(InquiryAttachmentService.validateContent(filename, declared, content))
            .isEqualTo(kind.equals("PDF") ? "application/pdf" : "image/jpeg");
    }

    @ParameterizedTest
    @CsvSource({
        "fake.png, image/png, PDF",
        "image.svg, image/svg+xml, PNG",
        "page.html, text/html, PNG",
        "noextension, image/png, PNG",
        "grade.png, image/jpeg, PNG",
        "grade.png, application/octet-stream, PNG"
    })
    void 확장자_선언_형식_내용이_하나라도_다르면_거절한다(String filename, String declared, String kind) {
        byte[] content = kind.equals("PDF") ? PDF : PNG;

        assertThatThrownBy(() -> InquiryAttachmentService.validateContent(filename, declared, content))
            .isInstanceOf(UnsupportedInquiryAttachmentTypeException.class);
    }

    @Test
    void 선언_형식이_없으면_거절한다() {
        assertThatThrownBy(() -> InquiryAttachmentService.validateContent("grade.png", null, PNG))
            .isInstanceOf(UnsupportedInquiryAttachmentTypeException.class);
    }

    @Test
    void 빈_파일과_5MB_초과_파일을_거절한다() {
        byte[] tooLarge = new byte[(int) InquiryAttachmentService.MAX_SIZE_BYTES + 1];
        System.arraycopy(PNG, 0, tooLarge, 0, PNG.length);

        assertThatThrownBy(() -> InquiryAttachmentService.validateContent("a.png", "image/png", new byte[0]))
            .isInstanceOf(InvalidInquiryAttachmentException.class);
        assertThatThrownBy(() -> InquiryAttachmentService.validateContent("a.png", "image/png", tooLarge))
            .isInstanceOf(InquiryAttachmentTooLargeException.class);
    }

    @Test
    void 파일명에서_경로와_제어문자와_방향_제어문자를_제거한다() {
        assertThat(InquiryAttachmentService.sanitizeFilename("C:\\Users\\me\\성적\u0000표.png"))
            .isEqualTo("성적표.png");
        assertThat(InquiryAttachmentService.sanitizeFilename("evil\u202Egnp.pdf"))
            .isEqualTo("evilgnp.pdf");
        assertThatThrownBy(() -> InquiryAttachmentService.sanitizeFilename("dir/"))
            .isInstanceOf(InvalidInquiryAttachmentException.class);
        assertThatThrownBy(() -> InquiryAttachmentService.sanitizeFilename(null))
            .isInstanceOf(InvalidInquiryAttachmentException.class);
        assertThatThrownBy(() -> InquiryAttachmentService.sanitizeFilename("a".repeat(252) + ".png"))
            .isInstanceOf(InvalidInquiryAttachmentException.class);
    }

    @Test
    void 다른_학생의_문의에는_업로드하지_않고_문의없음으로_응답한다() {
        when(students.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(inquiries.findByEntityIdAndStudentAndDeletedAtIsNull(inquiry.getEntityId(), student))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(authUserId, inquiry.getEntityId(), "a.png", "image/png", PNG))
            .isInstanceOf(InquiryNotFoundException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void 답변된_문의에는_첨부파일을_추가하거나_삭제할_수_없다() {
        inquiry.answer(mock(Admin.class), "답변입니다.");
        givenOwnedInquiry();

        assertThatThrownBy(() -> service.upload(authUserId, inquiry.getEntityId(), "a.png", "image/png", PNG))
            .isInstanceOf(InquiryUpdateConflictException.class);
        assertThatThrownBy(() -> service.delete(authUserId, inquiry.getEntityId(), UUID.randomUUID()))
            .isInstanceOf(InquiryUpdateConflictException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void 첨부파일이_이미_3개면_거절한다() {
        givenOwnedInquiry();
        when(attachments.countByInquiry(inquiry)).thenReturn(3L);

        assertThatThrownBy(() -> service.upload(authUserId, inquiry.getEntityId(), "a.png", "image/png", PNG))
            .isInstanceOf(InquiryAttachmentLimitExceededException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void 행_저장에_실패하면_올린_파일을_지운다() {
        givenOwnedInquiry();
        DataIntegrityViolationException failure = new DataIntegrityViolationException("fail");
        when(attachments.saveAndFlush(any(InquiryAttachment.class))).thenThrow(failure);
        doThrow(new InquiryAttachmentStorageUnavailableException()).when(storage).delete(anyString());

        assertThatThrownBy(() -> service.upload(authUserId, inquiry.getEntityId(), "a.png", "image/png", PNG))
            .isSameAs(failure);

        ArgumentCaptor<String> uploaded = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(uploaded.capture(), eq("image/png"), eq(PNG));
        verify(storage).delete(uploaded.getValue());
    }

    @Test
    void 삭제는_행을_지운_뒤_저장소_객체를_지운다() {
        InquiryAttachment attachment = new InquiryAttachment(inquiry, "a.png", "image/png", PNG.length);
        givenOwnedInquiry();
        when(attachments.findByEntityIdAndInquiry(attachment.getEntityId(), inquiry))
            .thenReturn(Optional.of(attachment));

        service.delete(authUserId, inquiry.getEntityId(), attachment.getEntityId());

        InOrder order = inOrder(attachments, storage);
        order.verify(attachments).delete(attachment);
        order.verify(attachments).flush();
        order.verify(storage).delete(attachment.getStorageKey());
    }

    @Test
    void 다른_문의의_첨부파일_ID로는_내려받을_수_없다() {
        UUID otherAttachment = UUID.randomUUID();
        givenOwnedInquiry();
        when(attachments.findByEntityIdAndInquiry(otherAttachment, inquiry)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.downloadMine(authUserId, inquiry.getEntityId(), otherAttachment))
            .isInstanceOf(InquiryAttachmentNotFoundException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void 본인_첨부파일을_저장된_형식과_파일명으로_내려받는다() {
        InquiryAttachment attachment = new InquiryAttachment(inquiry, "a.png", "image/png", PNG.length);
        givenOwnedInquiry();
        when(attachments.findByEntityIdAndInquiry(attachment.getEntityId(), inquiry))
            .thenReturn(Optional.of(attachment));
        when(storage.download(attachment.getStorageKey())).thenReturn(PNG);

        InquiryAttachmentFile file = service.downloadMine(authUserId, inquiry.getEntityId(), attachment.getEntityId());

        assertThat(file.filename()).isEqualTo("a.png");
        assertThat(file.contentType()).isEqualTo("image/png");
        assertThat(file.content()).isEqualTo(PNG);
    }

    @Test
    void 관리자는_삭제된_문의의_첨부파일도_조회한다() {
        inquiry.delete();
        InquiryAttachment attachment = new InquiryAttachment(inquiry, "a.png", "image/png", PNG.length);
        when(inquiries.findByEntityId(inquiry.getEntityId())).thenReturn(Optional.of(inquiry));
        when(attachments.findAllByInquiryOrderByCreatedAtAsc(inquiry)).thenReturn(List.of(attachment));

        List<InquiryAttachmentResponse> result = service.getForAdmin(authUserId, inquiry.getEntityId());

        verify(access).getAdmin(authUserId);
        assertThat(result).extracting(InquiryAttachmentResponse::entityId).containsExactly(attachment.getEntityId());
    }

    @Test
    void 관리자가_아니면_첨부파일을_내려받을_수_없다() {
        when(access.getAdmin(authUserId)).thenThrow(new AdminAccessDeniedException());

        assertThatThrownBy(() -> service.downloadForAdmin(authUserId, inquiry.getEntityId(), UUID.randomUUID()))
            .isInstanceOf(AdminAccessDeniedException.class);
        verify(inquiries, never()).findByEntityId(any());
        verifyNoInteractions(storage);
    }

    private void givenOwnedInquiry() {
        when(students.findByAuthUserId(authUserId)).thenReturn(Optional.of(student));
        when(inquiries.findByEntityIdAndStudentAndDeletedAtIsNull(inquiry.getEntityId(), student))
            .thenReturn(Optional.of(inquiry));
    }
}
