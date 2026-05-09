package com.secondhand.platform.modules.chat;

public class ReadConversationResponse {
    private Long conversationId;
    private Long readSeq;
    private Long deliveredSeq;
    private Long lastServerSeq;
    private Long unreadCount;

    public ReadConversationResponse(Long conversationId, Long readSeq, Long deliveredSeq, Long lastServerSeq, Long unreadCount) {
        this.conversationId = conversationId;
        this.readSeq = readSeq;
        this.deliveredSeq = deliveredSeq;
        this.lastServerSeq = lastServerSeq;
        this.unreadCount = unreadCount;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Long getReadSeq() {
        return readSeq;
    }

    public void setReadSeq(Long readSeq) {
        this.readSeq = readSeq;
    }

    public Long getDeliveredSeq() {
        return deliveredSeq;
    }

    public void setDeliveredSeq(Long deliveredSeq) {
        this.deliveredSeq = deliveredSeq;
    }

    public Long getLastServerSeq() {
        return lastServerSeq;
    }

    public void setLastServerSeq(Long lastServerSeq) {
        this.lastServerSeq = lastServerSeq;
    }

    public Long getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(Long unreadCount) {
        this.unreadCount = unreadCount;
    }
}
