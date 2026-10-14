package com.ahni.backend.exception;

public class ScholarshipCriteriaAlreadyExistsException extends RuntimeException {
    public ScholarshipCriteriaAlreadyExistsException() {
        super("같은 학과, 입학연도, 이름의 장학 기준이 이미 있습니다.");
    }
}
