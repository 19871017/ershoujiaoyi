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

    private EmbeddedDatabase database() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
    }
}
