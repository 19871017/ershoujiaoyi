package com.secondhand.platform.modules.order;

public class ShipOrderResponse {
    private String orderNo;
    private String status;
    private String shippingType;
    private String shippingCompany;
    private String trackingNo;
    private String remark;
    private String shippedAt;

    public ShipOrderResponse(String orderNo, String status, String shippingType, String shippingCompany, String trackingNo, String remark, String shippedAt) {
        this.orderNo = orderNo;
        this.status = status;
        this.shippingType = shippingType;
        this.shippingCompany = shippingCompany;
        this.trackingNo = trackingNo;
        this.remark = remark;
        this.shippedAt = shippedAt;
    }

    public String getOrderNo() { return orderNo; }
    public String getStatus() { return status; }
    public String getShippingType() { return shippingType; }
    public String getShippingCompany() { return shippingCompany; }
    public String getTrackingNo() { return trackingNo; }
    public String getRemark() { return remark; }
    public String getShippedAt() { return shippedAt; }
}
