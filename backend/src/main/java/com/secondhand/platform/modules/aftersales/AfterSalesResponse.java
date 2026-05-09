package com.secondhand.platform.modules.aftersales;

import java.math.BigDecimal;
import java.util.List;

public class AfterSalesResponse {
    private String afterSalesNo;
    private String orderNo;
    private Long applicantId;
    private String afterSalesType;
    private BigDecimal refundAmount;
    private String reason;
    private String description;
    private List<String> evidenceUrls;
    private String status;
    private String createdAt;

    public AfterSalesResponse(String afterSalesNo, String orderNo, Long applicantId, String afterSalesType,
                              BigDecimal refundAmount, String reason, String description, List<String> evidenceUrls,
                              String status, String createdAt) {
        this.afterSalesNo = afterSalesNo;
        this.orderNo = orderNo;
        this.applicantId = applicantId;
        this.afterSalesType = afterSalesType;
        this.refundAmount = refundAmount;
        this.reason = reason;
        this.description = description;
        this.evidenceUrls = evidenceUrls;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getAfterSalesNo() { return afterSalesNo; }
    public String getOrderNo() { return orderNo; }
    public Long getApplicantId() { return applicantId; }
    public String getAfterSalesType() { return afterSalesType; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public String getReason() { return reason; }
    public String getDescription() { return description; }
    public List<String> getEvidenceUrls() { return evidenceUrls; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
}
