package com.secondhand.platform.modules.chat;

import com.secondhand.platform.modules.chat.application.ChatApplicationService;
import com.secondhand.platform.modules.chat.application.SendMessageCommand;
import com.secondhand.platform.shared.kernel.Result;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
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

    public ChatController(ChatApplicationService chatApplicationService, CurrentUserResolver currentUserResolver) {
        this.chatApplicationService = chatApplicationService;
        this.currentUserResolver = currentUserResolver;
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

    @GetMapping("/conversations/{conversationId}/messages")
    public Result<MessageSyncResponse> syncMessages(
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long afterSeq,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request) {
        long resolvedUserId = currentUserResolver.resolve(request);
        return Result.ok(chatApplicationService.syncMessages(conversationId, resolvedUserId, afterSeq, limit));
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
}
