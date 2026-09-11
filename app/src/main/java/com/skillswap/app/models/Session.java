package com.skillswap.app.models;

/**
 * A scheduled session for a swap, stored at sessions/{sessionId}.
 */
public class Session {

    public static final String STATUS_SCHEDULED = "SCHEDULED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private String sessionId;
    private String requestId;
    private String userA; // sender of the original request
    private String userB; // receiver of the original request
    private String dateText;   // e.g. "12 Sep 2026"
    private String timeText;   // e.g. "17:30"
    private long dateTimeMillis; // epoch millis for the scheduled moment, used for reminders
    private String status;
    private long createdAt;

    public Session() {
    }

    public Session(String sessionId, String requestId, String userA, String userB,
                    String dateText, String timeText, long dateTimeMillis) {
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.userA = userA;
        this.userB = userB;
        this.dateText = dateText;
        this.timeText = timeText;
        this.dateTimeMillis = dateTimeMillis;
        this.status = STATUS_SCHEDULED;
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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
