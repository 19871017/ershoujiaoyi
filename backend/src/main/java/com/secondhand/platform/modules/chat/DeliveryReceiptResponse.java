package com.secondhand.platform.modules.chat;

public class DeliveryReceiptResponse {
    private Long conversationId;
    private Long deliveredSeq;
    private Long readSeq;
    private Long lastServerSeq;
    private Long unreadCount;

    public DeliveryReceiptResponse(Long conversationId, Long deliveredSeq, Long readSeq, Long lastServerSeq, Long unreadCount) {
        this.conversationId = conversationId;
        this.deliveredSeq = deliveredSeq;
        this.readSeq = readSeq;
        this.lastServerSeq = lastServerSeq;
        this.unreadCount = unreadCount;
    }

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getDeliveredSeq() { return deliveredSeq; }
    public void setDeliveredSeq(Long deliveredSeq) { this.deliveredSeq = deliveredSeq; }
    public Long getReadSeq() { return readSeq; }
    public void setReadSeq(Long readSeq) { this.readSeq = readSeq; }
    public Long getLastServerSeq() { return lastServerSeq; }
    public void setLastServerSeq(Long lastServerSeq) { this.lastServerSeq = lastServerSeq; }
    public Long getUnreadCount() { return unreadCount; }
    public void setUnreadCount(Long unreadCount) { this.unreadCount = unreadCount; }
}
