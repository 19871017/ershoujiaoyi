package com.secondhand.platform.modules.product.application;

import com.secondhand.platform.modules.product.CreateProductResponse;
import com.secondhand.platform.modules.product.ProductDetailResponse;
import com.secondhand.platform.modules.product.ProductListItemResponse;
import com.secondhand.platform.modules.product.UpdateProductResponse;
import com.secondhand.platform.modules.media.application.MediaUploadTicketService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductApplicationService {
    private static final String DEFAULT_TRADE_RULE = "offline-face-to-face-after-platform-order";
    private static final String STATUS_PENDING_AUDIT = "PENDING_AUDIT";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_SOLD = "SOLD";
    private static final String AUDIT_PENDING = "PENDING";
    private static final String AUDIT_APPROVED = "APPROVED";

    private final JdbcTemplate jdbcTemplate;
    private final MediaUploadTicketService mediaUploadTicketService;

    public ProductApplicationService(JdbcTemplate jdbcTemplate, MediaUploadTicketService mediaUploadTicketService) {
        this.jdbcTemplate = jdbcTemplate;
        this.mediaUploadTicketService = mediaUploadTicketService;
    }

    @Transactional
    public CreateProductResponse createProduct(CreateProductRequest request) {
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("product title required");
        }
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("product price must be positive");
        }
        BigDecimal price = money(request.getPrice());
        String title = request.getTitle().trim();
        String productNo = generateNo("GD", title, price, request.getImageUrls());
        jdbcTemplate.update(
                "insert into product_item (product_no,seller_id,title,category,price,product_status,audit_status,visible,trade_rule,description,image_urls,created_at,updated_at) values (?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                productNo,
                1L,
                title,
                "女装",
                price,
                STATUS_PENDING_AUDIT,
                AUDIT_PENDING,
                false,
                DEFAULT_TRADE_RULE,
                safeText(request.getDescription()),
                encodeImageUrls(safeImageUrls(1L, request.getImageUrls()))
        );
        return toCreateResponse(findByProductNo(productNo));
    }

    public List<ProductListItemResponse> listProducts() {
        return jdbcTemplate.query(
                "select id,product_no,title,price,product_status,audit_status,visible,created_at,image_urls from product_item where visible = true and product_status = ? and audit_status = ? order by created_at desc, id desc",
                this::mapListItem,
                STATUS_ACTIVE,
                AUDIT_APPROVED
        );
    }

    public List<ProductListItemResponse> listProductsBySeller(Long sellerId) {
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException("valid sellerId required");
        }
        return jdbcTemplate.query(
                "select id,product_no,title,price,product_status,audit_status,visible,created_at,image_urls from product_item where seller_id = ? and visible = true and product_status = ? and audit_status = ? order by created_at desc, id desc",
                this::mapListItem,
                sellerId,
                STATUS_ACTIVE,
                AUDIT_APPROVED
        );
    }

    public ProductDetailResponse detailProduct(Long productId) {
        ProductRecord product = getVisibleProduct(productId);
        return toDetailResponse(product);
    }

    @Transactional
    public UpdateProductResponse updateProduct(Long sellerId, Long productId, CreateProductRequest request) {
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("product title required");
        }
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("product price must be positive");
        }
        ProductRecord existing = getExistingProduct(productId);
        if (!Objects.equals(existing.sellerId(), sellerId)) {
            throw new IllegalArgumentException("product ownership mismatch");
        }
        if (STATUS_SOLD.equals(existing.status()) || existing.lockedOrderNo() != null) {
            throw new IllegalArgumentException("product cannot be edited after locked or sold");
        }
        List<String> images = safeImageUrls(sellerId, request.getImageUrls());
        int changed = jdbcTemplate.update(
                "update product_item set title = ?, description = ?, price = ?, image_urls = ?, product_status = ?, audit_status = ?, visible = false, updated_at = CURRENT_TIMESTAMP where id = ? and seller_id = ? and product_status <> ? and locked_order_no is null",
                request.getTitle().trim(),
                safeText(request.getDescription()),
                money(request.getPrice()),
                encodeImageUrls(images),
                STATUS_PENDING_AUDIT,
                AUDIT_PENDING,
                productId,
                sellerId,
                STATUS_SOLD
        );
        if (changed == 0) {
            throw new IllegalArgumentException("product update failed");
        }
        return toUpdateResponse(getExistingProduct(productId));
    }

    public ProductSnapshot snapshotForOrder(Long productId) {
        ProductRecord product = getVisibleProduct(productId);
        if (product.lockedOrderNo() != null) {
            throw new IllegalArgumentException("product-already-locked");
        }
        return new ProductSnapshot(product.productId(), product.productNo(), product.title(), product.price(), product.tradeRule());
    }

    @Transactional
    public void reserveForOrder(Long productId, String orderNo) {
        requireOrderNo(orderNo);
        ProductRecord product = getExistingProduct(productId);
        if (!STATUS_ACTIVE.equals(product.status()) || !AUDIT_APPROVED.equals(product.auditState()) || !product.visible()) {
            throw new IllegalArgumentException("product-not-saleable");
        }
        if (product.lockedOrderNo() != null && !Objects.equals(product.lockedOrderNo(), orderNo)) {
            throw new IllegalArgumentException("product-already-locked");
        }
        int changed = jdbcTemplate.update(
                "update product_item set locked_order_no = ?, updated_at = CURRENT_TIMESTAMP where id = ? and (locked_order_no is null or locked_order_no = ?)",
                orderNo,
                productId,
                orderNo
        );
        if (changed == 0) {
            throw new IllegalArgumentException("product-already-locked");
        }
    }

    public void assertSaleableForOrder(Long productId, String orderNo) {
        requireOrderNo(orderNo);
        ProductRecord product = getExistingProduct(productId);
        if (!STATUS_ACTIVE.equals(product.status()) || !AUDIT_APPROVED.equals(product.auditState()) || !product.visible()) {
            throw new IllegalArgumentException("product-not-saleable");
        }
        if (!Objects.equals(product.lockedOrderNo(), orderNo)) {
            throw new IllegalArgumentException("product-order-lock-mismatch");
        }
    }

    @Transactional
    public void markSold(Long productId, String orderNo) {
        requireOrderNo(orderNo);
        ProductRecord product = getExistingProduct(productId);
        if (STATUS_SOLD.equals(product.status())) {
            if (!Objects.equals(product.lockedOrderNo(), orderNo)) {
                throw new IllegalArgumentException("product-order-lock-mismatch");
            }
            return;
        }
        if (!STATUS_ACTIVE.equals(product.status()) || !AUDIT_APPROVED.equals(product.auditState()) || !Objects.equals(product.lockedOrderNo(), orderNo)) {
            throw new IllegalArgumentException("product-order-lock-mismatch");
        }
        int changed = jdbcTemplate.update(
                "update product_item set product_status = ?, visible = false, updated_at = CURRENT_TIMESTAMP where id = ? and locked_order_no = ? and product_status = ?",
                STATUS_SOLD,
                productId,
                orderNo,
                STATUS_ACTIVE
        );
        if (changed == 0) {
            throw new IllegalArgumentException("product-order-lock-mismatch");
        }
    }

    @Transactional
    public void approveForSale(Long productId) {
        ProductRecord product = getExistingProduct(productId);
        if (STATUS_SOLD.equals(product.status())) {
            throw new IllegalArgumentException("product-already-sold");
        }
        jdbcTemplate.update(
                "update product_item set product_status = ?, audit_status = ?, visible = true, updated_at = CURRENT_TIMESTAMP where id = ?",
                STATUS_ACTIVE,
                AUDIT_APPROVED,
                product.productId()
        );
    }

    private ProductRecord getVisibleProduct(Long productId) {
        ProductRecord product = getExistingProduct(productId);
        if (!STATUS_ACTIVE.equals(product.status()) || !AUDIT_APPROVED.equals(product.auditState()) || !product.visible()) {
            throw new IllegalArgumentException("product-not-saleable");
        }
        return product;
    }

    private ProductListItemResponse mapListItem(ResultSet rs, int rowNum) throws SQLException {
        return new ProductListItemResponse(
                rs.getLong("id"),
                rs.getString("product_no"),
                rs.getString("title"),
                rs.getBigDecimal("price"),
                firstImageUrl(decodeImageUrls(rs.getString("image_urls"))),
                rs.getString("product_status"),
                rs.getString("audit_status"),
                rs.getBoolean("visible"),
                timeText(rs.getTimestamp("created_at"))
        );
    }

    private ProductRecord getExistingProduct(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId required");
        }
        ProductRecord product = findById(productId);
        if (product == null) {
            throw new IllegalArgumentException("product-not-found");
        }
        return product;
    }

    private ProductRecord findByProductNo(String productNo) {
        try {
            return jdbcTemplate.queryForObject(
                    "select * from product_item where product_no = ?",
                    (rs, rowNum) -> mapProduct(rs.getLong("id"), rs.getString("product_no"), rs.getString("title"), rs.getString("description"), rs.getBigDecimal("price"), rs.getString("image_urls"), rs.getString("product_status"), rs.getString("audit_status"), rs.getBoolean("visible"), rs.getString("trade_rule"), rs.getString("locked_order_no"), rs.getTimestamp("created_at")),
                    productNo
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalStateException("product create failed");
        }
    }

    private ProductRecord findById(Long productId) {
        try {
            return jdbcTemplate.queryForObject(
                    "select * from product_item where id = ?",
                    (rs, rowNum) -> mapProduct(rs.getLong("id"), rs.getString("product_no"), rs.getString("title"), rs.getString("description"), rs.getBigDecimal("price"), rs.getString("image_urls"), rs.getString("product_status"), rs.getString("audit_status"), rs.getBoolean("visible"), rs.getString("trade_rule"), rs.getString("locked_order_no"), rs.getTimestamp("created_at")),
                    productId
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private ProductRecord mapProduct(Long productId, String productNo, String title, String description, BigDecimal price,
                                     String imageUrls, String status, String auditState, Boolean visible,
                                     String tradeRule, String lockedOrderNo, Timestamp createdAt) {
        Long sellerId = jdbcTemplate.queryForObject("select seller_id from product_item where id = ?", Long.class, productId);
        return new ProductRecord(productId, productNo, sellerId, title, description, price, decodeImageUrls(imageUrls), status, auditState,
                Boolean.TRUE.equals(visible), tradeRule == null ? DEFAULT_TRADE_RULE : tradeRule, lockedOrderNo, timeText(createdAt));
    }

    private CreateProductResponse toCreateResponse(ProductRecord product) {
        return new CreateProductResponse(product.productId(), product.productNo(), product.title(), product.price(), product.status(), product.auditState(), product.visible(), product.tradeRule(), product.createdAt());
    }

    private UpdateProductResponse toUpdateResponse(ProductRecord product) {
        return new UpdateProductResponse(product.productId(), product.productNo(), product.title(), product.description(), product.price(), product.imageUrls(), product.status(), product.auditState(), product.visible(), product.tradeRule());
    }

    private ProductDetailResponse toDetailResponse(ProductRecord product) {
        return new ProductDetailResponse(product.productId(), product.productNo(), product.title(), product.description(), product.price(), product.imageUrls(), product.status(), product.auditState(), product.visible(), product.tradeRule(), product.createdAt());
    }

    private void requireOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("orderNo required");
        }
    }

    private List<String> safeImageUrls(Long sellerId, List<String> imageUrls) {
        if (imageUrls == null) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>(imageUrls.stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isBlank()).toList());
        if (cleaned.size() > 9) {
            throw new IllegalArgumentException("product image count invalid");
        }
        for (String imageUrl : cleaned) {
            if (imageUrl.startsWith("local://") || imageUrl.toLowerCase(Locale.ROOT).contains("placeholder") || imageUrl.toLowerCase(Locale.ROOT).contains("preview")) {
                throw new IllegalArgumentException("product image url invalid");
            }
            mediaUploadTicketService.requireIssuedStorageUrl(sellerId, "PRODUCT_IMAGE", imageUrl);
        }
        return cleaned;
    }

    private String encodeImageUrls(List<String> imageUrls) {
        return String.join("\n", imageUrls);
    }

    private List<String> decodeImageUrls(String imageUrls) {
        if (imageUrls == null || imageUrls.isBlank()) {
            return List.of();
        }
        return imageUrls.lines().filter(line -> !line.isBlank()).toList();
    }

    private String firstImageUrl(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return null;
        }
        return imageUrls.get(0);
    }

    private String timeText(Timestamp timestamp) {
        LocalDateTime time = timestamp == null ? LocalDateTime.now() : timestamp.toLocalDateTime();
        return time.toString();
    }

    private String generateNo(String prefix, Object... values) {
        String seed = prefix + ':' + Objects.toString(List.of(values), "") + ':' + System.nanoTime();
        return prefix + '-' + sha256(seed).substring(0, 24).toUpperCase(Locale.ROOT);
    }

    private BigDecimal money(BigDecimal amount) {
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("product price scale must be <= 2", ex);
        }
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(Objects.toString(value, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(encoded.length * 2);
            for (byte b : encoded) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private record ProductRecord(Long productId, String productNo, Long sellerId, String title, String description, BigDecimal price,
                                 List<String> imageUrls, String status, String auditState, Boolean visible,
                                 String tradeRule, String lockedOrderNo, String createdAt) {
    }
}
