package com.example.demo.enums;

import com.example.demo.exception.InvalidNotificationTypeException;

public enum NotificationMessageType {
    CREATE,
    UPDATE,
    DELETE,
    ;

    public static NotificationMessageType fromString(String type) {
        for (NotificationMessageType notificationType : NotificationMessageType.values()) {
            if (notificationType.name().equalsIgnoreCase(type)) {
                return notificationType;
            }
        }
        throw new InvalidNotificationTypeException("unsupported type: " + type);
    }
}
