package mq

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/apache/rocketmq-client-go/v2"
	"github.com/apache/rocketmq-client-go/v2/primitive"
	"github.com/apache/rocketmq-client-go/v2/producer"
	"github.com/brucechen/notification-service/internal/config"
	"github.com/brucechen/notification-service/internal/dto"
)

// NotificationProducer handles sending messages to RocketMQ
type NotificationProducer struct {
	producer rocketmq.Producer
	topic    string
}

// NewNotificationProducer creates a new NotificationProducer
func NewNotificationProducer(cfg *config.Config) (*NotificationProducer, error) {
	p, err := rocketmq.NewProducer(
		producer.WithNameServer(cfg.RocketMQ.NameServers),
		producer.WithGroupName(cfg.RocketMQ.ProducerGroup),
		producer.WithRetry(cfg.RocketMQ.RetryTimes),
		producer.WithSendMsgTimeout(time.Duration(cfg.RocketMQ.SendTimeout)*time.Millisecond),
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create RocketMQ producer: %w", err)
	}

	if err := p.Start(); err != nil {
		return nil, fmt.Errorf("failed to start RocketMQ producer: %w", err)
	}

	return &NotificationProducer{
		producer: p,
		topic:    cfg.App.NotificationTopic,
	}, nil
}

// SendNotification sends a notification message to RocketMQ
func (p *NotificationProducer) SendNotification(ctx context.Context, message *dto.NotificationMessage) error {
	body, err := json.Marshal(message)
	if err != nil {
		return fmt.Errorf("failed to marshal message: %w", err)
	}

	msg := &primitive.Message{
		Topic: p.topic,
		Body:  body,
	}

	// Set message tag based on message type for filtering
	msg.WithTag(string(message.NotificationMessageType))

	result, err := p.producer.SendSync(ctx, msg)
	if err != nil {
		return fmt.Errorf("failed to send message: %w", err)
	}

	if result.Status != primitive.SendOK {
		return fmt.Errorf("message send status not OK: %v", result.Status)
	}

	return nil
}

// Close shuts down the producer
func (p *NotificationProducer) Close() error {
	return p.producer.Shutdown()
}