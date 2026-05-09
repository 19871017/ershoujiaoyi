package com.secondhand.platform.shared.events;

import java.time.LocalDateTime;

public class EventEnvelope<T> {
    private final String eventName;
    private final String eventId;
    private final T payload;
    private final LocalDateTime occurredAt;

    public EventEnvelope(String eventName, String eventId, T payload, LocalDateTime occurredAt) {
        this.eventName = eventName;
        this.eventId = eventId;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }

    public String getEventName() {
        return eventName;
    }

    public String getEventId() {
        return eventId;
    }

    public T getPayload() {
        return payload;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
