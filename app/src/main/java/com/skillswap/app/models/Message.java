package com.skillswap.app.models;

import java.util.Objects;

/**
 * A chat message stored at messages/{conversationId}/{messageId}.
 * conversationId is the two participant uids sorted alphabetically and joined with "_".
 *
 * {@code messageId} is deliberately allowed to be {@code null} at construction time:
 * Firebase only allocates the push key once the caller asks for one
 * ({@code DatabaseReference.push().getKey()}), which happens inside
 * {@code ChatRepository.sendMessage} — after the message itself is built. The
 * constructor cannot require it up front without forcing every caller to fetch a key
 * from a live {@code DatabaseReference} before it has anything to send.
 */
public class Message {

    private String messageId;
    private String senderId;
    private String receiverId;
    private String text;
    private long timestamp;

    /** Required by Firebase for deserialization; do not call directly. */
    public Message() {
    }

    /** @param messageId may be {@code null}; see the class doc. */
    public Message(String messageId, String senderId, String receiverId, String text) {
        this.messageId = messageId;
        this.senderId = Objects.requireNonNull(senderId, "senderId");
        this.receiverId = Objects.requireNonNull(receiverId, "receiverId");
        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("A message cannot be sent from a user to themselves");
        }
        this.text = Objects.requireNonNull(text, "text");
        if (text.trim().isEmpty()) {
            throw new IllegalArgumentException("Message text cannot be blank");
        }
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

    /**
     * Identity is the database key. Two freshly-constructed, not-yet-sent messages
     * (both with a {@code null} messageId) are therefore only equal by reference,
     * which is correct: they are not the same message until Firebase assigns each its
     * own key.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message)) return false;
        Message other = (Message) o;
        return messageId != null && Objects.equals(messageId, other.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(messageId);
    }

    @Override
    public String toString() {
        return "Message{messageId='" + messageId + "', senderId='" + senderId + "'}";
    }
}
