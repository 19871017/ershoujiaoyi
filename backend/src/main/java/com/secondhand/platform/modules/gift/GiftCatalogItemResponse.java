package com.secondhand.platform.modules.gift;

import java.math.BigDecimal;

public class GiftCatalogItemResponse {
    private final Long giftId;
    private final String giftCode;
    private final String name;
    private final String icon;
    private final BigDecimal price;
    private final BigDecimal platformRate;

    public GiftCatalogItemResponse(Long giftId, String giftCode, String name, String icon, BigDecimal price, BigDecimal platformRate) {
        this.giftId = giftId;
        this.giftCode = giftCode;
        this.name = name;
        this.icon = icon;
        this.price = price;
        this.platformRate = platformRate;
    }

    public Long getGiftId() { return giftId; }
    public String getGiftCode() { return giftCode; }
    public String getName() { return name; }
    public String getIcon() { return icon; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getPlatformRate() { return platformRate; }
}
