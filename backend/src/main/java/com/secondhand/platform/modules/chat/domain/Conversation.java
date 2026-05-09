package com.secondhand.platform.modules.chat.domain;

import com.secondhand.platform.shared.kernel.BaseEntity;

public class Conversation extends BaseEntity {
    private Long ownerUserId;
    private Long peerUserId;
    private String conversationType;
    private Long lastServerSeq;
    private String lastMessageSummary;

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Long getPeerUserId() { return peerUserId; }
    public void setPeerUserId(Long peerUserId) { this.peerUserId = peerUserId; }
    public String getConversationType() { return conversationType; }
    public void setConversationType(String conversationType) { this.conversationType = conversationType; }
    public Long getLastServerSeq() { return lastServerSeq; }
    public void setLastServerSeq(Long lastServerSeq) { this.lastServerSeq = lastServerSeq; }
    public String getLastMessageSummary() { return lastMessageSummary; }
    public void setLastMessageSummary(String lastMessageSummary) { this.lastMessageSummary = lastMessageSummary; }
}
