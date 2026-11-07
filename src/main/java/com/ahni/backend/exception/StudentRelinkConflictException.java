package com.ahni.backend.exception;

public class StudentRelinkConflictException extends RuntimeException {
    public StudentRelinkConflictException(String message) {
        super(message);
    }
}
