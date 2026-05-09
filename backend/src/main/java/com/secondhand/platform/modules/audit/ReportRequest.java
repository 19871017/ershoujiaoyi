package com.secondhand.platform.modules.audit;

public class ReportRequest {
    private String targetType;
    private String targetId;
    private String reason;
    private String description;
    private java.util.List<String> evidenceUrls;

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public java.util.List<String> getEvidenceUrls() { return evidenceUrls; }
    public void setEvidenceUrls(java.util.List<String> evidenceUrls) { this.evidenceUrls = evidenceUrls; }
}
