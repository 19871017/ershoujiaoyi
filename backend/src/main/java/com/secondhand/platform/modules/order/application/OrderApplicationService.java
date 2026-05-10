package com.secondhand.platform.modules.order.application;

import com.secondhand.platform.modules.order.CreateOrderResponse;
import com.secondhand.platform.modules.order.PayOrderResponse;
import com.secondhand.platform.modules.order.OrderDetailResponse;
import com.secondhand.platform.modules.order.OrderListItemResponse;
import com.secondhand.platform.modules.order.ShipOrderResponse;

import com.secondhand.platform.modules.product.application.ProductApplicationService;
import com.secondhand.platform.modules.product.application.ProductSnapshot;
import com.secondhand.platform.modules.wallet_ledger.application.CreditCommand;
import com.secondhand.platform.modules.wallet_ledger.application.DebitCommand;
import com.secondhand.platform.modules.wallet_ledger.application.LedgerTransactionResponse;
import com.secondhand.platform.modules.wallet_ledger.application.WalletLedgerService;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderApplicationService {
    private static final String STATUS_PENDING_PAY = "PENDING_PAY";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_SHIPPED = "SHIPPED";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String ORDER_PAYMENT = "ORDER_PAYMENT";
    private static final String ORDER_SETTLEMENT = "ORDER_SETTLEMENT";
    private static final String BALANCE_TYPE_RECHARGE = "RECHARGE";
    private static final String BALANCE_TYPE_WITHDRAWABLE = "WITHDRAWABLE";

    private final ProductApplicationService productApplicationService;
    private final WalletLedgerService walletLedgerService;
    private final JdbcTemplate jdbcTemplate;

    public OrderApplicationService(ProductApplicationService productApplicationService,
            WalletLedgerService walletLedgerService, JdbcTemplate jdbcTemplate) {
        this.productApplicationService = productApplicationService;
        this.walletLedgerService = walletLedgerService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest request, Long buyerId) {
        if (request == null || request.getGoodsId() == null) {
            throw new IllegalArgumentException("goodsId required");
        }
        if (buyerId == null || buyerId <= 0) {
            throw new IllegalArgumentException("buyerId required");
        }
        if (!Boolean.TRUE.equals(request.getAcceptedTradeRule())) {
            throw new IllegalArgumentException("trade-rule-not-accepted");
        }

        ProductSnapshot productSnapshot = productApplicationService.snapshotForOrder(request.getGoodsId());
        ensureNoPendingOrder(productSnapshot.getProductId());
        String orderNo = generateNo("OD", productSnapshot.getProductId(), productSnapshot.getProductNo(), buyerId);
        productApplicationService.reserveForOrder(productSnapshot.getProductId(), orderNo);
        jdbcTemplate.update(
                "insert into trade_order (order_no,product_id,goods_id,product_no,product_title,trade_rule_snapshot,buyer_id,seller_id,amount,order_status,accepted_trade_rule,created_at,updated_at) values (?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                orderNo,
                productSnapshot.getProductId(),
                request.getGoodsId(),
                productSnapshot.getProductNo(),
                productSnapshot.getTitle(),
                productSnapshot.getTradeRule(),
                buyerId,
                productSnapshot.getSellerId(),
                productSnapshot.getPrice(),
                STATUS_PENDING_PAY,
                true
        );
        return toResponse(findByOrderNoRequired(orderNo));
    }

    @Transactional
    public PayOrderResponse payOrder(String orderNo, Long userId) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("orderNo required");
        }
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId required");
        }
        OrderRecord order = findByOrderNoRequired(orderNo.trim());
        assertBuyer(order, userId);
        if (STATUS_PAID.equals(order.status())) {
            return toPayResponse(order, true);
        }
        if (!STATUS_PENDING_PAY.equals(order.status())) {
            throw new IllegalStateException("order-not-payable");
        }

        productApplicationService.assertSaleableForOrder(order.productId(), order.orderNo());
        DebitCommand command = new DebitCommand();
        command.setUserId(userId);
        command.setIdempotencyKey("ORDER_PAY:" + order.orderNo() + ':' + userId);
        command.setBizType(ORDER_PAYMENT);
        command.setBizNo(order.orderNo());
        command.setBalanceType(BALANCE_TYPE_RECHARGE);
        command.setAmount(order.amount());

        LedgerTransactionResponse ledger = walletLedgerService.debit(command);
        try {
            productApplicationService.markSold(order.productId(), order.orderNo());
        } catch (RuntimeException ex) {
            jdbcTemplate.update(
                    "update trade_order set paid_user_id = ?, ledger_no = ?, balance_type = ?, balance_before = ?, balance_after = ?, paid_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP where order_no = ?",
                    userId,
                    ledger.ledgerNo(),
                    ledger.balanceType(),
                    ledger.balanceBefore(),
                    ledger.balanceAfter(),
                    order.orderNo()
            );
            throw new IllegalStateException("order-payment-inconsistent", ex);
        }
        int changed = jdbcTemplate.update(
                "update trade_order set order_status = ?, paid_user_id = ?, ledger_no = ?, balance_type = ?, balance_before = ?, balance_after = ?, paid_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP where order_no = ? and order_status = ?",
                STATUS_PAID,
                userId,
                ledger.ledgerNo(),
                ledger.balanceType(),
                ledger.balanceBefore(),
                ledger.balanceAfter(),
                order.orderNo(),
                STATUS_PENDING_PAY
        );
        if (changed == 0) {
            return toPayResponse(findByOrderNoRequired(order.orderNo()), true);
        }
        return toPayResponse(findByOrderNoRequired(order.orderNo()), ledger.idempotentReplay());
    }

    @Transactional
    public ShipOrderResponse shipOrder(String orderNo, Long sellerId, ShipOrderRequest request) {
        if (sellerId == null || sellerId <= 0) throw new IllegalArgumentException("sellerId required");
        String safeOrderNo = requireText(orderNo, "orderNo required");
        OrderRecord order = findByOrderNoRequired(safeOrderNo);
        if (!Objects.equals(order.sellerId(), sellerId)) throw new IllegalArgumentException("order-seller-mismatch");
        if (!STATUS_PAID.equals(order.status())) throw new IllegalStateException("order-not-shippable");
        if (request == null) throw new IllegalArgumentException("shipping required");
        String shippingType = normalizeShippingType(request.getShippingType());
        String company = trimLimit(request.getShippingCompany(), 64);
        String trackingNo = trimLimit(request.getTrackingNo(), 128);
        String remark = trimLimit(request.getRemark(), 255);
        if ("EXPRESS".equals(shippingType) && (company == null || trackingNo == null)) {
            throw new IllegalArgumentException("express-shipping-info-required");
        }
        if ("MEETUP".equals(shippingType) && remark == null) {
            throw new IllegalArgumentException("meetup-remark-required");
        }
        int changed = jdbcTemplate.update("""
                update trade_order set order_status = 'SHIPPED', shipped_at = CURRENT_TIMESTAMP, shipped_by = ?,
                    shipping_type = ?, shipping_company = ?, tracking_no = ?, shipping_remark = ?, updated_at = CURRENT_TIMESTAMP
                where order_no = ? and seller_id = ? and order_status = 'PAID'
                """, sellerId, shippingType, company, trackingNo, remark, safeOrderNo, sellerId);
        if (changed == 0) throw new IllegalStateException("order-not-shippable");
        OrderRecord shipped = findByOrderNoRequired(safeOrderNo);
        return new ShipOrderResponse(shipped.orderNo(), shipped.status(), shipped.shippingType(), shipped.shippingCompany(), shipped.trackingNo(), shipped.shippingRemark(), shipped.shippedAt());
    }

    @Transactional
    public OrderDetailResponse confirmReceipt(String orderNo, Long buyerId) {
        if (buyerId == null || buyerId <= 0) throw new IllegalArgumentException("buyerId required");
        String safeOrderNo = requireText(orderNo, "orderNo required");
        OrderRecord order = findByOrderNoRequired(safeOrderNo);
        assertBuyer(order, buyerId);
        if (STATUS_COMPLETED.equals(order.status())) {
            return detailOrder(safeOrderNo, buyerId);
        }
        if (!STATUS_SHIPPED.equals(order.status())) {
            throw new IllegalStateException("order-not-confirmable");
        }
        if (hasActiveAfterSales(safeOrderNo, buyerId)) {
            throw new IllegalStateException("order-after-sales-active");
        }

        CreditCommand settlement = new CreditCommand();
        settlement.setUserId(order.sellerId());
        settlement.setIdempotencyKey("ORDER_SETTLE:" + safeOrderNo + ':' + order.sellerId());
        settlement.setBizType(ORDER_SETTLEMENT);
        settlement.setBizNo(safeOrderNo);
        settlement.setBalanceType(BALANCE_TYPE_WITHDRAWABLE);
        settlement.setAmount(order.amount());
        walletLedgerService.credit(settlement);

        int changed = jdbcTemplate.update("""
                update trade_order set order_status = ?, completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                where order_no = ? and buyer_id = ? and order_status = ?
                """, STATUS_COMPLETED, safeOrderNo, buyerId, STATUS_SHIPPED);
        if (changed == 0) {
            return detailOrder(safeOrderNo, buyerId);
        }
        return detailOrder(safeOrderNo, buyerId);
    }

    @Transactional
    public OrderReviewResponse submitReview(String orderNo, Long reviewerId, OrderReviewRequest request) {
        if (reviewerId == null || reviewerId <= 0) throw new IllegalArgumentException("reviewerId required");
        if (request == null) throw new IllegalArgumentException("review request required");
        String safeOrderNo = requireText(orderNo, "orderNo required");
        OrderRecord order = findByOrderNoRequired(safeOrderNo);
        assertBuyer(order, reviewerId);
        if (!STATUS_COMPLETED.equals(order.status())) throw new IllegalStateException("order-not-reviewable");
        int descriptionScore = validateScore(request.getDescriptionScore(), "descriptionScore invalid");
        int serviceScore = validateScore(request.getServiceScore(), "serviceScore invalid");
        int shippingScore = validateScore(request.getShippingScore(), "shippingScore invalid");
        String content = validateReviewContent(request.getContent());
        String reviewNo = generateNo("RV", safeOrderNo, reviewerId, order.sellerId());
        try {
            jdbcTemplate.update("""
                    insert into order_review (review_no,order_no,reviewer_id,reviewee_id,description_score,service_score,shipping_score,content,created_at)
                    values (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                    """, reviewNo, safeOrderNo, reviewerId, order.sellerId(), descriptionScore, serviceScore, shippingScore, content);
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw new IllegalStateException("order-review-already-submitted");
        }
        return findReviewByNo(reviewNo);
    }

    public OrderDetailResponse detailOrder(String orderNo, Long userId) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("userId required");
        String safeOrderNo = requireText(orderNo, "orderNo required");
        try {
            return jdbcTemplate.queryForObject("""
                    select o.*, a.after_sales_no, a.after_sales_status
                    from trade_order o
                    left join after_sales_record a on a.order_no = o.order_no and a.applicant_id = o.buyer_id
                    where o.order_no = ? and (o.buyer_id = ? or o.seller_id = ?)
                    """, (rs, rowNum) -> new OrderDetailResponse(
                    rs.getString("order_no"), rs.getLong("buyer_id"), rs.getLong("seller_id"), rs.getLong("product_id"), rs.getLong("goods_id"),
                    rs.getString("product_no"), rs.getString("product_title"), rs.getBigDecimal("amount"), rs.getString("trade_rule_snapshot"),
                    rs.getString("order_status"), Objects.equals(rs.getLong("buyer_id"), userId) ? "卖家" : "买家", rs.getString("after_sales_no"), rs.getString("after_sales_status"),
                    rs.getString("shipping_type"), rs.getString("shipping_company"), rs.getString("tracking_no"), rs.getString("shipping_remark"),
                    timeText(rs.getTimestamp("created_at")), timeText(rs.getTimestamp("paid_at")), timeText(rs.getTimestamp("shipped_at")), timeText(rs.getTimestamp("completed_at"))
            ), safeOrderNo, userId, userId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("order-not-found");
        }
    }

    public OrderDetailResponse adminDetailOrder(String orderNo) {
        String safeOrderNo = requireAdminOrderNo(orderNo);
        try {
            return jdbcTemplate.queryForObject("""
                    select o.*, a.after_sales_no, a.after_sales_status
                    from trade_order o
                    left join after_sales_record a on a.order_no = o.order_no and a.applicant_id = o.buyer_id
                    where o.order_no = ?
                    """, (rs, rowNum) -> new OrderDetailResponse(
                    rs.getString("order_no"), rs.getLong("buyer_id"), rs.getLong("seller_id"), rs.getLong("product_id"), rs.getLong("goods_id"),
                    rs.getString("product_no"), rs.getString("product_title"), rs.getBigDecimal("amount"), rs.getString("trade_rule_snapshot"),
                    rs.getString("order_status"), "后台", rs.getString("after_sales_no"), rs.getString("after_sales_status"),
                    rs.getString("shipping_type"), rs.getString("shipping_company"), rs.getString("tracking_no"), rs.getString("shipping_remark"),
                    timeText(rs.getTimestamp("created_at")), timeText(rs.getTimestamp("paid_at")), timeText(rs.getTimestamp("shipped_at")), timeText(rs.getTimestamp("completed_at"))
            ), safeOrderNo);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("order-not-found");
        }
    }

    public List<OrderListItemResponse> adminListOrders(String status, Integer limit) {
        String safeStatus = normalizeStatusFilter(status);
        int safeLimit = limit == null ? 20 : limit;
        if (safeLimit < 1 || safeLimit > 100) {
            throw new IllegalArgumentException("order limit invalid");
        }
        StringBuilder sql = new StringBuilder("""
                select o.*, a.after_sales_no, a.after_sales_status
                from trade_order o
                left join after_sales_record a on a.order_no = o.order_no and a.applicant_id = o.buyer_id
                where 1 = 1
                """);
        List<Object> args = new java.util.ArrayList<>();
        if (!"ALL".equals(safeStatus)) {
            if ("REFUNDING".equals(safeStatus)) {
                sql.append(" and a.after_sales_no is not null");
            } else {
                sql.append(" and o.order_status = ?");
                args.add(safeStatus);
            }
        }
        sql.append(" order by o.created_at desc limit ?");
        args.add(safeLimit);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new OrderListItemResponse(
                rs.getString("order_no"), rs.getLong("buyer_id"), rs.getLong("seller_id"), rs.getLong("product_id"), rs.getLong("goods_id"),
                rs.getString("product_no"), rs.getString("product_title"), rs.getBigDecimal("amount"), rs.getString("trade_rule_snapshot"),
                rs.getString("order_status"), "admin", "后台", rs.getString("after_sales_no"), rs.getString("after_sales_status"), timeText(rs.getTimestamp("created_at"))
        ), args.toArray());
    }

    public List<OrderListItemResponse> listOrders(Long userId, String role, String status) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("userId required");
        String safeRole = normalizeRole(role);
        String safeStatus = normalizeStatusFilter(status);
        boolean buyer = "buyer".equals(safeRole);
        StringBuilder sql = new StringBuilder("""
                select o.*, a.after_sales_no, a.after_sales_status
                from trade_order o
                left join after_sales_record a on a.order_no = o.order_no and a.applicant_id = o.buyer_id
                where %s = ?
                """.formatted(buyer ? "o.buyer_id" : "o.seller_id"));
        List<Object> args = new java.util.ArrayList<>();
        args.add(userId);
        if (!"ALL".equals(safeStatus)) {
            if ("REFUNDING".equals(safeStatus)) {
                sql.append(" and a.after_sales_no is not null");
            } else {
                sql.append(" and o.order_status = ?");
                args.add(safeStatus);
            }
        }
        sql.append(" order by o.created_at desc limit 100");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new OrderListItemResponse(
                rs.getString("order_no"), rs.getLong("buyer_id"), rs.getLong("seller_id"), rs.getLong("product_id"), rs.getLong("goods_id"),
                rs.getString("product_no"), rs.getString("product_title"), rs.getBigDecimal("amount"), rs.getString("trade_rule_snapshot"),
                rs.getString("order_status"), safeRole, buyer ? "卖家" : "买家", rs.getString("after_sales_no"), rs.getString("after_sales_status"), timeText(rs.getTimestamp("created_at"))
        ), args.toArray());
    }

    private void ensureNoPendingOrder(Long productId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from trade_order where product_id = ? and order_status = ?",
                Integer.class,
                productId,
                STATUS_PENDING_PAY
        );
        if (count != null && count > 0) {
            throw new IllegalArgumentException("product already has pending order");
        }
    }

    private OrderRecord findByOrderNoRequired(String orderNo) {
        try {
            return jdbcTemplate.queryForObject(
                    "select * from trade_order where order_no = ?",
                    (rs, rowNum) -> new OrderRecord(
                            rs.getString("order_no"),
                            rs.getLong("buyer_id"),
                            rs.getLong("seller_id"),
                            rs.getLong("goods_id"),
                            rs.getLong("product_id"),
                            rs.getString("product_no"),
                            rs.getString("product_title"),
                            rs.getBigDecimal("amount"),
                            rs.getString("trade_rule_snapshot"),
                            rs.getString("order_status"),
                            rs.getBoolean("accepted_trade_rule"),
                            timeText(rs.getTimestamp("created_at")),
                            rs.getLong("paid_user_id") == 0 ? null : rs.getLong("paid_user_id"),
                            rs.getString("ledger_no"),
                            rs.getString("balance_type"),
                            rs.getBigDecimal("balance_before"),
                            rs.getBigDecimal("balance_after"),
                            timeText(rs.getTimestamp("paid_at")),
                            timeText(rs.getTimestamp("shipped_at")),
                            rs.getString("shipping_type"),
                            rs.getString("shipping_company"),
                            rs.getString("tracking_no"),
                            rs.getString("shipping_remark"),
                            timeText(rs.getTimestamp("completed_at"))
                    ),
                    orderNo
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("order-not-found");
        }
    }

    private void assertBuyer(OrderRecord order, Long userId) {
        if (!Objects.equals(order.buyerId(), userId)) {
            throw new IllegalArgumentException("order-buyer-mismatch");
        }
    }

    private OrderReviewResponse findReviewByNo(String reviewNo) {
        return jdbcTemplate.queryForObject("""
                select * from order_review where review_no = ?
                """, (rs, rowNum) -> new OrderReviewResponse(
                rs.getString("review_no"),
                rs.getString("order_no"),
                rs.getLong("reviewer_id"),
                rs.getLong("reviewee_id"),
                rs.getInt("description_score"),
                rs.getInt("service_score"),
                rs.getInt("shipping_score"),
                rs.getString("content"),
                timeText(rs.getTimestamp("created_at"))
        ), reviewNo);
    }

    private int validateScore(Integer score, String message) {
        if (score == null || score < 1 || score > 5) throw new IllegalArgumentException(message);
        return score;
    }

    private String validateReviewContent(String content) {
        String safe = requireText(content, "review content required");
        if (safe.length() < 6 || safe.length() > 160) throw new IllegalArgumentException("review content invalid");
        if (safe.contains("\n") || safe.contains("\r")) throw new IllegalArgumentException("review content invalid");
        return safe;
    }

    private boolean hasActiveAfterSales(String orderNo, Long buyerId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from after_sales_record
                where order_no = ? and applicant_id = ? and after_sales_status not in ('REJECTED','CANCELLED','CLOSED')
                """, Integer.class, orderNo, buyerId);
        return count != null && count > 0;
    }

    private CreateOrderResponse toResponse(OrderRecord order) {
        return new CreateOrderResponse(order.orderNo(), order.buyerId(), order.goodsId(), order.productId(), order.productNo(),
                order.productTitle(), order.amount(), order.tradeRuleSnapshot(), order.status(), order.acceptedTradeRule(), order.createdAt());
    }

    private PayOrderResponse toPayResponse(OrderRecord order, boolean idempotentReplay) {
        return new PayOrderResponse(order.orderNo(), order.buyerId(), order.goodsId(), order.productId(), order.productNo(),
                order.productTitle(), order.amount(), order.status(), order.ledgerNo(), order.balanceType(),
                order.balanceBefore(), order.balanceAfter(), order.paidAt(), idempotentReplay);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private String requireAdminOrderNo(String value) {
        String safe = requireText(value, "orderNo required");
        if (!safe.matches("^OD-[A-Z0-9]{4,}$")) throw new IllegalArgumentException("orderNo invalid");
        return safe;
    }

    private String trimLimit(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > max) throw new IllegalArgumentException("field-too-long");
        if (trimmed.contains("\n") || trimmed.contains("\r")) throw new IllegalArgumentException("field-invalid");
        return trimmed;
    }

    private String normalizeShippingType(String value) {
        if (value == null || value.isBlank()) return "EXPRESS";
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "EXPRESS", "快递邮寄" -> "EXPRESS";
            case "MEETUP", "同城当面交付" -> "MEETUP";
            default -> throw new IllegalArgumentException("shipping-type-invalid");
        };
    }

    private String timeText(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        LocalDateTime time = timestamp.toLocalDateTime();
        return time.toString();
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank() || "buyer".equalsIgnoreCase(role)) return "buyer";
        if ("seller".equalsIgnoreCase(role)) return "seller";
        throw new IllegalArgumentException("order role invalid");
    }

    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) return "ALL";
        String value = status.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "PENDING_PAY", "PAID", "SHIPPED", "COMPLETED", "REFUNDING" -> value;
            default -> throw new IllegalArgumentException("order status invalid");
        };
    }

    private String generateNo(String prefix, Object a, Object b, Object c) {
        String seed = Objects.toString(a, "") + ':' + Objects.toString(b, "") + ':' + Objects.toString(c, "") + ':' + System.nanoTime();
        return prefix + '-' + Integer.toUnsignedString(seed.hashCode()).toUpperCase(Locale.ROOT);
    }

    private record OrderRecord(String orderNo, Long buyerId, Long sellerId, Long goodsId, Long productId, String productNo,
                               String productTitle, java.math.BigDecimal amount, String tradeRuleSnapshot,
                               String status, Boolean acceptedTradeRule, String createdAt, Long paidUserId,
                               String ledgerNo, String balanceType, java.math.BigDecimal balanceBefore,
                               java.math.BigDecimal balanceAfter, String paidAt, String shippedAt,
                               String shippingType, String shippingCompany, String trackingNo, String shippingRemark,
                               String completedAt) {
    }
}
