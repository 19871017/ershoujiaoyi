package com.secondhand.platform.modules.media.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                .storageUrl().startsWith("/uploads/evidence/report/7/"));
        assertTrue(service.issue(7L, "CHAT_IMAGE", "image/jpeg", 600_000L, "chat-proof.jpg")
                .storageUrl().startsWith("/uploads/chat-image/7/"));
        assertThrows(IllegalArgumentException.class, () -> service.issue(7L, "AFTER_SALES_EVIDENCE", "image/png", 10_000_001L, "too-large.png"));
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
        MediaUploadTicketResponse issued = service.issue(6L, "VIDEO_IDENTITY", "video/mp4", 16L, "face.mp4");
        MockMultipartFile file = new MockMultipartFile("file", "face.mp4", "video/mp4", "real-video".getBytes());

        MediaUploadTicketResponse uploaded = service.storeUploadedFile(6L, issued.ticketNo(), issued.uploadToken(), file);

        Path storedFile = storageRoot.resolve(issued.storageUrl().substring(1));
        assertEquals("UPLOADED", uploaded.status());
        assertEquals(issued.storageUrl(), uploaded.storageUrl());
        assertTrue(Files.exists(storedFile));
        assertEquals("real-video", Files.readString(storedFile));
        assertEquals(issued.ticketNo(), service.requireUploadedStorageUrl(6L, "VIDEO_IDENTITY", issued.storageUrl()).ticketNo());
        assertThrows(IllegalArgumentException.class, () -> service.storeUploadedFile(6L, issued.ticketNo(), issued.uploadToken(), file));
    }

    @Test
    void shouldRejectExpiredIssuedStorageUrl() {
        MediaUploadTicketResponse response = service.issue(8L, "REPORT_EVIDENCE", "image/png", 600_000L, "proof.png");
        jdbcTemplate.update("update media_upload_ticket set expires_at = DATEADD('MINUTE', -1, CURRENT_TIMESTAMP) where ticket_no = ?", response.ticketNo());

        assertThrows(IllegalArgumentException.class, () -> service.requireIssuedStorageUrl(8L, "REPORT_EVIDENCE", response.storageUrl()));
    }
}
