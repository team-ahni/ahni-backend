package com.ahni.backend.repository;

import com.ahni.backend.entity.Inquiry;
import com.ahni.backend.entity.InquiryAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InquiryAttachmentRepository extends JpaRepository<InquiryAttachment, Long> {
    List<InquiryAttachment> findAllByInquiryOrderByCreatedAtAsc(Inquiry inquiry);

    Optional<InquiryAttachment> findByEntityIdAndInquiry(UUID entityId, Inquiry inquiry);

    long countByInquiry(Inquiry inquiry);
}
