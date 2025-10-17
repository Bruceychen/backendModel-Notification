package com.example.demo.service;

import com.example.demo.model.Notifications;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    @Override
    public Optional<Notifications> getNotificationById(Long id) {
        return Optional.empty();
    }
}
