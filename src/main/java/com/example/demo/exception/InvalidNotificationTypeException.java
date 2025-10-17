package com.example.demo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidNotificationTypeException extends RuntimeException {
    public InvalidNotificationTypeException(String message) {
        super(message);
    }
}
