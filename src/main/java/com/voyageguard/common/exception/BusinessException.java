package com.voyageguard.common.exception;

/**
 * 각 BC의 도메인 규칙 위반 예외들의 공통 베이스.
 * - GlobalExceptionHandler는 구체 타입이 아닌 이 타입만 잡습니다.
 * - common이 특정 BC를 알면 의존 방향이 거꾸로 흘러 MSA 추출 시 발이 묶이기 때문에, 이렇게 구현한 것입니다.
 * - 새 예외는 이 클래스를 상속해 getErrorCode()만 구현하면 됩니다.
 */
public abstract class BusinessException extends RuntimeException{
    public BusinessException(String message) {
        super(message);
    }
    public abstract String getErrorCode();
}
