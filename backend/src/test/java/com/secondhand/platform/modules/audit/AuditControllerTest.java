package com.secondhand.platform.modules.audit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondhand.platform.modules.audit.application.AuditApplicationService;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuditControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void legacyAuditSubmitEndpointMustNotReturnFakeSuccess() throws Exception {
        EmbeddedDatabase database = database();
        AuditController controller = new AuditController(
                new AuditApplicationService(new JdbcTemplate(database)),
                new CurrentUserResolver()
        );
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(post("/api/audit/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reportEndpointPersistsRealAuditRecord() throws Exception {
        EmbeddedDatabase database = database();
        AuditController controller = new AuditController(
                new AuditApplicationService(new JdbcTemplate(database)),
                new CurrentUserResolver()
        );
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        ReportRequest request = new ReportRequest();
        request.setTargetType("PRODUCT");
        request.setTargetId("PRODUCT-100001");
        request.setReason("SPAM");
        request.setDescription("商品描述明显异常");

        mvc.perform(post("/api/audit/reports")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditType").value(AuditApplicationService.AUDIT_TYPE_REPORT))
                .andExpect(jsonPath("$.data.status").value(AuditApplicationService.STATUS_PENDING))
                .andExpect(jsonPath("$.data.targetId").value("PRODUCT-100001"));
    }

    @Test
    void videoIdentityEndpointMustRejectClientSuppliedIdentityFields() throws Exception {
        EmbeddedDatabase database = database();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.update("INSERT INTO user_account (user_no, phone, password_hash, nickname, status) VALUES (?,?,?,?,?)", "U-VIDEO-API", "13800136666", "hash", "视频用户", "ACTIVE");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE phone = ?", Long.class, "13800136666");
        jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, video_identity_status, video_verified) VALUES (?,?,?,?)", userId, "UNVERIFIED", "UNVERIFIED", false);
        String videoUrl = new com.secondhand.platform.modules.media.application.MediaUploadTicketService(jdbcTemplate)
                .issue(userId, "VIDEO_IDENTITY", "video/mp4", 5_000_000L, "api-video.mp4")
                .storageUrl();
        AuditController controller = new AuditController(
                new AuditApplicationService(jdbcTemplate),
                new CurrentUserResolver()
        );
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.secondhand.platform.shared.web.GlobalExceptionHandler())
                .build();
        String payload = """
                {"videoUrl":"%s","description":"认证","userId":999,"identityStatus":"VERIFIED","videoVerified":true,"admin":true}
                """.formatted(videoUrl);

        mvc.perform(post("/api/audit/video-identity")
                        .header("X-User-Id", String.valueOf(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("identity fields must be server-derived"));

        Integer auditCount = jdbcTemplate.queryForObject("SELECT count(1) FROM audit_record WHERE audit_type = 'VIDEO_IDENTITY'", Integer.class);
        String identityStatus = jdbcTemplate.queryForObject("SELECT identity_status FROM user_profile WHERE user_id = ?", String.class, userId);
        String videoStatus = jdbcTemplate.queryForObject("SELECT video_identity_status FROM user_profile WHERE user_id = ?", String.class, userId);
        Boolean videoVerified = jdbcTemplate.queryForObject("SELECT video_verified FROM user_profile WHERE user_id = ?", Boolean.class, userId);
        org.junit.jupiter.api.Assertions.assertEquals(0, auditCount);
        org.junit.jupiter.api.Assertions.assertEquals("UNVERIFIED", identityStatus);
        org.junit.jupiter.api.Assertions.assertEquals("UNVERIFIED", videoStatus);
        org.junit.jupiter.api.Assertions.assertEquals(false, videoVerified);
    }

    private EmbeddedDatabase database() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
    }
}
