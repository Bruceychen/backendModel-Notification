package com.example.demo.model;

import com.example.demo.exception.InvalidNotificationTypeException;

public enum NotificationType {
    EMAIL,
    SMS;

    public static NotificationType fromString(String type) {
        for (NotificationType notificationType : NotificationType.values()) {
            if (notificationType.name().equalsIgnoreCase(type)) {
                return notificationType;
            }
        }
        throw new InvalidNotificationTypeException("unsupported type: " + type);
    }
}
