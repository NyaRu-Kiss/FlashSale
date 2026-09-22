package com.flashsale.common.api;

import com.flashsale.common.trace.TraceContext;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleValidation(IllegalArgumentException exception) {
        return ApiResponse.failure(ErrorCode.VALIDATION_ERROR.name(), exception.getMessage(), TraceContext.getOrCreate());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpected(Exception exception) {
        return ApiResponse.failure(ErrorCode.INTERNAL_ERROR.name(), "internal error", TraceContext.getOrCreate());
    }
}
