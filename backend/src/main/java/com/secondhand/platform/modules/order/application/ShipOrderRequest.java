package com.secondhand.platform.modules.order.application;

public class ShipOrderRequest {
    private String shippingType;
    private String shippingCompany;
    private String trackingNo;
    private String remark;

    public String getShippingType() { return shippingType; }
    public void setShippingType(String shippingType) { this.shippingType = shippingType; }
    public String getShippingCompany() { return shippingCompany; }
    public void setShippingCompany(String shippingCompany) { this.shippingCompany = shippingCompany; }
    public String getTrackingNo() { return trackingNo; }
    public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
