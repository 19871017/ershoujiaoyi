package com.secondhand.platform.modules.media.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaUploadTicketService {
    private static final long MAX_VIDEO_IDENTITY_SIZE = 80_000_000L;
    private static final long MAX_PRODUCT_IMAGE_SIZE = 10_000_000L;
    private static final long MAX_COMMUNITY_IMAGE_SIZE = 10_000_000L;
    private static final long MAX_EVIDENCE_IMAGE_SIZE = 10_000_000L;
    private static final String SCENE_VIDEO_IDENTITY = "VIDEO_IDENTITY";
    private static final String SCENE_PRODUCT_IMAGE = "PRODUCT_IMAGE";
    private static final String SCENE_COMMUNITY_IMAGE = "COMMUNITY_IMAGE";
    private static final String SCENE_AFTER_SALES_EVIDENCE = "AFTER_SALES_EVIDENCE";
    private static final String SCENE_REPORT_EVIDENCE = "REPORT_EVIDENCE";
    private static final String SCENE_CHAT_IMAGE = "CHAT_IMAGE";

    private final JdbcTemplate jdbcTemplate;

    public MediaUploadTicketService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public MediaUploadTicketResponse issue(Long userId, String scene, String contentType, Long fileSize, String filename) {
        validateUserId(userId);
        String safeScene = requireText(scene, "upload scene required").toUpperCase(Locale.ROOT);
        String safeContentType = requireText(contentType, "contentType required").toLowerCase(Locale.ROOT);
        String safeFilename = sanitizeFilename(filename);
        validateSceneAndMedia(safeScene, safeContentType, fileSize, safeFilename);

        String ext = extensionFor(safeContentType, safeFilename);
        String ticketNo = "UT-" + safeScene.substring(0, Math.min(3, safeScene.length())) + '-' + System.currentTimeMillis() + '-' + Math.abs((int) (Math.random() * 100000));
        String uploadToken = UUID.randomUUID() + "-" + UUID.randomUUID();
        String storageDir = switch (safeScene) {
            case SCENE_PRODUCT_IMAGE -> "/uploads/product-image/";
            case SCENE_COMMUNITY_IMAGE -> "/uploads/community-image/";
            case SCENE_AFTER_SALES_EVIDENCE -> "/uploads/evidence/after-sales/";
            case SCENE_REPORT_EVIDENCE -> "/uploads/evidence/report/";
            case SCENE_CHAT_IMAGE -> "/uploads/chat-image/";
            default -> "/uploads/video-identity/";
        };
        String storageUrl = storageDir + userId + "/" + ticketNo + ext;
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);
        jdbcTemplate.update("""
                insert into media_upload_ticket (ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at)
                values (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?)
                """, ticketNo, userId, safeScene, safeFilename, safeContentType, fileSize, storageUrl, sha256(uploadToken), "ISSUED", Timestamp.valueOf(expiresAt));
        return new MediaUploadTicketResponse(ticketNo, userId, safeScene, safeContentType, fileSize, storageUrl, uploadToken, "ISSUED", expiresAt);
    }

    public MediaUploadTicketResponse requireIssuedStorageUrl(Long userId, String scene, String storageUrl) {
        validateUserId(userId);
        String safeScene = requireText(scene, "upload scene required").toUpperCase(Locale.ROOT);
        String safeStorageUrl = requireText(storageUrl, "storageUrl required");
        if (!safeStorageUrl.startsWith("/uploads/")) {
            throw new IllegalArgumentException("storageUrl must be issued by upload ticket");
        }
        try {
            return jdbcTemplate.queryForObject("""
                    select ticket_no, owner_user_id, scene, content_type, file_size, storage_url, status, expires_at
                    from media_upload_ticket
                    where owner_user_id = ? and scene = ? and storage_url = ? and status = 'ISSUED' and expires_at > CURRENT_TIMESTAMP
                    """, (rs, rowNum) -> new MediaUploadTicketResponse(
                    rs.getString("ticket_no"),
                    rs.getLong("owner_user_id"),
                    rs.getString("scene"),
                    rs.getString("content_type"),
                    rs.getLong("file_size"),
                    rs.getString("storage_url"),
                    null,
                    rs.getString("status"),
                    toLocalDateTime(rs.getTimestamp("expires_at"))
            ), userId, safeScene, safeStorageUrl);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("upload ticket not found");
        }
    }

    private void validateSceneAndMedia(String scene, String contentType, Long fileSize, String filename) {
        if (!List.of(SCENE_VIDEO_IDENTITY, SCENE_PRODUCT_IMAGE, SCENE_COMMUNITY_IMAGE, SCENE_AFTER_SALES_EVIDENCE, SCENE_REPORT_EVIDENCE, SCENE_CHAT_IMAGE).contains(scene)) {
            throw new IllegalArgumentException("unsupported media scene");
        }
        if (SCENE_VIDEO_IDENTITY.equals(scene)) {
            List<String> allowedTypes = List.of("video/mp4", "video/quicktime", "video/x-m4v");
            if (!allowedTypes.contains(contentType)) {
                throw new IllegalArgumentException("video identity contentType unsupported");
            }
            if (fileSize == null || fileSize <= 0 || fileSize > MAX_VIDEO_IDENTITY_SIZE) {
                throw new IllegalArgumentException("video identity fileSize invalid");
            }
        }
        if (List.of(SCENE_PRODUCT_IMAGE, SCENE_COMMUNITY_IMAGE, SCENE_AFTER_SALES_EVIDENCE, SCENE_REPORT_EVIDENCE, SCENE_CHAT_IMAGE).contains(scene)) {
            List<String> allowedTypes = List.of("image/jpeg", "image/png", "image/webp");
            if (!allowedTypes.contains(contentType)) {
                throw new IllegalArgumentException("image content type invalid");
            }
            long maxSize = switch (scene) {
                case SCENE_COMMUNITY_IMAGE -> MAX_COMMUNITY_IMAGE_SIZE;
                case SCENE_PRODUCT_IMAGE -> MAX_PRODUCT_IMAGE_SIZE;
                default -> MAX_EVIDENCE_IMAGE_SIZE;
            };
            if (fileSize == null || fileSize <= 0 || fileSize > maxSize) {
                throw new IllegalArgumentException("image size invalid");
            }
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.contains("..") || lower.contains("/") || lower.contains("\\") || lower.contains("placeholder") || lower.contains("preview")) {
            throw new IllegalArgumentException("filename invalid");
        }
    }

    private String sanitizeFilename(String filename) {
        String safe = requireText(filename, "filename required");
        return safe.trim();
    }

    private String extensionFor(String contentType, String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mov")) {
            return ".mov";
        }
        if (lower.endsWith(".m4v")) {
            return ".m4v";
        }
        if (lower.endsWith(".png")) {
            return ".png";
        }
        if (lower.endsWith(".webp")) {
            return ".webp";
        }
        if (contentType.startsWith("image/")) {
            return ".jpg";
        }
        return ".mp4";
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId required");
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("sha256 unavailable", e);
        }
    }
}
