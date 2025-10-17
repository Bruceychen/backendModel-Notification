package com.example.demo.util;

import com.example.demo.config.AppProperties;
import com.example.demo.model.Notifications;
import lombok.RequiredArgsConstructor;import org.apache.commons.collections.CollectionUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

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

    public void cacheNotification(Notifications notification) {
        String key = appProperties.getRedis().getNotificationKeyPrefix() + notification.getId();
        redisTemplate.opsForValue().set(key, notification, 10, TimeUnit.MINUTES);
    }
}
