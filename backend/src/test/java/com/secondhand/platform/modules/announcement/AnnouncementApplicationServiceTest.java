package com.secondhand.platform.modules.announcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class AnnouncementApplicationServiceTest {
    private JdbcTemplate jdbcTemplate;
    private AnnouncementApplicationService service;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        service = new AnnouncementApplicationService(jdbcTemplate);
    }

    @Test
    void getTickerShouldReturnDisabledDefaultFromSystemConfig() {
        AnnouncementTickerResponse ticker = service.getTicker();

        assertFalse(ticker.enabled());
        assertEquals("欢迎来到小原圈，请通过平台订单流程完成交易", ticker.text());
        assertEquals("📣", ticker.icon());
        assertEquals("/pages/notification/index", ticker.targetUrl());
    }

    @Test
    void adminUpdateTickerShouldPersistConfigAcrossServiceRecreation() {
        AdminUpdateAnnouncementTickerRequest request = new AdminUpdateAnnouncementTickerRequest();
        request.setEnabled(true);
        request.setText("今晚 22:00 女神榜按真实礼物数据刷新");
        request.setIcon("🔔");
        request.setTargetUrl("/pages/ranking/index?tab=goddess&period=week");

        AnnouncementTickerResponse updated = service.adminUpdateTicker(request);
        AnnouncementTickerResponse reloaded = new AnnouncementApplicationService(jdbcTemplate).getTicker();

        assertTrue(updated.enabled());
        assertEquals("今晚 22:00 女神榜按真实礼物数据刷新", reloaded.text());
        assertEquals("🔔", reloaded.icon());
        assertEquals("/pages/ranking/index?tab=goddess&period=week", reloaded.targetUrl());
        assertEquals(updated.updatedAt(), reloaded.updatedAt());
    }

    @Test
    void adminUpdateTickerShouldRejectMissingRequest() {
        assertThrows(IllegalArgumentException.class, () -> service.adminUpdateTicker(null));
    }

    @Test
    void adminUpdateTickerShouldRejectInvalidTextAndExternalTargetUrl() {
        AdminUpdateAnnouncementTickerRequest invalidText = new AdminUpdateAnnouncementTickerRequest();
        invalidText.setText("demo announcement");
        assertThrows(IllegalArgumentException.class, () -> service.adminUpdateTicker(invalidText));

        AdminUpdateAnnouncementTickerRequest invalidUrl = new AdminUpdateAnnouncementTickerRequest();
        invalidUrl.setTargetUrl("https://example.com/promo");
        assertThrows(IllegalArgumentException.class, () -> service.adminUpdateTicker(invalidUrl));
    }
}
