package com.example.demo.mq;

import com.example.demo.config.AppProperties;
import com.example.demo.dto.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "#{appProperties.rocketmq.notificationTopic}", consumerGroup = "notification_consumer_group")
public class NotificationConsumer implements RocketMQListener<NotificationMessage> {

    private final AppProperties appProperties;

    @Override
    public void onMessage(NotificationMessage message) {
        log.info("Received message: {}", message);
    }
}
