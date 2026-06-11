package com.secondhand.platform.modules.payment.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondhand.platform.modules.order.PayOrderResponse;
import com.secondhand.platform.modules.order.application.OrderApplicationService;
import com.secondhand.platform.modules.payment.AdminPaymentChannelConfigRequest;
import com.secondhand.platform.modules.payment.AdminPaymentChannelConfigResponse;
import com.secondhand.platform.modules.payment.PaymentIntentRequest;
import com.secondhand.platform.modules.payment.PaymentIntentResponse;
import com.secondhand.platform.modules.wallet_ledger.application.CreditCommand;
import com.secondhand.platform.modules.wallet_ledger.application.LedgerTransactionResponse;
import com.secondhand.platform.modules.wallet_ledger.application.WalletLedgerService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentApplicationService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String CHANNEL_ALIPAY = "ALIPAY";
    private static final String CHANNEL_WECHAT = "WECHAT";
    private static final String BIZ_TYPE_ORDER = "ORDER";
    private static final String BIZ_TYPE_RECHARGE = "RECHARGE";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_GATEWAY_READY = "GATEWAY_READY";

    private final WalletLedgerService walletLedgerService;
    private final JdbcTemplate jdbcTemplate;
    private final OrderApplicationService orderApplicationService;
    private final PaymentGatewayClient gatewayClient;

    public PaymentApplicationService(WalletLedgerService walletLedgerService, JdbcTemplate jdbcTemplate) {
        this(walletLedgerService, jdbcTemplate, null, new DefaultPaymentGatewayClient());
    }

    @Autowired
    public PaymentApplicationService(WalletLedgerService walletLedgerService,
                                     JdbcTemplate jdbcTemplate,
                                     OrderApplicationService orderApplicationService,
                                     PaymentGatewayClient gatewayClient) {
        this.walletLedgerService = walletLedgerService;
        this.jdbcTemplate = jdbcTemplate;
        this.orderApplicationService = orderApplicationService;
        this.gatewayClient = gatewayClient;
        ensurePaymentSchemaCompatibility();
        ensureDefaultPaymentConfigs();
    }

    @Transactional
    public RechargeResponse createRecharge(Long userId, CreateRechargeRequest request) {
        requireUserId(userId);
        validate(request);
        BigDecimal amount = money(request.getAmount());
        String channel = normalizeChannel(request.getChannel());
        String rechargeNo = uniqueRechargeNo();
        jdbcTemplate.update("""
                INSERT INTO payment_recharge_order (
                  recharge_no, user_id, amount, channel, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, rechargeNo, userId, amount, channel);
        return findRecharge(userId, rechargeNo);
    }

    @Transactional
    public PaymentIntentResponse createPaymentIntent(Long userId, PaymentIntentRequest request, String clientIp) {
        requireUserId(userId);
        if (request == null) {
            throw new IllegalArgumentException("payment intent request required");
        }
        String channel = normalizeChannel(request.getChannel());
        String bizType = normalizeBizType(request.getBizType());
        PaymentBizTarget target = resolvePaymentBizTarget(userId, bizType, request);
        PaymentChannelConfig config = loadChannelConfig(channel);
        if (!config.configured()) {
            throw new IllegalStateException("payment channel not configured");
        }
        PaymentTransaction transaction = loadOrCreateTransaction(userId, target, channel);
        PaymentGatewayPrepayResponse prepay = gatewayClient.createPrepay(new PaymentGatewayCommand(
                config,
                transaction.paymentNo(),
                target.bizType(),
                target.bizNo(),
                userId,
                target.amount(),
                target.subject(),
                sanitizeClientIp(clientIp)
        ));
        jdbcTemplate.update("""
                UPDATE payment_transaction
                SET status = ?, gateway_payload = ?, updated_at = CURRENT_TIMESTAMP
                WHERE payment_no = ? AND status <> ?
                """, STATUS_GATEWAY_READY, limit(prepay.gatewayResponse(), 1024), transaction.paymentNo(), STATUS_PAID);
        return new PaymentIntentResponse(
                transaction.paymentNo(),
                target.bizType(),
                target.bizNo(),
                target.amount(),
                channel,
                STATUS_GATEWAY_READY,
                prepay.actionType(),
                prepay.payUrl(),
                prepay.formHtml(),
                "gateway-ready"
        );
    }

    @Transactional
    public RechargeResponse simulateRechargeSuccess(Long userId, String rechargeNo) {
        requireUserId(userId);
        String safeRechargeNo = requireText(rechargeNo, "rechargeNo required");
        RechargeResponse recharge = findRecharge(userId, safeRechargeNo);
        if (recharge == null) {
            throw new IllegalArgumentException("recharge order not found");
        }
        if (STATUS_PAID.equals(recharge.status())) {
            return recharge;
        }
        markRechargePaid(userId, safeRechargeNo, recharge.amount(), "payment:recharge:" + safeRechargeNo);
        return findRecharge(userId, safeRechargeNo);
    }

    public List<AdminPaymentChannelConfigResponse> adminListChannelConfigs() {
        ensureDefaultPaymentConfigs();
        return List.of(toAdminResponse(loadChannelConfig(CHANNEL_ALIPAY)), toAdminResponse(loadChannelConfig(CHANNEL_WECHAT)));
    }

    @Transactional
    public AdminPaymentChannelConfigResponse adminUpdateChannelConfig(String channel, AdminPaymentChannelConfigRequest request) {
        String safeChannel = normalizeChannel(channel);
        if (request == null) {
            throw new IllegalArgumentException("payment config request required");
        }
        PaymentChannelConfig current = loadChannelConfig(safeChannel);
        PaymentChannelConfig next = new PaymentChannelConfig(
                safeChannel,
                request.getEnabled() != null ? request.getEnabled() : current.enabled(),
                request.getSandbox() != null ? request.getSandbox() : current.sandbox(),
                choosePublicText(request.getAppId(), current.appId(), 128, "payment appId invalid"),
                choosePublicText(request.getMerchantId(), current.merchantId(), 128, "payment merchantId invalid"),
                chooseUrl(request.getGatewayUrl(), current.gatewayUrl(), true),
                chooseUrl(request.getNotifyUrl(), current.notifyUrl(), false),
                chooseUrl(request.getReturnUrl(), current.returnUrl(), true),
                chooseSecret(request.getMerchantPrivateKey(), current.merchantPrivateKey(), "payment merchant private key invalid"),
                chooseSecret(request.getAlipayPublicKey(), current.alipayPublicKey(), "payment alipay public key invalid"),
                choosePublicText(request.getMerchantSerialNo(), current.merchantSerialNo(), 128, "payment merchant serialNo invalid"),
                chooseSecret(request.getApiV3Key(), current.apiV3Key(), "payment apiV3Key invalid"),
                chooseSecret(request.getWechatPayPublicKey(), current.wechatPayPublicKey(), "payment wechat public key invalid"),
                null
        );
        validateEnabledChannelConfig(next);
        upsertChannelConfig(next);
        return toAdminResponse(loadChannelConfig(safeChannel));
    }

    @Transactional
    public String handleAlipayNotify(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "failure";
        }
        PaymentChannelConfig config = loadChannelConfig(CHANNEL_ALIPAY);
        if (!verifyAlipaySignature(params, config.alipayPublicKey())) {
            return "failure";
        }
        String tradeStatus = params.getOrDefault("trade_status", "");
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            return "success";
        }
        String paymentNo = requireText(params.get("out_trade_no"), "paymentNo required");
        String providerTradeNo = params.get("trade_no");
        BigDecimal amount = money(new BigDecimal(requireText(params.get("total_amount"), "payment amount required")));
        markTransactionPaid(paymentNo, providerTradeNo, amount);
        return "success";
    }

    @Transactional
    public Map<String, String> handleWechatNotify(String body, String timestamp, String nonce, String signature) {
        PaymentChannelConfig config = loadChannelConfig(CHANNEL_WECHAT);
        if (!verifyWechatSignature(body, timestamp, nonce, signature, config.wechatPayPublicKey())) {
            return Map.of("code", "FAIL", "message", "signature invalid");
        }
        Map<String, Object> payload = parseJson(body);
        Object resourceObject = payload.get("resource");
        if (!(resourceObject instanceof Map<?, ?> resource)) {
            return Map.of("code", "FAIL", "message", "resource invalid");
        }
        Map<String, Object> plain = parseJson(decryptWechatResource(resource, config.apiV3Key()));
        if (!"SUCCESS".equals(String.valueOf(plain.get("trade_state")))) {
            return Map.of("code", "SUCCESS", "message", "ok");
        }
        String paymentNo = requireText(String.valueOf(plain.get("out_trade_no")), "paymentNo required");
        String providerTradeNo = String.valueOf(plain.get("transaction_id"));
        BigDecimal amount = wechatNotifyAmount(plain);
        markTransactionPaid(paymentNo, providerTradeNo, amount);
        return Map.of("code", "SUCCESS", "message", "ok");
    }

    private void markTransactionPaid(String paymentNo, String providerTradeNo, BigDecimal paidAmount) {
        PaymentTransaction transaction = loadTransaction(paymentNo);
        if (STATUS_PAID.equals(transaction.status())) {
            return;
        }
        if (transaction.amount().compareTo(paidAmount) != 0) {
            throw new IllegalStateException("payment amount mismatch");
        }
        if (BIZ_TYPE_RECHARGE.equals(transaction.bizType())) {
            markRechargePaid(transaction.userId(), transaction.bizNo(), transaction.amount(), "payment:recharge:" + paymentNo);
        } else if (BIZ_TYPE_ORDER.equals(transaction.bizType())) {
            if (orderApplicationService == null) {
                throw new IllegalStateException("order payment service unavailable");
            }
            PayOrderResponse paid = orderApplicationService.markOrderPaidByExternal(transaction.bizNo(), transaction.userId(), paymentNo, transaction.channel());
            if (!STATUS_PAID.equals(paid.getStatus())) {
                throw new IllegalStateException("order-payment-state-update-failed");
            }
        } else {
            throw new IllegalArgumentException("payment bizType invalid");
        }
        jdbcTemplate.update("""
                UPDATE payment_transaction
                SET status = ?, provider_trade_no = ?, paid_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE payment_no = ? AND status <> ?
                """, STATUS_PAID, limit(providerTradeNo, 128), paymentNo, STATUS_PAID);
    }

    private void markRechargePaid(Long userId, String rechargeNo, BigDecimal amount, String idempotencyKey) {
        CreditCommand command = new CreditCommand();
        command.setUserId(userId);
        command.setIdempotencyKey(idempotencyKey);
        command.setBizType("RECHARGE");
        command.setBizNo(rechargeNo);
        command.setBalanceType("RECHARGE");
        command.setAmount(amount);

        LedgerTransactionResponse ledger = walletLedgerService.credit(command);
        jdbcTemplate.update("""
                UPDATE payment_recharge_order
                SET status = 'PAID', ledger_no = ?, balance_before = ?, balance_after = ?, paid_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE recharge_no = ? AND user_id = ? AND status = 'PENDING'
                """, ledger.ledgerNo(), ledger.balanceBefore(), ledger.balanceAfter(), rechargeNo, userId);
    }

    private PaymentBizTarget resolvePaymentBizTarget(Long userId, String bizType, PaymentIntentRequest request) {
        if (BIZ_TYPE_ORDER.equals(bizType)) {
            String orderNo = requireText(request.getBizNo(), "orderNo required");
            List<PaymentBizTarget> rows = jdbcTemplate.query("""
                    SELECT order_no, buyer_id, amount, product_title, order_status
                    FROM trade_order
                    WHERE order_no = ?
                    """, (rs, rowNum) -> new PaymentBizTarget(
                    BIZ_TYPE_ORDER,
                    rs.getString("order_no"),
                    rs.getLong("buyer_id"),
                    money(rs.getBigDecimal("amount")),
                    "小原圈订单 " + rs.getString("product_title"),
                    rs.getString("order_status")
            ), orderNo);
            if (rows.isEmpty()) throw new IllegalArgumentException("order-not-found");
            PaymentBizTarget target = rows.get(0);
            if (!target.userId().equals(userId)) throw new IllegalArgumentException("order-buyer-mismatch");
            if (!STATUS_PENDING.equals(target.status()) && !"PENDING_PAY".equals(target.status())) throw new IllegalStateException("order-not-payable");
            return target;
        }
        if (BIZ_TYPE_RECHARGE.equals(bizType)) {
            String rechargeNo = requireText(request.getBizNo(), "rechargeNo required");
            RechargeResponse recharge = findRecharge(userId, rechargeNo);
            if (recharge == null) throw new IllegalArgumentException("recharge order not found");
            if (STATUS_PAID.equals(recharge.status())) throw new IllegalStateException("payment already paid");
            return new PaymentBizTarget(BIZ_TYPE_RECHARGE, recharge.rechargeNo(), userId, recharge.amount(), "小原圈钱包充值", recharge.status());
        }
        throw new IllegalArgumentException("payment bizType invalid");
    }

    private PaymentTransaction loadOrCreateTransaction(Long userId, PaymentBizTarget target, String channel) {
        List<PaymentTransaction> existing = jdbcTemplate.query("""
                SELECT payment_no,biz_type,biz_no,user_id,amount,channel,status,provider_trade_no
                FROM payment_transaction
                WHERE biz_type = ? AND biz_no = ? AND user_id = ? AND channel = ?
                ORDER BY id DESC
                LIMIT 1
                """, (rs, rowNum) -> mapTransaction(rs), target.bizType(), target.bizNo(), userId, channel);
        if (!existing.isEmpty()) {
            PaymentTransaction transaction = existing.get(0);
            if (STATUS_PAID.equals(transaction.status())) {
                throw new IllegalStateException("payment already paid");
            }
            if (transaction.amount().compareTo(target.amount()) != 0) {
                throw new IllegalStateException("payment amount mismatch");
            }
            return transaction;
        }
        String paymentNo = uniquePaymentNo();
        jdbcTemplate.update("""
                INSERT INTO payment_transaction (payment_no,biz_type,biz_no,user_id,amount,channel,status,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, paymentNo, target.bizType(), target.bizNo(), userId, target.amount(), channel, STATUS_PENDING);
        return loadTransaction(paymentNo);
    }

    private PaymentTransaction loadTransaction(String paymentNo) {
        List<PaymentTransaction> rows = jdbcTemplate.query("""
                SELECT payment_no,biz_type,biz_no,user_id,amount,channel,status,provider_trade_no
                FROM payment_transaction
                WHERE payment_no = ?
                """, (rs, rowNum) -> mapTransaction(rs), paymentNo);
        if (rows.isEmpty()) throw new IllegalArgumentException("payment intent not found");
        return rows.get(0);
    }

    private PaymentTransaction mapTransaction(ResultSet rs) throws SQLException {
        return new PaymentTransaction(
                rs.getString("payment_no"),
                rs.getString("biz_type"),
                rs.getString("biz_no"),
                rs.getLong("user_id"),
                money(rs.getBigDecimal("amount")),
                rs.getString("channel"),
                rs.getString("status"),
                rs.getString("provider_trade_no")
        );
    }

    private RechargeResponse findRecharge(Long userId, String rechargeNo) {
        List<RechargeResponse> rows = jdbcTemplate.query("""
                SELECT user_id, recharge_no, amount, channel, status, ledger_no, balance_before, balance_after, created_at
                FROM payment_recharge_order
                WHERE user_id = ? AND recharge_no = ?
                """, (rs, rowNum) -> mapRecharge(rs), userId, rechargeNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private RechargeResponse mapRecharge(ResultSet rs) throws SQLException {
        return new RechargeResponse(
                rs.getLong("user_id"),
                rs.getString("recharge_no"),
                money(rs.getBigDecimal("amount")),
                rs.getString("channel"),
                rs.getString("status"),
                rs.getString("ledger_no"),
                rs.getBigDecimal("balance_before"),
                rs.getBigDecimal("balance_after"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                false
        );
    }

    private PaymentChannelConfig loadChannelConfig(String channel) {
        ensureDefaultPaymentConfigs();
        return jdbcTemplate.query("""
                SELECT channel,enabled,sandbox,app_id,merchant_id,gateway_url,notify_url,return_url,merchant_private_key,alipay_public_key,merchant_serial_no,api_v3_key,wechat_pay_public_key,updated_at
                FROM payment_channel_config
                WHERE channel = ?
                """, (rs, rowNum) -> new PaymentChannelConfig(
                rs.getString("channel"),
                rs.getBoolean("enabled"),
                rs.getBoolean("sandbox"),
                rs.getString("app_id"),
                rs.getString("merchant_id"),
                rs.getString("gateway_url"),
                rs.getString("notify_url"),
                rs.getString("return_url"),
                rs.getString("merchant_private_key"),
                rs.getString("alipay_public_key"),
                rs.getString("merchant_serial_no"),
                rs.getString("api_v3_key"),
                rs.getString("wechat_pay_public_key"),
                rs.getTimestamp("updated_at") == null ? "" : rs.getTimestamp("updated_at").toInstant().toString()
        ), channel).stream().findFirst().orElse(new PaymentChannelConfig(channel, false, false, null, null, null, null, null, null, null, null, null, null, ""));
    }

    private void upsertChannelConfig(PaymentChannelConfig config) {
        int updated = jdbcTemplate.update("""
                UPDATE payment_channel_config
                SET enabled = ?, sandbox = ?, app_id = ?, merchant_id = ?, gateway_url = ?, notify_url = ?, return_url = ?,
                    merchant_private_key = ?, alipay_public_key = ?, merchant_serial_no = ?, api_v3_key = ?, wechat_pay_public_key = ?, updated_at = CURRENT_TIMESTAMP
                WHERE channel = ?
                """, config.enabled(), config.sandbox(), config.appId(), config.merchantId(), config.gatewayUrl(), config.notifyUrl(), config.returnUrl(),
                config.merchantPrivateKey(), config.alipayPublicKey(), config.merchantSerialNo(), config.apiV3Key(), config.wechatPayPublicKey(), config.channel());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO payment_channel_config (channel,enabled,sandbox,app_id,merchant_id,gateway_url,notify_url,return_url,merchant_private_key,alipay_public_key,merchant_serial_no,api_v3_key,wechat_pay_public_key,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, config.channel(), config.enabled(), config.sandbox(), config.appId(), config.merchantId(), config.gatewayUrl(), config.notifyUrl(), config.returnUrl(),
                    config.merchantPrivateKey(), config.alipayPublicKey(), config.merchantSerialNo(), config.apiV3Key(), config.wechatPayPublicKey());
        }
    }

    private AdminPaymentChannelConfigResponse toAdminResponse(PaymentChannelConfig config) {
        return new AdminPaymentChannelConfigResponse(
                config.channel(),
                config.enabled(),
                config.sandbox(),
                config.configured(),
                config.appId(),
                maskPublic(config.merchantId()),
                config.gatewayUrl(),
                config.notifyUrl(),
                config.returnUrl(),
                notBlank(config.merchantPrivateKey()),
                notBlank(config.alipayPublicKey()),
                maskPublic(config.merchantSerialNo()),
                notBlank(config.apiV3Key()),
                notBlank(config.wechatPayPublicKey()),
                config.updatedAt()
        );
    }

    private boolean verifyAlipaySignature(Map<String, String> params, String publicKey) {
        try {
            String sign = params.get("sign");
            if (isBlank(sign) || isBlank(publicKey)) return false;
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(parsePublicKey(publicKey));
            verifier.update(DefaultPaymentGatewayClient.signingContent(params).getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(sign));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean verifyWechatSignature(String body, String timestamp, String nonce, String signature, String publicKey) {
        try {
            if (isBlank(body) || isBlank(timestamp) || isBlank(nonce) || isBlank(signature) || isBlank(publicKey)) return false;
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(parsePublicKey(publicKey));
            verifier.update((timestamp + "\n" + nonce + "\n" + body + "\n").getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            return false;
        }
    }

    private PublicKey parsePublicKey(String publicKeyPemOrBase64) throws GeneralSecurityException {
        String normalized = publicKeyPemOrBase64 == null ? "" : publicKeyPemOrBase64
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("payment public key invalid");
        }
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(normalized)));
    }

    private String decryptWechatResource(Map<?, ?> resource, String apiV3Key) {
        try {
            String associatedData = String.valueOf(resource.get("associated_data"));
            String nonce = String.valueOf(resource.get("nonce"));
            String ciphertext = String.valueOf(resource.get("ciphertext"));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(apiV3Key.getBytes(StandardCharsets.UTF_8), "AES"),
                    new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
            if (!isBlank(associatedData)) {
                cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            }
            return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalArgumentException("payment notify decrypt failed", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("payment notify body invalid", ex);
        }
    }

    private BigDecimal wechatNotifyAmount(Map<String, Object> plain) {
        Object amountObject = plain.get("amount");
        if (!(amountObject instanceof Map<?, ?> amountMap)) {
            throw new IllegalArgumentException("payment amount required");
        }
        Object total = amountMap.get("total");
        return money(new BigDecimal(String.valueOf(total)).divide(new BigDecimal("100"), 2, RoundingMode.UNNECESSARY));
    }

    private void ensurePaymentSchemaCompatibility() {
        ensureTable("payment_channel_config", h2PaymentConfigDdl(), mysqlPaymentConfigDdl());
        ensureTable("payment_transaction", h2PaymentTransactionDdl(), mysqlPaymentTransactionDdl());
    }

    private void ensureTable(String tableName, String h2Ddl, String mysqlDdl) {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            if (tableExists(connection.getMetaData(), tableName)) return;
            String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            jdbcTemplate.execute(product.contains("mysql") ? mysqlDdl : h2Ddl);
        } catch (SQLException ex) {
            throw new IllegalStateException("payment schema compatibility check failed: " + tableName, ex);
        }
    }

    private boolean tableExists(DatabaseMetaData metaData, String tableName) throws SQLException {
        try (ResultSet rs = metaData.getTables(null, null, tableName.toUpperCase(Locale.ROOT), null)) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = metaData.getTables(null, null, tableName.toLowerCase(Locale.ROOT), null)) {
            return rs.next();
        }
    }

    private String h2PaymentConfigDdl() {
        return """
                CREATE TABLE payment_channel_config (
                  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                  channel VARCHAR(32) NOT NULL UNIQUE,
                  enabled BOOLEAN NOT NULL DEFAULT FALSE,
                  sandbox BOOLEAN NOT NULL DEFAULT FALSE,
                  app_id VARCHAR(128),
                  merchant_id VARCHAR(128),
                  gateway_url VARCHAR(512),
                  notify_url VARCHAR(512),
                  return_url VARCHAR(512),
                  merchant_private_key TEXT,
                  alipay_public_key TEXT,
                  merchant_serial_no VARCHAR(128),
                  api_v3_key VARCHAR(128),
                  wechat_pay_public_key TEXT,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """;
    }

    private String h2PaymentTransactionDdl() {
        return """
                CREATE TABLE payment_transaction (
                  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                  payment_no VARCHAR(128) NOT NULL UNIQUE,
                  biz_type VARCHAR(32) NOT NULL,
                  biz_no VARCHAR(128) NOT NULL,
                  user_id BIGINT NOT NULL,
                  amount DECIMAL(18,2) NOT NULL,
                  channel VARCHAR(32) NOT NULL,
                  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                  provider_trade_no VARCHAR(128),
                  gateway_payload TEXT,
                  paid_at TIMESTAMP,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE(biz_type, biz_no, user_id, channel)
                )
                """;
    }

    private String mysqlPaymentConfigDdl() {
        return h2PaymentConfigDdl()
                .replace("id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY", "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY")
                .replace("enabled BOOLEAN", "enabled TINYINT(1)")
                .replace("sandbox BOOLEAN", "sandbox TINYINT(1)")
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    private String mysqlPaymentTransactionDdl() {
        return h2PaymentTransactionDdl()
                .replace("id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY", "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY")
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    private void ensureDefaultPaymentConfigs() {
        insertDefaultChannel(CHANNEL_ALIPAY);
        insertDefaultChannel(CHANNEL_WECHAT);
    }

    private void insertDefaultChannel(String channel) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM payment_channel_config WHERE channel = ?", Integer.class, channel);
        if (count == null || count == 0) {
            jdbcTemplate.update("INSERT INTO payment_channel_config (channel,enabled,sandbox,created_at,updated_at) VALUES (?,FALSE,FALSE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", channel);
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("userId required");
    }

    private void validate(CreateRechargeRequest request) {
        if (request == null) throw new IllegalArgumentException("recharge request required");
        if (request.getAmount() == null || request.getAmount().signum() <= 0) throw new IllegalArgumentException("recharge amount must be positive");
        money(request.getAmount());
    }

    private BigDecimal money(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("amount required");
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("amount scale must be <= 2", ex);
        }
    }

    private String normalizeChannel(String channel) {
        String safe = requireText(channel, "channel required").toUpperCase(Locale.ROOT);
        if (!CHANNEL_WECHAT.equals(safe) && !CHANNEL_ALIPAY.equals(safe)) throw new IllegalArgumentException("unsupported recharge channel");
        return safe;
    }

    private String normalizeBizType(String value) {
        String safe = requireText(value, "payment bizType required").toUpperCase(Locale.ROOT);
        if (!BIZ_TYPE_ORDER.equals(safe) && !BIZ_TYPE_RECHARGE.equals(safe)) throw new IllegalArgumentException("payment bizType invalid");
        return safe;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private String choosePublicText(String next, String current, int max, String message) {
        if (next == null) return current;
        String safe = next.trim();
        if (safe.isEmpty()) return current;
        if (safe.length() > max || blocked(safe)) throw new IllegalArgumentException(message);
        return safe;
    }

    private String chooseSecret(String next, String current, String message) {
        if (next == null) return current;
        String safe = next.trim();
        if (safe.isEmpty() || "********".equals(safe)) return current;
        if (safe.length() > 12000) throw new IllegalArgumentException(message);
        return safe;
    }

    private void validateEnabledChannelConfig(PaymentChannelConfig config) {
        if (!config.enabled()) {
            return;
        }
        if ("ALIPAY".equals(config.channel())) {
            validateRequired(config.appId(), "payment appId invalid");
            validateRequired(config.notifyUrl(), "payment url invalid");
            validatePrivateKey(config.merchantPrivateKey(), "payment merchant private key invalid");
            validatePublicKey(config.alipayPublicKey(), "payment alipay public key invalid");
            return;
        }
        if ("WECHAT".equals(config.channel())) {
            validateRequired(config.appId(), "payment appId invalid");
            validateRequired(config.merchantId(), "payment merchantId invalid");
            validateRequired(config.notifyUrl(), "payment url invalid");
            validateRequired(config.merchantSerialNo(), "payment merchant serialNo invalid");
            validatePrivateKey(config.merchantPrivateKey(), "payment merchant private key invalid");
            validateApiV3Key(config.apiV3Key());
            validatePublicKey(config.wechatPayPublicKey(), "payment wechat public key invalid");
        }
    }

    private void validateRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validatePrivateKey(String value, String message) {
        try {
            DefaultPaymentGatewayClient.parsePrivateKey(value);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalArgumentException(message, ex);
        }
    }

    private void validatePublicKey(String value, String message) {
        try {
            parsePublicKey(value);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalArgumentException(message, ex);
        }
    }

    private void validateApiV3Key(String value) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length != 32) {
            throw new IllegalArgumentException("payment apiV3Key invalid");
        }
    }

    private String chooseUrl(String next, String current, boolean optional) {
        if (next == null) return current;
        String safe = next.trim();
        if (safe.isEmpty()) return optional ? null : current;
        if (safe.length() > 512 || !safe.startsWith("https://") || blocked(safe)) throw new IllegalArgumentException("payment url invalid");
        return safe;
    }

    private boolean blocked(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("preview") || lower.contains("demo") || lower.contains("mock") || lower.contains("sample") || lower.contains("placeholder");
    }

    private String sanitizeClientIp(String value) {
        if (value == null || value.isBlank()) return "127.0.0.1";
        String first = value.split(",", 2)[0].trim();
        if (first.length() > 64 || first.contains("\n") || first.contains("\r")) return "127.0.0.1";
        return first;
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String maskPublic(String value) {
        if (isBlank(value)) return null;
        String safe = value.trim();
        if (safe.length() <= 8) return "****";
        return safe.substring(0, 4) + "****" + safe.substring(safe.length() - 4);
    }

    private String uniqueRechargeNo() {
        long now = System.currentTimeMillis();
        long random = SECURE_RANDOM.nextLong() & Long.MAX_VALUE;
        return String.format(Locale.ROOT, "RC-%d-%016X", now, random);
    }

    private String uniquePaymentNo() {
        long now = System.currentTimeMillis();
        long random = SECURE_RANDOM.nextLong() & Long.MAX_VALUE;
        return String.format(Locale.ROOT, "PM-%d-%016X", now, random);
    }

    private record PaymentBizTarget(String bizType, String bizNo, Long userId, BigDecimal amount, String subject, String status) {}
    private record PaymentTransaction(String paymentNo, String bizType, String bizNo, Long userId, BigDecimal amount, String channel, String status, String providerTradeNo) {}
}
