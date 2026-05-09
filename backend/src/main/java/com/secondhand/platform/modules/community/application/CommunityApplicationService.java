package com.secondhand.platform.modules.community.application;

import com.secondhand.platform.modules.media.application.MediaUploadTicketService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityApplicationService {
    private static final int MAX_LIST_LIMIT = 50;
    private final JdbcTemplate jdbcTemplate;
    private final MediaUploadTicketService mediaUploadTicketService;

    public CommunityApplicationService(JdbcTemplate jdbcTemplate, MediaUploadTicketService mediaUploadTicketService) {
        this.jdbcTemplate = jdbcTemplate;
        this.mediaUploadTicketService = mediaUploadTicketService;
    }

    @Transactional
    public CommunityPostResponse createPost(Long authorId, CreateCommunityPostRequest request) {
        if (authorId == null || authorId <= 0) {
            throw new IllegalArgumentException("invalid author");
        }
        String title = requireText(request == null ? null : request.getTitle(), "title", 4, 64);
        String topic = requireText(request.getTopic(), "topic", 2, 32);
        String content = requireText(request.getContent(), "content", 8, 1000);
        List<String> images = sanitizeImages(authorId, request.getImageUrls());
        String postNo = "POST-" + authorId + "-" + System.currentTimeMillis();
        jdbcTemplate.update("INSERT INTO community_post(post_no, author_id, title, topic, content, image_urls, status, like_count, comment_count, created_at, updated_at) VALUES(?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                postNo, authorId, title, topic, content, joinImages(images), "PUBLISHED", 0, 0);
        Long postId = jdbcTemplate.queryForObject("SELECT id FROM community_post WHERE post_no = ?", Long.class, postNo);
        return loadPostById(postId);
    }

    public List<CommunityPostResponse> listPublishedPosts(int limit) {
        int capped = Math.max(1, Math.min(limit <= 0 ? 20 : limit, MAX_LIST_LIMIT));
        return jdbcTemplate.query("SELECT * FROM community_post WHERE status = 'PUBLISHED' ORDER BY created_at DESC, id DESC LIMIT ?",
                (rs, rowNum) -> mapPost(rs.getString("post_no"), rs.getLong("id"), rs.getLong("author_id"), rs.getString("title"), rs.getString("topic"), rs.getString("content"), rs.getString("image_urls"), rs.getString("status"), rs.getInt("like_count"), rs.getInt("comment_count"), rs.getTimestamp("created_at")), capped);
    }

    public CommunityPostDetailResponse detail(String postIdOrNo, Long currentUserId) {
        if (postIdOrNo == null || postIdOrNo.isBlank() || "preview".equalsIgnoreCase(postIdOrNo) || "UNKNOWN".equalsIgnoreCase(postIdOrNo)) {
            throw new IllegalArgumentException("invalid post id");
        }
        Long id;
        if (postIdOrNo.matches("\\d+")) {
            id = Long.parseLong(postIdOrNo);
        } else {
            List<Long> ids = jdbcTemplate.query("SELECT id FROM community_post WHERE post_no = ?", (rs, rowNum) -> rs.getLong("id"), postIdOrNo);
            if (ids.isEmpty()) {
                throw new IllegalArgumentException("post not found");
            }
            id = ids.get(0);
        }
        return detail(id, currentUserId);
    }

    public CommunityPostDetailResponse detail(Long postId, Long currentUserId) {
        CommunityPostResponse post = loadPublishedPostById(postId);
        List<CommunityCommentResponse> comments = jdbcTemplate.query("SELECT * FROM community_comment WHERE post_id = ? AND status = 'PUBLISHED' ORDER BY created_at ASC, id ASC",
                (rs, rowNum) -> new CommunityCommentResponse(rs.getString("comment_no"), rs.getLong("author_id"), rs.getString("content"), toInstant(rs.getTimestamp("created_at"))), postId);
        boolean liked = currentUserId != null && currentUserId > 0 && jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM community_like WHERE post_id = ? AND user_id = ?", Integer.class, postId, currentUserId) > 0;
        return new CommunityPostDetailResponse(post.getPostNo(), post.getPostId(), post.getAuthorId(), post.getTitle(), post.getTopic(), post.getContent(),
                post.getImageUrls(), post.getStatus(), post.getLikeCount(), post.getCommentCount(), post.getCreatedAt(), liked, comments);
    }

    @Transactional
    public CommunityCommentResponse addComment(Long authorId, Long postId, CreateCommunityCommentRequest request) {
        if (authorId == null || authorId <= 0) {
            throw new IllegalArgumentException("invalid author");
        }
        loadPublishedPostById(postId);
        String content = requireText(request == null ? null : request.getContent(), "content", 4, 500);
        String commentNo = "CMT-" + authorId + "-" + System.currentTimeMillis();
        jdbcTemplate.update("INSERT INTO community_comment(comment_no, post_id, author_id, content, status, created_at) VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)",
                commentNo, postId, authorId, content, "PUBLISHED");
        jdbcTemplate.update("UPDATE community_post SET comment_count = comment_count + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?", postId);
        return jdbcTemplate.queryForObject("SELECT * FROM community_comment WHERE comment_no = ?",
                (rs, rowNum) -> new CommunityCommentResponse(rs.getString("comment_no"), rs.getLong("author_id"), rs.getString("content"), toInstant(rs.getTimestamp("created_at"))), commentNo);
    }

    @Transactional
    public CommunityPostDetailResponse likePost(Long userId, Long postId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("invalid user");
        }
        loadPublishedPostById(postId);
        int inserted = jdbcTemplate.update("INSERT INTO community_like(post_id, user_id, created_at) SELECT ?, ?, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM community_like WHERE post_id = ? AND user_id = ?)",
                postId, userId, postId, userId);
        if (inserted > 0) {
            jdbcTemplate.update("UPDATE community_post SET like_count = like_count + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?", postId);
        }
        return detail(postId, userId);
    }

    @Transactional
    public CommunityPostDetailResponse unlikePost(Long userId, Long postId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("invalid user");
        }
        loadPublishedPostById(postId);
        int deleted = jdbcTemplate.update("DELETE FROM community_like WHERE post_id = ? AND user_id = ?", postId, userId);
        if (deleted > 0) {
            jdbcTemplate.update("UPDATE community_post SET like_count = CASE WHEN like_count > 0 THEN like_count - 1 ELSE 0 END, updated_at = CURRENT_TIMESTAMP WHERE id = ?", postId);
        }
        return detail(postId, userId);
    }

    private CommunityPostResponse loadPostById(Long postId) {
        List<CommunityPostResponse> posts = jdbcTemplate.query("SELECT * FROM community_post WHERE id = ?",
                (rs, rowNum) -> mapPost(rs.getString("post_no"), rs.getLong("id"), rs.getLong("author_id"), rs.getString("title"), rs.getString("topic"), rs.getString("content"), rs.getString("image_urls"), rs.getString("status"), rs.getInt("like_count"), rs.getInt("comment_count"), rs.getTimestamp("created_at")), postId);
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

    private CommunityPostResponse mapPost(String postNo, Long postId, Long authorId, String title, String topic, String content,
                                          String imageUrls, String status, int likeCount, int commentCount, Timestamp createdAt) {
        return new CommunityPostResponse(postNo, postId, authorId, title, topic, content, splitImages(imageUrls), status, likeCount, commentCount, toInstant(createdAt));
    }

    private static String requireText(String value, String field, int min, int max) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() < min || trimmed.length() > max) {
            throw new IllegalArgumentException("invalid " + field);
        }
        if (trimmed.contains("微信") || trimmed.contains("手机号") || trimmed.contains("支付宝")) {
            throw new IllegalArgumentException("contact info is not allowed");
        }
        return trimmed;
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
                    String lower = url.toLowerCase();
                    if (url.startsWith("local://") || lower.contains("placeholder") || lower.contains("preview")) {
                        throw new IllegalArgumentException("invalid image url");
                    }
                    mediaUploadTicketService.requireIssuedStorageUrl(authorId, "COMMUNITY_IMAGE", url);
                })
                .collect(Collectors.toList());
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
}
