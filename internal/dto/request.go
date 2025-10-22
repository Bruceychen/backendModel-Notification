package dto

import "github.com/brucechen/notification-service/internal/domain"

// NotificationRequest represents the request payload for creating a notification
type NotificationRequest struct {
	Type      domain.NotificationType `json:"type" binding:"required"`
	Recipient string                  `json:"recipient" binding:"required"`
	Subject   string                  `json:"subject"`
	Content   string                  `json:"content" binding:"required"`
}

// UpdateNotificationRequest represents the request payload for updating a notification
type UpdateNotificationRequest struct {
	Subject string `json:"subject"`
	Content string `json:"content" binding:"required"`
}