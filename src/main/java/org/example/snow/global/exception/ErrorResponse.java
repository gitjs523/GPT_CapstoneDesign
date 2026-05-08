package org.example.snow.global.exception;

import java.time.LocalDateTime;

public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String code,
        String error,
        String message,
        String path
) {

    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return new ErrorResponse(
                LocalDateTime.now(),
                errorCode.getStatus().value(),
                errorCode.getCode(),
                errorCode.getStatus().getReasonPhrase(),
                errorCode.getMessage(),
                path
        );
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
        return new ErrorResponse(
                LocalDateTime.now(),
                errorCode.getStatus().value(),
                errorCode.getCode(),
                errorCode.getStatus().getReasonPhrase(),
                message,
                path
        );
    }
}
