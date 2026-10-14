package com.ahni.backend.exception;

public class ScholarshipCriteriaNotFoundException extends RuntimeException {
    public ScholarshipCriteriaNotFoundException() {
        super("장학 기준을 찾을 수 없습니다.");
    }
}
