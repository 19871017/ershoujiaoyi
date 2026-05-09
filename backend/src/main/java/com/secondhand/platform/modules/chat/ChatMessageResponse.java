package com.secondhand.platform.modules.chat;

import java.time.LocalDateTime;

public class ChatMessageResponse {
    private Long conversationId;
    private Long serverSeq;
    private String serverMsgId;
    private String clientMsgId;
    private Long senderId;
    private Long receiverId;
    private String msgType;
    private String contentJson;
    private LocalDateTime createdAt;
    private Boolean deliveredToReceiver;
    private Boolean readByReceiver;

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getServerSeq() { return serverSeq; }
    public void setServerSeq(Long serverSeq) { this.serverSeq = serverSeq; }
    public String getServerMsgId() { return serverMsgId; }
    public void setServerMsgId(String serverMsgId) { this.serverMsgId = serverMsgId; }
    public String getClientMsgId() { return clientMsgId; }
    public void setClientMsgId(String clientMsgId) { this.clientMsgId = clientMsgId; }
    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }
    public String getMsgType() { return msgType; }
    public void setMsgType(String msgType) { this.msgType = msgType; }
    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Boolean getDeliveredToReceiver() { return deliveredToReceiver; }
    public void setDeliveredToReceiver(Boolean deliveredToReceiver) { this.deliveredToReceiver = deliveredToReceiver; }
    public Boolean getReadByReceiver() { return readByReceiver; }
    public void setReadByReceiver(Boolean readByReceiver) { this.readByReceiver = readByReceiver; }
}
