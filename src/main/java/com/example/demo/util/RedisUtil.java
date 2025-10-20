package com.example.demo.util;

import com.example.demo.config.AppProperties;
import com.example.demo.model.Notifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;
import org.apache.commons.collections.CollectionUtils;

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

    public Optional<Notifications> findNotificationById(Long id) {
        String key = appProperties.getRedis().getNotificationKeyPrefix() + id;
        Object cachedObject = redisTemplate.opsForValue().get(key);
        if (cachedObject instanceof Notifications notification) {
            return Optional.of(notification);
        }
        return Optional.empty();
    }

    public List<Notifications> findRecentNotifications() {
        String recentListKey = appProperties.getRedis().getRecentListKey();

        // get ZSET
        Set<Object> objects = redisTemplate.opsForZSet().reverseRange(recentListKey, 0, -1);

        if (CollectionUtils.isEmpty(objects)) {
            return List.of();
        }

        // convert
        return objects.stream()
                // convert ZSET (SET) into List via stream api
                .map(obj -> (Notifications) obj)
                .collect(Collectors.toList());
    }

    public void cacheNotification(Notifications notification) {
        String key = appProperties.getRedis().getNotificationKeyPrefix() + notification.getId();
        redisTemplate.opsForValue().set(key, notification, 10, TimeUnit.MINUTES);
    }

    public void populateRecentList(List<Notifications> notifications) {
        String recentListKey = appProperties.getRedis().getRecentListKey();

        if (CollectionUtils.isEmpty(notifications)) {
            return;
        }

        // prep for ZSET strucutre
        Set<ZSetOperations.TypedTuple<Object>> tuples = notifications.stream()
                .map(notification -> {
                    // using create date time as scor
                    double score = notification.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli();

                    // create typedTuple: include score and member (Notification field)
                    return ZSetOperations.TypedTuple.of((Object) notification, score);
                })
                .collect(Collectors.toSet());

        // run ZADD to put Set and score into ZSET atomically
        redisTemplate.opsForZSet().add(recentListKey, tuples);

        // (可選/推薦) 進行定長修剪，以防載入過多歷史數據
        // make sure keep top 10
        redisTemplate.opsForZSet().removeRange(recentListKey, 0, -11);
    }

    public void deleteNotification(Long id) {
        String key = appProperties.getRedis().getNotificationKeyPrefix() + id;
        redisTemplate.delete(key);
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
