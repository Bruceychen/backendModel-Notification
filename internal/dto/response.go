package dto

import (
	"time"

	"github.com/brucechen/notification-service/internal/domain"
)

// NotificationResponse represents the response payload for a notification
type NotificationResponse struct {
	ID        int64                   `json:"id"`
	Type      domain.NotificationType `json:"type"`
	Recipient string                  `json:"recipient"`
	Subject   string                  `json:"subject"`
	Content   string                  `json:"content"`
	CreatedAt time.Time               `json:"created_at"`
}

// FromEntity converts a domain.Notification to NotificationResponse
func FromEntity(n *domain.Notification) *NotificationResponse {
	return &NotificationResponse{
		ID:        n.ID,
		Type:      n.Type,
		Recipient: n.Recipient,
		Subject:   n.Subject,
		Content:   n.Content,
		CreatedAt: n.CreatedAt,
	}
}

// FromEntities converts a slice of domain.Notification to NotificationResponse slice
func FromEntities(notifications []*domain.Notification) []*NotificationResponse {
	responses := make([]*NotificationResponse, len(notifications))
	for i, n := range notifications {
		responses[i] = FromEntity(n)
	}
	return responses
}

// ErrorResponse represents an error response
type ErrorResponse struct {
	Message string `json:"message"`
}