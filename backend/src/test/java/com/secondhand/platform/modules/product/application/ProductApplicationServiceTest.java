package com.secondhand.platform.modules.product.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.secondhand.platform.modules.product.CreateProductResponse;
import com.secondhand.platform.modules.product.ProductDetailResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class ProductApplicationServiceTest {
    private EmbeddedDatabase database;
    private ProductApplicationService service;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        service = new ProductApplicationService(new JdbcTemplate(database), new com.secondhand.platform.modules.media.application.MediaUploadTicketService(new JdbcTemplate(database)));
    }

    @Test
    void createdProductShouldUseServerDerivedSellerAndAppearInSellerManagementList() {
        CreateProductResponse response = service.createProduct(7L, product(7L, "奶油色针织开衫", "79.00"));

        assertEquals("PENDING_AUDIT", response.getStatus());
        assertEquals("PENDING", response.getAuditState());
        assertFalse(response.getVisible());
        assertThrows(IllegalArgumentException.class, () -> service.detailProduct(response.getProductId()));
        assertTrue(service.listProducts().isEmpty());
        var mine = service.listMyProducts(7L);
        assertEquals(1, mine.size());
        assertEquals(response.getProductId(), mine.get(0).getProductId());
        assertEquals("奶油色针织开衫", mine.get(0).getTitle());
        assertTrue(service.listMyProducts(8L).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.listMyProducts(0L));
    }

    @Test
    void approvedProductShouldSurviveServiceRecreationAndBeSaleable() {
        CreateProductResponse response = service.createProduct(1L, product("粉色低跟鞋", "129.00"));
        service.approveForSale(response.getProductId());

        ProductApplicationService reloaded = new ProductApplicationService(new JdbcTemplate(database), new com.secondhand.platform.modules.media.application.MediaUploadTicketService(new JdbcTemplate(database)));
        ProductDetailResponse detail = reloaded.detailProduct(response.getProductId());
        ProductSnapshot snapshot = reloaded.snapshotForOrder(response.getProductId());

        assertEquals(response.getProductNo(), detail.getProductNo());
        assertEquals("ACTIVE", detail.getStatus());
        assertEquals("APPROVED", detail.getAuditState());
        assertTrue(detail.getVisible());
        assertEquals("粉色低跟鞋", snapshot.getTitle());
        assertEquals(1, reloaded.listProducts().size());
    }

    @Test
    void reserveAndSoldStateShouldBePersisted() {
        CreateProductResponse response = service.createProduct(1L, product("蝴蝶结小包", "56.00"));
        service.approveForSale(response.getProductId());

        service.reserveForOrder(response.getProductId(), "OD-1");
        assertThrows(IllegalArgumentException.class, () -> service.reserveForOrder(response.getProductId(), "OD-2"));

        ProductApplicationService reloaded = new ProductApplicationService(new JdbcTemplate(database), new com.secondhand.platform.modules.media.application.MediaUploadTicketService(new JdbcTemplate(database)));
        reloaded.assertSaleableForOrder(response.getProductId(), "OD-1");
        reloaded.markSold(response.getProductId(), "OD-1");

        assertThrows(IllegalArgumentException.class, () -> reloaded.snapshotForOrder(response.getProductId()));
    }

    @Test
    void sellerCanUpdateEditableProductAndResetAuditState() {
        CreateProductResponse response = service.createProduct(1L, product("待改商品", "88.00"));
        service.approveForSale(response.getProductId());

        CreateProductRequest update = product("改后商品标题", "66.00");
        var updated = service.updateProduct(1L, response.getProductId(), update);

        assertEquals("改后商品标题", updated.getTitle());
        assertEquals("PENDING_AUDIT", updated.getStatus());
        assertEquals("PENDING", updated.getAuditState());
        assertFalse(updated.getVisible());
        assertThrows(IllegalArgumentException.class, () -> service.detailProduct(response.getProductId()));
    }

    @Test
    void updateShouldRejectNonOwnerLockedAndUnissuedImages() {
        CreateProductResponse response = service.createProduct(1L, product("编辑安全商品", "88.00"));
        assertThrows(IllegalArgumentException.class, () -> service.updateProduct(2L, response.getProductId(), product("越权修改", "77.00")));

        CreateProductRequest unsafe = product("非法图片", "77.00");
        unsafe.setImageUrls(List.of("https://img.example.com/unissued.jpg"));
        assertThrows(IllegalArgumentException.class, () -> service.updateProduct(1L, response.getProductId(), unsafe));

        service.approveForSale(response.getProductId());
        service.reserveForOrder(response.getProductId(), "OD-LOCKED");
        assertThrows(IllegalArgumentException.class, () -> service.updateProduct(1L, response.getProductId(), product("锁定后修改", "77.00")));
    }

    @Test
    void productImagesShouldRequireIssuedProductImageTickets() {
        CreateProductRequest unsafe = product("带图测试", "99.00");
        unsafe.setImageUrls(List.of("https://img.example.com/unissued.jpg"));
        assertThrows(IllegalArgumentException.class, () -> service.createProduct(1L, unsafe));

        String issued = new com.secondhand.platform.modules.media.application.MediaUploadTicketService(new JdbcTemplate(database))
                .issue(1L, "PRODUCT_IMAGE", "image/jpeg", 300_000L, "dress.jpg")
                .storageUrl();
        CreateProductRequest request = product("带图测试", "99.00");
        request.setImageUrls(List.of(issued));
        CreateProductResponse response = service.createProduct(1L, request);
        service.approveForSale(response.getProductId());

        ProductDetailResponse detail = service.detailProduct(response.getProductId());
        assertEquals(List.of(issued), detail.getImageUrls());
    }

    @Test
    void publicSellerProductsShouldReturnOnlyVisibleApprovedProductsOwnedBySeller() {
        CreateProductResponse sellerProduct = service.createProduct(1L, product("卖家公开商品", "109.00"));
        service.approveForSale(sellerProduct.getProductId());
        CreateProductResponse hiddenPendingProduct = service.createProduct(1L, product("卖家待审商品", "89.00"));
        jdbcTemplate().update("UPDATE product_item SET seller_id = ? WHERE id = ?", 2L, hiddenPendingProduct.getProductId());
        CreateProductResponse otherSellerProduct = service.createProduct(1L, product("其他卖家商品", "99.00"));
        jdbcTemplate().update("UPDATE product_item SET seller_id = ? WHERE id = ?", 2L, otherSellerProduct.getProductId());
        service.approveForSale(otherSellerProduct.getProductId());

        var sellerProducts = service.listProductsBySeller(1L);
        var otherSellerProducts = service.listProductsBySeller(2L);

        assertEquals(1, sellerProducts.size());
        assertEquals(sellerProduct.getProductId(), sellerProducts.get(0).getProductId());
        assertEquals("卖家公开商品", sellerProducts.get(0).getTitle());
        assertEquals(1, otherSellerProducts.size());
        assertEquals(otherSellerProduct.getProductId(), otherSellerProducts.get(0).getProductId());
        assertThrows(IllegalArgumentException.class, () -> service.listProductsBySeller(0L));
    }

    @Test
    void userFavoritesShouldPersistOnlyVisibleApprovedProductsAndRemoveIdempotently() {
        CreateProductResponse visible = service.createProduct(1L, product("可收藏商品", "108.00"));
        service.approveForSale(visible.getProductId());
        CreateProductResponse pending = service.createProduct(1L, product("待审不可收藏", "78.00"));

        service.favoriteProduct(8L, visible.getProductId());
        service.favoriteProduct(8L, visible.getProductId());
        assertThrows(IllegalArgumentException.class, () -> service.favoriteProduct(8L, pending.getProductId()));
        assertThrows(IllegalArgumentException.class, () -> service.favoriteProduct(0L, visible.getProductId()));

        ProductApplicationService reloaded = new ProductApplicationService(new JdbcTemplate(database), new com.secondhand.platform.modules.media.application.MediaUploadTicketService(new JdbcTemplate(database)));
        var favorites = reloaded.listFavorites(8L);
        assertEquals(1, favorites.size());
        assertEquals(visible.getProductId(), favorites.get(0).getProductId());
        assertEquals("可收藏商品", favorites.get(0).getTitle());
        assertTrue(reloaded.listFavorites(9L).isEmpty());

        reloaded.unfavoriteProduct(8L, visible.getProductId());
        reloaded.unfavoriteProduct(8L, visible.getProductId());
        assertTrue(reloaded.listFavorites(8L).isEmpty());
    }

    private JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(database);
    }

    private CreateProductRequest product(String title, String price) {
        return product(1L, title, price);
    }

    private CreateProductRequest product(Long sellerId, String title, String price) {
        CreateProductRequest request = new CreateProductRequest();
        request.setTitle(title);
        request.setDescription("女生闲置测试商品");
        request.setPrice(new BigDecimal(price));
        String image1 = new com.secondhand.platform.modules.media.application.MediaUploadTicketService(new JdbcTemplate(database))
                .issue(sellerId, "PRODUCT_IMAGE", "image/jpeg", 300_000L, title + "-1.jpg")
                .storageUrl();
        String image2 = new com.secondhand.platform.modules.media.application.MediaUploadTicketService(new JdbcTemplate(database))
                .issue(sellerId, "PRODUCT_IMAGE", "image/jpeg", 320_000L, title + "-2.jpg")
                .storageUrl();
        request.setImageUrls(List.of(image1, image2));
        return request;
    }
}
