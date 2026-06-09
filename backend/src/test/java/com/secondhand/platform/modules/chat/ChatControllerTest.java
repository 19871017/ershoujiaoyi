package com.secondhand.platform.modules.chat;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.secondhand.platform.modules.chat.application.ChatApplicationService;
import com.secondhand.platform.modules.chat.application.CreateConversationCommand;
import com.secondhand.platform.modules.chat.application.SendMessageCommand;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import com.secondhand.platform.shared.web.GlobalExceptionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChatControllerTest {
    private JdbcTemplate jdbcTemplate;
    private ChatApplicationService service;
    private MockMvc mvc;
    private Path mediaRoot;

    @BeforeEach
    void setUp() throws Exception {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        service = new ChatApplicationService(jdbcTemplate);
        mediaRoot = Files.createTempDirectory("xiaoyuanquan-chat-media-test");
        insertActiveUser(1L, "买家一", "/uploads/avatar/user1.png");
        insertActiveUser(2L, "卖家二", "/uploads/avatar/user2.png");
        insertActiveUser(3L, "路人三", "/uploads/avatar/user3.png");

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        mvc = MockMvcBuilders.standaloneSetup(new ChatController(service, new CurrentUserResolver(jdbcTemplate, environment), mediaRoot.toString()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void sendAndListConversationShouldUseResolvedCurrentUser() throws Exception {
        mvc.perform(post("/api/chat/messages")
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled")
                        .contentType("application/json")
                        .content("""
                                {"receiverId":2,"clientMsgId":"ctrl-send-1","msgType":"TEXT","contentJson":"{\\"text\\":\\"你好卖家\\"}"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ack.conversationId", notNullValue()))
                .andExpect(jsonPath("$.data.ack.senderId", is(1)))
                .andExpect(jsonPath("$.data.ack.receiverId", is(2)))
                .andExpect(jsonPath("$.data.ack.serverSeq", is(1)))
                .andExpect(jsonPath("$.data.ack.msgType", is("TEXT")));

        mvc.perform(get("/api/chat/conversations")
                        .header("X-User-Id", "2")
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversations[0].peerUserId", is(1)))
                .andExpect(jsonPath("$.data.conversations[0].peerNickname", is("买家一")))
                .andExpect(jsonPath("$.data.conversations[0].lastMessageSummary", is("你好卖家")))
                .andExpect(jsonPath("$.data.conversations[0].unreadCount", is(1)));
    }

    @Test
    void syncEndpointShouldRouteLatestAndBeforeSeqWindows() throws Exception {
        Long conversationId = createConversation(1L, 2L);
        for (int index = 1; index <= 4; index += 1) {
            sendText(conversationId, "route-" + index, 1L, 2L, "消息" + index);
        }

        mvc.perform(get("/api/chat/conversations/{conversationId}/messages", conversationId)
                        .header("X-User-Id", "2")
                        .header("X-Dev-Mode", "enabled")
                        .param("latest", "true")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()", is(2)))
                .andExpect(jsonPath("$.data.messages[0].clientMsgId", is("route-3")))
                .andExpect(jsonPath("$.data.messages[1].clientMsgId", is("route-4")))
                .andExpect(jsonPath("$.data.nextAfterSeq", is(4)))
                .andExpect(jsonPath("$.data.previousBeforeSeq", is(3)))
                .andExpect(jsonPath("$.data.hasEarlier", is(true)));

        mvc.perform(get("/api/chat/conversations/{conversationId}/messages", conversationId)
                        .header("X-User-Id", "2")
                        .header("X-Dev-Mode", "enabled")
                        .param("beforeSeq", "3")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()", is(2)))
                .andExpect(jsonPath("$.data.messages[0].clientMsgId", is("route-1")))
                .andExpect(jsonPath("$.data.messages[1].clientMsgId", is("route-2")))
                .andExpect(jsonPath("$.data.previousBeforeSeq", is(1)))
                .andExpect(jsonPath("$.data.hasEarlier", is(false)));
    }

    @Test
    void revokeAndClearEndpointsShouldBeScopedToParticipant() throws Exception {
        Long conversationId = createConversation(1L, 2L);
        String serverMsgId = sendText(conversationId, "revoke-controller", 1L, 2L, "可撤回").getServerMsgId();
        sendText(conversationId, "clear-controller", 2L, 1L, "清理前消息");

        mvc.perform(post("/api/chat/messages/{serverMsgId}/revoke", serverMsgId)
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revoked", is(true)))
                .andExpect(jsonPath("$.data.serverMsgId", is(serverMsgId)));

        mvc.perform(get("/api/chat/conversations/{conversationId}/messages", conversationId)
                        .header("X-User-Id", "2")
                        .header("X-Dev-Mode", "enabled")
                        .param("afterSeq", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].revoked", is(true)))
                .andExpect(jsonPath("$.data.messages[0].contentJson", is("{\"revoked\":true}")));

        mvc.perform(post("/api/chat/conversations/{conversationId}/clear", conversationId)
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId", is(conversationId.intValue())))
                .andExpect(jsonPath("$.data.clearedSeq", is(2)));

        mvc.perform(get("/api/chat/conversations/{conversationId}", conversationId)
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId", is(conversationId.intValue())))
                .andExpect(jsonPath("$.data.peerUserId", is(2)))
                .andExpect(jsonPath("$.data.peerNickname", is("卖家二")))
                .andExpect(jsonPath("$.data.unreadCount", is(0)));

        mvc.perform(get("/api/chat/conversations")
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversations.length()", is(0)));

        mvc.perform(get("/api/chat/conversations/{conversationId}/messages", conversationId)
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled")
                        .param("afterSeq", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()", is(0)));

        sendText(conversationId, "clear-controller-new", 2L, 1L, "清理后新消息");

        mvc.perform(get("/api/chat/conversations")
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversations.length()", is(1)))
                .andExpect(jsonPath("$.data.conversations[0].lastMessageSummary", is("清理后新消息")))
                .andExpect(jsonPath("$.data.conversations[0].unreadCount", is(1)));

        mvc.perform(get("/api/chat/conversations/{conversationId}/messages", conversationId)
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled")
                        .param("afterSeq", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()", is(1)))
                .andExpect(jsonPath("$.data.messages[0].clientMsgId", is("clear-controller-new")));
    }

    @Test
    void nonParticipantShouldNotReadClearOrRevokeConversation() throws Exception {
        Long conversationId = createConversation(1L, 2L);
        String serverMsgId = sendText(conversationId, "deny-controller", 1L, 2L, "非参与者不可见").getServerMsgId();

        mvc.perform(get("/api/chat/conversations/{conversationId}", conversationId)
                        .header("X-User-Id", "3")
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));

        mvc.perform(get("/api/chat/conversations/{conversationId}/messages", conversationId)
                        .header("X-User-Id", "3")
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));

        mvc.perform(post("/api/chat/conversations/{conversationId}/clear", conversationId)
                        .header("X-User-Id", "3")
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));

        mvc.perform(post("/api/chat/messages/{serverMsgId}/revoke", serverMsgId)
                        .header("X-User-Id", "3")
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void httpChatFlowShouldSendTextVoiceRevokeAndClearWithPeerMetadata() throws Exception {
        String voiceUrl = issueChatVoiceTicket(1L, "/uploads/chat-voice/controller-owner1.webm");

        mvc.perform(post("/api/chat/messages")
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled")
                        .contentType("application/json")
                        .content("""
                                {"receiverId":2,"clientMsgId":"flow-text-1","msgType":"TEXT","contentJson":"{\\"text\\":\\"第一条文字\\"}"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ack.conversationId", notNullValue()))
                .andExpect(jsonPath("$.data.ack.serverSeq", is(1)))
                .andExpect(jsonPath("$.data.ack.serverMsgId", is("MSG-1-1")))
                .andExpect(jsonPath("$.data.ack.senderId", is(1)))
                .andExpect(jsonPath("$.data.ack.receiverId", is(2)))
                .andExpect(jsonPath("$.data.ack.msgType", is("TEXT")));

        mvc.perform(post("/api/chat/messages")
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled")
                        .contentType("application/json")
                        .content("""
                                {"conversationId":1,"receiverId":2,"clientMsgId":"flow-voice-1","msgType":"VOICE","contentJson":"{\\"url\\":\\"%s\\",\\"durationMs\\":1600,\\"sizeBytes\\":4096,\\"mimeType\\":\\"audio/webm\\"}"}
                                """.formatted(voiceUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ack.conversationId", is(1)))
                .andExpect(jsonPath("$.data.ack.serverSeq", is(2)))
                .andExpect(jsonPath("$.data.ack.serverMsgId", is("MSG-1-2")))
                .andExpect(jsonPath("$.data.ack.msgType", is("VOICE")));

        mvc.perform(get("/api/chat/conversations")
                        .header("X-User-Id", "2")
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversations[0].conversationId", is(1)))
                .andExpect(jsonPath("$.data.conversations[0].peerUserId", is(1)))
                .andExpect(jsonPath("$.data.conversations[0].peerNickname", is("买家一")))
                .andExpect(jsonPath("$.data.conversations[0].peerAvatarUrl", is("/uploads/avatar/user1.png")))
                .andExpect(jsonPath("$.data.conversations[0].lastMessageSummary", is("[语音]")))
                .andExpect(jsonPath("$.data.conversations[0].lastServerSeq", is(2)))
                .andExpect(jsonPath("$.data.conversations[0].unreadCount", is(2)));

        mvc.perform(get("/api/chat/conversations/{conversationId}/messages", 1)
                        .header("X-User-Id", "2")
                        .header("X-Dev-Mode", "enabled")
                        .param("latest", "true")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()", is(2)))
                .andExpect(jsonPath("$.data.messages[0].msgType", is("TEXT")))
                .andExpect(jsonPath("$.data.messages[0].contentJson", is("{\"text\":\"第一条文字\"}")))
                .andExpect(jsonPath("$.data.messages[0].revoked", is(false)))
                .andExpect(jsonPath("$.data.messages[1].msgType", is("VOICE")))
                .andExpect(jsonPath("$.data.messages[1].contentJson", is("{\"url\":\"/uploads/chat-voice/controller-owner1.webm\",\"durationMs\":1600,\"sizeBytes\":4096,\"mimeType\":\"audio/webm\"}")))
                .andExpect(jsonPath("$.data.messages[1].revoked", is(false)))
                .andExpect(jsonPath("$.data.nextAfterSeq", is(2)));

        mvc.perform(post("/api/chat/messages/{serverMsgId}/revoke", "MSG-1-2")
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId", is(1)))
                .andExpect(jsonPath("$.data.serverSeq", is(2)))
                .andExpect(jsonPath("$.data.revoked", is(true)));

        mvc.perform(get("/api/chat/conversations/{conversationId}/messages", 1)
                        .header("X-User-Id", "2")
                        .header("X-Dev-Mode", "enabled")
                        .param("afterSeq", "0")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[1].revoked", is(true)))
                .andExpect(jsonPath("$.data.messages[1].contentJson", is("{\"revoked\":true}")));

        mvc.perform(post("/api/chat/conversations/{conversationId}/clear", 1)
                        .header("X-User-Id", "2")
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId", is(1)))
                .andExpect(jsonPath("$.data.clearedSeq", is(2)))
                .andExpect(jsonPath("$.data.lastServerSeq", is(2)));

        mvc.perform(get("/api/chat/conversations/{conversationId}/messages", 1)
                        .header("X-User-Id", "2")
                        .header("X-Dev-Mode", "enabled")
                        .param("afterSeq", "0")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()", is(0)));
    }

    @Test
    void imageEndpointShouldRequireUploadedChatImageTicketAndSyncPayload() throws Exception {
        String imageUrl = issueChatImageTicket(1L, "/uploads/chat-image/controller-owner1.png");

        mvc.perform(post("/api/chat/messages")
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled")
                        .contentType("application/json")
                        .content("""
                                {"receiverId":2,"clientMsgId":"flow-image-1","msgType":"IMAGE","contentJson":"{\\"url\\":\\"%s\\",\\"width\\":720,\\"height\\":720,\\"sizeBytes\\":4096,\\"mimeType\\":\\"image/png\\"}"}
                                """.formatted(imageUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ack.conversationId", is(1)))
                .andExpect(jsonPath("$.data.ack.serverSeq", is(1)))
                .andExpect(jsonPath("$.data.ack.msgType", is("IMAGE")));

        mvc.perform(get("/api/chat/conversations")
                        .header("X-User-Id", "2")
                        .header("X-Dev-Mode", "enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversations[0].lastMessageSummary", is("[图片]")))
                .andExpect(jsonPath("$.data.conversations[0].unreadCount", is(1)));

        mvc.perform(get("/api/chat/conversations/{conversationId}/messages", 1)
                        .header("X-User-Id", "2")
                        .header("X-Dev-Mode", "enabled")
                        .param("latest", "true")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()", is(1)))
                .andExpect(jsonPath("$.data.messages[0].msgType", is("IMAGE")))
                .andExpect(jsonPath("$.data.messages[0].contentJson", is("{\"url\":\"/uploads/chat-image/controller-owner1.png\",\"width\":720,\"height\":720,\"sizeBytes\":4096,\"mimeType\":\"image/png\"}")));

        mvc.perform(post("/api/chat/messages")
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled")
                        .contentType("application/json")
                        .content("""
                                {"receiverId":2,"clientMsgId":"flow-image-unissued","msgType":"IMAGE","contentJson":"{\\"url\\":\\"/uploads/chat-image/unissued.png\\",\\"width\\":720,\\"height\\":720,\\"sizeBytes\\":4096,\\"mimeType\\":\\"image/png\\"}"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void chatMediaEndpointShouldRequireConversationParticipantAndMessageReference() throws Exception {
        String imageUrl = issueChatImageTicket(1L, "/uploads/chat-image/controller-owner1-secure.png");
        writeMediaFile(imageUrl, "secure-image");

        mvc.perform(post("/api/chat/messages")
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled")
                        .contentType("application/json")
                        .content("""
                                {"receiverId":2,"clientMsgId":"secure-image-1","msgType":"IMAGE","contentJson":"{\\"url\\":\\"%s\\",\\"width\\":720,\\"height\\":720,\\"sizeBytes\\":4096,\\"mimeType\\":\\"image/png\\"}"}
                                """.formatted(imageUrl)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/chat/media")
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled")
                        .param("url", imageUrl))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("secure-image"));

        mvc.perform(get("/api/chat/media")
                        .header("X-User-Id", "2")
                        .header("X-Dev-Mode", "enabled")
                        .param("url", imageUrl))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("secure-image"));

        mvc.perform(get("/api/chat/media")
                        .header("X-User-Id", "3")
                        .header("X-Dev-Mode", "enabled")
                        .param("url", imageUrl))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));

        String unusedImageUrl = issueChatImageTicket(1L, "/uploads/chat-image/controller-owner1-unused.png");
        writeMediaFile(unusedImageUrl, "unused-image");
        mvc.perform(get("/api/chat/media")
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled")
                        .param("url", unusedImageUrl))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void chatMediaEndpointShouldRejectDirtyMessageWhoseTicketOwnerIsNotSender() throws Exception {
        Long conversationId = createConversation(1L, 2L);
        String dirtyImageUrl = issueChatImageTicket(3L, "/uploads/chat-image/controller-owner3-dirty.png");
        writeMediaFile(dirtyImageUrl, "dirty-image");
        jdbcTemplate.update("""
                INSERT INTO im_message (
                  message_no, conversation_id, conversation_no, server_seq, client_msg_id, client_key,
                  sender_id, receiver_id, message_type, content_json, revoked, created_at, updated_at
                ) VALUES (?, ?, ?, 1, ?, ?, 1, 2, 'IMAGE', ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "MSG-DIRTY-OWNER-1", conversationId, "IM-SINGLE-1-2", "dirty-owner-1", conversationId + ":1:dirty-owner-1",
                "{\"url\":\"" + dirtyImageUrl + "\",\"width\":720,\"height\":720,\"sizeBytes\":4096,\"mimeType\":\"image/png\"}");

        mvc.perform(get("/api/chat/media")
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled")
                        .param("url", dirtyImageUrl))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));

        mvc.perform(get("/api/chat/media")
                        .header("X-User-Id", "2")
                        .header("X-Dev-Mode", "enabled")
                        .param("url", dirtyImageUrl))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void chatMediaEndpointShouldRejectSymlinkEscapeEvenWhenMessageReferencesTicket() throws Exception {
        String imageUrl = issueChatImageTicket(1L, "/uploads/chat-image/controller-owner1-symlink.png");
        Path outsideFile = Files.createTempFile("xiaoyuanquan-chat-outside", ".txt");
        Files.writeString(outsideFile, "outside-secret");
        Path symlink = mediaRoot.resolve(imageUrl.substring(1)).normalize();
        Files.createDirectories(symlink.getParent());
        Files.createSymbolicLink(symlink, outsideFile);

        mvc.perform(post("/api/chat/messages")
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled")
                        .contentType("application/json")
                        .content("""
                                {"receiverId":2,"clientMsgId":"secure-symlink-1","msgType":"IMAGE","contentJson":"{\\"url\\":\\"%s\\",\\"width\\":720,\\"height\\":720,\\"sizeBytes\\":4096,\\"mimeType\\":\\"image/png\\"}"}
                                """.formatted(imageUrl)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/chat/media")
                        .header("X-User-Id", "1")
                        .header("X-Dev-Mode", "enabled")
                        .param("url", imageUrl))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    private Long createConversation(Long ownerUserId, Long peerUserId) {
        CreateConversationCommand command = new CreateConversationCommand();
        command.setOwnerUserId(ownerUserId);
        command.setPeerUserId(peerUserId);
        return service.createConversation(command);
    }

    private ChatMessageAck sendText(Long conversationId, String clientMsgId, Long senderId, Long receiverId, String text) {
        SendMessageCommand command = new SendMessageCommand();
        command.setConversationId(conversationId);
        command.setClientMsgId(clientMsgId);
        command.setSenderId(senderId);
        command.setReceiverId(receiverId);
        command.setMsgType("TEXT");
        command.setContentJson("{\"text\":\"" + text + "\"}");
        return service.sendMessage(command);
    }

    private void insertActiveUser(Long userId, String nickname, String avatarUrl) {
        jdbcTemplate.update("""
                INSERT INTO user_account (id, user_no, phone, password_hash, nickname, avatar_url, status)
                VALUES (?, ?, ?, 'hash', ?, ?, 'ACTIVE')
                """, userId, "U-CHAT-CTRL-" + userId, "1389000" + userId, nickname, avatarUrl);
        jdbcTemplate.update("""
                INSERT INTO user_profile (user_id, gender, city, main_role, video_identity_status, video_verified)
                VALUES (?, 'unknown', '上海', 'BOTH', 'APPROVED', TRUE)
                """, userId);
    }

    private String issueChatVoiceTicket(Long ownerUserId, String storageUrl) {
        jdbcTemplate.update("""
                INSERT INTO media_upload_ticket (
                  ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at
                ) VALUES (?, ?, 'CHAT_VOICE', 'chat.webm', 'audio/webm', 4096, ?, 'hash', 'UPLOADED', CURRENT_TIMESTAMP, DATEADD('HOUR', 1, CURRENT_TIMESTAMP))
                """, "CTRL-VOICE-TICKET-" + ownerUserId + '-' + Math.abs(storageUrl.hashCode()), ownerUserId, storageUrl);
        return storageUrl;
    }

    private String issueChatImageTicket(Long ownerUserId, String storageUrl) {
        jdbcTemplate.update("""
                INSERT INTO media_upload_ticket (
                  ticket_no, owner_user_id, scene, original_filename, content_type, file_size, storage_url, upload_token_hash, status, created_at, expires_at
                ) VALUES (?, ?, 'CHAT_IMAGE', 'chat.png', 'image/png', 4096, ?, 'hash', 'UPLOADED', CURRENT_TIMESTAMP, DATEADD('HOUR', 1, CURRENT_TIMESTAMP))
                """, "CTRL-IMAGE-TICKET-" + ownerUserId + '-' + Math.abs(storageUrl.hashCode()), ownerUserId, storageUrl);
        return storageUrl;
    }

    private void writeMediaFile(String storageUrl, String content) throws Exception {
        Path target = mediaRoot.resolve(storageUrl.substring(1)).normalize();
        if (!target.startsWith(mediaRoot.resolve("uploads").normalize())) {
            throw new IllegalArgumentException("test storage url invalid");
        }
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }
}
