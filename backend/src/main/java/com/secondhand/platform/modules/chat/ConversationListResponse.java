package com.secondhand.platform.modules.chat;

import java.util.List;

public class ConversationListResponse {
    private List<ConversationListItemResponse> conversations;

    public ConversationListResponse(List<ConversationListItemResponse> conversations) {
        this.conversations = conversations;
    }

    public List<ConversationListItemResponse> getConversations() {
        return conversations;
    }

    public void setConversations(List<ConversationListItemResponse> conversations) {
        this.conversations = conversations;
    }
}
