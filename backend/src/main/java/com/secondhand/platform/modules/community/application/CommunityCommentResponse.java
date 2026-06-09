package com.secondhand.platform.modules.community.application;

import java.time.Instant;

public class CommunityCommentResponse {
    private final String commentNo;
    private final Long authorId;
    private final String authorName;
    private final String authorAvatar;
    private final String content;
    private final String status;
    private final Instant createdAt;

    public CommunityCommentResponse(String commentNo, Long authorId, String content, Instant createdAt) {
        this(commentNo, authorId, null, null, content, createdAt);
    }

    public CommunityCommentResponse(String commentNo, Long authorId, String authorName, String authorAvatar, String content, Instant createdAt) {
        this(commentNo, authorId, authorName, authorAvatar, content, "PUBLISHED", createdAt);
    }

    public CommunityCommentResponse(String commentNo, Long authorId, String authorName, String authorAvatar, String content, String status, Instant createdAt) {
        this.commentNo = commentNo;
        this.authorId = authorId;
        this.authorName = authorName;
        this.authorAvatar = authorAvatar;
        this.content = content;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getCommentNo() { return commentNo; }
    public Long getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public String getAuthorAvatar() { return authorAvatar; }
    public String getContent() { return content; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
