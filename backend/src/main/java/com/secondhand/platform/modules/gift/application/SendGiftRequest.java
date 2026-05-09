package com.secondhand.platform.modules.gift.application;

public class SendGiftRequest {
    /**
     * Deprecated/ignored by backend: sender is always resolved from CurrentUserResolver.
     */
    private Long senderId;
    private Long receiverId;
    private String giftCode;
    private Long giftId;
    private Integer quantity;
    private String sceneType;
    private Long sceneId;
    private String clientGiftId;
    private String requestNo;

    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }
    public String getGiftCode() { return giftCode; }
    public void setGiftCode(String giftCode) { this.giftCode = giftCode; }
    public Long getGiftId() { return giftId; }
    public void setGiftId(Long giftId) { this.giftId = giftId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getSceneType() { return sceneType; }
    public void setSceneType(String sceneType) { this.sceneType = sceneType; }
    public Long getSceneId() { return sceneId; }
    public void setSceneId(Long sceneId) { this.sceneId = sceneId; }
    public String getClientGiftId() { return clientGiftId; }
    public void setClientGiftId(String clientGiftId) { this.clientGiftId = clientGiftId; }
    public String getRequestNo() { return requestNo; }
    public void setRequestNo(String requestNo) { this.requestNo = requestNo; }
}
