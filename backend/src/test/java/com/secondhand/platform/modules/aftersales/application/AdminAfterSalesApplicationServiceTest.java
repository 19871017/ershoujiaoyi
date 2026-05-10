package com.secondhand.platform.modules.aftersales.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.secondhand.platform.modules.aftersales.AfterSalesResponse;
import java.math.BigDecimal;
import java.util.List;
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
    void adminListShouldReturnPersistedAfterSalesWithStatusFilterAndBoundedLimit() {
        insertAfterSales("AS-ADMIN-20260510-0001", "ORDER-ADMIN-20260510-0001", 8801L,
                "尺码不合适，申请售后协调", "/uploads/evidence/after-sales/8801/proof.jpg");
        insertAfterSalesWithStatus("AS-ADMIN-20260510-0003", "ORDER-ADMIN-20260510-0003", 8803L,
                "售后已完成记录", "/uploads/evidence/after-sales/8803/proof.jpg", "APPROVED");

        List<AfterSalesResponse> pendingRows = afterSalesService.listAdminAfterSales("PENDING_REVIEW", 20);
        List<AfterSalesResponse> allRows = afterSalesService.listAdminAfterSales("ALL", 10);

        assertEquals(1, pendingRows.size());
        assertEquals("AS-ADMIN-20260510-0001", pendingRows.get(0).getAfterSalesNo());
        assertEquals("PENDING_REVIEW", pendingRows.get(0).getStatus());
        assertEquals(2, allRows.size());
        assertThrows(IllegalArgumentException.class, () -> afterSalesService.listAdminAfterSales("preview-status", 20));
        assertThrows(IllegalArgumentException.class, () -> afterSalesService.listAdminAfterSales("PENDING_REVIEW", 101));
    }

    @Test
    void adminDetailShouldRejectPreviewAndMalformedAfterSalesNumbers() {
        insertAfterSales("AS-ADMIN-20260510-0002", "ORDER-ADMIN-20260510-0002", 8802L,
                "正常售后描述", "/uploads/evidence/after-sales/8802/proof.jpg");

        assertThrows(IllegalArgumentException.class, () -> afterSalesService.getAdminDetail("preview-after-sales"));
        assertThrows(IllegalArgumentException.class, () -> afterSalesService.getAdminDetail("AS-DEMO-0001"));
        assertThrows(IllegalArgumentException.class, () -> afterSalesService.getAdminDetail("AS-ADMIN-20260510-0002/../raw"));
    }

    @Test
    void adminReviewShouldPersistStatusAndOperatorAuditLogWithoutChangingAlreadyReviewedRows() {
        insertAfterSales("AS-ADMIN-20260510-0004", "ORDER-ADMIN-20260510-0004", 8804L,
                "售后等待人工复核", "/uploads/evidence/after-sales/8804/proof.jpg");

        AfterSalesResponse approved = afterSalesService.adminReview("AS-ADMIN-20260510-0004", "APPROVED", 9901L, "同意退款协调");

        assertEquals("APPROVED", approved.getStatus());
        assertEquals("APPROVED", jdbcTemplate.queryForObject("select after_sales_status from after_sales_record where after_sales_no = ?", String.class, "AS-ADMIN-20260510-0004"));
        Integer logCount = jdbcTemplate.queryForObject("""
                select count(1)
                from admin_audit_log
                where action = 'AFTER_SALES_REVIEW'
                  and operator_id = 9901
                  and target_type = 'AFTER_SALES'
                  and target_id = 'AS-ADMIN-20260510-0004'
                  and result = 'APPROVED'
                  and summary not like '%13800138000%'
                """, Integer.class);
        assertEquals(1, logCount);

        assertThrows(IllegalStateException.class, () -> afterSalesService.adminReview("AS-ADMIN-20260510-0004", "REJECTED", 9901L, "重复处理"));
        Integer unchangedLogCount = jdbcTemplate.queryForObject("select count(1) from admin_audit_log where action = 'AFTER_SALES_REVIEW' and target_id = 'AS-ADMIN-20260510-0004'", Integer.class);
        assertEquals(1, unchangedLogCount);
    }

    @Test
    void adminReviewShouldFailClosedForMalformedStatusOrOperator() {
        insertAfterSales("AS-ADMIN-20260510-0005", "ORDER-ADMIN-20260510-0005", 8805L,
                "售后等待复核", "/uploads/evidence/after-sales/8805/proof.jpg");

        assertThrows(IllegalArgumentException.class, () -> afterSalesService.adminReview("preview-after-sales", "APPROVED", 9901L, "ok"));
        assertThrows(IllegalArgumentException.class, () -> afterSalesService.adminReview("AS-ADMIN-20260510-0005", "PREVIEW", 9901L, "ok"));
        assertThrows(IllegalArgumentException.class, () -> afterSalesService.adminReview("AS-ADMIN-20260510-0005", "APPROVED", 0L, "ok"));
        assertEquals("PENDING_REVIEW", jdbcTemplate.queryForObject("select after_sales_status from after_sales_record where after_sales_no = ?", String.class, "AS-ADMIN-20260510-0005"));
    }

    private void insertAfterSales(String afterSalesNo, String orderNo, Long applicantId, String description, String evidenceUrls) {
        insertAfterSalesWithStatus(afterSalesNo, orderNo, applicantId, description, evidenceUrls, "PENDING_REVIEW");
    }

    private void insertAfterSalesWithStatus(String afterSalesNo, String orderNo, Long applicantId, String description, String evidenceUrls, String status) {
        jdbcTemplate.update("""
                insert into after_sales_record (after_sales_no, order_no, applicant_id, after_sales_type, refund_amount, reason, description, evidence_urls, after_sales_status, created_at, updated_at)
                values (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, afterSalesNo, orderNo, applicantId, "REFUND_ONLY", new BigDecimal("30.00"), "尺码不合适", description, evidenceUrls, status);
    }
}
