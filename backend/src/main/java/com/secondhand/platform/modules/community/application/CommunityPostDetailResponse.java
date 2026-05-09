package com.secondhand.platform.modules.community.application;

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

    public boolean getLikedByMe() { return likedByMe; }
    public List<CommunityCommentResponse> getComments() { return comments; }
}
