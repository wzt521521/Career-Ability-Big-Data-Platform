package com.career.platform.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.career.platform.common.error.BusinessException;
import com.career.platform.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    @Test
    void redactsSensitiveFieldsFromBusinessErrorResponses() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.BAD_REQUEST, "apiKey=raw-key password=raw-password"));

        assertEquals(400, response.getStatusCodeValue());
        assertFalse(response.getBody().getMessage().contains("raw-key"));
        assertFalse(response.getBody().getMessage().contains("raw-password"));
    }
}
