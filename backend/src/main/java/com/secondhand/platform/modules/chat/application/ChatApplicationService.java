package com.secondhand.platform.modules.chat.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondhand.platform.modules.chat.ChatMediaAccessResponse;
import com.secondhand.platform.modules.chat.ChatMessageAck;
import com.secondhand.platform.modules.chat.ChatMessageResponse;
import com.secondhand.platform.modules.chat.ClearConversationResponse;
import com.secondhand.platform.modules.chat.ConversationListItemResponse;
import com.secondhand.platform.modules.chat.DeliveryReceiptResponse;
import com.secondhand.platform.modules.chat.MessageSyncResponse;
import com.secondhand.platform.modules.chat.ReadConversationResponse;
import com.secondhand.platform.modules.chat.RevokeMessageResponse;
import com.secondhand.platform.modules.chat.domain.ChatMessage;
import com.secondhand.platform.modules.chat.domain.Conversation;
import com.secondhand.platform.modules.media.application.MediaUploadTicketService;
import com.secondhand.platform.modules.notification.application.NotificationApplicationService;
import com.secondhand.platform.shared.contracts.chat.MessageType;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatApplicationService {
    private static final String CONVERSATION_TYPE_SINGLE = "SINGLE";
    private static final int MAX_CLIENT_MSG_ID_LENGTH = 64;
    private static final int MAX_CONTENT_JSON_LENGTH = 4000;
    private static final int MAX_TEXT_LENGTH = 1000;
    private static final int MAX_MESSAGE_SUMMARY_LENGTH = 80;
    private static final int MAX_IMAGE_URL_LENGTH = 1024;
    private static final int MAX_IMAGE_MIME_TYPE_LENGTH = 64;
    private static final long MAX_IMAGE_SIZE_BYTES = 20L * 1024L * 1024L;
    private static final int MAX_VOICE_URL_LENGTH = 1024;
    private static final int MAX_VOICE_MIME_TYPE_LENGTH = 64;
    private static final long MAX_VOICE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final long MAX_VOICE_DURATION_MS = 600L * 1000L;
    private static final int DEFAULT_SYNC_LIMIT = 50;
    private static final int MAX_SYNC_LIMIT = 200;
    private static final int MESSAGE_REVOKE_WINDOW_MINUTES = 2;
    private static final String REVOKED_MESSAGE_CONTENT = "{\"revoked\":true}";
    private static final Pattern MOBILE_PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Set<String> BLOCKED_TEXT_TOKENS = Set.of(
            "微信",
            "weixin",
            "wechat",
            "v信",
            "vx",
            "qq",
            "支付宝",
            "alipay",
            "银行卡",
            "转账",
            "私下交易",
            "线下交易",
            "绕平台",
            "脱离平台",
            "不走平台",
            "先付款"
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> JSON_OBJECT_TYPE = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final MediaUploadTicketService mediaUploadTicketService;
    private final NotificationApplicationService notificationApplicationService;

    public ChatApplicationService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new MediaUploadTicketService(jdbcTemplate), new NotificationApplicationService(jdbcTemplate));
    }

    @Autowired
    public ChatApplicationService(JdbcTemplate jdbcTemplate, MediaUploadTicketService mediaUploadTicketService) {
        this(jdbcTemplate, mediaUploadTicketService, new NotificationApplicationService(jdbcTemplate));
    }

    public ChatApplicationService(JdbcTemplate jdbcTemplate, MediaUploadTicketService mediaUploadTicketService, NotificationApplicationService notificationApplicationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.mediaUploadTicketService = mediaUploadTicketService;
        this.notificationApplicationService = notificationApplicationService;
        ensureChatSchemaCompatibility();
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
        try {
            jdbcTemplate.update("""
                    INSERT INTO im_conversation (
                      conversation_no, owner_user_id, peer_user_id, conversation_type, last_seq, last_message_summary, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, conversationNo, minUserId, maxUserId, CONVERSATION_TYPE_SINGLE);
        } catch (DuplicateKeyException ex) {
            Long winnerId = queryLong("SELECT id FROM im_conversation WHERE conversation_no = ?", conversationNo);
            if (winnerId == null) {
                throw new IllegalStateException("conversation-create-duplicate-without-existing-row", ex);
            }
            return winnerId;
        }
        return requireLong("SELECT id FROM im_conversation WHERE conversation_no = ?", conversationNo);
    }

    @Transactional
    public ChatMessageAck sendMessage(SendMessageCommand command) {
        validateMessage(command);
        Long conversationId = resolveConversationId(command);
        Conversation conversation = requireConversationForUpdate(conversationId);
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
        notifyChatMessageReceived(command, conversationId, serverSeq);
        return toAck(requireMessageByClientKey(clientKey));
    }

    public List<ConversationListItemResponse> listConversations(Long userId) {
        validateUserId(userId);
        return jdbcTemplate.query("""
                SELECT c.id, c.owner_user_id, c.peer_user_id, c.last_seq, c.last_message_summary, c.updated_at,
                       COALESCE(viewer_receipt.cleared_seq, 0) AS viewer_cleared_seq,
                       peer.nickname AS peer_nickname, peer.avatar_url AS peer_avatar_url,
                       profile.gender AS peer_gender,
                       profile.city AS peer_city,
                       COALESCE(profile.main_role, 'BUYER') AS peer_main_role,
                       CASE WHEN COALESCE(profile.video_identity_status, 'UNVERIFIED') = 'APPROVED'
                                 AND COALESCE(profile.video_verified, FALSE) = TRUE
                                 AND UPPER(COALESCE(profile.main_role, 'BUYER')) IN ('SELLER', 'BOTH')
                                 AND EXISTS (
                                     SELECT 1
                                     FROM media_upload_ticket video_ticket
                                     WHERE video_ticket.owner_user_id = profile.user_id
                                       AND video_ticket.scene = 'VIDEO_IDENTITY'
                                       AND video_ticket.status = 'UPLOADED'
                                       AND video_ticket.storage_url LIKE '/uploads/video-identity/%'
                                       AND EXISTS (
                                           SELECT 1
                                           FROM audit_record audit
                                           WHERE audit.audit_type = 'VIDEO_IDENTITY'
                                             AND audit.user_id = profile.user_id
                                             AND audit.target_id = CONCAT('', profile.user_id)
                                             AND audit.status = 'APPROVED'
                                             AND audit.reason = video_ticket.storage_url
                                       )
                                 )
                            THEN TRUE ELSE FALSE END AS peer_video_verified,
                       COALESCE(received_gifts.seller_charm_score, 0) AS peer_seller_charm_score,
                       FLOOR(COALESCE(sent_gifts.sent_gift_amount, 0) + COALESCE(paid_orders.paid_order_amount, 0)) AS peer_buyer_power_score
                FROM im_conversation c
                JOIN user_account peer ON peer.id = CASE WHEN c.owner_user_id = ? THEN c.peer_user_id ELSE c.owner_user_id END AND peer.status = 'ACTIVE'
                LEFT JOIN user_profile profile ON profile.user_id = peer.id
                LEFT JOIN (
                    SELECT receiver_id, FLOOR(COALESCE(SUM(total_amount), 0)) AS seller_charm_score
                    FROM gift_order
                    WHERE status = 'SUCCESS'
                    GROUP BY receiver_id
                ) received_gifts ON received_gifts.receiver_id = peer.id
                LEFT JOIN (
                    SELECT sender_id, COALESCE(SUM(total_amount), 0) AS sent_gift_amount
                    FROM gift_order
                    WHERE status = 'SUCCESS'
                    GROUP BY sender_id
                ) sent_gifts ON sent_gifts.sender_id = peer.id
                LEFT JOIN (
                    SELECT buyer_id, COALESCE(SUM(amount), 0) AS paid_order_amount
                    FROM trade_order
                    WHERE order_status IN ('PAID', 'SHIPPED', 'COMPLETED')
                    GROUP BY buyer_id
                ) paid_orders ON paid_orders.buyer_id = peer.id
                LEFT JOIN im_receipt viewer_receipt ON viewer_receipt.conversation_id = c.id AND viewer_receipt.user_id = ?
                WHERE (c.owner_user_id = ? OR c.peer_user_id = ?)
                  AND (c.last_seq = 0 OR c.last_seq > COALESCE(viewer_receipt.cleared_seq, 0))
                ORDER BY c.updated_at DESC, c.id DESC
                """, (rs, rowNum) -> toConversationItem(rs, userId), userId, userId, userId, userId);
    }

    public ConversationListItemResponse getConversation(Long conversationId, Long userId) {
        validateUserId(userId);
        requireParticipantConversation(conversationId, userId);
        List<ConversationListItemResponse> rows = jdbcTemplate.query("""
                SELECT c.id, c.owner_user_id, c.peer_user_id, c.last_seq, c.last_message_summary, c.updated_at,
                       COALESCE(viewer_receipt.cleared_seq, 0) AS viewer_cleared_seq,
                       peer.nickname AS peer_nickname, peer.avatar_url AS peer_avatar_url,
                       profile.gender AS peer_gender,
                       profile.city AS peer_city,
                       COALESCE(profile.main_role, 'BUYER') AS peer_main_role,
                       CASE WHEN COALESCE(profile.video_identity_status, 'UNVERIFIED') = 'APPROVED'
                                 AND COALESCE(profile.video_verified, FALSE) = TRUE
                                 AND UPPER(COALESCE(profile.main_role, 'BUYER')) IN ('SELLER', 'BOTH')
                                 AND EXISTS (
                                     SELECT 1
                                     FROM media_upload_ticket video_ticket
                                     WHERE video_ticket.owner_user_id = profile.user_id
                                       AND video_ticket.scene = 'VIDEO_IDENTITY'
                                       AND video_ticket.status = 'UPLOADED'
                                       AND video_ticket.storage_url LIKE '/uploads/video-identity/%'
                                       AND EXISTS (
                                           SELECT 1
                                           FROM audit_record audit
                                           WHERE audit.audit_type = 'VIDEO_IDENTITY'
                                             AND audit.user_id = profile.user_id
                                             AND audit.target_id = CONCAT('', profile.user_id)
                                             AND audit.status = 'APPROVED'
                                             AND audit.reason = video_ticket.storage_url
                                       )
                                 )
                            THEN TRUE ELSE FALSE END AS peer_video_verified,
                       COALESCE(received_gifts.seller_charm_score, 0) AS peer_seller_charm_score,
                       FLOOR(COALESCE(sent_gifts.sent_gift_amount, 0) + COALESCE(paid_orders.paid_order_amount, 0)) AS peer_buyer_power_score
                FROM im_conversation c
                JOIN user_account peer ON peer.id = CASE WHEN c.owner_user_id = ? THEN c.peer_user_id ELSE c.owner_user_id END AND peer.status = 'ACTIVE'
                LEFT JOIN user_profile profile ON profile.user_id = peer.id
                LEFT JOIN (
                    SELECT receiver_id, FLOOR(COALESCE(SUM(total_amount), 0)) AS seller_charm_score
                    FROM gift_order
                    WHERE status = 'SUCCESS'
                    GROUP BY receiver_id
                ) received_gifts ON received_gifts.receiver_id = peer.id
                LEFT JOIN (
                    SELECT sender_id, COALESCE(SUM(total_amount), 0) AS sent_gift_amount
                    FROM gift_order
                    WHERE status = 'SUCCESS'
                    GROUP BY sender_id
                ) sent_gifts ON sent_gifts.sender_id = peer.id
                LEFT JOIN (
                    SELECT buyer_id, COALESCE(SUM(amount), 0) AS paid_order_amount
                    FROM trade_order
                    WHERE order_status IN ('PAID', 'SHIPPED', 'COMPLETED')
                    GROUP BY buyer_id
                ) paid_orders ON paid_orders.buyer_id = peer.id
                LEFT JOIN im_receipt viewer_receipt ON viewer_receipt.conversation_id = c.id AND viewer_receipt.user_id = ?
                WHERE c.id = ? AND (c.owner_user_id = ? OR c.peer_user_id = ?)
                """, (rs, rowNum) -> toConversationItem(rs, userId), userId, userId, conversationId, userId, userId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("conversation not found");
        }
        return rows.get(0);
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
                SELECT id, conversation_id, server_seq, client_msg_id, message_no, sender_id, receiver_id, message_type, content_json, revoked, created_at, updated_at
                FROM im_message
                WHERE conversation_id = ? AND server_seq > ? AND server_seq > ?
                ORDER BY server_seq ASC
                LIMIT ?
                """, (rs, rowNum) -> toMessageResponse(mapMessage(rs), userId), conversationId, normalizedAfterSeq, getClearedSeq(conversationId, userId), normalizedLimit + 1);
        boolean hasMore = rows.size() > normalizedLimit;
        List<ChatMessageResponse> pageMessages = hasMore ? rows.subList(0, normalizedLimit) : rows;
        long nextAfterSeq = pageMessages.isEmpty() ? normalizedAfterSeq : pageMessages.get(pageMessages.size() - 1).getServerSeq();
        long nextDeliveredSeq = Math.min(Math.max(getDeliveredSeq(conversationId, userId), nextAfterSeq), safeLastServerSeq(conversation));
        upsertReceiptSeq(conversationId, userId, getReadSeq(conversationId, userId), nextDeliveredSeq);
        return new MessageSyncResponse(pageMessages, nextAfterSeq, hasMore);
    }

    @Transactional
    public MessageSyncResponse syncEarlierMessages(Long conversationId, Long userId, Long beforeSeq, Integer limit) {
        validateUserId(userId);
        requireParticipantConversation(conversationId, userId);
        long normalizedBeforeSeq = beforeSeq == null ? 0L : beforeSeq;
        if (normalizedBeforeSeq <= 0L) {
            throw new IllegalArgumentException("beforeSeq invalid");
        }
        int normalizedLimit = limit == null ? DEFAULT_SYNC_LIMIT : limit;
        if (normalizedLimit <= 0) {
            throw new IllegalArgumentException("limit invalid");
        }
        normalizedLimit = Math.min(normalizedLimit, MAX_SYNC_LIMIT);
        long clearedSeq = getClearedSeq(conversationId, userId);

        List<ChatMessageResponse> descendingRows = jdbcTemplate.query("""
                SELECT id, conversation_id, server_seq, client_msg_id, message_no, sender_id, receiver_id, message_type, content_json, revoked, created_at, updated_at
                FROM im_message
                WHERE conversation_id = ? AND server_seq > ? AND server_seq < ?
                ORDER BY server_seq DESC
                LIMIT ?
                """, (rs, rowNum) -> toMessageResponse(mapMessage(rs), userId), conversationId, clearedSeq, normalizedBeforeSeq, normalizedLimit + 1);
        boolean hasEarlier = descendingRows.size() > normalizedLimit;
        List<ChatMessageResponse> pageMessages = (hasEarlier ? descendingRows.subList(0, normalizedLimit) : descendingRows).stream()
                .sorted((left, right) -> Long.compare(left.getServerSeq(), right.getServerSeq()))
                .toList();
        long previousBeforeSeq = pageMessages.isEmpty() ? normalizedBeforeSeq : pageMessages.get(0).getServerSeq();
        long nextAfterSeq = pageMessages.isEmpty() ? normalizedBeforeSeq : pageMessages.get(pageMessages.size() - 1).getServerSeq();
        return new MessageSyncResponse(pageMessages, nextAfterSeq, false, previousBeforeSeq, hasEarlier);
    }

    @Transactional
    public MessageSyncResponse syncRecentMessages(Long conversationId, Long userId, Integer limit) {
        validateUserId(userId);
        Conversation conversation = requireParticipantConversation(conversationId, userId);
        int normalizedLimit = limit == null ? DEFAULT_SYNC_LIMIT : limit;
        if (normalizedLimit <= 0) {
            throw new IllegalArgumentException("limit invalid");
        }
        normalizedLimit = Math.min(normalizedLimit, MAX_SYNC_LIMIT);
        long clearedSeq = getClearedSeq(conversationId, userId);

        List<ChatMessageResponse> descendingRows = jdbcTemplate.query("""
                SELECT id, conversation_id, server_seq, client_msg_id, message_no, sender_id, receiver_id, message_type, content_json, revoked, created_at, updated_at
                FROM im_message
                WHERE conversation_id = ? AND server_seq > ?
                ORDER BY server_seq DESC
                LIMIT ?
                """, (rs, rowNum) -> toMessageResponse(mapMessage(rs), userId), conversationId, clearedSeq, normalizedLimit);
        List<ChatMessageResponse> pageMessages = descendingRows.stream()
                .sorted((left, right) -> Long.compare(left.getServerSeq(), right.getServerSeq()))
                .toList();
        long nextAfterSeq = pageMessages.isEmpty() ? clearedSeq : pageMessages.get(pageMessages.size() - 1).getServerSeq();
        long previousBeforeSeq = pageMessages.isEmpty() ? nextAfterSeq : pageMessages.get(0).getServerSeq();
        boolean hasEarlier = previousBeforeSeq > clearedSeq + 1L;
        long nextDeliveredSeq = Math.min(Math.max(getDeliveredSeq(conversationId, userId), nextAfterSeq), safeLastServerSeq(conversation));
        upsertReceiptSeq(conversationId, userId, getReadSeq(conversationId, userId), nextDeliveredSeq);
        return new MessageSyncResponse(pageMessages, nextAfterSeq, false, previousBeforeSeq, hasEarlier);
    }

    @Transactional
    public DeliveryReceiptResponse markConversationDelivered(Long conversationId, Long userId) {
        validateUserId(userId);
        Conversation conversation = requireParticipantConversation(conversationId, userId);
        long lastServerSeq = safeLastServerSeq(conversation);
        long readSeq = getReadSeq(conversationId, userId);
        upsertReceiptSeq(conversationId, userId, readSeq, lastServerSeq);
        return new DeliveryReceiptResponse(conversationId, lastServerSeq, readSeq, lastServerSeq, unreadCount(conversationId, userId, readSeq));
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
        return new ReadConversationResponse(conversationId, nextReadSeq, nextDeliveredSeq, lastServerSeq, unreadCount(conversationId, userId, nextReadSeq));
    }

    @Transactional
    public RevokeMessageResponse revokeMessage(String serverMsgId, Long userId) {
        validateUserId(userId);
        if (serverMsgId == null || serverMsgId.isBlank() || serverMsgId.trim().length() > 128) {
            throw new IllegalArgumentException("serverMsgId invalid");
        }
        ChatMessage message = requireMessageByServerMsgId(serverMsgId.trim());
        Conversation conversation = requireParticipantConversation(message.getConversationId(), userId);
        if (!Objects.equals(message.getSenderId(), userId)) {
            throw new IllegalArgumentException("only sender can revoke message");
        }
        if (!Boolean.TRUE.equals(message.getRevoked())) {
            requireWithinRevokeWindow(message);
            jdbcTemplate.update("""
                    UPDATE im_message
                    SET revoked = TRUE, revoked_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND revoked = FALSE
                    """, message.getId());
        }
        if (Objects.equals(message.getServerSeq(), safeLastServerSeq(conversation))) {
            jdbcTemplate.update("""
                    UPDATE im_conversation
                    SET last_message_summary = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, "消息已撤回", message.getConversationId());
        }
        return new RevokeMessageResponse(message.getConversationId(), message.getServerSeq(), message.getServerMsgId(), true);
    }

    @Transactional
    public ClearConversationResponse clearConversation(Long conversationId, Long userId) {
        validateUserId(userId);
        Conversation conversation = requireParticipantConversation(conversationId, userId);
        long lastServerSeq = safeLastServerSeq(conversation);
        long currentReadSeq = getReadSeq(conversationId, userId);
        long currentDeliveredSeq = getDeliveredSeq(conversationId, userId);
        upsertReceiptSeq(conversationId, userId, currentReadSeq, currentDeliveredSeq, lastServerSeq);
        return new ClearConversationResponse(conversationId, lastServerSeq, lastServerSeq);
    }

    public ChatMediaAccessResponse requireChatMediaAccess(Long viewerUserId, String storageUrl) {
        validateUserId(viewerUserId);
        String safeUrl = normalizeChatMediaStorageUrl(storageUrl);
        String scene = sceneForChatMediaUrl(safeUrl);
        Long ownerUserId = loadChatMediaOwnerUserId(safeUrl, scene);
        mediaUploadTicketService.requireUploadedStorageUrl(ownerUserId, scene, safeUrl);
        String jsonEscapedSafeUrl = safeUrl.replace("/", "\\/");
        List<ChatMediaAccessResponse> rows = jdbcTemplate.query("""
                SELECT m.conversation_id, m.sender_id, m.receiver_id, m.message_type, m.content_json, t.content_type, t.file_size
                FROM im_message m
                JOIN media_upload_ticket t ON t.storage_url = ?
                LEFT JOIN im_receipt viewer_receipt ON viewer_receipt.conversation_id = m.conversation_id AND viewer_receipt.user_id = ?
                WHERE m.message_type IN ('IMAGE', 'VOICE')
                  AND m.revoked = FALSE
                  AND (m.sender_id = ? OR m.receiver_id = ?)
                  AND m.server_seq > COALESCE(viewer_receipt.cleared_seq, 0)
                  AND t.owner_user_id = m.sender_id
                  AND t.scene = ?
                  AND t.status = 'UPLOADED'
                  AND (LOCATE(?, m.content_json) > 0 OR LOCATE(?, m.content_json) > 0)
                ORDER BY m.id DESC
                """, (rs, rowNum) -> {
            String messageType = rs.getString("message_type");
            String contentJson = rs.getString("content_json");
            if (!chatMediaContentReferencesUrl(messageType, contentJson, safeUrl)) {
                return null;
            }
            return new ChatMediaAccessResponse(
                    safeUrl,
                    rs.getLong("conversation_id"),
                    rs.getLong("sender_id"),
                    rs.getLong("receiver_id"),
                    rs.getString("content_type"),
                    rs.getLong("file_size")
            );
        }, safeUrl, viewerUserId, viewerUserId, viewerUserId, scene, safeUrl, jsonEscapedSafeUrl).stream().filter(Objects::nonNull).toList();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("chat media access denied");
        }
        return rows.get(0);
    }

    private boolean chatMediaContentReferencesUrl(String messageType, String contentJson, String safeUrl) {
        if (contentJson == null || contentJson.isBlank()) {
            return false;
        }
        Map<String, Object> jsonObject;
        try {
            jsonObject = parseLegacyCompatibleJsonObject(contentJson);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        if (MessageType.IMAGE.name().equals(messageType)) {
            return Objects.equals(jsonObject.get("url"), safeUrl);
        }
        if (MessageType.VOICE.name().equals(messageType)) {
            return Objects.equals(jsonObject.get("url"), safeUrl)
                    || Objects.equals(jsonObject.get("audioUrl"), safeUrl)
                    || Objects.equals(jsonObject.get("voiceUrl"), safeUrl);
        }
        return false;
    }

    private Map<String, Object> parseLegacyCompatibleJsonObject(String contentJson) {
        String content = contentJson.trim();
        for (int index = 0; index < 3; index += 1) {
            try {
                return parseJsonObject(content);
            } catch (IllegalArgumentException ignored) {
                String unwrapped = unwrapJsonString(content);
                if (unwrapped.equals(content)) {
                    throw ignored;
                }
                content = unwrapped.trim();
            }
        }
        return parseJsonObject(content);
    }

    private String unwrapJsonString(String content) {
        try {
            String value = OBJECT_MAPPER.readValue(content, String.class);
            if (value != null && value.trim().startsWith(String.valueOf((char) 123))) {
                return value;
            }
        } catch (JsonProcessingException ignored) {
            // Some legacy H5 bundles stored escaped JSON without wrapping quotes.
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("{\\\"") && trimmed.endsWith("}")) {
            return trimmed.replace("\\\"", "\"");
        }
        return content;
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
        long clearedSeq = rs.getLong("viewer_cleared_seq");
        boolean clearedAllVisibleMessages = lastServerSeq > 0 && lastServerSeq <= clearedSeq;
        long readSeq = getReadSeq(conversationId, userId);
        long deliveredSeq = getDeliveredSeq(conversationId, userId);
        ConversationListItemResponse item = new ConversationListItemResponse();
        item.setConversationId(conversationId);
        item.setPeerUserId(Objects.equals(userId, rs.getLong("owner_user_id")) ? rs.getLong("peer_user_id") : rs.getLong("owner_user_id"));
        item.setPeerNickname(rs.getString("peer_nickname"));
        item.setPeerAvatarUrl(rs.getString("peer_avatar_url"));
        item.setPeerGender(rs.getString("peer_gender"));
        item.setPeerCity(rs.getString("peer_city"));
        item.setPeerMainRole(rs.getString("peer_main_role"));
        item.setPeerVideoVerified(rs.getBoolean("peer_video_verified"));
        item.setPeerSellerCharmScore(rs.getInt("peer_seller_charm_score"));
        item.setPeerBuyerPowerScore(rs.getInt("peer_buyer_power_score"));
        item.setLastMessageSummary(clearedAllVisibleMessages ? null : rs.getString("last_message_summary"));
        item.setLastServerSeq(lastServerSeq);
        item.setDeliveredSeq(deliveredSeq);
        item.setReadSeq(readSeq);
        item.setUnreadCount(clearedAllVisibleMessages ? 0L : unreadCount(conversationId, userId, readSeq));
        item.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        return item;
    }

    private void validateConversation(CreateConversationCommand command) {
        if (command == null || command.getOwnerUserId() == null || command.getPeerUserId() == null) {
            throw new IllegalArgumentException("conversation participants required");
        }
        validateUserId(command.getOwnerUserId());
        validateUserId(command.getPeerUserId());
        if (Objects.equals(command.getOwnerUserId(), command.getPeerUserId())) {
            throw new IllegalArgumentException("conversation participants must be different");
        }
        requireActiveUser(command.getOwnerUserId(), "conversation owner not found");
        requireActiveUser(command.getPeerUserId(), "conversation peer not found");
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
            requireTextContent(content);
            return;
        }
        if (MessageType.IMAGE.name().equals(msgType)) {
            validateImageContent(senderId, content);
            return;
        }
        if (MessageType.VOICE.name().equals(msgType)) {
            validateVoiceContent(senderId, content);
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
                || !url.startsWith("/uploads/chat-image/")) {
            throw new IllegalArgumentException("image url invalid");
        }
        mediaUploadTicketService.requireUploadedStorageUrl(senderId, "CHAT_IMAGE", url);
    }

    private void validateVoiceContent(Long senderId, String content) {
        Map<String, Object> jsonObject = parseJsonObject(content);
        Object urlValue = jsonObject.get("url");
        if (!(urlValue instanceof String url) || url.isBlank() || url.length() > MAX_VOICE_URL_LENGTH) {
            throw new IllegalArgumentException("voice url invalid");
        }
        validateChatVoiceTicket(senderId, url);
        long durationMs = voiceDurationMs(jsonObject);
        if (durationMs <= 0L || durationMs > MAX_VOICE_DURATION_MS) {
            throw new IllegalArgumentException("voice duration invalid");
        }
        validateOptionalPositiveLong(jsonObject.get("sizeBytes"), "voice sizeBytes invalid", 1L, MAX_VOICE_SIZE_BYTES);
        Object mimeTypeValue = jsonObject.get("mimeType");
        if (!(mimeTypeValue instanceof String mimeType)
                || mimeType.isBlank()
                || mimeType.length() > MAX_VOICE_MIME_TYPE_LENGTH
                || !mimeType.matches("audio/(webm|mp4|mpeg|wav|aac|x-m4a)")) {
            throw new IllegalArgumentException("voice mimeType invalid");
        }
    }

    private long voiceDurationMs(Map<String, Object> jsonObject) {
        Object durationMsValue = jsonObject.get("durationMs");
        if (durationMsValue instanceof Number number) {
            long longValue = number.longValue();
            if (Double.compare(number.doubleValue(), longValue) != 0) {
                throw new IllegalArgumentException("voice duration invalid");
            }
            return longValue;
        }
        Object durationSecondsValue = jsonObject.get("durationSeconds");
        if (durationSecondsValue instanceof Number number) {
            double seconds = number.doubleValue();
            if (!Double.isFinite(seconds)) {
                throw new IllegalArgumentException("voice duration invalid");
            }
            return Math.round(seconds * 1000D);
        }
        throw new IllegalArgumentException("voice duration invalid");
    }

    private void validateChatVoiceTicket(Long senderId, String url) {
        if (url.startsWith("local://")
                || url.startsWith("blob:")
                || url.startsWith("data:")
                || url.contains("placeholder")
                || url.contains("preview")
                || url.startsWith("http://")
                || url.startsWith("https://")
                || !url.startsWith("/uploads/chat-voice/")) {
            throw new IllegalArgumentException("voice url invalid");
        }
        mediaUploadTicketService.requireUploadedStorageUrl(senderId, "CHAT_VOICE", url);
    }

    private Map<String, Object> parseJsonObject(String content) {
        try {
            return OBJECT_MAPPER.readValue(content, JSON_OBJECT_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("contentJson invalid");
        }
    }

    private String requireTextContent(String content) {
        Map<String, Object> jsonObject = parseJsonObject(content);
        Object textValue = jsonObject.get("text");
        if (!(textValue instanceof String text)) {
            throw new IllegalArgumentException("text content required");
        }
        String normalizedText = text.trim();
        if (normalizedText.isEmpty() || normalizedText.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("text content required");
        }
        if (containsBlockedContactOrOffPlatformTrade(normalizedText)) {
            throw new IllegalArgumentException("contact info is not allowed");
        }
        return normalizedText;
    }

    private boolean containsBlockedContactOrOffPlatformTrade(String text) {
        String compact = text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。！？、；：‘’“”（）【】《》￥]+", "");
        String digitsOnly = text.replaceAll("\\D", "");
        if (MOBILE_PHONE_PATTERN.matcher(digitsOnly).find()) {
            return true;
        }
        return BLOCKED_TEXT_TOKENS.stream().anyMatch(compact::contains);
    }

    private boolean isAllowedImageUrl(String url) {
        return url.startsWith("/uploads/");
    }

    private String normalizeChatMediaStorageUrl(String storageUrl) {
        if (storageUrl == null || storageUrl.isBlank()) {
            throw new IllegalArgumentException("chat media url required");
        }
        String safeUrl = storageUrl.trim();
        String lower = safeUrl.toLowerCase();
        if (safeUrl.length() > MAX_IMAGE_URL_LENGTH
                || safeUrl.startsWith("local://")
                || safeUrl.startsWith("blob:")
                || safeUrl.startsWith("data:")
                || safeUrl.startsWith("http://")
                || safeUrl.startsWith("https://")
                || safeUrl.contains("\\")
                || safeUrl.contains("..")
                || safeUrl.contains("//")
                || lower.contains("%2e")
                || lower.contains("%2f")
                || lower.contains("%5c")
                || lower.contains("placeholder")
                || lower.contains("preview")
                || !(safeUrl.startsWith("/uploads/chat-image/") || safeUrl.startsWith("/uploads/chat-voice/"))) {
            throw new IllegalArgumentException("chat media url invalid");
        }
        return safeUrl;
    }

    private String sceneForChatMediaUrl(String storageUrl) {
        if (storageUrl.startsWith("/uploads/chat-image/")) {
            return "CHAT_IMAGE";
        }
        if (storageUrl.startsWith("/uploads/chat-voice/")) {
            return "CHAT_VOICE";
        }
        throw new IllegalArgumentException("chat media url invalid");
    }

    private Long loadChatMediaOwnerUserId(String storageUrl, String scene) {
        List<Long> rows = jdbcTemplate.queryForList("""
                SELECT owner_user_id
                FROM media_upload_ticket
                WHERE storage_url = ? AND scene = ? AND status = 'UPLOADED'
                ORDER BY id DESC
                LIMIT 1
                """, Long.class, storageUrl, scene);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("chat media ticket not found");
        }
        return rows.get(0);
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
        requireActiveUser(command.getSenderId(), "message sender not found");
        requireActiveUser(command.getReceiverId(), "message receiver not found");
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

    private void requireActiveUser(Long userId, String message) {
        validateUserId(userId);
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account WHERE id = ? AND status = 'ACTIVE'", Integer.class, userId);
        if (count == null || count <= 0) {
            throw new IllegalArgumentException(message);
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
        upsertReceiptSeq(conversationId, userId, readSeq, deliveredSeq, getClearedSeq(conversationId, userId));
    }

    private long getClearedSeq(Long conversationId, Long userId) {
        Long value = queryLong("SELECT cleared_seq FROM im_receipt WHERE conversation_id = ? AND user_id = ?", conversationId, userId);
        return value == null ? 0L : value;
    }

    private void upsertReceiptSeq(Long conversationId, Long userId, Long readSeq, Long deliveredSeq, Long clearedSeq) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM im_receipt WHERE conversation_id = ? AND user_id = ?", Integer.class, conversationId, userId);
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE im_receipt
                    SET read_seq = GREATEST(read_seq, ?), delivered_seq = GREATEST(delivered_seq, ?), cleared_seq = GREATEST(cleared_seq, ?), updated_at = CURRENT_TIMESTAMP
                    WHERE conversation_id = ? AND user_id = ?
                    """, readSeq, deliveredSeq, clearedSeq, conversationId, userId);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO im_receipt (conversation_id, user_id, read_seq, delivered_seq, cleared_seq, updated_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, conversationId, userId, readSeq, deliveredSeq, clearedSeq);
    }

    private long unreadCount(Long conversationId, Long userId, long readSeq) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM im_message
                WHERE conversation_id = ? AND receiver_id = ? AND server_seq > ? AND server_seq > ? AND revoked = FALSE
                """, Integer.class, conversationId, userId, readSeq, getClearedSeq(conversationId, userId));
        return count == null ? 0L : count.longValue();
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
        response.setContentJson(Boolean.TRUE.equals(message.getRevoked()) ? REVOKED_MESSAGE_CONTENT : message.getContentJson());
        response.setCreatedAt(message.getCreatedAt());
        response.setRevoked(Boolean.TRUE.equals(message.getRevoked()));
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
            return summarizeText(requireTextContent(command.getContentJson().trim()));
        }
        if (MessageType.IMAGE.name().equals(msgType)) {
            return "[图片]";
        }
        if (MessageType.VOICE.name().equals(msgType)) {
            return "[语音]";
        }
        return "[消息]";
    }

    private void notifyChatMessageReceived(SendMessageCommand command, long conversationId, long serverSeq) {
        if (command == null || command.getReceiverId() == null || command.getSenderId() == null || Objects.equals(command.getReceiverId(), command.getSenderId())) {
            return;
        }
        notificationApplicationService.createNotification(
                command.getReceiverId(),
                "CHAT",
                "你有一条新私信",
                displayUserName(command.getSenderId()) + " 发来新消息：" + buildMessageSummary(command),
                "/pages/chat/conversation/index?conversationId=" + conversationId + "&receiverId=" + command.getSenderId()
        );
    }

    private String displayUserName(Long userId) {
        List<String> rows = jdbcTemplate.query("""
                SELECT COALESCE(NULLIF(nickname, ''), NULLIF(user_no, ''), CONCAT('用户', id)) AS display_name
                FROM user_account
                WHERE id = ? AND status = 'ACTIVE'
                """, (rs, rowNum) -> rs.getString("display_name"), userId);
        return rows.isEmpty() ? "用户" + userId : rows.get(0);
    }

    private String summarizeText(String text) {
        if (text.length() <= MAX_MESSAGE_SUMMARY_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_MESSAGE_SUMMARY_LENGTH) + "...";
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

    private void requireWithinRevokeWindow(ChatMessage message) {
        LocalDateTime createdAt = message == null ? null : message.getCreatedAt();
        if (createdAt == null || createdAt.isBefore(LocalDateTime.now().minusMinutes(MESSAGE_REVOKE_WINDOW_MINUTES))) {
            throw new IllegalStateException("message revoke window expired");
        }
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

    private Conversation requireConversationForUpdate(Long conversationId) {
        List<Conversation> rows = jdbcTemplate.query("""
                SELECT id, owner_user_id, peer_user_id, conversation_type, last_seq, last_message_summary, created_at, updated_at
                FROM im_conversation
                WHERE id = ?
                FOR UPDATE
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
                SELECT id, conversation_id, server_seq, client_msg_id, message_no, sender_id, receiver_id, message_type, content_json, revoked, created_at, updated_at
                FROM im_message
                WHERE client_key = ?
                """, (rs, rowNum) -> mapMessage(rs), clientKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private ChatMessage requireMessageByServerMsgId(String serverMsgId) {
        List<ChatMessage> rows = jdbcTemplate.query("""
                SELECT id, conversation_id, server_seq, client_msg_id, message_no, sender_id, receiver_id, message_type, content_json, revoked, created_at, updated_at
                FROM im_message
                WHERE message_no = ?
                """, (rs, rowNum) -> mapMessage(rs), serverMsgId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("message not found");
        }
        return rows.get(0);
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
        message.setRevoked(rs.getBoolean("revoked"));
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

    private void ensureChatSchemaCompatibility() {
        ensureColumn("im_message", "revoked", "ALTER TABLE im_message ADD COLUMN revoked BOOLEAN NOT NULL DEFAULT FALSE");
        ensureColumn("im_message", "revoked_at", "ALTER TABLE im_message ADD COLUMN revoked_at TIMESTAMP");
        ensureColumn("im_receipt", "cleared_seq", "ALTER TABLE im_receipt ADD COLUMN cleared_seq BIGINT NOT NULL DEFAULT 0");
    }

    private void ensureColumn(String tableName, String columnName, String alterSql) {
        try {
            if (!columnExists(tableName, columnName)) {
                jdbcTemplate.execute(alterSql);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("chat schema compatibility check failed: " + tableName + "." + columnName, ex);
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        if (jdbcTemplate.getDataSource() == null) {
            throw new SQLException("dataSource unavailable");
        }
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            return columnExists(metaData, tableName, columnName)
                    || columnExists(metaData, tableName.toUpperCase(), columnName.toUpperCase())
                    || columnExists(metaData, tableName.toLowerCase(), columnName.toLowerCase());
        }
    }

    private boolean columnExists(DatabaseMetaData metaData, String tableName, String columnName) throws SQLException {
        try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
            return columns.next();
        }
    }
}
