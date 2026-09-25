package com.flashsale.common.api;

import com.flashsale.common.trace.TraceContext;
import com.flashsale.common.trace.StructuredLogContext;
import java.util.Map;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleValidation(IllegalArgumentException exception) {
        String code = exception.getMessage();
        if (code == null || !code.matches("[A-Z][A-Z0-9_]+")) code = ErrorCode.VALIDATION_ERROR.name();
        try (var ignored = StructuredLogContext.open(Map.of(StructuredLogContext.ERROR_CODE, code))) {
        return ApiResponse.failure(code, code, TraceContext.getOrCreate());
        }
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpected(Exception exception) {
        try (var ignored = StructuredLogContext.open(Map.of(StructuredLogContext.ERROR_CODE, ErrorCode.INTERNAL_ERROR.name()))) {
        return ApiResponse.failure(ErrorCode.INTERNAL_ERROR.name(), "internal error", TraceContext.getOrCreate());
        }
    }
}
