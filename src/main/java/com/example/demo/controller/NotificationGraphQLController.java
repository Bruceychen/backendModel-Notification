package com.example.demo.controller;

import com.example.demo.dto.NotificationRequest;
import com.example.demo.dto.NotificationResponse;
import com.example.demo.dto.UpdateNotificationRequest;
import com.example.demo.model.Notifications;
import com.example.demo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class NotificationGraphQLController {

    private final NotificationService notificationService;

    /**
     * Query: Get notification by ID
     *
     * GraphQL Query Example:
     * {
     *   notification(id: "1") {
     *     id
     *     type
     *     recipient
     *     subject
     *     content
     *     createdAt
     *   }
     * }
     */
    @QueryMapping
    public NotificationResponse notification(@Argument Long id) {
        Optional<Notifications> notification = notificationService.getNotificationById(id);
        return notification.map(NotificationResponse::fromEntity).orElse(null);
    }

    /**
     * Query: Get recent notifications (top 10)
     *
     * GraphQL Query Example:
     * {
     *   recentNotifications {
     *     id
     *     type
     *     recipient
     *     subject
     *     content
     *     createdAt
     *   }
     * }
     */
    @QueryMapping
    public List<NotificationResponse> recentNotifications() {
        List<Notifications> notifications = notificationService.getRecentNotifications();
        return notifications.stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Query: Get all notifications with optional pagination
     *
     * Note: This is a basic implementation. For production use, consider implementing
     * proper pagination in the service layer.
     *
     * GraphQL Query Example:
     * {
     *   allNotifications(limit: 20, offset: 0) {
     *     id
     *     type
     *     recipient
     *     subject
     *     content
     *     createdAt
     *   }
     * }
     */
    @QueryMapping
    public List<NotificationResponse> allNotifications(
            @Argument Integer limit,
            @Argument Integer offset) {
        // For now, returning recent notifications
        // TODO: Implement proper pagination in service layer if needed
        List<Notifications> notifications = notificationService.getRecentNotifications();
        return notifications.stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Mutation: Create new notification
     *
     * GraphQL Mutation Example:
     * mutation {
     *   createNotification(input: {
     *     type: EMAIL
     *     recipient: "user@example.com"
     *     subject: "Test Subject"
     *     content: "Test Content"
     *   }) {
     *     id
     *     type
     *     recipient
     *     subject
     *     content
     *     createdAt
     *   }
     * }
     */
    @MutationMapping
    public NotificationResponse createNotification(@Argument NotificationRequest input) {
        Notifications created = notificationService.createNotification(input);
        return NotificationResponse.fromEntity(created);
    }

    /**
     * Mutation: Update notification
     *
     * GraphQL Mutation Example:
     * mutation {
     *   updateNotification(id: "1", input: {
     *     subject: "Updated Subject"
     *     content: "Updated Content"
     *   }) {
     *     id
     *     type
     *     recipient
     *     subject
     *     content
     *     createdAt
     *   }
     * }
     */
    @MutationMapping
    public NotificationResponse updateNotification(
            @Argument Long id,
            @Argument UpdateNotificationRequest input) {
        Optional<Notifications> updated = notificationService.updateNotification(id, input);
        return updated.map(NotificationResponse::fromEntity).orElse(null);
    }

    /**
     * Mutation: Delete notification
     *
     * GraphQL Mutation Example:
     * mutation {
     *   deleteNotification(id: "1")
     * }
     */
    @MutationMapping
    public Boolean deleteNotification(@Argument Long id) {
        return notificationService.deleteNotification(id);
    }
}
