package com.example.demo.dto;

import com.example.demo.enums.NotificationType;
import com.example.demo.model.Notifications;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private Long id;
    private NotificationType type;
    private String recipient;
    private String subject;
    private String content;
    private LocalDateTime createdAt;

    public static NotificationResponse fromEntity(Notifications notifications) {
        return NotificationResponse.builder()
                .id(notifications.getId())
                .type(notifications.getType())
                .recipient(notifications.getRecipient())
                .subject(notifications.getSubject())
                .content(notifications.getContent())
                .createdAt(notifications.getCreatedAt())
                .build();
    }
}
