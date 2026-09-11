package com.skillswap.app.models;

/**
 * A chat message stored at messages/{conversationId}/{messageId}.
 * conversationId is the two participant uids sorted alphabetically and joined with "_".
 */
public class Message {

    private String messageId;
    private String senderId;
    private String receiverId;
    private String text;
    private long timestamp;

    public Message() {
    }

    public Message(String messageId, String senderId, String receiverId, String text) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.text = text;
        this.timestamp = System.currentTimeMillis();
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
