package com.secondhand.platform.modules.payment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.secondhand.platform.modules.payment.AdminPaymentChannelConfigRequest;
import com.secondhand.platform.modules.payment.PaymentIntentRequest;
import com.secondhand.platform.modules.payment.PaymentIntentResponse;
import com.secondhand.platform.modules.wallet_ledger.application.WalletLedgerService;
import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class PaymentApplicationServiceTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private WalletLedgerService walletLedgerService;
    private PaymentApplicationService service;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        walletLedgerService = new WalletLedgerService(jdbcTemplate);
        service = new PaymentApplicationService(walletLedgerService, jdbcTemplate);
    }

    @Test
    void createRechargeShouldPersistPendingOrder() {
        RechargeResponse response = service.createRecharge(1L, request("66.60", "wechat"));

        assertEquals("PENDING", response.status());
        assertEquals("WECHAT", response.channel());
        assertEquals(new BigDecimal("66.60"), response.amount());
        assertEquals(1, countPayment(response.rechargeNo()));
        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "SELECT status FROM payment_recharge_order WHERE recharge_no = ?", String.class, response.rechargeNo()));
    }

    @Test
    void simulateSuccessShouldPersistPaidStatusAndReplayAfterServiceRecreation() {
        RechargeResponse pending = service.createRecharge(1L, request("88.00", "alipay"));

        RechargeResponse paid = service.simulateRechargeSuccess(1L, pending.rechargeNo());
        PaymentApplicationService reloaded = new PaymentApplicationService(walletLedgerService, jdbcTemplate);
        RechargeResponse replay = reloaded.simulateRechargeSuccess(1L, pending.rechargeNo());

        assertEquals("PAID", paid.status());
        assertNotNull(paid.ledgerNo());
        assertEquals(paid.ledgerNo(), replay.ledgerNo());
        assertEquals(new BigDecimal("88.00"), replay.balanceAfter());
        assertEquals(1, walletLedgerService.listLedger(1L).size());
        assertEquals("PAID", jdbcTemplate.queryForObject(
                "SELECT status FROM payment_recharge_order WHERE recharge_no = ?", String.class, pending.rechargeNo()));
        assertEquals(paid.ledgerNo(), jdbcTemplate.queryForObject(
                "SELECT ledger_no FROM payment_recharge_order WHERE recharge_no = ?", String.class, pending.rechargeNo()));
    }

    @Test
    void simulateSuccessShouldRejectOtherUser() {
        RechargeResponse pending = service.createRecharge(1L, request("20.00", "wechat"));

        assertThrows(IllegalArgumentException.class, () -> service.simulateRechargeSuccess(2L, pending.rechargeNo()));
    }

    @Test
    void createRechargeShouldRejectInvalidAmountAndChannel() {
        assertThrows(IllegalArgumentException.class, () -> service.createRecharge(1L, request("0.00", "wechat")));
        assertThrows(IllegalArgumentException.class, () -> service.createRecharge(1L, request("1.234", "wechat")));
        assertThrows(IllegalArgumentException.class, () -> service.createRecharge(1L, request("1.00", "bank")));
    }

    @Test
    void createPaymentIntentShouldFailClosedWhenChannelIsNotConfigured() {
        RechargeResponse recharge = service.createRecharge(1L, request("12.00", "alipay"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.createPaymentIntent(1L, intent("RECHARGE", recharge.rechargeNo(), "ALIPAY"), "127.0.0.1"));

        assertEquals("payment channel not configured", error.getMessage());
    }

    @Test
    void adminConfiguredChannelCanCreateGatewayPaymentIntentWithoutEchoingSecrets() {
        PaymentApplicationService configured = new PaymentApplicationService(walletLedgerService, jdbcTemplate, null,
                command -> new PaymentGatewayPrepayResponse("FORM", null, "<form></form>", "{\"ok\":true}"));
        AdminPaymentChannelConfigRequest config = new AdminPaymentChannelConfigRequest();
        config.setEnabled(true);
        config.setAppId("alipay-app-1");
        config.setMerchantId("partner-1");
        config.setNotifyUrl("https://old.tiklxd09.club/api/payments/notify/alipay");
        KeyPair keyPair = rsaKeyPair();
        config.setMerchantPrivateKey(privateKeyPem(keyPair));
        config.setAlipayPublicKey(publicKeyPem(keyPair));

        var saved = configured.adminUpdateChannelConfig("ALIPAY", config);
        RechargeResponse recharge = configured.createRecharge(1L, request("18.00", "alipay"));
        PaymentIntentResponse intent = configured.createPaymentIntent(1L, intent("RECHARGE", recharge.rechargeNo(), "ALIPAY"), "127.0.0.1");

        assertEquals("ALIPAY", saved.channel());
        assertEquals(true, saved.configured());
        assertEquals(true, saved.merchantPrivateKeyConfigured());
        assertEquals(true, saved.alipayPublicKeyConfigured());
        assertEquals("FORM", intent.actionType());
        assertEquals(recharge.rechargeNo(), intent.bizNo());
        assertNotNull(intent.paymentNo());
    }

    @Test
    void adminEnabledChannelShouldRejectInvalidAlipayKeys() {
        AdminPaymentChannelConfigRequest config = new AdminPaymentChannelConfigRequest();
        config.setEnabled(true);
        config.setAppId("alipay-app-1");
        config.setNotifyUrl("https://old.tiklxd09.club/api/payments/notify/alipay");
        config.setMerchantPrivateKey("PRIVATE-KEY-FOR-TEST");
        config.setAlipayPublicKey("PUBLIC-KEY-FOR-TEST");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.adminUpdateChannelConfig("ALIPAY", config));

        assertEquals("payment merchant private key invalid", error.getMessage());
    }

    @Test
    void adminEnabledWechatShouldRejectInvalidApiV3KeyLength() {
        KeyPair keyPair = rsaKeyPair();
        AdminPaymentChannelConfigRequest config = new AdminPaymentChannelConfigRequest();
        config.setEnabled(true);
        config.setAppId("wx-app-1");
        config.setMerchantId("mch-1");
        config.setNotifyUrl("https://old.tiklxd09.club/api/payments/notify/wechat");
        config.setMerchantSerialNo("serial-1");
        config.setMerchantPrivateKey(privateKeyPem(keyPair));
        config.setApiV3Key("short-key");
        config.setWechatPayPublicKey(publicKeyPem(keyPair));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.adminUpdateChannelConfig("WECHAT", config));

        assertEquals("payment apiV3Key invalid", error.getMessage());
    }

    private int countPayment(String rechargeNo) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_recharge_order WHERE recharge_no = ?", Integer.class, rechargeNo);
        return count == null ? 0 : count;
    }

    private CreateRechargeRequest request(String amount, String channel) {
        CreateRechargeRequest request = new CreateRechargeRequest();
        request.setAmount(new BigDecimal(amount));
        request.setChannel(channel);
        return request;
    }

    private PaymentIntentRequest intent(String bizType, String bizNo, String channel) {
        PaymentIntentRequest request = new PaymentIntentRequest();
        request.setBizType(bizType);
        request.setBizNo(bizNo);
        request.setChannel(channel);
        request.setClientType("H5");
        return request;
    }

    private KeyPair rsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("RSA test key generation failed", ex);
        }
    }

    private String privateKeyPem(KeyPair keyPair) {
        return pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
    }

    private String publicKeyPem(KeyPair keyPair) {
        return pem("PUBLIC KEY", keyPair.getPublic().getEncoded());
    }

    private String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
                + "\n-----END " + type + "-----";
    }
}
