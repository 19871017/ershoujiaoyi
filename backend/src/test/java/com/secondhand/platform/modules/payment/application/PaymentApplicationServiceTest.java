package com.secondhand.platform.modules.payment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.secondhand.platform.modules.wallet_ledger.application.WalletLedgerService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class PaymentApplicationServiceTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private WalletLedgerService walletLedgerService;
    private PaymentApplicationService service;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        walletLedgerService = new WalletLedgerService(jdbcTemplate);
        service = new PaymentApplicationService(walletLedgerService, jdbcTemplate);
    }

    @Test
    void createRechargeShouldPersistPendingOrder() {
        RechargeResponse response = service.createRecharge(1L, request("66.60", "wechat"));

        assertEquals("PENDING", response.status());
        assertEquals("WECHAT", response.channel());
        assertEquals(new BigDecimal("66.60"), response.amount());
        assertEquals(1, countPayment(response.rechargeNo()));
        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "SELECT status FROM payment_recharge_order WHERE recharge_no = ?", String.class, response.rechargeNo()));
    }

    @Test
    void simulateSuccessShouldPersistPaidStatusAndReplayAfterServiceRecreation() {
        RechargeResponse pending = service.createRecharge(1L, request("88.00", "alipay"));

        RechargeResponse paid = service.simulateRechargeSuccess(1L, pending.rechargeNo());
        PaymentApplicationService reloaded = new PaymentApplicationService(walletLedgerService, jdbcTemplate);
        RechargeResponse replay = reloaded.simulateRechargeSuccess(1L, pending.rechargeNo());

        assertEquals("PAID", paid.status());
        assertNotNull(paid.ledgerNo());
        assertEquals(paid.ledgerNo(), replay.ledgerNo());
        assertEquals(new BigDecimal("88.00"), replay.balanceAfter());
        assertEquals(1, walletLedgerService.listLedger(1L).size());
        assertEquals("PAID", jdbcTemplate.queryForObject(
                "SELECT status FROM payment_recharge_order WHERE recharge_no = ?", String.class, pending.rechargeNo()));
        assertEquals(paid.ledgerNo(), jdbcTemplate.queryForObject(
                "SELECT ledger_no FROM payment_recharge_order WHERE recharge_no = ?", String.class, pending.rechargeNo()));
    }

    @Test
    void simulateSuccessShouldRejectOtherUser() {
        RechargeResponse pending = service.createRecharge(1L, request("20.00", "wechat"));

        assertThrows(IllegalArgumentException.class, () -> service.simulateRechargeSuccess(2L, pending.rechargeNo()));
    }

    @Test
    void createRechargeShouldRejectInvalidAmountAndChannel() {
        assertThrows(IllegalArgumentException.class, () -> service.createRecharge(1L, request("0.00", "wechat")));
        assertThrows(IllegalArgumentException.class, () -> service.createRecharge(1L, request("1.234", "wechat")));
        assertThrows(IllegalArgumentException.class, () -> service.createRecharge(1L, request("1.00", "bank")));
    }

    private int countPayment(String rechargeNo) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_recharge_order WHERE recharge_no = ?", Integer.class, rechargeNo);
        return count == null ? 0 : count;
    }

    private CreateRechargeRequest request(String amount, String channel) {
        CreateRechargeRequest request = new CreateRechargeRequest();
        request.setAmount(new BigDecimal(amount));
        request.setChannel(channel);
        return request;
    }
}
