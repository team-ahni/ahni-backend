package com.ahni.backend.repository;

import com.ahni.backend.domain.EnrollmentStatus;
import com.ahni.backend.entity.Inquiry;
import com.ahni.backend.entity.InquiryAttachment;
import com.ahni.backend.entity.Student;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Tag("integration")
class InquiryAttachmentRepositoryIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:18");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @Autowired
    private InquiryAttachmentRepository attachments;

    @Autowired
    private InquiryRepository inquiries;

    @Autowired
    private StudentRepository students;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 첨부파일은_자신이_속한_문의로만_조회된다() {
        Inquiry inquiry = inquiry();
        Inquiry other = inquiry();
        InquiryAttachment first = attachments.saveAndFlush(new InquiryAttachment(inquiry, "a.png", "image/png", 10));
        attachments.saveAndFlush(new InquiryAttachment(inquiry, "b.pdf", "application/pdf", 20));
        entityManager.clear();

        assertThat(attachments.findAllByInquiryOrderByCreatedAtAsc(inquiry))
            .extracting(InquiryAttachment::getFilename)
            .containsExactly("a.png", "b.pdf");
        assertThat(attachments.countByInquiry(inquiry)).isEqualTo(2);
        assertThat(attachments.findByEntityIdAndInquiry(first.getEntityId(), other)).isEmpty();
        assertThat(attachments.findByEntityIdAndInquiry(first.getEntityId(), inquiry)).isPresent();
    }

    @Test
    void 허용하지_않는_형식은_DB도_거절한다() {
        Inquiry inquiry = inquiry();

        assertThatThrownBy(() -> attachments.saveAndFlush(
            new InquiryAttachment(inquiry, "a.svg", "image/svg+xml", 10)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 크기가_5MB를_넘으면_DB도_거절한다() {
        Inquiry inquiry = inquiry();

        assertThatThrownBy(() -> attachments.saveAndFlush(
            new InquiryAttachment(inquiry, "a.png", "image/png", 5L * 1024 * 1024 + 1)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 문의와_첨부파일_테이블에_RLS가_켜져_있다() {
        assertThat(jdbcTemplate.queryForList(
            "SELECT relname FROM pg_class WHERE relname IN ('inquiry', 'inquiry_attachment') AND relrowsecurity",
            String.class
        )).containsExactlyInAnyOrder("inquiry", "inquiry_attachment");
    }

    private Inquiry inquiry() {
        Student student = students.saveAndFlush(new Student(
            UUID.randomUUID(), UUID.randomUUID() + "@inha.edu", 2024, EnrollmentStatus.ENROLLED, "인하"
        ));
        return inquiries.saveAndFlush(new Inquiry(student, "문의", "내용"));
    }
}
