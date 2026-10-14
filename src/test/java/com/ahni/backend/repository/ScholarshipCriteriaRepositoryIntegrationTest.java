package com.ahni.backend.repository;

import com.ahni.backend.entity.Department;
import com.ahni.backend.entity.ScholarshipCriteria;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Tag("integration")
class ScholarshipCriteriaRepositoryIntegrationTest {
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
    private ScholarshipCriteriaRepository scholarshipCriteriaRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void clearCriteria() {
        scholarshipCriteriaRepository.deleteAllInBatch();
        departmentRepository.deleteAllInBatch();
    }

    @Test
    void 학과와_입학연도의_기준만_이름순으로_조회한다() {
        Department software = departmentRepository.saveAndFlush(new Department("소프트웨어융합공학과"));
        Department computer = departmentRepository.saveAndFlush(new Department("컴퓨터공학과"));
        scholarshipCriteriaRepository.saveAllAndFlush(List.of(
            criteria(software, 2024, "성적우수장학금"),
            criteria(software, 2024, "국가근로장학금"),
            criteria(software, 2025, "성적우수장학금"),
            criteria(computer, 2024, "성적우수장학금")
        ));
        entityManager.clear();

        List<ScholarshipCriteria> found = scholarshipCriteriaRepository
            .findAllByDepartmentAndAdmissionYearOrderByNameAsc(software, 2024);

        assertThat(found).extracting(ScholarshipCriteria::getName)
            .containsExactly("국가근로장학금", "성적우수장학금");
        assertThat(found.getFirst().getMinGpa()).isEqualByComparingTo("3.50");
    }

    @Test
    void 관리자_목록은_필터가_없으면_전체를_조회한다() {
        Department software = departmentRepository.saveAndFlush(new Department("소프트웨어융합공학과"));
        scholarshipCriteriaRepository.saveAllAndFlush(List.of(
            criteria(software, 2024, "성적우수장학금"),
            criteria(software, 2025, "성적우수장학금")
        ));
        entityManager.clear();

        assertThat(scholarshipCriteriaRepository.findAllForAdmin(null, null)).hasSize(2);
        assertThat(scholarshipCriteriaRepository.findAllForAdmin(software.getEntityId(), 2025))
            .singleElement()
            .extracting(ScholarshipCriteria::getAdmissionYear)
            .isEqualTo(2025);
    }

    @Test
    void 같은_학과_입학연도_이름의_기준은_저장할_수_없다() {
        Department software = departmentRepository.saveAndFlush(new Department("소프트웨어융합공학과"));
        scholarshipCriteriaRepository.saveAndFlush(criteria(software, 2024, "성적우수장학금"));

        assertThatThrownBy(() -> scholarshipCriteriaRepository.saveAndFlush(
            criteria(software, 2024, "성적우수장학금")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private ScholarshipCriteria criteria(Department department, int admissionYear, String name) {
        return new ScholarshipCriteria(
            department, admissionYear, name,
            new BigDecimal("3.50"), new BigDecimal("12.0"), 0
        );
    }
}
