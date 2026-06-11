package com.secondhand.platform.modules.media.application;

import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaUploadTicketService {
    private static final long MAX_VIDEO_IDENTITY_SIZE = 80_000_000L;
    private static final long MAX_PRODUCT_IMAGE_SIZE = 10_000_000L;
    private static final long MAX_COMMUNITY_IMAGE_SIZE = 10_000_000L;
    private static final long MAX_EVIDENCE_IMAGE_SIZE = 10_000_000L;
    private static final long MAX_CHAT_VOICE_SIZE = 10_000_000L;
    private static final long MAX_CHAT_VIDEO_SIZE = 80_000_000L;
    private static final String SCENE_VIDEO_IDENTITY = "VIDEO_IDENTITY";
    private static final String SCENE_PRODUCT_IMAGE = "PRODUCT_IMAGE";
    private static final String SCENE_COMMUNITY_IMAGE = "COMMUNITY_IMAGE";
    private static final String SCENE_AFTER_SALES_EVIDENCE = "AFTER_SALES_EVIDENCE";
    private static final String SCENE_REPORT_EVIDENCE = "REPORT_EVIDENCE";
    private static final String SCENE_CHAT_IMAGE = "CHAT_IMAGE";
    private static final String SCENE_CHAT_VOICE = "CHAT_VOICE";
    private static final String SCENE_CHAT_VIDEO = "CHAT_VIDEO";

    private final JdbcTemplate jdbcTemplate;
    private final Path storageRoot;

    public MediaUploadTicketService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Path.of(System.getProperty("java.io.tmpdir"), "xiaoyuanquan-media-test").toString());
    }

    @Autowired
    public MediaUploadTicketService(JdbcTemplate jdbcTemplate, @Value("${media.storage-root:}") String storageRoot) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageRoot = resolveStorageRoot(storageRoot);
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
            case SCENE_REPORT_EVIDENCE -> "/uploads/report-evidence/";
            case SCENE_CHAT_IMAGE -> "/uploads/chat-image/";
            case SCENE_CHAT_VOICE -> "/uploads/chat-voice/";
            case SCENE_CHAT_VIDEO -> "/uploads/chat-video/";
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
        return requireStorageUrl(userId, scene, storageUrl, List.of("ISSUED", "UPLOADED"), true);
    }

    public MediaUploadTicketResponse requireUploadedStorageUrl(Long userId, String scene, String storageUrl) {
        return requireStorageUrl(userId, scene, storageUrl, List.of("UPLOADED"), false);
    }

    @Transactional
    public MediaUploadTicketResponse storeUploadedFile(Long userId, String ticketNo, String uploadToken, MultipartFile file) {
        validateUserId(userId);
        String safeTicketNo = requireText(ticketNo, "ticketNo required");
        String safeUploadToken = requireText(uploadToken, "uploadToken required");
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("upload file required");
        }
        UploadTicketRow ticket = loadIssuedTicketForUpload(userId, safeTicketNo);
        if (!ticket.uploadTokenHash().equals(sha256(safeUploadToken))) {
            throw new IllegalArgumentException("upload token invalid");
        }
        String uploadedContentType = requireText(file.getContentType(), "upload file contentType required").toLowerCase(Locale.ROOT);
        String uploadedFilename = requireText(file.getOriginalFilename(), "upload filename required");
        if (!uploadedContentType.equals(ticket.contentType())) {
            throw new IllegalArgumentException("upload file contentType mismatch");
        }
        if (file.getSize() <= 0 || file.getSize() > ticket.fileSize()) {
            throw new IllegalArgumentException("upload file size invalid");
        }
        validateSceneAndMedia(ticket.scene(), uploadedContentType, file.getSize(), uploadedFilename);
        Path target = storagePathFor(ticket.storageUrl());
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            if (SCENE_VIDEO_IDENTITY.equals(ticket.scene())) {
                VideoIdentityMediaInspector.requireValidVideoIdentityMedia(target);
            }
            if (isImageScene(ticket.scene())) {
                requireValidImageMedia(target, uploadedContentType);
            }
            if (SCENE_CHAT_VOICE.equals(ticket.scene())) {
                requireValidVoiceMedia(target, uploadedContentType);
            }
            if (SCENE_CHAT_VIDEO.equals(ticket.scene())) {
                requireValidChatVideoMedia(target, uploadedContentType);
            }
        } catch (IOException e) {
            throw new IllegalStateException("upload file save failed", e);
        } catch (IllegalArgumentException e) {
            deleteInvalidUpload(target);
            throw e;
        }
        int changed = jdbcTemplate.update("update media_upload_ticket set status = ? where ticket_no = ? and owner_user_id = ? and status = ?", "UPLOADED", safeTicketNo, userId, "ISSUED");
        if (changed != 1) {
            throw new IllegalStateException("upload ticket status update failed");
        }
        return requireUploadedStorageUrl(userId, ticket.scene(), ticket.storageUrl());
    }

    public double requireUploadedVideoIdentityMedia(Long userId, String storageUrl) {
        requireUploadedStorageUrl(userId, SCENE_VIDEO_IDENTITY, storageUrl);
        return VideoIdentityMediaInspector.requireValidVideoIdentityMedia(storagePathFor(storageUrl));
    }

    private MediaUploadTicketResponse requireStorageUrl(Long userId, String scene, String storageUrl, List<String> statuses, boolean requireUnexpiredIssuedTicket) {
        validateUserId(userId);
        String safeScene = requireText(scene, "upload scene required").toUpperCase(Locale.ROOT);
        String safeStorageUrl = requireText(storageUrl, "storageUrl required");
        if (!safeStorageUrl.startsWith("/uploads/")) {
            throw new IllegalArgumentException("storageUrl must be issued by upload ticket");
        }
        try {
            String placeholders = String.join(",", statuses.stream().map(status -> "?").toList());
            List<Object> args = new ArrayList<>(List.of(userId, safeScene, safeStorageUrl));
            args.addAll(statuses);
            String expiryClause = requireUnexpiredIssuedTicket ? "and (status <> 'ISSUED' or expires_at > CURRENT_TIMESTAMP)" : "";
            return jdbcTemplate.queryForObject("""
                    select ticket_no, owner_user_id, scene, content_type, file_size, storage_url, status, expires_at
                    from media_upload_ticket
                    where owner_user_id = ? and scene = ? and storage_url = ? and status in (%s) %s
                    """.formatted(placeholders, expiryClause), (rs, rowNum) -> new MediaUploadTicketResponse(
                    rs.getString("ticket_no"),
                    rs.getLong("owner_user_id"),
                    rs.getString("scene"),
                    rs.getString("content_type"),
                    rs.getLong("file_size"),
                    rs.getString("storage_url"),
                    null,
                    rs.getString("status"),
                    toLocalDateTime(rs.getTimestamp("expires_at"))
            ), args.toArray());
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("upload ticket not found");
        }
    }

    private UploadTicketRow loadIssuedTicketForUpload(Long userId, String ticketNo) {
        try {
            return jdbcTemplate.queryForObject("""
                    select scene, content_type, file_size, storage_url, upload_token_hash
                    from media_upload_ticket
                    where owner_user_id = ? and ticket_no = ? and status = 'ISSUED' and expires_at > CURRENT_TIMESTAMP
                    """, (rs, rowNum) -> new UploadTicketRow(
                    rs.getString("scene"),
                    rs.getString("content_type"),
                    rs.getLong("file_size"),
                    rs.getString("storage_url"),
                    rs.getString("upload_token_hash")
            ), userId, ticketNo);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("upload ticket not found");
        }
    }

    private Path storagePathFor(String storageUrl) {
        String safeStorageUrl = requireText(storageUrl, "storageUrl required");
        if (!safeStorageUrl.startsWith("/uploads/")) {
            throw new IllegalArgumentException("storageUrl invalid");
        }
        Path uploadsRoot = storageRoot.resolve("uploads").normalize();
        Path target = storageRoot.resolve(safeStorageUrl.substring(1)).normalize();
        if (!target.startsWith(uploadsRoot)) {
            throw new IllegalArgumentException("storageUrl invalid");
        }
        return target;
    }

    private void deleteInvalidUpload(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // The ticket remains non-UPLOADED, so a leftover file cannot be used by business flows.
        }
    }

    private Path resolveStorageRoot(String configuredRoot) {
        if (configuredRoot == null || configuredRoot.isBlank()) {
            throw new IllegalStateException("media.storage-root required");
        }
        return Path.of(configuredRoot).toAbsolutePath().normalize();
    }

    private void validateSceneAndMedia(String scene, String contentType, Long fileSize, String filename) {
        if (!List.of(SCENE_VIDEO_IDENTITY, SCENE_PRODUCT_IMAGE, SCENE_COMMUNITY_IMAGE, SCENE_AFTER_SALES_EVIDENCE, SCENE_REPORT_EVIDENCE, SCENE_CHAT_IMAGE, SCENE_CHAT_VOICE, SCENE_CHAT_VIDEO).contains(scene)) {
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
        if (isImageScene(scene)) {
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
        if (SCENE_CHAT_VOICE.equals(scene)) {
            List<String> allowedTypes = List.of("audio/webm", "audio/mp4", "audio/mpeg", "audio/wav", "audio/aac", "audio/x-m4a");
            if (!allowedTypes.contains(contentType)) {
                throw new IllegalArgumentException("voice content type invalid");
            }
            if (fileSize == null || fileSize <= 0 || fileSize > MAX_CHAT_VOICE_SIZE) {
                throw new IllegalArgumentException("voice size invalid");
            }
        }
        if (SCENE_CHAT_VIDEO.equals(scene)) {
            List<String> allowedTypes = List.of("video/mp4", "video/quicktime", "video/x-m4v", "video/webm");
            if (!allowedTypes.contains(contentType)) {
                throw new IllegalArgumentException("chat video content type invalid");
            }
            if (fileSize == null || fileSize <= 0 || fileSize > MAX_CHAT_VIDEO_SIZE) {
                throw new IllegalArgumentException("chat video size invalid");
            }
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.contains("..") || lower.contains("/") || lower.contains("\\") || lower.contains("placeholder") || lower.contains("preview")) {
            throw new IllegalArgumentException("filename invalid");
        }
    }

    private boolean isImageScene(String scene) {
        return List.of(SCENE_PRODUCT_IMAGE, SCENE_COMMUNITY_IMAGE, SCENE_AFTER_SALES_EVIDENCE, SCENE_REPORT_EVIDENCE, SCENE_CHAT_IMAGE).contains(scene);
    }

    private void requireValidImageMedia(Path mediaPath, String contentType) {
        try {
            if ("image/webp".equals(contentType)) {
                requireValidWebpMedia(mediaPath);
                return;
            }
            try (ImageInputStream stream = ImageIO.createImageInputStream(mediaPath.toFile())) {
                if (stream == null) {
                    throw new IllegalArgumentException("image media invalid");
                }
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) {
                    throw new IllegalArgumentException("image media invalid");
                }
                ImageReader reader = readers.next();
                try {
                    reader.setInput(stream, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    String formatName = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (width <= 0 || height <= 0 || !imageFormatMatches(contentType, formatName)) {
                        throw new IllegalArgumentException("image media invalid");
                    }
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("image media invalid", exception);
        }
    }

    private boolean imageFormatMatches(String contentType, String formatName) {
        if ("image/jpeg".equals(contentType)) {
            return "jpeg".equals(formatName) || "jpg".equals(formatName);
        }
        if ("image/png".equals(contentType)) {
            return "png".equals(formatName);
        }
        return false;
    }

    private void requireValidWebpMedia(Path mediaPath) throws IOException {
        byte[] header = new byte[30];
        try (InputStream inputStream = Files.newInputStream(mediaPath)) {
            int read = inputStream.readNBytes(header, 0, header.length);
            if (read < 30
                    || !ascii(header, 0, 4).equals("RIFF")
                    || !ascii(header, 8, 4).equals("WEBP")
                    || (!ascii(header, 12, 4).equals("VP8 ") && !ascii(header, 12, 4).equals("VP8L") && !ascii(header, 12, 4).equals("VP8X"))) {
                throw new IllegalArgumentException("image media invalid");
            }
        }
    }

    private String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }

    private void requireValidVoiceMedia(Path mediaPath, String contentType) {
        try (InputStream inputStream = Files.newInputStream(mediaPath)) {
            byte[] header = inputStream.readNBytes(64);
            if (!voiceHeaderMatches(contentType, header)) {
                throw new IllegalArgumentException("voice media invalid");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("voice media invalid", exception);
        }
    }

    private boolean voiceHeaderMatches(String contentType, byte[] header) {
        if (header == null || header.length < 4) {
            return false;
        }
        return switch (contentType) {
            case "audio/webm" -> isEbml(header);
            case "audio/mp4", "audio/x-m4a", "audio/aac" -> isMp4LikeAudio(header) || isAdtsAac(header);
            case "audio/mpeg" -> isMp3(header);
            case "audio/wav" -> isWav(header);
            default -> false;
        };
    }

    private void requireValidChatVideoMedia(Path mediaPath, String contentType) {
        try (InputStream inputStream = Files.newInputStream(mediaPath)) {
            byte[] header = inputStream.readNBytes(64);
            if (!chatVideoHeaderMatches(contentType, header)) {
                throw new IllegalArgumentException("chat video media invalid");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("chat video media invalid", exception);
        }
    }

    private boolean chatVideoHeaderMatches(String contentType, byte[] header) {
        if (header == null || header.length < 4) {
            return false;
        }
        return switch (contentType) {
            case "video/webm" -> isEbml(header);
            case "video/mp4", "video/quicktime", "video/x-m4v" -> isMp4LikeVideo(header);
            default -> false;
        };
    }

    private boolean isEbml(byte[] header) {
        return header.length >= 4
                && (header[0] & 0xFF) == 0x1A
                && (header[1] & 0xFF) == 0x45
                && (header[2] & 0xFF) == 0xDF
                && (header[3] & 0xFF) == 0xA3;
    }

    private boolean isMp4LikeAudio(byte[] header) {
        return header.length >= 12
                && "ftyp".equals(ascii(header, 4, 4));
    }

    private boolean isMp4LikeVideo(byte[] header) {
        return header.length >= 12
                && "ftyp".equals(ascii(header, 4, 4));
    }

    private boolean isAdtsAac(byte[] header) {
        return header.length >= 2
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xF0) == 0xF0;
    }

    private boolean isMp3(byte[] header) {
        return header.length >= 3
                && (("ID3".equals(ascii(header, 0, 3)))
                || ((header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0));
    }

    private boolean isWav(byte[] header) {
        return header.length >= 12
                && "RIFF".equals(ascii(header, 0, 4))
                && "WAVE".equals(ascii(header, 8, 4));
    }

    private String sanitizeFilename(String filename) {
        String safe = requireText(filename, "filename required");
        return safe.trim();
    }

    private String extensionFor(String contentType, String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (contentType.startsWith("video/")) {
            if (lower.endsWith(".webm")) {
                return ".webm";
            }
            if (lower.endsWith(".mov")) {
                return ".mov";
            }
            if (lower.endsWith(".m4v")) {
                return ".m4v";
            }
            if (contentType.equals("video/webm")) {
                return ".webm";
            }
            if (contentType.equals("video/quicktime")) {
                return ".mov";
            }
            if (contentType.equals("video/x-m4v")) {
                return ".m4v";
            }
            return ".mp4";
        }
        if (contentType.startsWith("audio/")) {
            if (lower.endsWith(".webm")) {
                return ".webm";
            }
            if (lower.endsWith(".m4a")) {
                return ".m4a";
            }
            if (lower.endsWith(".mp3")) {
                return ".mp3";
            }
            if (lower.endsWith(".wav")) {
                return ".wav";
            }
            if (lower.endsWith(".aac")) {
                return ".aac";
            }
            if (contentType.equals("audio/webm")) {
                return ".webm";
            }
            if (contentType.equals("audio/mpeg")) {
                return ".mp3";
            }
            if (contentType.equals("audio/wav")) {
                return ".wav";
            }
            if (contentType.equals("audio/aac")) {
                return ".aac";
            }
            return ".m4a";
        }
        if (contentType.startsWith("image/")) {
            if (lower.endsWith(".png")) {
                return ".png";
            }
            if (lower.endsWith(".webp")) {
                return ".webp";
            }
            return ".jpg";
        }
        if (lower.endsWith(".mov")) {
            return ".mov";
        }
        if (lower.endsWith(".m4v")) {
            return ".m4v";
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

    private record UploadTicketRow(
            String scene,
            String contentType,
            Long fileSize,
            String storageUrl,
            String uploadTokenHash
    ) {
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
