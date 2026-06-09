package com.secondhand.platform.modules.community.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        CreateCommunityPostRequest request = post("奶油白裙子怎么搭？", "生活日常", "今天整理衣柜，想听听姐妹搭配建议。", List.of(issuedCommunityImage(11L, "look-1.jpg")));

        CommunityPostResponse response = createPost(11L, request);

        assertEquals("PUBLISHED", response.getStatus());
        assertEquals(11L, response.getAuthorId());
        assertEquals("生活日常", response.getTopic());
        assertFalse(service.listPublishedPosts(20).isEmpty());

        CommunityApplicationService reloaded = new CommunityApplicationService(new JdbcTemplate(database), new com.secondhand.platform.modules.media.application.MediaUploadTicketService(new JdbcTemplate(database)));
        CommunityPostDetailResponse detail = reloaded.detail(response.getPostId(), 11L);
        assertEquals(response.getPostNo(), detail.getPostNo());
        assertEquals("奶油白裙子怎么搭？", detail.getTitle());
        assertEquals(0, detail.getComments().size());

        assertEquals("invalid post id", assertThrows(IllegalArgumentException.class, () -> reloaded.detail("preview", 11L)).getMessage());
        assertEquals("invalid post id", assertThrows(IllegalArgumentException.class, () -> reloaded.detail("UNKNOWN", 11L)).getMessage());
        assertEquals("invalid post id", assertThrows(IllegalArgumentException.class, () -> reloaded.detail("POST-DEMO-0001", 11L)).getMessage());
        assertEquals("invalid post id", assertThrows(IllegalArgumentException.class, () -> reloaded.detail("../1", 11L)).getMessage());
        assertEquals("invalid post id", assertThrows(IllegalArgumentException.class, () -> reloaded.detail("0", 11L)).getMessage());
    }

    @Test
    void createPostShouldAllowOnlyConfiguredCommunityTopics() {
        List<String> allowedTopics = List.of("生活日常", "闲置避坑", "交易经验", "求购心愿");
        for (int i = 0; i < allowedTopics.size(); i++) {
            String topic = allowedTopics.get(i);
            CommunityPostResponse response = createPost(30L + i, post("社区话题契约" + i, topic, "真实帖子必须落在前端可见的话题里。", List.of()));
            assertEquals(topic, response.getTopic());
        }
        assertEquals(4, countCommunityPosts());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> createPost(40L,
                post("旧话题不入库", "穿搭交流", "这个旧话题不在社区前端筛选范围内。", List.of())));
        assertEquals("invalid topic", error.getMessage());
        assertEquals(4, countCommunityPosts());
    }

    @Test
    void commentShouldPersistAndRequireValidContent() {
        CommunityPostResponse post = createPost(12L, post("玛丽珍鞋避坑", "闲置避坑", "鞋码偏小要提前说明。", List.of()));

        CommunityCommentResponse comment = addComment(18L, post.getPostId(), comment("我也遇到过，最好拍鞋底细节。"));

        assertEquals(18L, comment.getAuthorId());
        CommunityApplicationService reloaded = new CommunityApplicationService(new JdbcTemplate(database), new com.secondhand.platform.modules.media.application.MediaUploadTicketService(new JdbcTemplate(database)));
        CommunityPostDetailResponse detail = reloaded.detail(post.getPostId(), 18L);
        assertEquals(1, detail.getComments().size());
        assertEquals("我也遇到过，最好拍鞋底细节。", detail.getComments().get(0).getContent());

        assertThrows(IllegalArgumentException.class, () -> reloaded.addComment(18L, post.getPostId(), comment("好")));
    }

    @Test
    void communityTextShouldRejectContactInfoVariants() {
        assertEquals("contact info is not allowed", assertThrows(IllegalArgumentException.class, () -> createPost(19L,
                post("联系方式拦截", "生活日常", "喜欢的话可以加 wx：seller_2026 私聊。", List.of()))).getMessage());
        assertEquals("contact info is not allowed", assertThrows(IllegalArgumentException.class, () -> createPost(19L,
                post("手机号拦截", "生活日常", "这是我的电话 13800138000，直接联系。", List.of()))).getMessage());
        CommunityPostResponse post = createPost(19L, post("评论联系方式拦截", "闲置避坑", "评论也不能引导离开平台交易。", List.of()));
        assertEquals("contact info is not allowed", assertThrows(IllegalArgumentException.class,
                () -> addComment(20L, post.getPostId(), comment("Q Q 123456789 这里说"))).getMessage());
        assertEquals("contact info is not allowed", assertThrows(IllegalArgumentException.class,
                () -> addComment(20L, post.getPostId(), comment("可走支付宝收款码"))).getMessage());
    }

    @Test
    void duplicatePostAndCommentShouldReuseRecentExistingRecord() {
        CreateCommunityPostRequest duplicatePost = post("重复提交防刷", "交易经验", "网络抖动时重复点击发布，只应该保留一条真实动态。", List.of());

        CommunityPostResponse first = createPost(25L, duplicatePost);
        CommunityPostResponse second = createPost(25L, duplicatePost);

        assertEquals(first.getPostNo(), second.getPostNo());
        assertEquals(1, countCommunityPosts());

        CommunityCommentResponse firstComment = addComment(26L, first.getPostId(), comment("网络抖动重复发送评论，也应该复用已有评论。"));
        CommunityCommentResponse secondComment = addComment(26L, first.getPostId(), comment("网络抖动重复发送评论，也应该复用已有评论。"));

        assertEquals(firstComment.getCommentNo(), secondComment.getCommentNo());
        assertEquals(1, service.detail(first.getPostId(), 26L).getComments().size());
    }

    @Test
    void listDetailAndCommentsShouldExposeRealAuthorProfile() {
        seedUserProfile(12L, "发帖卖家", "/uploads/community-image/seller/avatar.jpg", "杭州");
        seedUserProfile(18L, "真实评论者", "/uploads/community-image/commenter/avatar.jpg", "上海");

        CommunityPostResponse post = createPost(12L, post("真实作者展示", "生活日常", "社区动态必须展示真实作者昵称头像和城市。", List.of()));
        CommunityCommentResponse comment = addComment(18L, post.getPostId(), comment("评论也要显示真实昵称和头像。"));

        CommunityPostResponse listRow = service.listPublishedPosts(20, 18L).get(0);
        CommunityPostDetailResponse detail = service.detail(post.getPostId(), 18L);
        CommunityCommentResponse detailComment = detail.getComments().get(0);

        assertEquals("发帖卖家", listRow.getAuthorName());
        assertEquals("/uploads/community-image/seller/avatar.jpg", listRow.getAuthorAvatar());
        assertEquals("杭州", listRow.getCity());
        assertEquals("发帖卖家", detail.getAuthorName());
        assertEquals("/uploads/community-image/seller/avatar.jpg", detail.getAuthorAvatar());
        assertEquals("杭州", detail.getCity());
        assertEquals("真实评论者", comment.getAuthorName());
        assertEquals("/uploads/community-image/commenter/avatar.jpg", comment.getAuthorAvatar());
        assertEquals("真实评论者", detailComment.getAuthorName());
        assertEquals("/uploads/community-image/commenter/avatar.jpg", detailComment.getAuthorAvatar());
    }

    @Test
    void commentShouldNotifyPostAuthorExceptSelfComment() {
        seedUser(12L, "发帖人");
        seedUser(18L, "评论者");
        CommunityPostResponse post = createPost(12L, post("评论通知", "交易经验", "评论后作者应该收到平台通知。", List.of()));

        addComment(18L, post.getPostId(), comment("这个经验很有用，我收藏一下。"));
        addComment(12L, post.getPostId(), comment("我补充一下自己的说明。"));

        var notices = new com.secondhand.platform.modules.notification.application.NotificationApplicationService(new JdbcTemplate(database))
                .listNotifications(12L, "COMMENT", 20);
        assertEquals(1, notices.size());
        assertEquals("你的动态有新评论", notices.get(0).title());
        assertEquals("评论者 评论：这个经验很有用，我收藏一下。", notices.get(0).description());
        assertEquals("/pages/community/detail/index?postId=" + post.getPostId(), notices.get(0).targetUrl());
    }

    @Test
    void likeShouldBeIdempotentAndPersistedPerUser() {
        CommunityPostResponse post = createPost(13L, post("交易流程经验", "交易经验", "建议交付安排和沟通记录都以后端订单与聊天记录为准。", List.of()));

        CommunityPostDetailResponse liked = likePost(21L, post.getPostId());
        CommunityPostDetailResponse replay = likePost(21L, post.getPostId());
        assertEquals(1, liked.getLikeCount());
        assertTrue(liked.getLikedByMe());
        assertEquals(1, replay.getLikeCount());
        assertTrue(replay.getLikedByMe());

        likePost(22L, post.getPostId());
        CommunityApplicationService reloaded = new CommunityApplicationService(new JdbcTemplate(database), new com.secondhand.platform.modules.media.application.MediaUploadTicketService(new JdbcTemplate(database)));
        CommunityPostDetailResponse detail = reloaded.detail(post.getPostId(), 21L);
        assertEquals(2, detail.getLikeCount());
        assertTrue(detail.getLikedByMe());
    }

    @Test
    void likeShouldNotifyPostAuthorOnlyOnFirstExternalLike() {
        seedUser(13L, "动态作者");
        seedUser(21L, "点赞者");
        CommunityPostResponse post = createPost(13L, post("点赞通知", "交易经验", "点赞后作者应该收到平台通知。", List.of()));

        likePost(21L, post.getPostId());
        likePost(21L, post.getPostId());
        likePost(13L, post.getPostId());

        var notices = new com.secondhand.platform.modules.notification.application.NotificationApplicationService(new JdbcTemplate(database))
                .listNotifications(13L, "LIKE", 20);
        assertEquals(1, notices.size());
        assertEquals("你的动态收到了点赞", notices.get(0).title());
        assertEquals("点赞者 点赞了《点赞通知》", notices.get(0).description());
        assertEquals("/pages/community/detail/index?postId=" + post.getPostId(), notices.get(0).targetUrl());
    }

    @Test
    void listShouldReturnViewerScopedLikedByMeForFeedRows() {
        CommunityPostResponse post = createPost(24L, post("社区点赞状态", "交易经验", "列表页点赞状态必须由后端按当前用户返回。", List.of()));
        likePost(31L, post.getPostId());

        CommunityPostResponse viewerListRow = service.listPublishedPosts(20, 31L).get(0);
        CommunityPostResponse otherViewerListRow = service.listPublishedPosts(20, 32L).get(0);

        assertTrue(viewerListRow.getLikedByMe());
        assertFalse(otherViewerListRow.getLikedByMe());
        assertEquals(1, viewerListRow.getLikeCount());
    }

    @Test
    void listAndDetailShouldReturnViewerScopedFollowedByMeForFeedAuthors() {
        CommunityPostResponse post = createPost(27L, post("社区关注状态", "生活日常", "列表页关注状态必须由后端按当前用户返回。", List.of()));
        followUser(31L, 27L);

        CommunityPostResponse viewerListRow = service.listPublishedPosts(20, 31L).get(0);
        CommunityPostResponse otherViewerListRow = service.listPublishedPosts(20, 32L).get(0);
        CommunityPostResponse anonymousListRow = service.listPublishedPosts(20, null).get(0);
        CommunityPostResponse selfListRow = service.listPublishedPosts(20, 27L).get(0);
        CommunityPostDetailResponse viewerDetail = service.detail(post.getPostId(), 31L);

        assertTrue(viewerListRow.getFollowedByMe());
        assertTrue(viewerDetail.getFollowedByMe());
        assertFalse(otherViewerListRow.getFollowedByMe());
        assertFalse(anonymousListRow.getFollowedByMe());
        assertFalse(selfListRow.getFollowedByMe());
    }

    @Test
    void listShouldFilterPublishedPostsByTopicOnBackend() {
        CommunityPostResponse life = createPost(41L, post("生活话题后端筛选", "生活日常", "社区话题必须由后端筛选，避免最新列表误空。", List.of()));
        createPost(42L, post("避坑话题后端筛选", "闲置避坑", "其他话题不能混入当前话题列表。", List.of()));
        likePost(31L, life.getPostId());

        List<CommunityPostResponse> rows = service.listPublishedPosts(20, 31L, "生活日常");

        assertEquals(1, rows.size());
        assertEquals(life.getPostNo(), rows.get(0).getPostNo());
        assertEquals("生活日常", rows.get(0).getTopic());
        assertTrue(rows.get(0).getLikedByMe());
    }

    @Test
    void listPublishedPostPageShouldUseStableCursorAndKeepTopicFilter() {
        CommunityPostResponse older = createPost(60L, post("旧帖可继续翻页", "生活日常", "社区不能只展示最新一屏，旧帖也要能继续触达。", List.of()));
        CommunityPostResponse sameTimeLowId = createPost(61L, post("同秒低编号动态", "生活日常", "同一时间发帖时，翻页不能漏掉低编号动态。", List.of()));
        CommunityPostResponse sameTimeHighId = createPost(62L, post("同秒高编号动态", "生活日常", "同一时间发帖时，翻页要按编号稳定排序。", List.of()));
        createPost(63L, post("其他话题不混入", "闲置避坑", "话题筛选必须在后端分页查询中继续生效。", List.of()));
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.update("UPDATE community_post SET created_at = TIMESTAMP '2026-06-01 10:00:00' WHERE id IN (?, ?)", sameTimeLowId.getPostId(), sameTimeHighId.getPostId());
        jdbc.update("UPDATE community_post SET created_at = TIMESTAMP '2026-05-31 10:00:00' WHERE id = ?", older.getPostId());
        jdbc.update("UPDATE community_post SET created_at = TIMESTAMP '2026-06-02 10:00:00' WHERE topic = '闲置避坑'");
        likePost(31L, sameTimeHighId.getPostId());

        CommunityPostPageResponse first = service.listPublishedPostPage(2, 31L, "生活日常", null);

        assertEquals(2, first.getPosts().size());
        assertTrue(first.getHasMore());
        assertNotNull(first.getNextCursor());
        assertEquals(sameTimeHighId.getPostId(), first.getPosts().get(0).getPostId());
        assertEquals(sameTimeLowId.getPostId(), first.getPosts().get(1).getPostId());
        assertTrue(first.getPosts().get(0).getLikedByMe());

        CommunityPostPageResponse second = service.listPublishedPostPage(2, 31L, "生活日常", first.getNextCursor());

        assertEquals(1, second.getPosts().size());
        assertFalse(second.getHasMore());
        assertNull(second.getNextCursor());
        assertEquals(older.getPostId(), second.getPosts().get(0).getPostId());
        assertEquals("生活日常", second.getPosts().get(0).getTopic());
    }

    @Test
    void listPublishedPostPageShouldRejectInvalidCursor() {
        createPost(64L, post("非法游标拦截", "生活日常", "社区翻页不能遇到非法游标后静默回到第一页。", List.of()));

        assertEquals("community feed cursor invalid", assertThrows(IllegalArgumentException.class,
                () -> service.listPublishedPostPage(20, 31L, "生活日常", "preview-cursor")).getMessage());
        assertEquals("community feed cursor invalid", assertThrows(IllegalArgumentException.class,
                () -> service.listPublishedPostPage(20, 31L, "生活日常", "not-a-valid-cursor")).getMessage());
    }

    @Test
    void postShouldPersistAndExposeApprovedVisibleRelatedProduct() {
        seedProduct(701L, 12L, "关联的小裙子", "129.00", "ACTIVE", "APPROVED", true);
        CreateCommunityPostRequest request = post("关联商品动态", "生活日常", "这条动态会挂到真实在售商品详情。", List.of());
        request.setRelatedProductId(701L);

        CommunityPostResponse created = createPost(12L, request);
        CommunityPostResponse listRow = service.listPublishedPosts(20, 31L).get(0);
        CommunityPostDetailResponse detail = service.detail(created.getPostId(), 31L);

        assertEquals(701L, created.getRelatedProductId());
        assertEquals("关联的小裙子", listRow.getRelatedProductTitle());
        assertEquals("129.00", listRow.getRelatedProductPrice().toPlainString());
        assertEquals(701L, detail.getRelatedProductId());
        assertEquals("关联的小裙子", detail.getRelatedProductTitle());
        assertEquals("129.00", detail.getRelatedProductPrice().toPlainString());
    }

    @Test
    void listAndDetailShouldHideRelatedProductAfterSellerCertificationRevoked() {
        seedProduct(704L, 12L, "认证卖家的关联商品", "188.00", "ACTIVE", "APPROVED", true);
        CreateCommunityPostRequest request = post("认证卖家商品动态", "交易经验", "社区商品卡必须跟随卖家认证状态。", List.of());
        request.setRelatedProductId(704L);
        CommunityPostResponse created = createPost(12L, request);

        new JdbcTemplate(database).update("UPDATE user_profile SET video_identity_status = 'REJECTED', video_verified = FALSE WHERE user_id = ?", 12L);

        CommunityPostResponse listRow = service.listPublishedPosts(20, 31L).get(0);
        CommunityPostDetailResponse detail = service.detail(created.getPostId(), 31L);

        assertEquals(created.getPostNo(), listRow.getPostNo());
        assertNull(listRow.getRelatedProductId());
        assertNull(listRow.getRelatedProductTitle());
        assertNull(listRow.getRelatedProductPrice());
        assertNull(detail.getRelatedProductId());
        assertNull(detail.getRelatedProductTitle());
        assertNull(detail.getRelatedProductPrice());
    }

    @Test
    void adminTraceShouldPreserveRelatedProductIdWhenPublicProductCardIsHidden() {
        seedProduct(706L, 12L, "下架后仍需追溯的商品", "166.00", "ACTIVE", "APPROVED", true);
        CreateCommunityPostRequest request = post("追溯关联商品动态", "交易经验", "后台追溯要保留帖子原始关联商品编号。", List.of());
        request.setRelatedProductId(706L);
        CommunityPostResponse created = createPost(12L, request);

        new JdbcTemplate(database).update("UPDATE product_item SET visible = FALSE WHERE id = ?", 706L);

        CommunityPostResponse publicListRow = service.listPublishedPosts(20, 31L).get(0);
        CommunityPostDetailResponse publicDetail = service.detail(created.getPostId(), 31L);
        CommunityPostDetailResponse adminDetail = service.adminDetail(created.getPostId().toString());

        assertNull(publicListRow.getRelatedProductId());
        assertNull(publicDetail.getRelatedProductId());
        assertEquals(706L, adminDetail.getRelatedProductId());
        assertEquals("下架后仍需追溯的商品", adminDetail.getRelatedProductTitle());
        assertEquals("166.00", adminDetail.getRelatedProductPrice().toPlainString());
    }

    @Test
    void adminTraceShouldFindParentPostByCommentNoOrContentEvenAfterCommentBlocked() {
        CommunityPostResponse post = createPost(51L, post("评论追溯父帖子", "闲置避坑", "后台处理评论举报时必须能定位到父帖子。", List.of()));
        CommunityCommentResponse comment = addComment(52L, post.getPostId(), comment("这条评论包含后台追溯关键词。"));

        List<CommunityPostResponse> byCommentNo = service.adminListPosts(comment.getCommentNo(), null, 20);
        List<CommunityPostResponse> byCommentContent = service.adminListPosts("后台追溯关键词", null, 20);

        assertEquals(1, byCommentNo.size());
        assertEquals(post.getPostNo(), byCommentNo.get(0).getPostNo());
        assertEquals(1, byCommentContent.size());
        assertEquals(post.getPostNo(), byCommentContent.get(0).getPostNo());

        new JdbcTemplate(database).update("UPDATE community_comment SET status = 'BLOCKED' WHERE comment_no = ?", comment.getCommentNo());

        List<CommunityPostResponse> byBlockedCommentNo = service.adminListPosts(comment.getCommentNo(), null, 20);
        CommunityPostDetailResponse adminDetail = service.adminDetail(post.getPostId().toString());

        assertEquals(1, byBlockedCommentNo.size());
        assertEquals(post.getPostNo(), byBlockedCommentNo.get(0).getPostNo());
        assertEquals(1, adminDetail.getComments().size());
        assertEquals(comment.getCommentNo(), adminDetail.getComments().get(0).getCommentNo());
        assertTrue(service.detail(post.getPostId(), 52L).getComments().isEmpty());
    }

    @Test
    void postShouldRejectUnavailableRelatedProduct() {
        seedProduct(702L, 12L, "待审核商品", "88.00", "PENDING_AUDIT", "PENDING", false);
        CreateCommunityPostRequest request = post("不可关联商品", "生活日常", "没有上架审核通过的商品不能挂到社区。", List.of());
        request.setRelatedProductId(702L);

        assertEquals("invalid related product", assertThrows(IllegalArgumentException.class,
                () -> createPost(12L, request)).getMessage());
        request.setRelatedProductId(-1L);
        assertEquals("invalid related product", assertThrows(IllegalArgumentException.class,
                () -> createPost(12L, request)).getMessage());
    }

    @Test
    void postShouldRejectRelatedProductWhenSellerVideoAuditIsDirty() {
        seedProductWithoutVideoAudit(705L, 12L, "脏认证卖家商品", "99.00");
        CreateCommunityPostRequest request = post("脏认证商品动态", "交易经验", "社区关联商品必须绑定真实通过的视频认证审核记录。", List.of());
        request.setRelatedProductId(705L);

        assertEquals("invalid related product", assertThrows(IllegalArgumentException.class,
                () -> createPost(12L, request)).getMessage());

        String wrongVideoUrl = "/uploads/video-identity/12/wrong.mp4";
        new JdbcTemplate(database).update("""
                INSERT INTO audit_record (
                  audit_no, audit_type, user_id, target_type, target_id, reason, description, status, reviewed_at
                ) VALUES ('AUDIT-WRONG-COM-12', 'VIDEO_IDENTITY', 12, 'USER', '12', ?, '视频认证通过', 'APPROVED', CURRENT_TIMESTAMP)
                """, wrongVideoUrl);
        assertEquals("invalid related product", assertThrows(IllegalArgumentException.class,
                () -> createPost(12L, request)).getMessage());
    }

    @Test
    void postShouldRejectOtherSellerRelatedProduct() {
        seedProduct(703L, 99L, "别人家的商品", "66.00", "ACTIVE", "APPROVED", true);
        CreateCommunityPostRequest request = post("冒用商品动态", "交易经验", "社区动态不能挂载其他卖家的商品。", List.of());
        request.setRelatedProductId(703L);

        assertEquals("invalid related product", assertThrows(IllegalArgumentException.class,
                () -> createPost(12L, request)).getMessage());
    }

    @Test
    void listShouldRejectInvalidTopicFilter() {
        createPost(43L, post("合法话题", "生活日常", "非法话题筛选不能退化成全量列表。", List.of()));

        assertEquals("invalid topic", assertThrows(IllegalArgumentException.class,
                () -> service.listPublishedPosts(20, 31L, "穿搭交流")).getMessage());
        assertEquals("invalid topic", assertThrows(IllegalArgumentException.class,
                () -> service.listPublishedPosts(20, 31L, "demo")).getMessage());
    }

    @Test
    void unlikeShouldOnlyRemoveCurrentUsersLikeAndStayIdempotent() {
        CommunityPostResponse post = createPost(23L, post("点赞撤回", "求购心愿", "测试点赞撤回只影响当前用户。", List.of()));
        likePost(31L, post.getPostId());
        likePost(32L, post.getPostId());

        CommunityPostDetailResponse unliked = unlikePost(31L, post.getPostId());
        CommunityPostDetailResponse replay = unlikePost(31L, post.getPostId());

        assertEquals(1, unliked.getLikeCount());
        assertFalse(unliked.getLikedByMe());
        assertEquals(1, replay.getLikeCount());
        assertFalse(replay.getLikedByMe());
        assertFalse(service.detail(post.getPostId(), 31L).getLikedByMe());
        assertTrue(service.detail(post.getPostId(), 32L).getLikedByMe());
        assertThrows(IllegalArgumentException.class, () -> unlikePost(0L, post.getPostId()));
    }

    @Test
    void listShouldHideNonPublishedPostsAndCapLimit() {
        CommunityPostResponse post = createPost(14L, post("袜子护理", "生活日常", "清洗收纳经验不要暴晒，收纳前保持干燥。", List.of()));
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.update("UPDATE community_post SET status = 'BLOCKED' WHERE post_no = ?", post.getPostNo());

        assertTrue(service.listPublishedPosts(500).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> createPost(14L, post("短", "生活日常", "太短", List.of())));
    }

    @Test
    void publicCommunityShouldHideDisabledAuthorsWhileAdminTraceKeepsRecords() {
        seedUser(66L, "待封禁作者");
        seedUser(77L, "待封禁评论者");
        CommunityPostResponse post = createPost(66L, post("封禁后不可公开", "生活日常", "用户被禁用后，公开社区不能继续展示历史动态。", List.of()));
        addComment(77L, post.getPostId(), comment("评论作者被禁用后，公开详情也不能展示这条评论。"));

        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.update("UPDATE user_account SET status = 'DISABLED' WHERE id = ?", 66L);

        assertTrue(service.listPublishedPosts(20, 31L).isEmpty());
        assertEquals("post not found", assertThrows(IllegalArgumentException.class,
                () -> service.detail(post.getPostId(), 31L)).getMessage());

        CommunityPostDetailResponse adminDetail = service.adminDetail(post.getPostId().toString());
        assertEquals(post.getPostNo(), adminDetail.getPostNo());
        assertEquals(1, adminDetail.getComments().size());
    }

    @Test
    void publicPostDetailShouldHideDisabledCommentAuthors() {
        seedUser(88L, "正常作者");
        seedUser(89L, "封禁评论者");
        CommunityPostResponse post = createPost(88L, post("评论过滤", "交易经验", "详情页只展示活跃用户评论。", List.of()));
        addComment(89L, post.getPostId(), comment("这条评论在公开详情需要隐藏。"));
        new JdbcTemplate(database).update("UPDATE user_account SET status = 'DISABLED' WHERE id = ?", 89L);

        CommunityPostDetailResponse detail = service.detail(post.getPostId(), 31L);
        CommunityPostDetailResponse adminDetail = service.adminDetail(post.getPostId().toString());

        assertTrue(detail.getComments().isEmpty());
        assertEquals(1, adminDetail.getComments().size());
    }

    @Test
    void adminBlockPostShouldHidePublishedPostButKeepTraceableDetail() {
        seedUser(93L, "社区处置作者");
        CommunityPostResponse post = createPost(93L, post("社区屏蔽帖子", "生活日常", "后台运营需要能直接屏蔽违规帖子。", List.of()));

        CommunityPostDetailResponse blocked = service.adminBlockPost(post.getPostId().toString(), "社区内容违规");

        assertEquals("BLOCKED", blocked.getStatus());
        assertTrue(service.listPublishedPosts(20, 31L).isEmpty());
        assertEquals("post not found", assertThrows(IllegalArgumentException.class,
                () -> service.detail(post.getPostId(), 31L)).getMessage());
        assertEquals(post.getPostNo(), service.adminDetail(post.getPostId().toString()).getPostNo());
        assertEquals("community post cannot be blocked", assertThrows(IllegalArgumentException.class,
                () -> service.adminBlockPost(post.getPostId().toString(), "重复屏蔽帖子")).getMessage());
        assertEquals("community moderation reason invalid", assertThrows(IllegalArgumentException.class,
                () -> service.adminBlockPost(post.getPostId().toString(), "preview reason")).getMessage());

        CommunityPostDetailResponse restored = service.adminRestorePost(post.getPostId().toString(), "误封恢复帖子");

        assertEquals("PUBLISHED", restored.getStatus());
        assertEquals(1, service.listPublishedPosts(20, 31L).size());
        assertEquals("community post cannot be restored", assertThrows(IllegalArgumentException.class,
                () -> service.adminRestorePost(post.getPostId().toString(), "重复恢复帖子")).getMessage());
    }

    @Test
    void adminBlockCommentShouldHideOnlyThatCommentAndRefreshPublicCount() {
        seedUser(94L, "评论处置作者");
        seedUser(95L, "违规评论者");
        seedUser(96L, "正常评论者");
        CommunityPostResponse post = createPost(94L, post("社区屏蔽评论", "交易经验", "后台运营需要能直接屏蔽违规评论。", List.of()));
        CommunityCommentResponse blockedComment = addComment(95L, post.getPostId(), comment("这条评论需要后台屏蔽。"));
        CommunityCommentResponse keptComment = addComment(96L, post.getPostId(), comment("这条评论应继续公开展示。"));

        CommunityPostDetailResponse adminDetail = service.adminBlockComment(blockedComment.getCommentNo(), "评论内容违规");

        assertEquals(1, adminDetail.getCommentCount());
        assertEquals(2, adminDetail.getComments().size());
        assertEquals("BLOCKED", adminDetail.getComments().stream()
                .filter(row -> row.getCommentNo().equals(blockedComment.getCommentNo()))
                .findFirst()
                .orElseThrow()
                .getStatus());
        CommunityPostDetailResponse publicDetail = service.detail(post.getPostId(), 31L);
        assertEquals(1, publicDetail.getComments().size());
        assertEquals(keptComment.getCommentNo(), publicDetail.getComments().get(0).getCommentNo());
        assertEquals("community comment cannot be blocked", assertThrows(IllegalArgumentException.class,
                () -> service.adminBlockComment(blockedComment.getCommentNo(), "重复屏蔽评论")).getMessage());
        assertEquals("invalid comment no", assertThrows(IllegalArgumentException.class,
                () -> service.adminBlockComment("preview-comment", "评论违规")).getMessage());

        CommunityPostDetailResponse restored = service.adminRestoreComment(blockedComment.getCommentNo(), "误封恢复评论");

        assertEquals(2, restored.getCommentCount());
        assertTrue(restored.getComments().stream()
                .filter(row -> row.getCommentNo().equals(blockedComment.getCommentNo()))
                .findFirst()
                .orElseThrow()
                .getStatus()
                .equals("PUBLISHED"));
        assertEquals(2, service.detail(post.getPostId(), 31L).getComments().size());
        assertEquals("community comment cannot be restored", assertThrows(IllegalArgumentException.class,
                () -> service.adminRestoreComment(blockedComment.getCommentNo(), "重复恢复评论")).getMessage());
    }

    @Test
    void communityMutationsShouldRejectMissingOrDisabledUsersAtServiceLayer() {
        seedUser(91L, "正常作者");
        seedUser(92L, "禁用用户");
        CommunityPostResponse post = createPost(91L, post("服务层用户状态", "交易经验", "社区写入必须在服务层也校验账号状态。", List.of()));
        new JdbcTemplate(database).update("UPDATE user_account SET status = 'DISABLED' WHERE id = ?", 92L);

        assertEquals("invalid author", assertThrows(IllegalArgumentException.class,
                () -> service.createPost(92L, post("禁用用户发帖", "生活日常", "禁用用户不能绕过接口直接写入社区。", List.of()))).getMessage());
        assertEquals("invalid author", assertThrows(IllegalArgumentException.class,
                () -> service.createPost(9999L, post("缺失用户发帖", "生活日常", "缺失用户不能绕过接口直接写入社区。", List.of()))).getMessage());
        assertEquals("invalid author", assertThrows(IllegalArgumentException.class,
                () -> service.addComment(92L, post.getPostId(), comment("禁用用户不能评论。"))).getMessage());
        assertEquals("invalid user", assertThrows(IllegalArgumentException.class,
                () -> service.likePost(92L, post.getPostId())).getMessage());
        assertEquals("invalid user", assertThrows(IllegalArgumentException.class,
                () -> service.unlikePost(92L, post.getPostId())).getMessage());
    }

    @Test
    void createPostShouldRejectUnuploadedCommunityImages() {
        assertThrows(IllegalArgumentException.class, () -> createPost(15L,
                post("图片凭证校验", "生活日常", "社区图片必须先拿平台上传凭证。", List.of("https://img.example.com/fake.jpg"))));
        assertThrows(IllegalArgumentException.class, () -> createPost(15L,
                post("图片凭证校验", "生活日常", "社区图片必须先拿平台上传凭证。", List.of("local://temp/community.jpg"))));
        assertEquals("invalid image url", assertThrows(IllegalArgumentException.class, () -> createPost(15L,
                post("图片路径校验", "生活日常", "社区图片不能使用路径穿越编码。", List.of("/uploads/community-image/%2e%2e/evil.jpg")))).getMessage());
        assertEquals("invalid image url", assertThrows(IllegalArgumentException.class, () -> createPost(15L,
                post("图片路径校验", "生活日常", "社区图片不能使用浏览器本地 blob。", List.of("blob:https://example.com/1")))).getMessage());
        assertEquals("invalid image url", assertThrows(IllegalArgumentException.class, () -> createPost(15L,
                post("图片路径校验", "生活日常", "社区图片不能使用双斜杠路径。", List.of("/uploads/community-image//bad.jpg")))).getMessage());
    }

    private String issuedCommunityImage(Long userId, String filename) {
        ensureActiveUser(userId);
        String storageUrl = new com.secondhand.platform.modules.media.application.MediaUploadTicketService(new JdbcTemplate(database))
                .issue(userId, "COMMUNITY_IMAGE", "image/jpeg", 300_000L, filename)
                .storageUrl();
        new JdbcTemplate(database).update("UPDATE media_upload_ticket SET status = 'UPLOADED' WHERE owner_user_id = ? AND storage_url = ?", userId, storageUrl);
        return storageUrl;
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

    private int countCommunityPosts() {
        return new JdbcTemplate(database).queryForObject("SELECT COUNT(*) FROM community_post", Integer.class);
    }

    private void seedUser(Long userId, String nickname) {
        new JdbcTemplate(database).update("""
                INSERT INTO user_account (id, user_no, phone, password_hash, nickname, status)
                SELECT ?, ?, ?, 'hash', ?, 'ACTIVE'
                WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE id = ?)
                """, userId, "U-COM-" + userId, "1390000" + String.format("%04d", userId), nickname, userId);
    }

    private void followUser(Long followerId, Long followedId) {
        ensureActiveUser(followerId);
        ensureActiveUser(followedId);
        new JdbcTemplate(database).update("""
                INSERT INTO user_follow (follower_id, followed_id, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                """, followerId, followedId);
    }

    private void ensureActiveUser(Long userId) {
        new JdbcTemplate(database).update("""
                INSERT INTO user_account (id, user_no, phone, password_hash, nickname, status)
                SELECT ?, ?, ?, 'hash', ?, 'ACTIVE'
                WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE id = ?)
                """, userId, "U-COM-" + userId, "1390000" + String.format("%04d", userId), "社区用户" + userId, userId);
    }

    private CommunityPostResponse createPost(Long authorId, CreateCommunityPostRequest request) {
        ensureActiveUser(authorId);
        return service.createPost(authorId, request);
    }

    private CommunityCommentResponse addComment(Long authorId, Long postId, CreateCommunityCommentRequest request) {
        ensureActiveUser(authorId);
        return service.addComment(authorId, postId, request);
    }

    private CommunityPostDetailResponse likePost(Long userId, Long postId) {
        ensureActiveUser(userId);
        return service.likePost(userId, postId);
    }

    private CommunityPostDetailResponse unlikePost(Long userId, Long postId) {
        ensureActiveUser(userId);
        return service.unlikePost(userId, postId);
    }

    private void seedUserProfile(Long userId, String nickname, String avatarUrl, String city) {
        new JdbcTemplate(database).update("""
                INSERT INTO user_account (id, user_no, phone, password_hash, nickname, avatar_url, status)
                VALUES (?, ?, ?, 'hash', ?, ?, 'ACTIVE')
                """, userId, "U-COM-" + userId, "1390000" + String.format("%04d", userId), nickname, avatarUrl);
        new JdbcTemplate(database).update("""
                INSERT INTO user_profile (user_id, city, main_role, video_identity_status, video_verified)
                VALUES (?, ?, 'SELLER', 'APPROVED', TRUE)
                """, userId, city);
    }

    private void seedProduct(Long productId, Long sellerId, String title, String price, String status, String auditStatus, boolean visible) {
        new JdbcTemplate(database).update("""
                INSERT INTO user_account (id, user_no, phone, password_hash, nickname, status)
                VALUES (?, ?, ?, 'hash', ?, 'ACTIVE')
                """, sellerId, "U-PROD-" + sellerId, "1391000" + String.format("%04d", sellerId), "商品卖家" + sellerId);
        new JdbcTemplate(database).update("""
                INSERT INTO user_profile (user_id, main_role, video_identity_status, video_verified)
                VALUES (?, 'SELLER', 'APPROVED', TRUE)
                """, sellerId);
        String videoUrl = "/uploads/video-identity/" + sellerId + "/identity.mp4";
        new JdbcTemplate(database).update("""
                INSERT INTO media_upload_ticket (
                  ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at
                ) VALUES (?, ?, 'VIDEO_IDENTITY', 'identity.mp4', 'video/mp4', 1024, ?, 'hash', 'UPLOADED', CURRENT_TIMESTAMP, DATEADD('HOUR', 1, CURRENT_TIMESTAMP))
                """, "VIDEO-COM-" + sellerId, sellerId, videoUrl);
        new JdbcTemplate(database).update("""
                INSERT INTO audit_record (
                  audit_no, audit_type, user_id, target_type, target_id, reason, description, status, reviewed_at
                ) VALUES (?, 'VIDEO_IDENTITY', ?, 'USER', ?, ?, '视频认证通过', 'APPROVED', CURRENT_TIMESTAMP)
                """, "AUDIT-VIDEO-COM-" + sellerId, sellerId, String.valueOf(sellerId), videoUrl);
        new JdbcTemplate(database).update("""
                INSERT INTO product_item (
                  id, product_no, seller_id, title, category, price, product_status, audit_status, visible, trade_rule
                ) VALUES (?, ?, ?, ?, '女装', ?, ?, ?, ?, 'PLATFORM_ORDER')
                """, productId, "P-COM-" + productId, sellerId, title, price, status, auditStatus, visible);
    }

    private void seedProductWithoutVideoAudit(Long productId, Long sellerId, String title, String price) {
        new JdbcTemplate(database).update("""
                INSERT INTO user_account (id, user_no, phone, password_hash, nickname, status)
                VALUES (?, ?, ?, 'hash', ?, 'ACTIVE')
                """, sellerId, "U-PROD-" + sellerId, "1391000" + String.format("%04d", sellerId), "商品卖家" + sellerId);
        new JdbcTemplate(database).update("""
                INSERT INTO user_profile (user_id, main_role, video_identity_status, video_verified)
                VALUES (?, 'SELLER', 'APPROVED', TRUE)
                """, sellerId);
        String videoUrl = "/uploads/video-identity/" + sellerId + "/identity.mp4";
        new JdbcTemplate(database).update("""
                INSERT INTO media_upload_ticket (
                  ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at
                ) VALUES (?, ?, 'VIDEO_IDENTITY', 'identity.mp4', 'video/mp4', 1024, ?, 'hash', 'UPLOADED', CURRENT_TIMESTAMP, DATEADD('HOUR', 1, CURRENT_TIMESTAMP))
                """, "VIDEO-COM-DIRTY-" + sellerId, sellerId, videoUrl);
        new JdbcTemplate(database).update("""
                INSERT INTO product_item (
                  id, product_no, seller_id, title, category, price, product_status, audit_status, visible, trade_rule
                ) VALUES (?, ?, ?, ?, '女装', ?, 'ACTIVE', 'APPROVED', TRUE, 'PLATFORM_ORDER')
                """, productId, "P-COM-" + productId, sellerId, title, price);
    }
}
