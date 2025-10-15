package com.example.demo.util;

import com.example.demo.config.AppProperties;
import com.example.demo.model.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AppProperties appProperties;

    public Optional<Notification> findNotificationById(Long id) {
        String key = appProperties.getRedis().getNotificationKeyPrefix() + id;
        Object cachedObject = redisTemplate.opsForValue().get(key);
        if (cachedObject instanceof Notification notification) {
            return Optional.of(notification);
        }
        return Optional.empty();
    }

    public List<Notification> findRecentNotifications() {
        String recentListKey = appProperties.getRedis().getRecentListKey();

        // 1. 使用 ZREVRANGE 從 ZSET 讀取元素
        Set<Object> objects = redisTemplate.opsForZSet().reverseRange(recentListKey, 0, -1);

        if (CollectionUtils.isEmpty(objects)) {
            return List.of();
        }

        // 2. 數據轉換
        return objects.stream()
                // 由於 ZSET 返回的是 Set，但我們需要 List，且 Set 保持了 ZSET 的排序
                // 這裡將 Set 元素流式處理並轉換為 List<Notification>
                .map(obj -> (Notification) obj)
                .collect(Collectors.toList());
    }

    public void cacheNotification(Notification notification) {
        String key = appProperties.getRedis().getNotificationKeyPrefix() + notification.getId();
        redisTemplate.opsForValue().set(key, notification, 10, TimeUnit.MINUTES);
    }

    public void addNotificationToRecentList(Notification notification) {
        String recentListKey = appProperties.getRedis().getRecentListKey();

        // 1. Score: 使用創建時間的毫秒級時間戳作為 Score
        double score = notification.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli();

        // 2. ZADD (新增/更新):
        //    ZADD 是原子操作。如果 notification 已經存在 (基於序列化的 JSON)，
        //    ZADD 會更新它的 score，在 create/update 時都調用它，可以處理新增和更新。
        redisTemplate.opsForZSet().add(recentListKey, notification, score);

        // 3. 定長維護 (原子操作): 移除除了最高 10 分之外的所有元素
        //    注意：ZSET 是升序排列 (分數越低越舊)，所以這邊移除最舊的元素。
        redisTemplate.opsForZSet().removeRange(recentListKey, 0, -11);
    }

    public void populateRecentList(List<Notification> notifications) {
        String recentListKey = appProperties.getRedis().getRecentListKey();

        if (CollectionUtils.isEmpty(notifications)) {
            return;
        }

        // 1. 準備 ZSET 的數據結構：Set<TypedTuple<V>>
        //    需要將 List<Notification> 轉換為 Set<ZSetOperations.TypedTuple<Notification>>
        Set<ZSetOperations.TypedTuple<Object>> tuples = notifications.stream()
                .map(notification -> {
                    // 使用創建時間的毫秒級時間戳作為 Score
                    double score = notification.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli();

                    // 建立 TypedTuple：包含 Score 和 Member (Notification 物件)
                    return ZSetOperations.TypedTuple.of((Object) notification, score);
                })
                .collect(Collectors.toSet());

        // 2. 執行 ZADD：將整個 Set 的元素和 Score 原子性地加入 ZSET
        redisTemplate.opsForZSet().add(recentListKey, tuples);

        // 3. (可選/推薦) 進行定長修剪，以防載入過多歷史數據
        //    確保只保留最新的 10 筆（分數最高的前 10 筆）
        redisTemplate.opsForZSet().removeRange(recentListKey, 0, -11);
    }

    public void updateNotificationInRecentList(Notification notification) {
        String recentListKey = appProperties.getRedis().getRecentListKey();

        // ZADD 命令如果 ID 存在，會執行更新
        redisTemplate.opsForZSet().add(
                recentListKey,
                notification,
                notification.getCreatedAt().toEpochSecond(ZoneOffset.UTC) // 使用創建時間作為 Score
        );

        // 保持定長（原子操作）
        redisTemplate.opsForZSet().removeRange(recentListKey, 0, -11); // 移除分數最低（最舊）的元素
    }

    public void deleteNotification(Long id) {
        String key = appProperties.getRedis().getNotificationKeyPrefix() + id;
        redisTemplate.delete(key);
    }

    public void removeNotificationFromRecentList(Notification notification) {
        String recentListKey = appProperties.getRedis().getRecentListKey();

        // ZREM 是原子操作，它會根據 Member (Notification 物件) 的值來移除 ZSET 中的元素
        // ZSET 的 Member 必須與 ZADD 時存入的 Member 完全匹配
        redisTemplate.opsForZSet().remove(recentListKey, notification);
    }

    public void clearRecentList() {
        String recentListKey = appProperties.getRedis().getRecentListKey();
        redisTemplate.delete(recentListKey);
    }

    public Boolean setnxWithExpiration(String key, String value, Duration timeout) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, timeout);
    }

    public String getLockKey(String key) {
        return appProperties.getRedis().getRecentListKey() + ":" + key + ":lock";
    }

    public void deleteKey(String key) {
        redisTemplate.delete(key);
    }
}
