package com.secondhand.platform.modules.payment;

import java.math.BigDecimal;

public record PaymentIntentResponse(
        String paymentNo,
        String bizType,
        String bizNo,
        BigDecimal amount,
        String channel,
        String status,
        String actionType,
        String payUrl,
        String formHtml,
        String message
) {
}
