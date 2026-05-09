package com.secondhand.platform.modules.aftersales.application;

import java.math.BigDecimal;
import java.util.List;

public class CreateAfterSalesRequest {
    private String orderNo;
    private String afterSalesType;
    private BigDecimal refundAmount;
    private String reason;
    private String description;
    private List<String> evidenceUrls;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getAfterSalesType() { return afterSalesType; }
    public void setAfterSalesType(String afterSalesType) { this.afterSalesType = afterSalesType; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getEvidenceUrls() { return evidenceUrls; }
    public void setEvidenceUrls(List<String> evidenceUrls) { this.evidenceUrls = evidenceUrls; }
}
