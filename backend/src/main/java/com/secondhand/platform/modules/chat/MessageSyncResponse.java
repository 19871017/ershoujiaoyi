package com.secondhand.platform.modules.chat;

import java.util.List;

public class MessageSyncResponse {
    private List<ChatMessageResponse> messages;
    private Long nextAfterSeq;
    private Boolean hasMore;

    public MessageSyncResponse(List<ChatMessageResponse> messages, Long nextAfterSeq, Boolean hasMore) {
        this.messages = messages;
        this.nextAfterSeq = nextAfterSeq;
        this.hasMore = hasMore;
    }

    public List<ChatMessageResponse> getMessages() { return messages; }
    public void setMessages(List<ChatMessageResponse> messages) { this.messages = messages; }
    public Long getNextAfterSeq() { return nextAfterSeq; }
    public void setNextAfterSeq(Long nextAfterSeq) { this.nextAfterSeq = nextAfterSeq; }
    public Boolean getHasMore() { return hasMore; }
    public void setHasMore(Boolean hasMore) { this.hasMore = hasMore; }
}
