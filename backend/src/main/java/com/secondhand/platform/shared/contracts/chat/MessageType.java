package com.secondhand.platform.shared.contracts.chat;

public enum MessageType {
    TEXT,
    IMAGE;

    public static MessageType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("message type required");
        }
        for (MessageType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("message type must be TEXT or IMAGE in MVP");
    }
}
