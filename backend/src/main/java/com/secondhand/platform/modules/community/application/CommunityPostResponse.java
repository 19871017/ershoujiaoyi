package com.secondhand.platform.modules.community.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class CommunityPostResponse {
    private final String postNo;
    private final Long postId;
    private final Long authorId;
    private final String authorName;
    private final String authorAvatar;
    private final String city;
    private final String ipLocation;
    private final String title;
    private final String topic;
    private final String content;
    private final List<String> imageUrls;
    private final String status;
    private final int likeCount;
    private final int commentCount;
    private final Instant createdAt;
    private final boolean likedByMe;
    private final boolean followedByMe;
    private final Long relatedProductId;
    private final String relatedProductTitle;
    private final BigDecimal relatedProductPrice;

    public CommunityPostResponse(String postNo, Long postId, Long authorId, String title, String topic, String content,
                                 List<String> imageUrls, String status, int likeCount, int commentCount, Instant createdAt) {
        this(postNo, postId, authorId, title, topic, content, imageUrls, status, likeCount, commentCount, createdAt, false);
    }

    public CommunityPostResponse(String postNo, Long postId, Long authorId, String title, String topic, String content,
                                 List<String> imageUrls, String status, int likeCount, int commentCount, Instant createdAt,
                                 boolean likedByMe) {
        this(postNo, postId, authorId, null, null, null, title, topic, content, imageUrls, status, likeCount, commentCount, createdAt, likedByMe, false, null, null, null);
    }

    public CommunityPostResponse(String postNo, Long postId, Long authorId, String authorName, String authorAvatar, String city,
                                 String title, String topic, String content, List<String> imageUrls, String status,
                                 int likeCount, int commentCount, Instant createdAt, boolean likedByMe) {
        this(postNo, postId, authorId, authorName, authorAvatar, city, title, topic, content, imageUrls, status, likeCount, commentCount, createdAt, likedByMe, false, null, null, null);
    }

    public CommunityPostResponse(String postNo, Long postId, Long authorId, String authorName, String authorAvatar, String city,
                                 String title, String topic, String content, List<String> imageUrls, String status,
                                 int likeCount, int commentCount, Instant createdAt, boolean likedByMe,
                                 Long relatedProductId, String relatedProductTitle, BigDecimal relatedProductPrice) {
        this(postNo, postId, authorId, authorName, authorAvatar, city, title, topic, content, imageUrls, status, likeCount, commentCount, createdAt, likedByMe, false, relatedProductId, relatedProductTitle, relatedProductPrice);
    }

    public CommunityPostResponse(String postNo, Long postId, Long authorId, String authorName, String authorAvatar, String city,
                                 String title, String topic, String content, List<String> imageUrls, String status,
                                 int likeCount, int commentCount, Instant createdAt, boolean likedByMe,
                                 boolean followedByMe, Long relatedProductId, String relatedProductTitle, BigDecimal relatedProductPrice) {
        this(postNo, postId, authorId, authorName, authorAvatar, city, null, title, topic, content, imageUrls, status, likeCount, commentCount, createdAt, likedByMe, followedByMe, relatedProductId, relatedProductTitle, relatedProductPrice);
    }

    public CommunityPostResponse(String postNo, Long postId, Long authorId, String authorName, String authorAvatar, String city, String ipLocation,
                                 String title, String topic, String content, List<String> imageUrls, String status,
                                 int likeCount, int commentCount, Instant createdAt, boolean likedByMe,
                                 boolean followedByMe, Long relatedProductId, String relatedProductTitle, BigDecimal relatedProductPrice) {
        this.postNo = postNo;
        this.postId = postId;
        this.authorId = authorId;
        this.authorName = authorName;
        this.authorAvatar = authorAvatar;
        this.city = city;
        this.ipLocation = ipLocation;
        this.title = title;
        this.topic = topic;
        this.content = content;
        this.imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
        this.status = status;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.createdAt = createdAt;
        this.likedByMe = likedByMe;
        this.followedByMe = followedByMe;
        this.relatedProductId = relatedProductId;
        this.relatedProductTitle = relatedProductTitle;
        this.relatedProductPrice = relatedProductPrice;
    }

    public String getPostNo() { return postNo; }
    public Long getPostId() { return postId; }
    public Long getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public String getAuthorAvatar() { return authorAvatar; }
    public String getCity() { return city; }
    public String getIpLocation() { return ipLocation; }
    public String getTitle() { return title; }
    public String getTopic() { return topic; }
    public String getContent() { return content; }
    public List<String> getImageUrls() { return imageUrls; }
    public String getStatus() { return status; }
    public int getLikeCount() { return likeCount; }
    public int getCommentCount() { return commentCount; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean getLikedByMe() { return likedByMe; }
    public boolean getFollowedByMe() { return followedByMe; }
    public Long getRelatedProductId() { return relatedProductId; }
    public String getRelatedProductTitle() { return relatedProductTitle; }
    public BigDecimal getRelatedProductPrice() { return relatedProductPrice; }
}
