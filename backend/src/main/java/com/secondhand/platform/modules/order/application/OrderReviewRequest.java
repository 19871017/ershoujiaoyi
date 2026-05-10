package com.secondhand.platform.modules.order.application;

public class OrderReviewRequest {
    private Integer descriptionScore;
    private Integer serviceScore;
    private Integer shippingScore;
    private String content;

    public Integer getDescriptionScore() {
        return descriptionScore;
    }

    public void setDescriptionScore(Integer descriptionScore) {
        this.descriptionScore = descriptionScore;
    }

    public Integer getServiceScore() {
        return serviceScore;
    }

    public void setServiceScore(Integer serviceScore) {
        this.serviceScore = serviceScore;
    }

    public Integer getShippingScore() {
        return shippingScore;
    }

    public void setShippingScore(Integer shippingScore) {
        this.shippingScore = shippingScore;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
