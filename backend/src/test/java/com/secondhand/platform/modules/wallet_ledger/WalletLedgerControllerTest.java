package com.secondhand.platform.modules.wallet_ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.secondhand.platform.modules.audit.application.AuditApplicationService;
import com.secondhand.platform.modules.wallet_ledger.application.WalletLedgerService;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import com.secondhand.platform.shared.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WalletLedgerControllerTest {
    private MockMvc mvc;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        WalletLedgerService walletLedgerService = new WalletLedgerService(jdbcTemplate);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        WalletLedgerController controller = new WalletLedgerController(
                walletLedgerService,
                new AuditApplicationService(jdbcTemplate),
                new CurrentUserResolver(jdbcTemplate, environment)
        );
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        createActiveUser(9L);
    }

    private void createActiveUser(Long userId) {
        jdbcTemplate.update("""
                INSERT INTO user_account (id, user_no, phone, password_hash, nickname, status)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                """, userId, "U-WALLET-" + userId, "1392000" + userId, "hash", "钱包用户" + userId);
    }

    @Test
    void payoutAccountEndpointsShouldBindAndReturnOnlyMaskedServerOwnedAccount() throws Exception {
        mvc.perform(post("/api/wallet/payout-account")
                        .header("X-User-Id", "9")
                        .header("X-Dev-Mode", "enabled")
                        .contentType("application/json")
                        .content("{\"paymentMethod\":\"ALIPAY\",\"accountName\":\"Alice\",\"accountNo\":\"alice@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payoutAccountId").isNumber())
                .andExpect(jsonPath("$.data.paymentMethod").value("ALIPAY"))
                .andExpect(jsonPath("$.data.accountName").value("Alice"))
                .andExpect(jsonPath("$.data.maskedAccountNo").value("a***e@example.com"))
                .andExpect(jsonPath("$.data.accountNo").doesNotExist());

        mvc.perform(get("/api/wallet/payout-account")
                        .header("X-User-Id", "9")
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maskedAccountNo").value("a***e@example.com"))
                .andExpect(jsonPath("$.data.accountNo").doesNotExist());
    }

    @Test
    void payoutAccountBindingShouldRejectMaskedClientInputWithoutPersistingAccount() throws Exception {
        mvc.perform(post("/api/wallet/payout-account")
                        .header("X-User-Id", "9")
                        .header("X-Dev-Mode", "enabled")
                        .contentType("application/json")
                        .content("{\"paymentMethod\":\"ALIPAY\",\"accountName\":\"Alice\",\"accountNo\":\"6222 **** **** 8088\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        Integer count = jdbcTemplate.queryForObject("select count(*) from payout_account where user_id = ?", Integer.class, 9L);
        org.junit.jupiter.api.Assertions.assertEquals(0, count);
    }

    @Test
    void ledgerDetailShouldReturnCurrentUsersLedgerOnly() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO wallet_account (user_id, recharge_balance, income_balance, frozen_balance, withdrawable_balance)
                VALUES (9, 0, 0, 0, 0)
                """);
        jdbcTemplate.update("""
                INSERT INTO wallet_ledger_entry (ledger_no,user_id,direction,amount,biz_type,biz_no,balance_type,balance_before,balance_after,status,idempotency_key,remark)
                VALUES ('CR-DETAIL-9',9,'CREDIT',25.00,'RECHARGE','RC-9','RECHARGE',0.00,25.00,'SUCCESS','detail-9','详情测试')
                """);
        createActiveUser(10L);

        mvc.perform(get("/api/wallet/ledger/CR-DETAIL-9")
                        .header("X-User-Id", "9")
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerNo").value("CR-DETAIL-9"))
                .andExpect(jsonPath("$.data.amount").value(25.00))
                .andExpect(jsonPath("$.data.remark").value("详情测试"));

        mvc.perform(get("/api/wallet/ledger/CR-DETAIL-9")
                        .header("X-User-Id", "10")
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
