package com.secondhand.platform.modules.chat;

public class RevokeMessageResponse {
    private Long conversationId;
    private Long serverSeq;
    private String serverMsgId;
    private Boolean revoked;

    public RevokeMessageResponse(Long conversationId, Long serverSeq, String serverMsgId, Boolean revoked) {
        this.conversationId = conversationId;
        this.serverSeq = serverSeq;
        this.serverMsgId = serverMsgId;
        this.revoked = revoked;
    }

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getServerSeq() { return serverSeq; }
    public void setServerSeq(Long serverSeq) { this.serverSeq = serverSeq; }
    public String getServerMsgId() { return serverMsgId; }
    public void setServerMsgId(String serverMsgId) { this.serverMsgId = serverMsgId; }
    public Boolean getRevoked() { return revoked; }
    public void setRevoked(Boolean revoked) { this.revoked = revoked; }
}
