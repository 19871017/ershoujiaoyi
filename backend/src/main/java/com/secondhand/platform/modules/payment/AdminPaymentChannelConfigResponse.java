package com.secondhand.platform.modules.payment;

public record AdminPaymentChannelConfigResponse(
        String channel,
        boolean enabled,
        boolean sandbox,
        boolean configured,
        String appId,
        String merchantId,
        String gatewayUrl,
        String notifyUrl,
        String returnUrl,
        boolean merchantPrivateKeyConfigured,
        boolean alipayPublicKeyConfigured,
        String merchantSerialNo,
        boolean apiV3KeyConfigured,
        boolean wechatPayPublicKeyConfigured,
        String updatedAt
) {
}
