package com.voyageguard.common.exception;

/** 로그인 실패(이메일 미존재, 비밀번호 불일치 등) - GlobalExceptionHandler가 401로 매핑 */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String message) {
        super(message);
    }
}
