package com.secondhand.platform.modules.chat.application;

public class CreateConversationCommand {
    private Long ownerUserId;
    private Long peerUserId;
    private String relatedBizType;
    private Long relatedBizId;

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Long getPeerUserId() { return peerUserId; }
    public void setPeerUserId(Long peerUserId) { this.peerUserId = peerUserId; }
    public String getRelatedBizType() { return relatedBizType; }
    public void setRelatedBizType(String relatedBizType) { this.relatedBizType = relatedBizType; }
    public Long getRelatedBizId() { return relatedBizId; }
    public void setRelatedBizId(Long relatedBizId) { this.relatedBizId = relatedBizId; }
}
