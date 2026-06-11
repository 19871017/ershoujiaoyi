package com.secondhand.platform.modules.media.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.mock.web.MockMultipartFile;

class MediaUploadTicketServiceTest {
    @TempDir
    Path storageRoot;
    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private MediaUploadTicketService service;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        service = new MediaUploadTicketService(jdbcTemplate, storageRoot.toString());
    }

    @Test
    void shouldIssueVideoIdentityUploadTicketAndPersistStorageUrl() {
        MediaUploadTicketResponse response = service.issue(9L, "VIDEO_IDENTITY", "video/mp4", 5_000_000L, "verify.mp4");

        assertNotNull(response.ticketNo());
        assertNotNull(response.uploadToken());
        assertEquals("VIDEO_IDENTITY", response.scene());
        assertEquals("video/mp4", response.contentType());
        assertTrue(response.storageUrl().startsWith("/uploads/video-identity/9/"));
        assertTrue(response.storageUrl().endsWith(".mp4"));
        assertEquals("ISSUED", response.status());

        Integer stored = jdbcTemplate.queryForObject("select count(1) from media_upload_ticket where ticket_no = ? and owner_user_id = ? and storage_url = ?", Integer.class, response.ticketNo(), 9L, response.storageUrl());
        assertEquals(1, stored);
    }

    @Test
    void shouldRejectUnsafeSceneTypeAndSize() {
        assertThrows(IllegalArgumentException.class, () -> service.issue(0L, "VIDEO_IDENTITY", "video/mp4", 1L, "a.mp4"));
        assertThrows(IllegalArgumentException.class, () -> service.issue(1L, "UNKNOWN", "video/mp4", 1L, "a.mp4"));
        assertThrows(IllegalArgumentException.class, () -> service.issue(1L, "VIDEO_IDENTITY", "image/png", 1L, "a.png"));
        assertThrows(IllegalArgumentException.class, () -> service.issue(1L, "VIDEO_IDENTITY", "video/mp4", 0L, "a.mp4"));
        assertThrows(IllegalArgumentException.class, () -> service.issue(1L, "VIDEO_IDENTITY", "video/mp4", 80_000_001L, "a.mp4"));
        assertThrows(IllegalArgumentException.class, () -> service.issue(1L, "VIDEO_IDENTITY", "video/mp4", 1L, "../evil.mp4"));
    }

    @Test
    void shouldIssueSensitiveEvidenceImageTickets() {
        assertTrue(service.issue(7L, "AFTER_SALES_EVIDENCE", "image/png", 600_000L, "refund-proof.png")
                .storageUrl().startsWith("/uploads/evidence/after-sales/7/"));
        assertTrue(service.issue(7L, "REPORT_EVIDENCE", "image/webp", 600_000L, "report-proof.webp")
                .storageUrl().startsWith("/uploads/report-evidence/7/"));
        assertTrue(service.issue(7L, "CHAT_IMAGE", "image/jpeg", 600_000L, "chat-proof.jpg")
                .storageUrl().startsWith("/uploads/chat-image/7/"));
        assertTrue(service.issue(7L, "CHAT_VOICE", "audio/webm", 600_000L, "chat-voice.webm")
                .storageUrl().startsWith("/uploads/chat-voice/7/"));
        assertTrue(service.issue(7L, "CHAT_VIDEO", "video/mp4", 600_000L, "chat-video.mp4")
                .storageUrl().startsWith("/uploads/chat-video/7/"));
        assertThrows(IllegalArgumentException.class, () -> service.issue(7L, "AFTER_SALES_EVIDENCE", "image/png", 10_000_001L, "too-large.png"));
        assertThrows(IllegalArgumentException.class, () -> service.issue(7L, "CHAT_VOICE", "image/png", 600_000L, "chat-voice.png"));
        assertThrows(IllegalArgumentException.class, () -> service.issue(7L, "CHAT_VOICE", "audio/webm", 10_000_001L, "chat-voice.webm"));
        assertThrows(IllegalArgumentException.class, () -> service.issue(7L, "CHAT_VIDEO", "audio/webm", 600_000L, "chat-video.webm"));
        assertThrows(IllegalArgumentException.class, () -> service.issue(7L, "CHAT_VIDEO", "video/mp4", 80_000_001L, "chat-video.mp4"));
    }

    @Test
    void shouldValidateIssuedStorageUrlOwnership() {
        MediaUploadTicketResponse response = service.issue(3L, "VIDEO_IDENTITY", "video/mp4", 1_000_000L, "face.mp4");

        assertEquals(response.ticketNo(), service.requireIssuedStorageUrl(3L, "VIDEO_IDENTITY", response.storageUrl()).ticketNo());
        assertThrows(IllegalArgumentException.class, () -> service.requireUploadedStorageUrl(3L, "VIDEO_IDENTITY", response.storageUrl()));
        assertThrows(IllegalArgumentException.class, () -> service.requireIssuedStorageUrl(4L, "VIDEO_IDENTITY", response.storageUrl()));
        assertThrows(IllegalArgumentException.class, () -> service.requireIssuedStorageUrl(3L, "VIDEO_IDENTITY", "https://cdn.example.com/free.mp4"));
        assertThrows(IllegalArgumentException.class, () -> service.requireIssuedStorageUrl(3L, "VIDEO_IDENTITY", "local://video.mp4"));
    }

    @Test
    void shouldStoreUploadedFileAndMarkTicketUploaded() throws Exception {
        byte[] videoBytes = minimalMp4WithDurationSeconds(10);
        MediaUploadTicketResponse issued = service.issue(6L, "VIDEO_IDENTITY", "video/mp4", (long) videoBytes.length, "face.mp4");
        MockMultipartFile file = new MockMultipartFile("file", "face.mp4", "video/mp4", videoBytes);

        MediaUploadTicketResponse uploaded = service.storeUploadedFile(6L, issued.ticketNo(), issued.uploadToken(), file);

        Path storedFile = storageRoot.resolve(issued.storageUrl().substring(1));
        assertEquals("UPLOADED", uploaded.status());
        assertEquals(issued.storageUrl(), uploaded.storageUrl());
        assertTrue(Files.exists(storedFile));
        assertEquals(videoBytes.length, Files.readAllBytes(storedFile).length);
        assertEquals(issued.ticketNo(), service.requireUploadedStorageUrl(6L, "VIDEO_IDENTITY", issued.storageUrl()).ticketNo());
        assertThrows(IllegalArgumentException.class, () -> service.storeUploadedFile(6L, issued.ticketNo(), issued.uploadToken(), file));
    }

    @Test
    void shouldStoreUploadedImageFileAndMarkTicketUploaded() throws Exception {
        byte[] imageBytes = imageBytes("png");
        MediaUploadTicketResponse issued = service.issue(6L, "COMMUNITY_IMAGE", "image/png", (long) imageBytes.length, "showcase.png");
        MockMultipartFile file = new MockMultipartFile("file", "showcase.png", "image/png", imageBytes);

        MediaUploadTicketResponse uploaded = service.storeUploadedFile(6L, issued.ticketNo(), issued.uploadToken(), file);

        assertEquals("UPLOADED", uploaded.status());
        assertTrue(Files.exists(storageRoot.resolve(issued.storageUrl().substring(1))));
        assertEquals(issued.ticketNo(), service.requireUploadedStorageUrl(6L, "COMMUNITY_IMAGE", issued.storageUrl()).ticketNo());
    }

    @Test
    void shouldRejectInvalidImageBytesBeforeMarkingTicketUploaded() throws Exception {
        byte[] invalidBytes = "not-a-real-image".getBytes(StandardCharsets.UTF_8);
        MediaUploadTicketResponse issued = service.issue(6L, "COMMUNITY_IMAGE", "image/png", (long) invalidBytes.length, "showcase.png");
        MockMultipartFile file = new MockMultipartFile("file", "showcase.png", "image/png", invalidBytes);

        assertEquals("image media invalid", assertThrows(IllegalArgumentException.class,
                () -> service.storeUploadedFile(6L, issued.ticketNo(), issued.uploadToken(), file)).getMessage());

        assertEquals("ISSUED", jdbcTemplate.queryForObject("select status from media_upload_ticket where ticket_no = ?", String.class, issued.ticketNo()));
        assertTrue(!Files.exists(storageRoot.resolve(issued.storageUrl().substring(1))));
    }

    @Test
    void shouldRejectInvalidVideoIdentityBytesBeforeMarkingTicketUploaded() throws Exception {
        byte[] invalidBytes = "not-an-mp4".getBytes(StandardCharsets.UTF_8);
        MediaUploadTicketResponse issued = service.issue(6L, "VIDEO_IDENTITY", "video/mp4", (long) invalidBytes.length, "face.mp4");
        MockMultipartFile file = new MockMultipartFile("file", "face.mp4", "video/mp4", invalidBytes);

        assertEquals("video identity media invalid", assertThrows(IllegalArgumentException.class,
                () -> service.storeUploadedFile(6L, issued.ticketNo(), issued.uploadToken(), file)).getMessage());

        assertEquals("ISSUED", jdbcTemplate.queryForObject("select status from media_upload_ticket where ticket_no = ?", String.class, issued.ticketNo()));
        assertTrue(!Files.exists(storageRoot.resolve(issued.storageUrl().substring(1))));
        assertThrows(IllegalArgumentException.class, () -> service.requireUploadedStorageUrl(6L, "VIDEO_IDENTITY", issued.storageUrl()));
    }

    @Test
    void shouldRejectMetadataOnlyVideoIdentityBeforeMarkingTicketUploaded() throws Exception {
        byte[] metadataOnly = mp4WithDurationButNoVideoTrack(10);
        MediaUploadTicketResponse issued = service.issue(6L, "VIDEO_IDENTITY", "video/mp4", (long) metadataOnly.length, "metadata-only.mp4");
        MockMultipartFile file = new MockMultipartFile("file", "metadata-only.mp4", "video/mp4", metadataOnly);

        assertEquals("video identity media invalid", assertThrows(IllegalArgumentException.class,
                () -> service.storeUploadedFile(6L, issued.ticketNo(), issued.uploadToken(), file)).getMessage());

        assertEquals("ISSUED", jdbcTemplate.queryForObject("select status from media_upload_ticket where ticket_no = ?", String.class, issued.ticketNo()));
        assertTrue(!Files.exists(storageRoot.resolve(issued.storageUrl().substring(1))));
    }

    @Test
    void shouldRejectZeroDurationVideoIdentityBeforeMarkingTicketUploaded() throws Exception {
        byte[] zeroDuration = minimalMp4WithDurationSeconds(0);
        MediaUploadTicketResponse issued = service.issue(6L, "VIDEO_IDENTITY", "video/mp4", (long) zeroDuration.length, "zero.mp4");
        MockMultipartFile file = new MockMultipartFile("file", "zero.mp4", "video/mp4", zeroDuration);

        assertEquals("video identity media invalid", assertThrows(IllegalArgumentException.class,
                () -> service.storeUploadedFile(6L, issued.ticketNo(), issued.uploadToken(), file)).getMessage());

        assertEquals("ISSUED", jdbcTemplate.queryForObject("select status from media_upload_ticket where ticket_no = ?", String.class, issued.ticketNo()));
        assertTrue(!Files.exists(storageRoot.resolve(issued.storageUrl().substring(1))));
    }

    @Test
    void shouldRejectOverlongVideoIdentityBeforeMarkingTicketUploaded() throws Exception {
        byte[] overlong = minimalMp4WithDurationSeconds(11);
        MediaUploadTicketResponse issued = service.issue(6L, "VIDEO_IDENTITY", "video/mp4", (long) overlong.length, "long.mp4");
        MockMultipartFile file = new MockMultipartFile("file", "long.mp4", "video/mp4", overlong);

        assertEquals("video identity media invalid", assertThrows(IllegalArgumentException.class,
                () -> service.storeUploadedFile(6L, issued.ticketNo(), issued.uploadToken(), file)).getMessage());

        assertEquals("ISSUED", jdbcTemplate.queryForObject("select status from media_upload_ticket where ticket_no = ?", String.class, issued.ticketNo()));
        assertTrue(!Files.exists(storageRoot.resolve(issued.storageUrl().substring(1))));
    }

    @Test
    void shouldStoreUploadedChatVoiceFileAndMarkTicketUploaded() throws Exception {
        byte[] voiceBytes = webmVoiceBytes();
        MediaUploadTicketResponse issued = service.issue(6L, "CHAT_VOICE", "audio/webm", (long) voiceBytes.length, "voice.webm");
        MockMultipartFile file = new MockMultipartFile("file", "voice.webm", "audio/webm", voiceBytes);

        MediaUploadTicketResponse uploaded = service.storeUploadedFile(6L, issued.ticketNo(), issued.uploadToken(), file);

        Path storedFile = storageRoot.resolve(issued.storageUrl().substring(1));
        assertEquals("UPLOADED", uploaded.status());
        assertTrue(uploaded.storageUrl().startsWith("/uploads/chat-voice/6/"));
        assertTrue(uploaded.storageUrl().endsWith(".webm"));
        assertTrue(Files.exists(storedFile));
        assertEquals(voiceBytes.length, Files.readAllBytes(storedFile).length);
        assertEquals(issued.ticketNo(), service.requireUploadedStorageUrl(6L, "CHAT_VOICE", issued.storageUrl()).ticketNo());
    }

    @Test
    void shouldRejectInvalidChatVoiceBytesBeforeMarkingTicketUploaded() throws Exception {
        byte[] invalidBytes = "not-a-real-voice".getBytes(StandardCharsets.UTF_8);
        MediaUploadTicketResponse issued = service.issue(6L, "CHAT_VOICE", "audio/webm", (long) invalidBytes.length, "voice.webm");
        MockMultipartFile file = new MockMultipartFile("file", "voice.webm", "audio/webm", invalidBytes);

        assertEquals("voice media invalid", assertThrows(IllegalArgumentException.class,
                () -> service.storeUploadedFile(6L, issued.ticketNo(), issued.uploadToken(), file)).getMessage());

        assertEquals("ISSUED", jdbcTemplate.queryForObject("select status from media_upload_ticket where ticket_no = ?", String.class, issued.ticketNo()));
        assertTrue(!Files.exists(storageRoot.resolve(issued.storageUrl().substring(1))));
        assertThrows(IllegalArgumentException.class, () -> service.requireUploadedStorageUrl(6L, "CHAT_VOICE", issued.storageUrl()));
    }

    @Test
    void shouldStoreUploadedChatVideoFileAndMarkTicketUploaded() throws Exception {
        byte[] videoBytes = minimalMp4WithDurationSeconds(30);
        MediaUploadTicketResponse issued = service.issue(6L, "CHAT_VIDEO", "video/mp4", (long) videoBytes.length, "video.mp4");
        MockMultipartFile file = new MockMultipartFile("file", "video.mp4", "video/mp4", videoBytes);

        MediaUploadTicketResponse uploaded = service.storeUploadedFile(6L, issued.ticketNo(), issued.uploadToken(), file);

        Path storedFile = storageRoot.resolve(issued.storageUrl().substring(1));
        assertEquals("UPLOADED", uploaded.status());
        assertTrue(uploaded.storageUrl().startsWith("/uploads/chat-video/6/"));
        assertTrue(uploaded.storageUrl().endsWith(".mp4"));
        assertTrue(Files.exists(storedFile));
        assertEquals(videoBytes.length, Files.readAllBytes(storedFile).length);
        assertEquals(issued.ticketNo(), service.requireUploadedStorageUrl(6L, "CHAT_VIDEO", issued.storageUrl()).ticketNo());
    }

    @Test
    void shouldRejectInvalidChatVideoBytesBeforeMarkingTicketUploaded() throws Exception {
        byte[] invalidBytes = "not-a-real-video".getBytes(StandardCharsets.UTF_8);
        MediaUploadTicketResponse issued = service.issue(6L, "CHAT_VIDEO", "video/mp4", (long) invalidBytes.length, "video.mp4");
        MockMultipartFile file = new MockMultipartFile("file", "video.mp4", "video/mp4", invalidBytes);

        assertEquals("chat video media invalid", assertThrows(IllegalArgumentException.class,
                () -> service.storeUploadedFile(6L, issued.ticketNo(), issued.uploadToken(), file)).getMessage());

        assertEquals("ISSUED", jdbcTemplate.queryForObject("select status from media_upload_ticket where ticket_no = ?", String.class, issued.ticketNo()));
        assertTrue(!Files.exists(storageRoot.resolve(issued.storageUrl().substring(1))));
        assertThrows(IllegalArgumentException.class, () -> service.requireUploadedStorageUrl(6L, "CHAT_VIDEO", issued.storageUrl()));
    }

    @Test
    void shouldRejectExpiredIssuedStorageUrl() {
        MediaUploadTicketResponse response = service.issue(8L, "REPORT_EVIDENCE", "image/png", 600_000L, "proof.png");
        jdbcTemplate.update("update media_upload_ticket set expires_at = DATEADD('MINUTE', -1, CURRENT_TIMESTAMP) where ticket_no = ?", response.ticketNo());

        assertThrows(IllegalArgumentException.class, () -> service.requireIssuedStorageUrl(8L, "REPORT_EVIDENCE", response.storageUrl()));
    }

    @Test
    void shouldAllowExpiredUploadedStorageUrlForBusinessSubmission() throws Exception {
        byte[] videoBytes = minimalMp4WithDurationSeconds(10);
        MediaUploadTicketResponse issued = service.issue(9L, "VIDEO_IDENTITY", "video/mp4", (long) videoBytes.length, "face.mp4");
        MockMultipartFile file = new MockMultipartFile("file", "face.mp4", "video/mp4", videoBytes);
        service.storeUploadedFile(9L, issued.ticketNo(), issued.uploadToken(), file);
        jdbcTemplate.update("update media_upload_ticket set expires_at = DATEADD('MINUTE', -1, CURRENT_TIMESTAMP) where ticket_no = ?", issued.ticketNo());

        assertEquals(issued.ticketNo(), service.requireUploadedStorageUrl(9L, "VIDEO_IDENTITY", issued.storageUrl()).ticketNo());
        assertEquals(issued.ticketNo(), service.requireIssuedStorageUrl(9L, "VIDEO_IDENTITY", issued.storageUrl()).ticketNo());
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

    private byte[] mp4WithDurationButNoVideoTrack(int durationSeconds) {
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
        byte[] moov = mp4Box("moov", mp4Box("mvhd", mvhdPayload.array()));
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

    private byte[] imageBytes(String format) throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }

    private byte[] webmVoiceBytes() {
        return concat(
                new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3},
                "xiaoyuanquan-voice".getBytes(StandardCharsets.US_ASCII)
        );
    }
}
