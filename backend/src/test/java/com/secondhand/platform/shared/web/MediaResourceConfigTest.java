package com.secondhand.platform.shared.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

class MediaResourceConfigTest {
    @TempDir
    Path mediaRoot;

    @Test
    void uploadsResourceLocationUsesConfiguredMediaStorageRoot() {
        MediaResourceConfig config = new MediaResourceConfig("/tmp/xiaoyuanquan-media");

        assertEquals(Path.of("/tmp/xiaoyuanquan-media/uploads").toAbsolutePath().normalize(), config.uploadsRoot());
    }

    @Test
    void publicUploadResourcePatternsExcludePrivateChatAndVideoIdentityMedia() {
        MediaResourceConfig config = new MediaResourceConfig("/tmp/xiaoyuanquan-media");
        java.util.List<String> patterns = Arrays.asList(config.publicUploadResourcePatterns());

        assertTrue(patterns.contains("/uploads/product-image/**"));
        assertTrue(patterns.contains("/uploads/community-image/**"));
        assertTrue(patterns.contains("/uploads/avatar/**"));
        assertTrue(patterns.stream().noneMatch(pattern -> pattern.contains("report-evidence")));
        assertTrue(patterns.stream().noneMatch(pattern -> pattern.contains("evidence/after-sales")));
        assertTrue(patterns.stream().noneMatch(pattern -> pattern.contains("video-identity")));
        assertTrue(patterns.stream().noneMatch(pattern -> pattern.contains("chat-image")));
        assertTrue(patterns.stream().noneMatch(pattern -> pattern.contains("chat-voice")));
        assertTrue(patterns.stream().noneMatch(pattern -> pattern.equals("/uploads/**")));
    }

    @Test
    void defaultMediaStorageRootUsesStableRuntimeDirectory() throws Exception {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);

        assertTrue(applicationYaml.contains("storage-root: ${MEDIA_STORAGE_ROOT:/opt/esxz-old}"));
    }

    @Test
    void uploadsResourceLocationRequiresExplicitStorageRoot() {
        assertThrows(IllegalStateException.class, () -> new MediaResourceConfig(""));
    }

    @Test
    void publicUploadResolverRejectsSymlinkEscape() throws Exception {
        Path publicRoot = mediaRoot.resolve("uploads/product-image");
        Path normalFile = publicRoot.resolve("1/normal.png");
        Files.createDirectories(normalFile.getParent());
        Files.writeString(normalFile, "normal");
        Path outsideFile = Files.createTempFile("xiaoyuanquan-public-media-outside", ".png");
        Files.writeString(outsideFile, "outside");
        Path symlink = publicRoot.resolve("1/leak.png");
        Files.createSymbolicLink(symlink, outsideFile);
        Resource location = new UrlResource(publicRoot.toUri());
        MediaResourceConfig.PublicUploadPathResourceResolver resolver = new MediaResourceConfig.PublicUploadPathResourceResolver();

        assertNotNull(resolver.getResource("1/normal.png", location));
        assertNull(resolver.getResource("1/leak.png", location));
    }
}
