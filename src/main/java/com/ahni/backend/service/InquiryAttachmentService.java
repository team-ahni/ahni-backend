package com.ahni.backend.service;

import com.ahni.backend.client.SupabaseStorageClient;
import com.ahni.backend.dto.InquiryAttachmentFile;
import com.ahni.backend.dto.InquiryAttachmentResponse;
import com.ahni.backend.entity.Inquiry;
import com.ahni.backend.entity.InquiryAttachment;
import com.ahni.backend.entity.Student;
import com.ahni.backend.exception.InquiryAttachmentLimitExceededException;
import com.ahni.backend.exception.InquiryAttachmentNotFoundException;
import com.ahni.backend.exception.InquiryAttachmentStorageUnavailableException;
import com.ahni.backend.exception.InquiryAttachmentTooLargeException;
import com.ahni.backend.exception.InquiryNotFoundException;
import com.ahni.backend.exception.InquiryUpdateConflictException;
import com.ahni.backend.exception.InvalidInquiryAttachmentException;
import com.ahni.backend.exception.StudentNotFoundException;
import com.ahni.backend.exception.UnsupportedInquiryAttachmentTypeException;
import com.ahni.backend.repository.InquiryAttachmentRepository;
import com.ahni.backend.repository.InquiryRepository;
import com.ahni.backend.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InquiryAttachmentService {
    static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    static final int MAX_ATTACHMENTS_PER_INQUIRY = 3;
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final Map<String, String> CONTENT_TYPES_BY_EXTENSION = Map.of(
        "jpg", "image/jpeg",
        "jpeg", "image/jpeg",
        "png", "image/png",
        "pdf", "application/pdf"
    );
    private static final Map<String, byte[]> SIGNATURES = Map.of(
        "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
        "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A},
        "application/pdf", "%PDF-".getBytes(StandardCharsets.US_ASCII)
    );

    private final StudentRepository students;
    private final InquiryRepository inquiries;
    private final InquiryAttachmentRepository attachments;
    private final AdminAccessService access;
    private final SupabaseStorageClient storage;

    public InquiryAttachmentService(
        StudentRepository students,
        InquiryRepository inquiries,
        InquiryAttachmentRepository attachments,
        AdminAccessService access,
        SupabaseStorageClient storage
    ) {
        this.students = students;
        this.inquiries = inquiries;
        this.attachments = attachments;
        this.access = access;
        this.storage = storage;
    }

    // ponytail: 업로드 동안 DB 커넥션을 잡고 있다. 첨부 트래픽이 늘면 저장소 호출을 트랜잭션 밖으로 뺀다.
    @Transactional
    public InquiryAttachmentResponse upload(
        UUID authUserId,
        UUID inquiryEntityId,
        String originalFilename,
        String declaredContentType,
        byte[] content
    ) {
        Inquiry inquiry = findOwnedInquiry(authUserId, inquiryEntityId);
        ensureEditable(inquiry);
        String filename = sanitizeFilename(originalFilename);
        String contentType = validateContent(filename, declaredContentType, content);
        if (attachments.countByInquiry(inquiry) >= MAX_ATTACHMENTS_PER_INQUIRY) {
            throw new InquiryAttachmentLimitExceededException();
        }

        InquiryAttachment attachment = new InquiryAttachment(inquiry, filename, contentType, content.length);
        storage.upload(attachment.getStorageKey(), contentType, content);
        try {
            return toResponse(attachments.saveAndFlush(attachment));
        } catch (RuntimeException exception) {
            deleteStoredObjectQuietly(attachment.getStorageKey());
            throw exception;
        }
    }

    public List<InquiryAttachmentResponse> getMine(UUID authUserId, UUID inquiryEntityId) {
        return list(findOwnedInquiry(authUserId, inquiryEntityId));
    }

    public InquiryAttachmentFile downloadMine(
        UUID authUserId,
        UUID inquiryEntityId,
        UUID attachmentEntityId
    ) {
        return download(findAttachment(findOwnedInquiry(authUserId, inquiryEntityId), attachmentEntityId));
    }

    @Transactional
    public void delete(UUID authUserId, UUID inquiryEntityId, UUID attachmentEntityId) {
        Inquiry inquiry = findOwnedInquiry(authUserId, inquiryEntityId);
        ensureEditable(inquiry);
        InquiryAttachment attachment = findAttachment(inquiry, attachmentEntityId);
        attachments.delete(attachment);
        attachments.flush();
        // 저장소 삭제가 실패하면 예외로 행 삭제도 롤백된다.
        storage.delete(attachment.getStorageKey());
    }

    public List<InquiryAttachmentResponse> getForAdmin(UUID authUserId, UUID inquiryEntityId) {
        access.getAdmin(authUserId);
        return list(findInquiry(inquiryEntityId));
    }

    public InquiryAttachmentFile downloadForAdmin(
        UUID authUserId,
        UUID inquiryEntityId,
        UUID attachmentEntityId
    ) {
        access.getAdmin(authUserId);
        return download(findAttachment(findInquiry(inquiryEntityId), attachmentEntityId));
    }

    static String sanitizeFilename(String originalFilename) {
        if (originalFilename == null) {
            throw new InvalidInquiryAttachmentException("파일명이 필요합니다.");
        }
        String name = originalFilename.substring(
            Math.max(originalFilename.lastIndexOf('/'), originalFilename.lastIndexOf('\\')) + 1
        );
        name = name.replaceAll("[\\p{Cntrl}\\p{Cf}]", "").strip();
        if (name.isEmpty() || name.length() > MAX_FILENAME_LENGTH) {
            throw new InvalidInquiryAttachmentException("파일명은 1자 이상 255자 이하여야 합니다.");
        }
        return name;
    }

    static String validateContent(String filename, String declaredContentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidInquiryAttachmentException("빈 파일은 첨부할 수 없습니다.");
        }
        if (content.length > MAX_SIZE_BYTES) {
            throw new InquiryAttachmentTooLargeException();
        }
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        String contentType = CONTENT_TYPES_BY_EXTENSION.get(extension);
        if (contentType == null
            || declaredContentType == null
            || !contentType.equalsIgnoreCase(declaredContentType.strip())
            || !startsWith(content, SIGNATURES.get(contentType))) {
            throw new UnsupportedInquiryAttachmentTypeException();
        }
        return contentType;
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        return content.length >= signature.length
            && Arrays.equals(content, 0, signature.length, signature, 0, signature.length);
    }

    private List<InquiryAttachmentResponse> list(Inquiry inquiry) {
        return attachments.findAllByInquiryOrderByCreatedAtAsc(inquiry).stream()
            .map(InquiryAttachmentService::toResponse)
            .toList();
    }

    private InquiryAttachmentFile download(InquiryAttachment attachment) {
        return new InquiryAttachmentFile(
            attachment.getFilename(),
            attachment.getContentType(),
            storage.download(attachment.getStorageKey())
        );
    }

    private void deleteStoredObjectQuietly(String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (InquiryAttachmentStorageUnavailableException ignored) {
            // 행이 없으므로 남은 객체는 접근할 수 없다. 원래 예외를 우선한다.
        }
    }

    private static void ensureEditable(Inquiry inquiry) {
        try {
            inquiry.ensureEditable();
        } catch (IllegalStateException exception) {
            throw new InquiryUpdateConflictException(exception.getMessage());
        }
    }

    private InquiryAttachment findAttachment(Inquiry inquiry, UUID attachmentEntityId) {
        return attachments.findByEntityIdAndInquiry(attachmentEntityId, inquiry)
            .orElseThrow(InquiryAttachmentNotFoundException::new);
    }

    private Inquiry findOwnedInquiry(UUID authUserId, UUID inquiryEntityId) {
        Student student = students.findByAuthUserId(authUserId)
            .orElseThrow(StudentNotFoundException::new);
        return inquiries.findByEntityIdAndStudentAndDeletedAtIsNull(inquiryEntityId, student)
            .orElseThrow(InquiryNotFoundException::new);
    }

    private Inquiry findInquiry(UUID inquiryEntityId) {
        return inquiries.findByEntityId(inquiryEntityId)
            .orElseThrow(InquiryNotFoundException::new);
    }

    private static InquiryAttachmentResponse toResponse(InquiryAttachment attachment) {
        return new InquiryAttachmentResponse(
            attachment.getEntityId(),
            attachment.getFilename(),
            attachment.getContentType(),
            attachment.getSizeBytes(),
            attachment.getCreatedAt()
        );
    }
}
