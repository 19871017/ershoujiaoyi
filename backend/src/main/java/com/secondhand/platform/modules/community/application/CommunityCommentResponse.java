package com.secondhand.platform.modules.community.application;

import java.time.Instant;

public class CommunityCommentResponse {
    private final String commentNo;
    private final Long authorId;
    private final String content;
    private final Instant createdAt;

    public CommunityCommentResponse(String commentNo, Long authorId, String content, Instant createdAt) {
        this.commentNo = commentNo;
        this.authorId = authorId;
        this.content = content;
        this.createdAt = createdAt;
    }

    public String getCommentNo() { return commentNo; }
    public Long getAuthorId() { return authorId; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
