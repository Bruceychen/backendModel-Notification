package com.example.demo.service;

import com.example.demo.dto.NotificationRequest;
import com.example.demo.dto.UpdateNotificationRequest;
import com.example.demo.model.Notifications;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface NotificationService {
    Notifications createNotification(NotificationRequest request);

    Optional<Notifications> getNotificationById(Long id);

    List<Notifications> getRecentNotifications();

    Optional<Notifications> updateNotification(Long id, UpdateNotificationRequest request);

    @Transactional
    boolean deleteNotification(Long id);
}
