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
    private JdbcTemplate jdbcTemplate;
    private ProductApplicationService service;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        service = new ProductApplicationService(jdbcTemplate, new com.secondhand.platform.modules.media.application.MediaUploadTicketService(jdbcTemplate));
        upsertProfile(1L, "SELLER", "APPROVED", true);
        upsertProfile(7L, "SELLER", "APPROVED", true);
    }

    @Test
    void createdProductShouldUseServerDerivedSellerAndAppearInSellerManagementList() {
        CreateProductResponse response = service.createProduct(7L, product(7L, "奶油色针织开衫", "79.00"));

        assertEquals("PENDING_AUDIT", response.getStatus());
        assertEquals("PENDING", response.getAuditState());
        assertProductAudit(response.getProductId(), 7L, "PENDING", "商品发布待审核", "奶油色针织开衫");
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
        assertEquals(1L, detail.getSellerId());
        assertEquals("ACTIVE", detail.getStatus());
        assertEquals("APPROVED", detail.getAuditState());
        assertTrue(detail.getVisible());
        assertEquals("粉色低跟鞋", snapshot.getTitle());
        assertEquals(1, reloaded.listProducts().size());
    }

    @Test
    void publicProductPriceShouldApplyDefaultBuyerMarkupWhileKeepingSellerPrice() {
        CreateProductResponse response = service.createProduct(1L, product("默认加价商品", "100.00"));
        service.approveForSale(response.getProductId());

        ProductDetailResponse detail = service.detailProduct(response.getProductId());
        ProductSnapshot snapshot = service.snapshotForOrder(response.getProductId());
        var publicRows = service.listProducts();
        var mineRows = service.listMyProducts(1L);

        assertEquals(new BigDecimal("100.00"), response.getPrice());
        assertEquals(new BigDecimal("130.00"), detail.getPrice());
        assertEquals(new BigDecimal("100.00"), detail.getSellerPrice());
        assertEquals(new BigDecimal("0.3000"), detail.getPlatformMarkupRate());
        assertEquals(new BigDecimal("30.00"), detail.getPlatformMarkupAmount());
        assertEquals(new BigDecimal("130.00"), snapshot.getBuyerPrice());
        assertEquals(new BigDecimal("100.00"), snapshot.getSellerPrice());
        assertEquals(new BigDecimal("30.00"), snapshot.getPlatformMarkupAmount());
        assertEquals(new BigDecimal("130.00"), publicRows.get(0).getPrice());
        assertEquals(new BigDecimal("100.00"), publicRows.get(0).getSellerPrice());
        assertEquals(new BigDecimal("130.00"), mineRows.get(0).getPrice());
        assertEquals(new BigDecimal("100.00"), mineRows.get(0).getSellerPrice());
    }

    @Test
    void adminProductMarkupConfigShouldPersistAndRejectUnsafeRates() {
        var updated = service.adminUpdatePricingConfig(new AdminProductPricingConfigRequest(new BigDecimal("0.25")));

        assertEquals(new BigDecimal("0.2500"), updated.markupRate());
        assertEquals(new BigDecimal("125.00"), service.applyBuyerPrice(new BigDecimal("100.00")));

        ProductApplicationService reloaded = new ProductApplicationService(new JdbcTemplate(database), new com.secondhand.platform.modules.media.application.MediaUploadTicketService(new JdbcTemplate(database)));
        assertEquals(new BigDecimal("0.2500"), reloaded.adminPricingConfig().markupRate());
        assertEquals(new BigDecimal("125.00"), reloaded.applyBuyerPrice(new BigDecimal("100.00")));
        assertThrows(IllegalArgumentException.class, () -> reloaded.adminUpdatePricingConfig(new AdminProductPricingConfigRequest(new BigDecimal("-0.01"))));
        assertThrows(IllegalArgumentException.class, () -> reloaded.adminUpdatePricingConfig(new AdminProductPricingConfigRequest(new BigDecimal("5.01"))));
        assertThrows(IllegalArgumentException.class, () -> reloaded.adminUpdatePricingConfig(new AdminProductPricingConfigRequest(new BigDecimal("0.12345"))));
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
        assertProductAudit(response.getProductId(), 1L, "PENDING", "商品编辑后重新审核", "改后商品标题");
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
    void buyerCannotCreateProductUntilSellerCertificationApproved() {
        upsertProfile(21L, "BUYER", "UNVERIFIED", false);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.createProduct(21L, product(21L, "未认证发布商品", "79.00")));

        assertEquals("seller certification required", error.getMessage());
    }

    @Test
    void productImagesShouldRequireUploadedProductImageTickets() {
        CreateProductRequest unsafe = product("带图测试", "99.00");
        unsafe.setImageUrls(List.of("https://img.example.com/unissued.jpg"));
        assertThrows(IllegalArgumentException.class, () -> service.createProduct(1L, unsafe));

        String uploaded = uploadedProductImage(1L, "dress.jpg", 300_000L);
        CreateProductRequest request = product("带图测试", "99.00");
        request.setImageUrls(List.of(uploaded));
        CreateProductResponse response = service.createProduct(1L, request);
        service.approveForSale(response.getProductId());

        ProductDetailResponse detail = service.detailProduct(response.getProductId());
        assertEquals(List.of(uploaded), detail.getImageUrls());
    }

    @Test
    void publicSellerProductsShouldReturnOnlyVisibleApprovedProductsOwnedBySeller() {
        upsertProfile(2L, "SELLER", "APPROVED", true);
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
        assertTrue(reloaded.detailProduct(visible.getProductId(), 8L).isFavoritedByMe());
        assertFalse(reloaded.detailProduct(visible.getProductId(), 9L).isFavoritedByMe());
        assertFalse(reloaded.detailProduct(visible.getProductId()).isFavoritedByMe());
        assertTrue(reloaded.listFavorites(9L).isEmpty());

        reloaded.unfavoriteProduct(8L, visible.getProductId());
        reloaded.unfavoriteProduct(8L, visible.getProductId());
        assertTrue(reloaded.listFavorites(8L).isEmpty());
    }

    @Test
    void sellerCanToggleApprovedProductVisibilityWithoutLocalOnlyStatusMutation() {
        CreateProductResponse response = service.createProduct(1L, product("可上下架商品", "88.00"));
        service.approveForSale(response.getProductId());

        var offline = service.updateVisibility(1L, response.getProductId(), false);

        assertEquals("OFFLINE", offline.getStatus());
        assertEquals("APPROVED", offline.getAuditState());
        assertFalse(offline.getVisible());
        assertThrows(IllegalArgumentException.class, () -> service.detailProduct(response.getProductId()));
        assertTrue(service.listProducts().isEmpty());
        assertEquals("OFFLINE", service.listMyProducts(1L).get(0).getStatus());

        var online = service.updateVisibility(1L, response.getProductId(), true);

        assertEquals("ACTIVE", online.getStatus());
        assertTrue(online.getVisible());
        assertEquals(1, service.listProducts().size());
        assertThrows(IllegalArgumentException.class, () -> service.updateVisibility(2L, response.getProductId(), false));
    }

    @Test
    void adminOfflineProductShouldHideActiveProductAndRequireFreshReviewBeforeOnlineAgain() {
        CreateProductResponse response = service.createProduct(1L, product("运营下架商品", "88.00"));
        service.approveForSale(response.getProductId());

        var offline = service.adminOfflineProduct(response.getProductId(), "违规内容运营下架");

        assertEquals("OFFLINE", offline.getStatus());
        assertEquals("REJECTED", offline.getAuditState());
        assertFalse(offline.getVisible());
        assertTrue(service.listProducts().isEmpty());
        assertEquals("OFFLINE", service.listMyProducts(1L).get(0).getStatus());
        assertThrows(IllegalArgumentException.class, () -> service.detailProduct(response.getProductId()));
        IllegalArgumentException restoreError = assertThrows(IllegalArgumentException.class, () -> service.updateVisibility(1L, response.getProductId(), true));
        assertEquals("product not approved", restoreError.getMessage());
    }

    @Test
    void adminOfflineProductShouldRejectLockedInactiveAndUnsafeReasons() {
        CreateProductResponse active = service.createProduct(1L, product("运营下架锁定商品", "88.00"));
        service.approveForSale(active.getProductId());
        service.reserveForOrder(active.getProductId(), "OD-ADMIN-OFFLINE");

        assertThrows(IllegalArgumentException.class, () -> service.adminOfflineProduct(active.getProductId(), "订单锁定后不可运营下架"));

        CreateProductResponse pending = service.createProduct(1L, product("待审不可运营下架", "66.00"));
        assertThrows(IllegalArgumentException.class, () -> service.adminOfflineProduct(pending.getProductId(), "待审商品不允许运营下架"));
        assertThrows(IllegalArgumentException.class, () -> service.adminOfflineProduct(active.getProductId(), "preview offline"));
    }

    @Test
    void approveForSaleShouldRejectProductWhenSellerCertificationWasRevoked() {
        CreateProductResponse response = service.createProduct(1L, product("认证后撤销商品", "88.00"));
        upsertProfile(1L, "BUYER", "REJECTED", false);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.approveForSale(response.getProductId()));

        assertEquals("seller certification required", error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.detailProduct(response.getProductId()));
    }

    @Test
    void approvedSellerWithoutUploadedVideoIdentityTicketCannotPublishOrExposeProducts() {
        upsertProfile(31L, "SELLER", "APPROVED", true);
        jdbcTemplate.update("DELETE FROM media_upload_ticket WHERE owner_user_id = ? AND scene = 'VIDEO_IDENTITY'", 31L);

        IllegalArgumentException createError = assertThrows(IllegalArgumentException.class, () -> service.createProduct(31L, product(31L, "脏认证卖家商品", "88.00")));

        assertEquals("seller certification required", createError.getMessage());

        CreateProductResponse product = service.createProduct(1L, product("认证后票据丢失商品", "99.00"));
        service.approveForSale(product.getProductId());
        jdbcTemplate.update("DELETE FROM media_upload_ticket WHERE owner_user_id = ? AND scene = 'VIDEO_IDENTITY'", 1L);

        assertTrue(service.listProducts().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.detailProduct(product.getProductId()));
    }

    @Test
    void approvedSellerWithoutMatchingVideoIdentityAuditCannotPublishOrExposeProducts() {
        upsertProfile(32L, "SELLER", "APPROVED", true);
        jdbcTemplate.update("DELETE FROM audit_record WHERE user_id = ? AND audit_type = 'VIDEO_IDENTITY'", 32L);

        IllegalArgumentException createError = assertThrows(IllegalArgumentException.class, () -> service.createProduct(32L, product(32L, "无审核记录认证商品", "88.00")));

        assertEquals("seller certification required", createError.getMessage());

        CreateProductResponse product = service.createProduct(1L, product("认证后审核记录丢失商品", "99.00"));
        service.approveForSale(product.getProductId());
        jdbcTemplate.update("DELETE FROM audit_record WHERE user_id = ? AND audit_type = 'VIDEO_IDENTITY'", 1L);

        assertTrue(service.listProducts().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.detailProduct(product.getProductId()));
    }

    @Test
    void adminAndMineShouldNotExposeRawVideoVerifiedWhenStatusIsNotApproved() {
        upsertProfile(41L, "SELLER", "REJECTED", true);
        CreateProductResponse product = service.createProduct(1L, product("脏状态认证字段商品", "99.00"));
        jdbcTemplate.update("UPDATE product_item SET seller_id = ? WHERE id = ?", 41L, product.getProductId());

        var adminRows = service.adminListProducts(null, null, "脏状态认证字段商品", 20);
        var mineRows = service.listMyProducts(41L);

        assertEquals(1, adminRows.size());
        assertEquals(1, mineRows.size());
        assertFalse(adminRows.get(0).getSellerVideoVerified());
        assertFalse(mineRows.get(0).getSellerVideoVerified());
    }

    @Test
    void adminAndMineShouldNotExposeRawVideoVerifiedWithoutUploadedTicketOrAudit() {
        upsertProfile(42L, "SELLER", "APPROVED", true);
        jdbcTemplate.update("DELETE FROM media_upload_ticket WHERE owner_user_id = ? AND scene = 'VIDEO_IDENTITY'", 42L);
        CreateProductResponse missingTicket = service.createProduct(1L, product("缺票据认证字段商品", "99.00"));
        jdbcTemplate.update("UPDATE product_item SET seller_id = ? WHERE id = ?", 42L, missingTicket.getProductId());

        upsertProfile(43L, "SELLER", "APPROVED", true);
        jdbcTemplate.update("DELETE FROM audit_record WHERE user_id = ? AND audit_type = 'VIDEO_IDENTITY'", 43L);
        CreateProductResponse missingAudit = service.createProduct(1L, product("缺审核认证字段商品", "98.00"));
        jdbcTemplate.update("UPDATE product_item SET seller_id = ? WHERE id = ?", 43L, missingAudit.getProductId());

        assertFalse(service.adminListProducts(null, null, "缺票据认证字段商品", 20).get(0).getSellerVideoVerified());
        assertFalse(service.listMyProducts(42L).get(0).getSellerVideoVerified());
        assertFalse(service.adminListProducts(null, null, "缺审核认证字段商品", 20).get(0).getSellerVideoVerified());
        assertFalse(service.listMyProducts(43L).get(0).getSellerVideoVerified());
    }

    @Test
    void revokedSellerProductsShouldNotStayPublicOrSaleable() {
        CreateProductResponse response = service.createProduct(1L, product("撤销后隐藏商品", "88.00"));
        service.approveForSale(response.getProductId());
        service.favoriteProduct(8L, response.getProductId());
        assertEquals(1, service.listProducts().size());
        assertEquals(1, service.listProductsBySeller(1L).size());
        assertEquals(1, service.listFavorites(8L).size());

        upsertProfile(1L, "BUYER", "REJECTED", false);

        assertTrue(service.listProducts().isEmpty());
        assertTrue(service.listProductsBySeller(1L).isEmpty());
        assertTrue(service.listFavorites(8L).isEmpty());
        IllegalArgumentException detailError = assertThrows(IllegalArgumentException.class, () -> service.detailProduct(response.getProductId()));
        assertEquals("seller certification required", detailError.getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.snapshotForOrder(response.getProductId()));
        assertThrows(IllegalArgumentException.class, () -> service.favoriteProduct(8L, response.getProductId()));
    }

    @Test
    void revokedSellerCannotBringApprovedProductBackOnline() {
        CreateProductResponse response = service.createProduct(1L, product("撤销后不可上线", "88.00"));
        service.approveForSale(response.getProductId());
        service.updateVisibility(1L, response.getProductId(), false);
        upsertProfile(1L, "BUYER", "REJECTED", false);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.updateVisibility(1L, response.getProductId(), true));

        assertEquals("seller certification required", error.getMessage());
        assertTrue(service.listProducts().isEmpty());
    }

    @Test
    void visibilityToggleRejectsPendingLockedAndSoldProducts() {
        CreateProductResponse pending = service.createProduct(1L, product("待审不可上架", "88.00"));
        assertThrows(IllegalArgumentException.class, () -> service.updateVisibility(1L, pending.getProductId(), true));

        CreateProductResponse locked = service.createProduct(1L, product("锁定不可下架", "99.00"));
        service.approveForSale(locked.getProductId());
        service.reserveForOrder(locked.getProductId(), "OD-LOCKED-VISIBILITY");
        assertThrows(IllegalArgumentException.class, () -> service.updateVisibility(1L, locked.getProductId(), false));

        service.markSold(locked.getProductId(), "OD-LOCKED-VISIBILITY");
        assertThrows(IllegalArgumentException.class, () -> service.updateVisibility(1L, locked.getProductId(), true));
    }

    private JdbcTemplate jdbcTemplate() {
        return jdbcTemplate;
    }

    private void assertProductAudit(Long productId, Long sellerId, String status, String reason, String description) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(1)
                from audit_record
                where audit_type = 'PRODUCT'
                  and target_type = 'PRODUCT'
                  and target_id = ?
                  and user_id = ?
                  and status = ?
                  and reason = ?
                  and description = ?
                """, Integer.class, String.valueOf(productId), sellerId, status, reason, description);
        assertEquals(1, count);
    }

    private void upsertProfile(long userId, String role, String videoStatus, boolean videoVerified) {
        Integer accountRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account WHERE id = ?", Integer.class, userId);
        if (accountRows == null || accountRows == 0) {
            jdbcTemplate.update("INSERT INTO user_account (id, user_no, phone, password_hash, nickname, status) VALUES (?, ?, ?, ?, ?, ?)", userId, "U-PRODUCT-" + userId, "1380013" + String.format("%04d", userId), "hash", "商品用户" + userId, "ACTIVE");
        }
        Integer profileRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_profile WHERE user_id = ?", Integer.class, userId);
        if (profileRows == null || profileRows == 0) {
            jdbcTemplate.update("INSERT INTO user_profile (user_id, identity_status, main_role, video_identity_status, video_verified) VALUES (?, ?, ?, ?, ?)", userId, "VERIFIED", role, videoStatus, videoVerified);
        } else {
            jdbcTemplate.update("UPDATE user_profile SET identity_status = ?, main_role = ?, video_identity_status = ?, video_verified = ? WHERE user_id = ?", "VERIFIED", role, videoStatus, videoVerified, userId);
        }
        if (videoVerified && "APPROVED".equals(videoStatus) && List.of("SELLER", "BOTH").contains(role)) {
            uploadedVideoIdentity(userId);
        }
    }

    private CreateProductRequest product(String title, String price) {
        return product(1L, title, price);
    }

    private CreateProductRequest product(Long sellerId, String title, String price) {
        CreateProductRequest request = new CreateProductRequest();
        request.setTitle(title);
        request.setDescription("女生闲置测试商品");
        request.setPrice(new BigDecimal(price));
        String image1 = uploadedProductImage(sellerId, title + "-1.jpg", 300_000L);
        String image2 = uploadedProductImage(sellerId, title + "-2.jpg", 320_000L);
        request.setImageUrls(List.of(image1, image2));
        return request;
    }

    private String uploadedProductImage(Long ownerUserId, String filename, Long fileSize) {
        String storageUrl = new com.secondhand.platform.modules.media.application.MediaUploadTicketService(new JdbcTemplate(database))
                .issue(ownerUserId, "PRODUCT_IMAGE", "image/jpeg", fileSize, filename)
                .storageUrl();
        jdbcTemplate.update("UPDATE media_upload_ticket SET status = 'UPLOADED' WHERE owner_user_id = ? AND storage_url = ?", ownerUserId, storageUrl);
        return storageUrl;
    }

    private String uploadedVideoIdentity(Long ownerUserId) {
        String storageUrl = new com.secondhand.platform.modules.media.application.MediaUploadTicketService(new JdbcTemplate(database))
                .issue(ownerUserId, "VIDEO_IDENTITY", "video/mp4", 1_000_000L, "identity-" + ownerUserId + ".mp4")
                .storageUrl();
        jdbcTemplate.update("UPDATE media_upload_ticket SET status = 'UPLOADED' WHERE owner_user_id = ? AND storage_url = ?", ownerUserId, storageUrl);
        jdbcTemplate.update("""
                INSERT INTO audit_record (audit_no, audit_type, user_id, target_type, target_id, reason, description, status, created_at, reviewed_at)
                VALUES (?, 'VIDEO_IDENTITY', ?, 'VIDEO_IDENTITY', ?, ?, '商品发布认证视频', 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "AUDIT-PRODUCT-VIDEO-" + ownerUserId, ownerUserId, String.valueOf(ownerUserId), storageUrl);
        return storageUrl;
    }
}
