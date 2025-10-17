package com.example.demo.service;

import com.example.demo.dto.NotificationRequest;
import com.example.demo.model.Notifications;

import java.util.Optional;

public interface NotificationService {

    Optional<Notifications> getNotificationById(Long id);

    Notifications createNotification(NotificationRequest request);
}
