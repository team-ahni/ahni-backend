package com.ahni.backend.exception;

public class TimetableNotFoundException extends RuntimeException {
    public TimetableNotFoundException() { super("시간표 항목을 찾을 수 없습니다."); }
}
