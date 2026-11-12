package com.tixcorner.dss.user;

/**
 * UserEvent — tabel penghubung Pengelola Event dengan event miliknya (tenant isolation, NFR-16).
 */
public class UserEvent {

    private final int userId;
    private final String eventId;

    public UserEvent(int userId, String eventId) {
        this.userId = userId;
        this.eventId = eventId;
    }

    /** true jika pasangan (user, event) ini adalah pasangan yang diizinkan. */
    public boolean isTerotorisasi(int userId, String eventId) {
        return this.userId == userId && this.eventId.equals(eventId);
    }

    public int getUserId() {
        return userId;
    }

    public String getEventId() {
        return eventId;
    }
}
