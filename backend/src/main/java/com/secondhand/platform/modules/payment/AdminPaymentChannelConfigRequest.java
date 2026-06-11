package com.secondhand.platform.modules.payment;

public class AdminPaymentChannelConfigRequest {
    private Boolean enabled;
    private Boolean sandbox;
    private String appId;
    private String merchantId;
    private String gatewayUrl;
    private String notifyUrl;
    private String returnUrl;
    private String merchantPrivateKey;
    private String alipayPublicKey;
    private String merchantSerialNo;
    private String apiV3Key;
    private String wechatPayPublicKey;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getSandbox() { return sandbox; }
    public void setSandbox(Boolean sandbox) { this.sandbox = sandbox; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public String getGatewayUrl() { return gatewayUrl; }
    public void setGatewayUrl(String gatewayUrl) { this.gatewayUrl = gatewayUrl; }
    public String getNotifyUrl() { return notifyUrl; }
    public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
    public String getMerchantPrivateKey() { return merchantPrivateKey; }
    public void setMerchantPrivateKey(String merchantPrivateKey) { this.merchantPrivateKey = merchantPrivateKey; }
    public String getAlipayPublicKey() { return alipayPublicKey; }
    public void setAlipayPublicKey(String alipayPublicKey) { this.alipayPublicKey = alipayPublicKey; }
    public String getMerchantSerialNo() { return merchantSerialNo; }
    public void setMerchantSerialNo(String merchantSerialNo) { this.merchantSerialNo = merchantSerialNo; }
    public String getApiV3Key() { return apiV3Key; }
    public void setApiV3Key(String apiV3Key) { this.apiV3Key = apiV3Key; }
    public String getWechatPayPublicKey() { return wechatPayPublicKey; }
    public void setWechatPayPublicKey(String wechatPayPublicKey) { this.wechatPayPublicKey = wechatPayPublicKey; }
}
