package com.example.demo.dto;

import lombok.Data;

@Data
public class UpdateNotificationRequest {
    private String subject;
    private String content;
}
