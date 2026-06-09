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
    private static final String STATUS_OFFLINE = "OFFLINE";
    private static final String STATUS_SOLD = "SOLD";
    private static final String AUDIT_TYPE_PRODUCT = "PRODUCT";
    private static final String AUDIT_PENDING = "PENDING";
    private static final String AUDIT_APPROVED = "APPROVED";
    private static final String AUDIT_REJECTED = "REJECTED";
    private static final String CERTIFIED_PRODUCT_SELLER_FILTER = """
            and exists (
                select 1
                from user_account a
                join user_profile up on up.user_id = a.id
                where a.id = p.seller_id
                  and a.status = 'ACTIVE'
                  and UPPER(COALESCE(up.main_role, 'BUYER')) IN ('SELLER', 'BOTH')
                  and up.video_identity_status = 'APPROVED'
                  and up.video_verified = TRUE
                  and exists (
                      select 1
                      from media_upload_ticket t
                      where t.owner_user_id = up.user_id
	                        and t.scene = 'VIDEO_IDENTITY'
	                        and t.status = 'UPLOADED'
	                        and t.storage_url like '/uploads/video-identity/%'
	                        and exists (
	                            select 1
	                            from audit_record ar
	                            where ar.audit_type = 'VIDEO_IDENTITY'
	                              and ar.user_id = up.user_id
	                              and ar.target_id = concat('', up.user_id)
	                              and ar.status = 'APPROVED'
	                              and ar.reason = t.storage_url
	                        )
	                  )
	            )
            """;
    private static final String CERTIFIED_SELLER_VERIFIED_SELECT = """
            CASE WHEN ua.status = 'ACTIVE'
                  AND UPPER(COALESCE(up.main_role, 'BUYER')) IN ('SELLER', 'BOTH')
                  AND up.video_identity_status = 'APPROVED'
                  AND up.video_verified = TRUE
                  AND EXISTS (
                      SELECT 1
                      FROM media_upload_ticket t
                      WHERE t.owner_user_id = up.user_id
                        AND t.scene = 'VIDEO_IDENTITY'
                        AND t.status = 'UPLOADED'
                        AND t.storage_url LIKE '/uploads/video-identity/%'
                        AND EXISTS (
                            SELECT 1
                            FROM audit_record ar
                            WHERE ar.audit_type = 'VIDEO_IDENTITY'
                              AND ar.user_id = up.user_id
                              AND ar.target_id = CONCAT('', up.user_id)
                              AND ar.status = 'APPROVED'
                              AND ar.reason = t.storage_url
                        )
                  )
            THEN TRUE ELSE FALSE END AS seller_video_verified
            """;
    private static final String PRODUCT_LIST_SELECT_PREFIX = """
            select p.id,p.product_no,p.seller_id,ua.nickname as seller_nickname,ua.avatar_url as seller_avatar_url,up.gender as seller_gender,p.title,p.category,up.city as seller_city,
            """;
    private static final String PRODUCT_LIST_SELECT_SUFFIX = """
            ,p.price,p.product_status,p.audit_status,p.visible,p.created_at,p.image_urls
            from product_item p
            join user_account ua on ua.id = p.seller_id
            left join user_profile up on up.user_id = p.seller_id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final MediaUploadTicketService mediaUploadTicketService;

    public ProductApplicationService(JdbcTemplate jdbcTemplate, MediaUploadTicketService mediaUploadTicketService) {
        this.jdbcTemplate = jdbcTemplate;
        this.mediaUploadTicketService = mediaUploadTicketService;
    }

    @Transactional
    public CreateProductResponse createProduct(Long sellerId, CreateProductRequest request) {
        requirePositiveId(sellerId, "valid sellerId required");
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("product title required");
        }
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("product price must be positive");
        }
        requireCertifiedSeller(sellerId);
        BigDecimal price = money(request.getPrice());
        String title = request.getTitle().trim();
        String productNo = generateNo("GD", title, price, request.getImageUrls() == null ? List.of() : request.getImageUrls());
        jdbcTemplate.update(
                "insert into product_item (product_no,seller_id,title,category,price,product_status,audit_status,visible,trade_rule,description,image_urls,created_at,updated_at) values (?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                productNo,
                sellerId,
                title,
                "女装",
                price,
                STATUS_PENDING_AUDIT,
                AUDIT_PENDING,
                false,
                DEFAULT_TRADE_RULE,
                safeText(request.getDescription()),
                encodeImageUrls(safeImageUrls(sellerId, request.getImageUrls()))
        );
        ProductRecord product = findByProductNo(productNo);
        ensurePendingProductAudit(product, "商品发布待审核");
        return toCreateResponse(product);
    }

    public List<ProductListItemResponse> adminListProducts(String status, String auditStatus, String keyword, Integer limit) {
        String safeStatus = normalizeAdminProductStatus(status);
        String safeAuditStatus = normalizeAdminAuditStatus(auditStatus);
        String safeKeyword = normalizeAdminSearchKeyword(keyword);
        int safeLimit = normalizeAdminLimit(limit, 20, 100, "product list limit invalid");

        StringBuilder sql = new StringBuilder(PRODUCT_LIST_SELECT_PREFIX + CERTIFIED_SELLER_VERIFIED_SELECT + PRODUCT_LIST_SELECT_SUFFIX + """
                where 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (safeStatus != null) {
            sql.append(" and p.product_status = ?");
            args.add(safeStatus);
        }
        if (safeAuditStatus != null) {
            sql.append(" and p.audit_status = ?");
            args.add(safeAuditStatus);
        }
        if (safeKeyword != null) {
            String likeKeyword = "%" + safeKeyword.toLowerCase(Locale.ROOT) + "%";
            sql.append("""
                     and (
                        lower(p.product_no) like ?
                        or lower(p.title) like ?
                        or lower(coalesce(p.description, '')) like ?
                        or lower(coalesce(ua.user_no, '')) like ?
                        or lower(coalesce(ua.nickname, '')) like ?
                """);
            args.add(likeKeyword);
            args.add(likeKeyword);
            args.add(likeKeyword);
            args.add(likeKeyword);
            args.add(likeKeyword);
            if (safeKeyword.matches("^[1-9]\\d{0,18}$")) {
                long numericKeyword = Long.parseLong(safeKeyword);
                sql.append("""
                        or p.id = ?
                        or p.seller_id = ?
                """);
                args.add(numericKeyword);
                args.add(numericKeyword);
            }
            sql.append(")");
        }
        sql.append(" order by p.updated_at desc, p.id desc limit ?");
        args.add(safeLimit);
        return jdbcTemplate.query(sql.toString(), this::mapListItem, args.toArray());
    }

    public List<ProductListItemResponse> listProducts() {
        return jdbcTemplate.query(
                PRODUCT_LIST_SELECT_PREFIX + CERTIFIED_SELLER_VERIFIED_SELECT + PRODUCT_LIST_SELECT_SUFFIX + """
                        where p.visible = true and p.product_status = ? and p.audit_status = ?
                        """ + CERTIFIED_PRODUCT_SELLER_FILTER + """
                        order by created_at desc, id desc
                        """,
                this::mapListItem,
                STATUS_ACTIVE,
                AUDIT_APPROVED
        );
    }

    public List<ProductListItemResponse> listProductsBySeller(Long sellerId) {
        requirePositiveId(sellerId, "valid sellerId required");
        return jdbcTemplate.query(
                PRODUCT_LIST_SELECT_PREFIX + CERTIFIED_SELLER_VERIFIED_SELECT + PRODUCT_LIST_SELECT_SUFFIX + """
                        where p.seller_id = ? and p.visible = true and p.product_status = ? and p.audit_status = ?
                        """ + CERTIFIED_PRODUCT_SELLER_FILTER + """
                        order by created_at desc, id desc
                        """,
                this::mapListItem,
                sellerId,
                STATUS_ACTIVE,
                AUDIT_APPROVED
        );
    }

    public List<ProductListItemResponse> listSoldProductsBySeller(Long sellerId) {
        requirePositiveId(sellerId, "valid sellerId required");
        return jdbcTemplate.query(
                PRODUCT_LIST_SELECT_PREFIX + CERTIFIED_SELLER_VERIFIED_SELECT + PRODUCT_LIST_SELECT_SUFFIX + """
                        where p.seller_id = ? and p.product_status = ? and p.audit_status = ?
                        """ + CERTIFIED_PRODUCT_SELLER_FILTER + """
                        order by p.updated_at desc, p.id desc
                        limit 30
                        """,
                this::mapListItem,
                sellerId,
                STATUS_SOLD,
                AUDIT_APPROVED
        );
    }

    public List<ProductListItemResponse> listMyProducts(Long sellerId) {
        requirePositiveId(sellerId, "valid sellerId required");
        return jdbcTemplate.query(
                PRODUCT_LIST_SELECT_PREFIX + CERTIFIED_SELLER_VERIFIED_SELECT + PRODUCT_LIST_SELECT_SUFFIX + """
                        where p.seller_id = ?
                        order by p.created_at desc, p.id desc
                        """,
                this::mapListItem,
                sellerId
        );
    }

    public List<ProductListItemResponse> listFavorites(Long userId) {
        requirePositiveId(userId, "valid userId required");
        return jdbcTemplate.query(
                """
                        select p.id,p.product_no,p.seller_id,ua.nickname as seller_nickname,ua.avatar_url as seller_avatar_url,up.gender as seller_gender,p.title,p.category,up.city as seller_city,
                        """ + CERTIFIED_SELLER_VERIFIED_SELECT + """
                        ,p.price,p.product_status,p.audit_status,p.visible,p.created_at,p.image_urls
                        from product_favorite f
                        join product_item p on p.id = f.product_id
                        join user_account ua on ua.id = p.seller_id
                        left join user_profile up on up.user_id = p.seller_id
                        where f.user_id = ? and p.visible = true and p.product_status = ? and p.audit_status = ?
                        """ + CERTIFIED_PRODUCT_SELLER_FILTER + """
                        order by f.created_at desc, f.id desc
                        """,
                this::mapListItem,
                userId,
                STATUS_ACTIVE,
                AUDIT_APPROVED
        );
    }

    @Transactional
    public void favoriteProduct(Long userId, Long productId) {
        requirePositiveId(userId, "valid userId required");
        getVisibleProduct(productId);
        Integer existing = jdbcTemplate.queryForObject(
                "select count(*) from product_favorite where user_id = ? and product_id = ?",
                Integer.class,
                userId,
                productId
        );
        if (existing == null || existing == 0) {
            jdbcTemplate.update(
                    "insert into product_favorite (user_id, product_id, created_at) values (?, ?, CURRENT_TIMESTAMP)",
                    userId,
                    productId
            );
        }
    }

    @Transactional
    public void unfavoriteProduct(Long userId, Long productId) {
        requirePositiveId(userId, "valid userId required");
        requirePositiveId(productId, "valid productId required");
        jdbcTemplate.update("delete from product_favorite where user_id = ? and product_id = ?", userId, productId);
    }

    public ProductDetailResponse detailProduct(Long productId) {
        return detailProduct(productId, null);
    }

    public ProductDetailResponse detailProduct(Long productId, Long viewerId) {
        ProductRecord product = getVisibleProduct(productId);
        return toDetailResponse(product, isProductFavoritedBy(viewerId, product.productId()));
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
        ProductRecord updated = getExistingProduct(productId);
        ensurePendingProductAudit(updated, "商品编辑后重新审核");
        return toUpdateResponse(updated);
    }

    @Transactional
    public UpdateProductResponse updateVisibility(Long sellerId, Long productId, boolean visible) {
        requirePositiveId(sellerId, "valid sellerId required");
        ProductRecord existing = getExistingProduct(productId);
        if (!Objects.equals(existing.sellerId(), sellerId)) {
            throw new IllegalArgumentException("product ownership mismatch");
        }
        if (!AUDIT_APPROVED.equals(existing.auditState())) {
            throw new IllegalArgumentException("product not approved");
        }
        if (STATUS_SOLD.equals(existing.status()) || existing.lockedOrderNo() != null) {
            throw new IllegalArgumentException("product cannot change visibility after locked or sold");
        }
        if (visible) {
            requireCertifiedSeller(sellerId);
        }
        String nextStatus = visible ? STATUS_ACTIVE : STATUS_OFFLINE;
        int changed = jdbcTemplate.update(
                "update product_item set product_status = ?, visible = ?, updated_at = CURRENT_TIMESTAMP where id = ? and seller_id = ? and audit_status = ? and product_status <> ? and locked_order_no is null",
                nextStatus,
                visible,
                productId,
                sellerId,
                AUDIT_APPROVED,
                STATUS_SOLD
        );
        if (changed == 0) {
            throw new IllegalArgumentException("product visibility update failed");
        }
        return toUpdateResponse(getExistingProduct(productId));
    }

    @Transactional
    public UpdateProductResponse adminOfflineProduct(Long productId, String reason) {
        ProductRecord existing = getExistingProduct(productId);
        normalizeAdminOfflineReason(reason);
        if (!STATUS_ACTIVE.equals(existing.status()) || !AUDIT_APPROVED.equals(existing.auditState()) || !existing.visible()) {
            throw new IllegalArgumentException("product-not-active");
        }
        if (STATUS_SOLD.equals(existing.status()) || existing.lockedOrderNo() != null) {
            throw new IllegalArgumentException("product cannot be offlined after locked or sold");
        }
        int changed = jdbcTemplate.update(
                "update product_item set product_status = ?, audit_status = ?, visible = false, updated_at = CURRENT_TIMESTAMP where id = ? and product_status = ? and audit_status = ? and visible = true and locked_order_no is null",
                STATUS_OFFLINE,
                AUDIT_REJECTED,
                existing.productId(),
                STATUS_ACTIVE,
                AUDIT_APPROVED
        );
        if (changed == 0) {
            throw new IllegalArgumentException("product offline failed");
        }
        return toUpdateResponse(getExistingProduct(productId));
    }

    public ProductSnapshot snapshotForOrder(Long productId) {
        ProductRecord product = getVisibleProduct(productId);
        if (product.lockedOrderNo() != null) {
            throw new IllegalArgumentException("product-already-locked");
        }
        return new ProductSnapshot(product.productId(), product.productNo(), product.title(), product.price(), product.tradeRule(), product.sellerId());
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
    public void releaseOrderLock(Long productId, String orderNo) {
        requireOrderNo(orderNo);
        ProductRecord product = getExistingProduct(productId);
        if (!Objects.equals(product.lockedOrderNo(), orderNo)) {
            throw new IllegalArgumentException("product-order-lock-mismatch");
        }
        if (!STATUS_ACTIVE.equals(product.status())) {
            throw new IllegalArgumentException("product-not-releasable");
        }
        int changed = jdbcTemplate.update(
                "update product_item set locked_order_no = null, updated_at = CURRENT_TIMESTAMP where id = ? and locked_order_no = ? and product_status = ?",
                productId,
                orderNo,
                STATUS_ACTIVE
        );
        if (changed == 0) {
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
        requireCertifiedSeller(product.sellerId());
        jdbcTemplate.update(
                "update product_item set product_status = ?, audit_status = ?, visible = true, updated_at = CURRENT_TIMESTAMP where id = ?",
                STATUS_ACTIVE,
                AUDIT_APPROVED,
                product.productId()
        );
    }

    @Transactional
    public void rejectForSale(Long productId) {
        ProductRecord product = getExistingProduct(productId);
        if (STATUS_SOLD.equals(product.status())) {
            throw new IllegalArgumentException("product-already-sold");
        }
        if (product.lockedOrderNo() != null) {
            throw new IllegalArgumentException("product-already-locked");
        }
        jdbcTemplate.update(
                "update product_item set product_status = ?, audit_status = ?, visible = false, updated_at = CURRENT_TIMESTAMP where id = ?",
                STATUS_OFFLINE,
                AUDIT_REJECTED,
                product.productId()
        );
    }

    public CreateProductResponse createResponse(Long productId) {
        return toCreateResponse(getExistingProduct(productId));
    }

    public ProductDetailResponse adminDetailProduct(Long productId) {
        return toDetailResponse(getExistingProduct(productId));
    }

    public String requirePendingProductAuditNo(Long productId) {
        ProductRecord product = getExistingProduct(productId);
        try {
            return jdbcTemplate.queryForObject("""
                    select audit_no
                    from audit_record
                    where audit_type = ?
                      and target_type = ?
                      and target_id = ?
                      and status = ?
                    order by created_at desc, id desc
                    limit 1
                    """, String.class, AUDIT_TYPE_PRODUCT, AUDIT_TYPE_PRODUCT, String.valueOf(product.productId()), AUDIT_PENDING);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("product audit record not found");
        }
    }

    private ProductRecord getVisibleProduct(Long productId) {
        ProductRecord product = getExistingProduct(productId);
        if (!STATUS_ACTIVE.equals(product.status()) || !AUDIT_APPROVED.equals(product.auditState()) || !product.visible()) {
            throw new IllegalArgumentException("product-not-saleable");
        }
        requireCertifiedSeller(product.sellerId());
        return product;
    }

    private ProductListItemResponse mapListItem(ResultSet rs, int rowNum) throws SQLException {
        return new ProductListItemResponse(
                rs.getLong("id"),
                rs.getString("product_no"),
                rs.getLong("seller_id"),
                rs.getString("seller_nickname"),
                rs.getString("seller_avatar_url"),
                rs.getString("seller_gender"),
                rs.getString("title"),
                rs.getString("category"),
                rs.getString("seller_city"),
                rs.getBoolean("seller_video_verified"),
                rs.getBigDecimal("price"),
                firstImageUrl(decodeImageUrls(rs.getString("image_urls"))),
                rs.getString("product_status"),
                rs.getString("audit_status"),
                rs.getBoolean("visible"),
                timeText(rs.getTimestamp("created_at"))
        );
    }

    private ProductRecord getExistingProduct(Long productId) {
        requirePositiveId(productId, "productId required");
        ProductRecord product = findById(productId);
        if (product == null) {
            throw new IllegalArgumentException("product-not-found");
        }
        return product;
    }

    private void ensurePendingProductAudit(ProductRecord product, String reason) {
        String targetId = String.valueOf(product.productId());
        List<String> existingAuditNos = jdbcTemplate.query("""
                select audit_no
                from audit_record
                where audit_type = ?
                  and target_type = ?
                  and target_id = ?
                order by id desc
                limit 1
                """, (rs, rowNum) -> rs.getString("audit_no"), AUDIT_TYPE_PRODUCT, AUDIT_TYPE_PRODUCT, targetId);
        String description = product.title();
        if (existingAuditNos.isEmpty()) {
            jdbcTemplate.update("""
                    insert into audit_record (audit_no,audit_type,user_id,target_type,target_id,reason,description,status,created_at)
                    values (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                    """,
                    generateNo("AU-PRODUCT", product.productId(), product.productNo(), product.title()),
                    AUDIT_TYPE_PRODUCT,
                    product.sellerId(),
                    AUDIT_TYPE_PRODUCT,
                    targetId,
                    reason,
                    description,
                    AUDIT_PENDING
            );
            return;
        }
        jdbcTemplate.update("""
                update audit_record
                set user_id = ?,
                    reason = ?,
                    description = ?,
                    status = ?,
                    review_remark = null,
                    reviewed_at = null,
                    created_at = CURRENT_TIMESTAMP
                where audit_no = ?
                """,
                product.sellerId(),
                reason,
                description,
                AUDIT_PENDING,
                existingAuditNos.get(0)
        );
    }

    private String normalizeAdminProductStatus(String status) {
        String safeStatus = safeText(status);
        if (safeStatus == null || "ALL".equalsIgnoreCase(safeStatus)) {
            return null;
        }
        String upper = safeStatus.toUpperCase(Locale.ROOT);
        if (!List.of(STATUS_PENDING_AUDIT, STATUS_ACTIVE, STATUS_OFFLINE, STATUS_SOLD).contains(upper)) {
            throw new IllegalArgumentException("product status invalid");
        }
        return upper;
    }

    private String normalizeAdminAuditStatus(String auditStatus) {
        String safeStatus = safeText(auditStatus);
        if (safeStatus == null || "ALL".equalsIgnoreCase(safeStatus)) {
            return null;
        }
        String upper = safeStatus.toUpperCase(Locale.ROOT);
        if (!List.of(AUDIT_PENDING, AUDIT_APPROVED, AUDIT_REJECTED).contains(upper)) {
            throw new IllegalArgumentException("product audit status invalid");
        }
        return upper;
    }

    private int normalizeAdminLimit(Integer limit, int defaultLimit, int maxLimit, String message) {
        int normalized = limit == null ? defaultLimit : limit;
        if (normalized <= 0 || normalized > maxLimit) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeAdminSearchKeyword(String keyword) {
        String normalized = safeText(keyword);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("keyword invalid");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("preview") || lower.contains("demo") || lower.contains("mock") || lower.contains("placeholder")) {
            throw new IllegalArgumentException("keyword invalid");
        }
        if (normalized.matches("^\\d+$") && !normalized.matches("^[1-9]\\d{0,18}$")) {
            throw new IllegalArgumentException("keyword invalid");
        }
        return normalized;
    }

    private String normalizeAdminOfflineReason(String reason) {
        String normalized = safeText(reason);
        if (normalized == null || normalized.length() > 128) {
            throw new IllegalArgumentException("product offline reason invalid");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("preview") || lower.contains("demo") || lower.contains("mock") || lower.contains("sample") || lower.contains("placeholder")) {
            throw new IllegalArgumentException("product offline reason invalid");
        }
        return normalized;
    }

    private void requirePositiveId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireCertifiedSeller(Long sellerId) {
        List<Boolean> rows = jdbcTemplate.query("""
                SELECT CASE WHEN a.status = 'ACTIVE'
                         AND UPPER(COALESCE(p.main_role, 'BUYER')) IN ('SELLER', 'BOTH')
                         AND p.video_identity_status = 'APPROVED'
                         AND p.video_verified = TRUE
                         AND EXISTS (
                             SELECT 1
                             FROM media_upload_ticket t
                             WHERE t.owner_user_id = p.user_id
	                               AND t.scene = 'VIDEO_IDENTITY'
	                               AND t.status = 'UPLOADED'
	                               AND t.storage_url LIKE '/uploads/video-identity/%'
	                               AND EXISTS (
	                                   SELECT 1
	                                   FROM audit_record ar
	                                   WHERE ar.audit_type = 'VIDEO_IDENTITY'
	                                     AND ar.user_id = p.user_id
	                                     AND ar.target_id = CONCAT('', p.user_id)
	                                     AND ar.status = 'APPROVED'
	                                     AND ar.reason = t.storage_url
	                               )
	                         )
                    THEN TRUE ELSE FALSE END AS can_publish
                FROM user_account a
                JOIN user_profile p ON p.user_id = a.id
                WHERE a.id = ?
                """, (rs, rowNum) -> rs.getBoolean("can_publish"), sellerId);
        if (rows.isEmpty() || !rows.get(0)) {
            throw new IllegalArgumentException("seller certification required");
        }
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
        return toDetailResponse(product, false);
    }

    private ProductDetailResponse toDetailResponse(ProductRecord product, boolean favoritedByMe) {
        return new ProductDetailResponse(product.productId(), product.productNo(), product.title(), product.description(), product.price(), product.imageUrls(), product.status(), product.auditState(), product.visible(), product.tradeRule(), product.createdAt(), product.sellerId(), favoritedByMe);
    }

    private boolean isProductFavoritedBy(Long userId, Long productId) {
        if (userId == null || userId <= 0 || productId == null || productId <= 0) return false;
        Integer count = jdbcTemplate.queryForObject("select count(*) from product_favorite where user_id = ? and product_id = ?", Integer.class, userId, productId);
        return count != null && count > 0;
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
            mediaUploadTicketService.requireUploadedStorageUrl(sellerId, "PRODUCT_IMAGE", imageUrl);
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
