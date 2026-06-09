package com.secondhand.platform.modules.product;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.secondhand.platform.modules.media.application.MediaUploadTicketService;
import com.secondhand.platform.modules.product.application.CreateProductRequest;
import com.secondhand.platform.modules.product.application.ProductApplicationService;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import com.secondhand.platform.shared.web.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductControllerTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private ProductApplicationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        service = new ProductApplicationService(jdbcTemplate, new MediaUploadTicketService(jdbcTemplate));
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        mvc = MockMvcBuilders.standaloneSetup(new ProductController(service, new CurrentUserResolver(jdbcTemplate, environment)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void publicProductDetailReturnsServerDerivedSellerIdForChatRouting() throws Exception {
        CreateProductResponse product = service.createProduct(41L, product(41L, "可私信卖家商品", "88.00"));
        service.approveForSale(product.getProductId());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/products/{productId}", product.getProductId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId", is(product.getProductId().intValue())))
                .andExpect(jsonPath("$.data.sellerId", is(41)));
    }

    @Test
    void sellerVisibilityEndpointUsesServerDerivedOwnerAndReturnsPersistedState() throws Exception {
        CreateProductResponse product = service.createProduct(41L, product(41L, "可下架商品", "88.00"));
        service.approveForSale(product.getProductId());

        mvc.perform(put("/api/products/{productId}/visibility", product.getProductId())
                        .header("X-User-Id", "41")
                        .header("X-Dev-Mode", "enabled")
                        .contentType("application/json")
                        .content("{\"visible\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId", is(product.getProductId().intValue())))
                .andExpect(jsonPath("$.data.status", is("OFFLINE")))
                .andExpect(jsonPath("$.data.visible", is(false)));

        mvc.perform(put("/api/products/{productId}/visibility", product.getProductId())
                        .header("X-User-Id", "42")
                        .header("X-Dev-Mode", "enabled")
                        .contentType("application/json")
                        .content("{\"visible\":true}"))
                .andExpect(status().isBadRequest());
    }

    private void upsertSellerProfile(Long userId) {
        Integer accountRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account WHERE id = ?", Integer.class, userId);
        if (accountRows == null || accountRows == 0) {
            jdbcTemplate.update("INSERT INTO user_account (id, user_no, phone, password_hash, nickname, status) VALUES (?, ?, ?, ?, ?, ?)", userId, "U-PC-" + userId, "1380016" + String.format("%04d", userId), "hash", "商品卖家" + userId, "ACTIVE");
        }
        Integer profileRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_profile WHERE user_id = ?", Integer.class, userId);
        if (profileRows == null || profileRows == 0) {
            jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, main_role, video_identity_status, video_verified) VALUES (?, ?, ?, ?, ?)", userId, "VERIFIED", "SELLER", "APPROVED", true);
            ensureSellerVideoCertification(userId);
            return;
        }
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", "SELLER", "APPROVED", true, userId);
        ensureSellerVideoCertification(userId);
    }

    private void ensureSellerVideoCertification(Long userId) {
        String videoUrl = "/uploads/video-identity/" + userId + "/product-controller-test.mp4";
        jdbcTemplate.update("""
                INSERT INTO media_upload_ticket (ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at)
                SELECT ?, ?, 'VIDEO_IDENTITY', 'product-controller-test.mp4', 'video/mp4', 1024, ?, 'hash', 'UPLOADED', CURRENT_TIMESTAMP, DATEADD('HOUR', 1, CURRENT_TIMESTAMP)
                WHERE NOT EXISTS (SELECT 1 FROM media_upload_ticket WHERE storage_url = ?)
                """, "VIDEO-PC-" + userId, userId, videoUrl, videoUrl);
        jdbcTemplate.update("""
                INSERT INTO audit_record (audit_no, audit_type, user_id, target_type, target_id, reason, description, status, reviewed_at)
                SELECT ?, 'VIDEO_IDENTITY', ?, 'USER', ?, ?, '测试卖家视频认证通过', 'APPROVED', CURRENT_TIMESTAMP
                WHERE NOT EXISTS (SELECT 1 FROM audit_record WHERE audit_no = ?)
                """, "AUDIT-VIDEO-PC-" + userId, userId, String.valueOf(userId), videoUrl, "AUDIT-VIDEO-PC-" + userId);
    }

    private CreateProductRequest product(Long sellerId, String title, String price) {
        upsertSellerProfile(sellerId);
        CreateProductRequest request = new CreateProductRequest();
        request.setTitle(title);
        request.setDescription("卖家上下架控制测试");
        request.setPrice(new BigDecimal(price));
        MediaUploadTicketService tickets = new MediaUploadTicketService(jdbcTemplate);
        String image = tickets.issue(sellerId, "PRODUCT_IMAGE", "image/jpeg", 300_000L, title + ".jpg").storageUrl();
        jdbcTemplate.update("UPDATE media_upload_ticket SET status = 'UPLOADED' WHERE owner_user_id = ? AND storage_url = ?", sellerId, image);
        request.setImageUrls(List.of(image));
        return request;
    }
}
