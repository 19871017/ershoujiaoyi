package com.secondhand.platform.modules.gift.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.secondhand.platform.modules.gift.SendGiftResponse;
import com.secondhand.platform.modules.wallet_ledger.WalletBalanceResponse;
import com.secondhand.platform.modules.wallet_ledger.application.CreditCommand;
import com.secondhand.platform.modules.wallet_ledger.application.WalletLedgerService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class GiftApplicationServiceTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private WalletLedgerService walletLedgerService;
    private GiftApplicationService service;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        walletLedgerService = new WalletLedgerService(jdbcTemplate);
        service = new GiftApplicationService(walletLedgerService, jdbcTemplate);
    }

    @Test
    void sendGiftShouldRejectWhenBothRequestNoAndClientGiftIdMissing() {
        SendGiftRequest request = giftRequest(2L, "ROSE", 1, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.sendGift(1L, request));
    }

    @Test
    void sendGiftShouldDebitSenderRechargeAndCreditReceiverIncomeAndPersistOrder() {
        seedUser(1L, "送礼人", "U-GIFT-SENDER");
        seedRecharge(1L, "100.00");
        SendGiftRequest request = giftRequest(2L, "COFFEE", 2, "gift-req-001", null);

        SendGiftResponse response = service.sendGift(1L, request);

        assertEquals("SUCCESS", response.getStatus());
        assertEquals("COFFEE", response.getGiftCode());
        assertMoney("12.00", response.getTotalAmount());
        assertMoney("2.40", response.getPlatformShare());
        assertMoney("9.60", response.getReceiverAmount());
        WalletBalanceResponse senderBalance = walletLedgerService.getBalance(1L);
        WalletBalanceResponse receiverBalance = walletLedgerService.getBalance(2L);
        assertMoney("88.00", senderBalance.getRechargeBalance());
        assertMoney("9.60", receiverBalance.getIncomeBalance());
        assertEquals(2, walletLedgerService.listLedger(1L).size());
        assertEquals(1, walletLedgerService.listLedger(2L).size());
        assertEquals(1, orderCount(response.getGiftOrderNo()));
        assertEquals(response.getDebitLedgerNo(), jdbcTemplate.queryForObject(
                "SELECT debit_ledger_no FROM gift_order WHERE gift_order_no = ?", String.class, response.getGiftOrderNo()));
        assertEquals(response.getReceiverCreditLedgerNo(), jdbcTemplate.queryForObject(
                "SELECT receiver_credit_ledger_no FROM gift_order WHERE gift_order_no = ?", String.class, response.getGiftOrderNo()));
        var notices = new com.secondhand.platform.modules.notification.application.NotificationApplicationService(jdbcTemplate)
                .listNotifications(2L, "GIFT", 20);
        assertEquals(1, notices.size());
        assertEquals("你收到了新礼物", notices.get(0).title());
        assertEquals("送礼人 送了 暖心咖啡 × 2", notices.get(0).description());
        assertEquals("/pages/gift/index?mode=received", notices.get(0).targetUrl());
    }

    @Test
    void repeatedRequestNoShouldReturnSameGiftOrderAndNotDoubleDebitAfterServiceRecreation() {
        seedRecharge(1L, "100.00");
        SendGiftRequest firstRequest = giftRequest(2L, "STAR", 1, "same-request-no", null);
        SendGiftRequest replayRequest = giftRequest(2L, "STAR", 1, "same-request-no", null);

        SendGiftResponse first = service.sendGift(1L, firstRequest);
        GiftApplicationService reloaded = new GiftApplicationService(walletLedgerService, jdbcTemplate);
        SendGiftResponse replay = reloaded.sendGift(1L, replayRequest);

        assertEquals(first.getGiftOrderNo(), replay.getGiftOrderNo());
        assertEquals(first.getDebitLedgerNo(), replay.getDebitLedgerNo());
        assertEquals(first.getReceiverCreditLedgerNo(), replay.getReceiverCreditLedgerNo());
        assertMoney("82.00", walletLedgerService.getBalance(1L).getRechargeBalance());
        assertMoney("13.50", walletLedgerService.getBalance(2L).getIncomeBalance());
        assertEquals(2, walletLedgerService.listLedger(1L).size());
        assertEquals(1, walletLedgerService.listLedger(2L).size());
        assertEquals(1, orderCount(first.getGiftOrderNo()));
        assertEquals(1, new com.secondhand.platform.modules.notification.application.NotificationApplicationService(jdbcTemplate)
                .listNotifications(2L, "GIFT", 20).size());
    }

    @Test
    void sameRequestNoFromDifferentSenderShouldCreateSeparateOrder() {
        seedRecharge(1L, "10.00");
        seedRecharge(3L, "10.00");

        SendGiftResponse first = service.sendGift(1L, giftRequest(2L, "ROSE", 1, "same-client-no", null));
        SendGiftResponse second = service.sendGift(3L, giftRequest(2L, "ROSE", 1, "same-client-no", null));

        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM gift_order", Integer.class));
        assertMoney("9.00", walletLedgerService.getBalance(1L).getRechargeBalance());
        assertMoney("9.00", walletLedgerService.getBalance(3L).getRechargeBalance());
        assertMoney("1.60", walletLedgerService.getBalance(2L).getIncomeBalance());
        assertThrows(AssertionError.class, () -> assertEquals(first.getGiftOrderNo(), second.getGiftOrderNo()));
    }

    @Test
    void clientGiftIdCanAlsoProvideIdempotencyKey() {
        seedRecharge(1L, "10.00");
        SendGiftRequest request = giftRequest(2L, "ROSE", 1, null, "client-gift-001");

        SendGiftResponse response = service.sendGift(1L, request);

        assertEquals("SUCCESS", response.getStatus());
        assertMoney("9.00", walletLedgerService.getBalance(1L).getRechargeBalance());
        assertMoney("0.80", walletLedgerService.getBalance(2L).getIncomeBalance());
    }

    @Test
    void recentGiftFeedShouldReturnPersistedGiftOrdersWithUserNames() {
        seedUser(1L, "小雨", "U-GIFT-1");
        seedUser(2L, "暖暖", "U-GIFT-2");
        seedUser(3L, "星星", "U-GIFT-3");
        seedRecharge(1L, "100.00");
        seedRecharge(3L, "100.00");

        SendGiftResponse first = service.sendGift(1L, giftRequest(2L, "ROSE", 1, "recent-001", null));
        SendGiftResponse latest = service.sendGift(3L, giftRequest(2L, "CROWN", 1, "recent-002", null));

        var feed = service.listRecentGiftFeed();

        assertEquals(2, feed.size());
        assertEquals(latest.getGiftOrderNo(), feed.get(0).getGiftOrderNo());
        assertEquals("星星", feed.get(0).getSenderName());
        assertEquals("暖暖", feed.get(0).getReceiverName());
        assertEquals("小原皇冠", feed.get(0).getGiftName());
        assertEquals("👑", feed.get(0).getGiftIcon());
        assertMoney("68.00", feed.get(0).getTotalAmount());
        assertEquals(first.getGiftOrderNo(), feed.get(1).getGiftOrderNo());
    }

    @Test
    void recentGiftFeedShouldDisplayLegacyGiftCodeNames() {
        seedUser(1L, "大王", "U-GIFT-1");
        seedUser(2L, "暖暖", "U-GIFT-2");
        insertLegacyGiftOrder("GF-LEGACY-CANDY", 1L, 2L, 1001L, "CANDY");
        insertLegacyGiftOrder("GF-LEGACY-RIBBON", 1L, 2L, 1002L, "RIBBON");

        var feed = service.listRecentGiftFeed();

        assertEquals(2, feed.size());
        assertEquals("GF-LEGACY-RIBBON", feed.get(0).getGiftOrderNo());
        assertEquals("丝带礼盒", feed.get(0).getGiftName());
        assertEquals("🎀", feed.get(0).getGiftIcon());
        assertEquals("GF-LEGACY-CANDY", feed.get(1).getGiftOrderNo());
        assertEquals("糖果", feed.get(1).getGiftName());
        assertEquals("🍬", feed.get(1).getGiftIcon());
    }

    private int orderCount(String giftOrderNo) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM gift_order WHERE gift_order_no = ?", Integer.class, giftOrderNo);
        return count == null ? 0 : count;
    }

    private void seedRecharge(Long userId, String amount) {
        CreditCommand command = new CreditCommand();
        command.setUserId(userId);
        command.setIdempotencyKey("seed-" + userId + '-' + amount);
        command.setBizType("TEST_SEED");
        command.setBizNo("seed");
        command.setBalanceType("RECHARGE");
        command.setAmount(new BigDecimal(amount));
        walletLedgerService.credit(command);
    }

    private void seedUser(Long userId, String nickname, String userNo) {
        jdbcTemplate.update("INSERT INTO user_account (id, user_no, phone, password_hash, nickname, status) VALUES (?, ?, ?, ?, ?, ?)",
                userId, userNo, "1380014" + String.format("%04d", userId), "hash", nickname, "ACTIVE");
    }

    private void insertLegacyGiftOrder(String giftOrderNo, Long senderId, Long receiverId, Long giftId, String giftCode) {
        jdbcTemplate.update("""
                INSERT INTO gift_order (
                  gift_order_no, idempotency_key, sender_id, receiver_id, gift_id, gift_code, quantity,
                  total_amount, platform_share, receiver_amount, debit_ledger_no, receiver_credit_ledger_no,
                  status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, 'SUCCESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                giftOrderNo,
                "legacy-" + giftOrderNo,
                senderId,
                receiverId,
                giftId,
                giftCode,
                new BigDecimal("1.00"),
                new BigDecimal("0.20"),
                new BigDecimal("0.80"),
                "DEBIT-" + giftOrderNo,
                "CREDIT-" + giftOrderNo);
    }

    private SendGiftRequest giftRequest(Long receiverId, String giftCode, Integer quantity, String requestNo, String clientGiftId) {
        SendGiftRequest request = new SendGiftRequest();
        request.setReceiverId(receiverId);
        request.setGiftCode(giftCode);
        request.setQuantity(quantity);
        request.setRequestNo(requestNo);
        request.setClientGiftId(clientGiftId);
        return request;
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected), actual);
    }
}
