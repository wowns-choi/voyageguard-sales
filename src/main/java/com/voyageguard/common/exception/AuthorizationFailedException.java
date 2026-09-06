package com.voyageguard.common.exception;

/** 인가 실패(로그인은 했지만 본인 소유가 아닌 리소스에 접근) - GlobalExceptionHandler가 403으로 매핑 */
public class AuthorizationFailedException extends RuntimeException {
    public AuthorizationFailedException(String message) {
        super(message);
    }
}
