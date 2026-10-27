package com.ahni.backend.exception;

public class LectureNotFoundException extends RuntimeException {
    public LectureNotFoundException() { super("강의를 찾을 수 없습니다."); }
}
