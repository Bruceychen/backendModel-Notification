package com.example.demo.service;

import com.example.demo.dto.NotificationRequest;
import com.example.demo.dto.UpdateNotificationRequest;
import com.example.demo.model.Notification;

import java.util.List;
import java.util.Optional;

public interface NotificationService {
    Notification createNotification(NotificationRequest request);

    Optional<Notification> getNotificationById(Long id);

    List<Notification> getRecentNotifications();

    Optional<Notification> updateNotification(Long id, UpdateNotificationRequest request);

    boolean deleteNotification(Long id);
}
