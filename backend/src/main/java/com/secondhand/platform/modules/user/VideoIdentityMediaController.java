package com.secondhand.platform.modules.user;

import com.secondhand.platform.modules.media.application.VideoIdentityMediaInspector;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import com.secondhand.platform.shared.web.MediaPathGuard;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/uploads/video-identity")
public class VideoIdentityMediaController {
    private static final String STORAGE_PREFIX = "/uploads/video-identity/";
    private static final List<String> PUBLIC_ROLES = List.of("SELLER", "BOTH");
    private static final List<String> ALLOWED_VIDEO_CONTENT_TYPES = List.of("video/mp4", "video/quicktime", "video/x-m4v");

    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserResolver currentUserResolver;
    private final Path mediaStorageRoot;

    public VideoIdentityMediaController(JdbcTemplate jdbcTemplate,
                                        CurrentUserResolver currentUserResolver,
                                        @Value("${media.storage-root:}") String mediaStorageRoot) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserResolver = currentUserResolver;
        this.mediaStorageRoot = resolveMediaStorageRoot(mediaStorageRoot);
    }

    @GetMapping("/{ownerUserId}/{filename:.+}")
    public ResponseEntity<Resource> readVideoIdentity(@PathVariable Long ownerUserId,
                                                      @PathVariable String filename,
                                                      HttpServletRequest request) {
        String storageUrl = normalizeStorageUrl(ownerUserId, filename);
        Long viewerId = currentUserResolver.resolveOptional(request);
        if (!isPublicApprovedVideo(ownerUserId, storageUrl) && !isOwnPendingVideo(ownerUserId, viewerId, storageUrl)) {
            throw new SecurityException("video identity media forbidden");
        }
        Path mediaPath = verifiedMediaPathFor(storageUrl);
        VideoIdentityMediaInspector.requireValidVideoIdentityMedia(mediaPath);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(resolveContentTypeFor(storageUrl)))
                .contentLength(fileSize(mediaPath))
                .body(new FileSystemResource(mediaPath));
    }

    private boolean isPublicApprovedVideo(Long ownerUserId, String storageUrl) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM user_profile p
                JOIN media_upload_ticket t
                  ON t.owner_user_id = p.user_id
                 AND t.scene = 'VIDEO_IDENTITY'
                 AND t.status = 'UPLOADED'
                 AND t.storage_url = ?
                JOIN audit_record a
                  ON a.id = (
                      SELECT MAX(latest_audit.id)
                      FROM audit_record latest_audit
                      WHERE latest_audit.audit_type = 'VIDEO_IDENTITY'
                        AND latest_audit.user_id = p.user_id
                        AND latest_audit.target_id = CONCAT('', p.user_id)
                        AND latest_audit.status = 'APPROVED'
                  )
                 AND a.audit_type = 'VIDEO_IDENTITY'
                 AND a.user_id = p.user_id
                 AND a.target_id = CONCAT('', p.user_id)
                 AND a.status = 'APPROVED'
                 AND a.reason = t.storage_url
                WHERE p.user_id = ?
                  AND p.video_identity_status = 'APPROVED'
                  AND p.video_verified = TRUE
                  AND UPPER(COALESCE(p.main_role, 'BUYER')) IN ('SELLER', 'BOTH')
                """, Integer.class, storageUrl, ownerUserId);
        return count != null && count > 0;
    }

    private boolean isOwnPendingVideo(Long ownerUserId, Long viewerId, String storageUrl) {
        if (viewerId == null || !viewerId.equals(ownerUserId)) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM user_profile p
                JOIN media_upload_ticket t
                  ON t.owner_user_id = p.user_id
                 AND t.scene = 'VIDEO_IDENTITY'
                 AND t.status = 'UPLOADED'
                 AND t.storage_url = ?
                JOIN audit_record a
                  ON a.audit_type = 'VIDEO_IDENTITY'
                 AND a.user_id = p.user_id
                 AND a.target_id = CONCAT('', p.user_id)
                 AND a.status = 'PENDING'
                 AND a.reason = t.storage_url
                WHERE p.user_id = ?
                  AND p.video_identity_status = 'PENDING'
                  AND p.video_verified = FALSE
                """, Integer.class, storageUrl, ownerUserId);
        return count != null && count > 0;
    }

    private String normalizeStorageUrl(Long ownerUserId, String filename) {
        if (ownerUserId == null || ownerUserId <= 0 || filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("video identity media url invalid");
        }
        String safeFilename = filename.trim();
        String lower = safeFilename.toLowerCase(Locale.ROOT);
        if (safeFilename.contains("/")
                || safeFilename.contains("\\")
                || safeFilename.contains("..")
                || safeFilename.contains("//")
                || lower.contains("%2e")
                || lower.contains("%2f")
                || lower.contains("%5c")
                || lower.contains("preview")
                || lower.contains("demo")
                || lower.contains("mock")
                || lower.contains("sample")
                || lower.contains("placeholder")) {
            throw new IllegalArgumentException("video identity media url invalid");
        }
        return STORAGE_PREFIX + ownerUserId + "/" + safeFilename;
    }

    private Path storagePathFor(String storageUrl) {
        Path uploadsRoot = mediaStorageRoot.resolve("uploads").normalize();
        Path target = mediaStorageRoot.resolve(storageUrl.substring(1)).normalize();
        if (!target.startsWith(uploadsRoot.resolve("video-identity").normalize())) {
            throw new IllegalArgumentException("video identity media url invalid");
        }
        return target;
    }

    private Path verifiedMediaPathFor(String storageUrl) {
        return MediaPathGuard.requireRegularFileInside(
                storagePathFor(storageUrl),
                mediaStorageRoot.resolve("uploads/video-identity"),
                "video identity media url invalid",
                "video identity media not found"
        );
    }

    private Path resolveMediaStorageRoot(String configuredRoot) {
        if (configuredRoot == null || configuredRoot.isBlank()) {
            throw new IllegalStateException("media.storage-root required");
        }
        return Path.of(configuredRoot).toAbsolutePath().normalize();
    }

    private String resolveContentTypeFor(String storageUrl) {
        List<String> rows = jdbcTemplate.query("""
                SELECT content_type
                FROM media_upload_ticket
                WHERE storage_url = ?
                  AND scene = 'VIDEO_IDENTITY'
                  AND status = 'UPLOADED'
                LIMIT 1
                """, (rs, rowNum) -> rs.getString("content_type"), storageUrl);
        if (!rows.isEmpty() && rows.get(0) != null && !rows.get(0).isBlank()) {
            String uploadedContentType = rows.get(0).trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_VIDEO_CONTENT_TYPES.contains(uploadedContentType)) {
                throw new IllegalArgumentException("video identity contentType unsupported");
            }
            return uploadedContentType;
        }
        return contentTypeFor(storageUrl);
    }

    private String contentTypeFor(String storageUrl) {
        String lower = storageUrl.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mov")) {
            return "video/quicktime";
        }
        if (lower.endsWith(".m4v")) {
            return "video/x-m4v";
        }
        return "video/mp4";
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new IllegalArgumentException("video identity media not found", exception);
        }
    }
}
