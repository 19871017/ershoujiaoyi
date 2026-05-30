package com.secondhand.platform.modules.payment;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.secondhand.platform.modules.payment.application.CreateRechargeRequest;
import com.secondhand.platform.modules.payment.application.PaymentApplicationService;
import com.secondhand.platform.modules.payment.application.RechargeResponse;
import com.secondhand.platform.modules.wallet_ledger.application.WalletLedgerService;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import com.secondhand.platform.shared.web.GlobalExceptionHandler;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PaymentControllerTest {
    private PaymentApplicationService paymentService;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        paymentService = new PaymentApplicationService(new WalletLedgerService(jdbcTemplate), jdbcTemplate);
    }

    @Test
    void simulateRechargeSuccessRejectsWithoutDevelopmentProfile() throws Exception {
        String rechargeNo = createRechargeNo(71L);

        mvcWithProfiles().perform(simulateRequest(71L, rechargeNo, true))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("forbidden")));
    }

    @Test
    void simulateRechargeSuccessRejectsProductionEvenWhenLocalProfileIsAlsoActive() throws Exception {
        String rechargeNo = createRechargeNo(72L);

        mvcWithProfiles("local", "prod").perform(simulateRequest(72L, rechargeNo, true))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("forbidden")));
    }

    @Test
    void simulateRechargeSuccessRejectsMissingDevModeHeaderInDevelopmentProfile() throws Exception {
        String rechargeNo = createRechargeNo(73L);

        mvcWithProfiles("dev").perform(simulateRequest(73L, rechargeNo, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("bad request")));
    }

    @Test
    void simulateRechargeSuccessAllowsDevelopmentProfileWithDevModeHeader() throws Exception {
        String rechargeNo = createRechargeNo(74L);

        mvcWithProfiles("local").perform(simulateRequest(74L, rechargeNo, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rechargeNo", is(rechargeNo)))
                .andExpect(jsonPath("$.data.status", is("PAID")));
    }

    private MockMvc mvcWithProfiles(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        PaymentController controller = new PaymentController(
                paymentService, new CurrentUserResolver(jdbcTemplate, environment), environment);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private RequestBuilder simulateRequest(long userId, String rechargeNo, boolean devMode) {
        MockHttpServletRequestBuilder request = post("/api/payments/recharge/simulate-success")
                .header("X-User-Id", String.valueOf(userId))
                .contentType("application/json")
                .content("{\"rechargeNo\":\"" + rechargeNo + "\"}");
        if (devMode) {
            return request.header("X-Dev-Mode", "enabled");
        }
        return request;
    }

    private String createRechargeNo(long userId) {
        ensureActiveUser(userId);
        CreateRechargeRequest request = new CreateRechargeRequest();
        request.setAmount(new BigDecimal("18.80"));
        request.setChannel("wechat");
        RechargeResponse response = paymentService.createRecharge(userId, request);
        return response.rechargeNo();
    }

    private void ensureActiveUser(long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE id = ?", Integer.class, userId);
        if (count != null && count > 0) {
            jdbcTemplate.update("UPDATE user_account SET status = 'ACTIVE' WHERE id = ?", userId);
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO user_account (id, user_no, phone, password_hash, nickname, status) VALUES (?, ?, ?, ?, ?, ?)",
                userId,
                "U-PAY-CTRL-" + userId,
                "1380040" + String.format("%04d", userId),
                "hash",
                "支付接口用户" + userId,
                "ACTIVE");
    }
}
