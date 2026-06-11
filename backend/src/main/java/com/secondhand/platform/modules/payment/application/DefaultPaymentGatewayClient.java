package com.secondhand.platform.modules.payment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DefaultPaymentGatewayClient implements PaymentGatewayClient {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter ALIPAY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ALIPAY_GATEWAY = "https://openapi.alipay.com/gateway.do";
    private static final String ALIPAY_SANDBOX_GATEWAY = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    private static final String WECHAT_H5_GATEWAY = "https://api.mch.weixin.qq.com/v3/pay/transactions/h5";

    private final HttpClient httpClient;

    public DefaultPaymentGatewayClient() {
        this(HttpClient.newHttpClient());
    }

    DefaultPaymentGatewayClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public PaymentGatewayPrepayResponse createPrepay(PaymentGatewayCommand command) {
        if (command == null || command.config() == null || !command.config().configured()) {
            throw new IllegalStateException("payment channel not configured");
        }
        return switch (command.config().channel()) {
            case "ALIPAY" -> createAlipayPrepay(command);
            case "WECHAT" -> createWechatH5Prepay(command);
            default -> throw new IllegalArgumentException("payment channel invalid");
        };
    }

    private PaymentGatewayPrepayResponse createAlipayPrepay(PaymentGatewayCommand command) {
        PaymentChannelConfig config = command.config();
        Map<String, String> bizContent = new LinkedHashMap<>();
        bizContent.put("out_trade_no", command.paymentNo());
        bizContent.put("total_amount", command.amount().setScale(2, RoundingMode.UNNECESSARY).toPlainString());
        bizContent.put("subject", command.subject());
        bizContent.put("product_code", "QUICK_WAP_WAY");

        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", config.appId());
        params.put("method", "alipay.trade.wap.pay");
        params.put("format", "JSON");
        params.put("charset", "utf-8");
        params.put("sign_type", "RSA2");
        params.put("timestamp", ALIPAY_TIMESTAMP.format(LocalDateTime.now()));
        params.put("version", "1.0");
        params.put("notify_url", config.notifyUrl());
        if (notBlank(config.returnUrl())) {
            params.put("return_url", config.returnUrl());
        }
        params.put("biz_content", writeJson(bizContent));
        params.put("sign", rsa2Sign(signingContent(params), config.merchantPrivateKey()));

        String gateway = notBlank(config.gatewayUrl()) ? config.gatewayUrl() : config.sandbox() ? ALIPAY_SANDBOX_GATEWAY : ALIPAY_GATEWAY;
        String form = buildAutoSubmitForm(gateway, params);
        return new PaymentGatewayPrepayResponse("FORM", null, form, safeGatewaySummary("ALIPAY", command.paymentNo()));
    }

    private PaymentGatewayPrepayResponse createWechatH5Prepay(PaymentGatewayCommand command) {
        PaymentChannelConfig config = command.config();
        Map<String, Object> amount = Map.of("total", toFen(command.amount()), "currency", "CNY");
        Map<String, Object> h5Info = Map.of("type", "Wap");
        Map<String, Object> sceneInfo = Map.of(
                "payer_client_ip", notBlank(command.clientIp()) ? command.clientIp() : "127.0.0.1",
                "h5_info", h5Info
        );
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appid", config.appId());
        body.put("mchid", config.merchantId());
        body.put("description", command.subject());
        body.put("out_trade_no", command.paymentNo());
        body.put("notify_url", config.notifyUrl());
        body.put("amount", amount);
        body.put("scene_info", sceneInfo);

        String requestBody = writeJson(body);
        String endpoint = notBlank(config.gatewayUrl()) ? config.gatewayUrl() : WECHAT_H5_GATEWAY;
        String authorization = wechatAuthorization("POST", URI.create(endpoint).getRawPath(), requestBody, config);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("payment gateway rejected");
            }
            Map<?, ?> payload = OBJECT_MAPPER.readValue(response.body(), Map.class);
            Object h5Url = payload.get("h5_url");
            if (!(h5Url instanceof String url) || url.isBlank()) {
                throw new IllegalStateException("payment gateway response invalid");
            }
            return new PaymentGatewayPrepayResponse("REDIRECT", url, null, safeGatewaySummary("WECHAT", command.paymentNo()));
        } catch (IOException ex) {
            throw new IllegalStateException("payment gateway unavailable", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("payment gateway interrupted", ex);
        }
    }

    private String wechatAuthorization(String method, String canonicalUrl, String body, PaymentChannelConfig config) {
        long timestamp = System.currentTimeMillis() / 1000L;
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String message = method + "\n" + canonicalUrl + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n";
        String signature = rsa2Sign(message, config.merchantPrivateKey());
        return "WECHATPAY2-SHA256-RSA2048 "
                + "mchid=\"" + config.merchantId() + "\","
                + "nonce_str=\"" + nonce + "\","
                + "signature=\"" + signature + "\","
                + "timestamp=\"" + timestamp + "\","
                + "serial_no=\"" + config.merchantSerialNo() + "\"";
    }

    static String rsa2Sign(String content, String privateKeyPemOrBase64) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(parsePrivateKey(privateKeyPemOrBase64));
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (GeneralSecurityException ex) {
            throw new IllegalArgumentException("payment merchant private key invalid", ex);
        }
    }

    static PrivateKey parsePrivateKey(String privateKeyPemOrBase64) throws GeneralSecurityException {
        String normalized = privateKeyPemOrBase64 == null ? "" : privateKeyPemOrBase64
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("payment merchant private key invalid");
        }
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(normalized)));
    }

    static String signingContent(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .filter(entry -> !"sign".equals(entry.getKey()) && !"sign_type".equals(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    private String buildAutoSubmitForm(String gateway, Map<String, String> params) {
        StringBuilder html = new StringBuilder();
        html.append("<form id=\"xiaoyuanquan-pay-form\" method=\"post\" action=\"").append(escapeHtml(gateway)).append("\">");
        params.forEach((key, value) -> html.append("<input type=\"hidden\" name=\"")
                .append(escapeHtml(key))
                .append("\" value=\"")
                .append(escapeHtml(value))
                .append("\"/>"));
        html.append("</form><script>document.getElementById('xiaoyuanquan-pay-form').submit();</script>");
        return html.toString();
    }

    private int toFen(BigDecimal amount) {
        return amount.multiply(new BigDecimal("100")).setScale(0, RoundingMode.UNNECESSARY).intValueExact();
    }

    private String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("payment payload invalid", ex);
        }
    }

    private String safeGatewaySummary(String channel, String paymentNo) {
        return "{\"channel\":\"" + channel + "\",\"paymentNo\":\"" + paymentNo + "\"}";
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String escapeHtml(String value) {
        return (value == null ? "" : value)
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
