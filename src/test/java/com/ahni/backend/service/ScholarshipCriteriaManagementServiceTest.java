package com.ahni.backend.service;

import com.ahni.backend.dto.ScholarshipCriteriaCreateRequest;
import com.ahni.backend.dto.ScholarshipCriteriaResponse;
import com.ahni.backend.dto.ScholarshipCriteriaUpdateRequest;
import com.ahni.backend.entity.Department;
import com.ahni.backend.entity.ScholarshipCriteria;
import com.ahni.backend.exception.AdminAccessDeniedException;
import com.ahni.backend.exception.ScholarshipCriteriaAlreadyExistsException;
import com.ahni.backend.exception.ScholarshipCriteriaNotFoundException;
import com.ahni.backend.repository.AdminRepository;
import com.ahni.backend.repository.DepartmentRepository;
import com.ahni.backend.repository.ScholarshipCriteriaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScholarshipCriteriaManagementServiceTest {
    private final UUID adminId = UUID.randomUUID();
    private final Department department = new Department("소프트웨어융합공학과");

    @Mock
    private AdminRepository adminRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private ScholarshipCriteriaRepository scholarshipCriteriaRepository;
    @InjectMocks
    private ScholarshipCriteriaManagementService service;

    @Test
    void 관리자가_장학_기준을_등록한다() {
        when(adminRepository.existsByAuthUserIdAndDeletedAtIsNull(adminId)).thenReturn(true);
        when(departmentRepository.findByEntityIdAndDeletedAtIsNull(department.getEntityId()))
            .thenReturn(Optional.of(department));
        when(scholarshipCriteriaRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        ScholarshipCriteriaResponse response = service.create(adminId, createRequest(" 성적우수장학금 "));

        assertThat(response.name()).isEqualTo("성적우수장학금");
        assertThat(response.department().entityId()).isEqualTo(department.getEntityId());
        assertThat(response.minGpa()).isEqualByComparingTo("3.50");
    }

    @Test
    void 같은_학과_입학연도에_같은_이름의_기준은_등록할_수_없다() {
        when(adminRepository.existsByAuthUserIdAndDeletedAtIsNull(adminId)).thenReturn(true);
        when(departmentRepository.findByEntityIdAndDeletedAtIsNull(department.getEntityId()))
            .thenReturn(Optional.of(department));
        when(scholarshipCriteriaRepository.existsByDepartmentAndAdmissionYearAndName(
            department, 2024, "성적우수장학금"
        )).thenReturn(true);

        assertThatThrownBy(() -> service.create(adminId, createRequest("성적우수장학금")))
            .isInstanceOf(ScholarshipCriteriaAlreadyExistsException.class);
        verify(scholarshipCriteriaRepository, never()).save(any());
    }

    @Test
    void 활성_관리자가_아니면_기준을_조회할_수_없다() {
        when(adminRepository.existsByAuthUserIdAndDeletedAtIsNull(adminId)).thenReturn(false);

        assertThatThrownBy(() -> service.findAll(adminId, null, null))
            .isInstanceOf(AdminAccessDeniedException.class);
    }

    @Test
    void 이름을_바꿀_때_다른_기준과_겹치면_수정할_수_없다() {
        ScholarshipCriteria criteria = criteria();
        when(adminRepository.existsByAuthUserIdAndDeletedAtIsNull(adminId)).thenReturn(true);
        when(scholarshipCriteriaRepository.findByEntityId(criteria.getEntityId()))
            .thenReturn(Optional.of(criteria));
        when(scholarshipCriteriaRepository.existsByDepartmentAndAdmissionYearAndName(
            department, 2024, "국가근로장학금"
        )).thenReturn(true);

        assertThatThrownBy(() -> service.update(adminId, criteria.getEntityId(), updateRequest("국가근로장학금")))
            .isInstanceOf(ScholarshipCriteriaAlreadyExistsException.class);
        assertThat(criteria.getName()).isEqualTo("성적우수장학금");
    }

    @Test
    void 이름이_같으면_기준값만_수정한다() {
        ScholarshipCriteria criteria = criteria();
        when(adminRepository.existsByAuthUserIdAndDeletedAtIsNull(adminId)).thenReturn(true);
        when(scholarshipCriteriaRepository.findByEntityId(criteria.getEntityId()))
            .thenReturn(Optional.of(criteria));

        ScholarshipCriteriaResponse response = service.update(
            adminId, criteria.getEntityId(), updateRequest("성적우수장학금")
        );

        assertThat(response.minGpa()).isEqualByComparingTo("3.00");
        assertThat(response.maxWarnings()).isEqualTo(1);
    }

    @Test
    void 장학_기준을_삭제한다() {
        ScholarshipCriteria criteria = criteria();
        when(adminRepository.existsByAuthUserIdAndDeletedAtIsNull(adminId)).thenReturn(true);
        when(scholarshipCriteriaRepository.findByEntityId(criteria.getEntityId()))
            .thenReturn(Optional.of(criteria));

        service.delete(adminId, criteria.getEntityId());

        verify(scholarshipCriteriaRepository).delete(criteria);
    }

    @Test
    void 없는_기준은_삭제할_수_없다() {
        UUID missing = UUID.randomUUID();
        when(adminRepository.existsByAuthUserIdAndDeletedAtIsNull(adminId)).thenReturn(true);
        when(scholarshipCriteriaRepository.findByEntityId(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(adminId, missing))
            .isInstanceOf(ScholarshipCriteriaNotFoundException.class);
    }

    private ScholarshipCriteria criteria() {
        return new ScholarshipCriteria(
            department, 2024, "성적우수장학금", new BigDecimal("3.50"), new BigDecimal("12.0"), 0
        );
    }

    private ScholarshipCriteriaCreateRequest createRequest(String name) {
        return new ScholarshipCriteriaCreateRequest(
            department.getEntityId(), 2024, name,
            new BigDecimal("3.50"), new BigDecimal("12.0"), 0
        );
    }

    private ScholarshipCriteriaUpdateRequest updateRequest(String name) {
        return new ScholarshipCriteriaUpdateRequest(
            name, new BigDecimal("3.00"), new BigDecimal("12.0"), 1
        );
    }
}
