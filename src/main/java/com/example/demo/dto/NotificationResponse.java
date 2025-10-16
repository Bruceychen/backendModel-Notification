package com.example.demo.dto;

import com.example.demo.enums.NotificationType;
import com.example.demo.model.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private Long id;
    private NotificationType type;
    private String recipient;
    private String subject;
    private String content;
    private LocalDateTime createdAt;

    public static NotificationResponse fromEntity(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .recipient(notification.getRecipient())
                .subject(notification.getSubject())
                .content(notification.getContent())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}