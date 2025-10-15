package com.example.demo.dto;

import com.example.demo.model.NotificationType;
import lombok.Data;

@Data
public class NotificationRequest {
    private NotificationType type;
    private String recipient;
    private String subject;
    private String content;
}