package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final RocketMQ rocketmq = new RocketMQ();
    private final Redis redis = new Redis();

    @Data
    public static class RocketMQ {
        private String notificationTopic;
    }

    @Data
    public static class Redis {
        private String recentListKey;
        private String notificationKeyPrefix;
    }
}
