package domain

import (
	"database/sql/driver"
	"errors"
	"fmt"
)

// NotificationType represents the type of notification (EMAIL or SMS)
type NotificationType string

const (
	NotificationTypeEmail NotificationType = "EMAIL"
	NotificationTypeSMS   NotificationType = "SMS"
)

// Valid notification types
var validNotificationTypes = map[NotificationType]bool{
	NotificationTypeEmail: true,
	NotificationTypeSMS:   true,
}

// FromString converts a string to NotificationType
func (t NotificationType) FromString(s string) (NotificationType, error) {
	nt := NotificationType(s)
	if !validNotificationTypes[nt] {
		return "", fmt.Errorf("unsupported type: %s", s)
	}
	return nt, nil
}

// IsValid checks if the notification type is valid
func (t NotificationType) IsValid() bool {
	return validNotificationTypes[t]
}

// Scan implements sql.Scanner interface for database deserialization
func (t *NotificationType) Scan(value interface{}) error {
	if value == nil {
		return errors.New("notification type cannot be null")
	}

	str, ok := value.([]byte)
	if !ok {
		return errors.New("failed to scan NotificationType")
	}

	*t = NotificationType(str)
	return nil
}

// Value implements driver.Valuer interface for database serialization
func (t NotificationType) Value() (driver.Value, error) {
	return string(t), nil
}

// NotificationMessageType represents the type of message event (CREATE, UPDATE, DELETE)
type NotificationMessageType string

const (
	MessageTypeCreate NotificationMessageType = "CREATE"
	MessageTypeUpdate NotificationMessageType = "UPDATE"
	MessageTypeDelete NotificationMessageType = "DELETE"
)

// Valid message types
var validMessageTypes = map[NotificationMessageType]bool{
	MessageTypeCreate: true,
	MessageTypeUpdate: true,
	MessageTypeDelete: true,
}

// FromString converts a string to NotificationMessageType
func (m NotificationMessageType) FromString(s string) (NotificationMessageType, error) {
	mt := NotificationMessageType(s)
	if !validMessageTypes[mt] {
		return "", fmt.Errorf("unsupported type: %s", s)
	}
	return mt, nil
}

// IsValid checks if the message type is valid
func (m NotificationMessageType) IsValid() bool {
	return validMessageTypes[m]
}