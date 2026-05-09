package com.secondhand.platform.modules.gift;

import java.math.BigDecimal;

public class ReceivedGiftItemResponse {
    private final String giftOrderNo;
    private final Long senderId;
    private final Long giftId;
    private final String giftCode;
    private final String giftName;
    private final String giftIcon;
    private final Integer quantity;
    private final BigDecimal totalAmount;
    private final BigDecimal platformShare;
    private final BigDecimal receiverAmount;
    private final String receiverCreditLedgerNo;
    private final String status;
    private final String createdAt;

    public ReceivedGiftItemResponse(
            String giftOrderNo,
            Long senderId,
            Long giftId,
            String giftCode,
            String giftName,
            String giftIcon,
            Integer quantity,
            BigDecimal totalAmount,
            BigDecimal platformShare,
            BigDecimal receiverAmount,
            String receiverCreditLedgerNo,
            String status,
            String createdAt
    ) {
        this.giftOrderNo = giftOrderNo;
        this.senderId = senderId;
        this.giftId = giftId;
        this.giftCode = giftCode;
        this.giftName = giftName;
        this.giftIcon = giftIcon;
        this.quantity = quantity;
        this.totalAmount = totalAmount;
        this.platformShare = platformShare;
        this.receiverAmount = receiverAmount;
        this.receiverCreditLedgerNo = receiverCreditLedgerNo;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getGiftOrderNo() { return giftOrderNo; }
    public Long getSenderId() { return senderId; }
    public Long getGiftId() { return giftId; }
    public String getGiftCode() { return giftCode; }
    public String getGiftName() { return giftName; }
    public String getGiftIcon() { return giftIcon; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getPlatformShare() { return platformShare; }
    public BigDecimal getReceiverAmount() { return receiverAmount; }
    public String getReceiverCreditLedgerNo() { return receiverCreditLedgerNo; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
}
