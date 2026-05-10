package com.secondhand.platform.modules.notification.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationApplicationService {
    private static final int MAX_LIST_LIMIT = 50;
    private static final Set<String> ALLOWED_TYPES = Set.of("ORDER", "CHAT", "AUDIT", "SYSTEM");
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
        String notificationNo = "NTF-" + userId + "-" + System.currentTimeMillis();
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
        return trimmed;
    }

    private static NotificationItemResponse mapRow(String notificationNo, Long userId, String type, String title, String description,
                                                   String targetUrl, boolean read, Timestamp createdAt, Timestamp readAt) {
        return new NotificationItemResponse(notificationNo, userId, type, title, description, targetUrl, read, toInstant(createdAt), toInstant(readAt));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
