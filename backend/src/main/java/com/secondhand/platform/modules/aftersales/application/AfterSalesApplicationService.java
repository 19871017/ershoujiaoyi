package com.secondhand.platform.modules.aftersales.application;

import com.secondhand.platform.modules.aftersales.AfterSalesResponse;
import com.secondhand.platform.modules.media.application.MediaUploadTicketService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AfterSalesApplicationService {
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    private final JdbcTemplate jdbcTemplate;
    private final MediaUploadTicketService mediaUploadTicketService;

    public AfterSalesApplicationService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new MediaUploadTicketService(jdbcTemplate));
    }

    @Autowired
    public AfterSalesApplicationService(JdbcTemplate jdbcTemplate, MediaUploadTicketService mediaUploadTicketService) {
        this.jdbcTemplate = jdbcTemplate;
        this.mediaUploadTicketService = mediaUploadTicketService;
    }

    @Transactional
    public AfterSalesResponse create(Long applicantId, CreateAfterSalesRequest request) {
        if (applicantId == null || applicantId <= 0) throw new IllegalArgumentException("applicantId required");
        if (request == null) throw new IllegalArgumentException("after-sales request required");
        String orderNo = requireText(request.getOrderNo(), "orderNo required");
        OrderForAfterSales order = findOrder(orderNo);
        if (!Objects.equals(order.buyerId(), applicantId)) throw new IllegalArgumentException("only buyer can apply after-sales");
        if (!STATUS_PAID.equals(order.status())) throw new IllegalArgumentException("order not paid");
        String type = normalizeType(request.getAfterSalesType());
        String reason = requireText(request.getReason(), "after-sales reason required");
        String description = requireText(request.getDescription(), "after-sales description required");
        if (description.length() < 8 || description.length() > 512) throw new IllegalArgumentException("after-sales description length invalid");
        BigDecimal refundAmount = request.getRefundAmount();
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0 || refundAmount.compareTo(order.amount()) > 0) {
            throw new IllegalArgumentException("refund amount invalid");
        }
        List<String> evidenceUrls = sanitizeEvidence(applicantId, request.getEvidenceUrls());
        String afterSalesNo = generateNo(orderNo, applicantId);
        try {
            jdbcTemplate.update("""
                    insert into after_sales_record (after_sales_no, order_no, applicant_id, after_sales_type, refund_amount, reason, description, evidence_urls, after_sales_status, created_at, updated_at)
                    values (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, afterSalesNo, orderNo, applicantId, type, refundAmount, reason, description, encode(evidenceUrls), STATUS_PENDING_REVIEW);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("after-sales already exists");
        }
        return findByAfterSalesNo(afterSalesNo);
    }

    public AfterSalesResponse detail(String afterSalesNo, Long userId) {
        AfterSalesResponse response = findByAfterSalesNo(requireText(afterSalesNo, "afterSalesNo required"));
        if (!Objects.equals(response.getApplicantId(), userId)) throw new IllegalArgumentException("after-sales applicant mismatch");
        return response;
    }

    private List<String> sanitizeEvidence(Long applicantId, List<String> evidenceUrls) {
        if (evidenceUrls == null || evidenceUrls.isEmpty()) throw new IllegalArgumentException("after-sales evidence required");
        if (evidenceUrls.size() > 6) throw new IllegalArgumentException("too many after-sales evidence images");
        List<String> cleaned = new ArrayList<>();
        for (String raw : evidenceUrls) {
            String url = requireText(raw, "after-sales evidence url required");
            String lower = url.toLowerCase(Locale.ROOT);
            if (lower.startsWith("local://") || lower.contains("placeholder") || lower.contains("preview") || !url.startsWith("/uploads/evidence/after-sales/")) {
                throw new IllegalArgumentException("after-sales evidence must use upload ticket");
            }
            mediaUploadTicketService.requireIssuedStorageUrl(applicantId, "AFTER_SALES_EVIDENCE", url);
            if (!cleaned.contains(url)) cleaned.add(url);
        }
        return cleaned;
    }

    private OrderForAfterSales findOrder(String orderNo) {
        try {
            return jdbcTemplate.queryForObject("select order_no,buyer_id,amount,order_status from trade_order where order_no = ?",
                    (rs, rowNum) -> new OrderForAfterSales(rs.getString("order_no"), rs.getLong("buyer_id"), rs.getBigDecimal("amount"), rs.getString("order_status")), orderNo);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("order-not-found");
        }
    }

    private AfterSalesResponse findByAfterSalesNo(String afterSalesNo) {
        try {
            return jdbcTemplate.queryForObject("select * from after_sales_record where after_sales_no = ?", (rs, rowNum) -> new AfterSalesResponse(
                    rs.getString("after_sales_no"), rs.getString("order_no"), rs.getLong("applicant_id"), rs.getString("after_sales_type"),
                    rs.getBigDecimal("refund_amount"), rs.getString("reason"), rs.getString("description"), decode(rs.getString("evidence_urls")),
                    rs.getString("after_sales_status"), timeText(rs.getTimestamp("created_at"))
            ), afterSalesNo);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("after-sales-not-found");
        }
    }

    private String normalizeType(String type) {
        String value = requireText(type, "after-sales type required");
        return switch (value) {
            case "仅退款", "REFUND_ONLY" -> "REFUND_ONLY";
            case "退货退款", "RETURN_REFUND" -> "RETURN_REFUND";
            case "平台介入", "PLATFORM_ARBITRATION" -> "PLATFORM_ARBITRATION";
            default -> throw new IllegalArgumentException("after-sales type invalid");
        };
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private String encode(List<String> urls) { return String.join("\n", urls); }
    private List<String> decode(String text) { return text == null || text.isBlank() ? List.of() : Arrays.asList(text.split("\\n")); }
    private String timeText(Timestamp timestamp) { return timestamp == null ? null : timestamp.toLocalDateTime().toString(); }
    private String generateNo(String orderNo, Long userId) { return "AS-" + Math.abs((orderNo + ':' + userId + ':' + LocalDateTime.now()).hashCode()); }
    private record OrderForAfterSales(String orderNo, Long buyerId, BigDecimal amount, String status) {}
}
