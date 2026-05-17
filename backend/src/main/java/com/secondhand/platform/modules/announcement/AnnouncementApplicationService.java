package com.secondhand.platform.modules.announcement;

import java.sql.Timestamp;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnouncementApplicationService {
    private static final String CONFIG_GROUP = "announcement";
    private static final String KEY_TICKER_ENABLED = "announcement.ticker.enabled";
    private static final String KEY_TICKER_TEXT = "announcement.ticker.text";
    private static final String KEY_TICKER_ICON = "announcement.ticker.icon";
    private static final String KEY_TICKER_TARGET_URL = "announcement.ticker.target_url";
    private static final String DEFAULT_TEXT = "欢迎来到小原圈，请通过平台订单流程完成交易";
    private static final String DEFAULT_ICON = "📣";
    private static final String DEFAULT_TARGET_URL = "/pages/notification/index";

    private final JdbcTemplate jdbcTemplate;

    public AnnouncementApplicationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureDefaults();
    }

    public synchronized AnnouncementTickerResponse getTicker() {
        ensureDefaults();
        Map<String, String> values = loadConfig();
        return new AnnouncementTickerResponse(
                Boolean.parseBoolean(values.getOrDefault(KEY_TICKER_ENABLED, "false")),
                values.getOrDefault(KEY_TICKER_TEXT, DEFAULT_TEXT),
                values.getOrDefault(KEY_TICKER_ICON, DEFAULT_ICON),
                values.getOrDefault(KEY_TICKER_TARGET_URL, DEFAULT_TARGET_URL),
                latestUpdatedAt()
        );
    }

    @Transactional
    public synchronized AnnouncementTickerResponse adminUpdateTicker(AdminUpdateAnnouncementTickerRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("announcement request required");
        }
        if (request.getEnabled() != null) {
            upsert(KEY_TICKER_ENABLED, Boolean.toString(request.getEnabled()), "boolean", "全局跑马灯启用状态");
        }
        if (request.getText() != null) {
            upsert(KEY_TICKER_TEXT, sanitizeText(request.getText()), "string", "全局跑马灯公告文案");
        }
        if (request.getIcon() != null) {
            upsert(KEY_TICKER_ICON, sanitizeIcon(request.getIcon()), "string", "全局跑马灯图标");
        }
        if (request.getTargetUrl() != null) {
            upsert(KEY_TICKER_TARGET_URL, sanitizeTargetUrl(request.getTargetUrl()), "string", "全局跑马灯跳转路径");
        }
        return getTicker();
    }

    private String sanitizeText(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 80) {
            throw new IllegalArgumentException("announcement text invalid");
        }
        if (trimmed.matches("(?i).*(preview|demo|mock|sample|placeholder).*")) {
            throw new IllegalArgumentException("announcement text invalid");
        }
        return trimmed;
    }

    private String sanitizeIcon(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_ICON;
        }
        if (trimmed.length() > 8) {
            throw new IllegalArgumentException("announcement icon invalid");
        }
        return trimmed;
    }

    private String sanitizeTargetUrl(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_TARGET_URL;
        }
        if (trimmed.length() > 256 || !trimmed.startsWith("/pages/")) {
            throw new IllegalArgumentException("announcement targetUrl invalid");
        }
        return trimmed;
    }

    private void ensureDefaults() {
        upsertIfMissing(KEY_TICKER_ENABLED, "false", "boolean", "全局跑马灯启用状态");
        upsertIfMissing(KEY_TICKER_TEXT, DEFAULT_TEXT, "string", "全局跑马灯公告文案");
        upsertIfMissing(KEY_TICKER_ICON, DEFAULT_ICON, "string", "全局跑马灯图标");
        upsertIfMissing(KEY_TICKER_TARGET_URL, DEFAULT_TARGET_URL, "string", "全局跑马灯跳转路径");
    }

    private Map<String, String> loadConfig() {
        return jdbcTemplate.query("""
                SELECT config_key, config_value
                FROM system_config
                WHERE config_group = ?
                """, (rs, rowNum) -> Map.entry(rs.getString("config_key"), rs.getString("config_value")), CONFIG_GROUP)
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> right));
    }

    private String latestUpdatedAt() {
        Timestamp updatedAt = jdbcTemplate.queryForObject("""
                SELECT MAX(updated_at)
                FROM system_config
                WHERE config_group = ?
                """, Timestamp.class, CONFIG_GROUP);
        return updatedAt == null ? "" : updatedAt.toInstant().toString();
    }

    private void upsertIfMissing(String key, String value, String type, String remark) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM system_config WHERE config_key = ?", Integer.class, key);
        if (count == null || count == 0) {
            jdbcTemplate.update("""
                    INSERT INTO system_config (config_key, config_value, config_type, config_group, remark, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, key, value, type, CONFIG_GROUP, remark);
        }
    }

    private void upsert(String key, String value, String type, String remark) {
        int updated = jdbcTemplate.update("""
                UPDATE system_config
                SET config_value = ?, config_type = ?, config_group = ?, remark = ?, updated_at = CURRENT_TIMESTAMP
                WHERE config_key = ?
                """, value, type, CONFIG_GROUP, remark, key);
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO system_config (config_key, config_value, config_type, config_group, remark, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, key, value, type, CONFIG_GROUP, remark);
        }
    }
}
