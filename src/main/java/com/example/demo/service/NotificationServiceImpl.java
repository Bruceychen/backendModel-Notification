package com.example.demo.service;

import com.example.demo.dto.NotificationMessage;
import com.example.demo.dto.NotificationRequest;
import com.example.demo.dto.UpdateNotificationRequest;
import com.example.demo.model.Notification;
import com.example.demo.model.NotificationType;
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
    public Notification createNotification(NotificationRequest request) {
        // 1. Create the entity
        Notification notification = new Notification();
        notification.setType(NotificationType.fromString(request.getType().name().toUpperCase()));
        notification.setRecipient(request.getRecipient());
        notification.setSubject(request.getSubject());
        notification.setContent(request.getContent());

        // 2. Save to DB
        Notification savedNotification = notificationRepository.save(notification);

        // 3. 註冊事務同步回調，確保所有非 DB 操作只在 DB 提交成功後執行。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // A. Push to RocketMQ (應在 DB 成功後發送，確保消息有效)
                notificationProducer.sendNotification(toMessage(savedNotification));

                // B. 更新單一快取 (使用 SET 進行更新，避免下次讀取穿透)
                redisUtil.cacheNotification(savedNotification);

                // C. 清除整個 Recent List
                // 避免高併發競爭，讓 Recent List 在下次讀取時從 DB 重新計算。
                redisUtil.clearRecentList();
            }
        });

        return savedNotification;
    }

    @Override
    public Optional<Notification> getNotificationById(Long id) {
        // 1. 先嘗試從 Redis 獲取
        Optional<Notification> cachedNotification = redisUtil.findNotificationById(id);
        if (cachedNotification.isPresent()) {
            return cachedNotification;
        }

        // 2. 嘗試從 DB 獲取
        Optional<Notification> notificationFromDb = notificationRepository.findById(id);

        // 3. 從 DB 獲取成功後，更新到 Redis 中
        notificationFromDb.ifPresent(redisUtil::cacheNotification);

        return notificationFromDb;
    }

    @Override
    public List<Notification> getRecentNotifications() {
        // 1. 嘗試從 Redis 獲取
        List<Notification> recentNotifications = redisUtil.findRecentNotifications();
        if (!CollectionUtils.isEmpty(recentNotifications)) {
            return recentNotifications;
        }

        // 2. 嘗試獲取分佈式鎖 (設置 TTL 為 30 秒)
        String lockKey = redisUtil.getLockKey("getRecentNotifications");
        Boolean acquired = redisUtil.setnxWithExpiration(lockKey, "locked", Duration.ofSeconds(30));

        if (Boolean.TRUE.equals(acquired)) {
            // 成功獲取鎖：該執行緒負責執行 DB 查詢和快取回填
            try {
                // 2.1 雙重檢查鎖定 (DCL): 再次檢查快取（防止在等待鎖的過程中快取已被回填）
                List<Notification> checkNotifications = redisUtil.findRecentNotifications();
                if (!CollectionUtils.isEmpty(checkNotifications)) {
                    return checkNotifications;
                }

                // 2.2 從 DB 查詢最新的數據
                List<Notification> recentNotificationFromDb = notificationRepository.findTop10ByOrderByCreatedAtDesc(PageRequest.of(0, 10));

                // 2.3 回填快取
                if (!CollectionUtils.isEmpty(recentNotificationFromDb)) {
                    redisUtil.populateRecentList(recentNotificationFromDb);
                }

                return recentNotificationFromDb;
            } finally {
                // 2.4 釋放鎖
                redisUtil.deleteKey(lockKey);
            }
        } else {
            // 獲取鎖失敗：表示有其他執行緒正在回填快取 (防驚群核心邏輯)

            // 3. 短暫等待後重試讀取快取
            try {
                // 等待 100ms 讓持有鎖的執行緒有時間完成 DB 查詢和快取回填
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            // 4. 再次嘗試讀取快取
            List<Notification> retryNotifications = redisUtil.findRecentNotifications();

            // 如果重試讀取成功，則返回；否則返回空列表
            return Optional.ofNullable(retryNotifications).orElse(Collections.emptyList());
        }
    }

    @Override
    @Transactional
    public Optional<Notification> updateNotification(Long id, UpdateNotificationRequest request) {
        return notificationRepository.findNotificationAndLockById(id).map(notification -> {
            notification.setSubject(request.getSubject());
            notification.setContent(request.getContent());
            Notification updatedNotification = notificationRepository.save(notification);

            // 2. 註冊事務同步回調 (確保在 DB 提交成功後才清理 Cache)
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // A. 清除整個 Recent List
                    redisUtil.clearRecentList();

                    // B. 清除單一快取，避免雙寫競爭造成髒讀
                    redisUtil.deleteNotification(id);
                }
            });

            return updatedNotification;
        });
    }

    @Override
    @Transactional
    public boolean deleteNotification(Long id) {
        // 1. 從 DB 讀取 (Read)
        if (!notificationRepository.existsById(id)) {
            return false;
        }

        // 2. 執行 DB 刪除
        notificationRepository.deleteById(id);

        // 3. 確認 DB 提交成功後清理快取
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // A. 清除整個 Recent List
                redisUtil.clearRecentList();

                // B. 清除單一快取，避免雙寫競爭
                redisUtil.deleteNotification(id);
            }
        });

        return true;
    }

    private NotificationMessage toMessage(Notification notification) {
        return NotificationMessage.builder()
                .id(notification.getId())
                .type(notification.getType())
                .recipient(notification.getRecipient())
                .subject(notification.getSubject())
                .content(notification.getContent())
                .build();
    }
}