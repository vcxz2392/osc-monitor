package com.osc.monitor.support;

import java.time.Instant;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    public record ErrorResponse(String code, String message, Instant timestamp) {

        static ErrorResponse of(ErrorCode errorCode, String message) {
            return new ErrorResponse(errorCode.name(), message, Instant.now());
        }
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handle(ApiException e) {
        return ResponseEntity.status(e.errorCode().status())
                .body(ErrorResponse.of(e.errorCode(), e.getMessage()));
    }

    /** size 범위 위반 등. 처리하지 않으면 500 으로 나가 클라이언트 잘못을 서버 오류로 알리게 된다. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handle(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .orElse(ErrorCode.INVALID_PARAMETER.message());
        return ResponseEntity.status(ErrorCode.INVALID_PARAMETER.status())
                .body(ErrorResponse.of(ErrorCode.INVALID_PARAMETER, message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handle(MissingServletRequestParameterException e) {
        return ResponseEntity.status(ErrorCode.INVALID_PARAMETER.status())
                .body(ErrorResponse.of(ErrorCode.INVALID_PARAMETER, "%s 파라미터가 필요합니다.".formatted(e.getParameterName())));
    }

    /** enum·숫자 변환 실패. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handle(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(ErrorCode.INVALID_PARAMETER.status())
                .body(ErrorResponse.of(ErrorCode.INVALID_PARAMETER,
                        "%s 값이 올바르지 않습니다: %s".formatted(e.getName(), e.getValue())));
    }
}
