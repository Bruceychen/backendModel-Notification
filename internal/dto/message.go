package dto

import "github.com/brucechen/notification-service/internal/domain"

// NotificationMessage represents a message sent to RocketMQ
type NotificationMessage struct {
	ID                      int64                           `json:"id"`
	NotificationType        domain.NotificationType         `json:"notification_type"`
	NotificationMessageType domain.NotificationMessageType  `json:"notification_message_type"`
	Recipient               string                          `json:"recipient"`
	Subject                 string                          `json:"subject"`
	Content                 string                          `json:"content"`
}

// NewNotificationMessage creates a new NotificationMessage from domain.Notification
func NewNotificationMessage(n *domain.Notification, messageType domain.NotificationMessageType) *NotificationMessage {
	return &NotificationMessage{
		ID:                      n.ID,
		NotificationType:        n.Type,
		NotificationMessageType: messageType,
		Recipient:               n.Recipient,
		Subject:                 n.Subject,
		Content:                 n.Content,
	}
}