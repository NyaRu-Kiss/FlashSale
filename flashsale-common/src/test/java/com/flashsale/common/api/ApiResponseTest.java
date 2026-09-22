package com.flashsale.common.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {
    @Test
    void createsSuccessAndFailureResponses() {
        assertEquals("OK", ApiResponse.success("value", "trace-1").code());
        assertEquals("VALIDATION_ERROR", ApiResponse.failure("VALIDATION_ERROR", "bad", "trace-1").code());
    }

    @Test
    void validatesPageResponse() {
        assertEquals(2, new PageResponse<>(List.of("a", "b"), 1, 20, 2).items().size());
        assertThrows(IllegalArgumentException.class, () -> new PageResponse<>(List.of(), 0, 20, 0));
    }
}
