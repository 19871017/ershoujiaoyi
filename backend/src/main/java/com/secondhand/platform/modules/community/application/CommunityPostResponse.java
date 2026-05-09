package com.secondhand.platform.modules.community.application;

import java.time.Instant;
import java.util.List;

public class CommunityPostResponse {
    private final String postNo;
    private final Long postId;
    private final Long authorId;
    private final String title;
    private final String topic;
    private final String content;
    private final List<String> imageUrls;
    private final String status;
    private final int likeCount;
    private final int commentCount;
    private final Instant createdAt;

    public CommunityPostResponse(String postNo, Long postId, Long authorId, String title, String topic, String content,
                                 List<String> imageUrls, String status, int likeCount, int commentCount, Instant createdAt) {
        this.postNo = postNo;
        this.postId = postId;
        this.authorId = authorId;
        this.title = title;
        this.topic = topic;
        this.content = content;
        this.imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
        this.status = status;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.createdAt = createdAt;
    }

    public String getPostNo() { return postNo; }
    public Long getPostId() { return postId; }
    public Long getAuthorId() { return authorId; }
    public String getTitle() { return title; }
    public String getTopic() { return topic; }
    public String getContent() { return content; }
    public List<String> getImageUrls() { return imageUrls; }
    public String getStatus() { return status; }
    public int getLikeCount() { return likeCount; }
    public int getCommentCount() { return commentCount; }
    public Instant getCreatedAt() { return createdAt; }
}
