package com.flashsale.common.api;

import com.flashsale.common.trace.TraceContext;
import com.flashsale.common.trace.StructuredLogContext;
import java.util.Map;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleValidation(IllegalArgumentException exception) {
        String code = exception.getMessage();
        if (code == null || !code.matches("[A-Z][A-Z0-9_]+")) code = ErrorCode.VALIDATION_ERROR.name();
        try (var ignored = StructuredLogContext.open(Map.of(StructuredLogContext.ERROR_CODE, code))) {
        return ApiResponse.failure(code, code, TraceContext.getOrCreate());
        }
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ApiResponse<Void> handleMissingHeader(MissingRequestHeaderException exception) {
        return ApiResponse.failure(ErrorCode.VALIDATION_ERROR.name(), ErrorCode.VALIDATION_ERROR.name(), TraceContext.getOrCreate());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpected(Exception exception) {
        log.error("unhandled request exception", exception);
        try (var ignored = StructuredLogContext.open(Map.of(StructuredLogContext.ERROR_CODE, ErrorCode.INTERNAL_ERROR.name()))) {
        return ApiResponse.failure(ErrorCode.INTERNAL_ERROR.name(), "internal error", TraceContext.getOrCreate());
        }
    }
}
