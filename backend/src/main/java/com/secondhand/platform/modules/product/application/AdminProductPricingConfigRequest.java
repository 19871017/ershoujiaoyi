package com.secondhand.platform.modules.product.application;

import java.math.BigDecimal;

public record AdminProductPricingConfigRequest(BigDecimal markupRate) {
}
