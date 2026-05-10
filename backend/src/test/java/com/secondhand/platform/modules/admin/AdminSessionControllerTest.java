package com.secondhand.platform.modules.admin;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.secondhand.platform.modules.auth.LoginRequest;
import com.secondhand.platform.modules.auth.application.AuthApplicationService;
import com.secondhand.platform.shared.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminSessionControllerTest {
    private JdbcTemplate jdbcTemplate;
    private MockMvc mvc;
    private AuthApplicationService authApplicationService;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        authApplicationService = new AuthApplicationService(jdbcTemplate);
        mvc = MockMvcBuilders.standaloneSetup(new AdminSessionController(jdbcTemplate))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void adminLoginReturnsPersistedPermissionsForActiveOperatorOnly() throws Exception {
        createUserThroughPasswordLogin("13900000071", "admin-pass-71");
        Long userId = jdbcTemplate.queryForObject("select id from user_account where phone = ?", Long.class, "13900000071");
        grantPermission(userId, "audit:read");
        grantPermission(userId, "finance:read");

        mvc.perform(post("/api/admin/session/login")
                        .contentType("application/json")
                        .content("{\"mobile\":\"13900000071\",\"password\":\"admin-pass-71\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(String.valueOf(userId)))
                .andExpect(jsonPath("$.data.username").value("小原圈用户0071"))
                .andExpect(jsonPath("$.data.devAdminEnabled").value(true))
                .andExpect(jsonPath("$.data.permissions", containsInAnyOrder("audit:read", "finance:read")))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.accessKey").doesNotExist());
    }

    @Test
    void adminLoginRejectsUsersWithoutExplicitAdminPermission() throws Exception {
        createUserThroughPasswordLogin("13900000072", "admin-pass-72");

        mvc.perform(post("/api/admin/session/login")
                        .contentType("application/json")
                        .content("{\"mobile\":\"13900000072\",\"password\":\"admin-pass-72\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("forbidden"));
    }

    @Test
    void adminLoginRejectsWrongPasswordAndInactiveOperator() throws Exception {
        createUserThroughPasswordLogin("13900000073", "admin-pass-73");
        Long userId = jdbcTemplate.queryForObject("select id from user_account where phone = ?", Long.class, "13900000073");
        grantPermission(userId, "audit:read");

        mvc.perform(post("/api/admin/session/login")
                        .contentType("application/json")
                        .content("{\"mobile\":\"13900000073\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isForbidden());

        jdbcTemplate.update("update user_account set status = 'DISABLED' where id = ?", userId);

        mvc.perform(post("/api/admin/session/login")
                        .contentType("application/json")
                        .content("{\"mobile\":\"13900000073\",\"password\":\"admin-pass-73\"}"))
                .andExpect(status().isForbidden());
    }

    private void createUserThroughPasswordLogin(String mobile, String password) {
        LoginRequest request = new LoginRequest();
        request.setMobile(mobile);
        request.setPassword(password);
        authApplicationService.login(request);
    }

    private void grantPermission(Long userId, String permission) {
        jdbcTemplate.update("""
                insert into admin_user_permission (user_id, permission_code, enabled, created_at, updated_at)
                values (?, ?, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId, permission);
    }
}
