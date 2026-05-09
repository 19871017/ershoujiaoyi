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
