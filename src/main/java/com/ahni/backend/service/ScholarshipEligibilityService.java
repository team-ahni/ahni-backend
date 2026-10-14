package com.ahni.backend.service;

import com.ahni.backend.dto.DepartmentResponse;
import com.ahni.backend.dto.GradeSummaryResponse;
import com.ahni.backend.dto.ScholarshipEligibilityResponse;
import com.ahni.backend.entity.Department;
import com.ahni.backend.entity.MajorType;
import com.ahni.backend.entity.Student;
import com.ahni.backend.entity.StudentGrade;
import com.ahni.backend.entity.StudentMajor;
import com.ahni.backend.exception.StudentNotFoundException;
import com.ahni.backend.repository.ScholarshipCriteriaRepository;
import com.ahni.backend.repository.StudentGradeRepository;
import com.ahni.backend.repository.StudentMajorRepository;
import com.ahni.backend.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ScholarshipEligibilityService {
    private final StudentRepository studentRepository;
    private final StudentMajorRepository studentMajorRepository;
    private final StudentGradeRepository gradeRepository;
    private final ScholarshipCriteriaRepository scholarshipCriteriaRepository;

    public ScholarshipEligibilityService(
        StudentRepository studentRepository,
        StudentMajorRepository studentMajorRepository,
        StudentGradeRepository gradeRepository,
        ScholarshipCriteriaRepository scholarshipCriteriaRepository
    ) {
        this.studentRepository = studentRepository;
        this.studentMajorRepository = studentMajorRepository;
        this.gradeRepository = gradeRepository;
        this.scholarshipCriteriaRepository = scholarshipCriteriaRepository;
    }

    public ScholarshipEligibilityResponse getEligibility(UUID authUserId) {
        Student student = studentRepository.findByAuthUserId(authUserId)
            .orElseThrow(StudentNotFoundException::new);
        // 활성 주전공은 프로필 등록 시 항상 하나 생성되므로, 없으면 프로필이 완성되지 않은 상태로 본다.
        Department department = studentMajorRepository.findAllByStudentAndDeletedAtIsNull(student)
            .stream()
            .filter(major -> major.getMajorType() == MajorType.PRIMARY)
            .map(StudentMajor::getDepartment)
            .findFirst()
            .orElseThrow(StudentNotFoundException::new);
        List<StudentGrade> grades = gradeRepository.findAllByStudent(student);
        GradeSummaryResponse summary = GradeSummaryCalculator.calculate(grades);
        int warningCount = ScholarshipEligibilityCalculator.warningCount(grades);

        return new ScholarshipEligibilityResponse(
            student.getAdmissionYear(),
            new DepartmentResponse(department.getEntityId(), department.getName()),
            summary.gpa(),
            summary.completedCredits(),
            warningCount,
            ScholarshipEligibilityCalculator.evaluate(
                scholarshipCriteriaRepository.findAllByDepartmentAndAdmissionYearOrderByNameAsc(
                    department,
                    student.getAdmissionYear()
                ),
                summary,
                warningCount
            )
        );
    }
}
