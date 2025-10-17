package com.example.demo.dto;

import com.example.demo.enums.NotificationType;
import com.example.demo.model.Notifications;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class NotificationResponseTest {

    @Test
    void fromEntity_shouldMapAllFieldsCorrectly() {
        // Given - prepare test data
        Notifications notification = new Notifications();
        notification.setId(123L);
        notification.setType(NotificationType.EMAIL);
        notification.setRecipient("user@test.com");
        notification.setSubject("Hello World");
        notification.setContent("This is test content");
        notification.setCreatedAt(LocalDateTime.of(2025, 10, 17, 10, 30, 0));

        // When - execute the method we're testing
        NotificationResponse response = NotificationResponse.fromEntity(notification);

        // Then - verify the results
        assertNotNull(response, "Response should not be null");
        assertEquals(123L, response.getId());
        assertEquals(NotificationType.EMAIL, response.getType());
        assertEquals("user@test.com", response.getRecipient());
        assertEquals("Hello World", response.getSubject());
        assertEquals("This is test content", response.getContent());
        assertEquals(LocalDateTime.of(2025, 10, 17, 10, 30, 0), response.getCreatedAt());
    }

    @Test
    void fromEntity_shouldHandleSmsType() {
        // Given
        Notifications notification = new Notifications();
        notification.setId(456L);
        notification.setType(NotificationType.SMS);
        notification.setRecipient("+1234567890");
        notification.setSubject("SMS Alert");
        notification.setContent("Your code is 123456");
        notification.setCreatedAt(LocalDateTime.now());

        // When
        NotificationResponse response = NotificationResponse.fromEntity(notification);

        // Then
        assertEquals(NotificationType.SMS, response.getType());
        assertEquals("+1234567890", response.getRecipient());
    }

    @Test
    void fromEntity_shouldHandleNullSubject() {
        // Given - subject can be null
        Notifications notification = new Notifications();
        notification.setId(789L);
        notification.setType(NotificationType.EMAIL);
        notification.setRecipient("test@example.com");
        notification.setSubject(null);  // null subject
        notification.setContent("Content without subject");
        notification.setCreatedAt(LocalDateTime.now());

        // When
        NotificationResponse response = NotificationResponse.fromEntity(notification);

        // Then
        assertNull(response.getSubject(), "Subject should be null when entity subject is null");
        assertNotNull(response.getContent());
    }
}