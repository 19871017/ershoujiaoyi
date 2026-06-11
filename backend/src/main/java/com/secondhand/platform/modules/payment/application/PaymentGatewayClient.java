package com.secondhand.platform.modules.payment.application;

public interface PaymentGatewayClient {
    PaymentGatewayPrepayResponse createPrepay(PaymentGatewayCommand command);
}
