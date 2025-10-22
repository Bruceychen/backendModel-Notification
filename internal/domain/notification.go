package domain

import (
	"time"
)

// Notification represents a notification entity in the system
type Notification struct {
	ID        int64            `gorm:"primaryKey;autoIncrement" json:"id"`
	Type      NotificationType `gorm:"type:varchar(20);not null" json:"type"`
	Recipient string           `gorm:"type:varchar(255);not null" json:"recipient"`
	Subject   string           `gorm:"type:varchar(255)" json:"subject"`
	Content   string           `gorm:"type:text;not null" json:"content"`
	CreatedAt time.Time        `gorm:"column:created_at;autoCreateTime;not null" json:"created_at"`
	Version   int64            `gorm:"column:version;default:0" json:"version"` // Optimistic locking
}

// TableName specifies the table name for GORM
func (Notification) TableName() string {
	return "notifications"
}