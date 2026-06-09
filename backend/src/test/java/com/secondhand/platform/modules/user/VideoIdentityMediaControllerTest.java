package com.secondhand.platform.modules.user;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.secondhand.platform.shared.web.CurrentUserResolver;
import com.secondhand.platform.shared.web.GlobalExceptionHandler;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class VideoIdentityMediaControllerTest {
    private JdbcTemplate jdbcTemplate;
    private MockMvc mvc;
    @TempDir
    Path mediaRoot;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        mvc = MockMvcBuilders.standaloneSetup(new VideoIdentityMediaController(
                        jdbcTemplate,
                        new CurrentUserResolver(jdbcTemplate, environment),
                        mediaRoot.toString()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void approvedSellerVideoIdentityMediaShouldBePubliclyReadable() throws Exception {
        long sellerId = 101L;
        String storageUrl = "/uploads/video-identity/" + sellerId + "/approved.mp4";
        insertActiveUser(sellerId);
        upsertProfile(sellerId, "SELLER", "APPROVED", true);
        insertUploadedVideoAudit("AUDIT-VIDEO-APPROVED", sellerId, storageUrl, "APPROVED");
        writeValidVideoFile(storageUrl);

        mvc.perform(get(storageUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().contentType("video/mp4"));
    }

    @Test
    void approvedSellerVideoIdentityMediaUsesAllowedUploadedTicketContentType() throws Exception {
        long sellerId = 106L;
        String storageUrl = "/uploads/video-identity/" + sellerId + "/approved-mov.mov";
        insertActiveUser(sellerId);
        upsertProfile(sellerId, "SELLER", "APPROVED", true);
        insertUploadedVideoAudit("AUDIT-VIDEO-MOV-TICKET", sellerId, storageUrl, "APPROVED", "video/quicktime");
        writeValidVideoFile(storageUrl);

        mvc.perform(get(storageUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().contentType("video/quicktime"));
    }

    @Test
    void approvedSellerVideoIdentityMediaRejectsUnsupportedUploadedTicketContentType() throws Exception {
        long sellerId = 109L;
        String storageUrl = "/uploads/video-identity/" + sellerId + "/approved-webm.mp4";
        insertActiveUser(sellerId);
        upsertProfile(sellerId, "SELLER", "APPROVED", true);
        insertUploadedVideoAudit("AUDIT-VIDEO-WEBM-TICKET", sellerId, storageUrl, "APPROVED", "video/webm");
        writeValidVideoFile(storageUrl);

        mvc.perform(get(storageUrl))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("bad request")));
    }

    @Test
    void approvedSellerVideoIdentityMediaShouldRejectSymlinkEscape() throws Exception {
        long sellerId = 110L;
        String storageUrl = "/uploads/video-identity/" + sellerId + "/approved-symlink.mp4";
        insertActiveUser(sellerId);
        upsertProfile(sellerId, "SELLER", "APPROVED", true);
        insertUploadedVideoAudit("AUDIT-VIDEO-SYMLINK", sellerId, storageUrl, "APPROVED");
        Path videoPath = mediaRoot.resolve(storageUrl.substring(1)).normalize();
        createSymlinkToOutsideFile(videoPath, "outside-video");

        mvc.perform(get(storageUrl))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("bad request")));
    }

    @Test
    void oldApprovedVideoIdentityMediaShouldFailClosedAfterNewerApproval() throws Exception {
        long sellerId = 107L;
        String oldStorageUrl = "/uploads/video-identity/" + sellerId + "/old-approved.mp4";
        String latestStorageUrl = "/uploads/video-identity/" + sellerId + "/latest-approved.mp4";
        insertActiveUser(sellerId);
        upsertProfile(sellerId, "SELLER", "APPROVED", true);
        insertUploadedVideoAudit("AUDIT-VIDEO-OLD", sellerId, oldStorageUrl, "APPROVED");
        insertUploadedVideoAudit("AUDIT-VIDEO-LATEST", sellerId, latestStorageUrl, "APPROVED");
        writeValidVideoFile(oldStorageUrl);
        writeValidVideoFile(latestStorageUrl);

        mvc.perform(get(oldStorageUrl))
                .andExpect(status().isForbidden());

        mvc.perform(get(latestStorageUrl))
                .andExpect(status().isOk());
    }

    @Test
    void pendingVideoIdentityMediaShouldOnlyBeReadableByOwner() throws Exception {
        long sellerId = 102L;
        String storageUrl = "/uploads/video-identity/" + sellerId + "/pending.webm";
        insertActiveUser(sellerId);
        insertActiveUser(103L);
        upsertProfile(sellerId, "SELLER", "PENDING", false);
        insertUploadedVideoAudit("AUDIT-VIDEO-PENDING", sellerId, storageUrl, "PENDING");
        writeValidVideoFile(storageUrl);

        mvc.perform(get(storageUrl))
                .andExpect(status().isForbidden());

        mvc.perform(get(storageUrl)
                        .header("X-User-Id", "103")
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isForbidden());

        mvc.perform(get(storageUrl)
                        .header("X-User-Id", String.valueOf(sellerId))
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("video/mp4"));
    }

    @Test
    void rejectedOrDirtyVideoIdentityMediaShouldFailClosed() throws Exception {
        long sellerId = 104L;
        String rejectedUrl = "/uploads/video-identity/" + sellerId + "/rejected.mp4";
        insertActiveUser(sellerId);
        upsertProfile(sellerId, "SELLER", "REJECTED", false);
        insertUploadedVideoAudit("AUDIT-VIDEO-REJECTED", sellerId, rejectedUrl, "REJECTED");
        writeValidVideoFile(rejectedUrl);

        mvc.perform(get(rejectedUrl)
                        .header("X-User-Id", String.valueOf(sellerId))
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/uploads/video-identity/{ownerUserId}/{filename}", sellerId, "placeholder-demo.mp4")
                        .header("X-User-Id", String.valueOf(sellerId))
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approvedDirtyVideoIdentityMediaShouldFailClosedOnRead() throws Exception {
        long sellerId = 108L;
        String storageUrl = "/uploads/video-identity/" + sellerId + "/dirty.mp4";
        insertActiveUser(sellerId);
        upsertProfile(sellerId, "SELLER", "APPROVED", true);
        insertUploadedVideoAudit("AUDIT-VIDEO-DIRTY", sellerId, storageUrl, "APPROVED");
        writeMediaFile(storageUrl, "not-a-real-video");

        mvc.perform(get(storageUrl))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("video identity media invalid")));
    }

    private void insertActiveUser(long userId) {
        jdbcTemplate.update("""
                INSERT INTO user_account (id, user_no, phone, password_hash, nickname, status, created_at, updated_at)
                VALUES (?, ?, ?, 'hash', ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId, "U" + userId, "138000" + userId, "用户" + userId);
    }

    private void upsertProfile(long userId, String mainRole, String videoIdentityStatus, boolean videoVerified) {
        jdbcTemplate.update("""
                MERGE INTO user_profile (user_id, gender, city, identity_status, main_role, video_identity_status, video_verified, created_at, updated_at)
                KEY(user_id)
                VALUES (?, 'goddess', '杭州', 'VERIFIED', ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId, mainRole, videoIdentityStatus, videoVerified);
    }

    private void insertUploadedVideoAudit(String auditNo, long userId, String storageUrl, String status) {
        insertUploadedVideoAudit(auditNo, userId, storageUrl, status, "video/mp4");
    }

    private void insertUploadedVideoAudit(String auditNo, long userId, String storageUrl, String status, String contentType) {
        jdbcTemplate.update("""
                INSERT INTO media_upload_ticket (ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at)
                VALUES (?, ?, 'VIDEO_IDENTITY', 'identity.mp4', ?, 1024, ?, 'hash', 'UPLOADED', CURRENT_TIMESTAMP, DATEADD('DAY', 1, CURRENT_TIMESTAMP))
                """, "TICKET-" + auditNo, userId, contentType, storageUrl);
        jdbcTemplate.update("""
                INSERT INTO audit_record (audit_no, audit_type, user_id, target_type, target_id, reason, description, status, created_at, reviewed_at)
                VALUES (?, 'VIDEO_IDENTITY', ?, 'USER', ?, ?, '视频认证测试', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, auditNo, userId, String.valueOf(userId), storageUrl, status);
    }

    private void writeMediaFile(String storageUrl, String content) throws Exception {
        Path target = mediaRoot.resolve(storageUrl.substring(1)).normalize();
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    private void writeValidVideoFile(String storageUrl) throws Exception {
        Path target = mediaRoot.resolve(storageUrl.substring(1)).normalize();
        Files.createDirectories(target.getParent());
        Files.write(target, minimalMp4WithDurationSeconds(10));
    }

    private void createSymlinkToOutsideFile(Path link, String content) throws Exception {
        Path outsideFile = Files.createTempFile("xiaoyuanquan-video-outside", ".txt");
        Files.writeString(outsideFile, content);
        Files.createDirectories(link.getParent());
        Files.createSymbolicLink(link, outsideFile);
    }

    private byte[] minimalMp4WithDurationSeconds(int durationSeconds) {
        byte[] ftyp = mp4Box("ftyp", concat(
                "isom".getBytes(StandardCharsets.US_ASCII),
                ByteBuffer.allocate(4).putInt(0).array(),
                "isom".getBytes(StandardCharsets.US_ASCII)
        ));
        ByteBuffer mvhdPayload = ByteBuffer.allocate(24);
        mvhdPayload.putInt(0);
        mvhdPayload.putInt(0);
        mvhdPayload.putInt(0);
        mvhdPayload.putInt(1000);
        mvhdPayload.putInt(durationSeconds * 1000);
        mvhdPayload.putInt(0);
        byte[] mvhd = mp4Box("mvhd", mvhdPayload.array());
        byte[] hdlr = mp4Box("hdlr", concat(
                ByteBuffer.allocate(8).putInt(0).putInt(0).array(),
                "vide".getBytes(StandardCharsets.US_ASCII),
                new byte[12]
        ));
        byte[] mdia = mp4Box("mdia", hdlr);
        byte[] trak = mp4Box("trak", mdia);
        byte[] moov = mp4Box("moov", concat(mvhd, trak));
        byte[] mdat = mp4Box("mdat", new byte[]{1});
        return concat(ftyp, moov, mdat);
    }

    private byte[] mp4Box(String type, byte[] content) {
        ByteBuffer buffer = ByteBuffer.allocate(8 + content.length);
        buffer.putInt(8 + content.length);
        buffer.put(type.getBytes(StandardCharsets.US_ASCII));
        buffer.put(content);
        return buffer.array();
    }

    private byte[] concat(byte[]... arrays) {
        int size = 0;
        for (byte[] array : arrays) {
            size += array.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(size);
        for (byte[] array : arrays) {
            buffer.put(array);
        }
        return buffer.array();
    }
}
