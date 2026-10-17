package com.ahni.backend.exception;

public class TimetableConflictException extends RuntimeException {
    public TimetableConflictException(String message) { super(message); }
}
