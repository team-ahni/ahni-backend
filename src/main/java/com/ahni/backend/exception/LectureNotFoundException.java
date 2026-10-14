package com.ahni.backend.exception;

public class LectureNotFoundException extends RuntimeException {
    public LectureNotFoundException() { super("강의 또는 교수를 찾을 수 없습니다."); }
}
