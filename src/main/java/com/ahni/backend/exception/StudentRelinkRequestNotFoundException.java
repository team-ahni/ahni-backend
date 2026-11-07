package com.ahni.backend.exception;

public class StudentRelinkRequestNotFoundException extends RuntimeException {
    public StudentRelinkRequestNotFoundException() {
        super("계정 재연결 요청을 찾을 수 없습니다.");
    }
}
