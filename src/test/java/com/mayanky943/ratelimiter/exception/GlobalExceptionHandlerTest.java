package com.mayanky943.ratelimiter.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void wrapsExceptionAsInternalError() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, Object>> response = handler.handle(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "internal_error");
        assertThat(response.getBody()).containsEntry("message", "boom");
    }

    @Test
    void handlesNullMessageGracefully() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, Object>> response = handler.handle(new RuntimeException());
        assertThat(response.getBody().get("message")).isEqualTo("Unknown error");
    }
}
