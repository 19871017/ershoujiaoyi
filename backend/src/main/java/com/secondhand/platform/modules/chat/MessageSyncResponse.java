package com.secondhand.platform.modules.chat;

import java.util.List;

public class MessageSyncResponse {
    private List<ChatMessageResponse> messages;
    private Long nextAfterSeq;
    private Boolean hasMore;
    private Long previousBeforeSeq;
    private Boolean hasEarlier;

    public MessageSyncResponse(List<ChatMessageResponse> messages, Long nextAfterSeq, Boolean hasMore) {
        this(messages, nextAfterSeq, hasMore, 0L, false);
    }

    public MessageSyncResponse(List<ChatMessageResponse> messages, Long nextAfterSeq, Boolean hasMore, Long previousBeforeSeq, Boolean hasEarlier) {
        this.messages = messages;
        this.nextAfterSeq = nextAfterSeq;
        this.hasMore = hasMore;
        this.previousBeforeSeq = previousBeforeSeq;
        this.hasEarlier = hasEarlier;
    }

    public List<ChatMessageResponse> getMessages() { return messages; }
    public void setMessages(List<ChatMessageResponse> messages) { this.messages = messages; }
    public Long getNextAfterSeq() { return nextAfterSeq; }
    public void setNextAfterSeq(Long nextAfterSeq) { this.nextAfterSeq = nextAfterSeq; }
    public Boolean getHasMore() { return hasMore; }
    public void setHasMore(Boolean hasMore) { this.hasMore = hasMore; }
    public Long getPreviousBeforeSeq() { return previousBeforeSeq; }
    public void setPreviousBeforeSeq(Long previousBeforeSeq) { this.previousBeforeSeq = previousBeforeSeq; }
    public Boolean getHasEarlier() { return hasEarlier; }
    public void setHasEarlier(Boolean hasEarlier) { this.hasEarlier = hasEarlier; }
}
