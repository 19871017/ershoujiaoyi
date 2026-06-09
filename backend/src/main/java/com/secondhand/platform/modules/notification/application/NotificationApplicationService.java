package com.secondhand.platform.modules.notification.application;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationApplicationService {
    private static final int MAX_LIST_LIMIT = 50;
    private static final Set<String> ALLOWED_TYPES = Set.of("ORDER", "CHAT", "AUDIT", "SYSTEM", "FOLLOW", "LIKE", "COMMENT", "GIFT");
    private static final Set<String> ALLOWED_STATIC_TARGET_URLS = Set.of(
            "/pages/notification/index",
            "/pages/chat/session-list/index",
            "/pages/user/identity/index",
            "/pages/tabbar/home/index",
            "/pages/tabbar/category/index",
            "/pages/tabbar/publish/index",
            "/pages/tabbar/message/index",
            "/pages/tabbar/me/index"
    );
    private final JdbcTemplate jdbcTemplate;

    public NotificationApplicationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<NotificationItemResponse> listNotifications(Long userId, String type, int limit) {
        requireUserId(userId);
        String normalizedType = normalizeListType(type);
        int capped = Math.max(1, Math.min(limit <= 0 ? 20 : limit, MAX_LIST_LIMIT));
        if ("ALL".equals(normalizedType)) {
            return jdbcTemplate.query("SELECT * FROM notification_record WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ?",
                    (rs, rowNum) -> mapRow(
                            rs.getString("notification_no"),
                            rs.getLong("user_id"),
                            rs.getString("notification_type"),
                            rs.getString("title"),
                            rs.getString("description"),
                            rs.getString("target_url"),
                            rs.getBoolean("read_flag"),
                            rs.getTimestamp("created_at"),
                            rs.getTimestamp("read_at")),
                    userId, capped);
        }
        return jdbcTemplate.query("SELECT * FROM notification_record WHERE user_id = ? AND notification_type = ? ORDER BY created_at DESC, id DESC LIMIT ?",
                (rs, rowNum) -> mapRow(
                        rs.getString("notification_no"),
                        rs.getLong("user_id"),
                        rs.getString("notification_type"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("target_url"),
                        rs.getBoolean("read_flag"),
                        rs.getTimestamp("created_at"),
                        rs.getTimestamp("read_at")),
                userId, normalizedType, capped);
    }

    @Transactional
    public NotificationItemResponse createNotification(Long userId, String type, String title, String description, String targetUrl) {
        requireUserId(userId);
        String normalizedType = normalizeType(type);
        String safeTitle = requireText(title, "title", 2, 80);
        String safeDescription = requireText(description, "description", 2, 300);
        String safeTargetUrl = validateTargetUrl(targetUrl);
        String notificationNo = "NTF-" + userId + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        jdbcTemplate.update("INSERT INTO notification_record(notification_no, user_id, notification_type, title, description, target_url, read_flag, created_at) VALUES(?,?,?,?,?,?,FALSE,CURRENT_TIMESTAMP)",
                notificationNo, userId, normalizedType, safeTitle, safeDescription, safeTargetUrl);
        return findByNo(notificationNo);
    }

    @Transactional
    public NotificationItemResponse markRead(Long userId, String notificationNo) {
        requireUserId(userId);
        String safeNo = requireNotificationNo(notificationNo);
        NotificationItemResponse existing = findByNo(safeNo);
        if (!userId.equals(existing.userId())) {
            throw new SecurityException("notification owner mismatch");
        }
        jdbcTemplate.update("UPDATE notification_record SET read_flag = TRUE, read_at = COALESCE(read_at, CURRENT_TIMESTAMP) WHERE notification_no = ? AND user_id = ?",
                safeNo, userId);
        return findByNo(safeNo);
    }

    private NotificationItemResponse findByNo(String notificationNo) {
        List<NotificationItemResponse> rows = jdbcTemplate.query("SELECT * FROM notification_record WHERE notification_no = ?",
                (rs, rowNum) -> mapRow(
                        rs.getString("notification_no"),
                        rs.getLong("user_id"),
                        rs.getString("notification_type"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("target_url"),
                        rs.getBoolean("read_flag"),
                        rs.getTimestamp("created_at"),
                        rs.getTimestamp("read_at")),
                notificationNo);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("notification not found");
        }
        return rows.get(0);
    }

    private static void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId required");
        }
    }

    private static String normalizeListType(String type) {
        String normalized = type == null || type.isBlank() ? "ALL" : type.trim().toUpperCase();
        if ("ALL".equals(normalized)) {
            return normalized;
        }
        return normalizeType(normalized);
    }

    private static String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase();
        if (!ALLOWED_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("notification type invalid");
        }
        return normalized;
    }

    private static String requireText(String value, String field, int min, int max) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() < min || trimmed.length() > max) {
            throw new IllegalArgumentException("notification " + field + " invalid");
        }
        return trimmed;
    }

    private static String requireNotificationNo(String notificationNo) {
        String trimmed = notificationNo == null ? "" : notificationNo.trim();
        if (!trimmed.matches("NTF-[A-Za-z0-9][A-Za-z0-9_-]{5,120}")) {
            throw new IllegalArgumentException("notificationNo invalid");
        }
        return trimmed;
    }

    private static String validateTargetUrl(String targetUrl) {
        String trimmed = targetUrl == null ? "" : targetUrl.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.length() > 256 || !trimmed.matches("/pages/[A-Za-z0-9/_-]+/index(\\?[A-Za-z0-9%=&_.:-]+)?")) {
            throw new IllegalArgumentException("notification targetUrl invalid");
        }
        if (ALLOWED_STATIC_TARGET_URLS.contains(trimmed)) {
            return trimmed;
        }
        if (trimmed.startsWith("/pages/after-sales/detail/index?")) {
            validateAfterSalesDetailTargetUrl(trimmed);
            return trimmed;
        }
        if (trimmed.startsWith("/pages/order/detail/index?")) {
            validateOrderDetailTargetUrl(trimmed);
            return trimmed;
        }
        if (trimmed.startsWith("/pages/community/detail/index?")) {
            validateCommunityDetailTargetUrl(trimmed);
            return trimmed;
        }
        if (trimmed.startsWith("/pages/user/public-profile/index?")) {
            validatePublicProfileTargetUrl(trimmed);
            return trimmed;
        }
        if (trimmed.startsWith("/pages/gift/index?")) {
            validateGiftTargetUrl(trimmed);
            return trimmed;
        }
        if (trimmed.startsWith("/pages/chat/conversation/index?")) {
            validateChatConversationTargetUrl(trimmed);
            return trimmed;
        }
        throw new IllegalArgumentException("notification targetUrl invalid");
    }

    private static void validateAfterSalesDetailTargetUrl(String targetUrl) {
        String query = targetUrl.substring("/pages/after-sales/detail/index?".length());
        String afterSalesNo = "";
        String orderNo = "";
        int afterSalesNoCount = 0;
        int orderNoCount = 0;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("notification targetUrl invalid");
            }
            String key = decode(parts[0]);
            String value = decode(parts[1]);
            if ("afterSalesNo".equals(key)) {
                afterSalesNo = value;
                afterSalesNoCount += 1;
            } else if ("orderNo".equals(key)) {
                orderNo = value;
                orderNoCount += 1;
            }
            else throw new IllegalArgumentException("notification targetUrl invalid");
        }
        if (afterSalesNoCount != 1 || orderNoCount != 1 ||
                !afterSalesNo.matches("AS-[A-Za-z0-9][A-Za-z0-9_-]{5,63}") || !orderNo.matches("OD-[0-9]{1,10}")) {
            throw new IllegalArgumentException("notification targetUrl invalid");
        }
    }

    private static void validateOrderDetailTargetUrl(String targetUrl) {
        String query = targetUrl.substring("/pages/order/detail/index?".length());
        String orderNo = "";
        int orderNoCount = 0;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("notification targetUrl invalid");
            }
            String key = decode(parts[0]);
            String value = decode(parts[1]);
            if ("orderNo".equals(key)) {
                orderNo = value;
                orderNoCount += 1;
            } else {
                throw new IllegalArgumentException("notification targetUrl invalid");
            }
        }
        if (orderNoCount != 1 || !orderNo.matches("OD-[0-9]{1,10}")) {
            throw new IllegalArgumentException("notification targetUrl invalid");
        }
    }

    private static void validateCommunityDetailTargetUrl(String targetUrl) {
        String query = targetUrl.substring("/pages/community/detail/index?".length());
        String postId = "";
        int postIdCount = 0;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("notification targetUrl invalid");
            }
            String key = decode(parts[0]);
            String value = decode(parts[1]);
            if ("postId".equals(key)) {
                postId = value;
                postIdCount += 1;
            } else {
                throw new IllegalArgumentException("notification targetUrl invalid");
            }
        }
        if (postIdCount != 1 || !postId.matches("[1-9]\\d{0,18}")) {
            throw new IllegalArgumentException("notification targetUrl invalid");
        }
    }

    private static void validatePublicProfileTargetUrl(String targetUrl) {
        String query = targetUrl.substring("/pages/user/public-profile/index?".length());
        String userId = "";
        int userIdCount = 0;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("notification targetUrl invalid");
            }
            String key = decode(parts[0]);
            String value = decode(parts[1]);
            if ("userId".equals(key)) {
                userId = value;
                userIdCount += 1;
            } else {
                throw new IllegalArgumentException("notification targetUrl invalid");
            }
        }
        if (userIdCount != 1 || !userId.matches("[1-9]\\d{0,18}")) {
            throw new IllegalArgumentException("notification targetUrl invalid");
        }
    }

    private static void validateGiftTargetUrl(String targetUrl) {
        String query = targetUrl.substring("/pages/gift/index?".length());
        boolean hasModeReceived = false;
        int modeCount = 0;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("notification targetUrl invalid");
            }
            String key = decode(parts[0]);
            String value = decode(parts[1]);
            if ("mode".equals(key) && "received".equals(value)) {
                hasModeReceived = true;
                modeCount += 1;
            } else {
                throw new IllegalArgumentException("notification targetUrl invalid");
            }
        }
        if (!hasModeReceived || modeCount != 1) {
            throw new IllegalArgumentException("notification targetUrl invalid");
        }
    }

    private static void validateChatConversationTargetUrl(String targetUrl) {
        String query = targetUrl.substring("/pages/chat/conversation/index?".length());
        String conversationId = "";
        String receiverId = "";
        int conversationIdCount = 0;
        int receiverIdCount = 0;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("notification targetUrl invalid");
            }
            String key = decode(parts[0]);
            String value = decode(parts[1]);
            if ("conversationId".equals(key)) {
                conversationId = value;
                conversationIdCount += 1;
            } else if ("receiverId".equals(key)) {
                receiverId = value;
                receiverIdCount += 1;
            }
            else throw new IllegalArgumentException("notification targetUrl invalid");
        }
        if (conversationIdCount != 1 || receiverIdCount != 1 ||
                !conversationId.matches("[1-9]\\d{0,18}") || !receiverId.matches("[1-9]\\d{0,18}")) {
            throw new IllegalArgumentException("notification targetUrl invalid");
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static NotificationItemResponse mapRow(String notificationNo, Long userId, String type, String title, String description,
                                                   String targetUrl, boolean read, Timestamp createdAt, Timestamp readAt) {
        return new NotificationItemResponse(notificationNo, userId, type, title, description, targetUrl, read, toInstant(createdAt), toInstant(readAt));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
