package com.secondhand.platform.modules.gift.application;

import com.secondhand.platform.modules.gift.GiftCatalogItemResponse;
import com.secondhand.platform.modules.gift.ReceivedGiftItemResponse;
import com.secondhand.platform.modules.gift.SendGiftResponse;
import com.secondhand.platform.modules.wallet_ledger.application.CreditCommand;
import com.secondhand.platform.modules.wallet_ledger.application.DebitCommand;
import com.secondhand.platform.modules.wallet_ledger.application.LedgerTransactionResponse;
import com.secondhand.platform.modules.wallet_ledger.application.WalletLedgerService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GiftApplicationService {
    private static final int MONEY_SCALE = 2;
    private static final int MIN_QUANTITY = 1;
    private static final int MAX_QUANTITY = 99;
    private static final BigDecimal ONE = BigDecimal.ONE;

    private static final Map<String, GiftConfig> GIFTS_BY_CODE = Map.of(
            "ROSE", new GiftConfig(1L, "ROSE", "玫瑰花", "🌹", new BigDecimal("1.00"), new BigDecimal("0.20")),
            "COFFEE", new GiftConfig(2L, "COFFEE", "暖心咖啡", "☕", new BigDecimal("6.00"), new BigDecimal("0.20")),
            "STAR", new GiftConfig(3L, "STAR", "星光应援", "⭐", new BigDecimal("18.00"), new BigDecimal("0.25")),
            "CROWN", new GiftConfig(4L, "CROWN", "小原皇冠", "👑", new BigDecimal("68.00"), new BigDecimal("0.30"))
    );
    private static final Map<Long, GiftConfig> GIFTS_BY_ID = giftById();

    private final WalletLedgerService walletLedgerService;
    private final JdbcTemplate jdbcTemplate;

    public GiftApplicationService(WalletLedgerService walletLedgerService, JdbcTemplate jdbcTemplate) {
        this.walletLedgerService = walletLedgerService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<GiftCatalogItemResponse> listCatalog() {
        return GIFTS_BY_CODE.values().stream()
                .sorted((left, right) -> Long.compare(left.id(), right.id()))
                .map(gift -> new GiftCatalogItemResponse(gift.id(), gift.code(), gift.name(), gift.icon(), gift.price(), gift.platformRate()))
                .toList();
    }

    public List<ReceivedGiftItemResponse> listReceivedGifts(Long receiverId) {
        validateReceiverForQuery(receiverId);
        return jdbcTemplate.query("""
                SELECT gift_order_no, sender_id, gift_id, gift_code, quantity, total_amount, platform_share,
                       receiver_amount, receiver_credit_ledger_no, status, created_at
                FROM gift_order
                WHERE receiver_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 50
                """, (rs, rowNum) -> mapReceivedGift(rs), receiverId);
    }

    @Transactional
    public SendGiftResponse sendGift(Long senderId, SendGiftRequest request) {
        validateSender(senderId);
        validateRequest(request);
        if (senderId.equals(request.getReceiverId())) {
            throw new IllegalArgumentException("cannot send gift to yourself");
        }

        GiftConfig gift = resolveGift(request);
        int quantity = request.getQuantity() == null ? MIN_QUANTITY : request.getQuantity();
        BigDecimal totalAmount = gift.price().multiply(BigDecimal.valueOf(quantity)).setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("gift amount must be positive");
        }
        BigDecimal platformShare = totalAmount.multiply(gift.platformRate()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal receiverAmount = totalAmount.subtract(platformShare).setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        if (receiverAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("receiver amount must be positive");
        }

        String idempotencyKey = idempotencyKey(senderId, request);
        String giftOrderNo = generateGiftOrderNo(idempotencyKey);
        SendGiftResponse cached = findGiftOrder(giftOrderNo);
        if (cached != null) {
            if (!Objects.equals(cached.getReceiverId(), request.getReceiverId())) {
                throw new IllegalArgumentException("gift idempotency receiver mismatch");
            }
            return cached;
        }

        DebitCommand debit = new DebitCommand();
        debit.setUserId(senderId);
        debit.setIdempotencyKey("gift:send:debit:" + idempotencyKey);
        debit.setBizType("GIFT_SEND");
        debit.setBizNo(giftOrderNo);
        debit.setBalanceType("RECHARGE");
        debit.setAmount(totalAmount);
        LedgerTransactionResponse debitLedger = walletLedgerService.debit(debit);

        CreditCommand credit = new CreditCommand();
        credit.setUserId(request.getReceiverId());
        credit.setIdempotencyKey("gift:send:receiver:" + idempotencyKey);
        credit.setBizType("GIFT_RECEIVE");
        credit.setBizNo(giftOrderNo);
        credit.setBalanceType("INCOME");
        credit.setAmount(receiverAmount);
        LedgerTransactionResponse receiverLedger = walletLedgerService.credit(credit);

        SendGiftResponse response = new SendGiftResponse(
                giftOrderNo,
                gift.id(),
                gift.code(),
                request.getReceiverId(),
                totalAmount,
                platformShare,
                receiverAmount,
                debitLedger.ledgerNo(),
                receiverLedger.ledgerNo(),
                "SUCCESS",
                nowText()
        );
        insertGiftOrder(senderId, request, quantity, response, idempotencyKey);
        return findGiftOrder(giftOrderNo);
    }

    private SendGiftResponse findGiftOrder(String giftOrderNo) {
        List<SendGiftResponse> rows = jdbcTemplate.query("""
                SELECT gift_order_no, gift_id, gift_code, receiver_id, total_amount, platform_share, receiver_amount,
                       debit_ledger_no, receiver_credit_ledger_no, status, created_at
                FROM gift_order
                WHERE gift_order_no = ?
                """, (rs, rowNum) -> mapGiftOrder(rs), giftOrderNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private SendGiftResponse mapGiftOrder(ResultSet rs) throws SQLException {
        return new SendGiftResponse(
                rs.getString("gift_order_no"),
                rs.getLong("gift_id"),
                rs.getString("gift_code"),
                rs.getLong("receiver_id"),
                rs.getBigDecimal("total_amount").setScale(MONEY_SCALE, RoundingMode.UNNECESSARY),
                rs.getBigDecimal("platform_share").setScale(MONEY_SCALE, RoundingMode.UNNECESSARY),
                rs.getBigDecimal("receiver_amount").setScale(MONEY_SCALE, RoundingMode.UNNECESSARY),
                rs.getString("debit_ledger_no"),
                rs.getString("receiver_credit_ledger_no"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    private ReceivedGiftItemResponse mapReceivedGift(ResultSet rs) throws SQLException {
        String giftCode = rs.getString("gift_code");
        GiftConfig gift = GIFTS_BY_CODE.get(giftCode);
        return new ReceivedGiftItemResponse(
                rs.getString("gift_order_no"),
                rs.getLong("sender_id"),
                rs.getLong("gift_id"),
                giftCode,
                gift == null ? giftCode : gift.name(),
                gift == null ? "🎁" : gift.icon(),
                rs.getInt("quantity"),
                rs.getBigDecimal("total_amount").setScale(MONEY_SCALE, RoundingMode.UNNECESSARY),
                rs.getBigDecimal("platform_share").setScale(MONEY_SCALE, RoundingMode.UNNECESSARY),
                rs.getBigDecimal("receiver_amount").setScale(MONEY_SCALE, RoundingMode.UNNECESSARY),
                rs.getString("receiver_credit_ledger_no"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    private void insertGiftOrder(Long senderId, SendGiftRequest request, int quantity, SendGiftResponse response, String idempotencyKey) {
        jdbcTemplate.update("""
                INSERT INTO gift_order (
                  gift_order_no, idempotency_key, sender_id, receiver_id, gift_id, gift_code, quantity,
                  total_amount, platform_share, receiver_amount, debit_ledger_no, receiver_credit_ledger_no,
                  status, request_no, client_gift_id, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                response.getGiftOrderNo(), idempotencyKey, senderId, request.getReceiverId(), response.getGiftId(), response.getGiftCode(), quantity,
                response.getTotalAmount(), response.getPlatformShare(), response.getReceiverAmount(), response.getDebitLedgerNo(),
                response.getReceiverCreditLedgerNo(), response.getStatus(), safeText(request.getRequestNo()), safeText(request.getClientGiftId()));
    }

    private static Map<Long, GiftConfig> giftById() {
        Map<Long, GiftConfig> result = new LinkedHashMap<>();
        GIFTS_BY_CODE.values().forEach(gift -> result.put(gift.id(), gift));
        return Map.copyOf(result);
    }

    private void validateSender(Long senderId) {
        if (senderId == null || senderId <= 0) {
            throw new IllegalArgumentException("senderId required");
        }
    }

    private void validateReceiverForQuery(Long receiverId) {
        if (receiverId == null || receiverId <= 0) {
            throw new IllegalArgumentException("receiverId required");
        }
    }

    private void validateRequest(SendGiftRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("gift request required");
        }
        if (request.getReceiverId() == null || request.getReceiverId() <= 0) {
            throw new IllegalArgumentException("receiverId required");
        }
        int quantity = request.getQuantity() == null ? MIN_QUANTITY : request.getQuantity();
        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("gift quantity must be between 1 and 99");
        }
        if (safeText(request.getGiftCode()) == null && request.getGiftId() == null) {
            throw new IllegalArgumentException("giftCode or giftId required");
        }
        if (safeText(request.getRequestNo()) == null && safeText(request.getClientGiftId()) == null) {
            throw new IllegalArgumentException("requestNo or clientGiftId required");
        }
    }

    private GiftConfig resolveGift(SendGiftRequest request) {
        String giftCode = safeText(request.getGiftCode());
        GiftConfig gift = giftCode == null ? null : GIFTS_BY_CODE.get(giftCode.toUpperCase(Locale.ROOT));
        if (gift == null && request.getGiftId() != null) {
            gift = GIFTS_BY_ID.get(request.getGiftId());
        }
        if (gift == null) {
            throw new IllegalArgumentException("unsupported gift");
        }
        return gift;
    }

    private String idempotencyKey(Long senderId, SendGiftRequest request) {
        String explicit = safeText(request.getRequestNo());
        if (explicit != null) {
            return "requestNo:" + senderId + ':' + explicit;
        }
        String clientGiftId = safeText(request.getClientGiftId());
        if (clientGiftId != null) {
            return "clientGiftId:" + senderId + ':' + clientGiftId;
        }
        throw new IllegalArgumentException("requestNo or clientGiftId required");
    }

    private String generateGiftOrderNo(String idempotencyKey) {
        return "GF-" + sha256(idempotencyKey).substring(0, 24).toUpperCase(Locale.ROOT);
    }

    private String nowText() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(Objects.toString(value, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(encoded.length * 2);
            for (byte b : encoded) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private record GiftConfig(Long id, String code, String name, String icon, BigDecimal price, BigDecimal platformRate) {
        private GiftConfig {
            price = price.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
            if (platformRate.compareTo(BigDecimal.ZERO) < 0 || platformRate.compareTo(ONE) >= 0) {
                throw new IllegalArgumentException("invalid platform rate");
            }
        }
    }
}
