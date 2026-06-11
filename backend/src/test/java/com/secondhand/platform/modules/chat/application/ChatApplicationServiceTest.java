package com.secondhand.platform.modules.chat.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.secondhand.platform.modules.chat.ChatMessageAck;
import com.secondhand.platform.modules.chat.ChatMessageResponse;
import com.secondhand.platform.modules.chat.ConversationListItemResponse;
import com.secondhand.platform.modules.chat.DeliveryReceiptResponse;
import com.secondhand.platform.modules.chat.MessageSyncResponse;
import com.secondhand.platform.modules.chat.ReadConversationResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class ChatApplicationServiceTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private ChatApplicationService service;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
	        jdbcTemplate = new JdbcTemplate(database);
	        service = new ChatApplicationService(jdbcTemplate);
	        insertUserAccount(1L, "买家一", "/uploads/avatar/user1.png");
	        insertUserAccount(2L, "卖家二", "/uploads/avatar/user2.png");
	        insertUserAccount(3L, "用户三", "/uploads/avatar/user3.png");
	    }

    @Test
    void singleConversationShouldBeUniqueRegardlessOfCreateOrder() {
        Long first = service.createConversation(conversation(1L, 2L));
        Long sameOrder = service.createConversation(conversation(1L, 2L));
        Long reversedOrder = service.createConversation(conversation(2L, 1L));

        assertEquals(first, sameOrder);
        assertEquals(first, reversedOrder);
        assertEquals(1, service.listConversations(1L).size());
        assertEquals(1, service.listConversations(2L).size());
    }

    @Test
    void shouldValidateTextAndImageMessages() {
        Long conversationId = service.createConversation(conversation(1L, 2L));

        ChatMessageAck textAck = service.sendMessage(text(conversationId, "c1", 1L, 2L, "hello"));
        String chatImageUrl = issueChatImageTicket(2L, "/uploads/chat-image/owner2-a.png");
        ChatMessageAck imageAck = service.sendMessage(image(conversationId, "c2", 2L, 1L, chatImageUrl));

        assertEquals(1L, textAck.getServerSeq());
        assertEquals("TEXT", textAck.getMsgType());
        assertEquals(2L, imageAck.getServerSeq());
        assertEquals("IMAGE", imageAck.getMsgType());
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(textWithContent(conversationId, "bad-text", 1L, 2L, "{\"text\":\"   \"}")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(textWithContent(conversationId, "missing-text", 1L, 2L, "{\"body\":\"hello\"}")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(textWithContent(conversationId, "number-text", 1L, 2L, "{\"text\":123}")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(textWithContent(conversationId, "null-text", 1L, 2L, "{\"text\":null}")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(textWithContent(conversationId, "malformed-text", 1L, 2L, "{\"text\":\"hello\"")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(image(conversationId, "bad-image", 1L, 2L, "ftp://cdn.example.com/a.png")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(image(conversationId, "local-image", 1L, 2L, "local://chat/a.png")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(image(conversationId, "placeholder-image", 1L, 2L, "/uploads/chat-image/placeholder.png")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(image(conversationId, "other-owner", 1L, 2L, chatImageUrl)));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(image(conversationId, "not-ticket", 1L, 2L, "/uploads/chat-image/no-ticket.png")));
        String expiredUploadedChatImageUrl = issueExpiredUploadedChatImageTicket(1L, "/uploads/chat-image/expired-owner1.png");
        ChatMessageAck expiredUploadedImageAck = service.sendMessage(image(conversationId, "expired-uploaded-ticket", 1L, 2L, expiredUploadedChatImageUrl));
        assertEquals(3L, expiredUploadedImageAck.getServerSeq());
        assertEquals("IMAGE", expiredUploadedImageAck.getMsgType());
    }

    @Test
    void textSummaryShouldUseParsedJsonAndPreserveEscapedCharacters() {
        Long conversationId = service.createConversation(conversation(1L, 2L));

        service.sendMessage(textWithContent(conversationId, "quote-text", 1L, 2L, "{\"text\":\"这是\\\"报价\\\"，可小刀\"}"));

        assertEquals("这是\"报价\"，可小刀", service.listConversations(1L).get(0).getLastMessageSummary());
    }

    @Test
    void textMessageShouldRejectContactInfoAndOffPlatformTrade() {
        Long conversationId = service.createConversation(conversation(1L, 2L));

        assertEquals("contact info is not allowed", assertThrows(IllegalArgumentException.class,
                () -> service.sendMessage(text(conversationId, "risk-phone", 1L, 2L, "联系 1 8 0-0 3 8 0-0 6 6 6"))).getMessage());
        assertEquals("contact info is not allowed", assertThrows(IllegalArgumentException.class,
                () -> service.sendMessage(text(conversationId, "risk-wechat", 1L, 2L, "加我微信聊"))).getMessage());
        assertEquals("contact info is not allowed", assertThrows(IllegalArgumentException.class,
                () -> service.sendMessage(text(conversationId, "risk-off-platform", 1L, 2L, "我们私下交易先转账"))).getMessage());
        assertEquals(0, jdbcTemplate.queryForObject("select count(1) from im_message where conversation_id = ?", Integer.class, conversationId));
    }

    @Test
    void sendingMessageShouldCreateOneChatNotificationForReceiver() {
        Long conversationId = service.createConversation(conversation(1L, 2L));

        service.sendMessage(text(conversationId, "notify-1", 1L, 2L, "hello"));
        service.sendMessage(text(conversationId, "notify-1", 1L, 2L, "hello"));

        List<String> rows = jdbcTemplate.query("""
                SELECT notification_type || '|' || title || '|' || description || '|' || target_url
                FROM notification_record
                WHERE user_id = ?
                ORDER BY id ASC
                """, (rs, rowNum) -> rs.getString(1), 2L);

        assertEquals(1, rows.size());
        assertEquals("CHAT|你有一条新私信|买家一 发来新消息：hello|/pages/chat/conversation/index?conversationId=" + conversationId + "&receiverId=1", rows.get(0));
    }

    @Test
    void shouldValidateVoiceMessagesAgainstUploadedVoiceTicket() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        String chatVoiceUrl = issueChatVoiceTicket(1L, "/uploads/chat-voice/owner1-a.webm");

        ChatMessageAck voiceAck = service.sendMessage(voice(conversationId, "v1", 1L, 2L, chatVoiceUrl));

        assertEquals(1L, voiceAck.getServerSeq());
        assertEquals("VOICE", voiceAck.getMsgType());
        assertEquals("[语音]", service.listConversations(1L).get(0).getLastMessageSummary());
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(voice(conversationId, "voice-local", 1L, 2L, "local://voice.webm")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(voice(conversationId, "voice-blob", 1L, 2L, "blob:https://example.com/a")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(voice(conversationId, "voice-http", 1L, 2L, "https://cdn.example.com/a.webm")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(voice(conversationId, "voice-placeholder", 1L, 2L, "/uploads/chat-voice/placeholder.webm")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(voice(conversationId, "voice-other-owner", 2L, 1L, chatVoiceUrl)));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(voice(conversationId, "voice-not-ticket", 1L, 2L, "/uploads/chat-voice/no-ticket.webm")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(voiceWithContent(conversationId, "voice-zero-duration", 1L, 2L,
                "{\"url\":\"" + chatVoiceUrl + "\",\"durationMs\":0,\"sizeBytes\":4096,\"mimeType\":\"audio/webm\"}")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(voiceWithContent(conversationId, "voice-bad-mime", 1L, 2L,
                "{\"url\":\"" + chatVoiceUrl + "\",\"durationMs\":1200,\"sizeBytes\":4096,\"mimeType\":\"video/mp4\"}")));
        String expiredUploadedChatVoiceUrl = issueExpiredUploadedChatVoiceTicket(1L, "/uploads/chat-voice/expired-owner1.webm");
        ChatMessageAck expiredUploadedVoiceAck = service.sendMessage(voice(conversationId, "expired-uploaded-voice-ticket", 1L, 2L, expiredUploadedChatVoiceUrl));
        assertEquals(2L, expiredUploadedVoiceAck.getServerSeq());
        assertEquals("VOICE", expiredUploadedVoiceAck.getMsgType());
    }

    @Test
    void shouldValidateVideoMessagesAgainstUploadedVideoTicket() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        String chatVideoUrl = issueChatVideoTicket(1L, "/uploads/chat-video/owner1-a.mp4");

        ChatMessageAck videoAck = service.sendMessage(video(conversationId, "video-1", 1L, 2L, chatVideoUrl));

        assertEquals(1L, videoAck.getServerSeq());
        assertEquals("VIDEO", videoAck.getMsgType());
        assertEquals("[视频]", service.listConversations(1L).get(0).getLastMessageSummary());
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(video(conversationId, "video-local", 1L, 2L, "local://video.mp4")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(video(conversationId, "video-blob", 1L, 2L, "blob:https://example.com/a")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(video(conversationId, "video-http", 1L, 2L, "https://cdn.example.com/a.mp4")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(video(conversationId, "video-placeholder", 1L, 2L, "/uploads/chat-video/placeholder.mp4")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(video(conversationId, "video-other-owner", 2L, 1L, chatVideoUrl)));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(video(conversationId, "video-not-ticket", 1L, 2L, "/uploads/chat-video/no-ticket.mp4")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(videoWithContent(conversationId, "video-zero-duration", 1L, 2L,
                "{\"url\":\"" + chatVideoUrl + "\",\"durationMs\":0,\"sizeBytes\":8192,\"mimeType\":\"video/mp4\"}")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(videoWithContent(conversationId, "video-bad-mime", 1L, 2L,
                "{\"url\":\"" + chatVideoUrl + "\",\"durationMs\":1200,\"sizeBytes\":8192,\"mimeType\":\"audio/webm\"}")));
        String expiredUploadedChatVideoUrl = issueExpiredUploadedChatVideoTicket(1L, "/uploads/chat-video/expired-owner1.mp4");
        ChatMessageAck expiredUploadedVideoAck = service.sendMessage(video(conversationId, "expired-uploaded-video-ticket", 1L, 2L, expiredUploadedChatVideoUrl));
        assertEquals(2L, expiredUploadedVideoAck.getServerSeq());
        assertEquals("VIDEO", expiredUploadedVideoAck.getMsgType());
    }

    @Test
    void chatMediaAccessShouldRequireExactMessageContentUrlField() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        String imageUrl = issueChatImageTicket(1L, "/uploads/chat-image/owner1-access.png");
        String videoUrl = issueChatVideoTicket(1L, "/uploads/chat-video/owner1-access.mp4");
        String doubleEncodedVoiceUrl = issueChatVoiceTicket(1L, "/uploads/chat-voice/owner1-double.webm");
        String legacyEscapedVoiceUrl = issueChatVoiceTicket(1L, "/uploads/chat-voice/owner1-escaped.webm");
        String smuggledVoiceUrl = issueChatVoiceTicket(3L, "/uploads/chat-voice/owner3-smuggled.webm");

        service.sendMessage(image(conversationId, "media-access-image", 1L, 2L, imageUrl));
        service.sendMessage(video(conversationId, "media-access-video", 1L, 2L, videoUrl));
        insertLegacyVoiceMessage(conversationId, "legacy-double-voice", 1L, 2L, doubleEncodedVoiceUrl, true);
        insertLegacyVoiceMessage(conversationId, "legacy-escaped-voice", 1L, 2L, legacyEscapedVoiceUrl, false);
        service.sendMessage(textWithContent(conversationId, "media-access-smuggled-text", 1L, 2L,
                "{\"text\":\"hello\",\"extra\":\"" + smuggledVoiceUrl + "\"}"));

        var access = service.requireChatMediaAccess(2L, imageUrl);
        var videoAccess = service.requireChatMediaAccess(2L, videoUrl);
        var doubleEncodedVoiceAccess = service.requireChatMediaAccess(2L, doubleEncodedVoiceUrl);
        var legacyEscapedVoiceAccess = service.requireChatMediaAccess(2L, legacyEscapedVoiceUrl);

        assertEquals(imageUrl, access.storageUrl());
        assertEquals(conversationId, access.conversationId());
        assertEquals(videoUrl, videoAccess.storageUrl());
        assertEquals(conversationId, videoAccess.conversationId());
        assertEquals(doubleEncodedVoiceUrl, doubleEncodedVoiceAccess.storageUrl());
        assertEquals(conversationId, doubleEncodedVoiceAccess.conversationId());
        assertEquals(legacyEscapedVoiceUrl, legacyEscapedVoiceAccess.storageUrl());
        assertEquals(conversationId, legacyEscapedVoiceAccess.conversationId());
        assertThrows(IllegalArgumentException.class, () -> service.requireChatMediaAccess(2L, smuggledVoiceUrl));
        assertThrows(IllegalArgumentException.class, () -> service.requireChatMediaAccess(3L, doubleEncodedVoiceUrl));
    }

    @Test
    void chatMediaAccessShouldAuthorizeLegacySlashEscapedMediaUrl() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        String slashEscapedVoiceUrl = issueChatVoiceTicket(1L, "/uploads/chat-voice/owner1-slash-escaped.webm");

        insertSlashEscapedVoiceMessage(conversationId, "legacy-slash-voice", 1L, 2L, slashEscapedVoiceUrl);

        var access = service.requireChatMediaAccess(2L, slashEscapedVoiceUrl);

        assertEquals(slashEscapedVoiceUrl, access.storageUrl());
        assertEquals(conversationId, access.conversationId());
        assertThrows(IllegalArgumentException.class, () -> service.requireChatMediaAccess(3L, slashEscapedVoiceUrl));
    }

    @Test
    void chatMediaAccessShouldAuthorizeOlderMediaBeyondLatestWindow() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        String olderVoiceUrl = issueChatVoiceTicket(1L, "/uploads/chat-voice/owner1-oldest.webm");

        service.sendMessage(voice(conversationId, "old-voice", 1L, 2L, olderVoiceUrl));
        for (int index = 1; index <= 201; index += 1) {
            String newerVoiceUrl = issueChatVoiceTicket(1L, "/uploads/chat-voice/owner1-newer-" + index + ".webm");
            service.sendMessage(voice(conversationId, "newer-voice-" + index, 1L, 2L, newerVoiceUrl));
        }

        var access = service.requireChatMediaAccess(2L, olderVoiceUrl);

        assertEquals(olderVoiceUrl, access.storageUrl());
        assertEquals(conversationId, access.conversationId());
        assertEquals(1L, access.senderId());
        assertEquals(2L, access.receiverId());
    }

    @Test
    void chatMediaAccessShouldRejectDirtyMessageWhoseTicketOwnerIsNotSender() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        String dirtyVoiceUrl = issueChatVoiceTicket(3L, "/uploads/chat-voice/owner3-dirty.webm");
        insertLegacyVoiceMessage(conversationId, "legacy-dirty-owner", 1L, 2L, dirtyVoiceUrl, false);

        assertEquals("chat media access denied", assertThrows(IllegalArgumentException.class,
                () -> service.requireChatMediaAccess(1L, dirtyVoiceUrl)).getMessage());
        assertEquals("chat media access denied", assertThrows(IllegalArgumentException.class,
                () -> service.requireChatMediaAccess(2L, dirtyVoiceUrl)).getMessage());
        assertEquals("chat media access denied", assertThrows(IllegalArgumentException.class,
                () -> service.requireChatMediaAccess(3L, dirtyVoiceUrl)).getMessage());
    }

    @Test
    void listConversationsShouldIncludePeerProfileFields() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        updateUserAccount(2L, "真实卖家", "/uploads/avatar/seller.png");
        insertUserProfile(2L, "goddess", "杭州", "SELLER", true);
        approveVideoIdentity(2L, issueVideoIdentityTicket(2L));
        insertGiftOrder("GIFT-2", 1L, 2L, 88);
        insertTradeOrder("TRADE-2", 2L, 1L, 36);
        service.sendMessage(text(conversationId, "profile-1", 1L, 2L, "hello"));

        ConversationListItemResponse item = service.listConversations(1L).get(0);

        assertEquals(2L, item.getPeerUserId());
        assertEquals("真实卖家", item.getPeerNickname());
        assertEquals("/uploads/avatar/seller.png", item.getPeerAvatarUrl());
        assertEquals("goddess", item.getPeerGender());
        assertEquals("杭州", item.getPeerCity());
        assertEquals("SELLER", item.getPeerMainRole());
        assertTrue(item.getPeerVideoVerified());
        assertEquals(88, item.getPeerSellerCharmScore());
        assertEquals(36, item.getPeerBuyerPowerScore());
	    }

    @Test
    void chatPeerVideoBadgeShouldRequireApprovedSellerVideoIdentity() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        service.sendMessage(text(conversationId, "video-badge-1", 1L, 2L, "hello"));
        insertUserProfileWithVideoStatus(2L, "goddess", "杭州", "SELLER", "PENDING", true);

        ConversationListItemResponse pendingItem = service.listConversations(1L).get(0);
        assertFalse(pendingItem.getPeerVideoVerified());

        jdbcTemplate.update("UPDATE user_profile SET video_identity_status = 'APPROVED', video_verified = TRUE WHERE user_id = ?", 2L);
        ConversationListItemResponse dirtyApprovedItem = service.listConversations(1L).get(0);
        assertFalse(dirtyApprovedItem.getPeerVideoVerified());
        assertFalse(service.getConversation(conversationId, 1L).getPeerVideoVerified());

        String videoUrl = issueVideoIdentityTicket(2L);
        ConversationListItemResponse ticketOnlyItem = service.listConversations(1L).get(0);
        assertFalse(ticketOnlyItem.getPeerVideoVerified());

        approveVideoIdentity(2L, "/uploads/video-identity/2/other.mp4");
        ConversationListItemResponse mismatchedAuditItem = service.listConversations(1L).get(0);
        assertFalse(mismatchedAuditItem.getPeerVideoVerified());
        assertFalse(service.getConversation(conversationId, 1L).getPeerVideoVerified());

        approveVideoIdentity(2L, videoUrl);
        ConversationListItemResponse approvedItem = service.listConversations(1L).get(0);
        assertTrue(approvedItem.getPeerVideoVerified());
        assertTrue(service.getConversation(conversationId, 1L).getPeerVideoVerified());

        jdbcTemplate.update("UPDATE user_profile SET main_role = 'BUYER' WHERE user_id = ?", 2L);
        ConversationListItemResponse buyerItem = service.listConversations(1L).get(0);
        assertFalse(buyerItem.getPeerVideoVerified());
    }

	    @Test
	    void shouldRejectMissingOrInactiveChatParticipants() {
	        assertThrows(IllegalArgumentException.class, () -> service.createConversation(conversation(1L, 999L)));

	        insertInactiveUserAccount(4L, "停用用户", "/uploads/avatar/inactive.png");

	        assertThrows(IllegalArgumentException.class, () -> service.createConversation(conversation(1L, 4L)));
	        Long conversationId = service.createConversation(conversation(1L, 2L));
	        markUserInactive(2L);
	        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(text(conversationId, "inactive-send", 1L, 2L, "hello")));
	    }

	    @Test
	    void listConversationsShouldHideRowsWhosePeerIsMissingOrInactive() {
	        Long activeConversationId = service.createConversation(conversation(1L, 2L));
	        service.sendMessage(text(activeConversationId, "active-peer", 1L, 2L, "hello"));
	        insertInactiveUserAccount(4L, "停用用户", "/uploads/avatar/inactive.png");
	        jdbcTemplate.update("""
	                INSERT INTO im_conversation (
	                  conversation_no, owner_user_id, peer_user_id, conversation_type, last_seq, last_message_summary, created_at, updated_at
	                ) VALUES ('IM-SINGLE-1-4', 1, 4, 'SINGLE', 1, 'ghost', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
	                """);
	        jdbcTemplate.update("""
	                INSERT INTO im_conversation (
	                  conversation_no, owner_user_id, peer_user_id, conversation_type, last_seq, last_message_summary, created_at, updated_at
	                ) VALUES ('IM-SINGLE-1-999', 1, 999, 'SINGLE', 1, 'missing', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
	                """);

	        List<ConversationListItemResponse> rows = service.listConversations(1L);

	        assertEquals(1, rows.size());
	        assertEquals(2L, rows.get(0).getPeerUserId());
	    }

    @Test
    void syncShouldRespectAfterSeqLimitAndUpdateDeliveredSeq() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        service.sendMessage(text(conversationId, "c1", 1L, 2L, "one"));
        service.sendMessage(text(conversationId, "c2", 2L, 1L, "two"));
        service.sendMessage(text(conversationId, "c3", 1L, 2L, "three"));

        MessageSyncResponse firstPage = service.syncMessages(conversationId, 2L, 0L, 2);

        assertEquals(2, firstPage.getMessages().size());
        assertEquals(2L, firstPage.getNextAfterSeq());
        assertTrue(firstPage.getHasMore());
        assertEquals("c1", firstPage.getMessages().get(0).getClientMsgId());
        assertEquals("c2", firstPage.getMessages().get(1).getClientMsgId());

        MessageSyncResponse secondPage = service.syncMessages(conversationId, 2L, firstPage.getNextAfterSeq(), 2);
        assertEquals(1, secondPage.getMessages().size());
        assertEquals(3L, secondPage.getNextAfterSeq());
        assertFalse(secondPage.getHasMore());

        DeliveryReceiptResponse delivered = service.markConversationDelivered(conversationId, 2L);
        assertEquals(3L, delivered.getDeliveredSeq());
        assertEquals(3L, delivered.getLastServerSeq());
        assertEquals(2L, delivered.getUnreadCount());
        assertEquals(3L, service.listConversations(2L).get(0).getDeliveredSeq());
    }

    @Test
    void recentSyncShouldReturnLatestWindowForLongConversation() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        for (int index = 1; index <= 60; index += 1) {
            service.sendMessage(text(conversationId, "long-" + index, 1L, 2L, "message " + index));
        }

        MessageSyncResponse firstForwardPage = service.syncMessages(conversationId, 2L, 0L, 50);
        MessageSyncResponse latestPage = service.syncRecentMessages(conversationId, 2L, 50);

        assertEquals(50, firstForwardPage.getMessages().size());
        assertEquals("long-1", firstForwardPage.getMessages().get(0).getClientMsgId());
        assertEquals("long-50", firstForwardPage.getMessages().get(49).getClientMsgId());
        assertTrue(firstForwardPage.getHasMore());

        assertEquals(50, latestPage.getMessages().size());
        assertEquals("long-11", latestPage.getMessages().get(0).getClientMsgId());
        assertEquals("long-60", latestPage.getMessages().get(49).getClientMsgId());
        assertEquals(60L, latestPage.getNextAfterSeq());
        assertFalse(latestPage.getHasMore());
        assertEquals(11L, latestPage.getPreviousBeforeSeq());
        assertTrue(latestPage.getHasEarlier());
        assertEquals(60L, service.listConversations(2L).get(0).getDeliveredSeq());

        MessageSyncResponse earlierPage = service.syncEarlierMessages(conversationId, 2L, latestPage.getPreviousBeforeSeq(), 50);

        assertEquals(10, earlierPage.getMessages().size());
        assertEquals("long-1", earlierPage.getMessages().get(0).getClientMsgId());
        assertEquals("long-10", earlierPage.getMessages().get(9).getClientMsgId());
        assertEquals(1L, earlierPage.getPreviousBeforeSeq());
        assertFalse(earlierPage.getHasEarlier());
    }

    @Test
    void unreadCountShouldOnlyIncludeMessagesReceivedByViewer() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        service.sendMessage(text(conversationId, "c1", 1L, 2L, "one"));
        service.sendMessage(text(conversationId, "c2", 2L, 1L, "two"));
        service.sendMessage(text(conversationId, "c3", 1L, 2L, "three"));

        assertEquals(1L, service.listConversations(1L).get(0).getUnreadCount());
        assertEquals(2L, service.listConversations(2L).get(0).getUnreadCount());

        ReadConversationResponse readBySecondUser = service.markConversationRead(conversationId, 2L, 1L);

        assertEquals(1L, readBySecondUser.getUnreadCount());
        assertEquals(1L, service.listConversations(2L).get(0).getUnreadCount());
    }

    @Test
    void unreadCountShouldIgnoreRevokedMessages() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        ChatMessageAck ack = service.sendMessage(text(conversationId, "revoked-unread", 1L, 2L, "撤回后不应红点提醒"));

        assertEquals(1L, service.listConversations(2L).get(0).getUnreadCount());

        service.revokeMessage(ack.getServerMsgId(), 1L);

        assertEquals(0L, service.listConversations(2L).get(0).getUnreadCount());
        assertEquals(0L, service.markConversationDelivered(conversationId, 2L).getUnreadCount());
        assertEquals(0L, service.markConversationRead(conversationId, 2L, null).getUnreadCount());
    }

    @Test
    void deliveredAndReadReceiptsShouldBeVisibleToSenderOnSync() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        service.sendMessage(text(conversationId, "c1", 1L, 2L, "one"));
        service.sendMessage(text(conversationId, "c2", 1L, 2L, "two"));

        service.markConversationDelivered(conversationId, 2L);
        MessageSyncResponse senderBeforeRead = service.syncMessages(conversationId, 1L, 0L, 10);

        ChatMessageResponse firstBeforeRead = senderBeforeRead.getMessages().get(0);
        assertTrue(firstBeforeRead.getDeliveredToReceiver());
        assertFalse(firstBeforeRead.getReadByReceiver());

        ReadConversationResponse read = service.markConversationRead(conversationId, 2L, 1L);
        assertEquals(1L, read.getReadSeq());
        assertEquals(2L, read.getDeliveredSeq());
        assertEquals(1L, read.getUnreadCount());

        MessageSyncResponse senderAfterRead = service.syncMessages(conversationId, 1L, 0L, 10);
        assertTrue(senderAfterRead.getMessages().get(0).getReadByReceiver());
        assertFalse(senderAfterRead.getMessages().get(1).getReadByReceiver());

        MessageSyncResponse receiverView = service.syncMessages(conversationId, 2L, 0L, 10);
        assertNull(receiverView.getMessages().get(0).getDeliveredToReceiver());
        assertNull(receiverView.getMessages().get(0).getReadByReceiver());
    }

    @Test
    void senderCanRevokeMessageWhileAuditContentStaysPersisted() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        ChatMessageAck textAck = service.sendMessage(text(conversationId, "revoke-text", 1L, 2L, "可撤回消息"));

        var revoked = service.revokeMessage(textAck.getServerMsgId(), 1L);

        assertEquals(conversationId, revoked.getConversationId());
        assertEquals(textAck.getServerSeq(), revoked.getServerSeq());
        assertTrue(revoked.getRevoked());
        MessageSyncResponse receiverView = service.syncMessages(conversationId, 2L, 0L, 10);
        assertEquals(1, receiverView.getMessages().size());
        assertTrue(receiverView.getMessages().get(0).getRevoked());
        assertEquals("{\"revoked\":true}", receiverView.getMessages().get(0).getContentJson());
        assertEquals("{\"text\":\"可撤回消息\"}", jdbcTemplate.queryForObject("select content_json from im_message where message_no = ?", String.class, textAck.getServerMsgId()));
        assertEquals("消息已撤回", service.listConversations(2L).get(0).getLastMessageSummary());
    }

    @Test
    void senderCannotRevokeMessageAfterWindowExpired() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        ChatMessageAck textAck = service.sendMessage(text(conversationId, "revoke-expired", 1L, 2L, "超过窗口不能撤回"));
        jdbcTemplate.update("update im_message set created_at = DATEADD('MINUTE', -3, CURRENT_TIMESTAMP), updated_at = DATEADD('MINUTE', -3, CURRENT_TIMESTAMP) where message_no = ?", textAck.getServerMsgId());

        assertEquals("message revoke window expired", assertThrows(IllegalStateException.class,
                () -> service.revokeMessage(textAck.getServerMsgId(), 1L)).getMessage());

        MessageSyncResponse receiverView = service.syncMessages(conversationId, 2L, 0L, 10);
        assertFalse(receiverView.getMessages().get(0).getRevoked());
        assertEquals("{\"text\":\"超过窗口不能撤回\"}", receiverView.getMessages().get(0).getContentJson());
    }

    @Test
    void nonSenderCannotRevokeMessage() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        ChatMessageAck textAck = service.sendMessage(text(conversationId, "revoke-denied", 1L, 2L, "不能被对方撤回"));

        assertThrows(IllegalArgumentException.class, () -> service.revokeMessage(textAck.getServerMsgId(), 2L));
        MessageSyncResponse receiverView = service.syncMessages(conversationId, 2L, 0L, 10);
        assertFalse(receiverView.getMessages().get(0).getRevoked());
    }

    @Test
    void clearConversationOnlyHidesHistoryForCurrentUser() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        service.sendMessage(text(conversationId, "clear-1", 1L, 2L, "one"));
        service.sendMessage(text(conversationId, "clear-2", 2L, 1L, "two"));

        var cleared = service.clearConversation(conversationId, 1L);

        assertEquals(conversationId, cleared.getConversationId());
        assertEquals(2L, cleared.getClearedSeq());
        assertEquals(0, service.listConversations(1L).size());
        ConversationListItemResponse clearedConversation = service.getConversation(conversationId, 1L);
        assertEquals(2L, clearedConversation.getPeerUserId());
        assertNull(clearedConversation.getLastMessageSummary());
        assertEquals(2L, clearedConversation.getLastServerSeq());
        assertEquals(0L, clearedConversation.getUnreadCount());
        assertEquals(0L, clearedConversation.getReadSeq());
        assertEquals(0L, clearedConversation.getDeliveredSeq());
        assertEquals(0, service.syncMessages(conversationId, 1L, 0L, 10).getMessages().size());
        assertEquals(1, service.listConversations(2L).size());
        assertEquals(2, service.syncMessages(conversationId, 2L, 0L, 10).getMessages().size());

        service.sendMessage(text(conversationId, "clear-3", 2L, 1L, "three"));

        assertEquals(1, service.listConversations(1L).size());
        ConversationListItemResponse visibleAfterNewMessage = service.getConversation(conversationId, 1L);
        assertEquals(2L, visibleAfterNewMessage.getPeerUserId());
        assertEquals("three", visibleAfterNewMessage.getLastMessageSummary());
        assertEquals(3L, visibleAfterNewMessage.getLastServerSeq());
        assertEquals(1L, visibleAfterNewMessage.getUnreadCount());
        assertEquals(0L, visibleAfterNewMessage.getReadSeq());
        assertEquals(0L, visibleAfterNewMessage.getDeliveredSeq());
        MessageSyncResponse firstUserView = service.syncMessages(conversationId, 1L, 0L, 10);
        assertEquals(1, firstUserView.getMessages().size());
        assertEquals("clear-3", firstUserView.getMessages().get(0).getClientMsgId());
    }

    @Test
    void clearConversationShouldDenyClearedViewerOldChatMediaAccess() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        String oldVoiceUrl = issueChatVoiceTicket(2L, "/uploads/chat-voice/owner2-clear-old.webm");
        String newVoiceUrl = issueChatVoiceTicket(2L, "/uploads/chat-voice/owner2-clear-new.webm");
        service.sendMessage(voice(conversationId, "clear-media-old", 2L, 1L, oldVoiceUrl));

        assertEquals(oldVoiceUrl, service.requireChatMediaAccess(1L, oldVoiceUrl).storageUrl());
        assertEquals(oldVoiceUrl, service.requireChatMediaAccess(2L, oldVoiceUrl).storageUrl());

        var cleared = service.clearConversation(conversationId, 1L);

        assertEquals(1L, cleared.getClearedSeq());
        assertEquals("chat media access denied", assertThrows(IllegalArgumentException.class,
                () -> service.requireChatMediaAccess(1L, oldVoiceUrl)).getMessage());
        assertEquals(oldVoiceUrl, service.requireChatMediaAccess(2L, oldVoiceUrl).storageUrl());

        service.sendMessage(voice(conversationId, "clear-media-new", 2L, 1L, newVoiceUrl));

        assertEquals(newVoiceUrl, service.requireChatMediaAccess(1L, newVoiceUrl).storageUrl());
        assertEquals(newVoiceUrl, service.requireChatMediaAccess(2L, newVoiceUrl).storageUrl());
    }

    @Test
    void nonParticipantShouldNotSyncOrMarkReceipts() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        service.sendMessage(text(conversationId, "c1", 1L, 2L, "one"));

        assertThrows(IllegalArgumentException.class, () -> service.syncMessages(conversationId, 3L, 0L, 10));
        assertThrows(IllegalArgumentException.class, () -> service.markConversationDelivered(conversationId, 3L));
        assertThrows(IllegalArgumentException.class, () -> service.markConversationRead(conversationId, 3L, null));
        assertThrows(IllegalArgumentException.class, () -> service.clearConversation(conversationId, 3L));
    }

    @Test
    void messagesAndReceiptsShouldSurviveServiceRecreation() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        ChatMessageAck ack = service.sendMessage(text(conversationId, "persist-1", 1L, 2L, "persist"));
        service.markConversationDelivered(conversationId, 2L);
        service.markConversationRead(conversationId, 2L, ack.getServerSeq());

        ChatApplicationService reloaded = new ChatApplicationService(jdbcTemplate);
        MessageSyncResponse senderView = reloaded.syncMessages(conversationId, 1L, 0L, 10);

        assertEquals(1, senderView.getMessages().size());
        assertEquals("persist-1", senderView.getMessages().get(0).getClientMsgId());
        assertTrue(senderView.getMessages().get(0).getDeliveredToReceiver());
        assertTrue(senderView.getMessages().get(0).getReadByReceiver());
        assertEquals(1L, reloaded.listConversations(1L).get(0).getLastServerSeq());
    }

	    private void insertUserAccount(Long userId, String nickname, String avatarUrl) {
	        jdbcTemplate.update("INSERT INTO user_account (id, user_no, phone, password_hash, nickname, avatar_url, status) VALUES (?, ?, ?, 'hash', ?, ?, 'ACTIVE')",
	                userId, "U" + userId, "1380000" + userId, nickname, avatarUrl);
	    }

	    private void updateUserAccount(Long userId, String nickname, String avatarUrl) {
	        jdbcTemplate.update("UPDATE user_account SET nickname = ?, avatar_url = ?, status = 'ACTIVE' WHERE id = ?", nickname, avatarUrl, userId);
	    }

	    private void insertInactiveUserAccount(Long userId, String nickname, String avatarUrl) {
	        jdbcTemplate.update("INSERT INTO user_account (id, user_no, phone, password_hash, nickname, avatar_url, status) VALUES (?, ?, ?, 'hash', ?, ?, 'DISABLED')",
	                userId, "U" + userId, "1380000" + userId, nickname, avatarUrl);
	    }

	    private void markUserInactive(Long userId) {
	        jdbcTemplate.update("UPDATE user_account SET status = 'DISABLED' WHERE id = ?", userId);
	    }

    private void insertUserProfile(Long userId, String gender, String city, String mainRole, boolean videoVerified) {
        jdbcTemplate.update("INSERT INTO user_profile (user_id, gender, city, main_role, video_identity_status, video_verified) VALUES (?, ?, ?, ?, ?, ?)",
                userId, gender, city, mainRole, videoVerified ? "APPROVED" : "UNVERIFIED", videoVerified);
    }

    private void insertUserProfileWithVideoStatus(Long userId, String gender, String city, String mainRole, String videoIdentityStatus, boolean videoVerified) {
        jdbcTemplate.update("INSERT INTO user_profile (user_id, gender, city, main_role, video_identity_status, video_verified) VALUES (?, ?, ?, ?, ?, ?)",
                userId, gender, city, mainRole, videoIdentityStatus, videoVerified);
    }

    private void insertGiftOrder(String giftOrderNo, Long senderId, Long receiverId, int totalAmount) {
        jdbcTemplate.update("""
                INSERT INTO gift_order (
                  gift_order_no, idempotency_key, sender_id, receiver_id, gift_id, gift_code, quantity,
                  total_amount, platform_share, receiver_amount, debit_ledger_no, receiver_credit_ledger_no, status
                ) VALUES (?, ?, ?, ?, 1, 'ROSE', 1, ?, 0, ?, ?, ?, 'SUCCESS')
                """, giftOrderNo, "IDEMP-" + giftOrderNo, senderId, receiverId, totalAmount, totalAmount,
                "LEDGER-D-" + giftOrderNo, "LEDGER-C-" + giftOrderNo);
    }

    private void insertTradeOrder(String orderNo, Long buyerId, Long sellerId, int amount) {
        jdbcTemplate.update("""
                INSERT INTO trade_order (
                  order_no, product_id, goods_id, product_no, product_title, trade_rule_snapshot,
                  buyer_id, seller_id, amount, order_status, accepted_trade_rule
                ) VALUES (?, 1, 1, ?, '测试商品', 'PLATFORM_ORDER', ?, ?, ?, 'PAID', TRUE)
                """, orderNo, "P-" + orderNo, buyerId, sellerId, amount);
    }

    private CreateConversationCommand conversation(Long ownerUserId, Long peerUserId) {
        CreateConversationCommand command = new CreateConversationCommand();
        command.setOwnerUserId(ownerUserId);
        command.setPeerUserId(peerUserId);
        return command;
    }

    private SendMessageCommand text(Long conversationId, String clientMsgId, Long senderId, Long receiverId, String text) {
        return textWithContent(conversationId, clientMsgId, senderId, receiverId, "{\"text\":\"" + text + "\"}");
    }

    private SendMessageCommand textWithContent(Long conversationId, String clientMsgId, Long senderId, Long receiverId, String contentJson) {
        SendMessageCommand command = baseMessage(conversationId, clientMsgId, senderId, receiverId);
        command.setMsgType("TEXT");
        command.setContentJson(contentJson);
        return command;
    }

    private SendMessageCommand image(Long conversationId, String clientMsgId, Long senderId, Long receiverId, String url) {
        SendMessageCommand command = baseMessage(conversationId, clientMsgId, senderId, receiverId);
        command.setMsgType("IMAGE");
        command.setContentJson("{\"url\":\"" + url + "\",\"width\":640,\"height\":480,\"sizeBytes\":1024,\"mimeType\":\"image/png\"}");
        return command;
    }

    private SendMessageCommand voice(Long conversationId, String clientMsgId, Long senderId, Long receiverId, String url) {
        return voiceWithContent(conversationId, clientMsgId, senderId, receiverId,
                "{\"url\":\"" + url + "\",\"durationMs\":1800,\"sizeBytes\":4096,\"mimeType\":\"audio/webm\"}");
    }

    private SendMessageCommand voiceWithContent(Long conversationId, String clientMsgId, Long senderId, Long receiverId, String contentJson) {
        SendMessageCommand command = baseMessage(conversationId, clientMsgId, senderId, receiverId);
        command.setMsgType("VOICE");
        command.setContentJson(contentJson);
        return command;
    }

    private SendMessageCommand video(Long conversationId, String clientMsgId, Long senderId, Long receiverId, String url) {
        return videoWithContent(conversationId, clientMsgId, senderId, receiverId,
                "{\"url\":\"" + url + "\",\"durationMs\":2200,\"sizeBytes\":8192,\"mimeType\":\"video/mp4\"}");
    }

    private SendMessageCommand videoWithContent(Long conversationId, String clientMsgId, Long senderId, Long receiverId, String contentJson) {
        SendMessageCommand command = baseMessage(conversationId, clientMsgId, senderId, receiverId);
        command.setMsgType("VIDEO");
        command.setContentJson(contentJson);
        return command;
    }

    private SendMessageCommand baseMessage(Long conversationId, String clientMsgId, Long senderId, Long receiverId) {
        SendMessageCommand command = new SendMessageCommand();
        command.setConversationId(conversationId);
        command.setClientMsgId(clientMsgId);
        command.setSenderId(senderId);
        command.setReceiverId(receiverId);
        return command;
    }

    private void insertLegacyVoiceMessage(Long conversationId, String clientMsgId, Long senderId, Long receiverId, String url, boolean doubleEncoded) {
        Long serverSeq = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(server_seq), 0) + 1 FROM im_message WHERE conversation_id = ?", Long.class, conversationId);
        String messageNo = "MSG-" + conversationId + "-" + serverSeq;
        String contentJson = "{\"url\":\"" + url + "\",\"durationMs\":1800,\"sizeBytes\":4096,\"mimeType\":\"audio/webm\"}";
        String legacyContentJson = doubleEncoded
                ? "\"" + contentJson.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
                : contentJson.replace("\"", "\\\"");
        jdbcTemplate.update("""
                INSERT INTO im_message (
                  message_no, conversation_id, conversation_no, server_seq, client_msg_id, client_key,
                  sender_id, receiver_id, message_type, content_json, revoked, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'VOICE', ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, messageNo, conversationId, "IM-SINGLE-1-2", serverSeq, clientMsgId, conversationId + ":" + senderId + ":" + clientMsgId, senderId, receiverId, legacyContentJson);
        jdbcTemplate.update("""
                UPDATE im_conversation
                SET last_seq = ?, last_message_summary = '[语音]', updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, serverSeq, conversationId);
    }

    private void insertSlashEscapedVoiceMessage(Long conversationId, String clientMsgId, Long senderId, Long receiverId, String url) {
        Long serverSeq = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(server_seq), 0) + 1 FROM im_message WHERE conversation_id = ?", Long.class, conversationId);
        String messageNo = "MSG-" + conversationId + "-" + serverSeq;
        String slashEscapedUrl = url.replace("/", "\\/");
        String contentJson = "{\"url\":\"" + slashEscapedUrl + "\",\"durationMs\":1800,\"sizeBytes\":4096,\"mimeType\":\"audio/webm\"}";
        jdbcTemplate.update("""
                INSERT INTO im_message (
                  message_no, conversation_id, conversation_no, server_seq, client_msg_id, client_key,
                  sender_id, receiver_id, message_type, content_json, revoked, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'VOICE', ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, messageNo, conversationId, "IM-SINGLE-1-2", serverSeq, clientMsgId, conversationId + ":" + senderId + ":" + clientMsgId, senderId, receiverId, contentJson);
        jdbcTemplate.update("""
                UPDATE im_conversation
                SET last_seq = ?, last_message_summary = '[语音]', updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, serverSeq, conversationId);
    }

    private String issueChatImageTicket(Long ownerUserId, String storageUrl) {
        jdbcTemplate.update("""
                INSERT INTO media_upload_ticket (
                  ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at
                ) VALUES (?, ?, 'CHAT_IMAGE', 'chat.png', 'image/png', 1024, ?, 'hash', 'UPLOADED', CURRENT_TIMESTAMP, DATEADD('HOUR', 1, CURRENT_TIMESTAMP))
                """, "TICKET-" + ownerUserId + '-' + Math.abs(storageUrl.hashCode()), ownerUserId, storageUrl);
        return storageUrl;
    }

    private String issueExpiredUploadedChatImageTicket(Long ownerUserId, String storageUrl) {
        jdbcTemplate.update("""
                INSERT INTO media_upload_ticket (
                  ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at
                ) VALUES (?, ?, 'CHAT_IMAGE', 'chat.png', 'image/png', 1024, ?, 'hash', 'UPLOADED', DATEADD('HOUR', -2, CURRENT_TIMESTAMP), DATEADD('HOUR', -1, CURRENT_TIMESTAMP))
                """, "EXPIRED-TICKET-" + ownerUserId + '-' + Math.abs(storageUrl.hashCode()), ownerUserId, storageUrl);
        return storageUrl;
    }

    private String issueChatVoiceTicket(Long ownerUserId, String storageUrl) {
        jdbcTemplate.update("""
                INSERT INTO media_upload_ticket (
                  ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at
                ) VALUES (?, ?, 'CHAT_VOICE', 'chat.webm', 'audio/webm', 4096, ?, 'hash', 'UPLOADED', CURRENT_TIMESTAMP, DATEADD('HOUR', 1, CURRENT_TIMESTAMP))
                """, "VOICE-TICKET-" + ownerUserId + '-' + Math.abs(storageUrl.hashCode()), ownerUserId, storageUrl);
        return storageUrl;
    }

    private String issueChatVideoTicket(Long ownerUserId, String storageUrl) {
        jdbcTemplate.update("""
                INSERT INTO media_upload_ticket (
                  ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at
                ) VALUES (?, ?, 'CHAT_VIDEO', 'chat.mp4', 'video/mp4', 8192, ?, 'hash', 'UPLOADED', CURRENT_TIMESTAMP, DATEADD('HOUR', 1, CURRENT_TIMESTAMP))
                """, "VIDEO-CHAT-TICKET-" + ownerUserId + '-' + Math.abs(storageUrl.hashCode()), ownerUserId, storageUrl);
        return storageUrl;
    }

    private String issueVideoIdentityTicket(Long ownerUserId) {
        String storageUrl = "/uploads/video-identity/" + ownerUserId + "/identity.mp4";
        jdbcTemplate.update("""
                INSERT INTO media_upload_ticket (
                  ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at
                ) VALUES (?, ?, 'VIDEO_IDENTITY', 'identity.mp4', 'video/mp4', 1024, ?, 'hash', 'UPLOADED', CURRENT_TIMESTAMP, DATEADD('HOUR', 1, CURRENT_TIMESTAMP))
                """, "VIDEO-TICKET-" + ownerUserId + '-' + Math.abs(storageUrl.hashCode()), ownerUserId, storageUrl);
        return storageUrl;
    }

    private void approveVideoIdentity(Long ownerUserId, String storageUrl) {
        jdbcTemplate.update("""
                INSERT INTO audit_record (
                  audit_no, audit_type, user_id, target_type, target_id, reason, description, status, reviewed_at
                ) VALUES (?, 'VIDEO_IDENTITY', ?, 'USER', ?, ?, '视频认证通过', 'APPROVED', CURRENT_TIMESTAMP)
                """, "AUDIT-VIDEO-" + ownerUserId + '-' + Math.abs(storageUrl.hashCode()), ownerUserId, String.valueOf(ownerUserId), storageUrl);
    }

    private String issueExpiredUploadedChatVoiceTicket(Long ownerUserId, String storageUrl) {
        jdbcTemplate.update("""
                INSERT INTO media_upload_ticket (
                  ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at
                ) VALUES (?, ?, 'CHAT_VOICE', 'chat.webm', 'audio/webm', 4096, ?, 'hash', 'UPLOADED', DATEADD('HOUR', -2, CURRENT_TIMESTAMP), DATEADD('HOUR', -1, CURRENT_TIMESTAMP))
                """, "EXPIRED-VOICE-TICKET-" + ownerUserId + '-' + Math.abs(storageUrl.hashCode()), ownerUserId, storageUrl);
        return storageUrl;
    }

    private String issueExpiredUploadedChatVideoTicket(Long ownerUserId, String storageUrl) {
        jdbcTemplate.update("""
                INSERT INTO media_upload_ticket (
                  ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at
                ) VALUES (?, ?, 'CHAT_VIDEO', 'chat.mp4', 'video/mp4', 8192, ?, 'hash', 'UPLOADED', DATEADD('HOUR', -2, CURRENT_TIMESTAMP), DATEADD('HOUR', -1, CURRENT_TIMESTAMP))
                """, "EXPIRED-VIDEO-CHAT-TICKET-" + ownerUserId + '-' + Math.abs(storageUrl.hashCode()), ownerUserId, storageUrl);
        return storageUrl;
    }
}
