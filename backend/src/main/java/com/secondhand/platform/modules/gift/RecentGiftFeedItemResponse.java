package com.secondhand.platform.modules.gift;

import java.math.BigDecimal;

public class RecentGiftFeedItemResponse {
    private final String giftOrderNo;
    private final Long senderId;
    private final String senderName;
    private final Long receiverId;
    private final String receiverName;
    private final Long giftId;
    private final String giftName;
    private final String giftIcon;
    private final Integer quantity;
    private final BigDecimal totalAmount;
    private final String createdAt;

    public RecentGiftFeedItemResponse(
            String giftOrderNo,
            Long senderId,
            String senderName,
            Long receiverId,
            String receiverName,
            Long giftId,
            String giftName,
            String giftIcon,
            Integer quantity,
            BigDecimal totalAmount,
            String createdAt
    ) {
        this.giftOrderNo = giftOrderNo;
        this.senderId = senderId;
        this.senderName = senderName;
        this.receiverId = receiverId;
        this.receiverName = receiverName;
        this.giftId = giftId;
        this.giftName = giftName;
        this.giftIcon = giftIcon;
        this.quantity = quantity;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
    }

    public String getGiftOrderNo() { return giftOrderNo; }
    public Long getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public Long getReceiverId() { return receiverId; }
    public String getReceiverName() { return receiverName; }
    public Long getGiftId() { return giftId; }
    public String getGiftName() { return giftName; }
    public String getGiftIcon() { return giftIcon; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCreatedAt() { return createdAt; }
}
