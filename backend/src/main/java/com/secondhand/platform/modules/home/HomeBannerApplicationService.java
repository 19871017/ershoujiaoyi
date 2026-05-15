package com.secondhand.platform.modules.home;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HomeBannerApplicationService {
    public static final String SIZE_HINT = "建议尺寸 750×300px（比例 5:2），JPG/PNG/WebP，单张不超过 500KB；重要文字和主体放在中间安全区，避免左右圆角裁切。";
    private static final int MAX_BANNERS = 6;
    private static final List<String> ALLOWED_ACTIONS = List.of("closet", "ranking", "forum", "search", "none");

    private final JdbcTemplate jdbcTemplate;

    public HomeBannerApplicationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureDefaults();
    }

    public synchronized List<HomeBannerResponse> listEnabled() {
        ensureDefaults();
        return jdbcTemplate.query("""
                SELECT id, kicker, title, description, cta, image_url, action, sort_order, enabled, updated_at
                FROM home_banner
                WHERE enabled = TRUE
                ORDER BY sort_order ASC, id ASC
                """, this::mapRow);
    }

    public synchronized List<HomeBannerResponse> adminList() {
        ensureDefaults();
        return jdbcTemplate.query("""
                SELECT id, kicker, title, description, cta, image_url, action, sort_order, enabled, updated_at
                FROM home_banner
                ORDER BY sort_order ASC, id ASC
                """, this::mapRow);
    }

    @Transactional
    public synchronized HomeBannerResponse adminCreate(AdminHomeBannerRequest request) {
        ensureDefaults();
        requireRequest(request);
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM home_banner", Integer.class);
        if (count != null && count >= MAX_BANNERS) {
            throw new IllegalStateException("home banner limit exceeded");
        }
        BannerValues values = validate(request, null);
        Long id = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(id), 0) + 1 FROM home_banner
                """, Long.class);
        return insertAndLoad(id, values);
    }

    @Transactional
    public synchronized HomeBannerResponse adminUpdate(Long bannerId, AdminHomeBannerRequest request) {
        ensureDefaults();
        requireValidId(bannerId);
        requireExists(bannerId);
        requireRequest(request);
        BannerValues values = validate(request, bannerId);
        jdbcTemplate.update("""
                UPDATE home_banner
                SET kicker = ?, title = ?, description = ?, cta = ?, image_url = ?, action = ?, sort_order = ?, enabled = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, values.kicker(), values.title(), values.description(), values.cta(), values.imageUrl(), values.action(), values.sortOrder(), values.enabled(), bannerId);
        return getById(bannerId);
    }

    @Transactional
    public synchronized HomeBannerResponse adminDelete(Long bannerId) {
        ensureDefaults();
        requireValidId(bannerId);
        HomeBannerResponse existing = getById(bannerId);
        jdbcTemplate.update("DELETE FROM home_banner WHERE id = ?", bannerId);
        return existing;
    }

    public synchronized HomeBannerResponse getById(Long bannerId) {
        ensureDefaults();
        requireValidId(bannerId);
        return jdbcTemplate.query("""
                SELECT id, kicker, title, description, cta, image_url, action, sort_order, enabled, updated_at
                FROM home_banner
                WHERE id = ?
                """, this::mapRow, bannerId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("home banner not found"));
    }

    private BannerValues validate(AdminHomeBannerRequest request, Long currentId) {
        String kicker = sanitizeText(request.getKicker(), "kicker", 32, true);
        String title = sanitizeText(request.getTitle(), "title", 40, true);
        String description = sanitizeText(request.getDescription(), "description", 80, true);
        String cta = sanitizeText(request.getCta(), "cta", 16, true);
        String imageUrl = sanitizeImageUrl(request.getImageUrl());
        String action = sanitizeAction(request.getAction());
        int sortOrder = sanitizeSortOrder(request.getSortOrder());
        boolean enabled = request.getEnabled() != null && request.getEnabled();
        ensureUniqueSort(sortOrder, currentId);
        return new BannerValues(kicker, title, description, cta, imageUrl, action, sortOrder, enabled);
    }

    private String sanitizeText(String value, String field, int maxLength, boolean required) {
        if (value == null || value.trim().isEmpty()) {
            if (required) throw new IllegalArgumentException("home banner " + field + " required");
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException("home banner " + field + " invalid");
        }
        if (trimmed.matches("(?i).*(preview|demo|mock|sample|placeholder).*")) {
            throw new IllegalArgumentException("home banner " + field + " invalid");
        }
        return trimmed;
    }

    private String sanitizeImageUrl(String value) {
        String trimmed = sanitizeText(value, "imageUrl", 512, true);
        if (!(trimmed.startsWith("/uploads/") || trimmed.startsWith("https://"))) {
            throw new IllegalArgumentException("home banner imageUrl invalid");
        }
        return trimmed;
    }

    private String sanitizeAction(String value) {
        String action = value == null || value.trim().isEmpty() ? "none" : value.trim().toLowerCase();
        if (!ALLOWED_ACTIONS.contains(action)) {
            throw new IllegalArgumentException("home banner action invalid");
        }
        return action;
    }

    private int sanitizeSortOrder(Integer value) {
        int sortOrder = value == null ? 100 : value;
        if (sortOrder < 1 || sortOrder > 999) {
            throw new IllegalArgumentException("home banner sortOrder invalid");
        }
        return sortOrder;
    }

    private void ensureUniqueSort(int sortOrder, Long currentId) {
        Integer count;
        if (currentId == null) {
            count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM home_banner WHERE sort_order = ?", Integer.class, sortOrder);
        } else {
            count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM home_banner WHERE sort_order = ? AND id <> ?", Integer.class, sortOrder, currentId);
        }
        if (count != null && count > 0) {
            throw new IllegalArgumentException("home banner sortOrder duplicated");
        }
    }

    private void requireRequest(AdminHomeBannerRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("home banner request required");
        }
    }

    private void requireValidId(Long bannerId) {
        if (bannerId == null || bannerId <= 0) {
            throw new IllegalArgumentException("home banner id invalid");
        }
    }

    private void requireExists(Long bannerId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM home_banner WHERE id = ?", Integer.class, bannerId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("home banner not found");
        }
    }

    private HomeBannerResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new HomeBannerResponse(
                rs.getLong("id"),
                rs.getString("kicker"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("cta"),
                rs.getString("image_url"),
                rs.getString("action"),
                rs.getInt("sort_order"),
                rs.getBoolean("enabled"),
                SIZE_HINT,
                rs.getTimestamp("updated_at") == null ? Instant.now().toString() : rs.getTimestamp("updated_at").toInstant().toString()
        );
    }

    private void ensureDefaults() {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(1) FROM home_banner", Integer.class);
        } catch (RuntimeException missingTable) {
            // Production deployments may already have schema.sql applied; local tests and older databases are initialized lazily.
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS home_banner (
                      id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                      kicker VARCHAR(64) NOT NULL,
                      title VARCHAR(80) NOT NULL,
                      description VARCHAR(160) NOT NULL,
                      cta VARCHAR(32) NOT NULL,
                      image_url VARCHAR(512) NOT NULL,
                      action VARCHAR(32) NOT NULL DEFAULT 'none',
                      sort_order INT NOT NULL,
                      enabled BOOLEAN NOT NULL DEFAULT TRUE,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      UNIQUE(sort_order)
                    )
                    """);
        }
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM home_banner", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        insertDefault(1L, "小原圈 · 今日新鲜", "把心爱闲置交给懂它的人", "附近好物、日常分享、圈内互动，一屏逛完。", "去发现", "/uploads/home/banner-closet.svg", "closet", 10);
        insertDefault(2L, "礼物积分上升", "男神女神礼物榜", "1 元礼物 = 1 分，按礼物积分看榜单。", "看榜单", "/uploads/home/banner-ranking.svg", "ranking", 20);
        insertDefault(3L, "日常生活频道", "分享今天的小确幸", "校园、寝室、城市日常，都可以轻松聊。", "去社区", "/uploads/home/banner-community.svg", "forum", 30);
    }

    private HomeBannerResponse insertAndLoad(Long id, BannerValues values) {
        jdbcTemplate.update("""
                INSERT INTO home_banner (id, kicker, title, description, cta, image_url, action, sort_order, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, values.kicker(), values.title(), values.description(), values.cta(), values.imageUrl(), values.action(), values.sortOrder(), values.enabled());
        return getById(id);
    }

    private void insertDefault(Long id, String kicker, String title, String description, String cta, String imageUrl, String action, int sortOrder) {
        insertAndLoad(id, new BannerValues(kicker, title, description, cta, imageUrl, action, sortOrder, true));
    }

    private record BannerValues(String kicker, String title, String description, String cta, String imageUrl, String action, int sortOrder, boolean enabled) {
    }
}
