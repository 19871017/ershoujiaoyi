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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductControllerTest {
    private EmbeddedDatabase database;
    private ProductApplicationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        service = new ProductApplicationService(jdbcTemplate, new MediaUploadTicketService(jdbcTemplate));
        mvc = MockMvcBuilders.standaloneSetup(new ProductController(service, new CurrentUserResolver()))
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
                        .contentType("application/json")
                        .content("{\"visible\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId", is(product.getProductId().intValue())))
                .andExpect(jsonPath("$.data.status", is("OFFLINE")))
                .andExpect(jsonPath("$.data.visible", is(false)));

        mvc.perform(put("/api/products/{productId}/visibility", product.getProductId())
                        .header("X-User-Id", "42")
                        .contentType("application/json")
                        .content("{\"visible\":true}"))
                .andExpect(status().isBadRequest());
    }

    private CreateProductRequest product(Long sellerId, String title, String price) {
        CreateProductRequest request = new CreateProductRequest();
        request.setTitle(title);
        request.setDescription("卖家上下架控制测试");
        request.setPrice(new BigDecimal(price));
        MediaUploadTicketService tickets = new MediaUploadTicketService(new JdbcTemplate(database));
        String image = tickets.issue(sellerId, "PRODUCT_IMAGE", "image/jpeg", 300_000L, title + ".jpg").storageUrl();
        request.setImageUrls(List.of(image));
        return request;
    }
}
