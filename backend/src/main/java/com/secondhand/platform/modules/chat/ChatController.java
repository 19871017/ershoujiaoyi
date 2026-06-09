package com.secondhand.platform.modules.chat;

import com.secondhand.platform.modules.chat.application.ChatApplicationService;
import com.secondhand.platform.modules.chat.application.SendMessageCommand;
import com.secondhand.platform.shared.kernel.Result;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import com.secondhand.platform.shared.web.MediaPathGuard;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatApplicationService chatApplicationService;
    private final CurrentUserResolver currentUserResolver;
    private final Path mediaStorageRoot;

    @Autowired
    public ChatController(ChatApplicationService chatApplicationService,
                          CurrentUserResolver currentUserResolver,
                          @Value("${media.storage-root:}") String mediaStorageRoot) {
        this.chatApplicationService = chatApplicationService;
        this.currentUserResolver = currentUserResolver;
        this.mediaStorageRoot = resolveMediaStorageRoot(mediaStorageRoot);
    }

    public ChatController(ChatApplicationService chatApplicationService, CurrentUserResolver currentUserResolver) {
        this(chatApplicationService, currentUserResolver, System.getProperty("java.io.tmpdir"));
    }

    @PostMapping("/messages")
    public Result<SendMessageResponse> sendMessage(@RequestBody SendMessageRequest request, HttpServletRequest httpRequest) {
        long resolvedUserId = currentUserResolver.resolve(httpRequest);

        SendMessageCommand command = new SendMessageCommand();
        command.setConversationId(request.getConversationId());
        command.setClientMsgId(request.getClientMsgId());
        command.setSenderId(resolvedUserId);
        command.setReceiverId(request.getReceiverId());
        command.setMsgType(request.getMsgType());
        command.setContentJson(request.getContentJson());
        return Result.ok(new SendMessageResponse(chatApplicationService.sendMessage(command)));
    }

    @GetMapping("/conversations")
    public Result<ConversationListResponse> listConversations(HttpServletRequest request) {
        long resolvedUserId = currentUserResolver.resolve(request);
        return Result.ok(new ConversationListResponse(chatApplicationService.listConversations(resolvedUserId)));
    }

    @GetMapping("/conversations/{conversationId}")
    public Result<ConversationListItemResponse> getConversation(@PathVariable Long conversationId, HttpServletRequest request) {
        long resolvedUserId = currentUserResolver.resolve(request);
        return Result.ok(chatApplicationService.getConversation(conversationId, resolvedUserId));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public Result<MessageSyncResponse> syncMessages(
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long afterSeq,
            @RequestParam(required = false) Long beforeSeq,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Boolean latest,
            HttpServletRequest request) {
        long resolvedUserId = currentUserResolver.resolve(request);
        if (beforeSeq != null) {
            return Result.ok(chatApplicationService.syncEarlierMessages(conversationId, resolvedUserId, beforeSeq, limit));
        }
        if (Boolean.TRUE.equals(latest)) {
            return Result.ok(chatApplicationService.syncRecentMessages(conversationId, resolvedUserId, limit));
        }
        return Result.ok(chatApplicationService.syncMessages(conversationId, resolvedUserId, afterSeq, limit));
    }

    @GetMapping("/media")
    public ResponseEntity<Resource> readChatMedia(@RequestParam("url") String storageUrl,
                                                  HttpServletRequest request) {
        long resolvedUserId = currentUserResolver.resolve(request);
        ChatMediaAccessResponse access = chatApplicationService.requireChatMediaAccess(resolvedUserId, storageUrl);
        Path mediaPath = verifiedMediaPathFor(access.storageUrl());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(access.contentType()))
                .contentLength(fileSize(mediaPath))
                .body(new FileSystemResource(mediaPath));
    }

    @PostMapping("/conversations/{conversationId}/delivered")
    public Result<DeliveryReceiptResponse> markConversationDelivered(
            @PathVariable Long conversationId,
            HttpServletRequest httpRequest) {
        long resolvedUserId = currentUserResolver.resolve(httpRequest);
        return Result.ok(chatApplicationService.markConversationDelivered(conversationId, resolvedUserId));
    }

    @PostMapping("/conversations/{conversationId}/read")
    public Result<ReadConversationResponse> markConversationRead(
            @PathVariable Long conversationId,
            @RequestBody(required = false) ReadConversationRequest readRequest,
            HttpServletRequest httpRequest) {
        Long readSeq = readRequest == null ? null : readRequest.getReadSeq();
        long resolvedUserId = currentUserResolver.resolve(httpRequest);
        return Result.ok(chatApplicationService.markConversationRead(conversationId, resolvedUserId, readSeq));
    }

    @PostMapping("/messages/{serverMsgId}/revoke")
    public Result<RevokeMessageResponse> revokeMessage(
            @PathVariable String serverMsgId,
            HttpServletRequest httpRequest) {
        long resolvedUserId = currentUserResolver.resolve(httpRequest);
        return Result.ok(chatApplicationService.revokeMessage(serverMsgId, resolvedUserId));
    }

    @PostMapping("/conversations/{conversationId}/clear")
    public Result<ClearConversationResponse> clearConversation(
            @PathVariable Long conversationId,
            HttpServletRequest httpRequest) {
        long resolvedUserId = currentUserResolver.resolve(httpRequest);
        return Result.ok(chatApplicationService.clearConversation(conversationId, resolvedUserId));
    }

    private Path storagePathFor(String storageUrl) {
        if (storageUrl == null || !storageUrl.startsWith("/uploads/chat-")) {
            throw new IllegalArgumentException("chat media url invalid");
        }
        Path uploadsRoot = mediaStorageRoot.resolve("uploads").normalize();
        Path target = mediaStorageRoot.resolve(storageUrl.substring(1)).normalize();
        if (!target.startsWith(uploadsRoot)) {
            throw new IllegalArgumentException("chat media url invalid");
        }
        return target;
    }

    private Path verifiedMediaPathFor(String storageUrl) {
        Path mediaPath = storagePathFor(storageUrl);
        return MediaPathGuard.requireRegularFileInside(
                mediaPath,
                mediaStorageRoot.resolve("uploads"),
                "chat media url invalid",
                "chat media not found"
        );
    }

    private Path resolveMediaStorageRoot(String configuredRoot) {
        if (configuredRoot == null || configuredRoot.isBlank()) {
            throw new IllegalStateException("media.storage-root required");
        }
        return Path.of(configuredRoot).toAbsolutePath().normalize();
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("chat media not found", exception);
        }
    }
}
