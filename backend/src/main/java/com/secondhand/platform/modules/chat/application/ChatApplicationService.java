package com.secondhand.platform.modules.chat.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondhand.platform.modules.chat.ChatMessageAck;
import com.secondhand.platform.modules.chat.ChatMessageResponse;
import com.secondhand.platform.modules.chat.ConversationListItemResponse;
import com.secondhand.platform.modules.chat.DeliveryReceiptResponse;
import com.secondhand.platform.modules.chat.MessageSyncResponse;
import com.secondhand.platform.modules.chat.ReadConversationResponse;
import com.secondhand.platform.modules.chat.domain.ChatMessage;
import com.secondhand.platform.modules.chat.domain.Conversation;
import com.secondhand.platform.shared.contracts.chat.MessageType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatApplicationService {
    private static final String CONVERSATION_TYPE_SINGLE = "SINGLE";
    private static final int MAX_CLIENT_MSG_ID_LENGTH = 64;
    private static final int MAX_CONTENT_JSON_LENGTH = 4000;
    private static final int MAX_IMAGE_URL_LENGTH = 1024;
    private static final int MAX_IMAGE_MIME_TYPE_LENGTH = 64;
    private static final long MAX_IMAGE_SIZE_BYTES = 20L * 1024L * 1024L;
    private static final int DEFAULT_SYNC_LIMIT = 50;
    private static final int MAX_SYNC_LIMIT = 200;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> JSON_OBJECT_TYPE = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;

    public ChatApplicationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Long createConversation(CreateConversationCommand command) {
        validateConversation(command);
        long minUserId = Math.min(command.getOwnerUserId(), command.getPeerUserId());
        long maxUserId = Math.max(command.getOwnerUserId(), command.getPeerUserId());
        String conversationNo = conversationNo(minUserId, maxUserId);
        Long existingId = queryLong("SELECT id FROM im_conversation WHERE conversation_no = ?", conversationNo);
        if (existingId != null) {
            return existingId;
        }
        jdbcTemplate.update("""
                INSERT INTO im_conversation (
                  conversation_no, owner_user_id, peer_user_id, conversation_type, last_seq, last_message_summary, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, conversationNo, minUserId, maxUserId, CONVERSATION_TYPE_SINGLE);
        return requireLong("SELECT id FROM im_conversation WHERE conversation_no = ?", conversationNo);
    }

    @Transactional
    public ChatMessageAck sendMessage(SendMessageCommand command) {
        validateMessage(command);
        Long conversationId = resolveConversationId(command);
        Conversation conversation = requireConversation(conversationId);
        validateConversationParticipants(conversation, command);
        String clientKey = clientMessageKey(conversationId, command.getSenderId(), command.getClientMsgId());
        ChatMessage existing = findMessageByClientKey(clientKey);
        if (existing != null) {
            return toAck(existing);
        }
        long serverSeq = safeLastServerSeq(conversation) + 1L;
        String serverMsgId = "MSG-" + conversationId + '-' + serverSeq;
        String msgType = normalizeMsgType(command.getMsgType());
        jdbcTemplate.update("""
                INSERT INTO im_message (
                  message_no, conversation_id, conversation_no, server_seq, client_msg_id, client_key,
                  sender_id, receiver_id, message_type, content_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, serverMsgId, conversationId, conversationNo(conversation), serverSeq, command.getClientMsgId().trim(), clientKey,
                command.getSenderId(), command.getReceiverId(), msgType, command.getContentJson().trim());
        jdbcTemplate.update("""
                UPDATE im_conversation
                SET last_seq = ?, last_message_summary = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, serverSeq, buildMessageSummary(command), conversationId);
        return toAck(requireMessageByClientKey(clientKey));
    }

    public List<ConversationListItemResponse> listConversations(Long userId) {
        validateUserId(userId);
        return jdbcTemplate.query("""
                SELECT id, owner_user_id, peer_user_id, last_seq, last_message_summary, updated_at
                FROM im_conversation
                WHERE owner_user_id = ? OR peer_user_id = ?
                ORDER BY updated_at DESC, id DESC
                """, (rs, rowNum) -> toConversationItem(rs, userId), userId, userId);
    }

    @Transactional
    public MessageSyncResponse syncMessages(Long conversationId, Long userId, Long afterSeq, Integer limit) {
        validateUserId(userId);
        Conversation conversation = requireParticipantConversation(conversationId, userId);
        long normalizedAfterSeq = afterSeq == null ? 0L : afterSeq;
        if (normalizedAfterSeq < 0L) {
            throw new IllegalArgumentException("afterSeq invalid");
        }
        int normalizedLimit = limit == null ? DEFAULT_SYNC_LIMIT : limit;
        if (normalizedLimit <= 0) {
            throw new IllegalArgumentException("limit invalid");
        }
        normalizedLimit = Math.min(normalizedLimit, MAX_SYNC_LIMIT);

        List<ChatMessageResponse> rows = jdbcTemplate.query("""
                SELECT id, conversation_id, server_seq, client_msg_id, message_no, sender_id, receiver_id, message_type, content_json, created_at, updated_at
                FROM im_message
                WHERE conversation_id = ? AND server_seq > ?
                ORDER BY server_seq ASC
                LIMIT ?
                """, (rs, rowNum) -> toMessageResponse(mapMessage(rs), userId), conversationId, normalizedAfterSeq, normalizedLimit + 1);
        boolean hasMore = rows.size() > normalizedLimit;
        List<ChatMessageResponse> pageMessages = hasMore ? rows.subList(0, normalizedLimit) : rows;
        long nextAfterSeq = pageMessages.isEmpty() ? normalizedAfterSeq : pageMessages.get(pageMessages.size() - 1).getServerSeq();
        long nextDeliveredSeq = Math.min(Math.max(getDeliveredSeq(conversationId, userId), nextAfterSeq), safeLastServerSeq(conversation));
        upsertReceiptSeq(conversationId, userId, getReadSeq(conversationId, userId), nextDeliveredSeq);
        return new MessageSyncResponse(pageMessages, nextAfterSeq, hasMore);
    }

    @Transactional
    public DeliveryReceiptResponse markConversationDelivered(Long conversationId, Long userId) {
        validateUserId(userId);
        Conversation conversation = requireParticipantConversation(conversationId, userId);
        long lastServerSeq = safeLastServerSeq(conversation);
        long readSeq = getReadSeq(conversationId, userId);
        upsertReceiptSeq(conversationId, userId, readSeq, lastServerSeq);
        return new DeliveryReceiptResponse(conversationId, lastServerSeq, readSeq, lastServerSeq, unreadCount(lastServerSeq, readSeq));
    }

    @Transactional
    public ReadConversationResponse markConversationRead(Long conversationId, Long userId, Long requestedReadSeq) {
        validateUserId(userId);
        Conversation conversation = requireParticipantConversation(conversationId, userId);
        long targetReadSeq = requestedReadSeq == null ? safeLastServerSeq(conversation) : requestedReadSeq;
        if (targetReadSeq < 0L) {
            throw new IllegalArgumentException("readSeq invalid");
        }
        long lastServerSeq = safeLastServerSeq(conversation);
        long currentReadSeq = getReadSeq(conversationId, userId);
        long nextReadSeq = Math.min(targetReadSeq, lastServerSeq);
        if (nextReadSeq < currentReadSeq) {
            nextReadSeq = currentReadSeq;
        }
        long nextDeliveredSeq = Math.max(getDeliveredSeq(conversationId, userId), nextReadSeq);
        upsertReceiptSeq(conversationId, userId, nextReadSeq, nextDeliveredSeq);
        return new ReadConversationResponse(conversationId, nextReadSeq, nextDeliveredSeq, lastServerSeq, unreadCount(lastServerSeq, nextReadSeq));
    }

    private Long resolveConversationId(SendMessageCommand command) {
        if (command.getConversationId() != null) {
            Conversation conversation = requireConversation(command.getConversationId());
            validateConversationParticipants(conversation, command);
            return conversation.getId();
        }
        CreateConversationCommand createCommand = new CreateConversationCommand();
        createCommand.setOwnerUserId(command.getSenderId());
        createCommand.setPeerUserId(command.getReceiverId());
        return createConversation(createCommand);
    }

    private ConversationListItemResponse toConversationItem(ResultSet rs, Long userId) throws SQLException {
        long conversationId = rs.getLong("id");
        long lastServerSeq = rs.getLong("last_seq");
        long readSeq = getReadSeq(conversationId, userId);
        long deliveredSeq = getDeliveredSeq(conversationId, userId);
        ConversationListItemResponse item = new ConversationListItemResponse();
        item.setConversationId(conversationId);
        item.setPeerUserId(Objects.equals(userId, rs.getLong("owner_user_id")) ? rs.getLong("peer_user_id") : rs.getLong("owner_user_id"));
        item.setLastMessageSummary(rs.getString("last_message_summary"));
        item.setLastServerSeq(lastServerSeq);
        item.setDeliveredSeq(deliveredSeq);
        item.setReadSeq(readSeq);
        item.setUnreadCount(unreadCount(lastServerSeq, readSeq));
        item.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        return item;
    }

    private void validateConversation(CreateConversationCommand command) {
        if (command == null || command.getOwnerUserId() == null || command.getPeerUserId() == null) {
            throw new IllegalArgumentException("conversation participants required");
        }
        if (Objects.equals(command.getOwnerUserId(), command.getPeerUserId())) {
            throw new IllegalArgumentException("conversation participants must be different");
        }
    }

    private void validateMessage(SendMessageCommand command) {
        if (command == null || command.getSenderId() == null || command.getReceiverId() == null) {
            throw new IllegalArgumentException("message context required");
        }
        if (Objects.equals(command.getSenderId(), command.getReceiverId())) {
            throw new IllegalArgumentException("message participants must be different");
        }
        if (command.getClientMsgId() == null || command.getClientMsgId().isBlank()) {
            throw new IllegalArgumentException("clientMsgId required");
        }
        String clientMsgId = command.getClientMsgId().trim();
        if (clientMsgId.length() > MAX_CLIENT_MSG_ID_LENGTH || !clientMsgId.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("clientMsgId invalid");
        }
        String msgType = normalizeMsgType(command.getMsgType());
        if (command.getContentJson() == null || command.getContentJson().isBlank()) {
            throw new IllegalArgumentException("contentJson required");
        }
        validateContentJson(command.getSenderId(), msgType, command.getContentJson());
    }

    private void validateContentJson(Long senderId, String msgType, String contentJson) {
        String content = contentJson.trim();
        if (content.length() > MAX_CONTENT_JSON_LENGTH || !content.startsWith("{") || !content.endsWith("}")) {
            throw new IllegalArgumentException("contentJson invalid");
        }
        if (MessageType.TEXT.name().equals(msgType)) {
            if (!content.contains("\"text\"") || content.matches(".*\"text\"\\s*:\\s*\"\\s*\".*")) {
                throw new IllegalArgumentException("text content required");
            }
            return;
        }
        if (MessageType.IMAGE.name().equals(msgType)) {
            validateImageContent(senderId, content);
        }
    }

    private void validateImageContent(Long senderId, String content) {
        Map<String, Object> jsonObject = parseJsonObject(content);
        Object urlValue = jsonObject.get("url");
        if (!(urlValue instanceof String url) || url.isBlank() || url.length() > MAX_IMAGE_URL_LENGTH) {
            throw new IllegalArgumentException("image url invalid");
        }
        if (!isAllowedImageUrl(url)) {
            throw new IllegalArgumentException("image url invalid");
        }
        validateChatImageTicket(senderId, url);
        validateOptionalPositiveInt(jsonObject.get("width"), "image width invalid", 1, 10000);
        validateOptionalPositiveInt(jsonObject.get("height"), "image height invalid", 1, 10000);
        validateOptionalPositiveLong(jsonObject.get("sizeBytes"), "image sizeBytes invalid", 1L, MAX_IMAGE_SIZE_BYTES);
        Object mimeTypeValue = jsonObject.get("mimeType");
        if (!(mimeTypeValue instanceof String mimeType)
                || mimeType.isBlank()
                || mimeType.length() > MAX_IMAGE_MIME_TYPE_LENGTH
                || !mimeType.matches("image/(jpeg|jpg|png|webp)")) {
            throw new IllegalArgumentException("image mimeType invalid");
        }
    }

    private void validateChatImageTicket(Long senderId, String url) {
        if (url.startsWith("local://")
                || url.contains("placeholder")
                || url.contains("preview")
                || url.startsWith("http://")
                || url.startsWith("https://")
                || !url.startsWith("/uploads/")) {
            throw new IllegalArgumentException("image url invalid");
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM media_upload_ticket
                WHERE owner_user_id = ? AND scene = 'CHAT_IMAGE' AND storage_url = ? AND status = 'ISSUED'
                  AND expires_at > CURRENT_TIMESTAMP
                """, Integer.class, senderId, url);
        if (count == null || count <= 0) {
            throw new IllegalArgumentException("chat image ticket invalid");
        }
    }

    private Map<String, Object> parseJsonObject(String content) {
        try {
            return OBJECT_MAPPER.readValue(content, JSON_OBJECT_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("contentJson invalid");
        }
    }

    private boolean isAllowedImageUrl(String url) {
        return url.startsWith("/uploads/");
    }

    private void validateOptionalPositiveInt(Object value, String errorMessage, int min, int max) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(errorMessage);
        }
        long longValue = number.longValue();
        if (longValue < min || longValue > max || Double.compare(number.doubleValue(), longValue) != 0) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private void validateOptionalPositiveLong(Object value, String errorMessage, long min, long max) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(errorMessage);
        }
        long longValue = number.longValue();
        if (longValue < min || longValue > max || Double.compare(number.doubleValue(), longValue) != 0) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private void validateConversationParticipants(Conversation conversation, SendMessageCommand command) {
        boolean senderMatched = Objects.equals(command.getSenderId(), conversation.getOwnerUserId())
                || Objects.equals(command.getSenderId(), conversation.getPeerUserId());
        boolean receiverMatched = Objects.equals(command.getReceiverId(), conversation.getOwnerUserId())
                || Objects.equals(command.getReceiverId(), conversation.getPeerUserId());
        if (!senderMatched || !receiverMatched) {
            throw new IllegalArgumentException("message participants not in conversation");
        }
    }

    private Conversation requireParticipantConversation(Long conversationId, Long userId) {
        Conversation conversation = requireConversation(conversationId);
        if (!isParticipant(conversation, userId)) {
            throw new IllegalArgumentException("user not in conversation");
        }
        return conversation;
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0L) {
            throw new IllegalArgumentException("userId required");
        }
    }

    private boolean isParticipant(Conversation conversation, Long userId) {
        return Objects.equals(userId, conversation.getOwnerUserId()) || Objects.equals(userId, conversation.getPeerUserId());
    }

    private long getReadSeq(Long conversationId, Long userId) {
        Long value = queryLong("SELECT read_seq FROM im_receipt WHERE conversation_id = ? AND user_id = ?", conversationId, userId);
        return value == null ? 0L : value;
    }

    private long getDeliveredSeq(Long conversationId, Long userId) {
        Long value = queryLong("SELECT delivered_seq FROM im_receipt WHERE conversation_id = ? AND user_id = ?", conversationId, userId);
        return value == null ? 0L : value;
    }

    private void upsertReceiptSeq(Long conversationId, Long userId, Long readSeq, Long deliveredSeq) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM im_receipt WHERE conversation_id = ? AND user_id = ?", Integer.class, conversationId, userId);
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE im_receipt
                    SET read_seq = GREATEST(read_seq, ?), delivered_seq = GREATEST(delivered_seq, ?), updated_at = CURRENT_TIMESTAMP
                    WHERE conversation_id = ? AND user_id = ?
                    """, readSeq, deliveredSeq, conversationId, userId);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO im_receipt (conversation_id, user_id, read_seq, delivered_seq, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, conversationId, userId, readSeq, deliveredSeq);
    }

    private long unreadCount(long lastServerSeq, long readSeq) {
        return Math.max(0L, lastServerSeq - readSeq);
    }

    private long safeLastServerSeq(Conversation conversation) {
        return conversation.getLastServerSeq() == null ? 0L : conversation.getLastServerSeq();
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message, Long viewerUserId) {
        ChatMessageResponse response = new ChatMessageResponse();
        response.setConversationId(message.getConversationId());
        response.setServerSeq(message.getServerSeq());
        response.setServerMsgId(message.getServerMsgId());
        response.setClientMsgId(message.getClientMsgId());
        response.setSenderId(message.getSenderId());
        response.setReceiverId(message.getReceiverId());
        response.setMsgType(message.getMsgType());
        response.setContentJson(message.getContentJson());
        response.setCreatedAt(message.getCreatedAt());
        if (Objects.equals(message.getSenderId(), viewerUserId)) {
            response.setDeliveredToReceiver(getDeliveredSeq(message.getConversationId(), message.getReceiverId()) >= message.getServerSeq());
            response.setReadByReceiver(getReadSeq(message.getConversationId(), message.getReceiverId()) >= message.getServerSeq());
        } else {
            response.setDeliveredToReceiver(null);
            response.setReadByReceiver(null);
        }
        return response;
    }

    private String buildMessageSummary(SendMessageCommand command) {
        String msgType = normalizeMsgType(command.getMsgType());
        if (MessageType.TEXT.name().equals(msgType)) {
            String content = command.getContentJson() == null ? "" : command.getContentJson().trim();
            String marker = "\"text\"";
            int keyIndex = content.indexOf(marker);
            if (keyIndex >= 0) {
                int colonIndex = content.indexOf(':', keyIndex + marker.length());
                int startQuoteIndex = colonIndex < 0 ? -1 : content.indexOf('"', colonIndex + 1);
                int endQuoteIndex = startQuoteIndex < 0 ? -1 : content.indexOf('"', startQuoteIndex + 1);
                if (endQuoteIndex > startQuoteIndex) {
                    return content.substring(startQuoteIndex + 1, endQuoteIndex);
                }
            }
            return "[文字]";
        }
        if (MessageType.IMAGE.name().equals(msgType)) {
            return "[图片]";
        }
        return "[消息]";
    }

    private ChatMessageAck toAck(ChatMessage message) {
        ChatMessageAck ack = new ChatMessageAck();
        ack.setMessageId(message.getServerMsgId());
        ack.setConversationId(message.getConversationId());
        ack.setServerSeq(message.getServerSeq());
        ack.setServerMsgId(message.getServerMsgId());
        ack.setClientMsgId(message.getClientMsgId());
        ack.setSendState("sent");
        ack.setServerTs(message.getCreatedAt());
        ack.setSenderId(message.getSenderId());
        ack.setReceiverId(message.getReceiverId());
        ack.setMsgType(message.getMsgType());
        return ack;
    }

    private Conversation requireConversation(Long conversationId) {
        List<Conversation> rows = jdbcTemplate.query("""
                SELECT id, owner_user_id, peer_user_id, conversation_type, last_seq, last_message_summary, created_at, updated_at
                FROM im_conversation
                WHERE id = ?
                """, (rs, rowNum) -> mapConversation(rs), conversationId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("conversation not found");
        }
        return rows.get(0);
    }

    private ChatMessage requireMessageByClientKey(String clientKey) {
        ChatMessage message = findMessageByClientKey(clientKey);
        if (message == null) {
            throw new IllegalStateException("message persistence failed");
        }
        return message;
    }

    private ChatMessage findMessageByClientKey(String clientKey) {
        List<ChatMessage> rows = jdbcTemplate.query("""
                SELECT id, conversation_id, server_seq, client_msg_id, message_no, sender_id, receiver_id, message_type, content_json, created_at, updated_at
                FROM im_message
                WHERE client_key = ?
                """, (rs, rowNum) -> mapMessage(rs), clientKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Conversation mapConversation(ResultSet rs) throws SQLException {
        Conversation conversation = new Conversation();
        conversation.setId(rs.getLong("id"));
        conversation.setOwnerUserId(rs.getLong("owner_user_id"));
        conversation.setPeerUserId(rs.getLong("peer_user_id"));
        conversation.setConversationType(rs.getString("conversation_type"));
        conversation.setLastServerSeq(rs.getLong("last_seq"));
        conversation.setLastMessageSummary(rs.getString("last_message_summary"));
        conversation.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        conversation.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        return conversation;
    }

    private ChatMessage mapMessage(ResultSet rs) throws SQLException {
        ChatMessage message = new ChatMessage();
        message.setId(rs.getLong("id"));
        message.setConversationId(rs.getLong("conversation_id"));
        message.setServerSeq(rs.getLong("server_seq"));
        message.setClientMsgId(rs.getString("client_msg_id"));
        message.setServerMsgId(rs.getString("message_no"));
        message.setSenderId(rs.getLong("sender_id"));
        message.setReceiverId(rs.getLong("receiver_id"));
        message.setMsgType(rs.getString("message_type"));
        message.setContentJson(rs.getString("content_json"));
        message.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        message.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        return message;
    }

    private Long queryLong(String sql, Object... args) {
        List<Long> rows = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong(1), args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Long requireLong(String sql, Object... args) {
        Long value = queryLong(sql, args);
        if (value == null) {
            throw new IllegalStateException("required row not found");
        }
        return value;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String conversationNo(Conversation conversation) {
        return conversationNo(conversation.getOwnerUserId(), conversation.getPeerUserId());
    }

    private String conversationNo(Long userA, Long userB) {
        long min = Math.min(userA, userB);
        long max = Math.max(userA, userB);
        return "IM-SINGLE-" + min + '-' + max;
    }

    private String clientMessageKey(Long conversationId, Long senderId, String clientMsgId) {
        return conversationId + ":" + senderId + ":" + clientMsgId.trim();
    }

    private String normalizeMsgType(String msgType) {
        return MessageType.from(msgType).name();
    }
}
