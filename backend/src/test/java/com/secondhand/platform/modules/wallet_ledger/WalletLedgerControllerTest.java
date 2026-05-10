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
        WalletLedgerController controller = new WalletLedgerController(
                walletLedgerService,
                new AuditApplicationService(jdbcTemplate),
                new CurrentUserResolver()
        );
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void payoutAccountEndpointsShouldBindAndReturnOnlyMaskedServerOwnedAccount() throws Exception {
        mvc.perform(post("/api/wallet/payout-account")
                        .header("X-User-Id", "9")
                        .contentType("application/json")
                        .content("{\"paymentMethod\":\"ALIPAY\",\"accountName\":\"Alice\",\"accountNo\":\"alice@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payoutAccountId").isNumber())
                .andExpect(jsonPath("$.data.paymentMethod").value("ALIPAY"))
                .andExpect(jsonPath("$.data.accountName").value("Alice"))
                .andExpect(jsonPath("$.data.maskedAccountNo").value("a***e@example.com"))
                .andExpect(jsonPath("$.data.accountNo").doesNotExist());

        mvc.perform(get("/api/wallet/payout-account").header("X-User-Id", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maskedAccountNo").value("a***e@example.com"))
                .andExpect(jsonPath("$.data.accountNo").doesNotExist());
    }

    @Test
    void payoutAccountBindingShouldRejectMaskedClientInputWithoutPersistingAccount() throws Exception {
        mvc.perform(post("/api/wallet/payout-account")
                        .header("X-User-Id", "9")
                        .contentType("application/json")
                        .content("{\"paymentMethod\":\"ALIPAY\",\"accountName\":\"Alice\",\"accountNo\":\"6222 **** **** 8088\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        Integer count = jdbcTemplate.queryForObject("select count(*) from payout_account where user_id = ?", Integer.class, 9L);
        org.junit.jupiter.api.Assertions.assertEquals(0, count);
    }
}
