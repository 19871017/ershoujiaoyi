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
        service.createNotification(11L, "ORDER", "订单已更新", "订单状态以服务端记录为准", "/pages/order/detail/index?orderNo=OD-12345");
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
        NotificationItemResponse notice = service.createNotification(21L, "AUDIT", "审核结果", "审核详情以平台通知为准", "/pages/user/identity/index");

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

    @Test
    void createNotificationShouldRejectUnsafeTargetUrlsBeforePersistence() {
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(41L, "SYSTEM", "外链通知", "跳转地址必须是站内白名单页面", "https://evil.example/phish"));
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(41L, "SYSTEM", "脚本通知", "跳转地址必须是站内白名单页面", "javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(41L, "SYSTEM", "越权通知", "跳转地址必须是站内白名单页面", "/pages/admin/risk/detail/index?riskNo=../secret"));
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(41L, "SYSTEM", "未知页面", "跳转地址必须是明确白名单页面", "/pages/ranking/index?tab=goddess&period=week"));
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(41L, "SYSTEM", "未知页面", "跳转地址必须是明确白名单页面", "/pages/system/privacy/index"));
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(41L, "ORDER", "售后通知", "售后目标编号必须来自后端", "/pages/after-sales/detail/index?afterSalesNo=preview-after-sales&orderNo=OD-1"));
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(41L, "ORDER", "售后通知", "售后目标编号必须来自后端", "/pages/after-sales/detail/index?afterSalesNo=AS-USER-20260520-000001&orderNo=preview-order"));
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(41L, "ORDER", "售后通知", "售后目标不能带多余参数", "/pages/after-sales/detail/index?afterSalesNo=AS-USER-20260520-000001&orderNo=OD-12345&from=preview"));
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(41L, "ORDER", "售后通知", "售后目标不能重复参数", "/pages/after-sales/detail/index?afterSalesNo=AS-USER-20260520-000001&afterSalesNo=AS-USER-20260520-000002&orderNo=OD-12345"));
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(41L, "ORDER", "订单通知", "订单目标编号必须来自后端", "/pages/order/detail/index?orderNo=preview-order"));
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(41L, "ORDER", "订单通知", "订单目标编号必须来自后端", "/pages/order/detail/index?orderNo=ORD-abc123"));
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(41L, "ORDER", "订单通知", "订单目标编号必须来自后端", "/pages/order/detail/index?orderNo=OD-12345&from=preview"));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification_record WHERE user_id = ?", Integer.class, 41L));
    }

    @Test
    void createNotificationShouldAllowCanonicalAfterSalesDetailTarget() {
        NotificationItemResponse notice = service.createNotification(51L, "ORDER", "售后申请已提交", "售后详情以服务端记录为准",
                "/pages/after-sales/detail/index?afterSalesNo=AS-USER-20260520-000001&orderNo=OD-12345");

        assertEquals("/pages/after-sales/detail/index?afterSalesNo=AS-USER-20260520-000001&orderNo=OD-12345", notice.targetUrl());
    }

    @Test
    void createNotificationShouldAllowCanonicalOrderDetailTarget() {
        NotificationItemResponse notice = service.createNotification(52L, "ORDER", "买家已付款", "订单详情以服务端记录为准",
                "/pages/order/detail/index?orderNo=OD-12345");

        assertEquals("/pages/order/detail/index?orderNo=OD-12345", notice.targetUrl());
    }

    @Test
    void createNotificationShouldAllowInteractionTypesAndSafeTargets() {
        NotificationItemResponse follow = service.createNotification(61L, "FOLLOW", "你有新的关注", "用户关注了你",
                "/pages/user/public-profile/index?userId=62");
        NotificationItemResponse like = service.createNotification(61L, "LIKE", "你的动态收到了点赞", "用户点赞了动态",
                "/pages/community/detail/index?postId=7");
        NotificationItemResponse comment = service.createNotification(61L, "COMMENT", "你的动态有新评论", "用户评论了动态",
                "/pages/community/detail/index?postId=7");
        NotificationItemResponse gift = service.createNotification(61L, "GIFT", "你收到了新礼物", "用户送了礼物",
                "/pages/gift/index?mode=received");
        NotificationItemResponse chat = service.createNotification(61L, "CHAT", "你有一条新私信", "用户发来新消息",
                "/pages/chat/conversation/index?conversationId=8&receiverId=62");

        assertEquals("FOLLOW", follow.type());
        assertEquals("LIKE", like.type());
        assertEquals("COMMENT", comment.type());
        assertEquals("GIFT", gift.type());
        assertEquals("CHAT", chat.type());
        assertEquals(5, service.listNotifications(61L, "ALL", 20).size());
        assertEquals(1, service.listNotifications(61L, "GIFT", 20).size());
    }

    @Test
    void createNotificationShouldRejectUnsafeInteractionTargets() {
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(71L, "FOLLOW", "关注", "非法用户", "/pages/user/public-profile/index?userId=0"));
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(71L, "LIKE", "点赞", "非法动态", "/pages/community/detail/index?postId=preview"));
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(71L, "COMMENT", "评论", "多余参数", "/pages/community/detail/index?postId=7&from=preview"));
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(71L, "COMMENT", "评论", "重复参数", "/pages/community/detail/index?postId=7&postId=8"));
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(71L, "GIFT", "礼物", "非法模式", "/pages/gift/index?mode=send"));
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(71L, "GIFT", "礼物", "重复模式", "/pages/gift/index?mode=received&mode=received"));
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(71L, "CHAT", "私信", "非法会话", "/pages/chat/conversation/index?conversationId=0&receiverId=62"));
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(71L, "CHAT", "私信", "多余参数", "/pages/chat/conversation/index?conversationId=8&receiverId=62&from=preview"));
        assertThrows(IllegalArgumentException.class, () -> service.createNotification(71L, "CHAT", "私信", "重复参数", "/pages/chat/conversation/index?conversationId=8&conversationId=9&receiverId=62"));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification_record WHERE user_id = ?", Integer.class, 71L));
    }
}
