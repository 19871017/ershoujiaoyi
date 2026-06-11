package com.secondhand.platform.modules.payment.application;

import java.math.BigDecimal;

public record PaymentGatewayCommand(
        PaymentChannelConfig config,
        String paymentNo,
        String bizType,
        String bizNo,
        Long userId,
        BigDecimal amount,
        String subject,
        String clientIp
) {
}
