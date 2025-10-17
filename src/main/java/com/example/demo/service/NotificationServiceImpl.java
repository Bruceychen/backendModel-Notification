package com.example.demo.service;

import com.example.demo.dto.NotificationMessage;
import com.example.demo.dto.NotificationRequest;
import com.example.demo.enums.NotificationMessageType;
import com.example.demo.enums.NotificationType;
import com.example.demo.model.Notifications;
import com.example.demo.mq.NotificationProducer;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationProducer notificationProducer;
    private final RedisUtil redisUtil;

    @Override
    @Transactional
    public Notifications createNotification(NotificationRequest request) {
        // gen a entity
        Notifications notification = new Notifications();
        notification.setType(NotificationType.fromString(request.getType().name().toUpperCase()));
        notification.setRecipient(request.getRecipient());
        notification.setSubject(request.getSubject());
        notification.setContent(request.getContent());

        // save to DB
        Notifications savedNotification = notificationRepository.save(notification);

        // register sync
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationProducer.sendNotification(toMessage(savedNotification, NotificationMessageType.CREATE));
            }
        });

        return null;
    }

    @Override
    public Optional<Notifications> getNotificationById(Long id) {
        // check if redis has
        Optional<Notifications> cachedNotification = redisUtil.findNotificationById(id);
        if(cachedNotification.isPresent()) {
            return cachedNotification;
        }

        // if not in redis, get from DB
        Optional<Notifications> notificationFromDb = notificationRepository.findById(id);

        // successfully from DB, update redis
        notificationFromDb.ifPresent(redisUtil::cacheNotification);

        return notificationFromDb;
    }

    private NotificationMessage toMessage(Notifications notification, NotificationMessageType messageType) {
        return NotificationMessage.builder()
                .id(notification.getId())
                .notificationType(notification.getType())
                .notificationMessageType(messageType)
                .recipient(notification.getRecipient())
                .subject(notification.getSubject())
                .content(notification.getContent())
                .build();
    }
}
