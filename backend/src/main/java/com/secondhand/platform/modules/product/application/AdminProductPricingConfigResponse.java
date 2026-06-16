package com.secondhand.platform.modules.product.application;

import java.math.BigDecimal;

public record AdminProductPricingConfigResponse(BigDecimal markupRate, String updatedAt) {
}
