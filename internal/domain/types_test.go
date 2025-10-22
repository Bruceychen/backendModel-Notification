package domain

import (
	"testing"
)

func TestNotificationType_IsValid(t *testing.T) {
	tests := []struct {
		name     string
		typeName NotificationType
		want     bool
	}{
		{"Valid EMAIL", NotificationTypeEmail, true},
		{"Valid SMS", NotificationTypeSMS, true},
		{"Invalid type", NotificationType("INVALID"), false},
		{"Empty type", NotificationType(""), false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := tt.typeName.IsValid(); got != tt.want {
				t.Errorf("NotificationType.IsValid() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestNotificationMessageType_IsValid(t *testing.T) {
	tests := []struct {
		name        string
		messageType NotificationMessageType
		want        bool
	}{
		{"Valid CREATE", MessageTypeCreate, true},
		{"Valid UPDATE", MessageTypeUpdate, true},
		{"Valid DELETE", MessageTypeDelete, true},
		{"Invalid type", NotificationMessageType("INVALID"), false},
		{"Empty type", NotificationMessageType(""), false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := tt.messageType.IsValid(); got != tt.want {
				t.Errorf("NotificationMessageType.IsValid() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestNotificationType_FromString(t *testing.T) {
	tests := []struct {
		name    string
		input   string
		want    NotificationType
		wantErr bool
	}{
		{"Valid EMAIL", "EMAIL", NotificationTypeEmail, false},
		{"Valid SMS", "SMS", NotificationTypeSMS, false},
		{"Invalid type", "INVALID", "", true},
		{"Empty string", "", "", true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var nt NotificationType
			got, err := nt.FromString(tt.input)
			if (err != nil) != tt.wantErr {
				t.Errorf("NotificationType.FromString() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if got != tt.want {
				t.Errorf("NotificationType.FromString() = %v, want %v", got, tt.want)
			}
		})
	}
}