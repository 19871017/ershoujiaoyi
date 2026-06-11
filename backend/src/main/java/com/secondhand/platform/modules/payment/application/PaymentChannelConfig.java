package com.secondhand.platform.modules.payment.application;

public record PaymentChannelConfig(
        String channel,
        boolean enabled,
        boolean sandbox,
        String appId,
        String merchantId,
        String gatewayUrl,
        String notifyUrl,
        String returnUrl,
        String merchantPrivateKey,
        String alipayPublicKey,
        String merchantSerialNo,
        String apiV3Key,
        String wechatPayPublicKey,
        String updatedAt
) {
    public boolean configured() {
        if ("ALIPAY".equals(channel)) {
            return enabled
                    && notBlank(appId)
                    && notBlank(merchantPrivateKey)
                    && notBlank(alipayPublicKey)
                    && notBlank(notifyUrl);
        }
        if ("WECHAT".equals(channel)) {
            return enabled
                    && notBlank(appId)
                    && notBlank(merchantId)
                    && notBlank(merchantSerialNo)
                    && notBlank(merchantPrivateKey)
                    && notBlank(apiV3Key)
                    && notBlank(wechatPayPublicKey)
                    && notBlank(notifyUrl);
        }
        return false;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
