package com.secondhand.platform.modules.community.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class CommunityPostDetailResponse extends CommunityPostResponse {
    private final boolean likedByMe;
    private final List<CommunityCommentResponse> comments;

    public CommunityPostDetailResponse(String postNo, Long postId, Long authorId, String title, String topic, String content,
                                       List<String> imageUrls, String status, int likeCount, int commentCount,
                                       Instant createdAt, boolean likedByMe, List<CommunityCommentResponse> comments) {
        super(postNo, postId, authorId, title, topic, content, imageUrls, status, likeCount, commentCount, createdAt);
        this.likedByMe = likedByMe;
        this.comments = comments == null ? List.of() : List.copyOf(comments);
    }

    public CommunityPostDetailResponse(String postNo, Long postId, Long authorId, String authorName, String authorAvatar, String city,
                                       String title, String topic, String content, List<String> imageUrls, String status,
                                       int likeCount, int commentCount, Instant createdAt, boolean likedByMe,
                                       List<CommunityCommentResponse> comments) {
        this(postNo, postId, authorId, authorName, authorAvatar, city, title, topic, content, imageUrls, status, likeCount, commentCount, createdAt, likedByMe, comments, null, null, null);
    }

    public CommunityPostDetailResponse(String postNo, Long postId, Long authorId, String authorName, String authorAvatar, String city,
                                       String title, String topic, String content, List<String> imageUrls, String status,
                                       int likeCount, int commentCount, Instant createdAt, boolean likedByMe,
                                       List<CommunityCommentResponse> comments, Long relatedProductId, String relatedProductTitle,
                                       BigDecimal relatedProductPrice) {
        this(postNo, postId, authorId, authorName, authorAvatar, city, title, topic, content, imageUrls, status, likeCount, commentCount, createdAt, likedByMe, false, comments, relatedProductId, relatedProductTitle, relatedProductPrice);
    }

    public CommunityPostDetailResponse(String postNo, Long postId, Long authorId, String authorName, String authorAvatar, String city,
                                       String title, String topic, String content, List<String> imageUrls, String status,
                                       int likeCount, int commentCount, Instant createdAt, boolean likedByMe, boolean followedByMe,
                                       List<CommunityCommentResponse> comments, Long relatedProductId, String relatedProductTitle,
                                       BigDecimal relatedProductPrice) {
        this(postNo, postId, authorId, authorName, authorAvatar, city, null, title, topic, content, imageUrls, status, likeCount, commentCount, createdAt, likedByMe, followedByMe, comments, relatedProductId, relatedProductTitle, relatedProductPrice);
    }

    public CommunityPostDetailResponse(String postNo, Long postId, Long authorId, String authorName, String authorAvatar, String city, String ipLocation,
                                       String title, String topic, String content, List<String> imageUrls, String status,
                                       int likeCount, int commentCount, Instant createdAt, boolean likedByMe, boolean followedByMe,
                                       List<CommunityCommentResponse> comments, Long relatedProductId, String relatedProductTitle,
                                       BigDecimal relatedProductPrice) {
        super(postNo, postId, authorId, authorName, authorAvatar, city, ipLocation, title, topic, content, imageUrls, status, likeCount, commentCount, createdAt, likedByMe,
                followedByMe, relatedProductId, relatedProductTitle, relatedProductPrice);
        this.likedByMe = likedByMe;
        this.comments = comments == null ? List.of() : List.copyOf(comments);
    }

    public boolean getLikedByMe() { return likedByMe; }
    public List<CommunityCommentResponse> getComments() { return comments; }
}
