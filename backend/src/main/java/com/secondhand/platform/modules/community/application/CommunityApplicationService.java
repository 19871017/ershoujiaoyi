package com.secondhand.platform.modules.community.application;

import com.secondhand.platform.modules.media.application.MediaUploadTicketService;
import com.secondhand.platform.modules.notification.application.NotificationApplicationService;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityApplicationService {
    private static final int MAX_LIST_LIMIT = 50;
    private static final int DUPLICATE_SUBMIT_WINDOW_SECONDS = 30;
    private static final String COMMUNITY_IMAGE_STORAGE_PREFIX = "/uploads/community-image/";
    private static final Pattern PHONE_CONTACT_PATTERN = Pattern.compile("(?<!\\d)(?:\\+?86[-\\s]?)?1[3-9]\\d[-\\s]?\\d{4}[-\\s]?\\d{4}(?!\\d)");
    private static final Pattern COMMUNITY_COMMENT_NO_PATTERN = Pattern.compile("CMT-[1-9]\\d*-[1-9]\\d*(?:-[A-Z0-9]{6,16})?");
    private static final List<String> ALLOWED_TOPICS = List.of("生活日常", "闲置避坑", "交易经验", "求购心愿");
    private static final String CERTIFIED_SELLER_RELATED_PRODUCT_FILTER = """
             AND EXISTS (
                 SELECT 1
                 FROM user_account seller
                 JOIN user_profile seller_profile ON seller_profile.user_id = seller.id
                 WHERE seller.id = rp.seller_id
                   AND seller.status = 'ACTIVE'
                   AND UPPER(COALESCE(seller_profile.main_role, 'BUYER')) IN ('SELLER', 'BOTH')
                   AND seller_profile.video_identity_status = 'APPROVED'
                   AND seller_profile.video_verified = TRUE
                   AND EXISTS (
                       SELECT 1
                       FROM media_upload_ticket video_ticket
                       WHERE video_ticket.owner_user_id = seller_profile.user_id
                         AND video_ticket.scene = 'VIDEO_IDENTITY'
                         AND video_ticket.status = 'UPLOADED'
                         AND video_ticket.storage_url LIKE '/uploads/video-identity/%'
                         AND EXISTS (
                             SELECT 1
                             FROM audit_record audit
                             WHERE audit.audit_type = 'VIDEO_IDENTITY'
                               AND audit.user_id = seller_profile.user_id
                               AND audit.target_id = CONCAT('', seller_profile.user_id)
                               AND audit.status = 'APPROVED'
                               AND audit.reason = video_ticket.storage_url
                         )
                   )
             )
            """;
    private final JdbcTemplate jdbcTemplate;
    private final MediaUploadTicketService mediaUploadTicketService;
    private final NotificationApplicationService notificationApplicationService;

    public CommunityApplicationService(JdbcTemplate jdbcTemplate, MediaUploadTicketService mediaUploadTicketService) {
        this(jdbcTemplate, mediaUploadTicketService, new NotificationApplicationService(jdbcTemplate));
    }

    @Autowired
    public CommunityApplicationService(JdbcTemplate jdbcTemplate, MediaUploadTicketService mediaUploadTicketService, NotificationApplicationService notificationApplicationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.mediaUploadTicketService = mediaUploadTicketService;
        this.notificationApplicationService = notificationApplicationService;
        ensureCommunitySchemaCompatibility();
    }

    @Transactional
    public CommunityPostResponse createPost(Long authorId, CreateCommunityPostRequest request) {
        if (authorId == null || authorId <= 0) {
            throw new IllegalArgumentException("invalid author");
        }
        requireActiveCommunityUser(authorId, "invalid author");
        String title = requireText(request == null ? null : request.getTitle(), "title", 4, 64);
        String topic = requireCommunityTopic(request.getTopic());
        String content = requireText(request.getContent(), "content", 8, 1000);
        List<String> images = sanitizeImages(authorId, request.getImageUrls());
        Long relatedProductId = normalizeRelatedProductId(authorId, request.getRelatedProductId());
        CommunityPostResponse recentDuplicate = findRecentDuplicatePost(authorId, title, topic, content, images, relatedProductId);
        if (recentDuplicate != null) {
            return recentDuplicate;
        }
        String postNo = nextCommunityNo("POST", authorId);
        jdbcTemplate.update("INSERT INTO community_post(post_no, author_id, title, topic, content, image_urls, related_product_id, status, like_count, comment_count, created_at, updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                postNo, authorId, title, topic, content, joinImages(images), relatedProductId, "PUBLISHED", 0, 0);
        Long postId = jdbcTemplate.queryForObject("SELECT id FROM community_post WHERE post_no = ?", Long.class, postNo);
        return loadPostById(postId);
    }

    public List<CommunityPostResponse> listPublishedPosts(int limit) {
        return listPublishedPosts(limit, null);
    }

    public List<CommunityPostResponse> listPublishedPosts(int limit, Long currentUserId) {
        return listPublishedPosts(limit, currentUserId, null);
    }

    public List<CommunityPostResponse> listPublishedPosts(int limit, Long currentUserId, String topic) {
        int capped = Math.max(1, Math.min(limit <= 0 ? 20 : limit, MAX_LIST_LIMIT));
        String normalizedTopic = normalizeOptionalCommunityTopic(topic);
        boolean hasViewer = currentUserId != null && currentUserId > 0;
        return jdbcTemplate.query("""
                        SELECT p.*,
                               COALESCE(NULLIF(a.nickname, ''), NULLIF(a.user_no, ''), CONCAT('用户', p.author_id)) AS author_name,
                               a.avatar_url AS author_avatar,
                               up.city AS author_city,
                               rp.title AS related_product_title,
                               rp.price AS related_product_price,
                               CASE WHEN ? = TRUE AND EXISTS (
                                   SELECT 1 FROM community_like l WHERE l.post_id = p.id AND l.user_id = ?
                               ) THEN TRUE ELSE FALSE END AS liked_by_me,
                               CASE WHEN ? = TRUE AND p.author_id <> ? AND EXISTS (
                                   SELECT 1 FROM user_follow f WHERE f.follower_id = ? AND f.followed_id = p.author_id
                               ) THEN TRUE ELSE FALSE END AS followed_by_me
                        FROM community_post p
                        JOIN user_account a ON a.id = p.author_id AND a.status = 'ACTIVE'
                        LEFT JOIN user_profile up ON up.user_id = p.author_id
                        LEFT JOIN product_item rp ON rp.id = p.related_product_id
                         AND rp.seller_id = p.author_id
                         AND rp.visible = TRUE
                         AND rp.product_status = 'ACTIVE'
                         AND rp.audit_status = 'APPROVED'
                        """ + CERTIFIED_SELLER_RELATED_PRODUCT_FILTER + """
                        WHERE p.status = 'PUBLISHED'
                          AND (? IS NULL OR p.topic = ?)
                        ORDER BY p.created_at DESC, p.id DESC
                        LIMIT ?
                        """,
                (rs, rowNum) -> mapPost(rs.getString("post_no"), rs.getLong("id"), rs.getLong("author_id"), rs.getString("author_name"), rs.getString("author_avatar"), rs.getString("author_city"), rs.getString("title"), rs.getString("topic"), rs.getString("content"), rs.getString("image_urls"), rs.getString("status"), rs.getInt("like_count"), rs.getInt("comment_count"), rs.getTimestamp("created_at"), rs.getBoolean("liked_by_me"), rs.getBoolean("followed_by_me"), nullableLong(rs, "related_product_id"), rs.getString("related_product_title"), rs.getBigDecimal("related_product_price")),
                hasViewer, currentUserId, hasViewer, currentUserId, currentUserId, normalizedTopic, normalizedTopic, capped);
    }

    public CommunityPostDetailResponse detail(String postIdOrNo, Long currentUserId) {
        String normalized = postIdOrNo == null ? "" : postIdOrNo.trim();
        if (!isValidPostLookup(normalized)) {
            throw new IllegalArgumentException("invalid post id");
        }
        Long id;
        if (normalized.matches("\\d+")) {
            id = Long.parseLong(normalized);
        } else {
            List<Long> ids = jdbcTemplate.query("SELECT id FROM community_post WHERE post_no = ?", (rs, rowNum) -> rs.getLong("id"), normalized);
            if (ids.isEmpty()) {
                throw new IllegalArgumentException("post not found");
            }
            id = ids.get(0);
        }
        return detail(id, currentUserId);
    }

    public CommunityPostDetailResponse detail(Long postId, Long currentUserId) {
        CommunityPostResponse post = loadPublishedPostById(postId);
        List<CommunityCommentResponse> comments = jdbcTemplate.query("""
                        SELECT c.*,
                               COALESCE(NULLIF(a.nickname, ''), NULLIF(a.user_no, ''), CONCAT('用户', c.author_id)) AS author_name,
                               a.avatar_url AS author_avatar
                        FROM community_comment c
                        JOIN user_account a ON a.id = c.author_id AND a.status = 'ACTIVE'
                        WHERE c.post_id = ? AND c.status = 'PUBLISHED'
                        ORDER BY c.created_at ASC, c.id ASC
                        """,
                (rs, rowNum) -> mapComment(rs), postId);
        boolean liked = currentUserId != null && currentUserId > 0 && jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM community_like WHERE post_id = ? AND user_id = ?", Integer.class, postId, currentUserId) > 0;
        boolean followed = isViewerFollowingAuthor(currentUserId, post.getAuthorId());
        return new CommunityPostDetailResponse(post.getPostNo(), post.getPostId(), post.getAuthorId(), post.getAuthorName(), post.getAuthorAvatar(), post.getCity(), post.getTitle(), post.getTopic(), post.getContent(),
                post.getImageUrls(), post.getStatus(), post.getLikeCount(), post.getCommentCount(), post.getCreatedAt(), liked, followed, comments,
                post.getRelatedProductId(), post.getRelatedProductTitle(), post.getRelatedProductPrice());
    }

    public List<CommunityPostResponse> adminListPosts(String keyword, Long authorId, Integer limit) {
        Long safeAuthorId = authorId == null ? null : requirePositiveId(authorId, "invalid author");
        String safeKeyword = normalizeAdminKeyword(keyword);
        int safeLimit = normalizeAdminLimit(limit);
        StringBuilder sql = new StringBuilder("""
                SELECT p.*,
                       COALESCE(NULLIF(a.nickname, ''), NULLIF(a.user_no, ''), CONCAT('用户', p.author_id)) AS author_name,
                       a.avatar_url AS author_avatar,
                       up.city AS author_city,
                       rp.title AS related_product_title,
                       rp.price AS related_product_price
                FROM community_post p
                LEFT JOIN user_account a ON a.id = p.author_id
                LEFT JOIN user_profile up ON up.user_id = p.author_id
                LEFT JOIN product_item rp ON rp.id = p.related_product_id
                WHERE 1 = 1
                """);
        List<Object> args = new java.util.ArrayList<>();
        if (safeAuthorId != null) {
            sql.append(" AND p.author_id = ?");
            args.add(safeAuthorId);
        }
        if (safeKeyword != null) {
            String likeKeyword = "%" + safeKeyword + "%";
            sql.append("""
                     AND (
                        p.post_no = ?
                        OR p.title LIKE ?
                        OR p.topic LIKE ?
                        OR p.content LIKE ?
                        OR a.user_no = ?
                        OR a.nickname LIKE ?
                        OR EXISTS (
                            SELECT 1
                            FROM community_comment c
                            WHERE c.post_id = p.id
                              AND (
                                c.comment_no = ?
                                OR c.content LIKE ?
                              )
                        )
                """);
            java.util.Collections.addAll(args, safeKeyword, likeKeyword, likeKeyword, likeKeyword, safeKeyword, likeKeyword, safeKeyword, likeKeyword);
            if (safeKeyword.matches("^[1-9]\\d{0,18}$")) {
                long numeric = Long.parseLong(safeKeyword);
                sql.append("""
                        OR p.id = ?
                        OR p.author_id = ?
                        OR p.related_product_id = ?
                """);
                java.util.Collections.addAll(args, numeric, numeric, numeric);
            }
            sql.append(")");
        }
        sql.append(" ORDER BY p.created_at DESC, p.id DESC LIMIT ?");
        args.add(safeLimit);
        return jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> mapAdminPost(rs.getString("post_no"), rs.getLong("id"), rs.getLong("author_id"), rs.getString("author_name"), rs.getString("author_avatar"), rs.getString("author_city"), rs.getString("title"), rs.getString("topic"), rs.getString("content"), rs.getString("image_urls"), rs.getString("status"), rs.getInt("like_count"), rs.getInt("comment_count"), rs.getTimestamp("created_at"), false, nullableLong(rs, "related_product_id"), rs.getString("related_product_title"), rs.getBigDecimal("related_product_price")),
                args.toArray());
    }

    public CommunityPostDetailResponse adminDetail(String postIdOrNo) {
        String normalized = postIdOrNo == null ? "" : postIdOrNo.trim();
        if (!isValidPostLookup(normalized)) {
            throw new IllegalArgumentException("invalid post id");
        }
        Long id;
        if (normalized.matches("\\d+")) {
            id = Long.parseLong(normalized);
        } else {
            List<Long> ids = jdbcTemplate.query("SELECT id FROM community_post WHERE post_no = ?", (rs, rowNum) -> rs.getLong("id"), normalized);
            if (ids.isEmpty()) {
                throw new IllegalArgumentException("post not found");
            }
            id = ids.get(0);
        }
        CommunityPostResponse post = loadAdminPostById(id);
        List<CommunityCommentResponse> comments = jdbcTemplate.query("""
                        SELECT c.*,
                               COALESCE(NULLIF(a.nickname, ''), NULLIF(a.user_no, ''), CONCAT('用户', c.author_id)) AS author_name,
                               a.avatar_url AS author_avatar
                        FROM community_comment c
                        LEFT JOIN user_account a ON a.id = c.author_id
                        WHERE c.post_id = ?
                        ORDER BY c.created_at ASC, c.id ASC
                        """,
                (rs, rowNum) -> mapComment(rs), id);
        return new CommunityPostDetailResponse(post.getPostNo(), post.getPostId(), post.getAuthorId(), post.getAuthorName(), post.getAuthorAvatar(), post.getCity(), post.getTitle(), post.getTopic(), post.getContent(),
                post.getImageUrls(), post.getStatus(), post.getLikeCount(), post.getCommentCount(), post.getCreatedAt(), false, comments,
                post.getRelatedProductId(), post.getRelatedProductTitle(), post.getRelatedProductPrice());
    }

    @Transactional
    public CommunityPostDetailResponse adminBlockPost(String postIdOrNo, String reason) {
        normalizeAdminModerationReason(reason);
        Long postId = resolveAdminPostId(postIdOrNo);
        int changed = jdbcTemplate.update("""
                update community_post
                set status = 'BLOCKED', updated_at = CURRENT_TIMESTAMP
                where id = ? and status = 'PUBLISHED'
                """, postId);
        if (changed == 0) {
            throw new IllegalArgumentException("community post cannot be blocked");
        }
        return adminDetail(postId.toString());
    }

    @Transactional
    public CommunityPostDetailResponse adminRestorePost(String postIdOrNo, String reason) {
        normalizeAdminModerationReason(reason);
        Long postId = resolveAdminPostId(postIdOrNo);
        int changed = jdbcTemplate.update("""
                update community_post
                set status = 'PUBLISHED', updated_at = CURRENT_TIMESTAMP
                where id = ?
                  and status = 'BLOCKED'
                  and exists (
                    select 1
                    from user_account author
                    where author.id = community_post.author_id
                      and author.status = 'ACTIVE'
                  )
                """, postId);
        if (changed == 0) {
            throw new IllegalArgumentException("community post cannot be restored");
        }
        refreshPublishedCommentCount(postId);
        return adminDetail(postId.toString());
    }

    @Transactional
    public CommunityPostDetailResponse adminBlockComment(String commentNo, String reason) {
        normalizeAdminModerationReason(reason);
        String safeCommentNo = normalizeAdminCommentNo(commentNo);
        List<Long> postIds = jdbcTemplate.query("""
                select c.post_id
                from community_comment c
                join community_post p on p.id = c.post_id
                where c.comment_no = ?
                  and c.status = 'PUBLISHED'
                  and p.status = 'PUBLISHED'
                """, (rs, rowNum) -> rs.getLong("post_id"), safeCommentNo);
        if (postIds.isEmpty()) {
            throw new IllegalArgumentException("community comment cannot be blocked");
        }
        Long postId = postIds.get(0);
        int changed = jdbcTemplate.update("""
                update community_comment
                set status = 'BLOCKED'
                where comment_no = ? and status = 'PUBLISHED'
                """, safeCommentNo);
        if (changed == 0) {
            throw new IllegalArgumentException("community comment cannot be blocked");
        }
        refreshPublishedCommentCount(postId);
        return adminDetail(postId.toString());
    }

    @Transactional
    public CommunityPostDetailResponse adminRestoreComment(String commentNo, String reason) {
        normalizeAdminModerationReason(reason);
        String safeCommentNo = normalizeAdminCommentNo(commentNo);
        List<Long> postIds = jdbcTemplate.query("""
                select c.post_id
                from community_comment c
                join community_post p on p.id = c.post_id
                join user_account comment_author on comment_author.id = c.author_id and comment_author.status = 'ACTIVE'
                where c.comment_no = ?
                  and c.status = 'BLOCKED'
                  and p.status = 'PUBLISHED'
                """, (rs, rowNum) -> rs.getLong("post_id"), safeCommentNo);
        if (postIds.isEmpty()) {
            throw new IllegalArgumentException("community comment cannot be restored");
        }
        Long postId = postIds.get(0);
        int changed = jdbcTemplate.update("""
                update community_comment
                set status = 'PUBLISHED'
                where comment_no = ? and status = 'BLOCKED'
                """, safeCommentNo);
        if (changed == 0) {
            throw new IllegalArgumentException("community comment cannot be restored");
        }
        refreshPublishedCommentCount(postId);
        return adminDetail(postId.toString());
    }

    @Transactional
    public CommunityCommentResponse addComment(Long authorId, Long postId, CreateCommunityCommentRequest request) {
        if (authorId == null || authorId <= 0) {
            throw new IllegalArgumentException("invalid author");
        }
        requireActiveCommunityUser(authorId, "invalid author");
        CommunityPostResponse post = loadPublishedPostById(postId);
        String content = requireText(request == null ? null : request.getContent(), "content", 4, 500);
        CommunityCommentResponse recentDuplicate = findRecentDuplicateComment(authorId, postId, content);
        if (recentDuplicate != null) {
            return recentDuplicate;
        }
        String commentNo = nextCommunityNo("CMT", authorId);
        jdbcTemplate.update("INSERT INTO community_comment(comment_no, post_id, author_id, content, status, created_at) VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)",
                commentNo, postId, authorId, content, "PUBLISHED");
        jdbcTemplate.update("UPDATE community_post SET comment_count = comment_count + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?", postId);
        notifyPostCommented(post, authorId, content);
        return jdbcTemplate.queryForObject("""
                        SELECT c.*,
                               COALESCE(NULLIF(a.nickname, ''), NULLIF(a.user_no, ''), CONCAT('用户', c.author_id)) AS author_name,
                               a.avatar_url AS author_avatar
                        FROM community_comment c
                        JOIN user_account a ON a.id = c.author_id AND a.status = 'ACTIVE'
                        WHERE c.comment_no = ?
                        """,
                (rs, rowNum) -> mapComment(rs), commentNo);
    }

    @Transactional
    public CommunityPostDetailResponse likePost(Long userId, Long postId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("invalid user");
        }
        requireActiveCommunityUser(userId, "invalid user");
        CommunityPostResponse post = loadPublishedPostById(postId);
        int inserted = jdbcTemplate.update("INSERT INTO community_like(post_id, user_id, created_at) SELECT ?, ?, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM community_like WHERE post_id = ? AND user_id = ?)",
                postId, userId, postId, userId);
        if (inserted > 0) {
            jdbcTemplate.update("UPDATE community_post SET like_count = like_count + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?", postId);
            notifyPostLiked(post, userId);
        }
        return detail(postId, userId);
    }

    @Transactional
    public CommunityPostDetailResponse unlikePost(Long userId, Long postId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("invalid user");
        }
        requireActiveCommunityUser(userId, "invalid user");
        loadPublishedPostById(postId);
        int deleted = jdbcTemplate.update("DELETE FROM community_like WHERE post_id = ? AND user_id = ?", postId, userId);
        if (deleted > 0) {
            jdbcTemplate.update("UPDATE community_post SET like_count = CASE WHEN like_count > 0 THEN like_count - 1 ELSE 0 END, updated_at = CURRENT_TIMESTAMP WHERE id = ?", postId);
        }
        return detail(postId, userId);
    }

    private static boolean isValidPostLookup(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase();
        if (lower.contains("preview") || lower.contains("demo") || lower.contains("mock") || lower.contains("sample") || lower.contains("placeholder") || "unknown".equals(lower)) {
            return false;
        }
        if (value.matches("\\d+")) {
            try {
                return Long.parseLong(value) > 0;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return value.matches("POST-[1-9]\\d*-[1-9]\\d*");
    }

    private static Long requirePositiveId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private Long resolveAdminPostId(String postIdOrNo) {
        String normalized = postIdOrNo == null ? "" : postIdOrNo.trim();
        if (!isValidPostLookup(normalized)) {
            throw new IllegalArgumentException("invalid post id");
        }
        if (normalized.matches("\\d+")) {
            return Long.parseLong(normalized);
        }
        List<Long> ids = jdbcTemplate.query("SELECT id FROM community_post WHERE post_no = ?", (rs, rowNum) -> rs.getLong("id"), normalized);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("post not found");
        }
        return ids.get(0);
    }

    private static String normalizeAdminCommentNo(String commentNo) {
        String normalized = commentNo == null ? "" : commentNo.trim();
        String lower = normalized.toLowerCase();
        if (normalized.isBlank()
                || lower.contains("preview")
                || lower.contains("demo")
                || lower.contains("mock")
                || lower.contains("sample")
                || lower.contains("placeholder")
                || !COMMUNITY_COMMENT_NO_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("invalid comment no");
        }
        return normalized;
    }

    private void requireActiveCommunityUser(Long userId, String message) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM user_account
                WHERE id = ? AND status = 'ACTIVE'
                """, Integer.class, userId);
        if (count == null || count <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static int normalizeAdminLimit(Integer limit) {
        int normalized = limit == null ? 20 : limit;
        if (normalized <= 0 || normalized > 100) {
            throw new IllegalArgumentException("invalid limit");
        }
        return normalized;
    }

    private static String normalizeAdminKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("invalid keyword");
        }
        String lower = normalized.toLowerCase();
        if (lower.contains("preview") || lower.contains("demo") || lower.contains("mock") || lower.contains("sample") || lower.contains("placeholder")) {
            throw new IllegalArgumentException("invalid keyword");
        }
        if (normalized.matches("^\\d+$") && !normalized.matches("^[1-9]\\d{0,18}$")) {
            throw new IllegalArgumentException("invalid keyword");
        }
        return normalized;
    }

    private static String normalizeAdminModerationReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        String lower = normalized.toLowerCase();
        if (normalized.isBlank()
                || normalized.length() > 128
                || lower.contains("preview")
                || lower.contains("demo")
                || lower.contains("mock")
                || lower.contains("sample")
                || lower.contains("placeholder")) {
            throw new IllegalArgumentException("community moderation reason invalid");
        }
        return normalized;
    }

    private CommunityPostResponse loadPostById(Long postId) {
        List<CommunityPostResponse> posts = jdbcTemplate.query("""
                        SELECT p.*,
                               COALESCE(NULLIF(a.nickname, ''), NULLIF(a.user_no, ''), CONCAT('用户', p.author_id)) AS author_name,
                               a.avatar_url AS author_avatar,
                               up.city AS author_city,
                               rp.title AS related_product_title,
                               rp.price AS related_product_price
                        FROM community_post p
                        JOIN user_account a ON a.id = p.author_id AND a.status = 'ACTIVE'
                        LEFT JOIN user_profile up ON up.user_id = p.author_id
                        LEFT JOIN product_item rp ON rp.id = p.related_product_id
                         AND rp.seller_id = p.author_id
                         AND rp.visible = TRUE
                         AND rp.product_status = 'ACTIVE'
                         AND rp.audit_status = 'APPROVED'
                        """ + CERTIFIED_SELLER_RELATED_PRODUCT_FILTER + """
                        WHERE p.id = ?
                        """,
                (rs, rowNum) -> mapPost(rs.getString("post_no"), rs.getLong("id"), rs.getLong("author_id"), rs.getString("author_name"), rs.getString("author_avatar"), rs.getString("author_city"), rs.getString("title"), rs.getString("topic"), rs.getString("content"), rs.getString("image_urls"), rs.getString("status"), rs.getInt("like_count"), rs.getInt("comment_count"), rs.getTimestamp("created_at"), false, nullableLong(rs, "related_product_id"), rs.getString("related_product_title"), rs.getBigDecimal("related_product_price")), postId);
        if (posts.isEmpty()) {
            throw new IllegalArgumentException("post not found");
        }
        return posts.get(0);
    }

    private CommunityPostResponse loadPublishedPostById(Long postId) {
        CommunityPostResponse post = loadPostById(postId);
        if (!"PUBLISHED".equals(post.getStatus())) {
            throw new IllegalArgumentException("post not found");
        }
        return post;
    }

    private CommunityPostResponse loadAdminPostById(Long postId) {
        List<CommunityPostResponse> posts = jdbcTemplate.query("""
                        SELECT p.*,
                               COALESCE(NULLIF(a.nickname, ''), NULLIF(a.user_no, ''), CONCAT('用户', p.author_id)) AS author_name,
                               a.avatar_url AS author_avatar,
                               up.city AS author_city,
                               rp.title AS related_product_title,
                               rp.price AS related_product_price
                        FROM community_post p
                        LEFT JOIN user_account a ON a.id = p.author_id
                        LEFT JOIN user_profile up ON up.user_id = p.author_id
                        LEFT JOIN product_item rp ON rp.id = p.related_product_id
                        WHERE p.id = ?
                        """,
                (rs, rowNum) -> mapAdminPost(rs.getString("post_no"), rs.getLong("id"), rs.getLong("author_id"), rs.getString("author_name"), rs.getString("author_avatar"), rs.getString("author_city"), rs.getString("title"), rs.getString("topic"), rs.getString("content"), rs.getString("image_urls"), rs.getString("status"), rs.getInt("like_count"), rs.getInt("comment_count"), rs.getTimestamp("created_at"), false, nullableLong(rs, "related_product_id"), rs.getString("related_product_title"), rs.getBigDecimal("related_product_price")), postId);
        if (posts.isEmpty()) {
            throw new IllegalArgumentException("post not found");
        }
        return posts.get(0);
    }

    private CommunityPostResponse mapPost(String postNo, Long postId, Long authorId, String title, String topic, String content,
                                          String imageUrls, String status, int likeCount, int commentCount, Timestamp createdAt) {
        return mapPost(postNo, postId, authorId, title, topic, content, imageUrls, status, likeCount, commentCount, createdAt, false);
    }

    private CommunityPostResponse mapPost(String postNo, Long postId, Long authorId, String title, String topic, String content,
                                          String imageUrls, String status, int likeCount, int commentCount, Timestamp createdAt,
                                          boolean likedByMe) {
        return new CommunityPostResponse(postNo, postId, authorId, title, topic, content, splitImages(imageUrls), status, likeCount, commentCount, toInstant(createdAt), likedByMe);
    }

    private CommunityPostResponse mapPost(String postNo, Long postId, Long authorId, String authorName, String authorAvatar, String city,
                                          String title, String topic, String content, String imageUrls, String status,
                                          int likeCount, int commentCount, Timestamp createdAt) {
        return mapPost(postNo, postId, authorId, authorName, authorAvatar, city, title, topic, content, imageUrls, status, likeCount, commentCount, createdAt, false);
    }

    private CommunityPostResponse mapPost(String postNo, Long postId, Long authorId, String authorName, String authorAvatar, String city,
                                          String title, String topic, String content, String imageUrls, String status,
                                          int likeCount, int commentCount, Timestamp createdAt, boolean likedByMe) {
        return new CommunityPostResponse(postNo, postId, authorId, normalizeAuthorName(authorName, authorId), normalizeBlank(authorAvatar), normalizeBlank(city),
                title, topic, content, splitImages(imageUrls), status, likeCount, commentCount, toInstant(createdAt), likedByMe);
    }

    private CommunityPostResponse mapPost(String postNo, Long postId, Long authorId, String authorName, String authorAvatar, String city,
                                          String title, String topic, String content, String imageUrls, String status,
                                          int likeCount, int commentCount, Timestamp createdAt, boolean likedByMe,
                                          Long relatedProductId, String relatedProductTitle, BigDecimal relatedProductPrice) {
        return mapPost(postNo, postId, authorId, authorName, authorAvatar, city, title, topic, content, imageUrls, status, likeCount, commentCount, createdAt, likedByMe, false, relatedProductId, relatedProductTitle, relatedProductPrice);
    }

    private CommunityPostResponse mapPost(String postNo, Long postId, Long authorId, String authorName, String authorAvatar, String city,
                                          String title, String topic, String content, String imageUrls, String status,
                                          int likeCount, int commentCount, Timestamp createdAt, boolean likedByMe, boolean followedByMe,
                                          Long relatedProductId, String relatedProductTitle, BigDecimal relatedProductPrice) {
        return new CommunityPostResponse(postNo, postId, authorId, normalizeAuthorName(authorName, authorId), normalizeBlank(authorAvatar), normalizeBlank(city),
                title, topic, content, splitImages(imageUrls), status, likeCount, commentCount, toInstant(createdAt), likedByMe, followedByMe,
                relatedProductTitle == null ? null : relatedProductId, normalizeBlank(relatedProductTitle), relatedProductPrice);
    }

    private CommunityPostResponse mapAdminPost(String postNo, Long postId, Long authorId, String authorName, String authorAvatar, String city,
                                               String title, String topic, String content, String imageUrls, String status,
                                               int likeCount, int commentCount, Timestamp createdAt, boolean likedByMe,
                                               Long relatedProductId, String relatedProductTitle, BigDecimal relatedProductPrice) {
        return new CommunityPostResponse(postNo, postId, authorId, normalizeAuthorName(authorName, authorId), normalizeBlank(authorAvatar), normalizeBlank(city),
                title, topic, content, splitImages(imageUrls), status, likeCount, commentCount, toInstant(createdAt), likedByMe,
                relatedProductId, normalizeBlank(relatedProductTitle), relatedProductPrice);
    }

    private static String normalizeAuthorName(String authorName, Long authorId) {
        String trimmed = normalizeBlank(authorName);
        return trimmed == null ? "用户" + authorId : trimmed;
    }

    private static String normalizeBlank(String value) {
        String trimmed = value == null ? null : value.trim();
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private static String requireText(String value, String field, int min, int max) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() < min || trimmed.length() > max) {
            throw new IllegalArgumentException("invalid " + field);
        }
        if (containsContactInfo(trimmed)) {
            throw new IllegalArgumentException("contact info is not allowed");
        }
        return trimmed;
    }

    private static boolean containsContactInfo(String text) {
        String lower = text.toLowerCase();
        String compact = lower.replaceAll("[\\s\\p{Punct}，。！？、；：‘’“”（）【】《》]+", "");
        return PHONE_CONTACT_PATTERN.matcher(lower).find()
                || compact.contains("微信")
                || compact.contains("微x")
                || compact.contains("v信")
                || compact.contains("vx")
                || compact.contains("wx")
                || compact.contains("wechat")
                || compact.contains("qq")
                || compact.contains("q号")
                || compact.contains("扣扣")
                || compact.contains("支付宝")
                || compact.contains("支fu宝")
                || compact.contains("alipay")
                || compact.contains("收款码")
                || compact.contains("手机号")
                || compact.contains("电话")
                || compact.contains("tel");
    }

    private CommunityPostResponse findRecentDuplicatePost(Long authorId, String title, String topic, String content, List<String> images, Long relatedProductId) {
        String imageUrls = joinImages(images);
        List<Long> ids = jdbcTemplate.query("""
                SELECT id
                FROM community_post
                WHERE author_id = ?
                  AND title = ?
                  AND topic = ?
                  AND content = ?
                  AND COALESCE(image_urls, '') = COALESCE(?, '')
                  AND COALESCE(related_product_id, -1) = COALESCE(?, -1)
                  AND status = 'PUBLISHED'
                  AND created_at >= ?
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"), authorId, title, topic, content, imageUrls, relatedProductId, duplicateSubmitWindowStart());
        return ids.isEmpty() ? null : loadPostById(ids.get(0));
    }

    private CommunityCommentResponse findRecentDuplicateComment(Long authorId, Long postId, String content) {
        List<CommunityCommentResponse> rows = jdbcTemplate.query("""
                SELECT c.*,
                       COALESCE(NULLIF(a.nickname, ''), NULLIF(a.user_no, ''), CONCAT('用户', c.author_id)) AS author_name,
                       a.avatar_url AS author_avatar
                FROM community_comment c
                JOIN user_account a ON a.id = c.author_id AND a.status = 'ACTIVE'
                WHERE c.post_id = ?
                  AND c.author_id = ?
                  AND c.content = ?
                  AND c.status = 'PUBLISHED'
                  AND c.created_at >= ?
                ORDER BY c.created_at DESC, c.id DESC
                LIMIT 1
                """, (rs, rowNum) -> new CommunityCommentResponse(rs.getString("comment_no"), rs.getLong("author_id"), rs.getString("author_name"), rs.getString("author_avatar"), rs.getString("content"), toInstant(rs.getTimestamp("created_at"))),
                postId, authorId, content, duplicateSubmitWindowStart());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Timestamp duplicateSubmitWindowStart() {
        return Timestamp.valueOf(LocalDateTime.now().minusSeconds(DUPLICATE_SUBMIT_WINDOW_SECONDS));
    }

    private static String nextCommunityNo(String prefix, Long userId) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return prefix + "-" + userId + "-" + System.currentTimeMillis() + "-" + random;
    }

    private static String requireCommunityTopic(String value) {
        String topic = requireText(value, "topic", 2, 32);
        if (!ALLOWED_TOPICS.contains(topic)) {
            throw new IllegalArgumentException("invalid topic");
        }
        return topic;
    }

    private Long normalizeRelatedProductId(Long authorId, Long relatedProductId) {
        if (relatedProductId == null) {
            return null;
        }
        if (authorId == null || authorId <= 0 || relatedProductId <= 0) {
            throw new IllegalArgumentException("invalid related product");
        }
        List<Long> rows = jdbcTemplate.query("""
                SELECT id
                FROM product_item
                WHERE id = ?
                  AND seller_id = ?
                  AND visible = TRUE
                  AND product_status = 'ACTIVE'
                  AND audit_status = 'APPROVED'
                  """ + CERTIFIED_SELLER_RELATED_PRODUCT_FILTER.replace("rp.", "") + """
                """, (rs, rowNum) -> rs.getLong("id"), relatedProductId, authorId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("invalid related product");
        }
        return relatedProductId;
    }

    private static String normalizeOptionalCommunityTopic(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return requireCommunityTopic(value);
    }

    private List<String> sanitizeImages(Long authorId, List<String> imageUrls) {
        if (imageUrls == null) {
            return List.of();
        }
        if (imageUrls.size() > 9) {
            throw new IllegalArgumentException("too many images");
        }
        return imageUrls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .peek(url -> {
                    if (hasInvalidCommunityImageUrl(url)) {
                        throw new IllegalArgumentException("invalid image url");
                    }
                    mediaUploadTicketService.requireUploadedStorageUrl(authorId, "COMMUNITY_IMAGE", url);
                })
                .collect(Collectors.toList());
    }

    private static boolean hasInvalidCommunityImageUrl(String url) {
        if (url == null || !url.startsWith(COMMUNITY_IMAGE_STORAGE_PREFIX)) {
            return true;
        }
        String lower = url.toLowerCase();
        String relativePath = url.substring(COMMUNITY_IMAGE_STORAGE_PREFIX.length());
        return relativePath.isBlank()
                || url.startsWith("local://")
                || url.startsWith("blob:")
                || url.startsWith("data:")
                || lower.contains("placeholder")
                || lower.contains("preview")
                || lower.contains("%2e")
                || lower.contains("%2f")
                || lower.contains("%5c")
                || url.contains("\\")
                || url.contains("..")
                || url.contains("//")
                || Arrays.stream(relativePath.split("/")).anyMatch(String::isBlank);
    }

    private static String joinImages(List<String> imageUrls) {
        return String.join("\n", imageUrls == null ? List.of() : imageUrls);
    }

    private static List<String> splitImages(String imageUrls) {
        if (imageUrls == null || imageUrls.isBlank()) {
            return List.of();
        }
        return Arrays.stream(imageUrls.split("\\n")).filter(s -> !s.isBlank()).collect(Collectors.toList());
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private CommunityCommentResponse mapComment(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CommunityCommentResponse(
                rs.getString("comment_no"),
                rs.getLong("author_id"),
                rs.getString("author_name"),
                rs.getString("author_avatar"),
                rs.getString("content"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("created_at"))
        );
    }

    private void refreshPublishedCommentCount(Long postId) {
        jdbcTemplate.update("""
                update community_post
                set comment_count = (
                    select count(1)
                    from community_comment
                    where post_id = community_post.id and status = 'PUBLISHED'
                ),
                updated_at = CURRENT_TIMESTAMP
                where id = ?
                """, postId);
    }

    private boolean isViewerFollowingAuthor(Long viewerId, Long authorId) {
        if (viewerId == null || viewerId <= 0 || authorId == null || authorId <= 0 || Objects.equals(viewerId, authorId)) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM user_follow
                WHERE follower_id = ?
                  AND followed_id = ?
                """, Integer.class, viewerId, authorId);
        return count != null && count > 0;
    }

    private void notifyPostLiked(CommunityPostResponse post, Long likerId) {
        if (post == null || Objects.equals(post.getAuthorId(), likerId)) {
            return;
        }
        notificationApplicationService.createNotification(
                post.getAuthorId(),
                "LIKE",
                "你的动态收到了点赞",
                displayUserName(likerId) + " 点赞了《" + truncateForNotice(post.getTitle(), 24) + "》",
                "/pages/community/detail/index?postId=" + post.getPostId()
        );
    }

    private void notifyPostCommented(CommunityPostResponse post, Long commenterId, String content) {
        if (post == null || Objects.equals(post.getAuthorId(), commenterId)) {
            return;
        }
        notificationApplicationService.createNotification(
                post.getAuthorId(),
                "COMMENT",
                "你的动态有新评论",
                displayUserName(commenterId) + " 评论：" + truncateForNotice(content, 36),
                "/pages/community/detail/index?postId=" + post.getPostId()
        );
    }

    private String displayUserName(Long userId) {
        List<String> rows = jdbcTemplate.query("""
                SELECT COALESCE(NULLIF(nickname, ''), NULLIF(user_no, ''), CONCAT('用户', id)) AS display_name
                FROM user_account
                WHERE id = ? AND status = 'ACTIVE'
                """, (rs, rowNum) -> rs.getString("display_name"), userId);
        return rows.isEmpty() ? "用户" + userId : rows.get(0);
    }

    private String truncateForNotice(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private void ensureCommunitySchemaCompatibility() {
        ensureColumn("community_post", "related_product_id", "ALTER TABLE community_post ADD COLUMN related_product_id BIGINT");
    }

    private void ensureColumn(String tableName, String columnName, String alterSql) {
        try {
            if (!columnExists(tableName, columnName)) {
                jdbcTemplate.execute(alterSql);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("community schema compatibility check failed: " + tableName + "." + columnName, ex);
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        if (jdbcTemplate.getDataSource() == null) {
            throw new SQLException("dataSource unavailable");
        }
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            return columnExists(metaData, tableName, columnName)
                    || columnExists(metaData, tableName.toUpperCase(), columnName.toUpperCase())
                    || columnExists(metaData, tableName.toLowerCase(), columnName.toLowerCase());
        }
    }

    private boolean columnExists(DatabaseMetaData metaData, String tableName, String columnName) throws SQLException {
        try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
            return columns.next();
        }
    }
}
