package com.secondhand.platform.modules.chat.domain;

import com.secondhand.platform.shared.kernel.BaseEntity;

public class ChatMessage extends BaseEntity {
    private Long conversationId;
    private Long serverSeq;
    private String clientMsgId;
    private String serverMsgId;
    private Long senderId;
    private Long receiverId;
    private String msgType;
    private String contentJson;

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getServerSeq() { return serverSeq; }
    public void setServerSeq(Long serverSeq) { this.serverSeq = serverSeq; }
    public String getClientMsgId() { return clientMsgId; }
    public void setClientMsgId(String clientMsgId) { this.clientMsgId = clientMsgId; }
    public String getServerMsgId() { return serverMsgId; }
    public void setServerMsgId(String serverMsgId) { this.serverMsgId = serverMsgId; }
    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }
    public String getMsgType() { return msgType; }
    public void setMsgType(String msgType) { this.msgType = msgType; }
    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
}
