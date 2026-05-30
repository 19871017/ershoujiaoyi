package com.secondhand.platform.modules.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.secondhand.platform.modules.auth.application.AuthApplicationService;
import com.secondhand.platform.shared.web.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(new AuthApplicationService(jdbcTemplate)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void registerRateLimitIgnoresSpoofableForwardedHeaders() throws Exception {
        String remoteAddr = "203.0.113.44";

        mvc.perform(registrationRequest("13800139901", remoteAddr, "198.51.100.1", "198.51.100.11"))
                .andExpect(status().isOk());

        mvc.perform(registrationRequest("13800139902", remoteAddr, "198.51.100.2", "198.51.100.12"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("daily registration limit exceeded"));

        assertEquals(1, count("user_account"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_config WHERE config_group = 'auth-registration-limit'",
                Integer.class));
    }

    private MockHttpServletRequestBuilder registrationRequest(
            String mobile,
            String remoteAddr,
            String forwardedFor,
            String realIp
    ) {
        return post("/api/auth/register")
                .with(request -> {
                    request.setRemoteAddr(remoteAddr);
                    return request;
                })
                .header("X-Forwarded-For", forwardedFor)
                .header("X-Real-IP", realIp)
                .contentType("application/json")
                .content(registerPayload(mobile));
    }

    private String registerPayload(String mobile) {
        return "{\"mobile\":\"" + mobile + "\",\"password\":\"pass-123456\",\"gender\":\"goddess\"}";
    }

    private int count(String table) {
        Integer value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }
}
