package com.secondhand.platform.modules.gift;

import java.math.BigDecimal;

public class SendGiftResponse {
    private String giftOrderNo;
    private Long giftId;
    private String giftCode;
    private Long receiverId;
    private BigDecimal totalAmount;
    private BigDecimal platformShare;
    private BigDecimal receiverAmount;
    private String debitLedgerNo;
    private String receiverCreditLedgerNo;
    private String status;
    private String createdAt;

    public SendGiftResponse(
            String giftOrderNo,
            Long giftId,
            String giftCode,
            Long receiverId,
            BigDecimal totalAmount,
            BigDecimal platformShare,
            BigDecimal receiverAmount,
            String debitLedgerNo,
            String receiverCreditLedgerNo,
            String status,
            String createdAt
    ) {
        this.giftOrderNo = giftOrderNo;
        this.giftId = giftId;
        this.giftCode = giftCode;
        this.receiverId = receiverId;
        this.totalAmount = totalAmount;
        this.platformShare = platformShare;
        this.receiverAmount = receiverAmount;
        this.debitLedgerNo = debitLedgerNo;
        this.receiverCreditLedgerNo = receiverCreditLedgerNo;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getGiftOrderNo() { return giftOrderNo; }
    public Long getGiftId() { return giftId; }
    public String getGiftCode() { return giftCode; }
    public Long getReceiverId() { return receiverId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getPlatformShare() { return platformShare; }
    public BigDecimal getReceiverAmount() { return receiverAmount; }
    public String getDebitLedgerNo() { return debitLedgerNo; }
    public String getReceiverCreditLedgerNo() { return receiverCreditLedgerNo; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
}
