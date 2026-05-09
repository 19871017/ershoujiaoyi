package com.secondhand.platform.modules.order.application;

public class CreateOrderRequest {
    private Long goodsId;
    private Boolean acceptedTradeRule;

    public Long getGoodsId() { return goodsId; }
    public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }
    public Boolean getAcceptedTradeRule() { return acceptedTradeRule; }
    public void setAcceptedTradeRule(Boolean acceptedTradeRule) { this.acceptedTradeRule = acceptedTradeRule; }
}
