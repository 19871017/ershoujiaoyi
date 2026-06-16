package com.secondhand.platform.modules.order.application;

import com.secondhand.platform.modules.order.CreateOrderResponse;
import com.secondhand.platform.modules.order.PayOrderResponse;
import com.secondhand.platform.modules.order.OrderDetailResponse;
import com.secondhand.platform.modules.order.OrderListItemResponse;
import com.secondhand.platform.modules.order.ShipOrderResponse;

import com.secondhand.platform.modules.notification.application.NotificationApplicationService;
import com.secondhand.platform.modules.product.application.ProductApplicationService;
import com.secondhand.platform.modules.product.application.ProductSnapshot;
import com.secondhand.platform.modules.wallet_ledger.application.CreditCommand;
import com.secondhand.platform.modules.wallet_ledger.application.DebitCommand;
import com.secondhand.platform.modules.wallet_ledger.application.LedgerTransactionResponse;
import com.secondhand.platform.modules.wallet_ledger.application.WalletLedgerService;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String ORDER_PAYMENT = "ORDER_PAYMENT";
    private static final String ORDER_SETTLEMENT = "ORDER_SETTLEMENT";
    private static final String BALANCE_TYPE_RECHARGE = "RECHARGE";
    private static final String BALANCE_TYPE_WITHDRAWABLE = "WITHDRAWABLE";

    private final ProductApplicationService productApplicationService;
    private final WalletLedgerService walletLedgerService;
    private final JdbcTemplate jdbcTemplate;
    private final NotificationApplicationService notificationApplicationService;

    public OrderApplicationService(ProductApplicationService productApplicationService,
            WalletLedgerService walletLedgerService, JdbcTemplate jdbcTemplate) {
        this(productApplicationService, walletLedgerService, jdbcTemplate, new NotificationApplicationService(jdbcTemplate));
    }

    @Autowired
    public OrderApplicationService(ProductApplicationService productApplicationService,
            WalletLedgerService walletLedgerService, JdbcTemplate jdbcTemplate,
            NotificationApplicationService notificationApplicationService) {
        this.productApplicationService = productApplicationService;
        this.walletLedgerService = walletLedgerService;
        this.jdbcTemplate = jdbcTemplate;
        this.notificationApplicationService = notificationApplicationService;
    }

    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest request, Long buyerId) {
        ensureOrderPricingSchemaCompatibility();
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
        if (Objects.equals(productSnapshot.getSellerId(), buyerId)) {
            throw new IllegalArgumentException("cannot buy your own product");
        }
        ensureNoPendingOrder(productSnapshot.getProductId());
        String orderNo = generateNo("OD", productSnapshot.getProductId(), productSnapshot.getProductNo(), buyerId);
        productApplicationService.reserveForOrder(productSnapshot.getProductId(), orderNo);
        jdbcTemplate.update(
                "insert into trade_order (order_no,product_id,goods_id,product_no,product_title,trade_rule_snapshot,buyer_id,seller_id,amount,seller_amount,platform_markup_rate,platform_markup_amount,order_status,accepted_trade_rule,created_at,updated_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                orderNo,
                productSnapshot.getProductId(),
                request.getGoodsId(),
                productSnapshot.getProductNo(),
                productSnapshot.getTitle(),
                productSnapshot.getTradeRule(),
                buyerId,
                productSnapshot.getSellerId(),
                productSnapshot.getBuyerPrice(),
                productSnapshot.getSellerPrice(),
                productSnapshot.getPlatformMarkupRate(),
                productSnapshot.getPlatformMarkupAmount(),
                STATUS_PENDING_PAY,
                true
        );
        return toResponse(findByOrderNoRequired(orderNo));
    }

    @Transactional
    public PayOrderResponse payOrder(String orderNo, Long userId) {
        ensureOrderPricingSchemaCompatibility();
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
            throw new IllegalStateException("order-payment-state-update-failed");
        }
        OrderRecord paid = findByOrderNoRequired(order.orderNo());
        notifyOrderPaid(paid);
        return toPayResponse(paid, ledger.idempotentReplay());
    }

    @Transactional
    public PayOrderResponse markOrderPaidByExternal(String orderNo, Long userId, String paymentNo, String channel) {
        ensureOrderPricingSchemaCompatibility();
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("orderNo required");
        }
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId required");
        }
        String safePaymentNo = requireText(paymentNo, "paymentNo required");
        String safeChannel = requireText(channel, "payment channel required").toUpperCase(Locale.ROOT);
        if (!"ALIPAY".equals(safeChannel) && !"WECHAT".equals(safeChannel)) {
            throw new IllegalArgumentException("payment channel invalid");
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
        productApplicationService.markSold(order.productId(), order.orderNo());
        int changed = jdbcTemplate.update(
                "update trade_order set order_status = ?, paid_user_id = ?, ledger_no = ?, balance_type = ?, balance_before = null, balance_after = null, paid_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP where order_no = ? and order_status = ?",
                STATUS_PAID,
                userId,
                safePaymentNo,
                "EXTERNAL_" + safeChannel,
                order.orderNo(),
                STATUS_PENDING_PAY
        );
        if (changed == 0) {
            throw new IllegalStateException("order-payment-state-update-failed");
        }
        OrderRecord paid = findByOrderNoRequired(order.orderNo());
        notifyOrderPaid(paid);
        return toPayResponse(paid, false);
    }

    @Transactional
    public OrderDetailResponse cancelPendingOrder(String orderNo, Long buyerId) {
        ensureOrderPricingSchemaCompatibility();
        if (buyerId == null || buyerId <= 0) throw new IllegalArgumentException("buyerId required");
        String safeOrderNo = requireText(orderNo, "orderNo required");
        OrderRecord order = findByOrderNoRequired(safeOrderNo);
        assertBuyer(order, buyerId);
        if (STATUS_CANCELLED.equals(order.status())) {
            return detailOrder(safeOrderNo, buyerId);
        }
        if (!STATUS_PENDING_PAY.equals(order.status())) {
            throw new IllegalStateException("order-not-cancellable");
        }
        productApplicationService.releaseOrderLock(order.productId(), order.orderNo());
        int changed = jdbcTemplate.update("""
                update trade_order set order_status = ?, updated_at = CURRENT_TIMESTAMP
                where order_no = ? and buyer_id = ? and order_status = ?
                """, STATUS_CANCELLED, safeOrderNo, buyerId, STATUS_PENDING_PAY);
        if (changed == 0) {
            throw new IllegalStateException("order-cancel-state-update-failed");
        }
        notifyOrderCancelled(findByOrderNoRequired(safeOrderNo));
        return detailOrder(safeOrderNo, buyerId);
    }

    @Transactional
    public ShipOrderResponse shipOrder(String orderNo, Long sellerId, ShipOrderRequest request) {
        ensureOrderPricingSchemaCompatibility();
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
        notifyOrderShipped(shipped);
        return new ShipOrderResponse(shipped.orderNo(), shipped.status(), shipped.shippingType(), shipped.shippingCompany(), shipped.trackingNo(), shipped.shippingRemark(), shipped.shippedAt());
    }

    @Transactional
    public OrderDetailResponse confirmReceipt(String orderNo, Long buyerId) {
        ensureOrderPricingSchemaCompatibility();
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
        settlement.setAmount(order.sellerAmount());
        walletLedgerService.credit(settlement);

        int changed = jdbcTemplate.update("""
                update trade_order set order_status = ?, completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                where order_no = ? and buyer_id = ? and order_status = ?
                """, STATUS_COMPLETED, safeOrderNo, buyerId, STATUS_SHIPPED);
        if (changed == 0) {
            throw new IllegalStateException("order-confirm-state-update-failed");
        }
        notifyOrderCompleted(findByOrderNoRequired(safeOrderNo));
        return detailOrder(safeOrderNo, buyerId);
    }

    @Transactional
    public OrderReviewResponse submitReview(String orderNo, Long reviewerId, OrderReviewRequest request) {
        ensureOrderPricingSchemaCompatibility();
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
        notifyOrderReviewed(order);
        return findReviewByNo(reviewNo);
    }

    public OrderDetailResponse detailOrder(String orderNo, Long userId) {
        ensureOrderPricingSchemaCompatibility();
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
                    rs.getString("product_no"), rs.getString("product_title"), rs.getBigDecimal("amount"), sellerAmount(rs), rs.getBigDecimal("platform_markup_rate"), rs.getBigDecimal("platform_markup_amount"), rs.getString("trade_rule_snapshot"),
                    rs.getString("order_status"), Objects.equals(rs.getLong("buyer_id"), userId) ? "buyer" : "seller", Objects.equals(rs.getLong("buyer_id"), userId) ? "卖家" : "买家", rs.getString("after_sales_no"), rs.getString("after_sales_status"),
                    rs.getString("shipping_type"), rs.getString("shipping_company"), rs.getString("tracking_no"), rs.getString("shipping_remark"),
                    timeText(rs.getTimestamp("created_at")), timeText(rs.getTimestamp("paid_at")), timeText(rs.getTimestamp("shipped_at")), timeText(rs.getTimestamp("completed_at"))
            ), safeOrderNo, userId, userId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("order-not-found");
        }
    }

    public OrderDetailResponse adminDetailOrder(String orderNo) {
        ensureOrderPricingSchemaCompatibility();
        String safeOrderNo = requireAdminOrderNo(orderNo);
        try {
            return jdbcTemplate.queryForObject("""
                    select o.*, a.after_sales_no, a.after_sales_status
                    from trade_order o
                    left join after_sales_record a on a.order_no = o.order_no and a.applicant_id = o.buyer_id
                    where o.order_no = ?
                    """, (rs, rowNum) -> new OrderDetailResponse(
                    rs.getString("order_no"), rs.getLong("buyer_id"), rs.getLong("seller_id"), rs.getLong("product_id"), rs.getLong("goods_id"),
                    rs.getString("product_no"), rs.getString("product_title"), rs.getBigDecimal("amount"), sellerAmount(rs), rs.getBigDecimal("platform_markup_rate"), rs.getBigDecimal("platform_markup_amount"), rs.getString("trade_rule_snapshot"),
                    rs.getString("order_status"), "admin", "后台", rs.getString("after_sales_no"), rs.getString("after_sales_status"),
                    rs.getString("shipping_type"), rs.getString("shipping_company"), rs.getString("tracking_no"), rs.getString("shipping_remark"),
                    timeText(rs.getTimestamp("created_at")), timeText(rs.getTimestamp("paid_at")), timeText(rs.getTimestamp("shipped_at")), timeText(rs.getTimestamp("completed_at"))
            ), safeOrderNo);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("order-not-found");
        }
    }

    public List<OrderListItemResponse> adminListOrders(String status, Integer limit) {
        return adminListOrders(status, null, limit);
    }

    public List<OrderListItemResponse> adminListOrders(String status, String keyword, Integer limit) {
        ensureOrderPricingSchemaCompatibility();
        String safeStatus = normalizeStatusFilter(status);
        String safeKeyword = normalizeAdminKeyword(keyword);
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
        if (safeKeyword != null) {
            if (safeKeyword.matches("\\d+")) {
                Long numericKeyword = parseNumericKeyword(safeKeyword);
                sql.append(" and (o.buyer_id = ? or o.seller_id = ? or o.product_id = ? or o.goods_id = ?)");
                args.add(numericKeyword);
                args.add(numericKeyword);
                args.add(numericKeyword);
                args.add(numericKeyword);
            } else {
                sql.append("""
                         and (lower(o.order_no) like ? escape '\\'
                         or lower(o.product_no) like ? escape '\\'
                         or lower(o.product_title) like ? escape '\\'
                         or lower(o.tracking_no) like ? escape '\\'
                         or lower(a.after_sales_no) like ? escape '\\')
                        """);
                String likeKeyword = "%" + escapeLike(safeKeyword.toLowerCase(Locale.ROOT)) + "%";
                args.add(likeKeyword);
                args.add(likeKeyword);
                args.add(likeKeyword);
                args.add(likeKeyword);
                args.add(likeKeyword);
            }
        }
        sql.append(" order by o.created_at desc limit ?");
        args.add(safeLimit);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new OrderListItemResponse(
                rs.getString("order_no"), rs.getLong("buyer_id"), rs.getLong("seller_id"), rs.getLong("product_id"), rs.getLong("goods_id"),
                rs.getString("product_no"), rs.getString("product_title"), rs.getBigDecimal("amount"), sellerAmount(rs), rs.getBigDecimal("platform_markup_rate"), rs.getBigDecimal("platform_markup_amount"), rs.getString("trade_rule_snapshot"),
                rs.getString("order_status"), "admin", "后台", rs.getString("after_sales_no"), rs.getString("after_sales_status"), timeText(rs.getTimestamp("created_at"))
        ), args.toArray());
    }

    public List<OrderListItemResponse> listOrders(Long userId, String role, String status) {
        ensureOrderPricingSchemaCompatibility();
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
                rs.getString("product_no"), rs.getString("product_title"), rs.getBigDecimal("amount"), sellerAmount(rs), rs.getBigDecimal("platform_markup_rate"), rs.getBigDecimal("platform_markup_amount"), rs.getString("trade_rule_snapshot"),
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
        ensureOrderPricingSchemaCompatibility();
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
                            sellerAmount(rs),
                            rs.getBigDecimal("platform_markup_rate"),
                            rs.getBigDecimal("platform_markup_amount"),
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

    private void notifyOrderPaid(OrderRecord order) {
        if (!hasNotificationTarget(order)) {
            return;
        }
        notificationApplicationService.createNotification(
                order.sellerId(),
                "ORDER",
                "买家已付款",
                "订单 " + order.orderNo() + " 已付款，请尽快安排发货。",
                orderDetailTargetUrl(order)
        );
    }

    private void notifyOrderCancelled(OrderRecord order) {
        if (!hasNotificationTarget(order)) {
            return;
        }
        notificationApplicationService.createNotification(
                order.sellerId(),
                "ORDER",
                "买家已取消订单",
                "订单 " + order.orderNo() + " 已取消，商品锁定已释放。",
                orderDetailTargetUrl(order)
        );
    }

    private void notifyOrderShipped(OrderRecord order) {
        if (!hasNotificationTarget(order)) {
            return;
        }
        notificationApplicationService.createNotification(
                order.buyerId(),
                "ORDER",
                "卖家已发货",
                "订单 " + order.orderNo() + " 已发货，请在订单详情查看物流或交付信息。",
                orderDetailTargetUrl(order)
        );
    }

    private void notifyOrderCompleted(OrderRecord order) {
        if (!hasNotificationTarget(order)) {
            return;
        }
        notificationApplicationService.createNotification(
                order.sellerId(),
                "ORDER",
                "订单已完成",
                "订单 " + order.orderNo() + " 已确认收货，结算记录以钱包账本为准。",
                orderDetailTargetUrl(order)
        );
    }

    private void notifyOrderReviewed(OrderRecord order) {
        if (!hasNotificationTarget(order)) {
            return;
        }
        notificationApplicationService.createNotification(
                order.sellerId(),
                "ORDER",
                "买家已评价",
                "订单 " + order.orderNo() + " 已收到买家评价，可在订单详情查看。",
                orderDetailTargetUrl(order)
        );
    }

    private String orderDetailTargetUrl(OrderRecord order) {
        return "/pages/order/detail/index?orderNo=" + order.orderNo();
    }

    private boolean hasNotificationTarget(OrderRecord order) {
        return order != null
                && order.orderNo() != null
                && order.orderNo().matches("OD-[0-9]{1,10}")
                && order.buyerId() != null
                && order.buyerId() > 0
                && order.sellerId() != null
                && order.sellerId() > 0;
    }

    private CreateOrderResponse toResponse(OrderRecord order) {
        return new CreateOrderResponse(order.orderNo(), order.buyerId(), order.goodsId(), order.productId(), order.productNo(),
                order.productTitle(), order.amount(), order.sellerAmount(), order.platformMarkupRate(), order.platformMarkupAmount(),
                order.tradeRuleSnapshot(), order.status(), order.acceptedTradeRule(), order.createdAt());
    }

    private PayOrderResponse toPayResponse(OrderRecord order, boolean idempotentReplay) {
        return new PayOrderResponse(order.orderNo(), order.buyerId(), order.goodsId(), order.productId(), order.productNo(),
                order.productTitle(), order.amount(), order.sellerAmount(), order.platformMarkupRate(), order.platformMarkupAmount(), order.status(), order.ledgerNo(), order.balanceType(),
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
            case "PENDING_PAY", "PAID", "SHIPPED", "COMPLETED", "CANCELLED", "REFUNDING" -> value;
            default -> throw new IllegalArgumentException("order status invalid");
        };
    }

    private String normalizeAdminKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String normalized = keyword.trim();
        if (normalized.length() > 64) throw new IllegalArgumentException("order keyword invalid");
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("preview") || lower.contains("demo") || lower.contains("mock") || lower.contains("sample") || lower.contains("placeholder")) {
            throw new IllegalArgumentException("order keyword invalid");
        }
        if (normalized.matches("\\d+") && normalized.length() > 18) throw new IllegalArgumentException("order keyword invalid");
        return normalized;
    }

    private Long parseNumericKeyword(String keyword) {
        try {
            return Long.valueOf(keyword);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("order keyword invalid");
        }
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private BigDecimal sellerAmount(ResultSet rs) throws SQLException {
        BigDecimal sellerAmount = rs.getBigDecimal("seller_amount");
        return sellerAmount == null || sellerAmount.compareTo(BigDecimal.ZERO) == 0 ? rs.getBigDecimal("amount") : sellerAmount;
    }

    private void ensureOrderPricingSchemaCompatibility() {
        ensureColumn("trade_order", "seller_amount", "ALTER TABLE trade_order ADD COLUMN seller_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00");
        ensureColumn("trade_order", "platform_markup_rate", "ALTER TABLE trade_order ADD COLUMN platform_markup_rate DECIMAL(8,4) NOT NULL DEFAULT 0.0000");
        ensureColumn("trade_order", "platform_markup_amount", "ALTER TABLE trade_order ADD COLUMN platform_markup_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00");
        jdbcTemplate.update("update trade_order set seller_amount = amount where seller_amount = 0.00");
    }

    private void ensureColumn(String tableName, String columnName, String alterSql) {
        try {
            if (!columnExists(tableName, columnName)) {
                jdbcTemplate.execute(alterSql);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("order schema compatibility check failed: " + tableName + "." + columnName, ex);
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        if (jdbcTemplate.getDataSource() == null) {
            throw new SQLException("dataSource unavailable");
        }
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            return columnExists(metaData, tableName, columnName)
                    || columnExists(metaData, tableName.toUpperCase(Locale.ROOT), columnName.toUpperCase(Locale.ROOT))
                    || columnExists(metaData, tableName.toLowerCase(Locale.ROOT), columnName.toLowerCase(Locale.ROOT));
        }
    }

    private boolean columnExists(DatabaseMetaData metaData, String tableName, String columnName) throws SQLException {
        try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
            return columns.next();
        }
    }

    private String generateNo(String prefix, Object a, Object b, Object c) {
        String seed = Objects.toString(a, "") + ':' + Objects.toString(b, "") + ':' + Objects.toString(c, "") + ':' + System.nanoTime();
        return prefix + '-' + Integer.toUnsignedString(seed.hashCode()).toUpperCase(Locale.ROOT);
    }

    private record OrderRecord(String orderNo, Long buyerId, Long sellerId, Long goodsId, Long productId, String productNo,
                               String productTitle, java.math.BigDecimal amount, java.math.BigDecimal sellerAmount,
                               java.math.BigDecimal platformMarkupRate, java.math.BigDecimal platformMarkupAmount, String tradeRuleSnapshot,
                               String status, Boolean acceptedTradeRule, String createdAt, Long paidUserId,
                               String ledgerNo, String balanceType, java.math.BigDecimal balanceBefore,
                               java.math.BigDecimal balanceAfter, String paidAt, String shippedAt,
                               String shippingType, String shippingCompany, String trackingNo, String shippingRemark,
                               String completedAt) {
    }
}
