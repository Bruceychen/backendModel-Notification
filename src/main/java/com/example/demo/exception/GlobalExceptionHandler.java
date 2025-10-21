package com.example.demo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidNotificationTypeException.class)
    public ResponseEntity<Map<String, String>> handleInvalidNotificationTypeException(InvalidNotificationTypeException ex) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("message", ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        // Check if the exception message is related to NotificationType enum
        if (ex.getMessage() != null && ex.getMessage().contains("No enum constant com.example.demo.enums.NotificationType.")) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "unsupported type");
            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }
        // For other IllegalArgumentExceptions, return a generic bad request
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("message", ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
}
