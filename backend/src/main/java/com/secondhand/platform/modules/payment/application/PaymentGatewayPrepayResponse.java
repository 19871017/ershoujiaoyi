package com.secondhand.platform.modules.payment.application;

public record PaymentGatewayPrepayResponse(
        String actionType,
        String payUrl,
        String formHtml,
        String gatewayResponse
) {
}
