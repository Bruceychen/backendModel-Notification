package mq

import (
	"context"
	"encoding/json"
	"fmt"
	"log"

	"github.com/apache/rocketmq-client-go/v2"
	"github.com/apache/rocketmq-client-go/v2/consumer"
	"github.com/apache/rocketmq-client-go/v2/primitive"
	"github.com/brucechen/notification-service/internal/config"
	"github.com/brucechen/notification-service/internal/dto"
)

// NotificationConsumer handles consuming messages from RocketMQ
type NotificationConsumer struct {
	consumer rocketmq.PushConsumer
}

// NewNotificationConsumer creates a new NotificationConsumer
func NewNotificationConsumer(cfg *config.Config) (*NotificationConsumer, error) {
	c, err := rocketmq.NewPushConsumer(
		consumer.WithNameServer(cfg.RocketMQ.NameServers),
		consumer.WithGroupName(cfg.RocketMQ.ConsumerGroup),
		consumer.WithConsumerModel(consumer.Clustering),
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create RocketMQ consumer: %w", err)
	}

	// Subscribe to the notification topic
	err = c.Subscribe(cfg.App.NotificationTopic, consumer.MessageSelector{}, func(ctx context.Context, msgs ...*primitive.MessageExt) (consumer.ConsumeResult, error) {
		for _, msg := range msgs {
			if err := processMessage(msg); err != nil {
				log.Printf("Failed to process message: %v", err)
				// Return ReconsumeLater to retry the message
				return consumer.ConsumeRetryLater, err
			}
		}
		return consumer.ConsumeSuccess, nil
	})
	if err != nil {
		return nil, fmt.Errorf("failed to subscribe to topic: %w", err)
	}

	if err := c.Start(); err != nil {
		return nil, fmt.Errorf("failed to start RocketMQ consumer: %w", err)
	}

	log.Printf("NotificationConsumer started successfully, subscribed to topic: %s", cfg.App.NotificationTopic)

	return &NotificationConsumer{
		consumer: c,
	}, nil
}

// processMessage processes a notification message
func processMessage(msg *primitive.MessageExt) error {
	var notificationMsg dto.NotificationMessage
	if err := json.Unmarshal(msg.Body, &notificationMsg); err != nil {
		return fmt.Errorf("failed to unmarshal message: %w", err)
	}

	// Process the message based on type
	switch notificationMsg.NotificationMessageType {
	case "CREATE":
		log.Printf("Processing CREATE notification: ID=%d, Type=%s, Recipient=%s",
			notificationMsg.ID, notificationMsg.NotificationType, notificationMsg.Recipient)
		// In a real implementation, you might send actual email/SMS here
		// For now, we just log it

	case "UPDATE":
		log.Printf("Processing UPDATE notification: ID=%d, Subject=%s",
			notificationMsg.ID, notificationMsg.Subject)
		// Handle notification update events

	case "DELETE":
		log.Printf("Processing DELETE notification: ID=%d", notificationMsg.ID)
		// Handle notification delete events

	default:
		log.Printf("Unknown message type: %s", notificationMsg.NotificationMessageType)
	}

	return nil
}

// Close shuts down the consumer
func (c *NotificationConsumer) Close() error {
	return c.consumer.Shutdown()
}