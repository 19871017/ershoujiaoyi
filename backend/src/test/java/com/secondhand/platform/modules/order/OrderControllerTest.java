package com.secondhand.platform.modules.order;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.secondhand.platform.modules.media.application.MediaUploadTicketService;
import com.secondhand.platform.modules.order.application.CreateOrderRequest;
import com.secondhand.platform.modules.order.application.OrderApplicationService;
import com.secondhand.platform.modules.product.CreateProductResponse;
import com.secondhand.platform.modules.product.application.CreateProductRequest;
import com.secondhand.platform.modules.product.application.ProductApplicationService;
import com.secondhand.platform.modules.wallet_ledger.application.WalletLedgerService;
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

class OrderControllerTest {
    private JdbcTemplate jdbcTemplate;
    private ProductApplicationService productService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        MediaUploadTicketService mediaUploadTicketService = new MediaUploadTicketService(jdbcTemplate);
        productService = new ProductApplicationService(jdbcTemplate, mediaUploadTicketService);
        OrderApplicationService orderService = new OrderApplicationService(productService, new WalletLedgerService(jdbcTemplate), jdbcTemplate);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        mvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService, new CurrentUserResolver(jdbcTemplate, environment)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createOrderReturnsSpecificSafeMessageWhenBuyerIsSeller() throws Exception {
        CreateProductResponse product = approvedProduct("自买接口测试商品", "168.00", 8801L);

        mvc.perform(post("/api/orders")
                        .header("X-User-Id", "8801")
                        .header("X-Dev-Mode", "enabled")
                        .contentType("application/json")
                        .content("{\"goodsId\":" + product.getProductId() + ",\"acceptedTradeRule\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("cannot buy your own product")));
    }

    private CreateProductResponse approvedProduct(String title, String price, Long sellerId) {
        upsertSellerProfile(sellerId);
        CreateProductRequest request = new CreateProductRequest();
        request.setTitle(title);
        request.setDescription("订单接口测试商品");
        request.setPrice(new BigDecimal(price));
        String image = new MediaUploadTicketService(jdbcTemplate)
                .issue(sellerId, "PRODUCT_IMAGE", "image/jpeg", 300_000L, title + ".jpg")
                .storageUrl();
        jdbcTemplate.update("UPDATE media_upload_ticket SET status = 'UPLOADED' WHERE owner_user_id = ? AND storage_url = ?", sellerId, image);
        request.setImageUrls(List.of(image));
        CreateProductResponse response = productService.createProduct(sellerId, request);
        productService.approveForSale(response.getProductId());
        return response;
    }

    private void upsertSellerProfile(Long userId) {
        Integer accountRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account WHERE id = ?", Integer.class, userId);
        if (accountRows == null || accountRows == 0) {
            jdbcTemplate.update("INSERT INTO user_account (id, user_no, phone, password_hash, nickname, status) VALUES (?, ?, ?, ?, ?, ?)", userId, "U-ORDER-CTRL-" + userId, "1380020" + String.format("%04d", userId), "hash", "订单接口卖家" + userId, "ACTIVE");
        }
        Integer profileRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_profile WHERE user_id = ?", Integer.class, userId);
        if (profileRows == null || profileRows == 0) {
            jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, main_role, video_identity_status, video_verified) VALUES (?, ?, ?, ?, ?)", userId, "VERIFIED", "SELLER", "APPROVED", true);
            return;
        }
        jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", "SELLER", "APPROVED", true, userId);
    }
}
