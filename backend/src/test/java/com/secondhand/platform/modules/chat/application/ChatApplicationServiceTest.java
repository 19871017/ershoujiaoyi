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
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(image(conversationId, "bad-image", 1L, 2L, "ftp://cdn.example.com/a.png")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(image(conversationId, "local-image", 1L, 2L, "local://chat/a.png")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(image(conversationId, "placeholder-image", 1L, 2L, "/uploads/chat-image/placeholder.png")));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(image(conversationId, "other-owner", 1L, 2L, chatImageUrl)));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(image(conversationId, "not-ticket", 1L, 2L, "/uploads/chat-image/no-ticket.png")));
        String expiredChatImageUrl = issueExpiredChatImageTicket(1L, "/uploads/chat-image/expired-owner1.png");
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(image(conversationId, "expired-ticket", 1L, 2L, expiredChatImageUrl)));
    }

    @Test
    void listConversationsShouldIncludePeerProfileFields() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        insertUserAccount(2L, "真实卖家", "/uploads/avatar/seller.png");
        insertUserProfile(2L, "goddess", "杭州", "SELLER", true);
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
    void nonParticipantShouldNotSyncOrMarkReceipts() {
        Long conversationId = service.createConversation(conversation(1L, 2L));
        service.sendMessage(text(conversationId, "c1", 1L, 2L, "one"));

        assertThrows(IllegalArgumentException.class, () -> service.syncMessages(conversationId, 3L, 0L, 10));
        assertThrows(IllegalArgumentException.class, () -> service.markConversationDelivered(conversationId, 3L));
        assertThrows(IllegalArgumentException.class, () -> service.markConversationRead(conversationId, 3L, null));
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

    private void insertUserProfile(Long userId, String gender, String city, String mainRole, boolean videoVerified) {
        jdbcTemplate.update("INSERT INTO user_profile (user_id, gender, city, main_role, video_identity_status, video_verified) VALUES (?, ?, ?, ?, ?, ?)",
                userId, gender, city, mainRole, videoVerified ? "APPROVED" : "UNVERIFIED", videoVerified);
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

    private SendMessageCommand baseMessage(Long conversationId, String clientMsgId, Long senderId, Long receiverId) {
        SendMessageCommand command = new SendMessageCommand();
        command.setConversationId(conversationId);
        command.setClientMsgId(clientMsgId);
        command.setSenderId(senderId);
        command.setReceiverId(receiverId);
        return command;
    }

    private String issueChatImageTicket(Long ownerUserId, String storageUrl) {
        jdbcTemplate.update("""
                INSERT INTO media_upload_ticket (
                  ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at
                ) VALUES (?, ?, 'CHAT_IMAGE', 'chat.png', 'image/png', 1024, ?, 'hash', 'UPLOADED', CURRENT_TIMESTAMP, DATEADD('HOUR', 1, CURRENT_TIMESTAMP))
                """, "TICKET-" + ownerUserId + '-' + Math.abs(storageUrl.hashCode()), ownerUserId, storageUrl);
        return storageUrl;
    }

    private String issueExpiredChatImageTicket(Long ownerUserId, String storageUrl) {
        jdbcTemplate.update("""
                INSERT INTO media_upload_ticket (
                  ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at
                ) VALUES (?, ?, 'CHAT_IMAGE', 'chat.png', 'image/png', 1024, ?, 'hash', 'UPLOADED', DATEADD('HOUR', -2, CURRENT_TIMESTAMP), DATEADD('HOUR', -1, CURRENT_TIMESTAMP))
                """, "EXPIRED-TICKET-" + ownerUserId + '-' + Math.abs(storageUrl.hashCode()), ownerUserId, storageUrl);
        return storageUrl;
    }
}
