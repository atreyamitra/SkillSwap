package com.skillswap.app.models;

import java.util.Objects;

/**
 * A scheduled session for a swap, stored at sessions/{sessionId}.
 *
 * Status uses the same enum-at-the-boundary pattern as {@link SwapRequest}: the wire
 * format is a plain String field for Firebase's reflection-based mapper, but
 * {@link #getStatus()}/{@link #setStatus} only ever expose {@link SessionStatus}. See
 * {@link SwapRequest}'s class doc for the full rationale.
 */
public class Session {

    private String sessionId;
    private String requestId;
    private String userA; // sender of the original request
    private String userB; // receiver of the original request
    private String dateText;   // e.g. "12 Sep 2026"
    private String timeText;   // e.g. "17:30"
    private long dateTimeMillis; // epoch millis for the scheduled moment, used for reminders
    private String status;
    private long createdAt;

    /** Required by Firebase for deserialization; do not call directly. */
    public Session() {
    }

    public Session(String sessionId, String requestId, String userA, String userB,
                    String dateText, String timeText, long dateTimeMillis) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.userA = Objects.requireNonNull(userA, "userA");
        this.userB = Objects.requireNonNull(userB, "userB");
        if (userA.equals(userB)) {
            throw new IllegalArgumentException("A session must have two distinct participants");
        }
        this.dateText = dateText;
        this.timeText = timeText;
        this.dateTimeMillis = dateTimeMillis;
        this.status = SessionStatus.SCHEDULED.name();
        this.createdAt = System.currentTimeMillis();
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getUserA() { return userA; }
    public void setUserA(String userA) { this.userA = userA; }

    public String getUserB() { return userB; }
    public void setUserB(String userB) { this.userB = userB; }

    public String getDateText() { return dateText; }
    public void setDateText(String dateText) { this.dateText = dateText; }

    public String getTimeText() { return timeText; }
    public void setTimeText(String timeText) { this.timeText = timeText; }

    public long getDateTimeMillis() { return dateTimeMillis; }
    public void setDateTimeMillis(long dateTimeMillis) { this.dateTimeMillis = dateTimeMillis; }

    public SessionStatus getStatus() {
        return status == null ? null : SessionStatus.valueOf(status);
    }

    public void setStatus(SessionStatus status) {
        this.status = Objects.requireNonNull(status, "status").name();
    }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Session)) return false;
        Session other = (Session) o;
        return Objects.equals(sessionId, other.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(sessionId);
    }

    @Override
    public String toString() {
        return "Session{sessionId='" + sessionId + "', status=" + status + '}';
    }
}
