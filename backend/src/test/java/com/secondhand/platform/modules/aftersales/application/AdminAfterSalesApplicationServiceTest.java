package com.secondhand.platform.modules.aftersales.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.secondhand.platform.modules.aftersales.AfterSalesResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class AdminAfterSalesApplicationServiceTest {
    private JdbcTemplate jdbcTemplate;
    private AfterSalesApplicationService afterSalesService;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        afterSalesService = new AfterSalesApplicationService(jdbcTemplate);
    }

    @Test
    void adminDetailShouldLoadPersistedAfterSalesByPositiveBackendNumber() {
        insertAfterSales("AS-ADMIN-20260510-0001", "ORDER-ADMIN-20260510-0001", 8801L,
                "尺码不合适，申请售后协调", "/uploads/evidence/after-sales/8801/proof.jpg");

        AfterSalesResponse detail = afterSalesService.getAdminDetail("AS-ADMIN-20260510-0001");

        assertEquals("AS-ADMIN-20260510-0001", detail.getAfterSalesNo());
        assertEquals("ORDER-ADMIN-20260510-0001", detail.getOrderNo());
        assertEquals(8801L, detail.getApplicantId());
        assertEquals("PENDING_REVIEW", detail.getStatus());
        assertEquals(1, detail.getEvidenceUrls().size());
        assertFalse(detail.getDescription().contains("13800138000"));
    }

    @Test
    void adminDetailShouldRejectPreviewAndMalformedAfterSalesNumbers() {
        insertAfterSales("AS-ADMIN-20260510-0002", "ORDER-ADMIN-20260510-0002", 8802L,
                "正常售后描述", "/uploads/evidence/after-sales/8802/proof.jpg");

        assertThrows(IllegalArgumentException.class, () -> afterSalesService.getAdminDetail("preview-after-sales"));
        assertThrows(IllegalArgumentException.class, () -> afterSalesService.getAdminDetail("AS-DEMO-0001"));
        assertThrows(IllegalArgumentException.class, () -> afterSalesService.getAdminDetail("AS-ADMIN-20260510-0002/../raw"));
    }

    private void insertAfterSales(String afterSalesNo, String orderNo, Long applicantId, String description, String evidenceUrls) {
        jdbcTemplate.update("""
                insert into after_sales_record (after_sales_no, order_no, applicant_id, after_sales_type, refund_amount, reason, description, evidence_urls, after_sales_status, created_at, updated_at)
                values (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, afterSalesNo, orderNo, applicantId, "REFUND_ONLY", new BigDecimal("30.00"), "尺码不合适", description, evidenceUrls, "PENDING_REVIEW");
    }
}
