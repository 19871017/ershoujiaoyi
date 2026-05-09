package com.secondhand.platform.modules.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class NotificationApplicationServiceTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private NotificationApplicationService service;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        service = new NotificationApplicationService(jdbcTemplate);
    }

    @Test
    void listNotificationsShouldReturnOnlyOwnerRowsAndHideOtherUsersRows() {
        service.createNotification(11L, "ORDER", "订单已更新", "订单状态以服务端记录为准", "/pages/order/detail/index?orderNo=ORD-abc123");
        service.createNotification(12L, "CHAT", "收到新私信", "聊天记录以服务端会话为准", "/pages/chat/session-list/index");

        List<NotificationItemResponse> ownerRows = service.listNotifications(11L, "ALL", 20);

        assertEquals(1, ownerRows.size());
        assertEquals(11L, ownerRows.get(0).userId());
        assertEquals("ORDER", ownerRows.get(0).type());
        assertFalse(ownerRows.get(0).read());
        assertTrue(ownerRows.get(0).notificationNo().startsWith("NTF-"));
    }

    @Test
    void markReadShouldRequireOwnerAndPersistReadState() {
        NotificationItemResponse notice = service.createNotification(21L, "AUDIT", "审核结果", "审核详情以后端记录为准", "/pages/admin/audit/detail/index?auditNo=AU-abc123");

        assertThrows(SecurityException.class, () -> service.markRead(22L, notice.notificationNo()));

        NotificationItemResponse read = service.markRead(21L, notice.notificationNo());
        assertTrue(read.read());

        NotificationApplicationService reloaded = new NotificationApplicationService(new JdbcTemplate(database));
        List<NotificationItemResponse> rows = reloaded.listNotifications(21L, "AUDIT", 20);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).read());
    }

    @Test
    void shouldRejectStaticPreviewIdentifiersAndInvalidTypes() {
        assertThrows(IllegalArgumentException.class, () -> service.listNotifications(0L, "ALL", 20));
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(31L, "ESCROW", "静态担保", "不允许", ""));
        assertThrows(IllegalArgumentException.class, () -> service.markRead(31L, "PREVIEW-NTF-001"));
        assertThrows(IllegalArgumentException.class, () -> service.markRead(31L, "UNKNOWN"));
    }
}
