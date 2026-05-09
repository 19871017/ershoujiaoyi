package com.secondhand.platform.modules.community.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class CommunityApplicationServiceTest {
    private EmbeddedDatabase database;
    private CommunityApplicationService service;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        service = new CommunityApplicationService(new JdbcTemplate(database), new com.secondhand.platform.modules.media.application.MediaUploadTicketService(new JdbcTemplate(database)));
    }

    @Test
    void createPostShouldPersistAndRejectPreviewTarget() {
        CreateCommunityPostRequest request = post("奶油白裙子怎么搭？", "穿搭交流", "今天整理衣柜，想听听姐妹搭配建议。", List.of(issuedCommunityImage(11L, "look-1.jpg")));

        CommunityPostResponse response = service.createPost(11L, request);

        assertEquals("PUBLISHED", response.getStatus());
        assertEquals(11L, response.getAuthorId());
        assertEquals("穿搭交流", response.getTopic());
        assertFalse(service.listPublishedPosts(20).isEmpty());

        CommunityApplicationService reloaded = new CommunityApplicationService(new JdbcTemplate(database), new com.secondhand.platform.modules.media.application.MediaUploadTicketService(new JdbcTemplate(database)));
        CommunityPostDetailResponse detail = reloaded.detail(response.getPostId(), 11L);
        assertEquals(response.getPostNo(), detail.getPostNo());
        assertEquals("奶油白裙子怎么搭？", detail.getTitle());
        assertEquals(0, detail.getComments().size());

        assertThrows(IllegalArgumentException.class, () -> reloaded.detail("preview", 11L));
        assertThrows(IllegalArgumentException.class, () -> reloaded.detail("UNKNOWN", 11L));
    }

    @Test
    void commentShouldPersistAndRequireValidContent() {
        CommunityPostResponse post = service.createPost(12L, post("玛丽珍鞋避坑", "闲置避坑", "鞋码偏小要提前说明。", List.of()));

        CommunityCommentResponse comment = service.addComment(18L, post.getPostId(), comment("我也遇到过，最好拍鞋底细节。"));

        assertEquals(18L, comment.getAuthorId());
        CommunityApplicationService reloaded = new CommunityApplicationService(new JdbcTemplate(database), new com.secondhand.platform.modules.media.application.MediaUploadTicketService(new JdbcTemplate(database)));
        CommunityPostDetailResponse detail = reloaded.detail(post.getPostId(), 18L);
        assertEquals(1, detail.getComments().size());
        assertEquals("我也遇到过，最好拍鞋底细节。", detail.getComments().get(0).getContent());

        assertThrows(IllegalArgumentException.class, () -> reloaded.addComment(18L, post.getPostId(), comment("好")));
    }

    @Test
    void likeShouldBeIdempotentAndPersistedPerUser() {
        CommunityPostResponse post = service.createPost(13L, post("同城约看提醒", "同城约看", "建议公共场所见面。", List.of()));

        CommunityPostResponse liked = service.likePost(21L, post.getPostId());
        CommunityPostResponse replay = service.likePost(21L, post.getPostId());
        assertEquals(1, liked.getLikeCount());
        assertEquals(1, replay.getLikeCount());

        service.likePost(22L, post.getPostId());
        CommunityApplicationService reloaded = new CommunityApplicationService(new JdbcTemplate(database), new com.secondhand.platform.modules.media.application.MediaUploadTicketService(new JdbcTemplate(database)));
        CommunityPostDetailResponse detail = reloaded.detail(post.getPostId(), 21L);
        assertEquals(2, detail.getLikeCount());
        assertTrue(detail.getLikedByMe());
    }

    @Test
    void listShouldHideNonPublishedPostsAndCapLimit() {
        CommunityPostResponse post = service.createPost(14L, post("袜子护理", "穿搭交流", "清洗收纳经验不要暴晒，收纳前保持干燥。", List.of()));
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.update("UPDATE community_post SET status = 'BLOCKED' WHERE post_no = ?", post.getPostNo());

        assertTrue(service.listPublishedPosts(500).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.createPost(14L, post("短", "穿搭交流", "太短", List.of())));
    }

    @Test
    void createPostShouldRejectUnissuedCommunityImages() {
        assertThrows(IllegalArgumentException.class, () -> service.createPost(15L,
                post("图片凭证校验", "穿搭交流", "社区图片必须先拿平台上传凭证。", List.of("https://img.example.com/fake.jpg"))));
        assertThrows(IllegalArgumentException.class, () -> service.createPost(15L,
                post("图片凭证校验", "穿搭交流", "社区图片必须先拿平台上传凭证。", List.of("local://temp/community.jpg"))));
    }

    private String issuedCommunityImage(Long userId, String filename) {
        return new com.secondhand.platform.modules.media.application.MediaUploadTicketService(new JdbcTemplate(database))
                .issue(userId, "COMMUNITY_IMAGE", "image/jpeg", 300_000L, filename)
                .storageUrl();
    }

    private CreateCommunityPostRequest post(String title, String topic, String content, List<String> imageUrls) {
        CreateCommunityPostRequest request = new CreateCommunityPostRequest();
        request.setTitle(title);
        request.setTopic(topic);
        request.setContent(content);
        request.setImageUrls(imageUrls);
        return request;
    }

    private CreateCommunityCommentRequest comment(String content) {
        CreateCommunityCommentRequest request = new CreateCommunityCommentRequest();
        request.setContent(content);
        return request;
    }
}
