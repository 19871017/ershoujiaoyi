package com.secondhand.platform.modules.chat;

import java.time.LocalDateTime;

public class ConversationListItemResponse {
    private Long conversationId;
    private Long peerUserId;
    private String lastMessageSummary;
    private Long lastServerSeq;
    private Long deliveredSeq;
    private Long readSeq;
    private Long unreadCount;
    private LocalDateTime updatedAt;

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Long getPeerUserId() {
        return peerUserId;
    }

    public void setPeerUserId(Long peerUserId) {
        this.peerUserId = peerUserId;
    }

    public String getLastMessageSummary() {
        return lastMessageSummary;
    }

    public void setLastMessageSummary(String lastMessageSummary) {
        this.lastMessageSummary = lastMessageSummary;
    }

    public Long getLastServerSeq() {
        return lastServerSeq;
    }

    public void setLastServerSeq(Long lastServerSeq) {
        this.lastServerSeq = lastServerSeq;
    }

    public Long getDeliveredSeq() {
        return deliveredSeq;
    }

    public void setDeliveredSeq(Long deliveredSeq) {
        this.deliveredSeq = deliveredSeq;
    }

    public Long getReadSeq() {
        return readSeq;
    }

    public void setReadSeq(Long readSeq) {
        this.readSeq = readSeq;
    }

    public Long getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(Long unreadCount) {
        this.unreadCount = unreadCount;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
