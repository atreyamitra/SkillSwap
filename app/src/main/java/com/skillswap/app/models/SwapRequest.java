package com.skillswap.app.models;

import java.util.Objects;

/**
 * A skill-swap request stored at swapRequests/{requestId}.
 *
 * The wire format for status is still a plain String (Firebase's POJO mapper
 * populates fields by reflection using whatever type they're declared as, and a raw
 * String is the simplest, most defensively-storable representation) — but the public
 * API only ever hands out and accepts a {@link RequestStatus}. Converting at the
 * {@link #getStatus()}/{@link #setStatus} boundary means every caller in the app works
 * with a type-safe, exhaustively-switchable enum while the on-disk shape never changes.
 *
 * Firebase requires the no-arg constructor for deserialization; it sets fields
 * directly via reflection afterwards, bypassing every other constructor (and its
 * validation) entirely. The validating constructor below therefore only protects
 * objects the app itself builds (e.g. when sending a new request) — not ones read back
 * from the database. That's a known, accepted gap for a demo app; see
 * docs/ENGINEERING_DECISIONS.md.
 */
public class SwapRequest {

    private String requestId;
    private String senderId;
    private String receiverId;
    private String senderName;
    private String receiverName;
    private String offeredSkill;
    private String requestedSkill;
    private String status;
    private long timestamp;

    /** Required by Firebase for deserialization; do not call directly. */
    public SwapRequest() {
    }

    public SwapRequest(String requestId, String senderId, String receiverId, String senderName,
                        String receiverName, String offeredSkill, String requestedSkill) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.senderId = Objects.requireNonNull(senderId, "senderId");
        this.receiverId = Objects.requireNonNull(receiverId, "receiverId");
        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("A user cannot send a swap request to themselves");
        }
        this.senderName = senderName;
        this.receiverName = receiverName;
        this.offeredSkill = Objects.requireNonNull(offeredSkill, "offeredSkill");
        this.requestedSkill = Objects.requireNonNull(requestedSkill, "requestedSkill");
        this.status = RequestStatus.PENDING.name();
        this.timestamp = System.currentTimeMillis();
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public String getOfferedSkill() { return offeredSkill; }
    public void setOfferedSkill(String offeredSkill) { this.offeredSkill = offeredSkill; }

    public String getRequestedSkill() { return requestedSkill; }
    public void setRequestedSkill(String requestedSkill) { this.requestedSkill = requestedSkill; }

    /** @return the current status, or {@code null} if this object was never populated. */
    public RequestStatus getStatus() {
        return status == null ? null : RequestStatus.valueOf(status);
    }

    public void setStatus(RequestStatus status) {
        this.status = Objects.requireNonNull(status, "status").name();
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    /**
     * Identity is the database key: two {@code SwapRequest} instances represent "the
     * same request" exactly when they have the same {@code requestId}, regardless of
     * whether one is a stale in-memory copy of a request whose other fields have since
     * changed server-side.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SwapRequest)) return false;
        SwapRequest other = (SwapRequest) o;
        return Objects.equals(requestId, other.requestId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(requestId);
    }

    @Override
    public String toString() {
        return "SwapRequest{requestId='" + requestId + "', status=" + status + '}';
    }
}
