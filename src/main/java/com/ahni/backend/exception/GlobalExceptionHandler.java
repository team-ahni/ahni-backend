package com.ahni.backend.exception;

import com.ahni.backend.dto.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
@Hidden
public class GlobalExceptionHandler {
    @ExceptionHandler(CourseNotInCurriculumException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleCourseNotInCurriculum(CourseNotInCurriculumException exception) {
        return new ApiErrorResponse("COURSE_NOT_IN_CURRICULUM", exception.getMessage());
    }
    @ExceptionHandler(CurriculumNotAvailableException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleCurriculumNotAvailable(CurriculumNotAvailableException exception) {
        return new ApiErrorResponse("CURRICULUM_NOT_AVAILABLE", exception.getMessage());
    }
    @ExceptionHandler(CurriculumAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleCurriculumAlreadyExists(CurriculumAlreadyExistsException exception) {
        return new ApiErrorResponse("CURRICULUM_ALREADY_EXISTS", exception.getMessage());
    }
    @ExceptionHandler(CurriculumEditConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleCurriculumEditConflict(CurriculumEditConflictException exception) {
        return new ApiErrorResponse("CURRICULUM_EDIT_CONFLICT", exception.getMessage());
    }
    @ExceptionHandler(CourseAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleCourseAlreadyExists(CourseAlreadyExistsException exception) {
        return new ApiErrorResponse("COURSE_ALREADY_EXISTS", exception.getMessage());
    }

    @ExceptionHandler(CourseAssignmentConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleCourseAssignmentConflict(CourseAssignmentConflictException exception) {
        return new ApiErrorResponse("COURSE_ASSIGNMENT_CONFLICT", exception.getMessage());
    }
    @ExceptionHandler(AdminAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiErrorResponse handleAdminAccessDenied(
        AdminAccessDeniedException exception
    ) {
        return new ApiErrorResponse("ADMIN_ACCESS_DENIED", exception.getMessage());
    }

    @ExceptionHandler(StudentAlreadyRegisteredException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleStudentAlreadyRegistered(StudentAlreadyRegisteredException exception) {
        return new ApiErrorResponse("STUDENT_ALREADY_REGISTERED", exception.getMessage());
    }

    @ExceptionHandler(StudentEmailAlreadyRegisteredException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleStudentEmailAlreadyRegistered(StudentEmailAlreadyRegisteredException exception) {
        return new ApiErrorResponse("STUDENT_EMAIL_ALREADY_REGISTERED", exception.getMessage());
    }

    @ExceptionHandler(StudentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleStudentNotFound(StudentNotFoundException exception) {
        return new ApiErrorResponse("STUDENT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(DepartmentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleDepartmentNotFound(DepartmentNotFoundException exception) {
        return new ApiErrorResponse("DEPARTMENT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(InvalidEnrollmentStatusException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidEnrollmentStatus(InvalidEnrollmentStatusException exception) {
        return new ApiErrorResponse("INVALID_ENROLLMENT_STATUS", exception.getMessage());
    }

    @ExceptionHandler(InvalidCourseCategoryException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidCourseCategory(InvalidCourseCategoryException exception) {
        return new ApiErrorResponse("INVALID_COURSE_CATEGORY", exception.getMessage());
    }

    @ExceptionHandler(InvalidGradeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidGrade(InvalidGradeException exception) {
        return new ApiErrorResponse("INVALID_GRADE", exception.getMessage());
    }

    @ExceptionHandler(CourseNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleCourseNotFound(CourseNotFoundException exception) {
        return new ApiErrorResponse("COURSE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(GradeAlreadyRegisteredException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleGradeAlreadyRegistered(
        GradeAlreadyRegisteredException exception
    ) {
        return new ApiErrorResponse("GRADE_ALREADY_REGISTERED", exception.getMessage());
    }

    @ExceptionHandler(GradeNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleGradeNotFound(GradeNotFoundException exception) {
        return new ApiErrorResponse("GRADE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(GradeReplacementConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleGradeReplacementConflict(
        GradeReplacementConflictException exception
    ) {
        return new ApiErrorResponse("GRADE_REPLACEMENT_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(InquiryNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleInquiryNotFound(InquiryNotFoundException exception) {
        return new ApiErrorResponse("INQUIRY_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(InvalidInquiryException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidInquiry(InvalidInquiryException exception) {
        return new ApiErrorResponse("INVALID_INQUIRY", exception.getMessage());
    }

    @ExceptionHandler(InquiryUpdateConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleInquiryUpdateConflict(InquiryUpdateConflictException exception) {
        return new ApiErrorResponse("INQUIRY_UPDATE_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(InvalidInquiryAttachmentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidInquiryAttachment(InvalidInquiryAttachmentException exception) {
        return new ApiErrorResponse("INVALID_INQUIRY_ATTACHMENT", exception.getMessage());
    }

    @ExceptionHandler({InquiryAttachmentTooLargeException.class, MaxUploadSizeExceededException.class})
    @ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
    public ApiErrorResponse handleInquiryAttachmentTooLarge() {
        return new ApiErrorResponse(
            "INQUIRY_ATTACHMENT_TOO_LARGE",
            new InquiryAttachmentTooLargeException().getMessage()
        );
    }

    @ExceptionHandler(UnsupportedInquiryAttachmentTypeException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ApiErrorResponse handleUnsupportedInquiryAttachmentType(
        UnsupportedInquiryAttachmentTypeException exception
    ) {
        return new ApiErrorResponse("UNSUPPORTED_INQUIRY_ATTACHMENT_TYPE", exception.getMessage());
    }

    @ExceptionHandler(InquiryAttachmentLimitExceededException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleInquiryAttachmentLimitExceeded(
        InquiryAttachmentLimitExceededException exception
    ) {
        return new ApiErrorResponse("INQUIRY_ATTACHMENT_LIMIT_EXCEEDED", exception.getMessage());
    }

    @ExceptionHandler(InquiryAttachmentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleInquiryAttachmentNotFound(InquiryAttachmentNotFoundException exception) {
        return new ApiErrorResponse("INQUIRY_ATTACHMENT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(InquiryAttachmentStorageUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiErrorResponse handleInquiryAttachmentStorageUnavailable(
        InquiryAttachmentStorageUnavailableException exception
    ) {
        return new ApiErrorResponse("INQUIRY_ATTACHMENT_STORAGE_UNAVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(GraduationRequirementNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleGraduationRequirementNotFound(
        GraduationRequirementNotFoundException exception
    ) {
        return new ApiErrorResponse(
            "GRADUATION_REQUIREMENT_NOT_FOUND",
            exception.getMessage()
        );
    }

    @ExceptionHandler(ScholarshipCriteriaNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleScholarshipCriteriaNotFound(
        ScholarshipCriteriaNotFoundException exception
    ) {
        return new ApiErrorResponse("SCHOLARSHIP_CRITERIA_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ScholarshipCriteriaAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleScholarshipCriteriaAlreadyExists(
        ScholarshipCriteriaAlreadyExistsException exception
    ) {
        return new ApiErrorResponse("SCHOLARSHIP_CRITERIA_ALREADY_EXISTS", exception.getMessage());
    }

    @ExceptionHandler(GraduationRequirementAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleGraduationRequirementAlreadyExists(
        GraduationRequirementAlreadyExistsException exception
    ) {
        return new ApiErrorResponse(
            "GRADUATION_REQUIREMENT_ALREADY_EXISTS",
            exception.getMessage()
        );
    }

    @ExceptionHandler(InvalidRequiredCourseAssignmentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidRequiredCourseAssignment(
        InvalidRequiredCourseAssignmentException exception
    ) {
        return new ApiErrorResponse(
            "INVALID_REQUIRED_COURSE_ASSIGNMENT",
            exception.getMessage()
        );
    }

    @ExceptionHandler(DuplicateMajorDepartmentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleDuplicateMajorDepartment(
        DuplicateMajorDepartmentException exception
    ) {
        return new ApiErrorResponse("DUPLICATE_MAJOR_DEPARTMENT", exception.getMessage());
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class,
        MissingServletRequestPartException.class,
        MultipartException.class,
        IllegalArgumentException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidRequest() {
        return new ApiErrorResponse("INVALID_REQUEST", "요청값이 올바르지 않습니다.");
    }
}
