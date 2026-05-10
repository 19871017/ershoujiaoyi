package com.secondhand.platform.modules.admin;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.secondhand.platform.modules.aftersales.application.AfterSalesApplicationService;
import com.secondhand.platform.modules.audit.application.AuditApplicationService;
import com.secondhand.platform.modules.location.LocationApplicationService;
import com.secondhand.platform.modules.order.application.OrderApplicationService;
import com.secondhand.platform.modules.user.application.UserApplicationService;
import com.secondhand.platform.modules.wallet_ledger.application.WalletLedgerService;
import com.secondhand.platform.shared.web.AdminAccessGuard;
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

class AdminControllerRbacTest {
    private JdbcTemplate jdbcTemplate;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);

        AuditApplicationService auditApplicationService = new AuditApplicationService(jdbcTemplate);

        AdminController controller = new AdminController(
                auditApplicationService,
                mock(WalletLedgerService.class),
                new LocationApplicationService(new com.secondhand.platform.modules.location.BaiduReverseGeocodeClient(), "", jdbcTemplate),
                mock(AfterSalesApplicationService.class),
                mock(OrderApplicationService.class),
                mock(UserApplicationService.class),
                new AdminAccessGuard(new CurrentUserResolver(), jdbcTemplate)
        );
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void adminDashboardRequiresPersistedAuditReadPermissionNotOnlyAdminModeHeader() throws Exception {
        mvc.perform(get("/api/admin/dashboard")
                        .header("X-Admin-Mode", "enabled")
                        .header("X-User-Id", "7"))
                .andExpect(status().isForbidden());

        createActiveUser(7L);
        grantPermission(7L, "audit:read");

        mvc.perform(get("/api/admin/dashboard")
                        .header("X-Admin-Mode", "enabled")
                        .header("X-User-Id", "7"))
                .andExpect(status().isOk());
    }

    @Test
    void adminDashboardAcceptsPersistedAuditReadPermissionWithoutAdminModeHeader() throws Exception {
        createActiveUser(9L);
        grantPermission(9L, "audit:read");

        mvc.perform(get("/api/admin/dashboard")
                        .header("X-User-Id", "9"))
                .andExpect(status().isOk());
    }

    @Test
    void adminDashboardRequiresActivePersistedAdminAccountWithPermission() throws Exception {
        grantPermission(11L, "audit:read");

        mvc.perform(get("/api/admin/dashboard")
                        .header("X-User-Id", "11"))
                .andExpect(status().isForbidden());

        createActiveUser(11L);

        mvc.perform(get("/api/admin/dashboard")
                        .header("X-User-Id", "11"))
                .andExpect(status().isOk());
    }

    @Test
    void adminLocationConfigUpdatePersistsOperatorAuditLog() throws Exception {
        createActiveUser(21L);
        grantPermission(21L, "system:config");

        mvc.perform(post("/api/admin/location/config")
                        .header("X-User-Id", "21")
                        .contentType("application/json")
                        .content("{\"defaultCity\":\"杭州\",\"coordinateType\":\"gcj02ll\"}"))
                .andExpect(status().isOk());

        Integer count = jdbcTemplate.queryForObject("""
                select count(1)
                from admin_audit_log
                where action = 'LOCATION_CONFIG_UPDATE'
                  and operator_id = 21
                  and target_type = 'SYSTEM_CONFIG'
                  and target_id = 'location'
                  and result = 'SUCCESS'
                  and summary not like '%SECRET%'
                """, Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
    }

    private void grantPermission(Long userId, String permission) {
        jdbcTemplate.update("""
                insert into admin_user_permission (user_id, permission_code, enabled, created_at, updated_at)
                values (?, ?, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId, permission);
    }

    private void createActiveUser(Long userId) {
        jdbcTemplate.update("""
                insert into user_account (id, user_no, phone, password_hash, nickname, status, created_at, updated_at)
                values (?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId, "U-ADMIN-" + userId, "1390000" + userId, "hash", "管理员" + userId);
    }
}
