package com.secondhand.platform.modules.community;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.secondhand.platform.modules.community.application.CommunityApplicationService;
import com.secondhand.platform.modules.community.application.CommunityPostResponse;
import com.secondhand.platform.modules.community.application.CreateCommunityPostRequest;
import com.secondhand.platform.modules.media.application.MediaUploadTicketService;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import com.secondhand.platform.shared.web.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CommunityControllerTest {
    private CommunityApplicationService service;
    private JdbcTemplate jdbcTemplate;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        service = new CommunityApplicationService(jdbcTemplate, new MediaUploadTicketService(jdbcTemplate));
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        mvc = MockMvcBuilders.standaloneSetup(new CommunityController(service, new CurrentUserResolver(jdbcTemplate, environment)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void publicCommunityFeedAndDetailAreReadableWithoutSession() throws Exception {
        seedActiveUser(12L, "公开作者");
        CommunityPostResponse post = service.createPost(12L, postRequest());

        mvc.perform(get("/api/community/posts").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].postId", is(post.getPostId().intValue())))
                .andExpect(jsonPath("$.data[0].likedByMe", is(false)));

        mvc.perform(get("/api/community/posts/{postId}", post.getPostId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId", is(post.getPostId().intValue())))
                .andExpect(jsonPath("$.data.likedByMe", is(false)));
    }

    @Test
    void publicCommunityPageEndpointKeepsOldListContractCompatible() throws Exception {
        seedActiveUser(12L, "公开作者");
        CommunityPostResponse first = service.createPost(12L, postRequest());
        CreateCommunityPostRequest secondRequest = postRequest();
        secondRequest.setTitle("第二条公开动态");
        secondRequest.setContent("分页接口应该返回 posts、hasMore 和 nextCursor。");
        CommunityPostResponse second = service.createPost(12L, secondRequest);

        mvc.perform(get("/api/community/posts").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].postId").exists());

        mvc.perform(get("/api/community/posts/page").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.posts[0].postId", is(second.getPostId().intValue())))
                .andExpect(jsonPath("$.data.posts[0].likedByMe", is(false)))
                .andExpect(jsonPath("$.data.hasMore", is(true)))
                .andExpect(jsonPath("$.data.nextCursor").exists());

        mvc.perform(get("/api/community/posts/page").param("limit", "1").param("cursor", "preview-cursor"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void communityMutationsStillRequireSession() throws Exception {
        seedActiveUser(12L, "公开作者");
        CommunityPostResponse post = service.createPost(12L, postRequest());

        mvc.perform(post("/api/community/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"匿名发布\",\"topic\":\"生活日常\",\"content\":\"匿名不能发布社区内容。\",\"imageUrls\":[]}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/community/posts/{postId}/comments", post.getPostId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"匿名不能评论\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/community/posts/{postId}/likes", post.getPostId()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authenticatedSessionCanCreateCommunityPost() throws Exception {
        seedActiveUser(21L, "发帖用户");
        seedUserSession("usr_11111111111111111111111111111111", "usr_22222222222222222222222222222222", 21L);

        mvc.perform(post("/api/community/posts")
                        .header("Authorization", "Bearer usr_11111111111111111111111111111111")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"真实会话发布动态",
                                  "topic":"生活日常",
                                  "content":"生产会话令牌应该可以发布真实社区动态。",
                                  "imageUrls":[]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorId", is(21)))
                .andExpect(jsonPath("$.data.authorName", is("发帖用户")))
                .andExpect(jsonPath("$.data.title", is("真实会话发布动态")))
                .andExpect(jsonPath("$.data.topic", is("生活日常")))
                .andExpect(jsonPath("$.data.status", is("PUBLISHED")));
    }

    @Test
    void authenticatedSessionCanCommentLikeAndUnlikeCommunityPost() throws Exception {
        seedActiveUser(12L, "公开作者");
        seedActiveUser(22L, "互动用户");
        seedUserSession("usr_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "usr_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", 22L);
        CommunityPostResponse post = service.createPost(12L, postRequest());

        mvc.perform(post("/api/community/posts/{postId}/comments", post.getPostId())
                        .header("Authorization", "Bearer usr_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"真实会话可以评论\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorId", is(22)))
                .andExpect(jsonPath("$.data.authorName", is("互动用户")))
                .andExpect(jsonPath("$.data.content", is("真实会话可以评论")));

        mvc.perform(post("/api/community/posts/{postId}/likes", post.getPostId())
                        .header("Authorization", "Bearer usr_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likedByMe", is(true)))
                .andExpect(jsonPath("$.data.likeCount", is(1)))
                .andExpect(jsonPath("$.data.commentCount", is(1)));

        mvc.perform(delete("/api/community/posts/{postId}/likes", post.getPostId())
                        .header("Authorization", "Bearer usr_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likedByMe", is(false)))
                .andExpect(jsonPath("$.data.likeCount", is(0)))
                .andExpect(jsonPath("$.data.commentCount", is(1)));
    }

    @Test
    void authenticatedSessionCanManageOnlyOwnCommunityPosts() throws Exception {
        seedActiveUser(31L, "管理自己的用户");
        seedActiveUser(32L, "其他作者");
        seedUserSession("usr_cccccccccccccccccccccccccccccccc", "usr_dddddddddddddddddddddddddddddddd", 31L);
        CommunityPostResponse ownPost = service.createPost(31L, postRequest());
        CommunityPostResponse otherPost = service.createPost(32L, postRequest());

        mvc.perform(get("/api/community/posts/mine")
                        .header("Authorization", "Bearer usr_cccccccccccccccccccccccccccccccc")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].postId", is(ownPost.getPostId().intValue())));

        mvc.perform(put("/api/community/posts/{postId}", ownPost.getPostId())
                        .header("Authorization", "Bearer usr_cccccccccccccccccccccccccccccccc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"我修改后的动态",
                                  "topic":"交易经验",
                                  "content":"自己的帖子可以被修改并继续公开展示。",
                                  "imageUrls":[]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title", is("我修改后的动态")))
                .andExpect(jsonPath("$.data.topic", is("交易经验")));

        mvc.perform(put("/api/community/posts/{postId}", otherPost.getPostId())
                        .header("Authorization", "Bearer usr_cccccccccccccccccccccccccccccccc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"不能修改别人的动态",
                                  "topic":"交易经验",
                                  "content":"其他作者的帖子必须被后端拒绝。",
                                  "imageUrls":[]
                                }
                                """))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/community/posts/{postId}", ownPost.getPostId())
                        .header("Authorization", "Bearer usr_cccccccccccccccccccccccccccccccc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("DELETED")));

        mvc.perform(get("/api/community/posts/{postId}", ownPost.getPostId()))
                .andExpect(status().isBadRequest());
    }

    private CreateCommunityPostRequest postRequest() {
        CreateCommunityPostRequest request = new CreateCommunityPostRequest();
        request.setTitle("匿名可读社区动态");
        request.setTopic("生活日常");
        request.setContent("社区公开列表和详情应该允许未登录用户读取。");
        request.setImageUrls(List.of());
        return request;
    }

    private void seedActiveUser(Long userId, String nickname) {
        jdbcTemplate.update("""
                INSERT INTO user_account (id, user_no, phone, password_hash, nickname, status)
                VALUES (?, ?, ?, 'hash', ?, 'ACTIVE')
                """, userId, "U-CTRL-" + userId, "1392000" + String.format("%04d", userId), nickname);
    }

    private void seedUserSession(String accessToken, String refreshToken, Long userId) {
        jdbcTemplate.update("""
                INSERT INTO user_session (access_token, refresh_token, user_id, expires_at, revoked)
                VALUES (?, ?, ?, DATEADD('DAY', 1, CURRENT_TIMESTAMP), FALSE)
                """, accessToken, refreshToken, userId);
    }
}
