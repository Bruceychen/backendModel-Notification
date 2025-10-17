package com.example.demo.mq;

import com.example.demo.config.AppProperties;
import com.example.demo.dto.NotificationMessage;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationProducer {

    private final RocketMQTemplate rocketmqTemplate;
    private final AppProperties appProperties;

    public void sendNotification(NotificationMessage message) {
        rocketmqTemplate.convertAndSend(appProperties.getRocketmq().getNotificationTopic(), message);
    }
}
