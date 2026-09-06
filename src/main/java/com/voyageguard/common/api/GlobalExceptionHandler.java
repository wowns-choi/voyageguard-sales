package com.voyageguard.common.api;

import com.voyageguard.common.exception.AuthenticationFailedException;
import com.voyageguard.common.exception.AuthorizationFailedException;
import com.voyageguard.common.exception.BusinessException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ProblemDetail handleAuthenticationFailed(AuthenticationFailedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(AuthorizationFailedException.class)
    public ProblemDetail handleAuthorizationFailed(AuthorizationFailedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleInsufficientInventory(BusinessException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        pd.setProperty("code", e.getErrorCode());
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handleDataAccess(DataAccessException e) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "일시적인 서버 오류입니다. 잠시 후 다시 시도해주세요."
        );
    }
}
