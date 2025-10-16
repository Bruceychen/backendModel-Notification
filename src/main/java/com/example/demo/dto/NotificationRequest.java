package com.example.demo.dto;

import com.example.demo.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {
    private NotificationType type;
    private String recipient;
    private String subject;
    private String content;


}