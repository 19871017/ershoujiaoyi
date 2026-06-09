package com.secondhand.platform.modules.chat;

public class ClearConversationResponse {
    private Long conversationId;
    private Long clearedSeq;
    private Long lastServerSeq;

    public ClearConversationResponse(Long conversationId, Long clearedSeq, Long lastServerSeq) {
        this.conversationId = conversationId;
        this.clearedSeq = clearedSeq;
        this.lastServerSeq = lastServerSeq;
    }

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getClearedSeq() { return clearedSeq; }
    public void setClearedSeq(Long clearedSeq) { this.clearedSeq = clearedSeq; }
    public Long getLastServerSeq() { return lastServerSeq; }
    public void setLastServerSeq(Long lastServerSeq) { this.lastServerSeq = lastServerSeq; }
}
