package com.skillswap.app.models;

/**
 * A skill-swap request stored at swapRequests/{requestId}.
 * status is one of: PENDING, ACCEPTED, REJECTED, COMPLETED (see Status constants).
 */
public class SwapRequest {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_COMPLETED = "COMPLETED";

    private String requestId;
    private String senderId;
    private String receiverId;
    private String senderName;
    private String receiverName;
    private String offeredSkill;
    private String requestedSkill;
    private String status;
    private long timestamp;

    public SwapRequest() {
    }

    public SwapRequest(String requestId, String senderId, String receiverId, String senderName,
                        String receiverName, String offeredSkill, String requestedSkill) {
        this.requestId = requestId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.senderName = senderName;
        this.receiverName = receiverName;
        this.offeredSkill = offeredSkill;
        this.requestedSkill = requestedSkill;
        this.status = STATUS_PENDING;
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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
