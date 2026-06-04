package com.secondhand.platform.modules.aftersales.application;

import com.secondhand.platform.modules.aftersales.AfterSalesResponse;
import com.secondhand.platform.modules.media.application.MediaUploadTicketService;
import com.secondhand.platform.modules.notification.application.NotificationApplicationService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
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
    private final NotificationApplicationService notificationApplicationService;

    public AfterSalesApplicationService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new MediaUploadTicketService(jdbcTemplate), new NotificationApplicationService(jdbcTemplate));
    }

    @Autowired
    public AfterSalesApplicationService(JdbcTemplate jdbcTemplate, MediaUploadTicketService mediaUploadTicketService) {
        this(jdbcTemplate, mediaUploadTicketService, new NotificationApplicationService(jdbcTemplate));
    }

    public AfterSalesApplicationService(JdbcTemplate jdbcTemplate, MediaUploadTicketService mediaUploadTicketService, NotificationApplicationService notificationApplicationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.mediaUploadTicketService = mediaUploadTicketService;
        this.notificationApplicationService = notificationApplicationService;
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
        for (int attempt = 0; attempt < 3; attempt++) {
            String afterSalesNo = generateNo();
            try {
                jdbcTemplate.update("""
                        insert into after_sales_record (after_sales_no, order_no, applicant_id, after_sales_type, refund_amount, reason, description, evidence_urls, after_sales_status, created_at, updated_at)
                        values (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                        """, afterSalesNo, orderNo, applicantId, type, refundAmount, reason, description, encode(evidenceUrls), STATUS_PENDING_REVIEW);
                AfterSalesResponse created = findByAfterSalesNo(afterSalesNo);
                notifyAfterSalesCreated(created);
                return created;
            } catch (DuplicateKeyException e) {
                if (hasExistingAfterSales(orderNo, applicantId)) {
                    throw new IllegalArgumentException("after-sales already exists");
                }
            }
        }
        throw new IllegalStateException("after-sales number generation failed");
    }

    public AfterSalesResponse detail(String afterSalesNo, Long userId) {
        AfterSalesResponse response = findByAfterSalesNo(requireText(afterSalesNo, "afterSalesNo required"));
        if (!Objects.equals(response.getApplicantId(), userId)) throw new IllegalArgumentException("after-sales applicant mismatch");
        return response;
    }

    public AfterSalesResponse getAdminDetail(String afterSalesNo) {
        return findByAfterSalesNo(requireAdminAfterSalesNo(afterSalesNo));
    }

    public List<AfterSalesResponse> listAdminAfterSales(String status, Integer limit) {
        String safeStatus = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status) ? null : status.trim().toUpperCase(Locale.ROOT);
        if (safeStatus != null && !STATUS_PENDING_REVIEW.equals(safeStatus) && !"APPROVED".equals(safeStatus) && !"REJECTED".equals(safeStatus)) {
            throw new IllegalArgumentException("after-sales status invalid");
        }
        int safeLimit = limit == null ? 20 : limit;
        if (safeLimit < 1 || safeLimit > 100) throw new IllegalArgumentException("after-sales limit invalid");
        String where = safeStatus == null ? "" : " where after_sales_status = ?";
        Object[] args = safeStatus == null ? new Object[]{safeLimit} : new Object[]{safeStatus, safeLimit};
        return jdbcTemplate.query("""
                select a.*, o.seller_id
                from after_sales_record a
                left join trade_order o on o.order_no = a.order_no
                """ + where + " order by a.created_at desc limit ?", (rs, rowNum) -> new AfterSalesResponse(
                rs.getString("after_sales_no"), rs.getString("order_no"), rs.getLong("applicant_id"), rs.getString("after_sales_type"),
                rs.getBigDecimal("refund_amount"), rs.getString("reason"), rs.getString("description"), decode(rs.getString("evidence_urls")),
                rs.getString("after_sales_status"), timeText(rs.getTimestamp("created_at")), rs.getLong("seller_id") == 0 ? null : rs.getLong("seller_id")
        ), args);
    }

    @Transactional
    public AfterSalesResponse adminReview(String afterSalesNo, String status, Long operatorId, String remark) {
        String safeAfterSalesNo = requireAdminAfterSalesNo(afterSalesNo);
        String safeStatus = normalizeReviewStatus(status);
        long safeOperatorId = validateOperatorId(operatorId);
        int updated = jdbcTemplate.update("""
                update after_sales_record
                set after_sales_status = ?, reviewed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                where after_sales_no = ? and after_sales_status = ?
                """, safeStatus, safeAfterSalesNo, STATUS_PENDING_REVIEW);
        if (updated != 1) {
            throw new IllegalStateException("after-sales already reviewed");
        }
        jdbcTemplate.update("""
                insert into admin_audit_log (action,operator_id,target_type,target_id,result,summary,created_at)
                values (?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """, "AFTER_SALES_REVIEW", safeOperatorId, "AFTER_SALES", safeAfterSalesNo, safeStatus, sanitizeReviewSummary(remark, safeStatus));
        AfterSalesResponse reviewed = findByAfterSalesNo(safeAfterSalesNo);
        notifyAfterSalesReviewed(reviewed, safeStatus);
        return reviewed;
    }

    private void notifyAfterSalesCreated(AfterSalesResponse response) {
        if (!hasNotificationTarget(response)) {
            return;
        }
        notificationApplicationService.createNotification(
                response.getApplicantId(),
                "ORDER",
                "售后申请已提交",
                "售后单 " + response.getAfterSalesNo() + " 已创建，进度以平台售后详情为准。",
                afterSalesDetailTargetUrl(response)
        );
    }

    private void notifyAfterSalesReviewed(AfterSalesResponse response, String status) {
        if (!hasNotificationTarget(response)) {
            return;
        }
        String title = "APPROVED".equals(status) ? "售后审核已通过" : "售后审核未通过";
        notificationApplicationService.createNotification(
                response.getApplicantId(),
                "ORDER",
                title,
                "售后单 " + response.getAfterSalesNo() + " 状态已更新为 " + status + "，请查看售后详情。",
                afterSalesDetailTargetUrl(response)
        );
    }

    private String afterSalesDetailTargetUrl(AfterSalesResponse response) {
        return "/pages/after-sales/detail/index?afterSalesNo=" + response.getAfterSalesNo() + "&orderNo=" + response.getOrderNo();
    }

    private boolean hasNotificationTarget(AfterSalesResponse response) {
        return response.getAfterSalesNo() != null
                && response.getAfterSalesNo().matches("AS-[A-Za-z0-9][A-Za-z0-9_-]{5,63}")
                && response.getOrderNo() != null
                && response.getOrderNo().matches("OD-[0-9]{1,10}");
    }

    private List<String> sanitizeEvidence(Long applicantId, List<String> evidenceUrls) {
        if (evidenceUrls == null || evidenceUrls.isEmpty()) throw new IllegalArgumentException("after-sales evidence required");
        if (evidenceUrls.size() > 6) throw new IllegalArgumentException("too many after-sales evidence images");
        List<String> cleaned = new ArrayList<>();
        for (String raw : evidenceUrls) {
            String url = requireText(raw, "after-sales evidence url required");
            String lower = url.toLowerCase(Locale.ROOT);
            if (lower.startsWith("local://")
                    || lower.startsWith("blob:")
                    || lower.startsWith("data:")
                    || lower.contains("placeholder")
                    || lower.contains("preview")
                    || lower.contains("%2e")
                    || lower.contains("%2f")
                    || lower.contains("%5c")
                    || url.contains("\\")
                    || url.contains("..")
                    || url.contains("//")
                    || !url.startsWith("/uploads/evidence/after-sales/")) {
                throw new IllegalArgumentException("after-sales evidence must use upload ticket");
            }
            String relativePath = url.substring("/uploads/evidence/after-sales/".length());
            if (relativePath.isBlank()) {
                throw new IllegalArgumentException("after-sales evidence must use upload ticket");
            }
            for (String segment : relativePath.split("/")) {
                if (segment.isBlank()) {
                    throw new IllegalArgumentException("after-sales evidence must use upload ticket");
                }
            }
            mediaUploadTicketService.requireUploadedStorageUrl(applicantId, "AFTER_SALES_EVIDENCE", url);
            if (!cleaned.contains(url)) cleaned.add(url);
        }
        return cleaned;
    }

    private boolean hasExistingAfterSales(String orderNo, Long applicantId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from after_sales_record where order_no = ? and applicant_id = ?", Integer.class, orderNo, applicantId);
        return count != null && count > 0;
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
            return jdbcTemplate.queryForObject("""
                    select a.*, o.seller_id
                    from after_sales_record a
                    left join trade_order o on o.order_no = a.order_no
                    where a.after_sales_no = ?
                    """, (rs, rowNum) -> new AfterSalesResponse(
                    rs.getString("after_sales_no"), rs.getString("order_no"), rs.getLong("applicant_id"), rs.getString("after_sales_type"),
                    rs.getBigDecimal("refund_amount"), rs.getString("reason"), rs.getString("description"), decode(rs.getString("evidence_urls")),
                    rs.getString("after_sales_status"), timeText(rs.getTimestamp("created_at")), rs.getLong("seller_id") == 0 ? null : rs.getLong("seller_id")
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
            case "平台介入", "售后协调", "PLATFORM_ARBITRATION" -> "PLATFORM_ARBITRATION";
            default -> throw new IllegalArgumentException("after-sales type invalid");
        };
    }

    private String requireAdminAfterSalesNo(String afterSalesNo) {
        String safeAfterSalesNo = requireText(afterSalesNo, "afterSalesNo required");
        if (!safeAfterSalesNo.matches("AS-[A-Z]+-\\d{8}-\\d{4,}")) {
            throw new IllegalArgumentException("after-sales record not found");
        }
        return safeAfterSalesNo;
    }

    private String normalizeReviewStatus(String status) {
        String safeStatus = requireText(status, "after-sales status required").toUpperCase(Locale.ROOT);
        if (!"APPROVED".equals(safeStatus) && !"REJECTED".equals(safeStatus)) {
            throw new IllegalArgumentException("after-sales status invalid");
        }
        return safeStatus;
    }

    private long validateOperatorId(Long operatorId) {
        if (operatorId == null || operatorId <= 0) throw new IllegalArgumentException("operatorId required");
        return operatorId;
    }

    private String sanitizeReviewSummary(String remark, String status) {
        String normalized = remark == null ? "" : remark.trim();
        if (normalized.length() > 80) normalized = normalized.substring(0, 80);
        normalized = normalized.replaceAll("\\d{3,}", "[REDACTED]");
        if (normalized.isBlank()) return "售后审核状态已更新：" + status;
        return "售后审核状态已更新：" + status + "，备注=" + normalized;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private String encode(List<String> urls) { return String.join("\n", urls); }
    private List<String> decode(String text) { return text == null || text.isBlank() ? List.of() : Arrays.asList(text.split("\\n")); }
    private String timeText(Timestamp timestamp) { return timestamp == null ? null : timestamp.toLocalDateTime().toString(); }
    private String generateNo() {
        String day = LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        long code = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000_000L);
        String suffix = String.format(Locale.ROOT, "%012d", code);
        return "AS-USER-" + day + '-' + suffix;
    }
    private record OrderForAfterSales(String orderNo, Long buyerId, BigDecimal amount, String status) {}
}
