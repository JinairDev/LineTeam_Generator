package com.crew.lineteam.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.Map;

/**
 * API 전역 에러 처리: 항상 JSON { "message": "..." } 반환
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static String safeMessage(Exception e, String fallback) {
        String msg = e.getMessage();
        return msg != null && !msg.isBlank() ? msg : fallback;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", safeMessage(e, "잘못된 요청입니다.")));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("message", safeMessage(e, "처리할 수 없는 상태입니다.")));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleFileTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("message", "파일 크기가 너무 큽니다."));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> handleMultipart(MultipartException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", safeMessage(e, "파일 업로드 형식이 올바르지 않습니다. 파일을 선택해 주세요.")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleBadBody(HttpMessageNotReadableException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "요청 본문이 올바르지 않습니다. JSON 형식을 확인해 주세요."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleOther(Exception e) {
        String msg = safeMessage(e, "일시적인 오류가 발생했습니다.");
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", msg));
    }
}
