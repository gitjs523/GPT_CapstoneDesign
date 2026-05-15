package org.example.snow.global.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void maxUploadSizeExceededReturns400WithFileTooLargeCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/notebooks/1/documents");
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(50 * 1024 * 1024L);

        ResponseEntity<ErrorResponse> response = handler.handleMaxUploadSizeExceededException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("DOC_015");
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().path()).isEqualTo("/api/notebooks/1/documents");
    }
}
