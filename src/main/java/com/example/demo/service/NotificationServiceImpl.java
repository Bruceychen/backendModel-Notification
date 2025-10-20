package com.example.demo.service;

import com.example.demo.dto.NotificationMessage;
import com.example.demo.dto.NotificationRequest;
import com.example.demo.dto.UpdateNotificationRequest;
import com.example.demo.enums.NotificationMessageType;
import com.example.demo.enums.NotificationType;
import com.example.demo.model.Notifications;
import com.example.demo.mq.NotificationProducer;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
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

                //redis
                redisUtil.cacheNotification(savedNotification);
                // clean recent list on redis
                // 避免高併發競爭，讓 Recent List 在下次讀取時從 DB 重新計算。
                redisUtil.clearRecentList();
            }
        });

        return savedNotification;
    }

    @Override
    public Optional<Notifications> getNotificationById(Long id) {
        // check if redis has
        Optional<Notifications> cachedNotification = redisUtil.findNotificationById(id);
        if (cachedNotification.isPresent()) {
            return cachedNotification;
        }

        // if not in redis, get from DB
        Optional<Notifications> notificationFromDb = notificationRepository.findById(id);

        // successfully from DB, update redis
        notificationFromDb.ifPresent(redisUtil::cacheNotification);

        return notificationFromDb;
    }

    @Override
    public List<Notifications> getRecentNotifications() {
        // try to fetch from Redis
        List<Notifications> recentNotifications = redisUtil.findRecentNotifications();
        if (CollectionUtils.isNotEmpty(recentNotifications)) {
            return recentNotifications;
        }

        // get lock
        String lockKey = redisUtil.getLockKey("getRecentNotifications");
        Boolean acquired = redisUtil.setnxWithExpiration(lockKey, "locked", Duration.ofSeconds(30));

        if (Boolean.TRUE.equals(acquired)) {
            // get lock
            try {
                // DCL (double check lock) check cache again in case be refill while wait for lock
                List<Notifications> checkNotifications = redisUtil.findRecentNotifications();
                if (CollectionUtils.isNotEmpty(checkNotifications)) {
                    return checkNotifications;
                }

                // get from DB
                List<Notifications> recentNotificationFromDb = notificationRepository.findTop10ByOrderByCreatedAtDesc(PageRequest.of(0, 10));

                // 2.3 refill the redis
                if (CollectionUtils.isNotEmpty(recentNotificationFromDb)) {
                    redisUtil.populateRecentList(recentNotificationFromDb);
                }

                return recentNotificationFromDb;
            } finally {
                // release lock
                redisUtil.deleteKey(lockKey);
            }
        } else {
            // fetch lock failed: other thread is fulfill redis (防驚群核心邏輯)

            // wait temp then re-try
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            // re-try
            List<Notifications> retryNotifications = redisUtil.findRecentNotifications();

            // return if success or return empty list
            return Optional.ofNullable(retryNotifications).orElse(Collections.emptyList());
        }
    }

    @Override
    @Transactional
    public Optional<Notifications> updateNotification(Long id, UpdateNotificationRequest request) {
        return notificationRepository.findNotificationAndLockById(id).map( notifications -> {
            notifications.setSubject(request.getSubject());
            notifications.setContent(request.getContent());
            Notifications updatedNotification = notificationRepository.save(notifications);

            // register (check DB success and clean cache)
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // clean whole recent list
                    redisUtil.clearRecentList();

                    // clean single cache in case duplicated
                    redisUtil.deleteNotification(id);

                    // MD not mentioned, but still push to MQ
                    notificationProducer.sendNotification(toMessage(updatedNotification, NotificationMessageType.UPDATE));
                }
            });

            return updatedNotification;
        });
    }

    @Override
    @Transactional
    public boolean deleteNotification(Long id) {
        // get from DB
        Optional<Notifications> dbNotification = notificationRepository.findNotificationAndLockById(id);
        if (dbNotification.isEmpty()) {
            return false;
        }

        // delete
        notificationRepository.deleteById(id);

        // check if DB committed then clean cache
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // clean whole recent list cache
                redisUtil.clearRecentList();

                // clean single cache in case of race condition
                redisUtil.deleteNotification(id);

                // .MD not listed, push to MQ to align local and remote system
                notificationProducer.sendNotification(toMessage(dbNotification.get(), NotificationMessageType.DELETE));
            }
        });

        return true;
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
